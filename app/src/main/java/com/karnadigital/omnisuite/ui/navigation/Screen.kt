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
     * Standalone PDF Merger Screen
     */
    object PdfMerge : Screen("pdf_merge")

    /**
     * Standalone PDF Splitter Screen
     */
    object PdfSplit : Screen("pdf_split")

    /**
     * Standalone PDF Password Lock Screen
     */
    object PdfLock : Screen("pdf_lock")

    /**
     * Standalone Word to PDF Converter Screen
     */
    object DocToPdf : Screen("doc_to_pdf")

    /**
     * Standalone PowerPoint to PDF Converter Screen
     */
    object PptToPdf : Screen("ppt_to_pdf")

    /**
     * Standalone Scanner to PDF Screen
     */
    object ScanToPdf : Screen("scan_to_pdf")

    /**
     * Standalone PDF to Images Extractor Screen
     */
    object PdfToImages : Screen("pdf_to_images")

    /**
     * Standalone PDF to Word Converter Screen
     */
    object PdfToWord : Screen("pdf_to_word")

    /**
     * Standalone PDF to PowerPoint Converter Screen
     */
    object PdfToPpt : Screen("pdf_to_ppt")

    /**
     * Standalone PDF to Excel Converter Screen
     */
    object PdfToExcel : Screen("pdf_to_excel")

    /**
     * Standalone PDF Form Filler Screen
     */
    object PdfFormFiller : Screen("pdf_form_filler")

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


