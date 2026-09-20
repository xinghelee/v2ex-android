package com.vibe.v2ex.feature.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.tags.MemberTagStore
import com.vibe.v2ex.data.tags.normalizeMemberTags
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemberTagEditorState(
    val loaded: Boolean = false,
    val tags: List<String> = emptyList(),
)

/** 每个被编辑的用户名一个实例（hiltViewModel 按 key 区分），打开对话框时重新从库里读，不复用上次草稿。 */
@HiltViewModel
class MemberTagEditorViewModel @Inject constructor(
    private val store: MemberTagStore,
) : ViewModel() {
    private val _state = MutableStateFlow(MemberTagEditorState())
    val state: StateFlow<MemberTagEditorState> = _state.asStateFlow()

    fun load(username: String) {
        _state.value = MemberTagEditorState()
        viewModelScope.launch {
            val tags = store.tagsFor(username)
            _state.update { it.copy(loaded = true, tags = tags) }
        }
    }

    fun add(tag: String) {
        val cleaned = tag.trim()
        if (cleaned.isEmpty()) return
        _state.update { it.copy(tags = normalizeMemberTags(it.tags + cleaned)) }
    }

    fun remove(tag: String) = _state.update { state -> state.copy(tags = state.tags.filterNot { it == tag }) }

    /** 落库后回调关闭对话框；不用状态位，否则同一用户再次打开时会先看到上次的「已保存」而立刻关掉。 */
    fun save(username: String, avatarUrl: String?, onSaved: () -> Unit) {
        viewModelScope.launch {
            store.setTags(username, _state.value.tags, avatarUrl)
            onSaved()
        }
    }
}

/**
 * 给 @[username] 打标记的对话框：已有标记可逐个移除，输入框回车或加号新增，保存才落库。
 * 全部移除后保存等于删掉这个用户的标记。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberTagEditorDialog(
    username: String,
    avatarUrl: String?,
    onDismiss: () -> Unit,
    viewModel: MemberTagEditorViewModel = hiltViewModel(key = "member-tag-editor:$username"),
) {
    val state by viewModel.state.collectAsState()
    var draft by rememberSaveable(username) { mutableStateOf("") }

    LaunchedEffect(username) { viewModel.load(username) }

    val commitDraft = {
        if (draft.isNotBlank()) {
            viewModel.add(draft)
            draft = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标记 @$username") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!state.loaded) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (state.tags.isEmpty()) {
                    Text(
                        text = "还没有标记。标记只保存在你自己这里，对方看不到。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        state.tags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.remove(tag) },
                                label = { Text(tag) },
                                trailingIcon = { Text("×", style = MaterialTheme.typography.labelLarge) },
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(40) },
                        placeholder = { Text("新标记，如「靠谱」「广告号」") },
                        singleLine = true,
                        enabled = state.loaded,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitDraft() }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = commitDraft,
                        enabled = state.loaded && draft.isNotBlank(),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "添加标记")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    commitDraft()
                    viewModel.save(username, avatarUrl, onSaved = onDismiss)
                },
                enabled = state.loaded,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
