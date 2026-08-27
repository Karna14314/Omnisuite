package com.karnadigital.omnisuite.core.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.karnadigital.omnisuite.core.repository.ThemeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Persisted Theme selection configuration (LIGHT, DARK, SYSTEM).
 * Saved entirely offline.
 *
 * Backed by a DataStore-powered [ThemeRepository] so that preferences survive cleanly and
 * are not tied to the legacy SharedPreferences storage. The public API is intentionally kept
 * identical (synchronous [mutableStateOf] holders) so that existing consumers across the UI
 * continue to work without changes; internally every read/write now flows through DataStore.
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
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var repository: ThemeRepository? = null

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

    /**
     * Initializes the reactive states from the DataStore-backed repository on startup.
     * Safe to call multiple times; subsequent calls refresh the in-memory state from DataStore.
     */
    fun initialize(context: Context) {
        val repo = repository ?: ThemeRepository.from(context).also { repository = it }
        // Seed synchronously from the first emitted DataStore values so the very first
        // composition reads the persisted settings instead of defaults.
        runBlocking {
            currentThemeState.value = repo.themeMode.first()
            currentAccentState.value = repo.accentColor.first()
            currentOutputFolderState.value = repo.outputFolder.first()
            currentPdfDpiState.value = repo.pdfDpi.first()
        }
        // Keep the in-memory state reactive to later DataStore changes (e.g. from another process).
        ioScope.launch { repo.themeMode.collect { currentThemeState.value = it } }
        ioScope.launch { repo.accentColor.collect { currentAccentState.value = it } }
        ioScope.launch { repo.outputFolder.collect { currentOutputFolderState.value = it } }
        ioScope.launch { repo.pdfDpi.collect { currentPdfDpiState.value = it } }
    }

    /**
     * Persists the selected theme mode reactively and triggers Compose updates instantly.
     */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        currentThemeState.value = mode
        val repo = repository ?: ThemeRepository.from(context).also { repository = it }
        ioScope.launch { repo.setThemeMode(mode) }
    }

    /**
     * Persists the selected accent color and triggers Compose updates instantly.
     */
    fun setAccentColor(context: Context, color: AccentColor) {
        currentAccentState.value = color
        val repo = repository ?: ThemeRepository.from(context).also { repository = it }
        ioScope.launch { repo.setAccentColor(color) }
    }

    /**
     * Persists the default output folder path configuration.
     */
    fun setOutputFolder(context: Context, folderName: String) {
        currentOutputFolderState.value = folderName
        val repo = repository ?: ThemeRepository.from(context).also { repository = it }
        ioScope.launch { repo.setOutputFolder(folderName) }
    }

    /**
     * Persists the selected PDF rendering DPI.
     */
    fun setPdfDpi(context: Context, dpi: Int) {
        currentPdfDpiState.value = dpi
        val repo = repository ?: ThemeRepository.from(context).also { repository = it }
        ioScope.launch { repo.setPdfDpi(dpi) }
    }
}
