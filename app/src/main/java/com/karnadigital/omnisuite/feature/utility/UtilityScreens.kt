package com.karnadigital.omnisuite.feature.utility

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageMakerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val fileOutputManager = coreEntryPoint(context).fileOutputManager()

    var selectedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var layoutMode by remember { mutableIntStateOf(0) }
    var spacingDp by remember { mutableFloatStateOf(8f) }
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
                        Text("Select 2 to 6 Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Combine multiple photos into beautiful grid and split collage designs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            } else {
                Text("${selectedBitmaps.size} photos selected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().background(bgColor)) {
                        val w = size.width
                        val h = size.height
                        val pad = spacingDp.dp.toPx()

                        when (selectedBitmaps.size) {
                            1 -> {
                                drawImage(selectedBitmaps[0].asImageBitmap(), dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()))
                            }
                            2 -> {
                                if (layoutMode == 0) {
                                    val cellW = (w - pad) / 2
                                    drawImage(selectedBitmaps[0].asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(0, 0), dstSize = androidx.compose.ui.unit.IntSize(cellW.toInt(), h.toInt()))
                                    drawImage(selectedBitmaps[1].asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset((cellW + pad).toInt(), 0), dstSize = androidx.compose.ui.unit.IntSize(cellW.toInt(), h.toInt()))
                                } else {
                                    val cellH = (h - pad) / 2
                                    drawImage(selectedBitmaps[0].asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(0, 0), dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), cellH.toInt()))
                                    drawImage(selectedBitmaps[1].asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(0, (cellH + pad).toInt()), dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), cellH.toInt()))
                                }
                            }
                            3 -> {
                                val topH = (h - pad) / 2
                                val botH = (h - pad) / 2
                                val botW = (w - pad) / 2
                                drawImage(selectedBitmaps[0].asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(0, 0), dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), topH.toInt()))
                                drawImage(selectedBitmaps[1].asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(0, (topH + pad).toInt()), dstSize = androidx.compose.ui.unit.IntSize(botW.toInt(), botH.toInt()))
                                drawImage(selectedBitmaps[2].asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset((botW + pad).toInt(), (topH + pad).toInt()), dstSize = androidx.compose.ui.unit.IntSize(botW.toInt(), botH.toInt()))
                            }
                            else -> {
                                val cellW = (w - pad) / 2
                                val cellH = (h - pad) / 2
                                drawImage(selectedBitmaps[0].asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(0, 0), dstSize = androidx.compose.ui.unit.IntSize(cellW.toInt(), cellH.toInt()))
                                drawImage(selectedBitmaps[1].asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset((cellW + pad).toInt(), 0), dstSize = androidx.compose.ui.unit.IntSize(cellW.toInt(), cellH.toInt()))
                                if (selectedBitmaps.size > 2) {
                                    drawImage(selectedBitmaps[2].asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(0, (cellH + pad).toInt()), dstSize = androidx.compose.ui.unit.IntSize(cellW.toInt(), cellH.toInt()))
                                }
                                if (selectedBitmaps.size > 3) {
                                    drawImage(selectedBitmaps[3].asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset((cellW + pad).toInt(), (cellH + pad).toInt()), dstSize = androidx.compose.ui.unit.IntSize(cellW.toInt(), cellH.toInt()))
                                }
                            }
                        }
                    }
                }

                Text("Layout Preset", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = layoutMode == 0, onClick = { layoutMode = 0 }, label = { Text("Split / Grid") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = layoutMode == 1, onClick = { layoutMode = 1 }, label = { Text("Stacked") }, modifier = Modifier.weight(1f))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Padding: ${spacingDp.toInt()}dp", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = spacingDp, onValueChange = { spacingDp = it }, valueRange = 0f..24f, modifier = Modifier.weight(1f).padding(start = 12.dp))
                }

                Text("Background Color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Color.White to "White", Color.Black to "Black", Color(0xFFE2E8F0) to "Light Gray", Color(0xFF1E293B) to "Dark Slate").forEach { (col, label) ->
                        FilterChip(selected = bgColor == col, onClick = { bgColor = col }, label = { Text(label) })
                    }
                }

                Button(
                    onClick = {
                        isSaving = true
                        try {
                            val canvasSize = 1080
                            val outBmp = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(outBmp)
                            canvas.drawColor(bgColor.toArgb())

                            val pad = spacingDp * (canvasSize / 360f)
                            when (selectedBitmaps.size) {
                                1 -> {
                                    canvas.drawBitmap(selectedBitmaps[0], null, Rect(0, 0, canvasSize, canvasSize), null)
                                }
                                2 -> {
                                    if (layoutMode == 0) {
                                        val cellW = ((canvasSize - pad) / 2).toInt()
                                        canvas.drawBitmap(selectedBitmaps[0], null, Rect(0, 0, cellW, canvasSize), null)
                                        canvas.drawBitmap(selectedBitmaps[1], null, Rect((cellW + pad).toInt(), 0, canvasSize, canvasSize), null)
                                    } else {
                                        val cellH = ((canvasSize - pad) / 2).toInt()
                                        canvas.drawBitmap(selectedBitmaps[0], null, Rect(0, 0, canvasSize, cellH), null)
                                        canvas.drawBitmap(selectedBitmaps[1], null, Rect(0, (cellH + pad).toInt(), canvasSize, canvasSize), null)
                                    }
                                }
                                3 -> {
                                    val topH = ((canvasSize - pad) / 2).toInt()
                                    val botW = ((canvasSize - pad) / 2).toInt()
                                    canvas.drawBitmap(selectedBitmaps[0], null, Rect(0, 0, canvasSize, topH), null)
                                    canvas.drawBitmap(selectedBitmaps[1], null, Rect(0, (topH + pad).toInt(), botW, canvasSize), null)
                                    canvas.drawBitmap(selectedBitmaps[2], null, Rect((botW + pad).toInt(), (topH + pad).toInt(), canvasSize, canvasSize), null)
                                }
                                else -> {
                                    val cellW = ((canvasSize - pad) / 2).toInt()
                                    val cellH = ((canvasSize - pad) / 2).toInt()
                                    canvas.drawBitmap(selectedBitmaps[0], null, Rect(0, 0, cellW, cellH), null)
                                    canvas.drawBitmap(selectedBitmaps[1], null, Rect((cellW + pad).toInt(), 0, canvasSize, cellH), null)
                                    if (selectedBitmaps.size > 2) {
                                        canvas.drawBitmap(selectedBitmaps[2], null, Rect(0, (cellH + pad).toInt(), cellW, canvasSize), null)
                                    }
                                    if (selectedBitmaps.size > 3) {
                                        canvas.drawBitmap(selectedBitmaps[3], null, Rect((cellW + pad).toInt(), (cellH + pad).toInt(), canvasSize, canvasSize), null)
                                    }
                                }
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
