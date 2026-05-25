package com.karnadigital.omnisuite.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.karnadigital.omnisuite.R

// Google Font Provider
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val SyneFontName = GoogleFont("Syne")
private val SyneFontFamily = FontFamily(
    Font(googleFont = SyneFontName, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = SyneFontName, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = SyneFontName, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = SyneFontName, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = SyneFontName, fontProvider = provider, weight = FontWeight.ExtraBold)
)

private val DmSansFontName = GoogleFont("DM Sans")
private val DmSansFontFamily = FontFamily(
    Font(googleFont = DmSansFontName, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = DmSansFontName, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = DmSansFontName, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = DmSansFontName, fontProvider = provider, weight = FontWeight.Bold)
)

// Curated Material3 Typography configurations with Syne and DM Sans
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = SyneFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontFamily = SyneFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelMedium = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
