package com.vibe.v2ex.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

    private val backup = """
        {"options":{"theme":{"mode":"compact"}},
         "api":{"pat":"secret-token","limit":600},
         "member-tag":{
           "alice":{"tags":[{"name":"靠谱"},{"name":" 老哥 "},{"name":""},{"name":"靠谱"}],"avatar":"https://cdn.v2ex.com/a.png"},
           "Bob_1":{"tags":[{"name":"广告号"}]},
           "bad name":{"tags":[{"name":"x"}]},
           "empty":{"tags":[]},
           "notobject":"oops",
           "numbers":{"tags":[{"name":12}]}
         },
         "settings-sync":{"version":7,"lastSyncTime":1700000000000}}
    """.trimIndent()

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
        assertNull(PolishSettingsNoteParser.parse(1, mark + "{\"options\":{}}" + " ".repeat(PolishSettingsNoteParser.MAX_CONTENT_CHARS)))
        val minimal = PolishSettingsNoteParser.parse(1, "$mark{\"options\":{}}")
        assertNotNull(minimal)
        assertTrue(minimal!!.memberTags.isEmpty())
        assertEquals(0, minimal.syncVersion)
        assertEquals(0, PolishSettingsNoteParser.parse(1, "$mark{\"options\":{},\"settings-sync\":{\"version\":\"x\"}}")!!.syncVersion)
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
        assertEquals("8", root.getValue("settings-sync").jsonObject.getValue("version").jsonPrimitive.content)
        assertEquals("1800000000000", root.getValue("settings-sync").jsonObject.getValue("lastSyncTime").jsonPrimitive.content)
        val tags = root.getValue("member-tag").jsonObject
        assertEquals(setOf("alice", "carol"), tags.keys)
        assertFalse(tags.getValue("alice").jsonObject.containsKey("avatar"))
        assertEquals("https://cdn.v2ex.com/c.png", tags.getValue("carol").jsonObject.getValue("avatar").jsonPrimitive.content)

        // 写回的内容必须能被自己（也就是插件的同一套规则）再解析出来。
        val reparsed = PolishSettingsNoteParser.parse(222, content)!!
        assertEquals(listOf("靠谱", "老哥", "新增"), reparsed.memberTags.getValue("alice").tags)
        assertEquals(8, reparsed.syncVersion)
    }

    @Test
    fun `build content for a brand new note adds an empty options key`() {
        val content = PolishSettingsNoteParser.buildContent(
            root = null,
            memberTags = mapOf("alice" to PolishMemberTag(listOf("靠谱"))),
            version = 1,
            nowMillis = 1,
        )
        val note = PolishSettingsNoteParser.parse(5, content)
        assertNotNull(note)
        assertEquals(1, note!!.syncVersion)
        assertEquals(listOf("靠谱"), note.memberTags.getValue("alice").tags)
        assertTrue(note.root.getValue("options").jsonObject.isEmpty())
    }

    @Test
    fun `sync byte estimate counts key name plus utf8 json`() {
        val empty = PolishSettingsNoteParser.memberTagsSyncBytes(emptyMap())
        assertEquals("member-tag".length + 2, empty)
        val one = PolishSettingsNoteParser.memberTagsSyncBytes(mapOf("a" to PolishMemberTag(listOf("靠谱"))))
        assertTrue(one > empty)
        assertTrue(one < PolishSettingsNoteParser.SYNC_ITEM_QUOTA_BYTES)
    }
}
