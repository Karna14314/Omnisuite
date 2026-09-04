package com.karnadigital.omnisuite.feature.utility

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEncryptScreen(onBack: () -> Unit, viewModel: UtilityToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.fileEncryptInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("AES File Encryption", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("*/*")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = if (viewModel.fileEncryptInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.fileEncryptInputUri != null) "File Selected" else "Tap to select file to encrypt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.fileEncryptInputUri != null) {
                OutlinedTextField(value = viewModel.fileEncryptPassword, onValueChange = { viewModel.fileEncryptPassword = it }, label = { Text("Encryption Key / Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                Button(onClick = { viewModel.encryptFile() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing && viewModel.fileEncryptPassword.isNotBlank()) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.EnhancedEncryption, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Encrypt File (AES-256)") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDecryptScreen(onBack: () -> Unit, viewModel: UtilityToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.fileDecryptInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("AES File Decryption", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("*/*")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.NoEncryption, contentDescription = null, tint = if (viewModel.fileDecryptInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.fileDecryptInputUri != null) "File Selected" else "Tap to select file to decrypt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.fileDecryptInputUri != null) {
                OutlinedTextField(value = viewModel.fileDecryptPassword, onValueChange = { viewModel.fileDecryptPassword = it }, label = { Text("Decryption Key / Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                Button(onClick = { viewModel.decryptFile() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing && viewModel.fileDecryptPassword.isNotBlank()) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.NoEncryption, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Decrypt File") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileChecksumScreen(onBack: () -> Unit, viewModel: UtilityToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.checksumInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("File Checksum (MD5 / SHA-256)", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("*/*")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Tag, contentDescription = null, tint = if (viewModel.checksumInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.checksumInputUri != null) "File Selected" else "Tap to select file for checksum", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.checksumInputUri != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = viewModel.checksumAlgorithm == "SHA-256", onClick = { viewModel.checksumAlgorithm = "SHA-256" }, label = { Text("SHA-256") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = viewModel.checksumAlgorithm == "MD5", onClick = { viewModel.checksumAlgorithm = "MD5" }, label = { Text("MD5") }, modifier = Modifier.weight(1f))
                }
                Button(onClick = { viewModel.getFileChecksum() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Calculate, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Calculate Hash") }
                }
            }
            if (viewModel.checksumResult != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Checksum Hash:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer { Text(viewModel.checksumResult!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextCompareScreen(onBack: () -> Unit, viewModel: UtilityToolsViewModel = hiltViewModel()) {
    Scaffold(topBar = { TopAppBar(title = { Text("Text Comparison Diff", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = viewModel.textCompare1, onValueChange = { viewModel.textCompare1 = it }, label = { Text("Original Text") }, modifier = Modifier.fillMaxWidth().height(140.dp), maxLines = 10)
            OutlinedTextField(value = viewModel.textCompare2, onValueChange = { viewModel.textCompare2 = it }, label = { Text("Modified Text") }, modifier = Modifier.fillMaxWidth().height(140.dp), maxLines = 10)
            Button(onClick = { viewModel.compareText() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing) {
                if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else { Icon(Icons.Default.Compare, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Compare Text Diff") }
            }
            if (viewModel.textCompareResult != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Diff Result:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer { Text(viewModel.textCompareResult!!, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedWordCountScreen(onBack: () -> Unit, viewModel: UtilityToolsViewModel = hiltViewModel()) {
    var text by remember { mutableStateOf("") }
    var wordCountResult by remember { mutableStateOf<Map<String, Any>?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Advanced Word Count", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Enter or paste text") }, modifier = Modifier.fillMaxWidth().height(200.dp), maxLines = 20)
            Button(onClick = { wordCountResult = viewModel.getWordCount(text) }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = text.isNotBlank()) {
                Icon(Icons.Default.Calculate, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Analyze Text")
            }
            if (wordCountResult != null) {
                val result = wordCountResult!!
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Analysis Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Words:"); Text("${result["words"]}", fontWeight = FontWeight.Bold) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Characters:"); Text("${result["characters"]}", fontWeight = FontWeight.Bold) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Lines:"); Text("${result["lines"]}", fontWeight = FontWeight.Bold) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Estimated Read Time:"); Text("${result["readingTime"]}", fontWeight = FontWeight.Bold) }
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
