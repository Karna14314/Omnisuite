package com.karnadigital.omnisuite.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.feature.history.HistoryScreen
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.karnadigital.omnisuite.feature.utility.rememberDocumentScannerLauncher

/**
 * Bottom Navigation Primary Root Tabs.
 */
enum class HomeTab {
    Workspace,
    History
}

/**
 * Premium 2-Tab root Scaffold viewport shell for OmniSuite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToQrGenerator: () -> Unit,
    onNavigateToBarcodeScanner: () -> Unit,
    onNavigateToImageTools: () -> Unit,
    onNavigateToPdfTools: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToSignaturePad: () -> Unit,
    onNavigateToWatermark: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(HomeTab.Workspace) }

    // Dynamic camera edge-crop smart scanner launcher
    val scannerLauncher = rememberDocumentScannerLauncher(
        onScanSuccess = { tempUri, savedFile ->
            Toast.makeText(context, "Document scanned successfully!", Toast.LENGTH_SHORT).show()
            viewModel.addRecentFile(
                fileUri = Uri.fromFile(savedFile).toString(),
                fileName = savedFile.name,
                mimeType = "application/pdf",
                fileSize = savedFile.length()
            )
        },
        onScanFailure = { exception ->
            Toast.makeText(context, "Scan error: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "O",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "OmniSuite",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings Screen",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Workspace Panel") },
                    label = { Text("Workspace") },
                    selected = selectedTab == HomeTab.Workspace,
                    onClick = { selectedTab = HomeTab.Workspace }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "History Log") },
                    label = { Text("History") },
                    selected = selectedTab == HomeTab.History,
                    onClick = { selectedTab = HomeTab.History }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                HomeTab.Workspace -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // ZONE 1: THE ACTIVE PIPELINE (RECENTS)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Files",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { viewModel.clearRecents() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when (val state = uiState) {
                            is RecentFilesUiState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(strokeWidth = 3.dp)
                                }
                            }
                            is RecentFilesUiState.Empty -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No recent documents opened offline.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is RecentFilesUiState.Success -> {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(state.files) { file ->
                                        RecentFileCard(
                                            file = file,
                                            onClick = { onOpenFile(file.fileName) }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // ZONE 2: THE DOCUMENT HUB (POLYMORPHIC VIEWERS)
                        CategoryHeader("Document Suite", "Tap to browse local device files")
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DocumentHubItem(
                                title = "Word",
                                iconText = "📝",
                                color = Color(0xFF3B82F6),
                                onClick = {
                                    viewModel.addRecentFile(
                                        fileUri = "content://com.android.providers.downloads.documents/document/proposal_doc",
                                        fileName = "Project_Proposal.docx",
                                        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        fileSize = 124500L
                                    )
                                    onOpenFile("placeholder_word.docx")
                                },
                                modifier = Modifier.weight(1f)
                            )
                            DocumentHubItem(
                                title = "Excel",
                                iconText = "📊",
                                color = Color(0xFF10B981),
                                onClick = {
                                    viewModel.addRecentFile(
                                        fileUri = "content://com.android.providers.downloads.documents/document/budget_sheet",
                                        fileName = "Q3_Monthly_Budget.xlsx",
                                        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        fileSize = 45280L
                                    )
                                    onOpenFile("placeholder_excel.xlsx")
                                },
                                modifier = Modifier.weight(1f)
                            )
                            DocumentHubItem(
                                title = "Slides",
                                iconText = "🖼️",
                                color = Color(0xFFF59E0B),
                                onClick = {
                                    viewModel.addRecentFile(
                                        fileUri = "content://com.android.providers.downloads.documents/document/pitch_slides",
                                        fileName = "Product_Pitch_Slides.pptx",
                                        mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                        fileSize = 852000L
                                    )
                                    onOpenFile("placeholder_slides.pptx")
                                },
                                modifier = Modifier.weight(1f)
                            )
                            DocumentHubItem(
                                title = "Text",
                                iconText = "📄",
                                color = Color(0xFF64748B),
                                onClick = {
                                    viewModel.addRecentFile(
                                        fileUri = "content://com.android.providers.downloads.documents/document/notes_txt",
                                        fileName = "Meeting_Notes.txt",
                                        mimeType = "text/plain",
                                        fileSize = 1500L
                                    )
                                    onOpenFile("placeholder_text.txt")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // ZONE 3: THE PDF FACTORY (STRUCTURE CHANGERS)
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                CategoryHeader("PDF Modification Toolkit", "Heavy off-device operations (PDFBox)")
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ToolItemCard(
                                        title = "Merge PDFs",
                                        description = "Combine files",
                                        iconText = "🥞",
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier.weight(1f),
                                        onClick = onNavigateToPdfTools
                                    )
                                    ToolItemCard(
                                        title = "Split PDF",
                                        description = "Extract pages",
                                        iconText = "✂️",
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier.weight(1f),
                                        onClick = onNavigateToPdfTools
                                    )
                                    ToolItemCard(
                                        title = "Encrypt PDF",
                                        description = "Password lock",
                                        iconText = "🔒",
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier.weight(1f),
                                        onClick = onNavigateToPdfTools
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ToolItemCard(
                                        title = "Digital Sign",
                                        description = "Stamp signature",
                                        iconText = "✍️",
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier.weight(1f),
                                        onClick = onNavigateToSignaturePad
                                    )
                                    ToolItemCard(
                                        title = "Watermark",
                                        description = "Stamp security",
                                        iconText = "🎨",
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier.weight(1f),
                                        onClick = onNavigateToWatermark
                                    )
                                    Box(modifier = Modifier.weight(1f)) // spacer block for balanced row grid alignment
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // ZONE 4: UTILITY & SCANNER LAB
                        CategoryHeader("Quick Utilities", "CameraX viewfinder & barcode generators")
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp)
                                    .clickable(onClick = onNavigateToBarcodeScanner)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📷", fontSize = 18.sp)
                                    }
                                    Column {
                                        Text(
                                            text = "Scan Barcode",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "Live scanner loop",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp)
                                    .clickable(onClick = onNavigateToQrGenerator)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🧬", fontSize = 18.sp)
                                    }
                                    Column {
                                        Text(
                                            text = "Generate QR",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "Create codes offline",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Smart Document Scanner Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp)
                                    .clickable(onClick = scannerLauncher)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📄", fontSize = 18.sp)
                                    }
                                    Column {
                                        Text(
                                            text = "Smart Scanner",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Auto edge-crop & PDF",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            // On-Demand Text Recognition Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp)
                                    .clickable(onClick = onNavigateToOcr)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🔬", fontSize = 18.sp)
                                    }
                                    Column {
                                        Text(
                                            text = "Text OCR",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "Offline character extraction",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Premium Full-Width Image Utilities Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clickable(onClick = onNavigateToImageTools)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🖼️", fontSize = 24.sp)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Image Lab",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Compress, resize, rotate, or convert images fully offline",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                Text("➔", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
                HomeTab.History -> {
                    HistoryScreen()
                }
            }
        }
    }
}

/**
 * 4-column document selector hubs.
 */
@Composable
fun DocumentHubItem(
    title: String,
    iconText: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = iconText,
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Generic header representing categorised tool sections.
 */
@Composable
fun CategoryHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Highly polished, responsive card representing a specific workspace tool action.
 */
@Composable
fun ToolItemCard(
    title: String,
    description: String,
    iconText: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = iconText,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Visual card representing a cached recent file item.
 */
@Composable
fun RecentFileCard(
    file: RecentFile,
    onClick: () -> Unit
) {
    val (typeChar, themeColor) = when {
        file.mimeType.contains("pdf", ignoreCase = true) -> "PDF" to Color(0xFFEF4444)
        file.mimeType.contains("sheet", ignoreCase = true) || file.mimeType.contains("excel", ignoreCase = true) -> "XLS" to Color(0xFF10B981)
        file.mimeType.contains("word", ignoreCase = true) || file.mimeType.contains("document", ignoreCase = true) -> "DOC" to Color(0xFF3B82F6)
        file.mimeType.contains("presentation", ignoreCase = true) || file.mimeType.contains("powerpoint", ignoreCase = true) -> "PPT" to Color(0xFFF59E0B)
        else -> "TXT" to Color(0xFF64748B)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(150.dp)
            .height(130.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(themeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = typeChar,
                        color = themeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Column {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFileSize(file.fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
