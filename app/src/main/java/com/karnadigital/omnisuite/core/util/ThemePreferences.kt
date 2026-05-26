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

/**
 * Premium HSL Accent Color presets for personalized application highlights.
 */
enum class AccentColor(val hex: String, val label: String) {
    EMERALD("#10B981", "Emerald Green"),
    OCEAN_BLUE("#3B82F6", "Ocean Blue"),
    VIOLET_SPARK("#8B5CF6", "Violet Spark"),
    PDF_CRIMSON("#EF4444", "PDF Crimson"),
    CLASSIC_SILVER("#64748B", "Classic Silver"),
    DEFAULT("#6366F1", "Default")
}

object ThemePreferences {
    private const val PREFS_NAME = "omnisuite_settings"
    private const val KEY_THEME_MODE = "pref_theme_mode"
    private const val KEY_ACCENT_COLOR = "pref_accent_color"
    private const val KEY_OUTPUT_FOLDER = "pref_output_folder"
    private const val KEY_PDF_DPI = "pref_pdf_dpi"

    /**
     * Reactive Compose state holder for real-time theme updates across viewports.
     */
    var currentThemeState = mutableStateOf(ThemeMode.LIGHT)
        private set

    /**
     * Reactive Accent Color state holder.
     */
    var currentAccentState = mutableStateOf(AccentColor.DEFAULT)
        private set

    /**
     * Reactive Default Output Folder configuration state holder.
     */
    var currentOutputFolderState = mutableStateOf("OmniSuite")
        private set

    /**
     * Reactive PDF rendering DPI resolution selector.
     */
    var currentPdfDpiState = mutableStateOf(150)
        private set

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Initializes the reactive states from persisted SharedPreferences on startup.
     */
    fun initialize(context: Context) {
        currentThemeState.value = getThemeMode(context)
        currentAccentState.value = getAccentColor(context)
        currentOutputFolderState.value = getOutputFolder(context)
        currentPdfDpiState.value = getPdfDpi(context)
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

    /**
     * Retrieves the saved accent color, defaulting to DEFAULT.
     */
    fun getAccentColor(context: Context): AccentColor {
        val colorStr = getPrefs(context).getString(KEY_ACCENT_COLOR, AccentColor.DEFAULT.name) ?: AccentColor.DEFAULT.name
        return try {
            AccentColor.valueOf(colorStr)
        } catch (e: Exception) {
            AccentColor.DEFAULT
        }
    }

    /**
     * Persists the selected accent color and triggers Compose updates instantly.
     */
    fun setAccentColor(context: Context, color: AccentColor) {
        getPrefs(context).edit().putString(KEY_ACCENT_COLOR, color.name).apply()
        currentAccentState.value = color
    }

    /**
     * Retrieves the saved output folder name, defaulting to "OmniSuite".
     */
    fun getOutputFolder(context: Context): String {
        return getPrefs(context).getString(KEY_OUTPUT_FOLDER, "OmniSuite") ?: "OmniSuite"
    }

    /**
     * Persists the default output folder path configuration.
     */
    fun setOutputFolder(context: Context, folderName: String) {
        getPrefs(context).edit().putString(KEY_OUTPUT_FOLDER, folderName).apply()
        currentOutputFolderState.value = folderName
    }

    /**
     * Retrieves the saved PDF resolution DPI, defaulting to 150.
     */
    fun getPdfDpi(context: Context): Int {
        val dpi = getPrefs(context).getInt(KEY_PDF_DPI, 150)
        return if (dpi in listOf(72, 150, 300)) dpi else 150
    }

    /**
     * Persists the selected PDF rendering DPI.
     */
    fun setPdfDpi(context: Context, dpi: Int) {
        getPrefs(context).edit().putInt(KEY_PDF_DPI, dpi).apply()
        currentPdfDpiState.value = dpi
    }
}
