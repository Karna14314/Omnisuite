package com.karnadigital.omnisuite.feature.pdf_tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File
import java.io.FileOutputStream

data class DrawPoint(val x: Float, val y: Float)

/**
 * Landscape digital signature capture canvas and dynamic PDF visual stamping screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturePadScreen(
    onBack: () -> Unit,
    viewModel: SignatureViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Navigation and screen sub-states
    var signatureSaved by remember { mutableStateOf(false) }
    var localSignatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var localSignatureFile by remember { mutableStateOf<File?>(null) }

    // Canvas drawing path tracking
    val strokes = remember { mutableStateListOf<List<DrawPoint>>() }
    val currentStroke = remember { mutableStateListOf<DrawPoint>() }
    var canvasWidth by remember { mutableStateOf(0) }
    var canvasHeight by remember { mutableStateOf(0) }

    // Tap and stamp tracking
    var tapOffset by remember { mutableStateOf<Offset?>(null) }
    var imageContainerWidth by remember { mutableStateOf(0f) }
    var imageContainerHeight by remember { mutableStateOf(0f) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let { viewModel.selectPdf(it) }
        }
    )

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri ->
            uri?.let { outputUri ->
                tapOffset?.let { tap ->
                    viewModel.stampSignature(
                        tapX = tap.x,
                        tapY = tap.y,
                        viewWidth = imageContainerWidth,
                        viewHeight = imageContainerHeight,
                        outputUri = outputUri
                    )
                }
            }
        }
    )

    // Watch status messages
    LaunchedEffect(viewModel.successMessage) {
        viewModel.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.resetStatus()
        }
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.resetStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (!signatureSaved) "Draw Digital Signature" else "Stamp PDF Document",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (signatureSaved) {
                            // Reset back to drawing pad
                            signatureSaved = false
                            localSignatureBitmap = null
                            strokes.clear()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = if (signatureSaved) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Go Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = signatureSaved,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                },
                label = "ScreenStateTransition"
            ) { isSaved ->
                if (!isSaved) {
                    // DRAWING PAD WORKSPACE
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Draw your signature on the white canvas below using your finger.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )

                        // Elegant Drawing Canvas surface
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .shadow(2.dp, RoundedCornerShape(12.dp))
                                .onGloballyPositioned {
                                    canvasWidth = it.size.width
                                    canvasHeight = it.size.height
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentStroke.clear()
                                            currentStroke.add(DrawPoint(offset.x, offset.y))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val pos = change.position
                                            if (pos.x in 0f..canvasWidth.toFloat() && pos.y in 0f..canvasHeight.toFloat()) {
                                                currentStroke.add(DrawPoint(pos.x, pos.y))
                                            }
                                        },
                                        onDragEnd = {
                                            if (currentStroke.isNotEmpty()) {
                                                strokes.add(currentStroke.toList())
                                                currentStroke.clear()
                                            }
                                        }
                                    )
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Draw completed paths
                                strokes.forEach { stroke ->
                                    if (stroke.size > 1) {
                                        for (i in 0 until stroke.size - 1) {
                                            drawLine(
                                                color = Color.Black,
                                                start = Offset(stroke[i].x, stroke[i].y),
                                                end = Offset(stroke[i + 1].x, stroke[i + 1].y),
                                                strokeWidth = 7f,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }
                                // Draw current path in real time
                                if (currentStroke.size > 1) {
                                    for (i in 0 until currentStroke.size - 1) {
                                        drawLine(
                                            color = Color.Black,
                                            start = Offset(currentStroke[i].x, currentStroke[i].y),
                                            end = Offset(currentStroke[i + 1].x, currentStroke[i + 1].y),
                                            strokeWidth = 7f,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }

                            // Empty placeholder indicator
                            if (strokes.isEmpty() && currentStroke.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Touch here to sign",
                                        color = Color.Gray.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }

                        // Toolbar controls row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = {
                                    strokes.clear()
                                    currentStroke.clear()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clear")
                            }

                            Button(
                                onClick = {
                                    if (strokes.isNotEmpty()) {
                                        strokes.removeAt(strokes.size - 1)
                                    }
                                },
                                enabled = strokes.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = "Undo")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Undo")
                            }

                            Button(
                                onClick = {
                                    if (strokes.isEmpty()) {
                                        Toast.makeText(context, "Please sign on the canvas first.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    // Save canvas paths to local bitmap offline
                                    val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
                                    val androidCanvas = android.graphics.Canvas(bitmap)
                                    androidCanvas.drawColor(android.graphics.Color.TRANSPARENT)

                                    val paint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.BLACK
                                        strokeWidth = 12f
                                        style = android.graphics.Paint.Style.STROKE
                                        strokeCap = android.graphics.Paint.Cap.ROUND
                                        strokeJoin = android.graphics.Paint.Join.ROUND
                                        isAntiAlias = true
                                    }

                                    strokes.forEach { stroke ->
                                        if (stroke.size > 1) {
                                            val p = android.graphics.Path()
                                            p.moveTo(stroke[0].x, stroke[0].y)
                                            for (i in 1 until stroke.size) {
                                                p.lineTo(stroke[i].x, stroke[i].y)
                                            }
                                            androidCanvas.drawPath(p, paint)
                                        }
                                    }

                                    val file = File(context.cacheDir, "sig_${System.currentTimeMillis()}.png")
                                    FileOutputStream(file).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }

                                    localSignatureFile = file
                                    viewModel.setSignatureFile(file)

                                    // Decode transparent signature for on-screen preview
                                    localSignatureBitmap = BitmapFactory.decodeFile(file.absolutePath)
                                    signatureSaved = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Accept")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Accept")
                            }
                        }
                    }
                } else {
                    // PDF SELECTION AND STAMP PLACEMENT WORKSPACE
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (viewModel.selectedPdfUri == null) {
                            // Let user pick PDF
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    .clickable { pdfLauncher.launch("application/pdf") },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "PDF Picker Icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Text(
                                        text = "Import PDF Document",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Tap here to select a PDF to sign.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            // Display rendered PDF page
                            Text(
                                text = "Tap exactly where on the page you want to stamp the signature.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    .onGloballyPositioned {
                                        imageContainerWidth = it.size.width.toFloat()
                                        imageContainerHeight = it.size.height.toFloat()
                                    }
                                    .pointerInput(Unit) {
                                        detectTapGestures { offset ->
                                            tapOffset = offset
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                viewModel.renderedPageBitmap?.let { pageBmp ->
                                    Box(
                                        modifier = Modifier.fillMaxHeight().aspectRatio(pageBmp.width.toFloat() / pageBmp.height.toFloat())
                                    ) {
                                        Image(
                                            bitmap = pageBmp.asImageBitmap(),
                                            contentDescription = "PDF Page View",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )

                                        // Draw responsive interactive visual overlay boundary indicator
                                        tapOffset?.let { tap ->
                                            localSignatureBitmap?.let { sigBmp ->
                                                Image(
                                                    bitmap = sigBmp.asImageBitmap(),
                                                    contentDescription = "Signature Preview Overlay",
                                                    modifier = Modifier
                                                        .offset {
                                                            IntOffset(
                                                                (tap.x - 70).toInt(),
                                                                (tap.y - 35).toInt()
                                                            )
                                                        }
                                                        .size(140.dp, 70.dp)
                                                        .border(1.dp, Color.Red.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                                                        .background(Color.White.copy(alpha = 0.35f))
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Navigation page cycler controllers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.prevPage() },
                                    enabled = viewModel.currentPageIndex > 0
                                ) {
                                    Icon(Icons.Default.ArrowBackIos, contentDescription = "Prev page")
                                }

                                Text(
                                    text = "Page ${viewModel.currentPageIndex + 1} of ${viewModel.pageCount}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                IconButton(
                                    onClick = { viewModel.nextPage() },
                                    enabled = viewModel.currentPageIndex < viewModel.pageCount - 1
                                ) {
                                    Icon(Icons.Default.ArrowForwardIos, contentDescription = "Next page")
                                }
                            }

                            // Dynamic Stamp Action Controller
                            Button(
                                onClick = {
                                    if (tapOffset == null) {
                                        Toast.makeText(context, "Please tap on the page first to position the signature.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val defaultSignedName = "Signed_${System.currentTimeMillis()}.pdf"
                                    savePdfLauncher.launch(defaultSignedName)
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                enabled = tapOffset != null
                            ) {
                                Icon(Icons.Default.Create, contentDescription = "Stamp")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stamp Signature & Save PDF")
                            }
                        }
                    }
                }
            }

            // Processing overlay dialog spinner
            if (viewModel.isProcessing) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = { Text("Processing Document", fontWeight = FontWeight.Bold) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("Applying offline changes...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                )
            }
        }
    }
}
