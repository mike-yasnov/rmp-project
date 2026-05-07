package com.highloadinvest.nativeapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.highloadinvest.nativeapp.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

class SessionStore(private val context: Context) {
    private val USER_ID = stringPreferencesKey("user_id")
    private val USERNAME = stringPreferencesKey("username")
    private val EMAIL = stringPreferencesKey("email")
    private val THEME = stringPreferencesKey("theme")

    data class Session(val userId: String, val username: String, val email: String)

    val sessionFlow: Flow<Session?> = context.dataStore.data.map { prefs ->
        val id = prefs[USER_ID] ?: return@map null
        Session(userId = id, username = prefs[USERNAME].orEmpty(), email = prefs[EMAIL].orEmpty())
    }

    val themeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[THEME]) {
            "light" -> ThemeMode.Light
            "dark" -> ThemeMode.Dark
            else -> ThemeMode.System
        }
    }

    suspend fun setSession(userId: String, username: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID] = userId
            prefs[USERNAME] = username
            prefs[EMAIL] = email
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(USER_ID); prefs.remove(USERNAME); prefs.remove(EMAIL)
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[THEME] = when (mode) {
                ThemeMode.Light -> "light"; ThemeMode.Dark -> "dark"; ThemeMode.System -> "system"
            }
        }
    }
}
