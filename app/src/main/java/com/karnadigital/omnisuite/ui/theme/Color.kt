package com.karnadigital.omnisuite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.karnadigital.omnisuite.core.util.ThemePreferences

/**
 * Redesign Design System Tokens (OmniColors)
 * A dynamic, theme-aware offline productivity palette.
 */
object OmniColors {
    // Backgrounds & Core Surfaces
    val Bg: Color
        @Composable
        get() = MaterialTheme.colorScheme.background

    val Surface: Color
        @Composable
        get() = MaterialTheme.colorScheme.surface

    val Surface2: Color
        @Composable
        get() = MaterialTheme.colorScheme.surfaceVariant

    val Border: Color
        @Composable
        get() = if (MaterialTheme.colorScheme.background == LightBackground) Color(0x1F000000) else Color(0x0FFFFFFF)

    // Text
    val TextPrimary: Color
        @Composable
        get() = MaterialTheme.colorScheme.onBackground

    val TextMuted: Color
        @Composable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    // Semantic category colors
    val PdfRed      = Color(0xFFEF4444)
    val PdfRedBg    = Color(0x1FEF4444)   // 12% opacity
    val DocBlue     = Color(0xFF3B82F6)
    val DocBlueBg   = Color(0x1F3B82F6)
    val XlsGreen    = Color(0xFF10B981)
    val XlsGreenBg  = Color(0x1F10B981)
    val ImgPurple   = Color(0xFF8B5CF6)
    val ImgPurpleBg = Color(0x1F8B5CF6)
    val PptOrange   = Color(0xFFF59E0B)
    val PptOrangeBg = Color(0x1FF59E0B)
    val ArcCyan     = Color(0xFF06B6D4)
    val ArcCyanBg   = Color(0x1F06B6D4)
    
    // Main Brand Accent
    val Accent: Color
        get() = try {
            Color(android.graphics.Color.parseColor(ThemePreferences.currentAccentState.value.hex))
        } catch (e: Exception) {
            Color(0xFF6366F1)
        }
    val AccentGlow: Color
        @Composable
        get() = Accent.copy(alpha = if (MaterialTheme.colorScheme.background == LightBackground) 0.1f else 0.25f)
}

// Light Theme curated Slate-Indigo & Warm Gold palette
val LightPrimary = Color(0xFF1E3A8A)        // Deep Royal Slate
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDBEAFE)
val LightOnPrimaryContainer = Color(0xFF1E40AF)

val LightSecondary = Color(0xFF0284C7)      // Vibrant Cyan Blue
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE0F2FE)
val LightOnSecondaryContainer = Color(0xFF0369A1)

val LightTertiary = Color(0xFFD97706)       // Rich Amber Gold
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFEF3C7)
val LightOnTertiaryContainer = Color(0xFF92400E)

val LightBackground = Color(0xFFF8FAFC)      // Sleek Off-White Slate
val LightOnBackground = Color(0xFF0F172A)    // Dark Charcoal Slate
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF0F172A)
val LightSurfaceVariant = Color(0xFFE2E8F0)  // Mild Gray border
val LightOnSurfaceVariant = Color(0xFF64748B)

// Dark Theme premium Deep Space Slate & Ice Blue palette
val DarkPrimary = Color(0xFF6366F1)
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkPrimaryContainer = Color(0x406366F1)
val DarkOnPrimaryContainer = Color(0xFFF0F2F7)

val DarkSecondary = Color(0xFF06B6D4)
val DarkOnSecondary = Color(0xFF0D0F14)
val DarkSecondaryContainer = Color(0x1F06B6D4)
val DarkOnSecondaryContainer = Color(0xFFF0F2F7)

val DarkTertiary = Color(0xFF10B981)
val DarkOnTertiary = Color(0xFF0D0F14)
val DarkTertiaryContainer = Color(0x1F10B981)
val DarkOnTertiaryContainer = Color(0xFFF0F2F7)

val DarkBackground = Color(0xFF0D0F14)
val DarkOnBackground = Color(0xFFF0F2F7)
val DarkSurface = Color(0xFF161922)
val DarkOnSurface = Color(0xFFF0F2F7)
val DarkSurfaceVariant = Color(0xFF1E2330)
val DarkOnSurfaceVariant = Color(0xFF7A8299)
