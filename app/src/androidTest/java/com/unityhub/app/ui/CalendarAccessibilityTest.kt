package com.unityhub.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unityhub.app.data.RehearsalTimeFilter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun serviceCalendarAnnouncesDateAndEmptyServiceCountForSelectedDay() {
        composeRule.setContent {
            MaterialTheme {
                ServiceCalendarContent(
                    plans = emptyList(),
                    mode = "month",
                    selectedDateText = "2026-10-28",
                    displayedMonthText = "2026-10",
                    onSelectedDateChange = {},
                    onDisplayedMonthChange = {},
                    onOpenServicePlan = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Wednesday, October 28, 2026, No planned services"
        ).assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun rehearsalCalendarAnnouncesDateAndEmptyRehearsalCountForSelectedDay() {
        composeRule.setContent {
            MaterialTheme {
                RehearsalCalendarContent(
                    rehearsals = emptyList(),
                    servicePlans = emptyList(),
                    timeFilter = RehearsalTimeFilter.ALL,
                    selectedDateText = "2026-10-28",
                    displayedMonthText = "2026-10",
                    onSelectedDateChange = {},
                    onDisplayedMonthChange = {},
                    onOpenRehearsal = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Wednesday, October 28, 2026, No rehearsals"
        ).assertIsDisplayed().assertIsSelected()
    }
}
