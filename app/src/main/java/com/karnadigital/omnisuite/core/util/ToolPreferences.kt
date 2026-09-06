package com.karnadigital.omnisuite.core.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * Persistent favorites manager for the OmniSuite Tools Hub.
 * Saves favorite tool identifiers offline using SharedPreferences.
 */
object ToolPreferences {
    private const val PREFS_NAME = "omnisuite_tool_preferences"
    private const val KEY_FAVORITES = "favorite_tools"

    private val defaultFavorites = setOf(
        "pdf_merge",
        "pdf_split",
        "doc_to_pdf",
        "pdf_to_images",
        "pdf_rotate",
        "watermark",
        "scan_to_pdf",
        "zip_maker"
    )

    private var prefs: SharedPreferences? = null
    private val _favoriteToolIds = mutableStateOf<Set<String>>(defaultFavorites)
    val favoriteToolIds: State<Set<String>> = _favoriteToolIds

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs?.getStringSet(KEY_FAVORITES, null)
            if (saved != null) {
                _favoriteToolIds.value = saved.toSet()
            } else {
                _favoriteToolIds.value = defaultFavorites
                prefs?.edit()?.putStringSet(KEY_FAVORITES, defaultFavorites)?.apply()
            }
        }
    }

    fun isFavorite(toolId: String): Boolean {
        return _favoriteToolIds.value.contains(toolId)
    }

    fun toggleFavorite(toolId: String) {
        val current = _favoriteToolIds.value.toMutableSet()
        if (current.contains(toolId)) {
            current.remove(toolId)
        } else {
            current.add(toolId)
        }
        _favoriteToolIds.value = current
        prefs?.edit()?.putStringSet(KEY_FAVORITES, current)?.apply()
    }

    fun setFavorites(toolIds: Set<String>) {
        _favoriteToolIds.value = toolIds
        prefs?.edit()?.putStringSet(KEY_FAVORITES, toolIds)?.apply()
    }

    fun clearFavorites() {
        _favoriteToolIds.value = emptySet()
        prefs?.edit()?.remove(KEY_FAVORITES)?.apply()
    }
}
