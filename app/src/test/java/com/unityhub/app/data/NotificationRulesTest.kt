package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRulesTest {
    private fun notification(readAt: String? = null) = NotificationDto(
        id = "notification-id",
        organizationId = "organization-id",
        recipientUserId = "user-id",
        notificationType = "assignment_created",
        title = "New service assignment",
        message = "You have been assigned as Electric Guitar.",
        servicePlanId = "service-id",
        assignmentId = "assignment-id",
        createdAt = "2026-11-04T04:00:00Z",
        readAt = readAt
    )

    @Test
    fun notificationsWithoutReadTimestampAreUnread() {
        assertTrue(NotificationRules.isUnread(notification()))
    }

    @Test
    fun notificationsWithReadTimestampAreNotUnread() {
        assertFalse(NotificationRules.isUnread(notification("2026-11-04T05:00:00Z")))
    }

    @Test
    fun unreadFilterIncludesOnlyNotificationsWithoutReadTimestamps() {
        val unread = notification()
        val read = notification("2026-11-04T05:00:00Z").copy(id = "read-notification")

        assertEquals(
            listOf(unread.id),
            NotificationRules.filterNotifications(listOf(unread, read), unreadOnly = true).map { it.id }
        )
    }

    @Test
    fun allFilterPreservesReadAndUnreadNotificationsInOriginalOrder() {
        val unread = notification()
        val read = notification("2026-11-04T05:00:00Z").copy(id = "read-notification")

        assertEquals(
            listOf(unread.id, read.id),
            NotificationRules.filterNotifications(listOf(unread, read), unreadOnly = false).map { it.id }
        )
    }

    @Test
    fun notificationsWithAServicePlanCanOpenThatService() {
        assertTrue(NotificationRules.hasServicePlan(notification()))
    }

    @Test
    fun notificationsWithoutAServicePlanCannotOpenAService() {
        assertFalse(NotificationRules.hasServicePlan(notification().copy(servicePlanId = null)))
    }

    @Test
    fun eachNotificationTypeHasAReadableLabel() {
        assertEquals("New assignment", NotificationRules.typeLabel("assignment_created"))
        assertEquals("Response reminder", NotificationRules.typeLabel("assignment_reminder"))
        assertEquals("Team response", NotificationRules.typeLabel("assignment_response"))
        assertEquals("Notification", NotificationRules.typeLabel("unknown"))
    }
}
