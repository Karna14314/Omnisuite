package com.karnadigital.omnisuite.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.util.AccentColor
import com.karnadigital.omnisuite.core.util.ThemeMode
import com.karnadigital.omnisuite.core.util.ThemePreferences
import com.karnadigital.omnisuite.ui.component.SectionHeader
import com.karnadigital.omnisuite.ui.component.SettingToggleRow
import com.karnadigital.omnisuite.ui.theme.OmniColors
import java.io.File
import java.util.Locale

/**
 * Premium redesigned Settings screen viewport.
 * Compliant with gradient banner, POI dependencies metadata, dynamic cache calculation,
 * and tri-state theme option persistence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val themeMode by ThemePreferences.currentThemeState
    val accentColor by ThemePreferences.currentAccentState
    
    var hardwareAcceleration by remember { mutableStateOf(true) }
    var autoSaveEnabled by remember { mutableStateOf(false) }
    
    var pdfDpiText by remember { mutableStateOf(ThemePreferences.currentPdfDpiState.value) }
    var showDpiDropdown by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    // Dynamic cache size calculation
    var cacheSizeStr by remember { mutableStateOf("0.0 B") }
    
    LaunchedEffect(Unit) {
        cacheSizeStr = viewModel.calculateCacheSize()
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

            val packageInfo = remember(context) {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                } catch (_: Exception) { null }
            }
            val appVersionName = packageInfo?.versionName ?: "1.0.2"
            val appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode ?: 3L
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong() ?: 3L
            }

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
                            text = "OmniSuite v$appVersionName (Build $appVersionCode)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = OmniColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "100% Free · Fully Offline · Apache POI 5.2.5 & PDFBox",
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

            // 3. Dynamic Accent Selector swatches
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
                            Text(text = "🎨", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Brand Accent Color",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = OmniColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Customize application highlight accent.",
                                style = MaterialTheme.typography.labelSmall,
                                color = OmniColors.TextMuted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccentColor.values().forEach { color ->
                            val isSelected = accentColor == color
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(color.hex)))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) OmniColors.TextPrimary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { ThemePreferences.setAccentColor(context, color) }
                            )
                        }
                    }
                }
            }



            // 5. PDF DPI Resolution Scale Card
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
                            Text(text = "🖨️", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "PDF Rendering Resolution (DPI)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = OmniColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Configure conversion rendering quality.",
                                style = MaterialTheme.typography.labelSmall,
                                color = OmniColors.TextMuted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showDpiDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = OmniColors.TextPrimary
                            ),
                            border = BorderStroke(1.dp, OmniColors.Border)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (pdfDpiText) {
                                        72 -> "72 DPI (Draft / Fast)"
                                        300 -> "300 DPI (High Definition)"
                                        else -> "150 DPI (Balanced)"
                                    }
                                )
                                Text("▼", fontSize = 10.sp, color = OmniColors.TextMuted)
                            }
                        }
                        DropdownMenu(
                            expanded = showDpiDropdown,
                            onDismissRequest = { showDpiDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.9f).background(OmniColors.Surface2)
                        ) {
                            listOf(72, 150, 300).forEach { dpi ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = when (dpi) {
                                                72 -> "72 DPI (Draft / Fast)"
                                                300 -> "300 DPI (High Definition)"
                                                else -> "150 DPI (Balanced)"
                                            },
                                            color = OmniColors.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        pdfDpiText = dpi
                                        ThemePreferences.setPdfDpi(context, dpi)
                                        showDpiDropdown = false
                                    }
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
            SectionHeader(title = "Storage & Maintenance")
            Spacer(modifier = Modifier.height(4.dp))

            // Dynamic Storage Usage & Clear Cache Card
            SettingActionCard(
                iconText = "🔥",
                iconBgColor = OmniColors.PdfRedBg,
                title = "Clear Document Cache",
                description = "Purge temporary rendering streams and scratch files.",
                trailingText = cacheSizeStr,
                onClick = {
                    viewModel.clearCache { newSize ->
                        cacheSizeStr = newSize
                        Toast.makeText(context, "Local cache cleared successfully.", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Clear Recent Files History Card
            SettingActionCard(
                iconVector = Icons.Default.Delete,
                iconTint = OmniColors.PdfRed,
                iconBgColor = OmniColors.PdfRedBg,
                title = "Clear Recent Files History",
                description = "Wipe recent document history and operations log.",
                isDestructive = true,
                onClick = { showClearHistoryDialog = true }
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Support & Community")
            Spacer(modifier = Modifier.height(4.dp))

            // Rate on Play Store Card
            SettingActionCard(
                iconVector = Icons.Default.Star,
                iconTint = OmniColors.XlsGreen,
                iconBgColor = OmniColors.XlsGreenBg,
                title = "Rate Us on Google Play",
                description = "Enjoying OmniSuite? Leave a review on Google Play Store.",
                onClick = { rateOnPlayStore(context) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Report a Bug Card
            SettingActionCard(
                iconVector = Icons.Default.BugReport,
                iconTint = OmniColors.PptOrange,
                iconBgColor = OmniColors.PptOrangeBg,
                title = "Report a Bug / Request Feature",
                description = "Draft an email with device diagnostics to developerncn29@gmail.com.",
                onClick = { reportBug(context, appVersionName, appVersionCode) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // GitHub Repository Card
            SettingActionCard(
                iconVector = Icons.Default.Code,
                iconTint = OmniColors.Accent,
                iconBgColor = OmniColors.AccentGlow,
                title = "GitHub Repository",
                description = "Explore source code, contribute, or star the project on GitHub.",
                onClick = { openGitHubRepo(context) }
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "About & Legal")
            Spacer(modifier = Modifier.height(4.dp))

            // Open Source Credits Card
            SettingActionCard(
                iconVector = Icons.Default.Description,
                iconTint = OmniColors.DocBlue,
                iconBgColor = OmniColors.DocBlueBg,
                title = "Open Source Credits",
                description = "View third-party open-source libraries, engines, and licenses.",
                onClick = { showLicensesDialog = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Offline Privacy Guarantee Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OmniColors.Surface2),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OmniColors.Border, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(OmniColors.XlsGreenBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security Icon",
                            tint = OmniColors.XlsGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "100% Offline & Private",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = OmniColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "OmniSuite processes all documents on your device. Zero cloud sync, zero telemetry, and zero tracking.",
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniColors.TextMuted,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // System Information Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OmniColors.Surface2),
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
                            text = "OmniSuite Architecture Specs",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = OmniColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• App Version: v$appVersionName (Build $appVersionCode)\n" +
                               "• Android Target: API 36 (Android 16)\n" +
                               "• Min SDK Level: API 30 (Android 11)\n" +
                               "• Engines: Apache POI 5.2.5 (OOXML / OLE2) & PDFBox Android\n" +
                               "• Architecture: Jetpack Compose Material 3 + Room SQLite + Hilt\n" +
                               "• License: Free & Open Source (Offline-First)",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniColors.TextMuted,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Confirmation Dialog: Clear History
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = {
                Text(
                    text = "Clear Recent History?",
                    fontWeight = FontWeight.Bold,
                    color = OmniColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to clear your recent document history? Your actual files on storage will remain untouched.",
                    color = OmniColors.TextMuted,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllRecentFiles {
                            Toast.makeText(context, "Recent document history wiped.", Toast.LENGTH_SHORT).show()
                        }
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OmniColors.PdfRed)
                ) {
                    Text("Clear", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = OmniColors.TextPrimary)
                }
            },
            containerColor = OmniColors.Surface2,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Open Source Credits Dialog
    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = OmniColors.Accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Source Credits",
                        fontWeight = FontWeight.Bold,
                        color = OmniColors.TextPrimary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "OmniSuite is built on top of world-class open-source projects:",
                        style = MaterialTheme.typography.bodySmall,
                        color = OmniColors.TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val openSourceLibs = listOf(
                        Triple("Apache POI (v5.2.5)", "Apache License 2.0", "Native Word (.docx/.doc), Excel (.xlsx/.xls), and PowerPoint (.pptx/.ppt) parser and manipulation engine."),
                        Triple("PDFBox Android (v2.0.27.0)", "Apache License 2.0", "Port of Apache PDFBox for high-fidelity PDF rendering, pagination, and text manipulation."),
                        Triple("Jetpack Compose & Material 3", "Apache License 2.0", "Declarative modern Android UI framework and Material Design 3 design system."),
                        Triple("Dagger / Hilt (v2.51.1)", "Apache License 2.0", "Dependency injection framework for clean decoupled architecture."),
                        Triple("AndroidX Room (v2.6.1)", "Apache License 2.0", "Offline SQLite object mapping library for recent files and history persistence."),
                        Triple("Coil Image Loader (v2.6.0)", "Apache License 2.0", "Fast, lightweight image loading with SVG decoder support."),
                        Triple("Google ML Kit & ZXing", "Apache License 2.0", "Barcode scanning, document scanner hardware acceleration, and QR code generation."),
                        Triple("Zip4j (v2.11.5)", "Apache License 2.0", "Comprehensive Java library for ZIP archive manipulation and encryption."),
                        Triple("Kotlin Coroutines & Flow", "Apache License 2.0", "Asynchronous concurrency and reactive stream execution.")
                    )

                    openSourceLibs.forEach { (name, license, desc) ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = OmniColors.Surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(1.dp, OmniColors.Border, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = OmniColors.TextPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = OmniColors.AccentGlow
                                    ) {
                                        Text(
                                            text = license,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OmniColors.Accent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = desc,
                                    fontSize = 11.sp,
                                    color = OmniColors.TextMuted,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLicensesDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = OmniColors.Accent)
                ) {
                    Text("Close", color = Color.White)
                }
            },
            containerColor = OmniColors.Surface2,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SettingActionCard(
    iconText: String? = null,
    iconVector: ImageVector? = null,
    iconTint: Color = OmniColors.Accent,
    iconBgColor: Color = OmniColors.Border,
    title: String,
    description: String,
    trailingText: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OmniColors.Surface2),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OmniColors.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
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
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconVector != null) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = title,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (iconText != null) {
                        Text(text = iconText, fontSize = 16.sp)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDestructive) OmniColors.PdfRed else OmniColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniColors.TextMuted
                    )
                }
            }
            if (trailingText != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OmniColors.TextMuted
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = OmniColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun rateOnPlayStore(context: Context) {
    val packageName = context.packageName
    val marketUri = Uri.parse("market://details?id=$packageName")
    val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }
    try {
        context.startActivity(marketIntent)
    } catch (e: ActivityNotFoundException) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(webIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open Play Store link.", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun reportBug(context: Context, versionName: String, versionCode: Long) {
    val availStorageMb = try {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
    } catch (_: Exception) { -1L }
    val totalStorageMb = try {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024)
    } catch (_: Exception) { -1L }

    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
    val memInfo = android.app.ActivityManager.MemoryInfo()
    actManager?.getMemoryInfo(memInfo)
    val availRamMb = memInfo.availMem / (1024 * 1024)
    val totalRamMb = memInfo.totalMem / (1024 * 1024)

    val dm = context.resources.displayMetrics
    val displayStr = "${dm.widthPixels}x${dm.heightPixels} (${dm.densityDpi} dpi)"

    val template = """
Hi OmniSuite Developer,

[Please describe the bug or issue]:


[Steps to reproduce]:
1. 
2. 
3. 

[Expected behavior]:


[Observed behavior]:


──────────────────────────────
SYSTEM & HARDWARE DIAGNOSTICS
──────────────────────────────
• App: OmniSuite v$versionName (Build $versionCode)
• Device: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}
• Brand / Product: ${Build.BRAND} / ${Build.PRODUCT}
• Android OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
• Display: $displayStr
• Free RAM: ${availRamMb} MB / ${totalRamMb} MB
• Storage: ${availStorageMb} MB free of ${totalStorageMb} MB
• Locale: ${Locale.getDefault().toLanguageTag()}
──────────────────────────────
""".trimIndent()

    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:developerncn29@gmail.com")
        putExtra(Intent.EXTRA_EMAIL, arrayOf("developerncn29@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, "[OmniSuite Bug Report] v$versionName (Build $versionCode)")
        putExtra(Intent.EXTRA_TEXT, template)
    }
    try {
        context.startActivity(emailIntent)
    } catch (e: ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("developerncn29@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "[OmniSuite Bug Report] v$versionName (Build $versionCode)")
            putExtra(Intent.EXTRA_TEXT, template)
        }
        try {
            context.startActivity(Intent.createChooser(fallback, "Send Bug Report"))
        } catch (_: Exception) {
            Toast.makeText(context, "No email client app found to send bug report.", Toast.LENGTH_LONG).show()
        }
    }
}

private fun openGitHubRepo(context: Context) {
    val repoUri = Uri.parse("https://github.com/Karna14314/Omnisuite")
    val intent = Intent(Intent.ACTION_VIEW, repoUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open browser link.", Toast.LENGTH_SHORT).show()
    }
}
