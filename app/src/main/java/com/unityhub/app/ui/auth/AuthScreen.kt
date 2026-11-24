package com.unityhub.app.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.ui.components.UnityHubLogoMark
import com.unityhub.app.ui.components.UnityHubWordmark
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.OAuthProvider
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val Slate950 = Color(0xFF12291E)
private val Slate900 = Color(0xFF193B2B)
private val Slate800 = Color(0xFF274634)
private val Slate700 = Color(0xFF405B49)
private val Slate600 = Color(0xFF566D5C)
private val Slate500 = Color(0xFF667A69)
private val Slate400 = Color(0xFF819082)
private val Slate300 = Color(0xFFBEC6B4)
private val Slate200 = Color(0xFFD9DDCE)
private val Slate100 = Color(0xFFECEEE2)
private val Slate50 = Color(0xFFF8F4E9)
private val ForestGreenDeep = Color(0xFF193B2B)
private val ForestGreen = Color(0xFF315C46)
private val Terracotta = Color(0xFFA7523B)
private val TerracottaSoft = Color(0xFFC47457)
private val MutedGold = Color(0xFFB59A5A)
private val MutedGoldBright = Color(0xFFC0A86F)
private val ButterCream = Color(0xFFF5EBD8)
private val ButterCreamLight = Color(0xFFFFF9EF)

private val GoogleOAuthProvider = object : OAuthProvider() {
    override val name: String = "google"
}

@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    var displayNameInput by rememberSaveable { mutableStateOf("") }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var confirmInput by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmValidationTriggered by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf("") }
    var noticeIsError by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val passwordMismatch = creating &&
        (confirmInput.isNotEmpty() || confirmValidationTriggered) &&
        passwordInput != confirmInput

    BackHandler(enabled = creating) {
        creating = false
        notice = ""
        noticeIsError = false
        passwordInput = ""
        confirmInput = ""
        passwordVisible = false
        confirmPasswordVisible = false
        confirmValidationTriggered = false
    }

    fun showNotice(message: String, isError: Boolean = false) {
        notice = message
        noticeIsError = isError
    }

    fun validEmail(): Boolean =
        android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput.trim()).matches()

    fun startOAuth(provider: OAuthProvider, providerLabel: String) {
        if (!SupabaseClientProvider.isConfigured) {
            showNotice("UnityHub sign-in is not configured yet. Please contact your administrator.", true)
            return
        }
        scope.launch {
            loading = true
            notice = ""
            try {
                SupabaseClientProvider.client.auth.signInWith(
                    provider,
                    redirectUrl = "unityhub://auth"
                )
            } catch (_: Exception) {
                showNotice(
                    "$providerLabel sign-in could not start. Check that this provider is enabled in the UnityHub Supabase project.",
                    true
                )
            } finally {
                loading = false
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ButterCreamLight)
            .imePadding()
    ) {
        val wideLayout = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(if (wideLayout) 1.04f else 1f)
                    .fillMaxHeight()
                    .background(ButterCreamLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (wideLayout) 44.dp else 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UnityHubMark(Modifier.size(40.dp))
                        Spacer(Modifier.width(11.dp))
                        UnityHubWordmark(baseColor = Slate900, fontSize = 21.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .align(Alignment.Center)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = if (wideLayout) 44.dp else 26.dp,
                                vertical = 28.dp
                            )
                    ) {
                        Text(
                            if (creating) "Create your account" else "Welcome back",
                            color = Slate900,
                            fontSize = 32.sp,
                            lineHeight = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.1).sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (creating) "Set up your UnityHub account."
                            else "Sign in to your team workspace.",
                            color = Slate500,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )

                        if (!creating) {
                            Spacer(Modifier.height(26.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SocialAuthButton(
                                    label = "Google",
                                    modifier = Modifier.weight(1f),
                                    enabled = !loading,
                                    onClick = { startOAuth(GoogleOAuthProvider, "Google") }
                                )
                                SocialAuthButton(
                                    label = "GitHub",
                                    modifier = Modifier.weight(1f),
                                    enabled = !loading,
                                    onClick = { startOAuth(Github, "GitHub") }
                                )
                            }

                            Spacer(Modifier.height(23.dp))
                            AuthDivider()
                            Spacer(Modifier.height(21.dp))
                        } else {
                            Spacer(Modifier.height(25.dp))
                        }

                        if (creating) {
                            AuthField(
                                label = "Full name",
                                value = displayNameInput,
                                onValueChange = { displayNameInput = it },
                                placeholder = "Your name",
                                                    keyboardOptions = KeyboardOptions(
                                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next
                                ),
                                enabled = !loading
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        AuthField(
                            label = "Email address",
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            placeholder = "you@example.com",
                                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            enabled = !loading
                        )

                        Spacer(Modifier.height(16.dp))
                        AuthField(
                            label = "Password",
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            placeholder = "Enter your password",
                                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (creating) ImeAction.Next else ImeAction.Done
                            ),
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    enabled = !loading,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        tint = Slate400,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            },
                            enabled = !loading
                        )

                        if (creating) {
                            Spacer(Modifier.height(16.dp))
                            AuthField(
                                label = "Confirm password",
                                value = confirmInput,
                                onValueChange = { confirmInput = it },
                                placeholder = "Re-enter your password",
                                                    keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                visualTransformation = if (confirmPasswordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                                        enabled = !loading,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            if (confirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (confirmPasswordVisible) "Hide confirm password" else "Show confirm password",
                                            tint = if (passwordMismatch) MaterialTheme.colorScheme.error else Slate400,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                },
                                isError = passwordMismatch,
                                enabled = !loading
                            )
                            if (passwordMismatch) {
                                Text(
                                    "Passwords do not match. Re-enter the same password.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            } else {
                                Text(
                                    "Use at least 12 characters and no more than 72 UTF-8 bytes.",
                                    color = Slate500,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        if (!creating) {
                            Spacer(Modifier.height(13.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        notice = ""
                                        when {
                                            !SupabaseClientProvider.isConfigured ->
                                                showNotice("UnityHub sign-in is not configured yet.", true)
                                            !validEmail() ->
                                                showNotice("Enter your account email first.", true)
                                            else -> scope.launch {
                                                loading = true
                                                try {
                                                    SupabaseClientProvider.client.auth.resetPasswordForEmail(
                                                        email = emailInput.trim(),
                                                        redirectUrl = "unityhub://auth?mode=recovery"
                                                    )
                                                    showNotice(
                                                        "If an account exists for that email, a password reset link has been sent."
                                                    )
                                                } catch (_: Exception) {
                                                    showNotice(
                                                        "We could not send the reset link. Check your email address and connection.",
                                                        true
                                                    )
                                                } finally {
                                                    loading = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !loading,
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "Forgot password?",
                                        color = ForestGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (notice.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                color = if (noticeIsError) Color(0xFFF9DEDC) else Color(0xFFFFF1E4),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    notice,
                                    color = if (noticeIsError) Color(0xFF8B2E25) else ForestGreenDeep,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        GradientAuthButton(
                            label = if (creating) "Create account" else "Sign in",
                            loading = loading,
                            enabled = !loading
                        ) {
                            notice = ""
                            if (creating) confirmValidationTriggered = true
                            when {
                                !SupabaseClientProvider.isConfigured ->
                                    showNotice(
                                        "UnityHub sign-in is not configured yet. Please contact your administrator.",
                                        true
                                    )
                                creating && displayNameInput.trim().length !in 2..80 ->
                                    showNotice("Enter your name using 2 to 80 characters.", true)
                                !validEmail() ->
                                    showNotice("Enter a valid email address.", true)
                                creating && !isValidUnityHubPassword(passwordInput) ->
                                    showNotice(
                                        "Use at least 12 characters and no more than 72 UTF-8 bytes.",
                                        true
                                    )
                                creating && passwordInput != confirmInput ->
                                    showNotice("Passwords do not match. Re-enter the same password.", true)
                                passwordInput.isBlank() ->
                                    showNotice("Enter your password.", true)
                                else -> scope.launch {
                                    loading = true
                                    try {
                                        if (creating) {
                                            SupabaseClientProvider.client.auth.signUpWith(Email) {
                                                email = emailInput.trim()
                                                password = passwordInput
                                                data = buildJsonObject {
                                                    put("display_name", displayNameInput.trim())
                                                }
                                            }
                                            creating = false
                                            displayNameInput = ""
                                            passwordInput = ""
                                            confirmInput = ""
                                            passwordVisible = false
                                            showNotice(
                                                "Your registration was received. Check your email for a confirmation link before signing in."
                                            )
                                        } else {
                                            SupabaseClientProvider.client.auth.signInWith(Email) {
                                                email = emailInput.trim()
                                                password = passwordInput
                                            }
                                            onAuthenticated()
                                        }
                                    } catch (exception: Exception) {
                                        showNotice(
                                            authFailureMessage(
                                                errorMessage = exception.message.orEmpty(),
                                                creatingAccount = creating
                                            ),
                                            true
                                        )
                                    } finally {
                                        loading = false
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (creating) "Already have an account? " else "Don't have an account? ",
                                color = Slate500,
                                fontSize = 13.sp
                            )
                            TextButton(
                                onClick = {
                                    creating = !creating
                                    notice = ""
                                    noticeIsError = false
                                    passwordInput = ""
                                    confirmInput = ""
                                    passwordVisible = false
                                    confirmPasswordVisible = false
                                    confirmValidationTriggered = false
                                },
                                enabled = !loading,
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (creating) "Sign in" else "Sign up free",
                                    color = ForestGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "By continuing, you agree to use UnityHub responsibly with your worship team.",
                            color = Slate400,
                            fontSize = 10.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            if (wideLayout) {
                BrandPanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun UnityHubMark(modifier: Modifier = Modifier) {
    UnityHubLogoMark(modifier = modifier, contentDescription = "UnityHub")
}

@Composable
private fun SocialAuthButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Slate200),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ButterCreamLight,
            contentColor = Slate700,
            disabledContainerColor = Slate50
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AuthDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Slate200))
        Text(
            "  OR CONTINUE WITH EMAIL  ",
            color = Slate500,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
        Box(Modifier.weight(1f).height(1.dp).background(Slate200))
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: (@Composable (Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingContent: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            label,
            color = if (isError) MaterialTheme.colorScheme.error else Slate700,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (focused && !isError) Modifier.border(
                        4.dp,
                        TerracottaSoft.copy(alpha = 0.12f),
                        shape
                    ) else Modifier
                )
                .clip(shape)
                .background(if (focused) ButterCreamLight else Slate50.copy(alpha = 0.78f))
                .border(
                    width = 1.dp,
                    color = if (isError) MaterialTheme.colorScheme.error else if (focused) TerracottaSoft else Slate200,
                    shape = shape
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon(focused)
                Spacer(Modifier.width(8.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    color = Slate900,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif
                ),
                cursorBrush = SolidColor(Terracotta),
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                color = Slate400,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (trailingContent != null) {
                Spacer(Modifier.width(8.dp))
                trailingContent()
            }
        }
    }
}

@Composable
private fun GradientAuthButton(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(ForestGreen)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = ButterCreamLight,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                label,
                color = ButterCreamLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BrandPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ForestGreenDeep)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 44.dp, vertical = 44.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UnityHubLogoMark(Modifier.size(42.dp), contentDescription = null)
                UnityHubWordmark(baseColor = ButterCreamLight, fontSize = 20.sp)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(3.dp)
                        .background(Terracotta)
                )
                Text(
                    "Plan. Lead. Worship.",
                    color = ButterCreamLight,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    letterSpacing = (-0.8).sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Songs, services and team coordination in one workspace.",
                    color = Slate300,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.widthIn(max = 390.dp)
                )
            }

            Text(
                "UNITYHUB",
                color = Slate400,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }
    }
}

