package com.unityhub.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var creatingAccount by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("UnityHub", style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(if (creatingAccount) "Create your account" else "Sign in to your team workspace")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it.trim() },
            modifier = Modifier.fillMaxWidth(), label = { Text("Email address") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Password") },
            singleLine = true, visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(18.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            onClick = {
                if (!SupabaseClientProvider.isConfigured) {
                    notice = "Supabase setup is required. Add your project URL and publishable key to local.properties."
                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    notice = "Enter a valid email address."
                } else if (creatingAccount && (password.length < 12 || password.encodeToByteArray().size > 72)) {
                    notice = "Use at least 12 characters and no more than 72 UTF-8 bytes."
                } else {
                    scope.launch {
                        loading = true
                        try {
                            if (creatingAccount) {
                                SupabaseClientProvider.client.auth.signUpWith(Email) {
                                    this.email = this@AuthScreen.email
                                    this.password = this@AuthScreen.password
                                }
                                notice = "If registration is accepted, check your email for a confirmation link."
                                creatingAccount = false
                                password = ""
                            } else {
                                SupabaseClientProvider.client.auth.signInWith(Email) {
                                    this.email = this@AuthScreen.email
                                    this.password = this@AuthScreen.password
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
            else Text(if (creatingAccount) "Create account" else "Sign in")
        }
        if (notice.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(notice, color = MaterialTheme.colorScheme.secondary)
        }
        TextButton(onClick = { creatingAccount = !creatingAccount; notice = "" }) {
            Text(if (creatingAccount) "Already registered? Sign in" else "Create an account")
        }
    }
}
