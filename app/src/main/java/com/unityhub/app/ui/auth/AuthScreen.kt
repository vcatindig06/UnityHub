package com.unityhub.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    var displayNameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "UnityHub",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(if (creating) "Create your account" else "Sign in to your worship team workspace")
        Spacer(Modifier.height(24.dp))

        if (creating) {
            OutlinedTextField(
                value = displayNameInput,
                onValueChange = { displayNameInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Full name") },
                singleLine = true,
                enabled = !loading
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = emailInput,
            onValueChange = { emailInput = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email address") },
            singleLine = true,
            enabled = !loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            enabled = !loading,
            visualTransformation = PasswordVisualTransformation()
        )

        if (creating) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmInput,
                onValueChange = { confirmInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Confirm password") },
                singleLine = true,
                enabled = !loading,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "At least 12 characters; maximum 72 UTF-8 bytes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(18.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            onClick = {
                notice = ""
                when {
                    !SupabaseClientProvider.isConfigured ->
                        notice = "Add your UnityHub Supabase URL and publishable key to local.properties."
                    creating && displayNameInput.trim().length !in 2..80 ->
                        notice = "Enter your name (2 to 80 characters)."
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput).matches() ->
                        notice = "Enter a valid email address."
                    creating && (passwordInput.length < 12 || passwordInput.encodeToByteArray().size > 72) ->
                        notice = "Use at least 12 characters and no more than 72 UTF-8 bytes."
                    creating && passwordInput != confirmInput ->
                        notice = "The passwords do not match."
                    passwordInput.isBlank() ->
                        notice = "Enter your password."
                    else -> scope.launch {
                        loading = true
                        try {
                            if (creating) {
                                SupabaseClientProvider.client.auth.signUpWith(Email) {
                                    email = emailInput
                                    password = passwordInput
                                    data = buildJsonObject {
                                        put("display_name", displayNameInput.trim())
                                    }
                                }
                                notice = "If registration is accepted, check your email for a confirmation link before signing in."
                                creating = false
                                displayNameInput = ""
                                passwordInput = ""
                                confirmInput = ""
                            } else {
                                SupabaseClientProvider.client.auth.signInWith(Email) {
                                    email = emailInput
                                    password = passwordInput
                                }
                                onAuthenticated()
                            }
                        } catch (_: Exception) {
                            notice = "Unable to complete the request. Check your details, email confirmation, and connection."
                        } finally {
                            loading = false
                        }
                    }
                }
            }
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (creating) "Create account" else "Sign in")
        }

        if (notice.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(notice, color = MaterialTheme.colorScheme.secondary)
        }

        TextButton(
            onClick = {
                creating = !creating
                notice = ""
            },
            enabled = !loading
        ) {
            Text(if (creating) "Already registered? Sign in" else "Create an account")
        }
    }
}
