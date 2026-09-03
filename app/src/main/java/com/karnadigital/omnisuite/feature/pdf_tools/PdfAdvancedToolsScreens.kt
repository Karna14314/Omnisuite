package com.karnadigital.omnisuite.feature.pdf_tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfCompareScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker1 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { viewModel.compareUri1 = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} } }
    val filePicker2 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { viewModel.compareUri2 = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} } }

    Scaffold(topBar = { TopAppBar(title = { Text("Compare PDF Documents", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker1.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Compare, contentDescription = null, tint = if (viewModel.compareUri1 != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (viewModel.compareUri1 != null) "PDF 1 Selected" else "Select PDF 1", style = MaterialTheme.typography.titleMedium)
                }
            }
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker2.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Compare, contentDescription = null, tint = if (viewModel.compareUri2 != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (viewModel.compareUri2 != null) "PDF 2 Selected" else "Select PDF 2", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (viewModel.compareUri1 != null && viewModel.compareUri2 != null) {
                Button(onClick = { viewModel.comparePdf() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Compare, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Compare Documents") }
                }
            }
            if (viewModel.compareResult != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Comparison Result:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(viewModel.compareResult!!, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfMetadataScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.metadataInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Edit PDF Metadata", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = if (viewModel.metadataInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.metadataInputUri != null) "PDF Selected" else "Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.metadataInputUri != null) {
                OutlinedTextField(value = viewModel.metadataTitle, onValueChange = { viewModel.metadataTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = viewModel.metadataAuthor, onValueChange = { viewModel.metadataAuthor = it }, label = { Text("Author") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = viewModel.metadataSubject, onValueChange = { viewModel.metadataSubject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = viewModel.metadataKeywords, onValueChange = { viewModel.metadataKeywords = it }, label = { Text("Keywords") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { viewModel.editMetadata() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Save, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Save Metadata") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfBookmarkScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.bookmarkInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("PDF Outline & Bookmarks", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = if (viewModel.bookmarkInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.bookmarkInputUri != null) "PDF Selected" else "Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.bookmarkInputUri != null) {
                OutlinedTextField(value = viewModel.bookmarkTitles, onValueChange = { viewModel.bookmarkTitles = it }, label = { Text("Bookmark Titles (one per line)") }, modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 10)
                OutlinedTextField(value = viewModel.bookmarkPages, onValueChange = { viewModel.bookmarkPages = it }, label = { Text("Target Page Numbers (one per line, 1-based)") }, modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 10)
                Button(onClick = { viewModel.editBookmarks() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Bookmark, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Apply Bookmarks") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}
