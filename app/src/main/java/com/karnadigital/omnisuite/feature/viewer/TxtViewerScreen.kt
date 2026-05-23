package com.karnadigital.omnisuite.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Build

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Premium Plain Text Viewer and Editor Core.
 * Allows safe stream reading/writing offline and distraction-free visual canvas editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxtViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    viewModel: TxtViewerViewModel = hiltViewModel()
) {
    LaunchedEffect(fileUri) {
        viewModel.loadTextFile(fileUri)
    }

    val state by viewModel.loadState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var textContent by remember { mutableStateOf("") }
    var isInitialized by remember { mutableStateOf(false) }

    // Synchronize content state when loaded successfully
    val currentState = state
    if (currentState is TxtLoadState.Success && !isInitialized) {
        textContent = currentState.content
        isInitialized = true
    }

    // Capture save confirmation signals to trigger success/failure Snackbars
    LaunchedEffect(viewModel) {
        viewModel.saveStatus.collect { success ->
            if (success) {
                snackbarHostState.showSnackbar(
                    message = "Changes saved offline successfully",
                    duration = SnackbarDuration.Short
                )
            } else {
                snackbarHostState.showSnackbar(
                    message = "Failed to write changes back to local storage",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }


    var showFormatting by remember { mutableStateOf(false) }
    var fontSize by remember { mutableFloatStateOf(16f) }
    var themeIndex by remember { mutableIntStateOf(0) }
    val themes = listOf(
        Color.White to Color.Black, // Light
        Color(0xFFF4ECD8) to Color(0xFF5B4636), // Sepia
        Color(0xFF2D2D2D) to Color(0xFFE0E0E0), // Cinematic Dark
        Color(0xFF1E1E1E) to Color(0xFFA0A0A0) // Grey
    )
    val currentTheme = themes[themeIndex]

    Scaffold(

        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentState) {
                            is TxtLoadState.Success -> currentState.fileName
                            else -> "Plain Text Editor"
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

                        IconButton(onClick = { showFormatting = !showFormatting }) {
                            Icon(Icons.Default.Build, contentDescription = "Format")
                        }

                    if (state is TxtLoadState.Success) {
                        IconButton(
                            onClick = {
                                viewModel.saveTextFile(textContent)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save changes",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
                .background(currentTheme.first)
        ) {
            when (currentState) {
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
                            text = "Reading stream safely...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = currentTheme.second.copy(alpha = 0.7f)
                        )
                    }
                }
                is TxtLoadState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        TextField(
                            value = textContent,
                            onValueChange = { textContent = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .minimumInteractiveComponentSize(),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                color = currentTheme.second,
                                lineHeight = (fontSize + 6).sp
                            ),
                            placeholder = {
                                Text(
                                    text = "Start typing documents...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSize.sp,
                                    color = currentTheme.second.copy(alpha = 0.4f)
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }
                }
                is TxtLoadState.Error -> {
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
                            text = "Failed to open document",
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

        // Formatting & Word Count Analyser Drawer
        if (showFormatting) {
            ModalBottomSheet(
                onDismissRequest = { showFormatting = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Reader View & Formatting",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // Reader Theme Presets
                    Column {
                        Text(
                            text = "Reader Theme Preset",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf("Paper", "Sepia", "Dark", "Grey").forEachIndexed { idx, name ->
                                val isSelected = themeIndex == idx
                                val swatchColors = themes[idx]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(swatchColors.first)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { themeIndex = idx },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name,
                                        color = swatchColors.second,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Font Size Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Font Scale Size",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${fontSize.toInt()} sp",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                        }
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            valueRange = 12f..32f,
                            steps = 9
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Word Count & Estimated Reading Time Metrics
                    Text(
                        text = "Word Count Analyser",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Calculate metrics dynamically
                    val paragraphs = if (textContent.isBlank()) 0 else textContent.split(Regex("\n+")).filter { it.isNotBlank() }.size
                    val words = if (textContent.isBlank()) 0 else textContent.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    val charsWithSpaces = textContent.length
                    val charsWithoutSpaces = textContent.filter { !it.isWhitespace() }.length
                    val readingTimeMin = (words / 200.0).let { if (it > 0 && it < 1.0) 1 else it.toInt() }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Paragraphs Count", fontSize = 13.sp)
                                Text("$paragraphs", fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Words Count", fontSize = 13.sp)
                                Text("$words", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Characters (with spaces)", fontSize = 13.sp)
                                Text("$charsWithSpaces", fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Characters (no spaces)", fontSize = 13.sp)
                                Text("$charsWithoutSpaces", fontWeight = FontWeight.Bold)
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⏱️", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Estimated Reading Time", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    text = if (words == 0) "0 min" else "$readingTimeMin min",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

