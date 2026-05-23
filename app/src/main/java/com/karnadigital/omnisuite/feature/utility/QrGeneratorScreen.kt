package com.karnadigital.omnisuite.feature.utility

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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


fun Modifier.glassmorphic(): Modifier = this.then(
    Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.2f),
                    Color.White.copy(alpha = 0.05f)
                )
            )
        )
        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
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

    var selectedType by remember { mutableStateOf(QrType.URL) }
    var qrColor by remember { mutableStateOf(AndroidColor.BLACK) }
    var qrSize by remember { mutableStateOf(512f) }
    var errorCorrection by remember { mutableStateOf("M") } // L, M, Q, H
    var embedLogo by remember { mutableStateOf(true) }
    var isCustomizationExpanded by remember { mutableStateOf(false) }

    // Form States
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

    // Payload Compiler
    val compiledPayload by remember {
        derivedStateOf {
            when (selectedType) {
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
        }
    }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(compiledPayload, qrColor, qrSize, errorCorrection, embedLogo) {
        qrBitmap = if (compiledPayload.isNotBlank()) {
            generateQrCodeBitmap(
                content = compiledPayload,
                qrColor = qrColor,
                size = qrSize.toInt(),
                errorCorrection = errorCorrection,
                embedLogo = embedLogo,
                context = context
            )
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline QR Generator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. ORGANIZED CATEGORIES VIEW
            Text(
                text = "Select QR Payload Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Category sections
            QrCategoryBlock(
                title = "Personal & Social",
                types = QrType.values().filter { it.category == QrCategory.PERSONAL },
                selectedType = selectedType,
                onSelect = { selectedType = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            QrCategoryBlock(
                title = "Web & Store Links",
                types = QrType.values().filter { it.category == QrCategory.WEBLINKS },
                selectedType = selectedType,
                onSelect = { selectedType = it }
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
                        selectedType = selectedType,
                        onSelect = { selectedType = it }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    QrCategoryBlock(
                        title = "Location Coordinates",
                        types = QrType.values().filter { it.category == QrCategory.LOCATION },
                        selectedType = selectedType,
                        onSelect = { selectedType = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. INPUT FORM CARD
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${selectedType.icon} Configure Payload",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = selectedType.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    when (selectedType) {
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
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
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
                                label = { Text("Organization / Company") },
                                leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
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
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = emailSubject,
                                onValueChange = { emailSubject = it },
                                label = { Text("Subject") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = emailBody,
                                onValueChange = { emailBody = it },
                                label = { Text("Compose Message Body") },
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
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = smsMessage,
                                onValueChange = { smsMessage = it },
                                label = { Text("SMS Message Text") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        QrType.PHONE -> {
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text("Mobile Number") },
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
                                label = { Text("Phone Number (with Country Code)") },
                                placeholder = { Text("e.g. 919999999999") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = waMessage,
                                onValueChange = { waMessage = it },
                                label = { Text("Prefilled Message") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        QrType.PLAY_STORE -> {
                            OutlinedTextField(
                                value = playStorePackage,
                                onValueChange = { playStorePackage = it },
                                label = { Text("Play Store Package ID") },
                                placeholder = { Text("e.g. com.karnadigital.omnisuite") },
                                leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. COLLAPSIBLE CUSTOMIZATION DRAWER PANEL
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCustomizationExpanded = !isCustomizationExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Advanced Visual Customizations",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // A. Color Accent Chips
                            Column {
                                Text("QR Code Modules Color", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(
                                        AndroidColor.BLACK to Color.Black,
                                        AndroidColor.rgb(16, 185, 129) to Color(0xFF10B981), // Emerald
                                        AndroidColor.rgb(59, 130, 246) to Color(0xFF3B82F6), // Royal Blue
                                        AndroidColor.rgb(245, 158, 11) to Color(0xFFF59E0B), // Orange/Amber
                                        AndroidColor.rgb(239, 68, 68) to Color(0xFFEF4444)    // Red
                                    ).forEach { (colorVal, composeColor) ->
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(composeColor)
                                                .clickable { qrColor = colorVal }
                                                .padding(2.dp)
                                        ) {
                                            if (qrColor == colorVal) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.4f))
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // B. Error Correction Level
                            Column {
                                Text("Error Correction Tolerance Level", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    listOf(
                                        "L" to "Low (7%)",
                                        "M" to "Medium (15%)",
                                        "Q" to "Quartile (25%)",
                                        "H" to "High (30%)"
                                    ).forEach { (level, name) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { errorCorrection = level }
                                        ) {
                                            RadioButton(
                                                selected = errorCorrection == level,
                                                onClick = { errorCorrection = level },
                                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                            )
                                            Text(name, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // C. Embed Center Logo overlay
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Embed Center Brand Badge", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Overlays a small premium white-backed OmniSuite logo in the center.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = embedLogo,
                                    onCheckedChange = { embedLogo = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                                )
                            }

                            // D. Resolution Slider
                            Column {
                                Text("QR Output Resolution: ${qrSize.toInt()}x${qrSize.toInt()}px", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Slider(
                                    value = qrSize,
                                    onValueChange = { qrSize = it },
                                    valueRange = 256f..1024f,
                                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. DYNAMIC HIGH PREMIUMN QR PREVIEW CARD (300dp+)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .size(310.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = qrBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Live custom QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🧬", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Awaiting Form Inputs...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "QR code will render live here",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. EXPORT ROW BUTTONS
            if (qrBitmap != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            // Direct save to default Documents/OmniSuite/QR/ PNG folder
                            val resolver = context.contentResolver
                            val outName = "QR_${System.currentTimeMillis()}.png"
                            
                            val stream = java.io.ByteArrayOutputStream()
                            qrBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            val bytes = stream.toByteArray()

                            val savedUri = FileOutputManager.saveToDefault(
                                context = context,
                                bytes = bytes,
                                filename = outName,
                                mimeType = "image/png",
                                subfolder = "QR"
                            )

                            if (savedUri != null) {
                                viewModel.logQrCodeGeneration(compiledPayload)
                                Toast.makeText(context, "QR saved successfully in OmniSuite/QR folder!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save QR to storage.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save QR", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(compiledPayload))
                            Toast.makeText(context, "Payload string copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Payload", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(32.dp))
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

            // Flow grid row of chips
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

private fun generateQrCodeBitmap(
    content: String,
    qrColor: Int,
    size: Int = 512,
    errorCorrection: String = "M",
    embedLogo: Boolean = false,
    context: Context? = null,
    format: BarcodeFormat = BarcodeFormat.QR_CODE
): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val hints = HashMap<com.google.zxing.EncodeHintType, Any>()
        val ecLevel = when (errorCorrection) {
            "L" -> com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L
            "Q" -> com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.Q
            "H" -> com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H
            else -> com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
        }
        hints[com.google.zxing.EncodeHintType.ERROR_CORRECTION] = ecLevel
        
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(content, format, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix.get(x, y)) qrColor else AndroidColor.WHITE
                )
            }
        }
        
        if (embedLogo && context != null) {
            val canvas = android.graphics.Canvas(bitmap)
            val centerSize = size / 5
            val start = (size - centerSize) / 2
            
            // Draw white background card for the logo
            val paintBg = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }
            val rect = android.graphics.RectF(
                start.toFloat() - 4f,
                start.toFloat() - 4f,
                (start + centerSize).toFloat() + 4f,
                (start + centerSize).toFloat() + 4f
            )
            canvas.drawRoundRect(rect, 10f, 10f, paintBg)
            
            // Draw a small red "O" (OmniSuite logo) inside the card
            val paintText = android.graphics.Paint().apply {
                color = android.graphics.Color.RED
                textSize = centerSize.toFloat() * 0.7f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val textX = size / 2f
            val textY = size / 2f - (paintText.descent() + paintText.ascent()) / 2
            canvas.drawText("O", textX, textY, paintText)
        }
        
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
