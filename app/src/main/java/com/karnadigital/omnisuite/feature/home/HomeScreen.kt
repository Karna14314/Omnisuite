package com.karnadigital.omnisuite.feature.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.feature.history.HistoryScreen
import com.karnadigital.omnisuite.feature.settings.SettingsScreen
import com.karnadigital.omnisuite.feature.tools.AllToolsScreen
import com.karnadigital.omnisuite.ui.component.*
import com.karnadigital.omnisuite.ui.theme.OmniColors
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = hiltViewModel(),
    onEvent: (NavigationEvent) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Home) }
    var searchQuery by remember { mutableStateOf("") }
    var lastRequestedType by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val fileName = getFileName(context, it) ?: "file"
                    val fileExtension = fileName.substringAfterLast('.').lowercase(Locale.ROOT)

                    val type = lastRequestedType
                    if (type != null) {
                        val isValid = when (type) {
                            "pdf" -> fileExtension == "pdf"
                            "word" -> fileExtension in listOf("docx", "doc", "odt")
                            "excel" -> fileExtension in listOf("xlsx", "xls", "ods", "csv")
                            "slides" -> fileExtension in listOf("pptx", "ppt", "odp")
                            "image" -> fileExtension in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
                            "text" -> fileExtension in listOf("txt", "py", "kt", "java", "json", "xml", "html", "css", "js", "gradle", "sh", "bat", "cpp", "c", "md", "properties")
                            "csv" -> fileExtension == "csv"
                            "zip" -> fileExtension == "zip"
                            else -> true
                        }

                        if (!isValid) {
                            val formatMessage = when (type) {
                                "pdf" -> "PDF (.pdf)"
                                "word" -> "Word Document (.docx, .doc)"
                                "excel" -> "Excel Spreadsheet (.xlsx, .xls, .csv)"
                                "slides" -> "PowerPoint Slides (.pptx, .ppt)"
                                "image" -> "Image (.png, .jpg, .webp)"
                                "text" -> "Text File (.txt, .json, .xml)"
                                "csv" -> "CSV Sheet (.csv)"
                                "zip" -> "ZIP Archive (.zip)"
                                else -> "valid file"
                            }
                            Toast.makeText(context, "Invalid format! Please select a $formatMessage.", Toast.LENGTH_LONG).show()
                            lastRequestedType = null
                            return@launch
                        }
                    }

                    lastRequestedType = null

                    val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    try {
                        context.contentResolver.takePersistableUriPermission(it, takeFlags)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val fileSize = getFileSize(context, it)
                    val mimeType = context.contentResolver.getType(it) ?: when {
                        fileName.endsWith(".pdf") -> "application/pdf"
                        fileName.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        fileName.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        fileName.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                        fileName.endsWith(".txt") -> "text/plain"
                        fileName.endsWith(".csv") -> "text/csv"
                        fileName.endsWith(".zip") -> "application/zip"
                        else -> "*/*"
                    }

                    viewModel.addRecentFile(
                        fileUri = it.toString(),
                        fileName = fileName,
                        mimeType = mimeType,
                        fileSize = fileSize
                    )
                    onEvent(NavigationEvent.OpenFile(it.toString()))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Import error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val onSelectFileForType: (String) -> Unit = { type ->
        lastRequestedType = if (type == "any") null else type
        when (type) {
            "pdf" -> documentLauncher.launch(arrayOf("application/pdf"))
            "word" -> documentLauncher.launch(arrayOf(
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.sun.xml.writer",
                "application/vnd.oasis.opendocument.text"
            ))
            "excel" -> documentLauncher.launch(arrayOf(
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/comma-separated-values",
                "text/csv"
            ))
            "slides" -> documentLauncher.launch(arrayOf(
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.oasis.opendocument.presentation"
            ))
            "image" -> documentLauncher.launch(arrayOf("image/*"))
            "text" -> documentLauncher.launch(arrayOf("text/plain"))
            "csv" -> documentLauncher.launch(arrayOf("text/csv", "text/comma-separated-values"))
            "zip" -> documentLauncher.launch(arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/x-zip"
            ))
            else -> documentLauncher.launch(arrayOf("*/*"))
        }
    }

    Scaffold(
        bottomBar = {
            OmniBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        containerColor = OmniColors.Bg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                HomeTab.Home -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        OmniTopBar(
                            showActions = true,
                            onNotificationsClick = {
                                Toast.makeText(context, "OmniSuite is operating 100% offline.", Toast.LENGTH_SHORT).show()
                            },
                            onSettingsClick = { selectedTab = HomeTab.Settings }
                        )

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search files, tools...", color = OmniColors.TextMuted) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon", tint = OmniColors.TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OmniColors.Accent,
                                unfocusedBorderColor = OmniColors.Border,
                                focusedContainerColor = OmniColors.Surface2,
                                unfocusedContainerColor = OmniColors.Surface2,
                                focusedTextColor = OmniColors.TextPrimary,
                                unfocusedTextColor = OmniColors.TextPrimary
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        SectionHeader(title = "Quick Open & Tools")
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HomeGridToolCard(
                                    title = "📋 PDF Reader",
                                    bgColor = OmniColors.PdfRedBg,
                                    borderColor = OmniColors.PdfRed.copy(alpha = 0.4f),
                                    textColor = OmniColors.PdfRed,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSelectFileForType("pdf") }
                                )
                                HomeGridToolCard(
                                    title = "📝 Word Viewer",
                                    bgColor = OmniColors.DocBlueBg,
                                    borderColor = OmniColors.DocBlue.copy(alpha = 0.4f),
                                    textColor = OmniColors.DocBlue,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSelectFileForType("word") }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HomeGridToolCard(
                                    title = "📊 Excel Viewer",
                                    bgColor = OmniColors.XlsGreenBg,
                                    borderColor = OmniColors.XlsGreen.copy(alpha = 0.4f),
                                    textColor = OmniColors.XlsGreen,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSelectFileForType("excel") }
                                )
                                HomeGridToolCard(
                                    title = "🖼️ Slides Viewer",
                                    bgColor = Color(0x1FF59E0B),
                                    borderColor = Color(0xFFF59E0B).copy(alpha = 0.4f),
                                    textColor = Color(0xFFF59E0B),
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSelectFileForType("slides") }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HomeGridToolCard(
                                    title = "📸 Image Viewer",
                                    bgColor = OmniColors.ImgPurpleBg,
                                    borderColor = OmniColors.ImgPurple.copy(alpha = 0.4f),
                                    textColor = OmniColors.ImgPurple,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSelectFileForType("image") }
                                )
                                HomeGridToolCard(
                                    title = "📦 ZIP Explorer",
                                    bgColor = OmniColors.ArcCyanBg,
                                    borderColor = OmniColors.ArcCyan.copy(alpha = 0.4f),
                                    textColor = OmniColors.ArcCyan,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSelectFileForType("zip") }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HomeGridToolCard(
                                    title = "📲 QR Scanner",
                                    bgColor = Color(0x1F0F9D58),
                                    borderColor = Color(0xFF0F9D58).copy(alpha = 0.4f),
                                    textColor = Color(0xFF0F9D58),
                                    modifier = Modifier.weight(1f),
                                    onClick = { onEvent(NavigationEvent.NavigateToBarcodeScanner) }
                                )
                                HomeGridToolCard(
                                    title = "🎨 Image Lab",
                                    bgColor = Color(0x1FE91E63),
                                    borderColor = Color(0xFFE91E63).copy(alpha = 0.4f),
                                    textColor = Color(0xFFE91E63),
                                    modifier = Modifier.weight(1f),
                                    onClick = { onEvent(NavigationEvent.NavigateToImageTools) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        SectionHeader(title = "Local File Explorer")
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(OmniColors.Surface2)
                                    .border(1.dp, OmniColors.Border, RoundedCornerShape(14.dp))
                                    .clickable { selectedTab = HomeTab.Files }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(OmniColors.Accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "💾", fontSize = 17.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Device File Manager",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OmniColors.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Browse downloads, documents, and local workspaces",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OmniColors.TextMuted
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = "Open",
                                    tint = OmniColors.TextMuted
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(OmniColors.Surface2)
                                    .border(1.dp, OmniColors.Border, RoundedCornerShape(14.dp))
                                    .clickable { onSelectFileForType("any") }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(OmniColors.Accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "📂", fontSize = 17.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Open Supported Document",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OmniColors.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Open PDF, Word, Excel, Slide, ZIP, or Text file",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OmniColors.TextMuted
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = "Open",
                                    tint = OmniColors.TextMuted
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
                HomeTab.Tools -> {
                    AllToolsScreen(
                        isInline = true,
                        onBack = { selectedTab = HomeTab.Home },
                        onEvent = onEvent,
                        onSelectFileForType = onSelectFileForType
                    )
                }
                HomeTab.Files -> {
                    FilesScreen(
                        onSelectFileForType = onSelectFileForType,
                        onNavigateToBarcodeScanner = { onEvent(NavigationEvent.NavigateToBarcodeScanner) },
                        onNavigateToScanToPdf = { onEvent(NavigationEvent.NavigateToScanToPdf) },
                        onOpenFile = { onEvent(NavigationEvent.OpenFile(it)) }
                    )
                }
                HomeTab.History -> {
                    HistoryScreen(
                        onOpenFile = { onEvent(NavigationEvent.OpenFile(it)) }
                    )
                }
                HomeTab.Settings -> {
                    SettingsScreen(
                        onBack = { selectedTab = HomeTab.Home }
                    )
                }
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Unknown_File"
}

private fun getFileSize(context: Context, uri: Uri): Long {
    var result: Long = 0L
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        result = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    } else if (uri.scheme == "file") {
        try {
            val file = java.io.File(uri.path ?: "")
            if (file.exists()) {
                result = file.length()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return result
}

@Composable
private fun HomeGridToolCard(
    title: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
