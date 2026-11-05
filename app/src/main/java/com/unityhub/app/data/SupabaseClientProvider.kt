package com.unityhub.app.data

import android.content.Context
import com.unityhub.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    private const val PREFERENCES_NAME = "unityhub_auth_preferences"
    private const val KEY_REMEMBER_SESSION = "remember_session"

    @Volatile
    private var rememberSession = true

    private val persistentSessionManager = SettingsSessionManager()
    private val inMemorySessionManager = MemorySessionManager()

    private val adaptiveSessionManager = object : SessionManager {
        override suspend fun saveSession(session: UserSession) {
            if (rememberSession) {
                inMemorySessionManager.deleteSession()
                persistentSessionManager.saveSession(session)
            } else {
                persistentSessionManager.deleteSession()
                inMemorySessionManager.saveSession(session)
            }
        }

        override suspend fun loadSession(): UserSession {
            return if (rememberSession) {
                persistentSessionManager.loadSession()
            } else {
                inMemorySessionManager.loadSession()
            }
        }

        override suspend fun deleteSession() {
            persistentSessionManager.deleteSession()
            inMemorySessionManager.deleteSession()
        }
    }

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    val client by lazy {
        require(isConfigured) { "Supabase settings are missing from local.properties." }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                scheme = "unityhub"
                host = "auth"
                flowType = FlowType.PKCE
                sessionManager = adaptiveSessionManager
            }
            install(Postgrest)
        }
    }

    fun initializeRememberSession(context: Context) {
        rememberSession = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMEMBER_SESSION, true)
    }

    fun readRememberSession(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMEMBER_SESSION, true)
    }

    fun setRememberSession(context: Context, remember: Boolean) {
        rememberSession = remember
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REMEMBER_SESSION, remember)
            .apply()
    }
}
