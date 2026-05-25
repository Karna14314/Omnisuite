package com.karnadigital.omnisuite.core.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/**
 * Persisted Theme selection configuration (LIGHT, DARK, SYSTEM).
 * Saved entirely offline using device SharedPreferences.
 */
enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

object ThemePreferences {
    private const val PREFS_NAME = "omnisuite_settings"
    private const val KEY_THEME_MODE = "pref_theme_mode"

    /**
     * Reactive Compose state holder for real-time theme updates across viewports.
     */
    var currentThemeState = mutableStateOf(ThemeMode.LIGHT)
        private set

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Initializes the reactive state from persisted SharedPreferences on startup.
     */
    fun initialize(context: Context) {
        currentThemeState.value = getThemeMode(context)
    }

    /**
     * Retrieves the saved theme mode, defaulting to LIGHT.
     */
    fun getThemeMode(context: Context): ThemeMode {
        val modeStr = getPrefs(context).getString(KEY_THEME_MODE, ThemeMode.LIGHT.name) ?: ThemeMode.LIGHT.name
        return try {
            ThemeMode.valueOf(modeStr)
        } catch (e: Exception) {
            ThemeMode.LIGHT
        }
    }

    /**
     * Persists the selected theme mode reactively and triggers Compose updates instantly.
     */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
        currentThemeState.value = mode
    }
}
