package com.unityhub.app.ui.auth

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.OAuthProvider
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
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
private val Emerald800 = Color(0xFF065F46)
private val Emerald700 = Color(0xFF047857)
private val Emerald600 = Color(0xFF059669)
private val Emerald500 = Color(0xFF10B981)
private val Emerald400 = Color(0xFF34D399)
private val Emerald300 = Color(0xFF6EE7B7)

@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    var displayNameInput by rememberSaveable { mutableStateOf("") }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var confirmInput by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var keepSignedIn by rememberSaveable {
        mutableStateOf(SupabaseClientProvider.readRememberSession(context))
    }
    var notice by rememberSaveable { mutableStateOf("") }
    var noticeIsError by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
        SupabaseClientProvider.setRememberSession(context, keepSignedIn)
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
                    .background(Color.White)
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
                        Text(
                            "UnityHub",
                            color = Slate900,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.6).sp
                        )
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
                                    onClick = { startOAuth(OAuthProvider("google"), "Google") }
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
                                        tint = if (focused) Emerald600 else Slate400,
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
                                    tint = if (focused) Emerald600 else Slate400,
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
                                    tint = if (focused) Emerald600 else Slate400,
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
                                        tint = if (focused) Emerald600 else Slate400,
                                        modifier = Modifier.size(19.dp)
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                visualTransformation = PasswordVisualTransformation(),
                                enabled = !loading
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Use at least 12 characters and no more than 72 UTF-8 bytes.",
                                color = Slate500,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }

                        if (!creating) {
                            Spacer(Modifier.height(13.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = keepSignedIn,
                                        onCheckedChange = {
                                            keepSignedIn = it
                                            SupabaseClientProvider.setRememberSession(context, it)
                                        },
                                        enabled = !loading,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Emerald600,
                                            uncheckedColor = Slate400,
                                            checkmarkColor = Color.White
                                        ),
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "Keep me signed in",
                                        color = Slate600,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
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
                                                        redirectUrl = "unityhub://auth"
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
                                        color = Emerald700,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (notice.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                color = if (noticeIsError) Color(0xFFFFF1F2) else Color(0xFFECFDF5),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    notice,
                                    color = if (noticeIsError) Color(0xFF9F1239) else Emerald800,
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
                                creating && (
                                    passwordInput.length < 12 ||
                                        passwordInput.encodeToByteArray().size > 72
                                    ) ->
                                    showNotice(
                                        "Use at least 12 characters and no more than 72 UTF-8 bytes.",
                                        true
                                    )
                                creating && passwordInput != confirmInput ->
                                    showNotice("The passwords do not match.", true)
                                passwordInput.isBlank() ->
                                    showNotice("Enter your password.", true)
                                else -> scope.launch {
                                    loading = true
                                    SupabaseClientProvider.setRememberSession(context, keepSignedIn)
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
                                    } catch (_: Exception) {
                                        showNotice(
                                            if (creating) {
                                                "We could not create your account. Check your details and connection."
                                            } else {
                                                "Sign-in failed. Check your email, password, and email confirmation."
                                            },
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
                                },
                                enabled = !loading,
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (creating) "Sign in" else "Sign up free",
                                    color = Emerald700,
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
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.linearGradient(
                    listOf(Emerald500, Emerald700, Emerald800)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Groups,
            contentDescription = "UnityHub",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
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
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            label,
            color = Slate700,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (focused) Modifier.border(
                        4.dp,
                        Emerald500.copy(alpha = 0.12f),
                        shape
                    ) else Modifier
                )
                .clip(shape)
                .background(if (focused) Color.White else Slate50.copy(alpha = 0.78f))
                .border(
                    width = 1.dp,
                    color = if (focused) Emerald500 else Slate200,
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
                cursorBrush = SolidColor(Emerald600),
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
                ambientColor = Emerald500.copy(alpha = 0.30f),
                spotColor = Emerald500.copy(alpha = 0.38f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(Emerald700, Emerald800))
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
            .background(Slate900)
            .clip(RoundedCornerShape(0.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 52.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.035f),
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.035f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += step
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Emerald500.copy(alpha = 0.28f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.12f),
                    radius = size.width * 0.60f
                ),
                radius = size.width * 0.60f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Emerald700.copy(alpha = 0.30f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.05f, size.height * 0.85f),
                    radius = size.width * 0.52f
                ),
                radius = size.width * 0.52f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.05f, size.height * 0.85f)
            )
            val dotStep = 22.dp.toPx()
            var dotX = 0f
            while (dotX <= size.width) {
                var dotY = 0f
                while (dotY <= size.height) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.045f),
                        radius = 0.8.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(dotX, dotY)
                    )
                    dotY += dotStep
                }
                dotX += dotStep
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 46.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.07f),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(Emerald400, CircleShape)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "MADE FOR WORSHIP TEAMS",
                        color = Emerald300,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.0.sp
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(25.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "One team.\nOne purpose.\nIn unity.",
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
                                listOf(Emerald700, Emerald600, Emerald500)[index]
                            )
                            .border(2.dp, Slate900, CircleShape),
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
                            Text("★", color = Emerald400, fontSize = 12.sp)
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
                        .background(Emerald500.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = Emerald300,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Your next service", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("SERVICE PLAN PREVIEW", color = Slate400, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Surface(
                    color = Emerald500.copy(alpha = 0.16f),
                    shape = CircleShape
                ) {
                    Text(
                        "READY",
                        color = Emerald300,
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
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Emerald300, modifier = Modifier.size(17.dp))
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
                                            Emerald400.copy(alpha = 0.32f),
                                            Emerald700.copy(alpha = 0.12f)
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
                            Icon(Icons.Filled.Groups, contentDescription = null, tint = Emerald300, modifier = Modifier.size(20.dp))
                        } else {
                            Box(Modifier.size(16.dp).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape))
                        }
                    }
                }
            }
        }
    }
}
