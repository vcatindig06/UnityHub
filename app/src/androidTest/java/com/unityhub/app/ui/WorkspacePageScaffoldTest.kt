package com.unityhub.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
                        Box(Modifier.testTag("workspace-bottom-bar")) {
                            WorkspaceBottomNavigationBar(
                                selectedTab = 0,
                                onTabSelected = {},
                                onPrimaryAction = {}
                            )
                        }
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
        assertTrue(
            "The navigation bar must remain compact so it cannot consume the page body.",
            bottomBounds.height <= with(composeRule.density) { 100.dp.toPx() }
        )
    }

    @Test
    fun scaffoldKeepsLongContentReachableAtDoubleFontScaleOnNarrowViewport() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density,
                    fontScale = 2f
                )
            ) {
                MaterialTheme {
                    WorkspacePageScaffold(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .testTag("narrow-scaffold"),
                        topBar = {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .testTag("narrow-top-bar")
                            ) {
                                Text("UnityHub worship team workspace")
                                Text("Service planning and team coordination")
                            }
                        },
                        bottomBar = {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .testTag("narrow-bottom-bar")
                            ) {
                                Text("Main navigation")
                                Button(
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Open Team")
                                }
                            }
                        }
                    ) { innerPadding ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                "Upcoming service assignments",
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag("large-content-heading")
                            )
                            Text("A long service title that should wrap instead of being clipped on a narrow phone.")
                            Button(
                                onClick = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("large-content-action")
                            ) {
                                Text("Review service assignments")
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("narrow-top-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("narrow-bottom-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("large-content-heading").assertIsDisplayed()
        composeRule.onNodeWithText("Open Team").assertIsDisplayed()
        composeRule.onNodeWithText("Review service assignments").performScrollTo().assertIsDisplayed()

        val root = composeRule.onNodeWithTag("narrow-scaffold").fetchSemanticsNode().boundsInRoot
        val top = composeRule.onNodeWithTag("narrow-top-bar").fetchSemanticsNode().boundsInRoot
        val bottom = composeRule.onNodeWithTag("narrow-bottom-bar").fetchSemanticsNode().boundsInRoot
        val tolerancePx = with(composeRule.density) { 1.dp.toPx() }

        assertTrue("The narrow scaffold must stay within a 320 dp viewport.", root.width <= with(composeRule.density) { 320.dp.toPx() } + tolerancePx)
        assertTrue("The top bar must not exceed the narrow viewport.", top.left >= root.left - tolerancePx && top.right <= root.right + tolerancePx)
        assertTrue("The bottom bar must not exceed the narrow viewport.", bottom.left >= root.left - tolerancePx && bottom.right <= root.right + tolerancePx)
    }

    @Test
    fun scaffoldActionsExposeAccessibleLabelsAndHeadingSemantics() {
        composeRule.setContent {
            MaterialTheme {
                WorkspacePageScaffold(
                    modifier = Modifier.testTag("accessible-scaffold"),
                    topBar = {
                        Text(
                            "Notifications",
                            modifier = Modifier
                                .semantics { heading() }
                                .testTag("notifications-heading")
                        )
                    }
                ) { innerPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = {},
                            modifier = Modifier.testTag("open-team-action")
                        ) {
                            Text("Open Team")
                        }
                        IconButton(
                            onClick = {},
                            modifier = Modifier.testTag("notifications-action")
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Notifications, 2 unread"
                            )
                        }
                    }
                }
            }
        }

        val headingNode = composeRule.onNodeWithTag("notifications-heading")
            .fetchSemanticsNode()
        assertTrue(
            "The notification title must be announced as a heading.",
            headingNode.config.contains(SemanticsProperties.Heading)
        )
        composeRule.onNodeWithTag("open-team-action").assertHasClickAction()
        composeRule.onNodeWithText("Open Team").assertIsDisplayed()
        composeRule.onNodeWithTag("notifications-action")
            .assertHasClickAction()
            .assertContentDescriptionEquals("Notifications, 2 unread")

        val actionRole = composeRule.onNodeWithTag("open-team-action")
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Role]
        assertTrue("Primary actions must expose button semantics.", actionRole == Role.Button)
    }
}
