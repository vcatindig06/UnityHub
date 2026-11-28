package com.unityhub.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceBottomNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomNavigationStaysCompactAndLeavesWorkspaceContentVisible() {
        composeRule.setContent {
            MaterialTheme {
                val selectedTab = remember { mutableIntStateOf(0) }
                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("workspace-scaffold"),
                    topBar = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("workspace-top-bar")
                        ) {
                            Text("Workspace")
                        }
                    },
                    bottomBar = {
                        WorkspaceBottomNavigationBar(
                            selectedTab = selectedTab.intValue,
                            onTabSelected = { selectedTab.intValue = it },
                            onPrimaryAction = {},
                            modifier = Modifier.testTag("workspace-bottom-navigation")
                        )
                    }
                ) { innerPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .testTag("workspace-content")
                    ) {
                        Text("Dashboard content")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard content").assertIsDisplayed()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Songs").assertIsDisplayed()
        composeRule.onNodeWithText("Services").assertIsDisplayed()
        composeRule.onNodeWithText("Community").assertIsDisplayed()
        composeRule.onNodeWithText("Song").assertIsDisplayed()

        val rootBounds = composeRule.onNodeWithTag("workspace-scaffold")
            .fetchSemanticsNode().boundsInRoot
        val bottomBounds = composeRule.onNodeWithTag("workspace-bottom-navigation")
            .fetchSemanticsNode().boundsInRoot
        val tolerancePx = with(composeRule.density) { 2.dp.toPx() }
        // Material 3 includes the OS navigation inset in the measured bottom-bar bounds.
        val maxNavigationAndSystemInsetHeightPx = with(composeRule.density) { 120.dp.toPx() }

        assertTrue(
            "The workspace bottom navigation must stay compact.",
            bottomBounds.height <= maxNavigationAndSystemInsetHeightPx
        )
        assertTrue(
            "The navigation bar must remain anchored to the bottom of the workspace.",
            bottomBounds.bottom >= rootBounds.bottom - tolerancePx
        )
    }

    @Test
    fun centerActionChangesFromSongToSetlistOnSongsAndRemainsClickable() {
        var primaryActionCount = 0

        composeRule.setContent {
            MaterialTheme {
                val selectedTab = remember { mutableIntStateOf(0) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        WorkspaceBottomNavigationBar(
                            selectedTab = selectedTab.intValue,
                            onTabSelected = { selectedTab.intValue = it },
                            onPrimaryAction = { primaryActionCount += 1 }
                        )
                    }
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding))
                }
            }
        }

        composeRule.onNodeWithText("Song").assertIsDisplayed()
        composeRule.onNodeWithText("Songs").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Setlist").assertIsDisplayed()
        composeRule.onNodeWithText("Setlist").performClick()
        composeRule.runOnIdle {
            assertEquals("The central action should invoke its callback once.", 1, primaryActionCount)
        }
    }
    @Test
    fun bottomNavigationRemainsUsableAtLargeFontScalesOnCompactWidth() {
        for (fontScale in listOf(1.3f, 2.0f)) {
            var primaryActionCount = 0

            composeRule.setContent {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = baseDensity.density,
                        fontScale = fontScale
                    )
                ) {
                    MaterialTheme {
                        val selectedTab = remember { mutableIntStateOf(0) }
                        Scaffold(
                            modifier = Modifier.widthIn(max = 360.dp).fillMaxSize(),
                            bottomBar = {
                                WorkspaceBottomNavigationBar(
                                    selectedTab = selectedTab.intValue,
                                    onTabSelected = { selectedTab.intValue = it },
                                    onPrimaryAction = { primaryActionCount += 1 }
                                )
                            }
                        ) { innerPadding ->
                            Box(Modifier.fillMaxSize().padding(innerPadding))
                        }
                    }
                }
            }

            listOf("Home", "Songs", "Services", "Community", "Song").forEach { label ->
                composeRule.onNodeWithText(label).assertIsDisplayed()
            }
            composeRule.onNodeWithText("Songs").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Setlist").assertIsDisplayed()
            composeRule.onNodeWithText("Setlist").performClick()
            composeRule.runOnIdle {
                assertEquals(
                    "The primary action must remain clickable at font scale $fontScale.",
                    1,
                    primaryActionCount
                )
            }
        }
    }

}
