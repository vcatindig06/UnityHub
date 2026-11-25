package com.unityhub.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared full-window shell for onboarding and the signed-in workspace.
 *
 * Keeping the full-size constraint here prevents the app bars, content, and
 * bottom navigation from being measured as a content-sized group.
 */
@Composable
internal fun WorkspacePageScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = topBar,
        bottomBar = bottomBar,
        content = content
    )
}
