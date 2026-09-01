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
            val encodedUri = android.net.Uri.encode(fileUri)
            return "viewer_dispatcher?fileUri=$encodedUri"
        }
    }

    /**
     * Offline Image adjustment tools screen (compress, resize, rotate, transcode).
     */
    object ImageTools : Screen("image_tools?fileUri={fileUri}&tab={tab}") {
        fun createRoute(fileUri: String? = null, tab: Int? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(it) }
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
            val encodedUri = android.net.Uri.encode(fileUri)
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
            val encodedUri = android.net.Uri.encode(fileUri)
            return "signature_pad?fileUri=$encodedUri"
        }
    }

    /**
     * Document Watermarking settings panel screen.
     */
    object Watermark : Screen("watermark?fileUri={fileUri}") {
        fun createRoute(fileUri: String): String {
            val encodedUri = android.net.Uri.encode(fileUri)
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
            val encodedUri = fileUri?.let { android.net.Uri.encode(it) }
            return "pdf_decrypt?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone PDF Rotate Screen
     */
    object PdfRotate : Screen("pdf_rotate?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(it) }
            return "pdf_rotate?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone PDF Extract Screen
     */
    object PdfExtract : Screen("pdf_extract?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(it) }
            return "pdf_extract?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone PDF Delete Screen
     */
    object PdfDelete : Screen("pdf_delete?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(it) }
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
            val encodedUri = fileUri?.let { android.net.Uri.encode(it) }
            return "docx_to_txt?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone CSV to XLSX Converter Screen
     */
    object CsvToXlsx : Screen("csv_to_xlsx?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(it) }
            return "csv_to_xlsx?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone XLSX to CSV Converter Screen
     */
    object XlsxToCsv : Screen("xlsx_to_csv?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(it) }
            return "xlsx_to_csv?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone PPTX to TXT Converter Screen
     */
    object PptxToTxt : Screen("pptx_to_txt?fileUri={fileUri}") {
        fun createRoute(fileUri: String? = null): String {
            val encodedUri = fileUri?.let { android.net.Uri.encode(it) }
            return "pptx_to_txt?fileUri=${encodedUri ?: ""}"
        }
    }

    /**
     * Standalone TAR Archive creation/extraction Screen
     */
    object TarTools : Screen("tar_tools")

    /**
     * Dedicated full-page history screen for browsing all recent files.
     */
    object History : Screen("history")

    /**
     * Sequential Image Viewer Screen for multiple images
     */
    object SequentialImageViewer : Screen("sequential_image_viewer?uris={uris}&title={title}") {
        fun createRoute(uris: List<String>, title: String = "Extracted Images"): String {
            val joined = uris.joinToString("|||")
            val encodedUris = android.net.Uri.encode(joined)
            val encodedTitle = android.net.Uri.encode(title)
            return "sequential_image_viewer?uris=$encodedUris&title=$encodedTitle"
        }
    }

    /**
     * PDF Page Numbering Screen
     */
    object PdfPageNumber : Screen("pdf_page_number")

    /**
     * PDF Page Reorder Screen
     */
    object PdfReorder : Screen("pdf_reorder")

    /**
     * PDF Extract Images Screen
     */
    object PdfExtractImages : Screen("pdf_extract_images")

    /**
     * TXT to PDF Converter Screen
     */
    object TxtToPdf : Screen("txt_to_pdf")

    /**
     * CSV to PDF Converter Screen
     */
    object CsvToPdf : Screen("csv_to_pdf")

    /**
     * PDF to TXT Converter Screen
     */
    object PdfToTxt : Screen("pdf_to_txt")

    /**
     * Images to PDF with Layout Options Screen
     */
    object ImagesToPdfLayout : Screen("images_to_pdf_layout")

    /**
     * PDF Header & Footer Screen
     */
    object PdfHeaderFooter : Screen("pdf_header_footer")

    /**
     * PDF Resize Pages Screen
     */
    object PdfResize : Screen("pdf_resize")

    /**
     * Password-Protected ZIP Screen
     */
    object PasswordZip : Screen("password_zip")

    /**
     * PDF to PDF/A Screen
     */
    object PdfToPdfA : Screen("pdf_to_pdfa")

    /**
     * PDF Metadata Editor Screen
     */
    object PdfMetadata : Screen("pdf_metadata")

    /**
     * PDF Crop Margins Screen
     */
    object PdfCropMargins : Screen("pdf_crop_margins")

    /**
     * PDF Redact Screen
     */
    object PdfRedact : Screen("pdf_redact")

    /**
     * PDF Repair Screen
     */
    object PdfRepair : Screen("pdf_repair")

    /**
     * PDF Overlay Screen
     */
    object PdfOverlay : Screen("pdf_overlay")

    /**
     * PDF Compare Screen
     */
    object PdfCompare : Screen("pdf_compare")

    /**
     * PDF to Markdown Screen
     */
    object PdfToMarkdown : Screen("pdf_to_markdown")

    /**
     * PDF Split by Size Screen
     */
    object PdfSplitBySize : Screen("pdf_split_by_size")

    /**
     * PDF Insert Pages Screen
     */
    object PdfInsertPages : Screen("pdf_insert_pages")

    /**
     * PDF Replace Pages Screen
     */
    object PdfReplacePages : Screen("pdf_replace_pages")

    /**
     * PDF Bookmark Editor Screen
     */
    object PdfBookmarks : Screen("pdf_bookmarks")

    /**
     * Password ZIP Extract Screen
     */
    object PasswordZipExtract : Screen("password_zip_extract")

    /**
     * PDF Split by Bookmarks Screen
     */
    object PdfSplitByBookmarks : Screen("pdf_split_by_bookmarks")

    /**
     * PDF Underlay Screen
     */
    object PdfUnderlay : Screen("pdf_underlay")

    /**
     * PDF Form Creation Screen
     */
    object PdfFormCreation : Screen("pdf_form_creation")

    /**
     * File Encrypt Screen
     */
    object FileEncrypt : Screen("file_encrypt")

    /**
     * File Decrypt Screen
     */
    object FileDecrypt : Screen("file_decrypt")

    /**
     * PDF Selective Image Extract Screen
     */
    object PdfSelectiveImageExtract : Screen("pdf_selective_image_extract")

    /**
     * PDF All Pages to Image Screen
     */
    object PdfAllPagesToImage : Screen("pdf_all_pages_to_image")

    /**
     * File Checksum Screen
     */
    object FileChecksum : Screen("file_checksum")

    /**
     * PDF Replace Pages Screen
     */
    object PdfReplacePages : Screen("pdf_replace_pages")

    /**
     * Unit Converter Screen
     */
    object UnitConverter : Screen("unit_converter")

    /**
     * Color Picker Screen
     */
    object ColorPicker : Screen("color_picker")

    /**
     * Collage Maker Screen
     */
    object CollageMaker : Screen("collage_maker")

    /**
     * Meme Maker Screen
     */
    object MemeMaker : Screen("meme_maker")

    /**
     * PDF Bookmark Reader Screen
     */
    object PdfBookmarkReader : Screen("pdf_bookmark_reader")

    /**
     * PDF/A Validation Screen
     */
    object PdfAValidation : Screen("pdf_a_validation")

    /**
     * Enhanced PDF to Word Screen
     */
    object PdfToWordEnhanced : Screen("pdf_to_word_enhanced")

    /**
     * Enhanced Markdown to PDF Screen
     */
    object MarkdownToPdfEnhanced : Screen("markdown_to_pdf_enhanced")

    /**
     * SVG to PDF Screen
     */
    object SvgToPdf : Screen("svg_to_pdf")

    /**
     * Advanced Word Count Screen
     */
    object AdvancedWordCount : Screen("advanced_word_count")

    /**
     * Sticker Maker Screen
     */
    object StickerMaker : Screen("sticker_maker")

    /**
     * Sticker Import Screen
     */
    object StickerImport : Screen("sticker_import")

    /**
     * Exact Resize Screen
     */
    object ExactResize : Screen("exact_resize")

    /**
     * Read Aloud Screen
     */
    object ReadAloud : Screen("read_aloud")
}


