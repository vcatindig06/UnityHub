package com.unityhub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.ui.UnityHubApp
import com.unityhub.app.ui.auth.AuthScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import com.unityhub.app.ui.theme.UnityHubTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SupabaseClientProvider.isConfigured) {
            runCatching { SupabaseClientProvider.client.handleDeeplinks(intent) }
        }
        setContent {
            UnityHubTheme {
                var authenticated by rememberSaveable { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (SupabaseClientProvider.isConfigured) {
                        authenticated = runCatching {
                            SupabaseClientProvider.client.auth.currentSessionOrNull() != null
                        }.getOrDefault(false)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (authenticated) {
                        UnityHubApp(
                            onSignOut = {
                                scope.launch {
                                    runCatching { SupabaseClientProvider.client.auth.signOut() }
                                    authenticated = false
                                }
                            }
                        )
                    } else {
                        AuthScreen(onAuthenticated = { authenticated = true })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (SupabaseClientProvider.isConfigured) {
            runCatching { SupabaseClientProvider.client.handleDeeplinks(intent) }
        }
    }
}
