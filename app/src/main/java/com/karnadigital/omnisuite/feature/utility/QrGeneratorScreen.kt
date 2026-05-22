package com.karnadigital.omnisuite.feature.utility

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.google.zxing.qrcode.QRCodeWriter
import java.io.OutputStream

enum class QrType(val displayName: String, val icon: String) {
    URL("URL", "🔗"),
    TEXT("Text", "📄"),
    WIFI("WiFi", "📶"),
    CONTACT("Contact", "📇"),
    EMAIL("Email", "✉️"),
    SMS("SMS", "💬"),
    PHONE("Phone", "📞"),
    GEO("Location", "📍"),
    EVENT("Event", "📅"),
    WHATSAPP("WhatsApp", "💬"),
    PLAY_STORE("Play Store", "🛍️")
}

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

    // Forms States
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

    // Event
    var eventTitle by remember { mutableStateOf("") }
    var eventStart by remember { mutableStateOf("") } // YYYYMMDD
    var eventLocation by remember { mutableStateOf("") }
    var eventDesc by remember { mutableStateOf("") }

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
                QrType.SMS -> "SMTO:$smsPhone:$smsMessage"
                QrType.PHONE -> "tel:$phoneNumber"
                QrType.GEO -> "geo:$latitude,$longitude"
                QrType.EVENT -> """
                    BEGIN:VCALENDAR
                    VERSION:2.0
                    BEGIN:VEVENT
                    SUMMARY:$eventTitle
                    DTSTART:${eventStart.trim()}T090000Z
                    LOCATION:$eventLocation
                    DESCRIPTION:$eventDesc
                    END:VEVENT
                    END:VCALENDAR
                """.trimIndent()
                QrType.WHATSAPP -> "https://wa.me/${waPhone.filter { it.isDigit() }}?text=${Uri.encode(waMessage)}"
                QrType.PLAY_STORE -> "https://play.google.com/store/apps/details?id=${playStorePackage.trim()}"
            }
        }
    }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(compiledPayload, qrColor) {
        qrBitmap = if (compiledPayload.isNotBlank()) {
            generateQrCodeBitmap(compiledPayload, qrColor)
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline QR Creator", fontWeight = FontWeight.Bold) },
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

            // 1. HORIZONTAL TYPE SELECTOR
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(QrType.values()) { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text("${type.icon} ${type.displayName}") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. DYNAMIC INPUT FORM CARDS
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Customize ${selectedType.displayName} Payload",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    when (selectedType) {
                        QrType.URL -> {
                            OutlinedTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                label = { Text("Website URL") },
                                placeholder = { Text("https://example.com") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        QrType.TEXT -> {
                            OutlinedTextField(
                                value = rawText,
                                onValueChange = { rawText = it },
                                label = { Text("Plain Text") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                        QrType.WIFI -> {
                            OutlinedTextField(
                                value = ssid,
                                onValueChange = { ssid = it },
                                label = { Text("Network Name (SSID)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = wifiPassword,
                                onValueChange = { wifiPassword = it },
                                label = { Text("WiFi Password") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Security Protocol", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = wifiSecurity == "WPA", onClick = { wifiSecurity = "WPA" })
                                    Text("WPA/WPA2")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = wifiSecurity == "WEP", onClick = { wifiSecurity = "WEP" })
                                    Text("WEP")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = wifiSecurity == "nopass", onClick = { wifiSecurity = "nopass" })
                                    Text("None")
                                }
                            }
                        }
                        QrType.CONTACT -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = firstName,
                                    onValueChange = { firstName = it },
                                    label = { Text("First Name") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = lastName,
                                    onValueChange = { lastName = it },
                                    label = { Text("Last Name") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = contactPhone,
                                onValueChange = { contactPhone = it },
                                label = { Text("Phone Number") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = contactEmail,
                                onValueChange = { contactEmail = it },
                                label = { Text("Email Address") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = contactOrg,
                                onValueChange = { contactOrg = it },
                                label = { Text("Company / Org") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = contactAddress,
                                onValueChange = { contactAddress = it },
                                label = { Text("Postal Address") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        QrType.EMAIL -> {
                            OutlinedTextField(
                                value = emailAddress,
                                onValueChange = { emailAddress = it },
                                label = { Text("Recipient Email") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = emailSubject,
                                onValueChange = { emailSubject = it },
                                label = { Text("Subject") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = emailBody,
                                onValueChange = { emailBody = it },
                                label = { Text("Email Body Message") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }
                        QrType.SMS -> {
                            OutlinedTextField(
                                value = smsPhone,
                                onValueChange = { smsPhone = it },
                                label = { Text("Phone Number") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = smsMessage,
                                onValueChange = { smsMessage = it },
                                label = { Text("SMS Message Text") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }
                        QrType.PHONE -> {
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text("Phone Number") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        QrType.GEO -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = latitude,
                                    onValueChange = { latitude = it },
                                    label = { Text("Latitude") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = longitude,
                                    onValueChange = { longitude = it },
                                    label = { Text("Longitude") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        QrType.EVENT -> {
                            OutlinedTextField(
                                value = eventTitle,
                                onValueChange = { eventTitle = it },
                                label = { Text("Event Summary") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = eventStart,
                                onValueChange = { eventStart = it },
                                label = { Text("Start Date (e.g. 20260522)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = eventLocation,
                                onValueChange = { eventLocation = it },
                                label = { Text("Location") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = eventDesc,
                                onValueChange = { eventDesc = it },
                                label = { Text("Description") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }
                        QrType.WHATSAPP -> {
                            OutlinedTextField(
                                value = waPhone,
                                onValueChange = { waPhone = it },
                                label = { Text("Phone Number (with Country Code)") },
                                placeholder = { Text("919999999999") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = waMessage,
                                onValueChange = { waMessage = it },
                                label = { Text("Prefilled Message") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }
                        QrType.PLAY_STORE -> {
                            OutlinedTextField(
                                value = playStorePackage,
                                onValueChange = { playStorePackage = it },
                                label = { Text("App Package ID") },
                                placeholder = { Text("com.karnadigital.omnisuite") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. COLOR PALETTE PICKER
            Text("Select QR Code Accent Color", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    AndroidColor.BLACK to Color.Black,
                    AndroidColor.rgb(16, 185, 129) to Color(0xFF10B981), // Emerald
                    AndroidColor.rgb(59, 130, 246) to Color(0xFF3B82F6), // Royal Blue
                    AndroidColor.rgb(99, 102, 241) to Color(0xFF6366F1), // Indigo
                    AndroidColor.rgb(239, 68, 68) to Color(0xFFEF6868)    // Deep Red
                ).forEach { (colorVal, composeColor) ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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
                                    .background(Color.White.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. LIVE QR PREVIEW CARD
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(24.dp))
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
                            contentDescription = "Generated QR Code bitmap",
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
                                text = "Awaiting Payload Input...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. SAVE & EXPORT ACTION BUTTONS
            if (qrBitmap != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val ok = saveQrCodeToGallery(context, qrBitmap!!, "OmniSuite_QR_${System.currentTimeMillis()}.png")
                            if (ok) {
                                viewModel.logQrCodeGeneration(compiledPayload)
                                Toast.makeText(context, "QR saved to gallery successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save QR to storage.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save PNG", fontWeight = FontWeight.Bold)
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Raw", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun saveQrCodeToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OmniSuite_QR")
        }
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            resolver.openOutputStream(imageUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                true
            } ?: false
        } else {
            false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun generateQrCodeBitmap(content: String, qrColor: Int, size: Int = 512): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
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
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
