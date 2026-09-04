package com.karnadigital.omnisuite.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.engine.EncodingDetector
import com.karnadigital.omnisuite.core.engine.SyntaxHighlighter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxtViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    onToolAction: (ViewerTool) -> Unit = {},
    viewModel: TxtViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var isInitialized by remember { mutableStateOf(false) }
    val loadState by viewModel.loadState.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(-1) }
    var showReplace by remember { mutableStateOf(false) }

    var showGoToLine by remember { mutableStateOf(false) }
    var goToLineInput by remember { mutableStateOf("") }

    var showEncodingPicker by remember { mutableStateOf(false) }
    var currentEncoding by remember { mutableStateOf("UTF-8") }
    var detectedEncoding by remember { mutableStateOf<String?>(null) }

    var fontSize by remember { mutableFloatStateOf(14f) }
    var showLineNumbers by remember { mutableStateOf(true) }
    var wordWrap by remember { mutableStateOf(false) }
    var themeIndex by remember { mutableIntStateOf(0) }

    val fileExtension = remember {
        fileUri.substringAfterLast('.', "").lowercase()
    }

    val themes = listOf(
        Color.White to Color.Black,
        Color(0xFFF4ECD8) to Color(0xFF5B4636),
        Color(0xFF1E1E1E) to Color(0xFFD4D4D4),
        Color(0xFF2D2D2D) to Color(0xFFA0A0A0)
    )
    val currentTheme = themes[themeIndex]

    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    val isCodeFile = fileExtension in setOf(
        "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "c", "h", "cpp",
        "hpp", "cc", "cxx", "cs", "php", "sql", "html", "htm", "css", "xml",
        "json", "yaml", "yml", "md", "markdown", "gradle", "groovy", "properties",
        "ini", "cfg", "conf", "config", "log", "csv", "tsv", "sh", "bash", "rb",
        "go", "rs", "swift", "dart", "scala", "r", "lua", "pl"
    )

    LaunchedEffect(fileUri) {
        withContext(Dispatchers.IO) {
            val file = File(fileUri)
            if (file.exists()) {
                val encoding = EncodingDetector.detectEncoding(file)
                val encodingName = when (encoding.charset) {
                    StandardCharsets.UTF_8 -> "UTF-8"
                    StandardCharsets.UTF_16LE -> "UTF-16LE"
                    StandardCharsets.UTF_16BE -> "UTF-16BE"
                    StandardCharsets.US_ASCII -> "ASCII"
                    StandardCharsets.ISO_8859_1 -> "ISO-8859-1"
                    else -> encoding.charset.name()
                }
                detectedEncoding = encodingName
                currentEncoding = encodingName
                val content = EncodingDetector.readTextWithEncoding(file, encoding.charset)
                textFieldValue = TextFieldValue(content)
                isInitialized = true
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.saveStatus.collect { success ->
            if (success) {
                snackbarHostState.showSnackbar("Changes saved successfully")
            } else {
                snackbarHostState.showSnackbar("Failed to save changes")
            }
        }
    }

    fun performSearch(query: String, content: String) {
        if (query.isEmpty()) {
            searchResults = emptyList()
            currentMatchIndex = -1
            return
        }
        val matches = mutableListOf<Int>()
        var idx = content.indexOf(query, 0, ignoreCase = true)
        while (idx >= 0) {
            matches.add(idx)
            idx = content.indexOf(query, idx + query.length, ignoreCase = true)
        }
        searchResults = matches
        currentMatchIndex = if (matches.isNotEmpty()) 0 else -1
    }

    fun replaceAll(query: String, replacement: String, content: String): String {
        if (query.isEmpty()) return content
        return content.replace(query, replacement, ignoreCase = true)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (showSearch) {
                Surface(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                showSearch = false
                                searchQuery = ""
                                searchResults = emptyList()
                                currentMatchIndex = -1
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    performSearch(it, textFieldValue.text)
                                },
                                placeholder = { Text("Search...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                            if (searchResults.isNotEmpty()) {
                                Text(
                                    text = "${currentMatchIndex + 1}/${searchResults.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(onClick = {
                                    if (currentMatchIndex > 0) currentMatchIndex--
                                    else currentMatchIndex = searchResults.size - 1
                                }) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
                                }
                                IconButton(onClick = {
                                    if (currentMatchIndex < searchResults.size - 1) currentMatchIndex++
                                    else currentMatchIndex = 0
                                }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                                }
                            }
                            IconButton(onClick = { showReplace = !showReplace }) {
                                Icon(
                                    if (showReplace) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle replace"
                                )
                            }
                        }
                        if (showReplace) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = replaceQuery,
                                    onValueChange = { replaceQuery = it },
                                    placeholder = { Text("Replace with...") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = {
                                    val newContent = replaceAll(searchQuery, replaceQuery, textFieldValue.text)
                                    textFieldValue = TextFieldValue(
                                        text = newContent,
                                        selection = androidx.compose.ui.text.TextRange.Zero
                                    )
                                    performSearch(searchQuery, newContent)
                                }) {
                                    Text("All")
                                }
                            }
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = fileUri.substringAfterLast('/'),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isCodeFile) {
                                Text(
                                    text = fileExtension.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        com.karnadigital.omnisuite.feature.utility.ReadAloudButton(text = textFieldValue.text)

                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showGoToLine = true }) {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = "Go to line")
                        }
                        IconButton(onClick = { showEncodingPicker = true }) {
                            Icon(Icons.Default.Code, contentDescription = "Encoding")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val file = File(fileUri)
                                    val charset = try {
                                        Charset.forName(currentEncoding)
                                    } catch (e: Exception) {
                                        StandardCharsets.UTF_8
                                    }
                                    file.bufferedWriter(charset).use { it.write(textFieldValue.text) }
                                }
                                viewModel.emitSaveStatus(true)
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                        }
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        var showRenameDialog by remember { mutableStateOf(false) }

                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename document") },
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (showLineNumbers) "Hide line numbers" else "Show line numbers") },
                                onClick = {
                                    showMenu = false
                                    showLineNumbers = !showLineNumbers
                                },
                                leadingIcon = {
                                    Icon(
                                        if (showLineNumbers) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (wordWrap) "Disable word wrap" else "Enable word wrap") },
                                onClick = {
                                    showMenu = false
                                    wordWrap = !wordWrap
                                },
                                leadingIcon = { Icon(Icons.Default.WrapText, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Change theme") },
                                onClick = {
                                    showMenu = false
                                    themeIndex = (themeIndex + 1) % themes.size
                                },
                                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) }
                            )
                        }

                        if (showRenameDialog) {
                            var newNameInput by remember { mutableStateOf(fileUri.substringAfterLast('/')) }
                            AlertDialog(
                                onDismissRequest = { showRenameDialog = false },
                                title = { Text("Rename Document", fontWeight = FontWeight.Bold) },
                                text = {
                                    OutlinedTextField(
                                        value = newNameInput,
                                        onValueChange = { newNameInput = it },
                                        label = { Text("New File Name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (newNameInput.isNotBlank()) {
                                                val oldFile = File(fileUri)
                                                if (oldFile.exists() && oldFile.isFile) {
                                                    val ext = oldFile.extension
                                                    val cleanName = if (newNameInput.contains(".")) newNameInput else if (ext.isNotBlank()) "$newNameInput.$ext" else newNameInput
                                                    val newFile = File(oldFile.parentFile, cleanName)
                                                    if (oldFile.renameTo(newFile)) {
                                                        Toast.makeText(context, "Renamed to $cleanName", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                            showRenameDialog = false
                                        }
                                    ) {
                                        Text("Rename")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRenameDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // M365 Text Editing Ribbon Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canUndo by viewModel.canUndo.collectAsState()
                        val canRedo by viewModel.canRedo.collectAsState()

                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = canUndo,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                        }

                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = canRedo,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                        }

                        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { fontSize = maxOf(8f, fontSize - 1f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.TextDecrease, contentDescription = "Decrease font", modifier = Modifier.size(18.dp))
                            }
                            Text(
                                text = "${fontSize.toInt()}pt",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            IconButton(
                                onClick = { fontSize = minOf(32f, fontSize + 1f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.TextIncrease, contentDescription = "Increase font", modifier = Modifier.size(18.dp))
                            }
                        }

                        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                        FilterChip(
                            selected = showLineNumbers,
                            onClick = { showLineNumbers = !showLineNumbers },
                            label = { Text("Lines", fontSize = 11.sp) }
                        )

                        FilterChip(
                            selected = wordWrap,
                            onClick = { wordWrap = !wordWrap },
                            label = { Text("Wrap", fontSize = 11.sp) }
                        )

                        FilterChip(
                            selected = false,
                            onClick = { showEncodingPicker = true },
                            label = { Text(currentEncoding, fontSize = 11.sp) }
                        )

                        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                        Button(
                            onClick = {
                                viewModel.saveTextFile(textFieldValue.text)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Document Metadata Footer Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val lineCount = textFieldValue.text.count { it == '\n' } + 1
                        val wordCount = if (textFieldValue.text.isBlank()) 0
                        else textFieldValue.text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                        val charCount = textFieldValue.text.length

                        Text(
                            text = "Ln $lineCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$wordCount words",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$charCount chars",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentEncoding,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(currentTheme.first)
        ) {
            when (val currentLoadState = loadState) {
                is TxtLoadState.Loading -> {
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
                            text = "Loading file...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = currentTheme.second.copy(alpha = 0.7f)
                        )
                    }
                }
                is TxtLoadState.Success -> {
                    val lines = textFieldValue.text.split("\n")
                    val lineCountStr = lines.size.toString()
                    val gutterWidth = if (showLineNumbers) {
                        (lineCountStr.length * (fontSize * 0.6f) + 16f).dp
                    } else 0.dp

                    val textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        color = currentTheme.second,
                        lineHeight = (fontSize * 1.5f).sp
                    )

                    val searchMatchOffset = if (currentMatchIndex in searchResults.indices) {
                        searchResults[currentMatchIndex]
                    } else -1

                    LaunchedEffect(searchMatchOffset) {
                        if (searchMatchOffset >= 0) {
                            val text = textFieldValue.text
                            val lineNum = text.substring(0, searchMatchOffset).count { it == '\n' }
                            val lineHeight = fontSize * 1.5f
                            val targetScroll = ((lineNum - 5) * lineHeight).toInt().coerceAtLeast(0)
                            verticalScrollState.animateScrollTo(targetScroll)
                        }
                    }

                    val annotatedText = if (isCodeFile) {
                        SyntaxHighlighter.highlight(textFieldValue.text, fileExtension)
                    } else {
                        androidx.compose.ui.text.AnnotatedString(textFieldValue.text)
                    }

                    if (wordWrap) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScrollState)
                                .padding(8.dp)
                        ) {
                            if (showLineNumbers) {
                                Row {
                                    Column(
                                        modifier = Modifier.width(gutterWidth),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        lines.forEachIndexed { index, _ ->
                                            Text(
                                                text = "${index + 1}",
                                                style = textStyle.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    fontSize = (fontSize * 0.85f).sp
                                                ),
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                        }
                                    }
                                    BasicTextField(
                                        value = textFieldValue,
                                        onValueChange = { textFieldValue = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = textStyle,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        decorationBox = { innerTextField ->
                                            innerTextField()
                                        }
                                    )
                                }
                            } else {
                                SelectionContainer {
                                    BasicTextField(
                                        value = textFieldValue,
                                        onValueChange = { textFieldValue = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = textStyle,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (showLineNumbers) {
                                Column(
                                    modifier = Modifier
                                        .width(gutterWidth)
                                        .fillMaxHeight()
                                        .verticalScroll(verticalScrollState)
                                        .background(currentTheme.first.copy(alpha = 0.95f))
                                        .padding(end = 8.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    lines.forEachIndexed { index, _ ->
                                        Text(
                                            text = "${index + 1}",
                                            style = textStyle.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                fontSize = (fontSize * 0.85f).sp
                                            )
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    modifier = Modifier.fillMaxHeight().width(1.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .horizontalScroll(horizontalScrollState)
                                    .verticalScroll(verticalScrollState)
                                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                            ) {
                                SelectionContainer {
                                    BasicTextField(
                                        value = textFieldValue,
                                        onValueChange = { textFieldValue = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = textStyle,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
                is TxtLoadState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Failed to open file",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentLoadState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showGoToLine) {
        Dialog(onDismissRequest = { showGoToLine = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Go to Line",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = goToLineInput,
                        onValueChange = { goToLineInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Line number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val lineNum = goToLineInput.toIntOrNull()
                                if (lineNum != null && lineNum > 0) {
                                    val lines = textFieldValue.text.split("\n")
                                    if (lineNum <= lines.size) {
                                        var offset = 0
                                        for (i in 0 until lineNum - 1) {
                                            offset += lines[i].length + 1
                                        }
                                        textFieldValue = textFieldValue.copy(
                                            selection = androidx.compose.ui.text.TextRange(offset)
                                        )
                                        showGoToLine = false
                                    }
                                }
                            }
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showGoToLine = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            val lineNum = goToLineInput.toIntOrNull()
                            if (lineNum != null && lineNum > 0) {
                                val lines = textFieldValue.text.split("\n")
                                if (lineNum <= lines.size) {
                                    var offset = 0
                                    for (i in 0 until lineNum - 1) {
                                        offset += lines[i].length + 1
                                    }
                                    textFieldValue = textFieldValue.copy(
                                        selection = androidx.compose.ui.text.TextRange(offset)
                                    )
                                    showGoToLine = false
                                }
                            }
                        }) {
                            Text("Go")
                        }
                    }
                }
            }
        }
    }

    if (showEncodingPicker) {
        Dialog(onDismissRequest = { showEncodingPicker = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Text(
                        text = "Select Encoding",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (detectedEncoding != null) {
                        Text(
                            text = "Detected: $detectedEncoding",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    HorizontalDivider()
                    EncodingDetector.SUPPORTED_ENCODINGS.forEach { encoding ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentEncoding = encoding
                                    showEncodingPicker = false
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            val file = File(fileUri)
                                            val charset = try {
                                                Charset.forName(encoding)
                                            } catch (e: Exception) {
                                                StandardCharsets.UTF_8
                                            }
                                            val content = EncodingDetector.readTextWithEncoding(file, charset)
                                            textFieldValue = TextFieldValue(content)
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentEncoding == encoding,
                                onClick = {
                                    currentEncoding = encoding
                                    showEncodingPicker = false
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            val file = File(fileUri)
                                            val charset = try {
                                                Charset.forName(encoding)
                                            } catch (e: Exception) {
                                                StandardCharsets.UTF_8
                                            }
                                            val content = EncodingDetector.readTextWithEncoding(file, charset)
                                            textFieldValue = TextFieldValue(content)
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = encoding,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
