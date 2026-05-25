package com.karnadigital.omnisuite.feature.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karnadigital.omnisuite.core.util.ThemeMode
import com.karnadigital.omnisuite.core.util.ThemePreferences
import com.karnadigital.omnisuite.ui.component.SectionHeader
import com.karnadigital.omnisuite.ui.component.SettingToggleRow
import com.karnadigital.omnisuite.ui.theme.OmniColors
import java.io.File

/**
 * Premium redesigned Settings screen viewport.
 * Compliant with gradient banner, POI dependencies metadata, dynamic cache calculation,
 * and tri-state theme option persistence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeMode by ThemePreferences.currentThemeState
    
    var hardwareAcceleration by remember { mutableStateOf(true) }
    var autoSaveEnabled by remember { mutableStateOf(false) }
    
    // Dynamic cache size calculation
    var cacheSizeStr by remember { mutableStateOf("0.0 B") }
    
    LaunchedEffect(Unit) {
        cacheSizeStr = getCacheSize(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        color = OmniColors.TextPrimary
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Navigate back",
                                tint = OmniColors.TextPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OmniColors.Bg
                )
            )
        },
        containerColor = OmniColors.Bg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Premium App identity card with gradient accent background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                OmniColors.Accent.copy(alpha = 0.2f),
                                OmniColors.ImgPurple.copy(alpha = 0.1f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        OmniColors.Accent.copy(alpha = 0.2f),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(OmniColors.Accent, OmniColors.ImgPurple)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "O",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "OmniSuite v1.1",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = OmniColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "100% Free · Fully Offline · Apache POI 5.2.5",
                            fontSize = 11.sp,
                            color = OmniColors.TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Preferences")
            Spacer(modifier = Modifier.height(4.dp))

            // 2. Tri-state Theme Selector Composable
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OmniColors.Surface2
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OmniColors.Border, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(OmniColors.Border),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🌙", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "App Theme Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = OmniColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Select your visual appearance preference.",
                                style = MaterialTheme.typography.labelSmall,
                                color = OmniColors.TextMuted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM).forEach { mode ->
                            val isSelected = themeMode == mode
                            val bg = if (isSelected) OmniColors.AccentGlow else OmniColors.Surface
                            val text = if (isSelected) OmniColors.Accent else OmniColors.TextMuted
                            val border = if (isSelected) OmniColors.Accent.copy(alpha = 0.5f) else OmniColors.Border
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .border(1.dp, border, RoundedCornerShape(10.dp))
                                    .clickable { ThemePreferences.setThemeMode(context, mode) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when(mode) {
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                        ThemeMode.SYSTEM -> "System"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = text
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hardware acceleration switch card
            SettingToggleRow(
                icon = "⚡",
                title = "Hardware Acceleration",
                description = "Enable GPU rendering routines for large documents.",
                checked = hardwareAcceleration,
                onCheckedChange = { hardwareAcceleration = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Auto save toggle switch card
            SettingToggleRow(
                icon = "💾",
                title = "Auto-Save Temp Files",
                description = "Periodically write changes to private cache storage.",
                checked = autoSaveEnabled,
                onCheckedChange = { autoSaveEnabled = it }
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Maintenance")
            Spacer(modifier = Modifier.height(4.dp))

            // Clear Cache Action Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OmniColors.Surface2
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OmniColors.Border, RoundedCornerShape(14.dp))
                    .clickable {
                        try {
                            context.cacheDir.deleteRecursively()
                            cacheSizeStr = getCacheSize(context)
                            Toast.makeText(context, "Local SAF cache cleared successfully.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Clear failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(OmniColors.PdfRedBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🗑", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Clear SAF Cache",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = OmniColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Delete copied SAF document payload streams.",
                                style = MaterialTheme.typography.labelSmall,
                                color = OmniColors.TextMuted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Cache",
                        tint = OmniColors.PdfRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Storage Usage Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OmniColors.Surface2
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OmniColors.Border, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(OmniColors.Border),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "📊", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Storage Usage",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = OmniColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Cached Document Payload: $cacheSizeStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = OmniColors.TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "System Information")
            Spacer(modifier = Modifier.height(4.dp))

            // System Information Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OmniColors.Surface2
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OmniColors.Border, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info Icon",
                            tint = OmniColors.Accent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OmniSuite Engine",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = OmniColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• Platform: Android 16 (API 36, Baklava)\n" +
                               "• Min SDK Level: API 30 (Android 11)\n" +
                               "• Engine Base: Apache POI 5.2.5 & PDFBox\n" +
                               "• Mode: 100% Free / Fully Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniColors.TextMuted,
                        lineHeight = 20.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun getCacheSize(context: Context): String {
    val size = getFolderSize(context.cacheDir)
    return formatFileSize(size)
}

private fun getFolderSize(file: File): Long {
    var size: Long = 0
    if (file.isDirectory) {
        val files = file.listFiles()
        if (files != null) {
            for (f in files) {
                size += getFolderSize(f)
            }
        }
    } else {
        size += file.length()
    }
    return size
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
