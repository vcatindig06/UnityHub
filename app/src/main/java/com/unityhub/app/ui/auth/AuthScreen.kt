package com.unityhub.app.ui.auth

import androidx.compose.runtime.Composable

@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    androidx.compose.material3.Text(text = "UnityHub sign in")
}
