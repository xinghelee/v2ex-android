package com.vibe.v2ex.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolishSettingsNoteTest {
    private val mark = PolishSettingsNoteParser.MARK

    private fun noteList(vararg items: Pair<String, String>): String = buildString {
        append("<div id='Main'>")
        items.forEach { (href, title) ->
            append("<div class='note_item'><div class='note_item_title'><a href='$href'>$title</a></div></div>")
        }
        append("<a href='/notes/9999'>$mark 不在 note_item 里</a>")
        append("</div>")
    }

    private fun editPage(content: String): String =
        "<form method='post'><textarea id='note_content' class='note_editor' name='content'>$content</textarea></form>"

    // 2.x 的写法：标签是纯字符串；Bob_1 是 1.x 遗留的 {name} 对象，两种都要认。
    private val backup = """
        {"options":{"theme":{"mode":"compact"},"nestedReply":{"display":"indent"}},
         "api":{"pat":"secret-token","limit":600},
         "member-tag":{
           "alice":{"tags":["靠谱"," 老哥 ","","靠谱"],"avatar":"https://cdn.v2ex.com/a.png"},
           "Bob_1":{"tags":[{"name":"广告号"}]},
           "bad name":{"tags":["x"]},
           "empty":{"tags":[]},
           "notobject":"oops",
           "numbers":{"tags":[12,{"name":34}]}
         },
         "settings-sync":{"version":7,"lastSyncTime":1700000000000,"lastCheckTime":1700000000000}}
    """.trimIndent()

    private val validOptions = """{"options":{"theme":{},"nestedReply":{}}"""

    @Test
    fun `finds the first note whose title starts with the mark`() {
        val html = noteList(
            "/notes/100" to "购物清单",
            "/notes/222?x=1" to "$mark 备份",
            "/notes/333" to mark,
        )
        assertEquals(222L, PolishSettingsNoteParser.findNoteId(html))
        assertEquals(444L, PolishSettingsNoteParser.findNoteId(noteList("/notes/edit/444" to mark)))
        assertNull(PolishSettingsNoteParser.findNoteId(noteList("/notes/100" to "购物清单")))
        assertNull(PolishSettingsNoteParser.findNoteId(noteList("https://evil.example/notes/5" to mark)))
        assertNull(PolishSettingsNoteParser.findNoteId(noteList("/notes/abc" to mark)))
        assertNull(PolishSettingsNoteParser.findNoteId(noteList("/notes/5" to "前缀 $mark")))
        assertNull(PolishSettingsNoteParser.findNoteId(""))
    }

    @Test
    fun `reads the editor textarea verbatim and rejects pages without one`() {
        assertEquals("$mark{\"a\":1}", PolishSettingsNoteParser.noteContent(editPage("$mark{&quot;a&quot;:1}")))
        assertEquals("a  b", PolishSettingsNoteParser.noteContent(editPage("a  b")))
        assertNull(PolishSettingsNoteParser.noteContent("<html><a href='/signin'>登录</a></html>"))
        assertNull(PolishSettingsNoteParser.noteContent("<textarea id='other' class='note_editor'>x</textarea>"))
    }

    @Test
    fun `parses member tags leniently and keeps the rest of the backup`() {
        val note = PolishSettingsNoteParser.parse(222, "  $mark$backup  ")
        assertNotNull(note)
        note!!
        assertEquals(222L, note.noteId)
        assertEquals(7, note.syncVersion)
        assertEquals(listOf("alice", "Bob_1"), note.memberTags.keys.toList())
        assertEquals(listOf("靠谱", "老哥"), note.memberTags.getValue("alice").tags)
        assertEquals("https://cdn.v2ex.com/a.png", note.memberTags.getValue("alice").avatar)
        assertNull(note.memberTags.getValue("Bob_1").avatar)
        assertEquals("secret-token", note.root.getValue("api").jsonObject.getValue("pat").jsonPrimitive.content)
    }

    @Test
    fun `rejects content that is not a Polish backup`() {
        assertNull(PolishSettingsNoteParser.parse(1, ""))
        assertNull(PolishSettingsNoteParser.parse(1, "{\"options\":{}}"))
        assertNull(PolishSettingsNoteParser.parse(1, "$mark{not json"))
        assertNull(PolishSettingsNoteParser.parse(1, "$mark[1,2]"))
        assertNull(PolishSettingsNoteParser.parse(1, "$mark{\"member-tag\":{}}"))
        assertNull(PolishSettingsNoteParser.parse(1, mark + "$validOptions}" + " ".repeat(PolishSettingsNoteParser.MAX_CONTENT_CHARS)))
        // 插件 2.x 不认的都拒绝：旧版 App 新建记事本时写的空 options 就属于这种。
        assertNull(PolishSettingsNoteParser.parse(1, "$mark{\"options\":{}}"))
        assertNull(PolishSettingsNoteParser.parse(1, "$mark{\"options\":{\"theme\":{}}}"))
        assertNull(PolishSettingsNoteParser.parse(1, "$mark{\"options\":\"x\"}"))
        val minimal = PolishSettingsNoteParser.parse(1, "$mark$validOptions}")
        assertNotNull(minimal)
        assertTrue(minimal!!.memberTags.isEmpty())
        assertEquals(0, minimal.syncVersion)
        assertEquals(0, PolishSettingsNoteParser.parse(1, "$mark$validOptions,\"settings-sync\":{\"version\":\"x\"}}")!!.syncVersion)
    }

    @Test
    fun `build content only replaces member tags and sync info`() {
        val note = PolishSettingsNoteParser.parse(222, mark + backup)!!
        val content = PolishSettingsNoteParser.buildContent(
            root = note.root,
            memberTags = linkedMapOf(
                "alice" to PolishMemberTag(listOf("靠谱", "老哥", "新增")),
                "carol" to PolishMemberTag(listOf("同事"), avatar = "https://cdn.v2ex.com/c.png"),
            ),
            version = 8,
            nowMillis = 1_800_000_000_000,
        )
        assertTrue(content.startsWith(mark))
        val root = Json.parseToJsonElement(content.removePrefix(mark)) as JsonObject
        assertEquals("secret-token", root.getValue("api").jsonObject.getValue("pat").jsonPrimitive.content)
        assertEquals("compact", root.getValue("options").jsonObject.getValue("theme").jsonObject.getValue("mode").jsonPrimitive.content)
        val syncInfo = root.getValue("settings-sync").jsonObject
        assertEquals("8", syncInfo.getValue("version").jsonPrimitive.content)
        assertEquals("1800000000000", syncInfo.getValue("lastSyncTime").jsonPrimitive.content)
        assertEquals("1800000000000", syncInfo.getValue("lastCheckTime").jsonPrimitive.content)
        val tags = root.getValue("member-tag").jsonObject
        assertEquals(setOf("alice", "carol"), tags.keys)
        assertFalse(tags.getValue("alice").jsonObject.containsKey("avatar"))
        assertEquals("https://cdn.v2ex.com/c.png", tags.getValue("carol").jsonObject.getValue("avatar").jsonPrimitive.content)
        // 插件 2.x 渲染时直接 tags.join，写成 {name} 对象会显示成 [object Object]。
        assertEquals(
            listOf("靠谱", "老哥", "新增"),
            tags.getValue("alice").jsonObject.getValue("tags").jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(tags.getValue("alice").jsonObject.getValue("tags").jsonArray.all { it is JsonPrimitive && it.isString })

        // 写回的内容必须能被自己（也就是插件的同一套规则）再解析出来。
        val reparsed = PolishSettingsNoteParser.parse(222, content)!!
        assertEquals(listOf("靠谱", "老哥", "新增"), reparsed.memberTags.getValue("alice").tags)
        assertEquals(8, reparsed.syncVersion)
    }
}
