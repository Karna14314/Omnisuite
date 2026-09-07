package com.karnadigital.omnisuite.feature.pdf_tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfHeaderFooterScreen(
    onBack: () -> Unit,
    viewModel: PdfToolsViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.headerFooterInputUri = it
            try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
    }

    val thumbnails = rememberPdfThumbnails(context, viewModel.headerFooterInputUri)
    var alignment by remember { mutableStateOf("Center") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Header & Footer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.headerFooterInputUri != null) {
                        IconButton(onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Change PDF")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (viewModel.headerFooterInputUri == null || thumbnails.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { filePickerLauncher.launch(arrayOf("application/pdf")) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "Select PDF Document",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Add custom document header stamps and footer page numbers with real-time preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // Live Page Preview Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Live Document Preview (Page 1)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        val firstBmp = thumbnails.firstOrNull()
                        if (firstBmp != null) {
                            val aspect = firstBmp.width.toFloat() / firstBmp.height.toFloat()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .aspectRatio(aspect)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                    .background(Color.White)
                            ) {
                                // Background page bitmap
                                Image(
                                    bitmap = firstBmp.asImageBitmap(),
                                    contentDescription = "Page Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )

                                // Live Header Overlay
                                val headerDisplay = viewModel.headerFooterHeaderText.ifEmpty { "Header Stamp" }
                                val alignOffset = when (alignment) {
                                    "Left" -> Alignment.TopStart
                                    "Right" -> Alignment.TopEnd
                                    else -> Alignment.TopCenter
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .background(Color.White.copy(alpha = 0.85f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = headerDisplay,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = (viewModel.headerFooterFontSize * 0.8f).sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.DarkGray
                                        ),
                                        modifier = Modifier.align(alignOffset),
                                        maxLines = 1
                                    )
                                }

                                // Live Footer Overlay
                                val footerDisplay = viewModel.headerFooterFooterText.ifEmpty { "Page 1 of ${thumbnails.size}" }
                                val footerAlign = when (alignment) {
                                    "Left" -> Alignment.BottomStart
                                    "Right" -> Alignment.BottomEnd
                                    else -> Alignment.BottomCenter
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(Color.White.copy(alpha = 0.85f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = footerDisplay,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = (viewModel.headerFooterFontSize * 0.8f).sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.DarkGray
                                        ),
                                        modifier = Modifier.align(footerAlign),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Controls
                OutlinedTextField(
                    value = viewModel.headerFooterHeaderText,
                    onValueChange = { viewModel.headerFooterHeaderText = it },
                    label = { Text("Header text (e.g. Confidential, Company Name)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.VerticalAlignTop, contentDescription = null) }
                )

                OutlinedTextField(
                    value = viewModel.headerFooterFooterText,
                    onValueChange = { viewModel.headerFooterFooterText = it },
                    label = { Text("Footer text (e.g. Page {page} of {total})") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.VerticalAlignBottom, contentDescription = null) }
                )

                // Alignment Chips
                Text("Text Alignment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Left", "Center", "Right").forEach { pos ->
                        FilterChip(
                            selected = alignment == pos,
                            onClick = { alignment = pos },
                            label = { Text(pos) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Font Size", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("${viewModel.headerFooterFontSize}pt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = viewModel.headerFooterFontSize.toFloat(),
                        onValueChange = { viewModel.headerFooterFontSize = it.toInt() },
                        valueRange = 8f..24f,
                        steps = 15
                    )
                }

                Button(
                    onClick = { viewModel.addHeaderFooter() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !viewModel.isProcessing
                ) {
                    if (viewModel.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Title, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Header & Footer to ${thumbnails.size} Pages")
                    }
                }
            }

            if (viewModel.successMessage != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(viewModel.successMessage!!, color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (viewModel.errorMessage != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    com.karnadigital.omnisuite.ui.component.OperationResultBottomSheet(
        show = viewModel.successUri != null,
        onDismiss = { viewModel.resetStatus() },
        title = "Header & Footer Applied",
        fileName = viewModel.successName,
        fileUri = viewModel.successUri?.toString(),
        mimeType = "application/pdf"
    )
}
