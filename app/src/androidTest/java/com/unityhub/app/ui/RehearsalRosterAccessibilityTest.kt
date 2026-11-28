package com.unityhub.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unityhub.app.data.RehearsalAssignmentDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RehearsalRosterAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun availabilityCardExplainsQueuedOfflineResponseAndKeepsActionsAtLeast48Dp() {
        val selectedResponses = mutableListOf<String>()

        composeRule.setContent {
            MaterialTheme {
                RehearsalAvailabilityResponseCard(
                    assignment = assignment("pending"),
                    pendingSync = true,
                    offlineSnapshot = true,
                    busy = false,
                    onRespond = { status -> selectedResponses.add(status) }
                )
            }
        }

        composeRule.onNodeWithText("Your availability").assertIsDisplayed()
        composeRule.onNodeWithText("Current response: Awaiting response").assertIsDisplayed()
        composeRule.onNodeWithText("Saved on this device · waiting to sync").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Showing the last synced rehearsal details. Your response will be sent when the connection returns."
        ).assertIsDisplayed()

        assertTouchTargetAtLeast48Dp("Available")
        assertTouchTargetAtLeast48Dp("Can't attend")

        composeRule.onNodeWithText("Available").performClick()
        composeRule.onNodeWithText("Can't attend").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("available", "unavailable"), selectedResponses)
        }
    }

    @Test
    fun attendanceCardWrapsLongNamesAndKeepsBothManagerActionsAtLeast48DpAtLargeFontScale() {
        val selectedAttendance = mutableListOf<String>()
        val memberName = "Alexandria-Christina Rivera de la Cruz, Music Director"

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density,
                    fontScale = 2f
                )
            ) {
                MaterialTheme {
                    Box(Modifier.width(360.dp)) {
                        RehearsalAssignmentCard(
                            assignment = assignment("available"),
                            memberName = memberName,
                            canManage = true,
                            attendanceEnabled = true,
                            busy = false,
                            onMarkAttendance = { status -> selectedAttendance.add(status) }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(memberName).assertIsDisplayed()
        composeRule.onNodeWithText("Available").assertIsDisplayed()
        assertTouchTargetAtLeast48Dp("Attended")
        assertTouchTargetAtLeast48Dp("Absent")

        composeRule.onNodeWithText("Attended").performClick()
        composeRule.onNodeWithText("Absent").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("attended", "absent"), selectedAttendance)
        }
    }

    private fun assertTouchTargetAtLeast48Dp(label: String) {
        val heightPx = composeRule.onNodeWithText(label)
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val minimumHeightPx = with(composeRule.density) { 48.dp.toPx() }
        assertTrue(
            "$label should have a touch target at least 48dp high.",
            heightPx >= minimumHeightPx
        )
    }

    private fun assignment(responseStatus: String) = RehearsalAssignmentDto(
        id = "assignment-1",
        organizationId = "organization-1",
        rehearsalId = "rehearsal-1",
        memberId = "member-1",
        responseStatus = responseStatus,
        responseNote = "",
        assignedBy = "leader",
        respondedAt = null,
        createdAt = "2026-11-28T00:00:00Z"
    )
}
