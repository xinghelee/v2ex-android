package com.vibe.v2ex.feature.topic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.model.Reply
import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.repository.TopicRepository
import com.vibe.v2ex.designsystem.ContentBlock
import com.vibe.v2ex.designsystem.htmlToPlainText
import com.vibe.v2ex.designsystem.parseContentBlocks
import com.vibe.v2ex.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** [floor] is null when the mention couldn't be resolved to an earlier floor — UI shows a bare @username. */
data class QuotedReply(val username: String, val floor: Int?, val excerpt: String)

/** [floor] is 1-based over replies sorted ascending by id — client-derived, not part of the API payload. */
data class FloorReply(
    val reply: Reply,
    val floor: Int,
    val isAuthor: Boolean,
    val quoted: QuotedReply?,
    val blocks: List<ContentBlock>,
)

data class TopicUiState(
    val topic: Topic? = null,
    val topicBlocks: List<ContentBlock> = emptyList(),
    val replies: List<FloorReply> = emptyList(),
    val onlyPoster: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val favorited: Boolean = false,
    val favoriteSyncing: Boolean = false,
    val message: String? = null,
) {
    /** Floors are assigned before filtering, so quote references stay valid under 只看楼主. */
    val visibleReplies: List<FloorReply>
        get() = if (onlyPoster) replies.filter { it.isAuthor } else replies
}

// Strips a leading `@user` / `@user #N` (plain or anchor-wrapped) off the rendered HTML while
// keeping remaining markup intact, so later links in the same reply stay tappable.
private val LEADING_MENTION_REGEX =
    Regex("""^\s*@\s*(?:<a\b[^>]*>[^<]*</a>|[A-Za-z0-9_-]+)\s*(?:#\d+)?\s*""")

private const val QUOTE_EXCERPT_LIMIT = 40

@HiltViewModel
class TopicViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TopicRepository,
    private val secureStore: SecureStore,
) : ViewModel() {
    private val topicId: Long = savedStateHandle.toRoute<Route.Topic>().topicId

    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.loadTopic(topicId)
                .onSuccess { detail ->
                    val (topicBlocks, threaded) = withContext(Dispatchers.Default) {
                        val bodyHtml = detail.topic.contentRendered.orEmpty()
                            .ifBlank { detail.topic.content.orEmpty() }
                        parseContentBlocks(bodyHtml) to threadReplies(detail.replies, detail.topic.authorName)
                    }
                    _uiState.update {
                        it.copy(
                            topic = detail.topic,
                            topicBlocks = topicBlocks,
                            replies = threaded,
                            isLoading = false,
                        )
                    }
                    syncFavoriteState()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "加载失败") }
                }
        }
    }

    fun toggleOnlyPoster() {
        _uiState.update { it.copy(onlyPoster = !it.onlyPoster) }
    }

    fun toggleFavorite() {
        val state = _uiState.value
        if (state.favoriteSyncing) return
        if (!secureStore.isWebSessionActive) {
            _uiState.update { it.copy(message = "收藏需要先在「账户」中登录网页会话") }
            return
        }
        val target = !state.favorited
        _uiState.update { it.copy(favorited = target, favoriteSyncing = true) }
        viewModelScope.launch {
            repository.setFavorite(topicId, target)
                .onSuccess { _uiState.update { it.copy(favoriteSyncing = false) } }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            favorited = !target,
                            favoriteSyncing = false,
                            message = if (target) "收藏失败，请稍后再试" else "取消收藏失败，请稍后再试",
                        )
                    }
                }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Server-authoritative initial state — a blind toggle would pick the wrong action link on already-favorited topics. */
    private fun syncFavoriteState() {
        if (!secureStore.isWebSessionActive) return
        viewModelScope.launch {
            repository.fetchFavoriteState(topicId)?.let { favorited ->
                _uiState.update { it.copy(favorited = favorited) }
            }
        }
    }

    /**
     * Floors are 1-based over id-ascending replies. [floorsByAuthor] is built incrementally,
     * so a reply can only quote something earlier in the thread.
     */
    private fun threadReplies(replies: List<Reply>, authorName: String): List<FloorReply> {
        val sorted = replies.sortedBy { it.id }
        val floorsByAuthor = mutableMapOf<String, MutableList<Int>>()
        val result = ArrayList<FloorReply>(sorted.size)
        sorted.forEachIndexed { index, reply ->
            val floor = index + 1
            val quoted = resolveQuote(reply.content, floorsByAuthor, result)
            val html = reply.contentRendered.ifBlank { reply.content }
            val cleaned = if (quoted != null) LEADING_MENTION_REGEX.replaceFirst(html, "") else html
            result += FloorReply(
                reply = reply,
                floor = floor,
                isAuthor = authorName.isNotEmpty() && reply.authorName == authorName,
                quoted = quoted,
                blocks = parseContentBlocks(cleaned),
            )
            floorsByAuthor.getOrPut(reply.authorName) { mutableListOf() } += floor
        }
        return result
    }

    private fun resolveQuote(
        rawContent: String,
        floorsByAuthor: Map<String, List<Int>>,
        earlier: List<FloorReply>,
    ): QuotedReply? {
        var i = 0
        // A markdown-blockquote `>` prefix is tolerated; the quote must otherwise lead the reply.
        while (i < rawContent.length && (rawContent[i].isWhitespace() || rawContent[i] == '>')) i++
        if (i >= rawContent.length || rawContent[i] != '@') return null
        i++
        val nameStart = i
        while (i < rawContent.length && rawContent[i].isUsernameChar()) i++
        val username = rawContent.substring(nameStart, i)
        if (username.length < 2) return null
        while (i < rawContent.length && rawContent[i] == ' ') i++
        var explicitFloor: Int? = null
        if (i < rawContent.length && rawContent[i] == '#') {
            i++
            val digitsStart = i
            while (i < rawContent.length && rawContent[i] in '0'..'9') i++
            if (i > digitsStart) explicitFloor = rawContent.substring(digitsStart, i).toIntOrNull()
        }
        val resolvedFloor = explicitFloor ?: floorsByAuthor[username]?.lastOrNull()
        if (resolvedFloor != null) {
            val quotedReply = earlier.getOrNull(resolvedFloor - 1)
            if (quotedReply != null) {
                val plain = htmlToPlainText(
                    quotedReply.reply.contentRendered.ifBlank { quotedReply.reply.content },
                )
                val excerpt = if (plain.length > QUOTE_EXCERPT_LIMIT) {
                    plain.take(QUOTE_EXCERPT_LIMIT) + "…"
                } else {
                    plain
                }
                return QuotedReply(username, resolvedFloor, excerpt)
            }
        }
        return QuotedReply(username, null, "")
    }

    private fun Char.isUsernameChar(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '_' || this == '-'
}
