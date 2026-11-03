package com.unityhub.app.data

import com.unityhub.app.BuildConfig

object SupabaseClientProvider {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()
}
