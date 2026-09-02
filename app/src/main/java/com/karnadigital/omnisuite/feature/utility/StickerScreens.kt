package com.karnadigital.omnisuite.feature.utility

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerMakerScreen(onBack: () -> Unit, viewModel: UtilityToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var stickerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var stickerName by remember { mutableStateOf("") }
    var removeBg by remember { mutableStateOf(true) }
    var bgThreshold by remember { mutableFloatStateOf(30f) }
    var selectMode by remember { mutableStateOf(false) }
    var selectionStart by remember { mutableStateOf(Offset.Zero) }
    var selectionEnd by remember { mutableStateOf(Offset.Zero) }
    var savedStickers by remember { mutableStateOf<List<File>>(emptyList()) }
    var showSaved by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                sourceBitmap = BitmapFactory.decodeStream(stream)
            }
        }
    }

    LaunchedEffect(Unit) {
        savedStickers = viewModel.utilityToolsRepository.loadStickers(context)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Sticker Maker", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (sourceBitmap == null) {
                Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("image/*")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(48.dp))
                        Text("Tap to select image", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp).pointerInput(selectMode) {
                        detectDragGestures(
                            onDragStart = { selectionStart = it },
                            onDrag = { change, _ -> selectionEnd = change.position }
                        )
                    }) {
                        Image(bitmap = sourceBitmap!!.asImageBitmap(), contentDescription = "Source", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        if (selectMode && selectionStart != selectionEnd) {
                            val left = minOf(selectionStart.x, selectionEnd.x)
                            val top = minOf(selectionStart.y, selectionEnd.y)
                            val right = maxOf(selectionStart.x, selectionEnd.x)
                            val bottom = maxOf(selectionStart.y, selectionEnd.y)
                            Box(modifier = Modifier.offset(x = left.dp, y = top.dp).size(width = (right - left).dp, height = (bottom - left).dp).border(2.dp, Color.Red))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectMode, onClick = { selectMode = !selectMode }, label = { Text("Select") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = removeBg, onClick = { removeBg = !removeBg }, label = { Text("Remove BG") }, modifier = Modifier.weight(1f))
                }
                if (removeBg) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("BG Threshold", style = MaterialTheme.typography.bodySmall); Text("${bgThreshold.toInt()}", style = MaterialTheme.typography.bodySmall) }
                        Slider(value = bgThreshold, onValueChange = { bgThreshold = it }, valueRange = 10f..100f)
                    }
                }
                Button(onClick = {
                    val bmp = sourceBitmap ?: return@Button
                    val left = selectionStart.x.toInt().coerceIn(0, bmp.width - 1)
                    val top = selectionStart.y.toInt().coerceIn(0, bmp.height - 1)
                    val right = selectionEnd.x.toInt().coerceIn(left + 1, bmp.width)
                    val bottom = selectionEnd.y.toInt().coerceIn(top + 1, bmp.height)
                    stickerBitmap = viewModel.utilityToolsRepository.extractSticker(bmp, left, top, right, bottom, removeBg, bgThreshold.toInt())
                }, modifier = Modifier.fillMaxWidth(), enabled = selectMode && selectionStart != selectionEnd) {
                    Icon(Icons.Default.ContentCut, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Extract Sticker")
                }
                if (stickerBitmap != null) {
                    Text("Extracted Sticker:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Image(bitmap = stickerBitmap!!.asImageBitmap(), contentDescription = "Sticker", modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)).background(Color.Gray.copy(alpha = 0.2f)))
                    OutlinedTextField(value = stickerName, onValueChange = { stickerName = it }, label = { Text("Sticker name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        if (stickerName.isNotBlank() && stickerBitmap != null) {
                            viewModel.utilityToolsRepository.saveSticker(context, stickerBitmap!!, stickerName)
                            savedStickers = viewModel.utilityToolsRepository.loadStickers(context)
                            stickerName = ""
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = stickerName.isNotBlank()) {
                        Icon(Icons.Default.Save, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Save Sticker")
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Saved Stickers (${savedStickers.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { showSaved = !showSaved }) { Text(if (showSaved) "Hide" else "Show") }
            }
            if (showSaved && savedStickers.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedStickers) { file ->
                        Image(bitmap = BitmapFactory.decodeFile(file.absolutePath).asImageBitmap(), contentDescription = file.nameWithoutExtension, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.1f)))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerImportScreen(onBack: () -> Unit, viewModel: UtilityToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var savedStickers by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedSticker by remember { mutableStateOf<File?>(null) }
    var stickerX by remember { mutableFloatStateOf(50f) }
    var stickerY by remember { mutableFloatStateOf(50f) }
    var stickerSize by remember { mutableFloatStateOf(100f) }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> backgroundBitmap = BitmapFactory.decodeStream(stream) } }
    }

    LaunchedEffect(Unit) { savedStickers = viewModel.utilityToolsRepository.loadStickers(context) }

    Scaffold(topBar = { TopAppBar(title = { Text("Import Sticker", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (backgroundBitmap == null) {
                Card(modifier = Modifier.fillMaxWidth().clickable { bgPicker.launch(arrayOf("image/*")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(48.dp))
                        Text("Tap to select background image", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        Image(bitmap = (resultBitmap ?: backgroundBitmap!!).asImageBitmap(), contentDescription = "Preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
                Text("Select Sticker:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedStickers) { file ->
                        val isSelected = selectedSticker == file
                        Image(bitmap = BitmapFactory.decodeFile(file.absolutePath).asImageBitmap(), contentDescription = file.nameWithoutExtension, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)).border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent).clickable {
                            selectedSticker = file
                            val bg = backgroundBitmap ?: return@clickable
                            val sticker = BitmapFactory.decodeFile(file.absolutePath) ?: return@clickable
                            resultBitmap = viewModel.utilityToolsRepository.placeSticker(bg, sticker, stickerX.toInt(), stickerY.toInt(), stickerSize.toInt(), stickerSize.toInt())
                        })
                    }
                }
                if (selectedSticker != null) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Size"); Text("${stickerSize.toInt()}px") }
                        Slider(value = stickerSize, onValueChange = { stickerSize = it; val bg = backgroundBitmap; val st = selectedSticker; if (bg != null && st != null) { resultBitmap = viewModel.utilityToolsRepository.placeSticker(bg, BitmapFactory.decodeFile(st.absolutePath), stickerX.toInt(), stickerY.toInt(), stickerSize.toInt(), stickerSize.toInt()) } }, valueRange = 30f..300f)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("X: ${stickerX.toInt()}", style = MaterialTheme.typography.bodySmall)
                            Slider(value = stickerX, onValueChange = { stickerX = it; val bg = backgroundBitmap; val st = selectedSticker; if (bg != null && st != null) { resultBitmap = viewModel.utilityToolsRepository.placeSticker(bg, BitmapFactory.decodeFile(st.absolutePath), stickerX.toInt(), stickerY.toInt(), stickerSize.toInt(), stickerSize.toInt()) } }, valueRange = 0f..500f)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Y: ${stickerY.toInt()}", style = MaterialTheme.typography.bodySmall)
                            Slider(value = stickerY, onValueChange = { stickerY = it; val bg = backgroundBitmap; val st = selectedSticker; if (bg != null && st != null) { resultBitmap = viewModel.utilityToolsRepository.placeSticker(bg, BitmapFactory.decodeFile(st.absolutePath), stickerX.toInt(), stickerY.toInt(), stickerSize.toInt(), stickerSize.toInt()) } }, valueRange = 0f..500f)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExactResizeScreen(onBack: () -> Unit, viewModel: UtilityToolsViewModel = hiltViewModel()) {
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var widthInput by remember { mutableStateOf("500") }
    var heightInput by remember { mutableStateOf("500") }
    var keepAspectRatio by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf("") }
    val presets = listOf("500x500 (Icon)", "1080x1080 (Instagram)", "1200x628 (LinkedIn)", "1024x768 (Tablet)", "256x256 (Favicon)", "1080x1920 (Story)")

    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                sourceBitmap = BitmapFactory.decodeStream(stream)
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Exact Resize", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (sourceBitmap == null) {
                Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("image/*")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoSizeSelectLarge, contentDescription = null, modifier = Modifier.size(48.dp))
                        Text("Tap to select image", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("Original: ${sourceBitmap!!.width}x${sourceBitmap!!.height}", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
                Text("Presets:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { preset ->
                        FilterChip(selected = selectedPreset == preset, onClick = {
                            selectedPreset = preset
                            val dims = preset.substringBefore(" ").split("x")
                            if (dims.size == 2) { widthInput = dims[0]; heightInput = dims[1] }
                        }, label = { Text(preset.substringBefore(" "), fontSize = 11.sp) })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = widthInput, onValueChange = { widthInput = it.filter { c -> c.isDigit() } }, label = { Text("Width (px)") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = heightInput, onValueChange = { heightInput = it.filter { c -> c.isDigit() } }, label = { Text("Height (px)") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = keepAspectRatio, onCheckedChange = { keepAspectRatio = it }); Text("Keep aspect ratio") }
                Button(onClick = {
                    val w = widthInput.toIntOrNull() ?: return@Button
                    val h = heightInput.toIntOrNull() ?: return@Button
                    resultBitmap = viewModel.utilityToolsRepository.resizeExact(sourceBitmap!!, w, h, keepAspectRatio)
                }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = widthInput.isNotBlank() && heightInput.isNotBlank()) {
                    Icon(Icons.Default.PhotoSizeSelectLarge, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Resize")
                }
                if (resultBitmap != null) {
                    Text("Result: ${resultBitmap!!.width}x${resultBitmap!!.height}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Image(bitmap = resultBitmap!!.asImageBitmap(), contentDescription = "Result", modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAloudScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var isSpeaking by remember { mutableStateOf(false) }
    var pitch by remember { mutableFloatStateOf(1.0f) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    val tts = remember { android.speech.tts.TextToSpeech(context) { } }

    DisposableEffect(Unit) {
        onDispose { tts.stop(); tts.shutdown() }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Read Aloud", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Lightweight offline text-to-speech. Paste text or load from a document to have it read aloud. Uses Android's built-in TTS engine.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Enter or paste text to read aloud") }, modifier = Modifier.fillMaxWidth().height(200.dp), maxLines = 20)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (text.isNotBlank()) {
                        tts.language = java.util.Locale.getDefault()
                        tts.setPitch(pitch)
                        tts.setSpeechRate(speed)
                        tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "readaloud")
                        isSpeaking = true
                    }
                }, modifier = Modifier.weight(1f), enabled = text.isNotBlank() && !isSpeaking) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Play")
                }
                OutlinedButton(onClick = { tts.stop(); isSpeaking = false }, modifier = Modifier.weight(1f), enabled = isSpeaking) {
                    Icon(Icons.Default.Stop, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Stop")
                }
            }
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Speed", style = MaterialTheme.typography.bodySmall); Text("${String.format("%.1f", speed)}x", style = MaterialTheme.typography.bodySmall) }
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.5f..2.0f)
            }
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Pitch", style = MaterialTheme.typography.bodySmall); Text("${String.format("%.1f", pitch)}", style = MaterialTheme.typography.bodySmall) }
                Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.5f..2.0f)
            }
        }
    }
}
