package com.unityhub.app.ui.auth

internal fun isValidUnityHubPassword(password: String): Boolean =
    password.length >= 12 && password.encodeToByteArray().size <= 72
