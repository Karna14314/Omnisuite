package com.karnadigital.omnisuite.feature.utility

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.documentscanner.GmsDocumentScannerOptions
import com.google.android.gms.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.android.gms.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.android.gms.documentscanner.GmsDocumentScanning
import com.google.android.gms.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream

/**
 * Reusable Composable scanner registration contract that handles the ML Kit GmsDocumentScanner
 * configuration options and provides callbacks for success and failure.
 */
@Composable
fun rememberDocumentScannerLauncher(
    onScanSuccess: (Uri, File) -> Unit,
    onScanFailure: (Exception) -> Unit
): () -> Unit {
    val context = LocalContext.current

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            try {
                val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
                val tempPdfUri = result?.pdf?.uri
                if (tempPdfUri != null) {
                    val savedFile = saveScannedDocumentToDisk(context, tempPdfUri)
                    if (savedFile != null) {
                        onScanSuccess(tempPdfUri, savedFile)
                    } else {
                        onScanFailure(Exception("Failed to copy scanned file to persistent storage."))
                    }
                } else {
                    onScanFailure(Exception("Scanned document data was empty."))
                }
            } catch (e: Exception) {
                onScanFailure(e)
            }
        } else if (activityResult.resultCode == Activity.RESULT_CANCELED) {
            onScanFailure(Exception("Scan operation cancelled by user."))
        } else {
            onScanFailure(Exception("Document scanner failed with code: ${activityResult.resultCode}"))
        }
    }

    val options = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryAllowed(true)
            .setPageLimit(100)
            .setResultFormats(RESULT_FORMAT_PDF)
            .setScannerMode(SCANNER_MODE_FULL)
            .build()
    }

    val scannerClient = remember(options) {
        GmsDocumentScanning.getClient(options)
    }

    return {
        val activity = context.findActivity()
        if (activity != null) {
            scannerClient.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    val request = IntentSenderRequest.Builder(intentSender).build()
                    scannerLauncher.launch(request)
                }
                .addOnFailureListener { exception ->
                    onScanFailure(exception)
                }
        } else {
            onScanFailure(Exception("Unable to resolve active activity context."))
        }
    }
}

/**
 * Utility extension to find the parent Activity context from Compose environment.
 */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Copies the temporary scanner GMS Uri output stream into permanent app files sandboxing.
 */
private fun saveScannedDocumentToDisk(context: Context, tempUri: Uri): File? {
    val dest = File(context.filesDir, "scanned_doc_${System.currentTimeMillis()}.pdf")
    return try {
        context.contentResolver.openInputStream(tempUri)?.use { inp ->
            FileOutputStream(dest).use { out ->
                inp.copyTo(out)
            }
        }
        dest
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
