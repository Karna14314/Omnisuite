package com.karnadigital.omnisuite.feature.viewer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Reflowable Word Document (DOCX) mobile viewer and interactive editor engine.
 * Renders paragraphs as rich e-book typography layouts or editable fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocxViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    viewModel: DocxViewerViewModel = hiltViewModel()
) {
    LaunchedEffect(fileUri) {
        viewModel.loadWordFile(fileUri)
    }

    val state by viewModel.loadState.collectAsState()
    var isEditMode by remember { mutableStateOf(false) }
    var showAppendDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri ->
            uri?.let {
                isExporting = true
                viewModel.exportToPdf(
                    context = context,
                    outputUri = it,
                    onSuccess = {
                        isExporting = false
                        Toast.makeText(context, "Document exported to PDF successfully!", Toast.LENGTH_LONG).show()
                    },
                    onFailure = { error ->
                        isExporting = false
                        Toast.makeText(context, "Export failed: $error", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    )

    LaunchedEffect(Unit) {
        viewModel.saveStatus.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (val s = state) {
                            is DocxLoadState.Success -> s.fileName
                            else -> "Document Viewer"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                actions = {
                    if (state is DocxLoadState.Success) {
                        var showMenu by remember { mutableStateOf(false) }

                        if (isEditMode) {
                            IconButton(onClick = { viewModel.commitChanges() }) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Commit changes",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { isEditMode = !isEditMode }) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Close else Icons.Default.Edit,
                                contentDescription = "Toggle Edit Mode"
                            )
                        }
                        
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options"
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export to PDF") },
                                onClick = {
                                    showMenu = false
                                    val currentSuccess = state as DocxLoadState.Success
                                    val defaultName = currentSuccess.fileName.substringBeforeLast(".") + ".pdf"
                                    exportPdfLauncher.launch(defaultName)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "PDF Export"
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            if (isEditMode && state is DocxLoadState.Success) {
                ExtendedFloatingActionButton(
                    onClick = { showAppendDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Append paragraph") },
                    text = { Text("Append") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val currentState = state) {
                is DocxLoadState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Reflowing document pages...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
                is DocxLoadState.Success -> {
                    val document = currentState.document
                    if (document.paragraphs.isEmpty()) {
                        EmptyDocumentState()
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                        ) {
                            itemsIndexed(document.paragraphs) { index, paragraph ->
                                if (isEditMode) {
                                    DocxParagraphEditorItem(
                                        index = index,
                                        paragraph = paragraph,
                                        onTextChange = { updatedText ->
                                            viewModel.updateParagraph(index, updatedText)
                                        }
                                    )
                                } else {
                                    DocxParagraphItem(paragraph)
                                }
                            }
                        }
                    }
                }
                is DocxLoadState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error Icon",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Word Read Error",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showAppendDialog) {
        var newParagraphText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAppendDialog = false },
            title = { Text("Append New Paragraph") },
            text = {
                OutlinedTextField(
                    value = newParagraphText,
                    onValueChange = { newParagraphText = it },
                    label = { Text("Paragraph Text") },
                    placeholder = { Text("Type paragraph content here...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newParagraphText.isNotBlank()) {
                            viewModel.appendParagraph(newParagraphText)
                        }
                        showAppendDialog = false
                    }
                ) {
                    Text("Append")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAppendDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isExporting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Exporting PDF", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("Converting document offline...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        )
    }
}

@Composable
fun DocxParagraphEditorItem(
    index: Int,
    paragraph: DocxParagraph,
    onTextChange: (String) -> Unit
) {
    var textState by remember(paragraph) { 
        mutableStateOf(paragraph.runs.joinToString("") { it.text }) 
    }

    OutlinedTextField(
        value = textState,
        onValueChange = {
            textState = it
            onTextChange(it)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        textStyle = if (paragraph.isHeading) {
            MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        label = { Text("Paragraph ${index + 1}") }
    )
}

@Composable
fun DocxParagraphItem(paragraph: DocxParagraph) {
    val annotatedString = remember(paragraph) {
        buildAnnotatedString {
            paragraph.runs.forEach { run ->
                val start = length
                append(run.text)
                val end = length

                val spanStyle = SpanStyle(
                    fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = when {
                        run.isUnderline && run.isStrike -> TextDecoration.Underline + TextDecoration.LineThrough
                        run.isUnderline -> TextDecoration.Underline
                        run.isStrike -> TextDecoration.LineThrough
                        else -> TextDecoration.None
                    }
                )
                addStyle(spanStyle, start, end)
            }
        }
    }

    val textAlign = when (paragraph.alignment) {
        "CENTER" -> TextAlign.Center
        "RIGHT" -> TextAlign.Right
        "JUSTIFY" -> TextAlign.Justify
        else -> TextAlign.Left
    }

    val style = if (paragraph.isHeading) {
        MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 28.sp,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        MaterialTheme.typography.bodyLarge.copy(
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    val verticalPadding = if (paragraph.isHeading) 12.dp else 6.dp

    Text(
        text = annotatedString,
        style = style,
        textAlign = textAlign,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = verticalPadding)
    )
}

@Composable
fun EmptyDocumentState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Empty",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Document Contains No Text",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
