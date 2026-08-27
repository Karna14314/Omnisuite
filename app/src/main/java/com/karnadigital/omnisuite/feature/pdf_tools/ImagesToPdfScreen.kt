package com.karnadigital.omnisuite.feature.pdf_tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.util.FileOutputManager
import com.karnadigital.omnisuite.di.coreEntryPoint
import com.karnadigital.omnisuite.ui.theme.OmniColors
import com.karnadigital.omnisuite.ui.component.OperationResultBottomSheet
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Collections

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesToPdfScreen(
    onNavigateBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    viewModel: PdfToolsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val fileOutputManager = coreEntryPoint(context).fileOutputManager()
    val coroutineScope = rememberCoroutineScope()
    
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var outputFileName by remember { mutableStateOf("compiled_images") }
    var isProcessing by remember { mutableStateOf(false) }

    var showBottomSheet by remember { mutableStateOf(false) }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var resultSize by remember { mutableStateOf(0L) }
    var resultFileName by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages = selectedImages + uris
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Images to PDF Converter",
                        fontWeight = FontWeight.Bold,
                        color = OmniColors.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = OmniColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OmniColors.Bg
                )
            )
        },
        containerColor = OmniColors.Bg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (selectedImages.isEmpty()) {
                // Beautiful Empty State Add Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                        .background(OmniColors.Surface, RoundedCornerShape(16.dp))
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Add Photos",
                            tint = OmniColors.PdfRed,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Add Images to Convert",
                            style = MaterialTheme.typography.titleMedium,
                            color = OmniColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap here to select multiple photos from your device gallery. You can then reorder, rename, and compile them to a high-quality PDF offline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OmniColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                // Selected Images Grid
                Text(
                    text = "Selected Images (${selectedImages.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OmniColors.TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(selectedImages) { index, uri ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = OmniColors.Surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Selected thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            fontWeight = FontWeight.Bold,
                                            color = OmniColors.TextPrimary,
                                            fontSize = 12.sp
                                        )
                                        Row {
                                            if (index > 0) {
                                                IconButton(
                                                    onClick = {
                                                        val mutable = selectedImages.toMutableList()
                                                        Collections.swap(mutable, index, index - 1)
                                                        selectedImages = mutable
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowUp,
                                                        contentDescription = "Move Up",
                                                        tint = OmniColors.TextPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            if (index < selectedImages.size - 1) {
                                                IconButton(
                                                    onClick = {
                                                        val mutable = selectedImages.toMutableList()
                                                        Collections.swap(mutable, index, index + 1)
                                                        selectedImages = mutable
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowDown,
                                                        contentDescription = "Move Down",
                                                        tint = OmniColors.TextPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        selectedImages = selectedImages.filterIndexed { i, _ -> i != index }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = OmniColors.Surface.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clickable { imagePickerLauncher.launch("image/*") }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add More",
                                        tint = OmniColors.PdfRed
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add More",
                                        fontSize = 12.sp,
                                        color = OmniColors.TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = OmniColors.Surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Output Options",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OmniColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = outputFileName,
                        onValueChange = { outputFileName = it },
                        label = { Text("Output PDF Filename") },
                        placeholder = { Text("compiled_images") },
                        suffix = { Text(".pdf") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OmniColors.PdfRed,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (selectedImages.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { selectedImages = emptyList() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = OmniColors.TextPrimary
                        )
                    ) {
                        Text("Clear All")
                    }
                }

                Button(
                    onClick = {
                        if (selectedImages.isEmpty()) {
                            imagePickerLauncher.launch("image/*")
                        } else {
                            coroutineScope.launch {
                                isProcessing = true
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        val pdf = PDDocument()
                                        selectedImages.forEach { imageUri ->
                                            val page = PDPage(PDRectangle.A4)
                                            pdf.addPage(page)
                                            val contentStream = PDPageContentStream(pdf, page)
                                            
                                            val bitmap = context.contentResolver.openInputStream(imageUri).use { stream ->
                                                BitmapFactory.decodeStream(stream)
                                            } ?: throw Exception("Failed to decode image from gallery")
                                            
                                            val pdImage = LosslessFactory.createFromImage(pdf, bitmap)
                                            
                                            val pageWidth = page.mediaBox.width
                                            val pageHeight = page.mediaBox.height
                                            val imageWidth = bitmap.width.toFloat()
                                            val imageHeight = bitmap.height.toFloat()
                                            
                                            val ratio = Math.min((pageWidth - 40f) / imageWidth, (pageHeight - 40f) / imageHeight)
                                            val drawWidth = imageWidth * ratio
                                            val drawHeight = imageHeight * ratio
                                            val x = (pageWidth - drawWidth) / 2f
                                            val y = (pageHeight - drawHeight) / 2f
                                            
                                            contentStream.drawImage(pdImage, x, y, drawWidth, drawHeight)
                                            contentStream.close()
                                            bitmap.recycle()
                                        }

                                        val stream = ByteArrayOutputStream()
                                        pdf.save(stream)
                                        pdf.close()
                                        val bytes = stream.toByteArray()
                                        
                                        val filename = if (outputFileName.isBlank()) "compiled_images.pdf" else "${outputFileName.trim()}.pdf"
                                        val savedUri = fileOutputManager.saveToDefault(
                                            bytes = bytes,
                                            filename = filename,
                                            mimeType = "application/pdf",
                                            subfolder = ""
                                        )
                                        Pair(savedUri, bytes.size.toLong())
                                    }

                                    val outputUri = result.first
                                    val fileSize = result.second

                                    if (outputUri != null) {
                                        val filename = outputUri.path?.substringAfterLast('/') ?: "compiled_images.pdf"
                                        viewModel.registerRecentFile(
                                            RecentFile(
                                                fileUri = outputUri.toString(),
                                                fileName = filename,
                                                mimeType = "application/pdf",
                                                fileSize = fileSize,
                                                lastOpened = System.currentTimeMillis(),
                                                isOperation = true
                                            )
                                        )

                                        resultUri = outputUri
                                        resultSize = fileSize
                                        resultFileName = filename
                                        showBottomSheet = true
                                    } else {
                                        Toast.makeText(context, "Failed to save compiled PDF", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(2f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OmniColors.PdfRed,
                        contentColor = Color.White
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        val label = if (selectedImages.isEmpty()) "Select Images" else "Compile to PDF"
                        Text(
                            text = label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    OperationResultBottomSheet(
        show = showBottomSheet,
        onDismiss = {
            showBottomSheet = false
            selectedImages = emptyList()
        },
        title = "PDF Compiled Successfully",
        fileName = resultFileName,
        fileUri = resultUri?.toString(),
        fileSize = resultSize,
        mimeType = "application/pdf",
        onOpenFile = onOpenFile
    )
}
