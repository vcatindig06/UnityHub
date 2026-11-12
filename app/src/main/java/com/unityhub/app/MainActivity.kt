package com.unityhub.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.unityhub.app.data.DevicePushTokenRepository
import com.unityhub.app.data.OfflineServiceCache
import com.unityhub.app.data.OfflineSongCache
import com.unityhub.app.data.OfflineWorkspaceCache
import com.unityhub.app.data.PendingServiceAssignmentResponseQueue
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.push.UnityHubFirebase
import com.unityhub.app.sync.OfflineSyncScheduler
import com.unityhub.app.sync.OfflineSyncStatusStore
import com.unityhub.app.push.UnityHubFirebaseMessagingService
import com.unityhub.app.push.UnityHubNotificationManager
import com.unityhub.app.push.UnityHubPushNavigation
import com.unityhub.app.ui.UnityHubApp
import com.unityhub.app.ui.AppUpdateController
import com.unityhub.app.ui.components.UnityHubLoadingScreen
import com.unityhub.app.ui.auth.AuthScreen
import com.unityhub.app.ui.auth.PasswordRecoveryScreen
import com.unityhub.app.ui.theme.UnityHubTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingPushNavigation by mutableStateOf<UnityHubPushNavigation?>(null)
    private var passwordRecoveryMode by mutableStateOf(false)
    private var passwordRecoverySessionReady by mutableStateOf(false)
    private var passwordRecoveryFailed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        capturePushNavigation(intent)
        capturePasswordRecovery(intent)
        handleAuthenticationDeepLink(intent)
        UnityHubNotificationManager.createChannel(this)

        setContent {
            UnityHubTheme {
                AppUpdateController()
                var authenticated by rememberSaveable { mutableStateOf(false) }
                var authenticationReady by rememberSaveable { mutableStateOf(false) }
                var showNotificationPermissionPrompt by rememberSaveable { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { showNotificationPermissionPrompt = false }
                )

                LaunchedEffect(Unit) {
                    if (!SupabaseClientProvider.isConfigured) {
                        authenticationReady = true
                    } else {
                        SupabaseClientProvider.client.auth.sessionStatus.collect { status ->
                            when (status) {
                                is SessionStatus.Authenticated -> {
                                    authenticated = true
                                    authenticationReady = true
                                }
                                is SessionStatus.NotAuthenticated -> {
                                    authenticated = false
                                    authenticationReady = true
                                }
                                is SessionStatus.RefreshFailure -> {
                                    authenticated = false
                                    authenticationReady = true
                                }
                                SessionStatus.Initializing -> Unit
                            }
                        }
                    }
                }

                LaunchedEffect(authenticated, passwordRecoveryMode) {
                    if (authenticated && !passwordRecoveryMode && UnityHubFirebase.initialize(this@MainActivity)) {
                        syncFcmToken()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            showNotificationPermissionPrompt = true
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!authenticationReady) {
                        UnityHubLoadingScreen()
                    } else if (passwordRecoveryMode) {
                        PasswordRecoveryScreen(
                            isLoading = !passwordRecoveryFailed &&
                                (!passwordRecoverySessionReady || !authenticated),
                            canUpdatePassword = passwordRecoverySessionReady && authenticated,
                            onPasswordUpdated = { passwordRecoveryMode = false },
                            onBackToSignIn = { passwordRecoveryMode = false }
                        )
                    } else if (authenticated) {
                        UnityHubApp(
                            pushNavigation = pendingPushNavigation,
                            onPushNavigationHandled = { pendingPushNavigation = null },
                            onSignOut = {
                                scope.launch {
                                    runCatching {
                                        val client = SupabaseClientProvider.client
                                        val userId = client.auth.currentUserOrNull()?.id
                                        val token = getSharedPreferences(
                                            UnityHubFirebaseMessagingService.PUSH_PREFERENCES,
                                            MODE_PRIVATE
                                        ).getString(
                                            UnityHubFirebaseMessagingService.KEY_FCM_TOKEN,
                                            null
                                        )
                                        if (!userId.isNullOrBlank() && !token.isNullOrBlank()) {
                                            runCatching {
                                                DevicePushTokenRepository(client).unregisterToken(userId, token)
                                            }
                                        }
                                        if (!userId.isNullOrBlank()) {
                                            OfflineSyncScheduler.cancel(applicationContext, userId)
                                            runCatching {
                                                PendingServiceAssignmentResponseQueue(applicationContext).clearUser(userId)
                                            }
                                            runCatching { OfflineSyncStatusStore.clearUser(applicationContext, userId) }
                                            runCatching { OfflineSongCache(applicationContext).clearUser(userId) }
                                            runCatching { OfflineServiceCache(applicationContext).clearUser(userId) }
                                            runCatching { OfflineWorkspaceCache(applicationContext).clear(userId) }
                                        }
                                    }
                                    runCatching { SupabaseClientProvider.client.auth.signOut() }
                                    authenticated = false
                                }
                            }
                        )
                    } else {
                        AuthScreen(onAuthenticated = { authenticated = true })
                    }
                }

                if (showNotificationPermissionPrompt) {
                    AlertDialog(
                        onDismissRequest = { showNotificationPermissionPrompt = false },
                        title = { Text("Enable team alerts") },
                        text = {
                            Text(
                                "Allow UnityHub to notify you about rehearsal invitations, availability updates, team assignments, and service reminders, even when the app is closed."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showNotificationPermissionPrompt = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                }
                            ) {
                                Text("Enable")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNotificationPermissionPrompt = false }) {
                                Text("Not now")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun capturePushNavigation(source: Intent?) {
        if (source == null) return
        val notificationId = source.getStringExtra(UnityHubNotificationManager.EXTRA_NOTIFICATION_ID)
            ?: source.getStringExtra("notification_id")
        val servicePlanId = source.getStringExtra(UnityHubNotificationManager.EXTRA_SERVICE_PLAN_ID)
            ?: source.getStringExtra("service_plan_id")
        val rehearsalId = source.getStringExtra(UnityHubNotificationManager.EXTRA_REHEARSAL_ID)
            ?: source.getStringExtra("rehearsal_id")
        if (!notificationId.isNullOrBlank() || !servicePlanId.isNullOrBlank() || !rehearsalId.isNullOrBlank()) {
            pendingPushNavigation = UnityHubPushNavigation(notificationId, servicePlanId, rehearsalId)
        }
    }

    private fun capturePasswordRecovery(source: Intent?) {
        val deepLink = source?.data ?: return
        if (
            deepLink.scheme == "unityhub" &&
            deepLink.host == "auth" &&
            (
                deepLink.getQueryParameter("mode") == "recovery" ||
                    deepLink.getQueryParameter("type") == "recovery"
            )
        ) {
            passwordRecoveryMode = true
            passwordRecoverySessionReady = false
            passwordRecoveryFailed = false
        }
    }

    private fun handleAuthenticationDeepLink(source: Intent?) {
        val deepLinkIntent = source ?: return
        if (!passwordRecoveryMode) {
            if (SupabaseClientProvider.isConfigured) {
                runCatching { SupabaseClientProvider.client.handleDeeplinks(deepLinkIntent) }
            }
            return
        }

        val recoveryUri = deepLinkIntent.data
        val recoveryCode = recoveryUri?.getQueryParameter("code")
        val recoveryError = recoveryUri?.getQueryParameter("error")
        val recoveryErrorCode = recoveryUri?.getQueryParameter("error_code")
        if (
            !SupabaseClientProvider.isConfigured ||
            recoveryCode.isNullOrBlank() ||
            !recoveryError.isNullOrBlank() ||
            !recoveryErrorCode.isNullOrBlank()
        ) {
            passwordRecoveryFailed = true
            return
        }

        runCatching {
            SupabaseClientProvider.client.handleDeeplinks(
                deepLinkIntent,
                onSessionSuccess = { passwordRecoverySessionReady = true },
                onError = { passwordRecoveryFailed = true }
            )
        }.onFailure {
            passwordRecoveryFailed = true
        }
    }

    private fun syncFcmToken() {
        if (!UnityHubFirebase.isConfigured || !SupabaseClientProvider.isConfigured) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            getSharedPreferences(
                UnityHubFirebaseMessagingService.PUSH_PREFERENCES,
                MODE_PRIVATE
            ).edit()
                .putString(UnityHubFirebaseMessagingService.KEY_FCM_TOKEN, token)
                .apply()

            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    val client = SupabaseClientProvider.client
                    val userId = client.auth.currentUserOrNull()?.id ?: return@runCatching
                    DevicePushTokenRepository(client).registerToken(userId, token)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        capturePushNavigation(intent)
        capturePasswordRecovery(intent)
        handleAuthenticationDeepLink(intent)
    }
}
