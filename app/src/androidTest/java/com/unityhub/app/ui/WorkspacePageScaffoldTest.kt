package com.unityhub.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class WorkspacePageScaffoldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scaffoldKeepsContentBetweenTopBarAndBottomNavigation() {
        composeRule.setContent {
            MaterialTheme {
                WorkspacePageScaffold(
                    modifier = Modifier.testTag("workspace-scaffold"),
                    topBar = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("workspace-top-bar")
                        )
                    },
                    bottomBar = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("workspace-bottom-bar")
                        )
                    }
                ) { innerPadding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .testTag("workspace-content")
                    )
                }
            }
        }

        composeRule.onNodeWithTag("workspace-top-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-content").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-bottom-bar").assertIsDisplayed()

        val rootBounds = composeRule.onNodeWithTag("workspace-scaffold")
            .fetchSemanticsNode().boundsInRoot
        val topBounds = composeRule.onNodeWithTag("workspace-top-bar")
            .fetchSemanticsNode().boundsInRoot
        val contentBounds = composeRule.onNodeWithTag("workspace-content")
            .fetchSemanticsNode().boundsInRoot
        val bottomBounds = composeRule.onNodeWithTag("workspace-bottom-bar")
            .fetchSemanticsNode().boundsInRoot
        val tolerancePx = with(composeRule.density) { 2.dp.toPx() }

        assertTrue("The workspace scaffold must occupy the available window.", rootBounds.height > 0f)
        assertTrue("The content must begin below the top app bar.", contentBounds.top >= topBounds.bottom)
        assertTrue("The content must end above the bottom navigation.", contentBounds.bottom <= bottomBounds.top)
        assertTrue(
            "The bottom navigation must stay anchored to the bottom of the workspace.",
            bottomBounds.bottom >= rootBounds.bottom - tolerancePx
        )
    }
}
