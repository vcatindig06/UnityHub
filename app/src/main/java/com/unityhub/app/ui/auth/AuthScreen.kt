package com.unityhub.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.SupabaseClientProvider
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().imePadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("UnityHub", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(if (creating) "Create your account" else "Sign in to your worship team workspace")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(emailInput, { emailInput = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(passwordInput, { passwordInput = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        if (creating) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(confirmInput, { confirmInput = it }, Modifier.fillMaxWidth(), label = { Text("Confirm password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Text("At least 12 characters; maximum 72 UTF-8 bytes.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            onClick = {
                notice = ""
                if (!SupabaseClientProvider.isConfigured) notice = "Add your Supabase project URL and publishable key to local.properties."
                else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput).matches()) notice = "Enter a valid email address."
                else if (creating && (passwordInput.length < 12 || passwordInput.encodeToByteArray().size > 72)) notice = "Use at least 12 characters and no more than 72 UTF-8 bytes."
                else if (creating && passwordInput != confirmInput) notice = "The passwords do not match."
                else if (passwordInput.isBlank()) notice = "Enter your password."
                else scope.launch {
                    loading = true
                    try {
                        if (creating) {
                            SupabaseClientProvider.client.auth.signUpWith(Email) {
                                email = emailInput
                                password = passwordInput
                            }
                            notice = "If registration is accepted, check your email for a confirmation link before signing in."
                            creating = false
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
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (creating) "Create account" else "Sign in")
        }
        if (notice.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(notice, color = MaterialTheme.colorScheme.secondary)
        }
        TextButton(onClick = { creating = !creating; notice = "" }) {
            Text(if (creating) "Already registered? Sign in" else "Create an account")
        }
    }
}
