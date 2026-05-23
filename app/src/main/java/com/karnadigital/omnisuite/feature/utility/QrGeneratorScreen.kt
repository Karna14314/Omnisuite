package com.karnadigital.omnisuite.feature.utility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.karnadigital.omnisuite.core.util.FileOutputManager

enum class QrCategory {
    PERSONAL, WEBLINKS, NETWORK, LOCATION
}

enum class QrType(val category: QrCategory, val displayName: String, val icon: String) {
    URL(QrCategory.WEBLINKS, "Website URL", "🔗"),
    TEXT(QrCategory.WEBLINKS, "Plain Text", "📄"),
    PLAY_STORE(QrCategory.WEBLINKS, "Play Store", "🛍️"),
    
    CONTACT(QrCategory.PERSONAL, "Contact vCard", "📇"),
    PHONE(QrCategory.PERSONAL, "Phone Number", "📞"),
    EMAIL(QrCategory.PERSONAL, "Compose Email", "✉️"),
    SMS(QrCategory.PERSONAL, "SMS Text", "💬"),
    WHATSAPP(QrCategory.PERSONAL, "WhatsApp Link", "💬"),
    
    WIFI(QrCategory.NETWORK, "WiFi Access", "📶"),
    
    GEO(QrCategory.LOCATION, "Coordinates", "📍")
}

data class GradientPreset(
    val name: String,
    val startColor: Int,
    val endColor: Int,
    val startCompose: Color,
    val endCompose: Color
)

val gradientPresets = listOf(
    GradientPreset("Midnight Black", AndroidColor.BLACK, AndroidColor.BLACK, Color.Black, Color.Black),
    GradientPreset("Ocean Mist", AndroidColor.rgb(10, 80, 180), AndroidColor.rgb(0, 210, 255), Color(0xFF0A50B4), Color(0xFF00D2FF)),
    GradientPreset("Emerald Gold", AndroidColor.rgb(16, 120, 80), AndroidColor.rgb(200, 180, 40), Color(0xFF107850), Color(0xFFC8B428)),
    GradientPreset("Neon Sunset", AndroidColor.rgb(150, 0, 180), AndroidColor.rgb(255, 100, 0), Color(0xFF9600B4), Color(0xFFFF6400)),
    GradientPreset("Cyberpunk Pink", AndroidColor.rgb(255, 0, 128), AndroidColor.rgb(128, 0, 255), Color(0xFFFF0080), Color(0xFF8000FF))
)

fun Modifier.glassmorphic(): Modifier = this.then(
    Modifier
        .clip(RoundedCornerShape(20.dp))
        .background(
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.02f)
                )
            )
        )
        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrGeneratorScreen(
    viewModel: QrGeneratorViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var activeTabState by remember { mutableStateOf(0) } // 0 = QR Code, 1 = Barcode Builder

    // Customization States
    var selectedGradientPreset by remember { mutableStateOf(gradientPresets[0]) }
    var qrSize by remember { mutableStateOf(512f) }
    var errorCorrection by remember { mutableStateOf("M") } // L, M, Q, H
    var embedLogo by remember { mutableStateOf(true) }
    var isCustomizationExpanded by remember { mutableStateOf(false) }

    // --- QR Payload States ---
    var selectedQrType by remember { mutableStateOf(QrType.URL) }
    var urlText by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf("") }
    
    // WiFi
    var ssid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var wifiSecurity by remember { mutableStateOf("WPA") } // WPA, WEP, nopass

    // Contact
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var contactOrg by remember { mutableStateOf("") }
    var contactAddress by remember { mutableStateOf("") }

    // Email
    var emailAddress by remember { mutableStateOf("") }
    var emailSubject by remember { mutableStateOf("") }
    var emailBody by remember { mutableStateOf("") }

    // SMS
    var smsPhone by remember { mutableStateOf("") }
    var smsMessage by remember { mutableStateOf("") }

    // Phone
    var phoneNumber by remember { mutableStateOf("") }

    // Geo
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }

    // WhatsApp
    var waPhone by remember { mutableStateOf("") }
    var waMessage by remember { mutableStateOf("") }

    // Play Store
    var playStorePackage by remember { mutableStateOf("") }

    // --- Barcode Builder States ---
    val barcodeFormats = listOf(
        BarcodeFormat.EAN_13 to "EAN-13",
        BarcodeFormat.EAN_8 to "EAN-8",
        BarcodeFormat.UPC_A to "UPC-A",
        BarcodeFormat.UPC_E to "UPC-E",
        BarcodeFormat.CODE_128 to "Code-128",
        BarcodeFormat.ITF to "ITF (Interleaved 2 of 5)",
        BarcodeFormat.CODE_39 to "Code-39",
        BarcodeFormat.CODABAR to "Codabar",
        BarcodeFormat.CODE_93 to "Code-93",
        BarcodeFormat.PDF_417 to "PDF-417"
    )
    var selectedBarcodeFormat by remember { mutableStateOf(barcodeFormats[0].first) }
    var barcodeInput by remember { mutableStateOf("") }
    var isBarcodeDropdownExpanded by remember { mutableStateOf(false) }

    // Validation logic for barcode
    val barcodeValidationError by remember {
        derivedStateOf {
            if (activeTabState == 1) {
                validateBarcodeContent(barcodeInput, selectedBarcodeFormat)
            } else null
        }
    }

    // Dynamic Payload Resolver
    val compiledPayload by remember {
        derivedStateOf {
            if (activeTabState == 0) {
                when (selectedQrType) {
                    QrType.URL -> urlText
                    QrType.TEXT -> rawText
                    QrType.WIFI -> "WIFI:S:$ssid;T:$wifiSecurity;P:$wifiPassword;;"
                    QrType.CONTACT -> """
                        BEGIN:VCARD
                        VERSION:3.0
                        N:$lastName;$firstName;;;
                        FN:$firstName $lastName
                        ORG:$contactOrg
                        TEL;TYPE=CELL:$contactPhone
                        EMAIL;TYPE=PREF,INTERNET:$contactEmail
                        ADR:;;$contactAddress;;;;
                        END:VCARD
                    """.trimIndent()
                    QrType.EMAIL -> "mailto:$emailAddress?subject=${Uri.encode(emailSubject)}&body=${Uri.encode(emailBody)}"
                    QrType.SMS -> "smsto:$smsPhone:$smsMessage"
                    QrType.PHONE -> "tel:$phoneNumber"
                    QrType.GEO -> "geo:$latitude,$longitude"
                    QrType.WHATSAPP -> "https://wa.me/${waPhone.filter { it.isDigit() }}?text=${Uri.encode(waMessage)}"
                    QrType.PLAY_STORE -> "https://play.google.com/store/apps/details?id=${playStorePackage.trim()}"
                }
            } else {
                if (barcodeValidationError == null) barcodeInput else ""
            }
        }
    }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(compiledPayload, selectedGradientPreset, qrSize, errorCorrection, embedLogo, activeTabState, selectedBarcodeFormat) {
        qrBitmap = if (compiledPayload.isNotBlank()) {
            val format = if (activeTabState == 0) BarcodeFormat.QR_CODE else selectedBarcodeFormat
            val width = if (format == BarcodeFormat.QR_CODE || format == BarcodeFormat.PDF_417) qrSize.toInt() else (qrSize.toInt() * 1.6f).toInt()
            val height = if (format == BarcodeFormat.QR_CODE || format == BarcodeFormat.PDF_417) qrSize.toInt() else (qrSize.toInt() * 0.7f).toInt()
            
            generateQrCodeBitmap(
                content = compiledPayload,
                startColor = selectedGradientPreset.startColor,
                endColor = selectedGradientPreset.endColor,
                width = width,
                height = height,
                errorCorrection = errorCorrection,
                embedLogo = embedLogo && format == BarcodeFormat.QR_CODE,
                context = context,
                format = format
            )
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Glass QR & Barcode Deck", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // sticky top tabs selector
            ScrollableTabRow(
                selectedTabIndex = activeTabState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTabState]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = activeTabState == 0,
                    onClick = { activeTabState = 0 },
                    text = { Text("QR Code Deck", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTabState == 1,
                    onClick = { activeTabState = 1 },
                    text = { Text("Barcode Builder", fontWeight = FontWeight.Bold) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (activeTabState == 0) { // --- QR DECK ---
                        Text(
                            text = "Select QR Payload Type",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        QrCategoryBlock(
                            title = "Personal & Social",
                            types = QrType.values().filter { it.category == QrCategory.PERSONAL },
                            selectedType = selectedQrType,
                            onSelect = { selectedQrType = it }
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        QrCategoryBlock(
                            title = "Web & Store Links",
                            types = QrType.values().filter { it.category == QrCategory.WEBLINKS },
                            selectedType = selectedQrType,
                            onSelect = { selectedQrType = it }
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                QrCategoryBlock(
                                    title = "Network Access",
                                    types = QrType.values().filter { it.category == QrCategory.NETWORK },
                                    selectedType = selectedQrType,
                                    onSelect = { selectedQrType = it }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                QrCategoryBlock(
                                    title = "Location Coordinates",
                                    types = QrType.values().filter { it.category == QrCategory.LOCATION },
                                    selectedType = selectedQrType,
                                    onSelect = { selectedQrType = it }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // QR Input Form
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${selectedQrType.icon} Configure Payload",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = selectedQrType.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                when (selectedQrType) {
                                    QrType.URL -> {
                                        OutlinedTextField(
                                            value = urlText,
                                            onValueChange = { urlText = it },
                                            label = { Text("Website Link URL") },
                                            placeholder = { Text("https://example.com") },
                                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    QrType.TEXT -> {
                                        OutlinedTextField(
                                            value = rawText,
                                            onValueChange = { rawText = it },
                                            label = { Text("Plain Text Note") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 3,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    QrType.WIFI -> {
                                        OutlinedTextField(
                                            value = ssid,
                                            onValueChange = { ssid = it },
                                            label = { Text("Network Name (SSID)") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = wifiPassword,
                                            onValueChange = { wifiPassword = it },
                                            label = { Text("WiFi Password") },
                                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        Text("Security Protocol", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = wifiSecurity == "WPA", onClick = { wifiSecurity = "WPA" })
                                                Text("WPA/WPA2", fontSize = 13.sp)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = wifiSecurity == "WEP", onClick = { wifiSecurity = "WEP" })
                                                Text("WEP", fontSize = 13.sp)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = wifiSecurity == "nopass", onClick = { wifiSecurity = "nopass" })
                                                Text("Open", fontSize = 13.sp)
                                            }
                                        }
                                    }
                                    QrType.CONTACT -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = firstName,
                                                onValueChange = { firstName = it },
                                                label = { Text("First Name") },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            OutlinedTextField(
                                                value = lastName,
                                                onValueChange = { lastName = it },
                                                label = { Text("Last Name") },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                        OutlinedTextField(
                                            value = contactPhone,
                                            onValueChange = { contactPhone = it },
                                            label = { Text("Phone Number") },
                                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = contactEmail,
                                            onValueChange = { contactEmail = it },
                                            label = { Text("Email Address") },
                                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = contactOrg,
                                            onValueChange = { contactOrg = it },
                                            label = { Text("Organization") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = contactAddress,
                                            onValueChange = { contactAddress = it },
                                            label = { Text("Address") },
                                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    QrType.EMAIL -> {
                                        OutlinedTextField(
                                            value = emailAddress,
                                            onValueChange = { emailAddress = it },
                                            label = { Text("Recipient Email") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = emailSubject,
                                            onValueChange = { emailSubject = it },
                                            label = { Text("Subject") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = emailBody,
                                            onValueChange = { emailBody = it },
                                            label = { Text("Body") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    QrType.SMS -> {
                                        OutlinedTextField(
                                            value = smsPhone,
                                            onValueChange = { smsPhone = it },
                                            label = { Text("Phone Number") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = smsMessage,
                                            onValueChange = { smsMessage = it },
                                            label = { Text("SMS Message") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    QrType.PHONE -> {
                                        OutlinedTextField(
                                            value = phoneNumber,
                                            onValueChange = { phoneNumber = it },
                                            label = { Text("Phone Number") },
                                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    QrType.GEO -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = latitude,
                                                onValueChange = { latitude = it },
                                                label = { Text("Latitude") },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            OutlinedTextField(
                                                value = longitude,
                                                onValueChange = { longitude = it },
                                                label = { Text("Longitude") },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                    }
                                    QrType.WHATSAPP -> {
                                        OutlinedTextField(
                                            value = waPhone,
                                            onValueChange = { waPhone = it },
                                            label = { Text("WhatsApp Phone (e.g. 919999999999)") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = waMessage,
                                            onValueChange = { waMessage = it },
                                            label = { Text("Message Text") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    QrType.PLAY_STORE -> {
                                        OutlinedTextField(
                                            value = playStorePackage,
                                            onValueChange = { playStorePackage = it },
                                            label = { Text("Package ID (e.g. com.example.app)") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else { // --- BARCODE BUILDER ---
                        Text(
                            text = "Configure Barcode Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Dropdown selector for format
                                Box {
                                    OutlinedButton(
                                        onClick = { isBarcodeDropdownExpanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Format: ${barcodeFormats.firstOrNull { it.first == selectedBarcodeFormat }?.second ?: ""}",
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = isBarcodeDropdownExpanded,
                                        onDismissRequest = { isBarcodeDropdownExpanded = false }
                                    ) {
                                        barcodeFormats.forEach { (format, name) ->
                                            DropdownMenuItem(
                                                text = { Text(name) },
                                                onClick = {
                                                    selectedBarcodeFormat = format
                                                    isBarcodeDropdownExpanded = false
                                                    barcodeInput = "" // Reset to avoid instantly showing validator error
                                                }
                                            )
                                        }
                                    }
                                }

                                // Informative constraints card
                                val instructionText = getBarcodeConstraintMessage(selectedBarcodeFormat)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(instructionText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }

                                // Input Field
                                OutlinedTextField(
                                    value = barcodeInput,
                                    onValueChange = { barcodeInput = it },
                                    label = { Text("Barcode Data String") },
                                    isError = barcodeValidationError != null,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                // Validation Banner
                                barcodeValidationError?.let { error ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Customization Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isCustomizationExpanded = !isCustomizationExpanded },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Advanced Color & Style", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { isCustomizationExpanded = !isCustomizationExpanded }) {
                                    Icon(
                                        imageVector = if (isCustomizationExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand details"
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isCustomizationExpanded) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Gradient preset chips
                                    Column {
                                        Text("Cinematic Gradients Presets", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            gradientPresets.forEach { preset ->
                                                val isSelected = selectedGradientPreset.name == preset.name
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            Brush.linearGradient(
                                                                colors = listOf(preset.startCompose, preset.endCompose)
                                                            )
                                                        )
                                                        .clickable { selectedGradientPreset = preset }
                                                        .border(
                                                            width = if (isSelected) 3.dp else 1.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                                            shape = CircleShape
                                                        )
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Selected: ${selectedGradientPreset.name}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // EMBED CENTER LOGO
                                    if (activeTabState == 0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Embed Brand Badge", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("Overlays OmniSuite logo in the center.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = embedLogo,
                                                onCheckedChange = { embedLogo = it }
                                            )
                                        }
                                    }

                                    // ERROR TOLERANCE LEVEL
                                    if (activeTabState == 0) {
                                        Column {
                                            Text("Error Correction Level", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                listOf("L" to "Low (7%)", "M" to "Medium (15%)", "Q" to "Quartile (25%)", "H" to "High (30%)").forEach { (lvl, label) ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.clickable { errorCorrection = lvl }
                                                    ) {
                                                        RadioButton(selected = errorCorrection == lvl, onClick = { errorCorrection = lvl })
                                                        Text(label, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Live preview container
                    val format = if (activeTabState == 0) BarcodeFormat.QR_CODE else selectedBarcodeFormat
                    val isSquare = format == BarcodeFormat.QR_CODE || format == BarcodeFormat.PDF_417
                    
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .width(320.dp)
                            .height(if (isSquare) 320.dp else 190.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val bitmap = qrBitmap
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Live custom layout output",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(if (activeTabState == 0) "🧬" else "📊", fontSize = 42.sp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Awaiting Form Inputs...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (activeTabState == 0) "QR will compile live" else "Barcode will compile live",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action buttons
                    if (qrBitmap != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val stream = java.io.ByteArrayOutputStream()
                                    qrBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                    val bytes = stream.toByteArray()
                                    val outName = if (activeTabState == 0) "QR_${System.currentTimeMillis()}.png" else "BARCODE_${System.currentTimeMillis()}.png"

                                    val savedUri = FileOutputManager.saveToDefault(
                                        context = context,
                                        bytes = bytes,
                                        filename = outName,
                                        mimeType = "image/png",
                                        subfolder = if (activeTabState == 0) "QR" else "Barcodes"
                                    )

                                    if (savedUri != null) {
                                        viewModel.logQrCodeGeneration(compiledPayload)
                                        Toast.makeText(context, "Saved successfully to default folder!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save generated image.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Copy", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(compiledPayload))
                                    Toast.makeText(context, "Payload copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Data", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun QrCategoryBlock(
    title: String,
    types: List<QrType>,
    selectedType: QrType,
    onSelect: (QrType) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                types.forEach { type ->
                    val isSelected = selectedType == type
                    val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    val tc = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable { onSelect(type) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(type.icon, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = type.displayName.substringBefore(" "),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tc
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getBarcodeConstraintMessage(format: BarcodeFormat): String {
    return when (format) {
        BarcodeFormat.EAN_13 -> "EAN-13 requires exactly 13 numeric digits."
        BarcodeFormat.EAN_8 -> "EAN-8 requires exactly 8 numeric digits."
        BarcodeFormat.UPC_A -> "UPC-A requires exactly 12 numeric digits."
        BarcodeFormat.UPC_E -> "UPC-E requires exactly 8 numeric digits (starts with 0)."
        BarcodeFormat.ITF -> "ITF requires numeric digits of an even length (e.g. 10, 12, 14 digits)."
        BarcodeFormat.CODABAR -> "Codabar supports numbers, symbols (-$:/.+) and letters A-D as start/stop."
        BarcodeFormat.CODE_39 -> "Code-39 supports alphanumeric characters, spaces and symbols (-$.%/+)."
        BarcodeFormat.CODE_93 -> "Code-93 supports standard alphanumeric data."
        BarcodeFormat.CODE_128 -> "Code-128 supports full high-density alphanumeric data."
        BarcodeFormat.PDF_417 -> "PDF-417 supports two-dimensional high-density alphanumeric data."
        else -> "Standard alphanumeric input."
    }
}

private fun validateBarcodeContent(content: String, format: BarcodeFormat): String? {
    if (content.isBlank()) return null // Don't show error on completely empty input
    return when (format) {
        BarcodeFormat.EAN_13 -> {
            if (content.length != 13) "EAN-13 requires exactly 13 digits (current: ${content.length})"
            else if (!content.all { it.isDigit() }) "EAN-13 must contain only numeric digits"
            else null
        }
        BarcodeFormat.EAN_8 -> {
            if (content.length != 8) "EAN-8 requires exactly 8 digits (current: ${content.length})"
            else if (!content.all { it.isDigit() }) "EAN-8 must contain only numeric digits"
            else null
        }
        BarcodeFormat.UPC_A -> {
            if (content.length != 12) "UPC-A requires exactly 12 digits (current: ${content.length})"
            else if (!content.all { it.isDigit() }) "UPC-A must contain only numeric digits"
            else null
        }
        BarcodeFormat.UPC_E -> {
            if (content.length != 8) "UPC-E requires exactly 8 digits (current: ${content.length})"
            else if (!content.all { it.isDigit() }) "UPC-E must contain only numeric digits"
            else if (content.firstOrNull() != '0' && content.firstOrNull() != '1') "UPC-E must start with 0 or 1"
            else null
        }
        BarcodeFormat.ITF -> {
            if (!content.all { it.isDigit() }) "ITF must contain only numeric digits"
            else if (content.length % 2 != 0) "ITF requires an even number of digits (current: ${content.length})"
            else null
        }
        BarcodeFormat.CODABAR -> {
            val validChars = "0123456789-$:/.+ABCDabcd"
            if (!content.all { it in validChars }) "Codabar contains invalid characters"
            else null
        }
        else -> null
    }
}

private fun generateQrCodeBitmap(
    content: String,
    startColor: Int,
    endColor: Int,
    width: Int = 512,
    height: Int = 512,
    errorCorrection: String = "M",
    embedLogo: Boolean = false,
    context: Context? = null,
    format: BarcodeFormat = BarcodeFormat.QR_CODE
): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val hints = HashMap<com.google.zxing.EncodeHintType, Any>()
        if (format == BarcodeFormat.QR_CODE) {
            val ecLevel = when (errorCorrection) {
                "L" -> com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L
                "Q" -> com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.Q
                "H" -> com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H
                else -> com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
            }
            hints[com.google.zxing.EncodeHintType.ERROR_CORRECTION] = ecLevel
        }
        hints[com.google.zxing.EncodeHintType.MARGIN] = 1 // Sleek, thin margin
        
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(content, format, width, height, hints)
        val w = bitMatrix.width
        val h = bitMatrix.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until w) {
            for (y in 0 until h) {
                if (bitMatrix.get(x, y)) {
                    // Apply gorgeous diagonal gradient interpolation
                    val ratio = (x.toFloat() + y.toFloat()) / (w + h).toFloat()
                    val r = ((1 - ratio) * AndroidColor.red(startColor) + ratio * AndroidColor.red(endColor)).toInt()
                    val g = ((1 - ratio) * AndroidColor.green(startColor) + ratio * AndroidColor.green(endColor)).toInt()
                    val b = ((1 - ratio) * AndroidColor.blue(startColor) + ratio * AndroidColor.blue(endColor)).toInt()
                    bitmap.setPixel(x, y, AndroidColor.rgb(r, g, b))
                } else {
                    bitmap.setPixel(x, y, AndroidColor.WHITE)
                }
            }
        }
        
        if (embedLogo && context != null && format == BarcodeFormat.QR_CODE) {
            val canvas = android.graphics.Canvas(bitmap)
            val centerSize = w / 5
            val startX = (w - centerSize) / 2
            val startY = (h - centerSize) / 2
            
            // Draw rounded white background badge card for logo
            val paintBg = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }
            val rect = android.graphics.RectF(
                startX.toFloat() - 4f,
                startY.toFloat() - 4f,
                (startX + centerSize).toFloat() + 4f,
                (startY + centerSize).toFloat() + 4f
            )
            canvas.drawRoundRect(rect, 10f, 10f, paintBg)
            
            // Draw a high-aesthetics bold red "O" representing OmniSuite branding in the center
            val paintText = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(239, 68, 68) // Material Red
                textSize = centerSize.toFloat() * 0.7f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val textX = w / 2f
            val textY = h / 2f - (paintText.descent() + paintText.ascent()) / 2
            canvas.drawText("O", textX, textY, paintText)
        }
        
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
