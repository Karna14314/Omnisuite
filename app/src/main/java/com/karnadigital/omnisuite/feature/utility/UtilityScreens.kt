package com.karnadigital.omnisuite.feature.utility

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.di.coreEntryPoint
import com.karnadigital.omnisuite.ui.component.OperationResultBottomSheet
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedColor by remember { mutableStateOf<Int?>(null) }
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    var imageContainerSize by remember { mutableStateOf(IntSize.Zero) }
    val recentColors = remember { mutableStateListOf<Int>() }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val original = BitmapFactory.decodeStream(stream)
                    if (original != null) {
                        val maxDim = 1280
                        val scale = min(1f, maxDim.toFloat() / max(original.width, original.height))
                        selectedBitmap = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                original,
                                (original.width * scale).toInt(),
                                (original.height * scale).toInt(),
                                true
                            )
                        } else {
                            original
                        }
                        val cx = selectedBitmap!!.width / 2
                        val cy = selectedBitmap!!.height / 2
                        val centerColor = selectedBitmap!!.getPixel(cx, cy)
                        selectedColor = centerColor
                        touchPos = null
                        if (!recentColors.contains(centerColor)) recentColors.add(0, centerColor)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Color Picker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick Image")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedBitmap == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { imagePicker.launch("image/*") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Colorize,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Tap to Select Image",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Pick any photo from gallery to sample exact color codes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(Color.DarkGray)
                            .onSizeChanged { imageContainerSize = it }
                            .pointerInput(selectedBitmap) {
                                detectTapGestures { offset ->
                                    val bmp = selectedBitmap ?: return@detectTapGestures
                                    if (imageContainerSize.width > 0 && imageContainerSize.height > 0) {
                                        val bx = (offset.x / imageContainerSize.width * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                                        val by = (offset.y / imageContainerSize.height * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                                        val sampled = bmp.getPixel(bx, by)
                                        selectedColor = sampled
                                        touchPos = offset
                                        if (!recentColors.contains(sampled)) {
                                            recentColors.add(0, sampled)
                                            if (recentColors.size > 8) recentColors.removeAt(recentColors.lastIndex)
                                        }
                                    }
                                }
                            }
                            .pointerInput(selectedBitmap) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val bmp = selectedBitmap ?: return@detectDragGestures
                                    if (imageContainerSize.width > 0 && imageContainerSize.height > 0) {
                                        val bx = (change.position.x / imageContainerSize.width * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                                        val by = (change.position.y / imageContainerSize.height * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                                        val sampled = bmp.getPixel(bx, by)
                                        selectedColor = sampled
                                        touchPos = change.position
                                        if (!recentColors.contains(sampled)) {
                                            recentColors.add(0, sampled)
                                            if (recentColors.size > 8) recentColors.removeAt(recentColors.lastIndex)
                                        }
                                    }
                                }
                            }
                    ) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Sample Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )

                        touchPos?.let { pos ->
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(
                                    color = Color.White,
                                    radius = 16.dp.toPx(),
                                    center = pos,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                )
                                drawCircle(
                                    color = Color.Black,
                                    radius = 18.dp.toPx(),
                                    center = pos,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                )
                                selectedColor?.let { c ->
                                    drawCircle(
                                        color = Color(c),
                                        radius = 13.dp.toPx(),
                                        center = pos
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    "Touch or drag across image to inspect colors",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            selectedColor?.let { color ->
                val hexString = String.format("#%06X", (0xFFFFFF and color))
                val r = android.graphics.Color.red(color)
                val g = android.graphics.Color.green(color)
                val b = android.graphics.Color.blue(color)
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(color, hsv)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(color))
                                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = hexString,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "RGB: ($r, $g, $b)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "HSV: ${hsv[0].toInt()}°, ${(hsv[1] * 100).toInt()}%, ${(hsv[2] * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(hexString))
                                Toast.makeText(context, "Copied $hexString to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy HEX Code")
                        }
                    }
                }
            }

            if (recentColors.isNotEmpty()) {
                Text("Sampled Palette", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentColors) { col ->
                        val hex = String.format("#%06X", (0xFFFFFF and col))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedColor = col
                                clipboardManager.setText(AnnotatedString(hex))
                                Toast.makeText(context, "Selected & copied $hex", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(col))
                                    .border(
                                        if (selectedColor == col) 3.dp else 1.dp,
                                        if (selectedColor == col) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(hex, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

data class CollageSlot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

enum class CollageAspectRatio(val label: String, val ratio: Float) {
    SQUARE("1:1 (Square)", 1f),
    PORTRAIT("4:5 (Post)", 4f / 5f),
    STORY("9:16 (Story)", 9f / 16f),
    LANDSCAPE("16:9 (Wide)", 16f / 9f)
}

private fun getCollageLabels(count: Int): List<String> = when (count) {
    1 -> listOf("Single")
    2 -> listOf("Split Columns", "Stacked Rows")
    3 -> listOf("Hero Top", "Hero Left", "3 Columns", "3 Rows")
    4 -> listOf("2×2 Grid", "Hero Top", "Hero Left", "4 Columns")
    5 -> listOf("2 Top + 3 Bot", "Hero Left", "Hero Top", "3 Top + 2 Bot")
    else -> listOf("3×2 Grid", "2×3 Grid", "2 Top + 4 Bot", "4 Top + 2 Bot")
}

private fun getCollageSlots(
    count: Int,
    layoutIndex: Int,
    width: Float,
    height: Float,
    gap: Float
): List<CollageSlot> {
    if (count <= 0) return emptyList()
    if (count == 1) return listOf(CollageSlot(0f, 0f, width, height))

    return when (count) {
        2 -> {
            if (layoutIndex % 2 == 0) {
                val colW = (width - gap) / 2f
                listOf(
                    CollageSlot(0f, 0f, colW, height),
                    CollageSlot(colW + gap, 0f, width, height)
                )
            } else {
                val rowH = (height - gap) / 2f
                listOf(
                    CollageSlot(0f, 0f, width, rowH),
                    CollageSlot(0f, rowH + gap, width, height)
                )
            }
        }
        3 -> {
            when (layoutIndex % 4) {
                0 -> {
                    val topH = (height - gap) / 2f
                    val botW = (width - gap) / 2f
                    listOf(
                        CollageSlot(0f, 0f, width, topH),
                        CollageSlot(0f, topH + gap, botW, height),
                        CollageSlot(botW + gap, topH + gap, width, height)
                    )
                }
                1 -> {
                    val leftW = (width - gap) / 2f
                    val rightH = (height - gap) / 2f
                    listOf(
                        CollageSlot(0f, 0f, leftW, height),
                        CollageSlot(leftW + gap, 0f, width, rightH),
                        CollageSlot(leftW + gap, rightH + gap, width, height)
                    )
                }
                2 -> {
                    val colW = (width - 2 * gap) / 3f
                    listOf(
                        CollageSlot(0f, 0f, colW, height),
                        CollageSlot(colW + gap, 0f, 2 * colW + gap, height),
                        CollageSlot(2 * (colW + gap), 0f, width, height)
                    )
                }
                else -> {
                    val rowH = (height - 2 * gap) / 3f
                    listOf(
                        CollageSlot(0f, 0f, width, rowH),
                        CollageSlot(0f, rowH + gap, width, 2 * rowH + gap),
                        CollageSlot(0f, 2 * (rowH + gap), width, height)
                    )
                }
            }
        }
        4 -> {
            when (layoutIndex % 4) {
                0 -> {
                    val cellW = (width - gap) / 2f
                    val cellH = (height - gap) / 2f
                    listOf(
                        CollageSlot(0f, 0f, cellW, cellH),
                        CollageSlot(cellW + gap, 0f, width, cellH),
                        CollageSlot(0f, cellH + gap, cellW, height),
                        CollageSlot(cellW + gap, cellH + gap, width, height)
                    )
                }
                1 -> {
                    val topH = (height - gap) * 0.58f
                    val botW = (width - 2 * gap) / 3f
                    listOf(
                        CollageSlot(0f, 0f, width, topH),
                        CollageSlot(0f, topH + gap, botW, height),
                        CollageSlot(botW + gap, topH + gap, 2 * botW + gap, height),
                        CollageSlot(2 * (botW + gap), topH + gap, width, height)
                    )
                }
                2 -> {
                    val leftW = (width - gap) * 0.58f
                    val rightH = (height - 2 * gap) / 3f
                    listOf(
                        CollageSlot(0f, 0f, leftW, height),
                        CollageSlot(leftW + gap, 0f, width, rightH),
                        CollageSlot(leftW + gap, rightH + gap, width, 2 * rightH + gap),
                        CollageSlot(leftW + gap, 2 * (rightH + gap), width, height)
                    )
                }
                else -> {
                    val colW = (width - 3 * gap) / 4f
                    listOf(
                        CollageSlot(0f, 0f, colW, height),
                        CollageSlot(colW + gap, 0f, 2 * colW + gap, height),
                        CollageSlot(2 * (colW + gap), 0f, 3 * colW + 2 * gap, height),
                        CollageSlot(3 * (colW + gap), 0f, width, height)
                    )
                }
            }
        }
        5 -> {
            when (layoutIndex % 4) {
                0 -> {
                    val halfH = (height - gap) / 2f
                    val topW = (width - gap) / 2f
                    val botW = (width - 2 * gap) / 3f
                    listOf(
                        CollageSlot(0f, 0f, topW, halfH),
                        CollageSlot(topW + gap, 0f, width, halfH),
                        CollageSlot(0f, halfH + gap, botW, height),
                        CollageSlot(botW + gap, halfH + gap, 2 * botW + gap, height),
                        CollageSlot(2 * (botW + gap), halfH + gap, width, height)
                    )
                }
                1 -> {
                    val leftW = (width - gap) / 2f
                    val rightW = (width - gap) / 2f
                    val subW = (rightW - gap) / 2f
                    val subH = (height - gap) / 2f
                    listOf(
                        CollageSlot(0f, 0f, leftW, height),
                        CollageSlot(leftW + gap, 0f, leftW + gap + subW, subH),
                        CollageSlot(leftW + 2 * gap + subW, 0f, width, subH),
                        CollageSlot(leftW + gap, subH + gap, leftW + gap + subW, height),
                        CollageSlot(leftW + 2 * gap + subW, subH + gap, width, height)
                    )
                }
                2 -> {
                    val topH = (height - gap) / 2f
                    val botH = (height - gap) / 2f
                    val subW = (width - gap) / 2f
                    val subH = (botH - gap) / 2f
                    listOf(
                        CollageSlot(0f, 0f, width, topH),
                        CollageSlot(0f, topH + gap, subW, topH + gap + subH),
                        CollageSlot(subW + gap, topH + gap, width, topH + gap + subH),
                        CollageSlot(0f, topH + 2 * gap + subH, subW, height),
                        CollageSlot(subW + gap, topH + 2 * gap + subH, width, height)
                    )
                }
                else -> {
                    val halfH = (height - gap) / 2f
                    val topW = (width - 2 * gap) / 3f
                    val botW = (width - gap) / 2f
                    listOf(
                        CollageSlot(0f, 0f, topW, halfH),
                        CollageSlot(topW + gap, 0f, 2 * topW + gap, halfH),
                        CollageSlot(2 * (topW + gap), 0f, width, halfH),
                        CollageSlot(0f, halfH + gap, botW, height),
                        CollageSlot(botW + gap, halfH + gap, width, height)
                    )
                }
            }
        }
        else -> {
            when (layoutIndex % 4) {
                0 -> {
                    val colW = (width - 2 * gap) / 3f
                    val rowH = (height - gap) / 2f
                    listOf(
                        CollageSlot(0f, 0f, colW, rowH),
                        CollageSlot(colW + gap, 0f, 2 * colW + gap, rowH),
                        CollageSlot(2 * (colW + gap), 0f, width, rowH),
                        CollageSlot(0f, rowH + gap, colW, height),
                        CollageSlot(colW + gap, rowH + gap, 2 * colW + gap, height),
                        CollageSlot(2 * (colW + gap), rowH + gap, width, height)
                    )
                }
                1 -> {
                    val colW = (width - gap) / 2f
                    val rowH = (height - 2 * gap) / 3f
                    listOf(
                        CollageSlot(0f, 0f, colW, rowH),
                        CollageSlot(colW + gap, 0f, width, rowH),
                        CollageSlot(0f, rowH + gap, colW, 2 * rowH + gap),
                        CollageSlot(colW + gap, rowH + gap, width, 2 * rowH + gap),
                        CollageSlot(0f, 2 * (rowH + gap), colW, height),
                        CollageSlot(colW + gap, 2 * (rowH + gap), width, height)
                    )
                }
                2 -> {
                    val halfH = (height - gap) / 2f
                    val topW = (width - gap) / 2f
                    val botW = (width - 3 * gap) / 4f
                    listOf(
                        CollageSlot(0f, 0f, topW, halfH),
                        CollageSlot(topW + gap, 0f, width, halfH),
                        CollageSlot(0f, halfH + gap, botW, height),
                        CollageSlot(botW + gap, halfH + gap, 2 * botW + gap, height),
                        CollageSlot(2 * (botW + gap), halfH + gap, 3 * botW + 2 * gap, height),
                        CollageSlot(3 * (botW + gap), halfH + gap, width, height)
                    )
                }
                else -> {
                    val halfH = (height - gap) / 2f
                    val topW = (width - 3 * gap) / 4f
                    val botW = (width - gap) / 2f
                    listOf(
                        CollageSlot(0f, 0f, topW, halfH),
                        CollageSlot(topW + gap, 0f, 2 * topW + gap, halfH),
                        CollageSlot(2 * (topW + gap), 0f, 3 * topW + 2 * gap, halfH),
                        CollageSlot(3 * (topW + gap), 0f, width, halfH),
                        CollageSlot(0f, halfH + gap, botW, height),
                        CollageSlot(botW + gap, halfH + gap, width, height)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageMakerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val fileOutputManager = coreEntryPoint(context).fileOutputManager()

    var selectedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedRatio by remember { mutableStateOf(CollageAspectRatio.SQUARE) }
    var layoutMode by remember { mutableIntStateOf(0) }
    var spacingDp by remember { mutableFloatStateOf(8f) }
    var cornerRadiusDp by remember { mutableFloatStateOf(8f) }
    var bgColor by remember { mutableStateOf(Color.White) }

    var showResultSheet by remember { mutableStateOf(false) }
    var resultFileName by remember { mutableStateOf<String?>(null) }
    var resultFileUri by remember { mutableStateOf<String?>(null) }
    var resultFileSize by remember { mutableStateOf(0L) }
    var isSaving by remember { mutableStateOf(false) }

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val list = mutableListOf<Bitmap>()
            for (uri in uris.take(6)) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            val maxDim = 800
                            val scale = min(1f, maxDim.toFloat() / max(bmp.width, bmp.height))
                            val scaled = if (scale < 1f) {
                                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                            } else bmp
                            list.add(scaled)
                        }
                    }
                } catch (_: Exception) { }
            }
            selectedBitmaps = list
            layoutMode = 0
        }
    }

    val appendPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val remaining = 6 - selectedBitmaps.size
            val list = selectedBitmaps.toMutableList()
            for (uri in uris.take(remaining)) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            val maxDim = 800
                            val scale = min(1f, maxDim.toFloat() / max(bmp.width, bmp.height))
                            val scaled = if (scale < 1f) {
                                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                            } else bmp
                            list.add(scaled)
                        }
                    }
                } catch (_: Exception) { }
            }
            selectedBitmaps = list
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Collage Maker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { multiPicker.launch("image/*") }) {
                        Icon(Icons.Default.Collections, contentDescription = "Select Images")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedBitmaps.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { multiPicker.launch("image/*") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Select 1 to 6 Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Combine up to 6 photos into aesthetic modern collage templates with custom borders, rounded corners, and aspect ratios", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedBitmaps.size} / 6 photos selected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { selectedBitmaps = emptyList() }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }

                // Thumbnails strip
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(selectedBitmaps.size) { idx ->
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        ) {
                            Image(
                                bitmap = selectedBitmaps[idx].asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = {
                                    selectedBitmaps = selectedBitmaps.toMutableList().also { it.removeAt(idx) }
                                },
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.TopEnd)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    if (selectedBitmaps.size < 6) {
                        item {
                            OutlinedButton(
                                onClick = { appendPicker.launch("image/*") },
                                modifier = Modifier.size(64.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Photo", modifier = Modifier.size(20.dp))
                                    Text("Add", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                // Interactive Collage Preview Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(selectedRatio.ratio)
                        .clip(RoundedCornerShape(16.dp)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().background(bgColor)) {
                        val w = size.width
                        val h = size.height
                        val pad = spacingDp.dp.toPx()
                        val cornerPx = cornerRadiusDp.dp.toPx()
                        val slots = getCollageSlots(selectedBitmaps.size, layoutMode, w, h, pad)

                        for (i in selectedBitmaps.indices) {
                            val slot = slots.getOrNull(i) ?: continue
                            val bmp = selectedBitmaps[i]
                            val srcW = bmp.width.toFloat()
                            val srcH = bmp.height.toFloat()
                            val dstW = slot.width
                            val dstH = slot.height
                            if (dstW <= 0f || dstH <= 0f || srcW <= 0f || srcH <= 0f) continue

                            val scale = maxOf(dstW / srcW, dstH / srcH)
                            val cropW = (dstW / scale).toInt().coerceIn(1, bmp.width)
                            val cropH = (dstH / scale).toInt().coerceIn(1, bmp.height)
                            val cropX = ((bmp.width - cropW) / 2).coerceIn(0, bmp.width - cropW)
                            val cropY = ((bmp.height - cropH) / 2).coerceIn(0, bmp.height - cropH)

                            val roundRect = RoundRect(
                                left = slot.left,
                                top = slot.top,
                                right = slot.right,
                                bottom = slot.bottom,
                                cornerRadius = CornerRadius(cornerPx, cornerPx)
                            )
                            val path = Path().apply { addRoundRect(roundRect) }

                            clipPath(path) {
                                drawImage(
                                    image = bmp.asImageBitmap(),
                                    srcOffset = androidx.compose.ui.unit.IntOffset(cropX, cropY),
                                    srcSize = androidx.compose.ui.unit.IntSize(cropW, cropH),
                                    dstOffset = androidx.compose.ui.unit.IntOffset(slot.left.toInt(), slot.top.toInt()),
                                    dstSize = androidx.compose.ui.unit.IntSize(dstW.toInt(), dstH.toInt())
                                )
                            }
                        }
                    }
                }

                // Aspect Ratio Selector
                Text("Canvas Ratio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CollageAspectRatio.values()) { ratio ->
                        FilterChip(
                            selected = selectedRatio == ratio,
                            onClick = { selectedRatio = ratio },
                            label = { Text(ratio.label) }
                        )
                    }
                }

                // Layout Presets
                val layoutLabels = getCollageLabels(selectedBitmaps.size)
                Text("Layout Preset", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(layoutLabels.indices.toList()) { index ->
                        FilterChip(
                            selected = (layoutMode % layoutLabels.size) == index,
                            onClick = { layoutMode = index },
                            label = { Text(layoutLabels[index]) }
                        )
                    }
                }

                // Spacing & Corner Radius Sliders
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Spacing: ${spacingDp.toInt()}dp", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                    Slider(value = spacingDp, onValueChange = { spacingDp = it }, valueRange = 0f..24f, modifier = Modifier.weight(1f))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Corners: ${cornerRadiusDp.toInt()}dp", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                    Slider(value = cornerRadiusDp, onValueChange = { cornerRadiusDp = it }, valueRange = 0f..24f, modifier = Modifier.weight(1f))
                }

                // Background Color
                Text("Background Color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val bgColors = listOf(
                    Color.White to "White",
                    Color.Black to "Black",
                    Color(0xFFFBF8F3) to "Cream",
                    Color(0xFFE2E8F0) to "Light Gray",
                    Color(0xFF1E293B) to "Dark Slate",
                    Color(0xFFE0E7FF) to "Pastel Blue",
                    Color(0xFFFFE4E6) to "Soft Rose"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bgColors) { (col, label) ->
                        FilterChip(
                            selected = bgColor == col,
                            onClick = { bgColor = col },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(col, CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                )
                            },
                            label = { Text(label) }
                        )
                    }
                }

                // Save Collage Button
                Button(
                    onClick = {
                        isSaving = true
                        try {
                            val baseDim = 1080
                            val canvasW: Int
                            val canvasH: Int
                            if (selectedRatio.ratio >= 1f) {
                                canvasW = (baseDim * selectedRatio.ratio).toInt()
                                canvasH = baseDim
                            } else {
                                canvasW = baseDim
                                canvasH = (baseDim / selectedRatio.ratio).toInt()
                            }

                            val outBmp = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(outBmp)
                            canvas.drawColor(bgColor.toArgb())

                            val scaleFactor = canvasW / 360f
                            val exportGap = spacingDp * scaleFactor
                            val exportCornerPx = cornerRadiusDp * scaleFactor
                            val slots = getCollageSlots(selectedBitmaps.size, layoutMode, canvasW.toFloat(), canvasH.toFloat(), exportGap)

                            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            for (i in selectedBitmaps.indices) {
                                val slot = slots.getOrNull(i) ?: continue
                                val bmp = selectedBitmaps[i]
                                val srcW = bmp.width.toFloat()
                                val srcH = bmp.height.toFloat()
                                val dstW = slot.width
                                val dstH = slot.height
                                if (dstW <= 0f || dstH <= 0f || srcW <= 0f || srcH <= 0f) continue

                                val scale = maxOf(dstW / srcW, dstH / srcH)
                                val cropW = (dstW / scale).toInt().coerceIn(1, bmp.width)
                                val cropH = (dstH / scale).toInt().coerceIn(1, bmp.height)
                                val cropX = ((bmp.width - cropW) / 2).coerceIn(0, bmp.width - cropW)
                                val cropY = ((bmp.height - cropH) / 2).coerceIn(0, bmp.height - cropH)

                                val srcRect = Rect(cropX, cropY, cropX + cropW, cropY + cropH)
                                val dstRectF = RectF(slot.left, slot.top, slot.right, slot.bottom)

                                canvas.save()
                                if (exportCornerPx > 0f) {
                                    val path = AndroidPath().apply {
                                        addRoundRect(dstRectF, exportCornerPx, exportCornerPx, AndroidPath.Direction.CW)
                                    }
                                    canvas.clipPath(path)
                                }
                                canvas.drawBitmap(bmp, srcRect, dstRectF, paint)
                                canvas.restore()
                            }

                            val stream = ByteArrayOutputStream()
                            outBmp.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                            val bytes = stream.toByteArray()
                            val name = "collage_${System.currentTimeMillis()}.jpg"
                            val savedUri = fileOutputManager.saveToDefault(bytes, name, "image/jpeg", "Pictures")
                            if (savedUri != null) {
                                resultFileName = name
                                resultFileUri = savedUri.toString()
                                resultFileSize = bytes.size.toLong()
                                showResultSheet = true
                            } else {
                                Toast.makeText(context, "Failed to save collage.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Collage export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSaving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Collage to Gallery", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    OperationResultBottomSheet(
        show = showResultSheet,
        onDismiss = { showResultSheet = false },
        title = "Collage Created!",
        fileName = resultFileName,
        fileUri = resultFileUri,
        fileSize = resultFileSize,
        mimeType = "image/jpeg"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemeMakerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val fileOutputManager = coreEntryPoint(context).fileOutputManager()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var topText by remember { mutableStateOf("") }
    var bottomText by remember { mutableStateOf("") }
    var fontSizeSp by remember { mutableFloatStateOf(32f) }
    var isUppercase by remember { mutableStateOf(true) }

    var showResultSheet by remember { mutableStateOf(false) }
    var resultFileName by remember { mutableStateOf<String?>(null) }
    var resultFileUri by remember { mutableStateOf<String?>(null) }
    var resultFileSize by remember { mutableStateOf(0L) }
    var isSaving by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        val maxDim = 1280
                        val scale = min(1f, maxDim.toFloat() / max(bmp.width, bmp.height))
                        sourceBitmap = if (scale < 1f) {
                            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                        } else {
                            bmp
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meme Maker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Pick Image")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (sourceBitmap == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { imagePicker.launch("image/*") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Mood, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Select Meme Template", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Pick a photo from your gallery to add top and bottom captions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            } else {
                val previewBmp = sourceBitmap!!
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = previewBmp.asImageBitmap(),
                            contentDescription = "Meme Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val displayTop = if (isUppercase) topText.uppercase() else topText
                            val displayBottom = if (isUppercase) bottomText.uppercase() else bottomText

                            if (displayTop.isNotEmpty()) {
                                Text(
                                    text = displayTop,
                                    color = Color.White,
                                    fontSize = (fontSizeSp * 0.7f).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(1.dp))
                            }

                            if (displayBottom.isNotEmpty()) {
                                Text(
                                    text = displayBottom,
                                    color = Color.White,
                                    fontSize = (fontSizeSp * 0.7f).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(1.dp))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = topText,
                    onValueChange = { topText = it },
                    label = { Text("Top Caption") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = bottomText,
                    onValueChange = { bottomText = it },
                    label = { Text("Bottom Caption") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Text Size: ${fontSizeSp.toInt()}sp", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { fontSizeSp = it },
                        valueRange = 18f..64f,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isUppercase = !isUppercase }
                ) {
                    Checkbox(checked = isUppercase, onCheckedChange = { isUppercase = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto ALL CAPS (Classic Meme)", style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = {
                        isSaving = true
                        try {
                            val bmp = previewBmp.copy(Bitmap.Config.ARGB_8888, true)
                            val canvas = android.graphics.Canvas(bmp)
                            val paintFill = Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = fontSizeSp * (bmp.width / 400f)
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                textAlign = Paint.Align.CENTER
                                style = Paint.Style.FILL
                                isAntiAlias = true
                            }
                            val paintStroke = Paint().apply {
                                color = android.graphics.Color.BLACK
                                textSize = fontSizeSp * (bmp.width / 400f)
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                textAlign = Paint.Align.CENTER
                                style = Paint.Style.STROKE
                                strokeWidth = paintFill.textSize * 0.12f
                                isAntiAlias = true
                            }

                            val top = if (isUppercase) topText.uppercase() else topText
                            val bottom = if (isUppercase) bottomText.uppercase() else bottomText
                            val cx = bmp.width / 2f

                            if (top.isNotBlank()) {
                                val y = paintFill.textSize + 24f
                                canvas.drawText(top, cx, y, paintStroke)
                                canvas.drawText(top, cx, y, paintFill)
                            }

                            if (bottom.isNotBlank()) {
                                val y = bmp.height - 24f
                                canvas.drawText(bottom, cx, y, paintStroke)
                                canvas.drawText(bottom, cx, y, paintFill)
                            }

                            val stream = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                            val bytes = stream.toByteArray()
                            val name = "meme_${System.currentTimeMillis()}.jpg"
                            val savedUri = fileOutputManager.saveToDefault(bytes, name, "image/jpeg", "Pictures")
                            if (savedUri != null) {
                                resultFileName = name
                                resultFileUri = savedUri.toString()
                                resultFileSize = bytes.size.toLong()
                                showResultSheet = true
                            } else {
                                Toast.makeText(context, "Failed to save meme.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSaving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Meme to Gallery", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    OperationResultBottomSheet(
        show = showResultSheet,
        onDismiss = { showResultSheet = false },
        title = "Meme Created!",
        fileName = resultFileName,
        fileUri = resultFileUri,
        fileSize = resultFileSize,
        mimeType = "image/jpeg"
    )
}
