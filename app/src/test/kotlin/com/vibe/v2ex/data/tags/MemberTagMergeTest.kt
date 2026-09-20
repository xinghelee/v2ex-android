package com.vibe.v2ex.data.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberTagMergeTest {
    @Test
    fun `union keeps primary order and appends what only the other side has`() {
        val local = listOf(
            MemberTagRecord("alice", listOf("靠谱", "老哥")),
            MemberTagRecord("bob", listOf("广告号"), avatarUrl = null),
        )
        val remote = listOf(
            MemberTagRecord("Alice", listOf("老哥", "新增"), avatarUrl = "https://cdn.v2ex.com/a.png"),
            MemberTagRecord("carol", listOf("同事")),
        )

        val merged = mergeMemberTags(local, remote)

        assertEquals(listOf("alice", "bob", "carol"), merged.map { it.username })
        assertEquals(listOf("靠谱", "老哥", "新增"), merged[0].tags)
        assertEquals("https://cdn.v2ex.com/a.png", merged[0].avatarUrl)
        assertEquals(listOf("广告号"), merged[1].tags)
        assertEquals(listOf("同事"), merged[2].tags)
    }

    @Test
    fun `never drops a tag from either side`() {
        val local = listOf(MemberTagRecord("alice", listOf("a", "b")))
        val remote = listOf(MemberTagRecord("alice", listOf("c")))
        assertEquals(listOf("a", "b", "c"), mergeMemberTags(local, remote).single().tags)
        assertEquals(listOf("c", "a", "b"), mergeMemberTags(remote, local).single().tags)
    }

    @Test
    fun `normalizes whitespace duplicates and empty entries`() {
        val merged = mergeMemberTags(
            listOf(MemberTagRecord(" alice ", listOf(" 靠谱 ", "", "靠谱", "  "))),
            listOf(MemberTagRecord("", listOf("x")), MemberTagRecord("bob", listOf("", " "))),
        )
        assertEquals(1, merged.size)
        assertEquals("alice", merged.single().username)
        assertEquals(listOf("靠谱"), merged.single().tags)
        assertEquals(listOf("a", "b"), normalizeMemberTags(listOf(" a", "b ", "a", "")))
    }

    @Test
    fun `lookup is case insensitive and empty when disabled`() {
        val lookup = MemberTagLookup.of(listOf(MemberTagRecord("Alice", listOf("靠谱")), MemberTagRecord("bob", emptyList())))
        assertTrue(lookup.enabled)
        assertEquals(1, lookup.taggedCount)
        assertEquals(listOf("靠谱"), lookup.tagsFor("ALICE"))
        assertTrue(lookup.tagsFor("bob").isEmpty())
        assertTrue(lookup.tagsFor("").isEmpty())
        assertFalse(MemberTagLookup.Disabled.enabled)
        assertTrue(MemberTagLookup.Disabled.tagsFor("alice").isEmpty())
    }
}
