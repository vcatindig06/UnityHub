package com.unityhub.app.ui.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val Slate950 = Color(0xFF020617)
private val Slate900 = Color(0xFF0F172A)
private val Slate800 = Color(0xFF1E293B)
private val Slate700 = Color(0xFF334155)
private val Slate600 = Color(0xFF475569)
private val Slate500 = Color(0xFF64748B)
private val Slate400 = Color(0xFF94A3B8)
private val Slate300 = Color(0xFFCBD5E1)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate50 = Color(0xFFF8FAFC)
private val WarmCrimsonDark = Color(0xFF97152F)
private val WarmCrimson = Color(0xFFBC1F39)
private val WarmCoral = Color(0xFFE34B3F)
private val WarmCoralBright = Color(0xFFFF603E)
private val WarmOrange = Color(0xFFFF9945)
private val WarmAmber = Color(0xFFFFC36A)

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
            .background(Color.White)
            .imePadding()
    ) {
        val wideLayout = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(if (wideLayout) 1.04f else 1f)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White,
                                Color(0xFFFFFBF8),
                                Color(0xFFFFF3EC),
                                Color.White
                            )
                        )
                    )
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
                    Text(
                        "WORSHIP TEAM WORKSPACE",
                        color = Slate500,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.15.sp
                    )
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
                            if (creating) {
                                "Join your worship team and keep every part of ministry in sync."
                            } else {
                                "Sign in to continue planning, organizing songs, and serving together."
                            },
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
                                ) {
                                    Text(
                                        "G",
                                        color = Color(0xFF4285F4),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                SocialAuthButton(
                                    label = "GitHub",
                                    modifier = Modifier.weight(1f),
                                    enabled = !loading,
                                    onClick = { startOAuth(Github, "GitHub") }
                                ) {
                                    Icon(Icons.Filled.Code, contentDescription = null, tint = Slate900)
                                }
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
                                leadingIcon = { focused ->
                                    Icon(
                                        Icons.Filled.Groups,
                                        contentDescription = null,
                                        tint = if (focused) WarmCoral else Slate400,
                                        modifier = Modifier.size(19.dp)
                                    )
                                },
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
                            leadingIcon = { focused ->
                                Icon(
                                    Icons.Filled.MailOutline,
                                    contentDescription = null,
                                    tint = if (focused) WarmCoral else Slate400,
                                    modifier = Modifier.size(19.dp)
                                )
                            },
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
                            leadingIcon = { focused ->
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (focused) WarmCoral else Slate400,
                                    modifier = Modifier.size(19.dp)
                                )
                            },
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
                                leadingIcon = { focused ->
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = if (focused) WarmCoral else Slate400,
                                        modifier = Modifier.size(19.dp)
                                    )
                                },
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
                                            tint = if (passwordMismatch) Color(0xFFDC2626) else Slate400,
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
                                    color = Color(0xFFDC2626),
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
                                Text(
                                    "Your session stays active until you sign out.",
                                    color = Slate500,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
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
                                        color = WarmCrimson,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (notice.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                color = if (noticeIsError) Color(0xFFFFF1F2) else Color(0xFFFFF1E4),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    notice,
                                    color = if (noticeIsError) Color(0xFF9F1239) else WarmCrimsonDark,
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
                                    color = WarmCrimson,
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
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Slate200),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Slate700,
            disabledContainerColor = Slate50
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        icon()
        Spacer(Modifier.width(9.dp))
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
    leadingIcon: @Composable (Boolean) -> Unit,
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
            color = if (isError) Color(0xFFDC2626) else Slate700,
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
                        WarmCoralBright.copy(alpha = 0.12f),
                        shape
                    ) else Modifier
                )
                .clip(shape)
                .background(if (focused) Color.White else Slate50.copy(alpha = 0.78f))
                .border(
                    width = 1.dp,
                    color = if (isError) Color(0xFFDC2626) else if (focused) WarmCoralBright else Slate200,
                    shape = shape
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon(focused)
            Spacer(Modifier.width(11.dp))
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
                cursorBrush = SolidColor(WarmCoral),
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
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = shape,
                ambientColor = WarmCoralBright.copy(alpha = 0.30f),
                spotColor = WarmCoralBright.copy(alpha = 0.38f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(WarmCrimson, WarmCrimsonDark))
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun BrandPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF07080A))
            .clip(RoundedCornerShape(0.dp))
    ) {
        WarmAuroraBackground(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 46.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    UnityHubLogoMark(Modifier.size(46.dp), contentDescription = null)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        UnityHubWordmark(baseColor = Color.White, fontSize = 20.sp)
                        Text(
                            "PLAN · LEAD · WORSHIP · TOGETHER",
                            color = Slate300,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.75.sp
                        )
                    }
                }
                Surface(
                    color = Color.White.copy(alpha = 0.07f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Text(
                        "MADE FOR WORSHIP TEAMS",
                        color = WarmAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.75.sp,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(25.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "One team.\\nOne purpose.\\nIn unity.",
                        color = Color.White,
                        fontSize = 36.sp,
                        lineHeight = 43.sp,
                        letterSpacing = (-1.2).sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Plan every service, organize every song, and keep your people connected in one place.",
                        color = Slate300,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.widthIn(max = 390.dp)
                    )
                }

                ShowcaseCard()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("T", "S", "P").forEachIndexed { index, letter ->
                    Box(
                        modifier = Modifier
                            .offset(x = if (index == 0) 0.dp else (-5).dp)
                            .size(35.dp)
                            .clip(CircleShape)
                            .background(
                                listOf(WarmCrimson, WarmCoral, WarmCoralBright)[index]
                            )
                            .border(2.dp, Color(0xFF071B17), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            letter,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(15.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(5) {
                            Text("★", color = WarmOrange, fontSize = 12.sp)
                        }
                    }
                    Text(
                        "Teams, songs, and service plans in sync.",
                        color = Slate300,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Native Compose aurora using UnityHub's crimson, coral, and amber brand gradient.
 * Each light blade drifts and breathes independently behind the brand-panel content.
 * The mobile form uses the same warm brand accents for a consistent experience.
 */
@Composable
private fun WarmAuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "unityhub-brand-aurora")

    val slowDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing)
        ),
        label = "aurora-slow-drift"
    )
    val middleDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing)
        ),
        label = "aurora-middle-drift"
    )
    val quickDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15_000, easing = LinearEasing)
        ),
        label = "aurora-quick-drift"
    )

    // A fixed seed keeps the fine film grain stable while the light itself moves.
    val grain = remember {
        val random = Random(2_026)
        List(230) {
            GrainPoint(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = 0.35f + random.nextFloat() * 0.55f,
                alpha = 0.018f + random.nextFloat() * 0.025f
            )
        }
    }

    Canvas(modifier = modifier) {
        val panelWidth = size.width
        val panelHeight = size.height
        val twoPi = (2.0 * PI).toFloat()

        val crimson = Color(0xFFD51D3A)
        val coral = Color(0xFFFF583D)
        val amber = Color(0xFFFFB547)

        fun drawBlade(
            start: Offset,
            end: Offset,
            pulse: Float,
            widthFactor: Float
        ) {
            val bladeBrush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.13f to crimson.copy(alpha = 0.12f),
                    0.36f to crimson.copy(alpha = 0.48f),
                    0.58f to coral.copy(alpha = 0.43f),
                    0.79f to amber.copy(alpha = 0.25f),
                    1.00f to Color.Transparent
                ),
                start = start,
                end = end
            )

            // Broad low-opacity bloom provides the soft-focus edge of the blade.
            drawLine(
                brush = bladeBrush,
                start = start,
                end = end,
                strokeWidth = panelWidth * widthFactor * 2.7f,
                cap = StrokeCap.Round,
                alpha = pulse * 0.30f,
                blendMode = BlendMode.Screen
            )
            // Narrower centre line keeps the warm light visible without overpowering text.
            drawLine(
                brush = bladeBrush,
                start = start,
                end = end,
                strokeWidth = panelWidth * widthFactor,
                cap = StrokeCap.Round,
                alpha = pulse * 0.78f,
                blendMode = BlendMode.Screen
            )
        }

        // 22-second upper blade. The first frame starts at full bloom.
        val slowAngle = slowDrift * twoPi
        val slowShift = sin(slowAngle)
        val slowPulse = 0.84f + 0.16f * ((cos(slowAngle) + 1f) / 2f)
        drawBlade(
            start = Offset(
                -panelWidth * 0.18f + slowShift * panelWidth * 0.045f,
                panelHeight * 0.40f + cos(slowAngle) * panelHeight * 0.025f
            ),
            end = Offset(
                panelWidth * 1.16f + slowShift * panelWidth * 0.045f,
                panelHeight * 0.06f - cos(slowAngle) * panelHeight * 0.025f
            ),
            pulse = slowPulse,
            widthFactor = 0.095f * (1f + 0.035f * slowShift)
        )

        // 18-second middle blade moves on its own loop.
        val middleAngle = middleDrift * twoPi
        val middleShift = sin(middleAngle)
        val middlePulse = 0.82f + 0.18f * ((cos(middleAngle) + 1f) / 2f)
        drawBlade(
            start = Offset(
                -panelWidth * 0.16f + middleShift * panelWidth * 0.055f,
                panelHeight * 0.82f + cos(middleAngle) * panelHeight * 0.035f
            ),
            end = Offset(
                panelWidth * 1.14f + middleShift * panelWidth * 0.055f,
                panelHeight * 0.40f - cos(middleAngle) * panelHeight * 0.035f
            ),
            pulse = middlePulse,
            widthFactor = 0.075f * (1f + 0.04f * middleShift)
        )

        // 15-second lower blade adds a slower secondary glow near the panel edge.
        val quickAngle = quickDrift * twoPi
        val quickShift = sin(quickAngle)
        val quickPulse = 0.80f + 0.20f * ((cos(quickAngle) + 1f) / 2f)
        drawBlade(
            start = Offset(
                -panelWidth * 0.12f + quickShift * panelWidth * 0.04f,
                panelHeight * 1.02f + cos(quickAngle) * panelHeight * 0.025f
            ),
            end = Offset(
                panelWidth * 1.10f + quickShift * panelWidth * 0.04f,
                panelHeight * 0.76f - cos(quickAngle) * panelHeight * 0.025f
            ),
            pulse = quickPulse,
            widthFactor = 0.060f * (1f + 0.035f * quickShift)
        )

        // Fine neutral grid.
        val gridStep = 56.dp.toPx()
        var gridX = 0f
        while (gridX <= panelWidth) {
            drawLine(
                color = Color.White.copy(alpha = 0.025f),
                start = Offset(gridX, 0f),
                end = Offset(gridX, panelHeight),
                strokeWidth = 1.dp.toPx()
            )
            gridX += gridStep
        }
        var gridY = 0f
        while (gridY <= panelHeight) {
            drawLine(
                color = Color.White.copy(alpha = 0.025f),
                start = Offset(0f, gridY),
                end = Offset(panelWidth, gridY),
                strokeWidth = 1.dp.toPx()
            )
            gridY += gridStep
        }

        // Subtle film grain: deterministic speckles, not a distracting star field.
        grain.forEach { point ->
            drawCircle(
                color = Color.White.copy(alpha = point.alpha),
                radius = point.radius.dp.toPx(),
                center = Offset(point.x * panelWidth, point.y * panelHeight)
            )
        }

        // Vignette keeps the copy and service preview legible over the light blades.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xB807080A)
                ),
                center = Offset(panelWidth * 0.52f, panelHeight * 0.48f),
                radius = maxOf(panelWidth, panelHeight) * 0.78f
            )
        )
    }
}

private data class GrainPoint(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float
)

@Composable
private fun ShowcaseCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .shadow(28.dp, RoundedCornerShape(20.dp)),
        color = Color.White.copy(alpha = 0.055f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(39.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(WarmCoralBright.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = WarmAmber,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Your next service", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("SERVICE PLAN PREVIEW", color = Slate400, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Surface(
                    color = WarmCoralBright.copy(alpha = 0.16f),
                    shape = CircleShape
                ) {
                    Text(
                        "READY",
                        color = WarmAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Song set and team assignments", color = Slate200, fontSize = 11.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.82f).height(7.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.17f)))
                Box(Modifier.fillMaxWidth(0.56f).height(7.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (index == 0) {
                                    Brush.linearGradient(
                                        listOf(
                                            WarmOrange.copy(alpha = 0.32f),
                                            WarmCrimson.copy(alpha = 0.12f)
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.08f),
                                            Color.White.copy(alpha = 0.035f)
                                        )
                                    )
                                }
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index == 0) {
                            Icon(Icons.Filled.Groups, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(20.dp))
                        } else {
                            Box(Modifier.size(16.dp).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape))
                        }
                    }
                }
            }
        }
    }
}
