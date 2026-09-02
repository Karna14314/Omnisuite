package com.karnadigital.omnisuite.feature.utility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterScreen(onBack: () -> Unit, viewModel: UtilityToolsViewModel = hiltViewModel()) {
    var category by remember { mutableStateOf("Length") }
    var fromUnit by remember { mutableStateOf("m") }
    var toUnit by remember { mutableStateOf("ft") }
    var inputValue by remember { mutableStateOf("1") }
    val unitResult by viewModel.unitResult.collectAsState()
    val categories = remember { mutableMapOf(
        "Length" to listOf("mm" to "mm", "cm" to "cm", "m" to "m", "km" to "km", "in" to "in", "ft" to "ft", "yd" to "yd", "mi" to "mi"),
        "Weight" to listOf("mg" to "mg", "g" to "g", "kg" to "kg", "t" to "t", "oz" to "oz", "lb" to "lb"),
        "Temperature" to listOf("C" to "°C", "F" to "°F", "K" to "K"),
        "Area" to listOf("sqm" to "m²", "sqkm" to "km²", "sqft" to "ft²", "acre" to "acre", "ha" to "ha"),
        "Volume" to listOf("ml" to "ml", "l" to "L", "gal" to "gal", "cup" to "cup"),
        "Speed" to listOf("mps" to "m/s", "kmh" to "km/h", "mph" to "mph"),
        "Time" to listOf("s" to "sec", "min" to "min", "hr" to "hr", "day" to "day"),
        "Data" to listOf("B" to "Byte", "KB" to "KB", "MB" to "MB", "GB" to "GB", "TB" to "TB")
    )}

    LaunchedEffect(category) {
        val units = categories[category] ?: emptyList()
        if (units.isNotEmpty()) { fromUnit = units[0].first; toUnit = units.getOrNull(1)?.first ?: units[0].first }
    }
    LaunchedEffect(inputValue, fromUnit, toUnit, category) {
        val value = inputValue.toDoubleOrNull() ?: 0.0
        viewModel.convertUnit(value, fromUnit, toUnit, category)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Unit Converter", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(value = category, onValueChange = {}, readOnly = true, label = { Text("Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.keys.forEach { cat -> DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; expanded = false }) }
                }
            }
            OutlinedTextField(value = inputValue, onValueChange = { inputValue = it }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var fromExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = fromExpanded, onExpandedChange = { fromExpanded = it }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(value = fromUnit, onValueChange = {}, readOnly = true, label = { Text("From") }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = fromExpanded, onDismissRequest = { fromExpanded = false }) {
                        (categories[category] ?: emptyList()).forEach { (key, label) -> DropdownMenuItem(text = { Text("$label ($key)") }, onClick = { fromUnit = key; fromExpanded = false }) }
                    }
                }
                var toExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = toExpanded, onExpandedChange = { toExpanded = it }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(value = toUnit, onValueChange = {}, readOnly = true, label = { Text("To") }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = toExpanded, onDismissRequest = { toExpanded = false }) {
                        (categories[category] ?: emptyList()).forEach { (key, label) -> DropdownMenuItem(text = { Text("$label ($key)") }, onClick = { toUnit = key; toExpanded = false }) }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(text = "$inputValue $fromUnit = $unitResult $toUnit", modifier = Modifier.fillMaxWidth().padding(24.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerScreen(onBack: () -> Unit, viewModel: UtilityToolsViewModel = hiltViewModel()) {
    val colorPicked by viewModel.colorPicked.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Color Picker", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How to use", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tap on any image in the Image Viewer to pick colors. The color picker shows the hex code and RGB values of the selected pixel.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (colorPicked != null) {
                val color = colorPicked!!
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(8.dp)).background(Color(color)))
                        Text("HEX: #${Integer.toHexString(color).uppercase().padStart(8, '0')}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("RGB: ${android.graphics.Color.red(color)}, ${android.graphics.Color.green(color)}, ${android.graphics.Color.blue(color)}", style = MaterialTheme.typography.bodyMedium)
                        Text("ARGB: ${android.graphics.Color.alpha(color)}, ${android.graphics.Color.red(color)}, ${android.graphics.Color.green(color)}, ${android.graphics.Color.blue(color)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No color picked yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageMakerScreen(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Collage Maker", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Collage Maker", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select multiple images to create a photo collage. Choose from grid, 2x2, or 3x3 layouts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Layouts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Grid", "2x2", "3x3").forEachIndexed { index, label ->
                    FilterChip(selected = false, onClick = {}, label = { Text(label) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemeMakerScreen(onBack: () -> Unit) {
    var topText by remember { mutableStateOf("") }
    var bottomText by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text("Meme Maker", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = topText, onValueChange = { topText = it }, label = { Text("Top text") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = bottomText, onValueChange = { bottomText = it }, label = { Text("Bottom text") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How to use", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select an image from the Image Viewer, then add top and bottom text to create a meme. Text is automatically converted to uppercase.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
