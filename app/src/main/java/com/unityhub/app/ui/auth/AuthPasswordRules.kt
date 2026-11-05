package com.unityhub.app.ui.auth

internal fun isValidUnityHubPassword(password: String): Boolean =
    password.length >= 12 && password.encodeToByteArray().size <= 72

internal fun authFailureMessage(
    errorMessage: String,
    creatingAccount: Boolean
): String {
    val error = errorMessage.lowercase()

    return when {
        error.contains("email_not_confirmed") ||
            error.contains("email not confirmed") ||
            error.contains("email is not confirmed") ->
            "Your email address has not been confirmed yet. Open the confirmation email we sent, then try signing in again."

        error.contains("invalid login credentials") ||
            error.contains("invalid_credentials") ||
            error.contains("invalid email or password") ->
            "The email or password is incorrect. Check both details and try again. New accounts must confirm their email before signing in."

        error.contains("too many requests") ||
            error.contains("rate limit") ||
            error.contains("over_email_send_rate_limit") ->
            "Too many attempts were made. Please wait a little while before trying again."

        error.contains("network") ||
            error.contains("timeout") ||
            error.contains("unable to resolve host") ||
            error.contains("failed to connect") ->
            "UnityHub could not reach the server. Check your internet connection and try again."

        creatingAccount ->
            "We couldn't create your account with those details. Check your email and password, or sign in if you already have an account."

        else ->
            "We couldn't sign you in. Check your email and password, confirm your email if this is a new account, and try again."
    }
}
