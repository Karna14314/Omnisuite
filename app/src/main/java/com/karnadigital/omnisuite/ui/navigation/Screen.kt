package com.karnadigital.omnisuite.ui.navigation

/**
 * Sealed class representing all navigatable screen routes inside OmniSuite.
 */
sealed class Screen(val route: String) {
    
    /**
     * The primary root shell screen containing the Workspace dashboard and History tabs.
     */
    object MainShell : Screen("main_shell")

    /**
     * Dedicated All Tools hub screen.
     */
    object Tools : Screen("tools")

    /**
     * Storage Access Framework file browser screen.
     */
    object Files : Screen("files")

    /**
     * Settings configurations screen.
     */
    object Settings : Screen("settings")

    /**
     * Real-time offline QR code generator screen.
     */
    object QrGenerator : Screen("qr_generator")

    /**
     * Dedicated CameraX barcode and QR viewfinder scanner screen.
     */
    object BarcodeScanner : Screen("barcode_scanner")

    /**
     * Polymorphic file viewer router that opens files of different MIME types dynamically.
     * Takes an optional/required Storage Access Framework (SAF) URI string as a query argument.
     */
    object ViewerDispatcher : Screen("viewer_dispatcher?fileUri={fileUri}") {
        fun createRoute(fileUri: String): String {
            return "viewer_dispatcher?fileUri=$fileUri"
        }
    }

    /**
     * Offline Image adjustment tools screen (compress, resize, rotate, transcode).
     */
    object ImageTools : Screen("image_tools")

    /**
     * Offline PDF Factory engines screen (Merge, Split, Password Lock).
     */
    object PdfTools : Screen("pdf_tools")

    /**
     * On-Demand Text Recognition (OCR) vision screen.
     */
    object Ocr : Screen("ocr")

    /**
     * Digital Signature Pad capture and PDF stamping screen.
     */
    object SignaturePad : Screen("signature_pad")

    /**
     * Document Watermarking settings panel screen.
     */
    object Watermark : Screen("watermark")

    /**
     * Offline Batch operations panel screen (Image Lab, PDF Lock).
     */
    object BatchTools : Screen("batch_tools")
}


