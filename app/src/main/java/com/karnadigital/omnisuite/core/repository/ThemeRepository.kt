package com.karnadigital.omnisuite.core.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karnadigital.omnisuite.core.util.AccentColor
import com.karnadigital.omnisuite.core.util.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed repository for theme and app preferences.
 *
 * Replaces the legacy [com.karnadigital.omnisuite.core.util.ThemePreferences] global mutable
 * state with reactive, persisted [Flow]s so that preference changes propagate correctly across
 * configuration changes and obey unidirectional data flow.
 */
class ThemeRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("pref_theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("pref_accent_color")
        val OUTPUT_FOLDER = stringPreferencesKey("pref_output_folder")
        val PDF_DPI = intPreferencesKey("pref_pdf_dpi")
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.LIGHT
    }

    val accentColor: Flow<AccentColor> = dataStore.data.map { prefs ->
        prefs[Keys.ACCENT_COLOR]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() } ?: AccentColor.DEFAULT
    }

    val outputFolder: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.OUTPUT_FOLDER] ?: "OmniSuite"
    }

    val pdfDpi: Flow<Int> = dataStore.data.map { prefs ->
        val dpi = prefs[Keys.PDF_DPI] ?: 150
        if (dpi in listOf(72, 150, 300)) dpi else 150
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentColor(color: AccentColor) {
        dataStore.edit { it[Keys.ACCENT_COLOR] = color.name }
    }

    suspend fun setOutputFolder(folderName: String) {
        dataStore.edit { it[Keys.OUTPUT_FOLDER] = folderName }
    }

    suspend fun setPdfDpi(dpi: Int) {
        dataStore.edit { it[Keys.PDF_DPI] = dpi }
    }

    companion object {
        private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "omnisuite_settings")

        fun from(context: Context): ThemeRepository = ThemeRepository(context.themeDataStore)
    }
}
