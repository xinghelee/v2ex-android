package com.vibe.v2ex.data.push

import com.vibe.v2ex.data.model.Member
import com.vibe.v2ex.data.model.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPushPlannerTest {

    private fun notification(id: Long, from: String = "alice", text: String = "<a href=\"/member/$from\">$from</a> 在 <a href=\"/t/1\">标题</a> 里回复了你") =
        Notification(id = id, text = text, member = Member(username = from))

    @Test
    fun `first run only records a baseline and notifies nothing`() {
        val plan = NotificationPushPlanner.plan(lastSeenId = 0L, notifications = listOf(notification(30), notification(20)))

        assertTrue(plan.toNotify.isEmpty())
        assertEquals(30L, plan.newLastSeenId)
    }

    @Test
    fun `only notifications newer than the baseline are pushed, newest first`() {
        val plan = NotificationPushPlanner.plan(
            lastSeenId = 20L,
            notifications = listOf(notification(21), notification(30), notification(20), notification(5)),
        )

        assertEquals(listOf(30L, 21L), plan.toNotify.map { it.id })
        assertEquals(30L, plan.newLastSeenId)
    }

    @Test
    fun `nothing new keeps the baseline and pushes nothing`() {
        val plan = NotificationPushPlanner.plan(lastSeenId = 30L, notifications = listOf(notification(30), notification(20)))

        assertTrue(plan.toNotify.isEmpty())
        assertEquals(30L, plan.newLastSeenId)
    }

    @Test
    fun `empty page never lowers the baseline`() {
        val plan = NotificationPushPlanner.plan(lastSeenId = 30L, notifications = emptyList())

        assertTrue(plan.toNotify.isEmpty())
        assertEquals(30L, plan.newLastSeenId)
    }

    @Test
    fun `blocked users are skipped but still advance the baseline`() {
        val plan = NotificationPushPlanner.plan(
            lastSeenId = 10L,
            notifications = listOf(notification(12, from = "Spammer"), notification(11, from = "bob")),
            blockedUsernames = setOf("spammer"),
        )

        assertEquals(listOf(11L), plan.toNotify.map { it.id })
        assertEquals(12L, plan.newLastSeenId)
    }

    @Test
    fun `plain text strips the html links`() {
        assertEquals("alice 在 标题 里回复了你", NotificationPushPlanner.plainText(notification(1)))
        assertEquals("你有一条新提醒", NotificationPushPlanner.plainText(Notification(id = 2, text = null)))
    }
}
