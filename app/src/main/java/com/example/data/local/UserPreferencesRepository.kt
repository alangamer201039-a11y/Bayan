package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ReadingFontSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.bayanPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "bayan_prefs")

data class UserPreferences(
    val selectedProvince: String = "صلاح الدين",
    val isDarkTheme: Boolean = true,
    val fontSize: ReadingFontSize = ReadingFontSize.MEDIUM,
    val verifiedSourcesOnly: Boolean = true,
    val autoRefreshEnabled: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val SELECTED_PROVINCE = stringPreferencesKey("selected_province")
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val VERIFIED_SOURCES_ONLY = booleanPreferencesKey("verified_sources_only")
        val AUTO_REFRESH_ENABLED = booleanPreferencesKey("auto_refresh_enabled")
    }

    val userPreferences: Flow<UserPreferences> = context.bayanPrefsDataStore.data.map { prefs ->
        UserPreferences(
            selectedProvince = prefs[Keys.SELECTED_PROVINCE] ?: "صلاح الدين",
            isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
            fontSize = ReadingFontSize.fromKey(prefs[Keys.FONT_SIZE] ?: ReadingFontSize.MEDIUM.key),
            verifiedSourcesOnly = prefs[Keys.VERIFIED_SOURCES_ONLY] ?: true,
            autoRefreshEnabled = prefs[Keys.AUTO_REFRESH_ENABLED] ?: true
        )
    }

    suspend fun setSelectedProvince(province: String) {
        context.bayanPrefsDataStore.edit { prefs ->
            prefs[Keys.SELECTED_PROVINCE] = province
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.bayanPrefsDataStore.edit { prefs ->
            prefs[Keys.IS_DARK_THEME] = isDark
        }
    }

    suspend fun setFontSize(fontSize: ReadingFontSize) {
        context.bayanPrefsDataStore.edit { prefs ->
            prefs[Keys.FONT_SIZE] = fontSize.key
        }
    }

    suspend fun setVerifiedSourcesOnly(enabled: Boolean) {
        context.bayanPrefsDataStore.edit { prefs ->
            prefs[Keys.VERIFIED_SOURCES_ONLY] = enabled
        }
    }

    suspend fun setAutoRefreshEnabled(enabled: Boolean) {
        context.bayanPrefsDataStore.edit { prefs ->
            prefs[Keys.AUTO_REFRESH_ENABLED] = enabled
        }
    }
}
