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
            val encodedUri = android.net.Uri.encode(android.net.Uri.encode(fileUri))
            return "viewer_dispatcher?fileUri=$encodedUri"
        }
    }

    /**
     * Offline Image adjustment tools screen (compress, resize, rotate, transcode).
     */
    object ImageTools : Screen("image_tools?fileUri={fileUri}&tab={tab}") {
        fun createRoute(fileUri: String? = null, tab: Int? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(android.net.Uri.encode(it)) }
            return "image_tools?fileUri=${encodedUri ?: ""}&tab=${tab ?: ""}"
        }
    }

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
    object PdfLock : Screen("pdf_lock?fileUri={fileUri}") {
        fun createRoute(fileUri: String): String {
            val encodedUri = android.net.Uri.encode(android.net.Uri.encode(fileUri))
            return "pdf_lock?fileUri=$encodedUri"
        }
    }

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
    object SignaturePad : Screen("signature_pad?fileUri={fileUri}") {
        fun createRoute(fileUri: String): String {
            val encodedUri = android.net.Uri.encode(android.net.Uri.encode(fileUri))
            return "signature_pad?fileUri=$encodedUri"
        }
    }

    /**
     * Document Watermarking settings panel screen.
     */
    object Watermark : Screen("watermark?fileUri={fileUri}") {
        fun createRoute(fileUri: String): String {
            val encodedUri = android.net.Uri.encode(android.net.Uri.encode(fileUri))
            return "watermark?fileUri=$encodedUri"
        }
    }

    /**
     * Offline Batch operations panel screen (Image Lab, PDF Lock).
     */
    object BatchTools : Screen("batch_tools")

    /**
     * Standalone ZIP File Maker Screen.
     */
    object ZipMaker : Screen("zip_maker")

    /**
     * Standalone Images to PDF Compiler Screen.
     */
    object ImagesToPdf : Screen("images_to_pdf")

    /**
     * Standalone PDF Compressor Screen
     */
    object PdfCompress : Screen("pdf_compress")

    /**
     * Standalone PDF Form Flattening Screen
     */
    object PdfFlatten : Screen("pdf_flatten")

    /**
     * Standalone Excel to PDF Converter Screen
     */
    object XlsToPdf : Screen("xls_to_pdf")

    /**
     * Standalone PDF Decrypt Screen
     */
    object PdfDecrypt : Screen("pdf_decrypt?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(android.net.Uri.encode(it)) }
            return "pdf_decrypt?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone PDF Rotate Screen
     */
    object PdfRotate : Screen("pdf_rotate?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(android.net.Uri.encode(it)) }
            return "pdf_rotate?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone PDF Extract Screen
     */
    object PdfExtract : Screen("pdf_extract?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(android.net.Uri.encode(it)) }
            return "pdf_extract?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone PDF Delete Screen
     */
    object PdfDelete : Screen("pdf_delete?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(android.net.Uri.encode(it)) }
            return "pdf_delete?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone Web to PDF Screen
     */
    object WebToPdf : Screen("web_to_pdf")

    /**
     * Standalone HTML to PDF Screen
     */
    object HtmlToPdf : Screen("html_to_pdf")

    /**
     * Standalone Markdown to PDF Screen
     */
    object MarkdownToPdf : Screen("markdown_to_pdf")

    /**
     * Standalone DOCX to TXT Converter Screen
     */
    object DocxToTxt : Screen("docx_to_txt?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(android.net.Uri.encode(it)) }
            return "docx_to_txt?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone CSV to XLSX Converter Screen
     */
    object CsvToXlsx : Screen("csv_to_xlsx?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(android.net.Uri.encode(it)) }
            return "csv_to_xlsx?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone XLSX to CSV Converter Screen
     */
    object XlsxToCsv : Screen("xlsx_to_csv?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(android.net.Uri.encode(it)) }
            return "xlsx_to_csv?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone PPTX to TXT Converter Screen
     */
    object PptxToTxt : Screen("pptx_to_txt?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(android.net.Uri.encode(it)) }
            return "pptx_to_txt?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone TAR Archive creation/extraction Screen
     */
    object TarTools : Screen("tar_tools")
}


