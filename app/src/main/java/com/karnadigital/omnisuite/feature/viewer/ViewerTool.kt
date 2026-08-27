package com.karnadigital.omnisuite.feature.viewer

import com.karnadigital.omnisuite.ui.navigation.Screen

sealed class ViewerTool {
    data object OpenIn : ViewerTool()
    data object Print : ViewerTool()
    data object Share : ViewerTool()
    data object ExportPdf : ViewerTool()

    data class Navigate(val route: String) : ViewerTool()
    data class NavigateImageTool(val fileUri: String, val tab: Int) : ViewerTool()
}

enum class ImageLabTab(val index: Int) {
    QUICK_EDIT(0),
    LONG_STITCH(1),
    EXTRACT_TEXT(2),
    ID_CARD(3),
    WATERMARK(4)
}

fun pdfToolActions(fileUri: String): List<Pair<ViewerTool, String>> = listOf(
    ViewerTool.OpenIn to "Open in...",
    ViewerTool.Print to "Print",
    ViewerTool.Share to "Share",
    ViewerTool.Navigate(Screen.SignaturePad.createRoute(fileUri)) to "✍️ Add Digital Signature",
    ViewerTool.Navigate(Screen.Watermark.createRoute(fileUri)) to "🎨 Add Watermark",
    ViewerTool.Navigate(Screen.PdfLock.createRoute(fileUri)) to "🔒 Encrypt / Lock PDF",
    ViewerTool.Navigate(Screen.PdfDecrypt.createRoute(fileUri)) to "🔓 Decrypt PDF",
    ViewerTool.Navigate(Screen.PdfRotate.createRoute(fileUri)) to "🔄 Rotate Pages",
    ViewerTool.Navigate(Screen.PdfExtract.createRoute(fileUri)) to "✂️ Extract Pages",
    ViewerTool.Navigate(Screen.PdfDelete.createRoute(fileUri)) to "🗑️ Delete Pages",
    ViewerTool.Navigate(Screen.PdfCompress.route) to "🗜️ Compress PDF",
    ViewerTool.Navigate(Screen.PdfFlatten.route) to "🔒 Flatten Form"
)

fun docxToolActions(fileUri: String): List<Pair<ViewerTool, String>> = listOf(
    ViewerTool.OpenIn to "Open in...",
    ViewerTool.Print to "Print",
    ViewerTool.Share to "Share",
    ViewerTool.ExportPdf to "Export to PDF",
    ViewerTool.Navigate(Screen.DocxToTxt.createRoute(fileUri)) to "📄 Convert to TXT"
)

fun xlsxToolActions(fileUri: String): List<Pair<ViewerTool, String>> = listOf(
    ViewerTool.OpenIn to "Open in...",
    ViewerTool.Print to "Print",
    ViewerTool.Share to "Share",
    ViewerTool.ExportPdf to "Export to PDF",
    ViewerTool.Navigate(Screen.XlsxToCsv.createRoute(fileUri)) to "📥 Convert to CSV",
    ViewerTool.Navigate(Screen.CsvToXlsx.createRoute(fileUri)) to "📤 Import CSV"
)

fun pptxToolActions(fileUri: String): List<Pair<ViewerTool, String>> = listOf(
    ViewerTool.OpenIn to "Open in...",
    ViewerTool.Print to "Print",
    ViewerTool.Share to "Share",
    ViewerTool.ExportPdf to "Convert to PDF (Image)",
    ViewerTool.Navigate(Screen.PptxToTxt.createRoute(fileUri)) to "📄 Extract Text to TXT"
)

fun imageToolActions(fileUri: String): List<Pair<ViewerTool, String>> = listOf(
    ViewerTool.OpenIn to "Open in...",
    ViewerTool.Print to "Print",
    ViewerTool.Share to "Share",
    ViewerTool.ExportPdf to "Convert to PDF",
    ViewerTool.NavigateImageTool(fileUri, ImageLabTab.QUICK_EDIT.index) to "🖼️ Edit in Image Lab",
    ViewerTool.NavigateImageTool(fileUri, ImageLabTab.WATERMARK.index) to "💧 Add Watermark",
    ViewerTool.NavigateImageTool(fileUri, ImageLabTab.EXTRACT_TEXT.index) to "🔬 Extract Text (OCR)",
    ViewerTool.NavigateImageTool(fileUri, ImageLabTab.LONG_STITCH.index) to "🧵 Long Stitch",
    ViewerTool.NavigateImageTool(fileUri, ImageLabTab.ID_CARD.index) to "🪪 ID Card Maker"
)

fun txtToolActions(): List<Pair<ViewerTool, String>> = listOf(
    ViewerTool.Print to "Print",
    ViewerTool.Share to "Share"
)
