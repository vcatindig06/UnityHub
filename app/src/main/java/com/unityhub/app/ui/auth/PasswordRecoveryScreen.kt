package com.unityhub.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unityhub.app.data.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun PasswordRecoveryScreen(
    isLoading: Boolean,
    canUpdatePassword: Boolean,
    onPasswordUpdated: () -> Unit,
    onBackToSignIn: () -> Unit
) {
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }
    var completed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "UnityHub",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                when {
                    isLoading -> {
                        Text(
                            "Verifying your recovery link",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "UnityHub is securely checking the password-reset link.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        CircularProgressIndicator(
                            modifier = Modifier.height(24.dp).width(24.dp),
                            strokeWidth = 2.dp
                        )
                    }

                    !canUpdatePassword -> {
                        Text(
                            "Recovery link unavailable",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "This password-reset link may have expired or already been used. Return to UnityHub. If you are not signed in, request a new link from the sign-in screen.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = onBackToSignIn,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Return to UnityHub")
                        }
                    }

                    completed -> {
                        Text(
                            "Password updated",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Your password has been changed successfully. You can now continue to your UnityHub workspace.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = onPasswordUpdated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continue to UnityHub")
                        }
                    }

                    else -> {
                        Text(
                            "Create a new password",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Choose a new password for your UnityHub account.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = {
                                newPassword = it
                                message = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("New password") },
                            singleLine = true,
                            enabled = !saving,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                message = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Confirm new password") },
                            singleLine = true,
                            enabled = !saving,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        Text(
                            "Use at least 12 characters and no more than 72 UTF-8 bytes.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (message.isNotBlank()) {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = {
                                when {
                                    !isValidUnityHubPassword(newPassword) -> {
                                        message = "Choose a password that meets the length requirements."
                                    }
                                    newPassword != confirmPassword -> {
                                        message = "The passwords do not match."
                                    }
                                    else -> scope.launch {
                                        saving = true
                                        message = ""
                                        try {
                                            SupabaseClientProvider.client.auth.updateUser {
                                                password = newPassword
                                            }
                                            completed = true
                                        } catch (_: Exception) {
                                            message = "We could not update your password. The recovery link may have expired; request a new one and try again."
                                        } finally {
                                            saving = false
                                        }
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Save new password")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
