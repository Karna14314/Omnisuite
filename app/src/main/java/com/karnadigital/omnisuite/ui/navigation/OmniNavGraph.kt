package com.karnadigital.omnisuite.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.karnadigital.omnisuite.feature.home.HomeScreen
import com.karnadigital.omnisuite.feature.utility.QrGeneratorScreen
import com.karnadigital.omnisuite.feature.utility.BarcodeScannerScreen
import com.karnadigital.omnisuite.feature.settings.SettingsScreen
import com.karnadigital.omnisuite.feature.viewer.ViewerDispatcherScreen
import com.karnadigital.omnisuite.feature.tools.ImageToolsScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfMergeScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfSplitScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfLockScreen
import com.karnadigital.omnisuite.feature.pdf_tools.DocToPdfScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PptToPdfScreen
import com.karnadigital.omnisuite.feature.pdf_tools.ScanToPdfScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfToImagesScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfToWordScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfToPptScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfToExcelScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfFormFillerScreen
import com.karnadigital.omnisuite.feature.utility.OcrScreen
import com.karnadigital.omnisuite.feature.pdf_tools.SignaturePadScreen
import com.karnadigital.omnisuite.feature.pdf_tools.WatermarkScreen
import com.karnadigital.omnisuite.feature.tools.BatchToolsScreen
import com.karnadigital.omnisuite.feature.tools.ZipMakerScreen
import com.karnadigital.omnisuite.feature.pdf_tools.ImagesToPdfScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfCompressScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfFlattenScreen
import com.karnadigital.omnisuite.feature.pdf_tools.XlsToPdfScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfDecryptScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfRotateScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfExtractScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PdfDeleteScreen
import com.karnadigital.omnisuite.feature.pdf_tools.WebToPdfScreen
import com.karnadigital.omnisuite.feature.pdf_tools.HtmlToPdfScreen
import com.karnadigital.omnisuite.feature.pdf_tools.MarkdownToPdfScreen
import com.karnadigital.omnisuite.feature.pdf_tools.DocxToTxtScreen
import com.karnadigital.omnisuite.feature.pdf_tools.CsvToXlsxScreen
import com.karnadigital.omnisuite.feature.pdf_tools.XlsxToCsvScreen
import com.karnadigital.omnisuite.feature.pdf_tools.PptxToTxtScreen
import com.karnadigital.omnisuite.feature.tools.TarToolsScreen


/**
 * Global Jetpack Compose Navigation graph for OmniSuite.
 * Manages screen state transitions and dynamic parameterized routing off-device.
 */
@Composable
fun OmniNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.MainShell.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 1. Root Main Shell Screen (Dashboard & History Bottom Tabs)
        composable(route = Screen.MainShell.route) {
            HomeScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToQrGenerator = {
                    navController.navigate(Screen.QrGenerator.route)
                },
                onNavigateToBarcodeScanner = {
                    navController.navigate(Screen.BarcodeScanner.route)
                },
                onNavigateToImageTools = {
                    navController.navigate(Screen.ImageTools.route)
                },
                onNavigateToPdfMerge = {
                    navController.navigate(Screen.PdfMerge.route)
                },
                onNavigateToPdfSplit = {
                    navController.navigate(Screen.PdfSplit.route)
                },
                onNavigateToPdfLock = {
                    navController.navigate(Screen.PdfLock.route)
                },
                onNavigateToDocToPdf = {
                    navController.navigate(Screen.DocToPdf.route)
                },
                onNavigateToPptToPdf = {
                    navController.navigate(Screen.PptToPdf.route)
                },
                onNavigateToScanToPdf = {
                    navController.navigate(Screen.ScanToPdf.route)
                },
                onNavigateToPdfToImages = {
                    navController.navigate(Screen.PdfToImages.route)
                },
                onNavigateToOcr = {
                    navController.navigate(Screen.Ocr.route)
                },
                onNavigateToSignaturePad = {
                    navController.navigate(Screen.SignaturePad.route)
                },
                onNavigateToWatermark = {
                    navController.navigate(Screen.Watermark.route)
                },
                onNavigateToPdfToWord = {
                    navController.navigate(Screen.PdfToWord.route)
                },
                onNavigateToPdfToPpt = {
                    navController.navigate(Screen.PdfToPpt.route)
                },
                onNavigateToPdfToExcel = {
                    navController.navigate(Screen.PdfToExcel.route)
                },
                onNavigateToPdfFormFiller = {
                    navController.navigate(Screen.PdfFormFiller.route)
                },
                onNavigateToBatchTools = {
                    navController.navigate(Screen.BatchTools.route)
                },
                onNavigateToZipMaker = {
                    navController.navigate(Screen.ZipMaker.route)
                },
                onNavigateToImagesToPdf = {
                    navController.navigate(Screen.ImagesToPdf.route)
                },
                onNavigateToPdfCompress = {
                    navController.navigate(Screen.PdfCompress.route)
                },
                onNavigateToPdfFlatten = {
                    navController.navigate(Screen.PdfFlatten.route)
                },
                onNavigateToXlsToPdf = {
                    navController.navigate(Screen.XlsToPdf.route)
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                },
                onNavigateToPdfDecrypt = {
                    navController.navigate(Screen.PdfDecrypt.createRoute())
                },
                onNavigateToPdfRotate = {
                    navController.navigate(Screen.PdfRotate.createRoute())
                },
                onNavigateToPdfExtract = {
                    navController.navigate(Screen.PdfExtract.createRoute())
                },
                onNavigateToPdfDelete = {
                    navController.navigate(Screen.PdfDelete.createRoute())
                },
                onNavigateToWebToPdf = {
                    navController.navigate(Screen.WebToPdf.route)
                },
                onNavigateToHtmlToPdf = {
                    navController.navigate(Screen.HtmlToPdf.route)
                },
                onNavigateToMarkdownToPdf = {
                    navController.navigate(Screen.MarkdownToPdf.route)
                },
                onNavigateToDocxToTxt = {
                    navController.navigate(Screen.DocxToTxt.createRoute())
                },
                onNavigateToCsvToXlsx = {
                    navController.navigate(Screen.CsvToXlsx.createRoute())
                },
                onNavigateToXlsxToCsv = {
                    navController.navigate(Screen.XlsxToCsv.createRoute())
                },
                onNavigateToPptxToTxt = {
                    navController.navigate(Screen.PptxToTxt.createRoute())
                },
                onNavigateToTarTools = {
                    navController.navigate(Screen.TarTools.route)
                }
            )
        }


        // 2. Settings Panel
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 3. Offline QR/Barcode Generator
        composable(route = Screen.QrGenerator.route) {
            QrGeneratorScreen(
                onBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 4. CameraX Scanner
        composable(route = Screen.BarcodeScanner.route) {
            BarcodeScannerScreen(
                onBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 5. Dynamic Viewer Dispatcher
        composable(
            route = Screen.ViewerDispatcher.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            ViewerDispatcherScreen(
                fileUri = fileUri,
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                },
                onOpenPdfTool = { route ->
                    navController.navigate(route)
                },
                onOpenImageTool = { uri, tab ->
                    navController.navigate(Screen.ImageTools.createRoute(uri, tab))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 6. Local Offline Image Utilities
        composable(
            route = Screen.ImageTools.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("tab") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            val tabString = backStackEntry.arguments?.getString("tab")
            val tab = tabString?.toIntOrNull() ?: 0

            ImageToolsScreen(
                initialUri = fileUri,
                initialTab = tab,
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 7a. Standalone PDF Merger Screen
        composable(route = Screen.PdfMerge.route) {
            PdfMergeScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 7b. Standalone PDF Splitter Screen
        composable(route = Screen.PdfSplit.route) {
            PdfSplitScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 7c. Standalone PDF Password Lock Screen
        composable(
            route = Screen.PdfLock.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            PdfLockScreen(
                initialPdfUri = fileUri,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                }
            )
        }

        // 7d. Standalone Word to PDF Converter Screen
        composable(route = Screen.DocToPdf.route) {
            DocToPdfScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 7e. Standalone PowerPoint to PDF Converter Screen
        composable(route = Screen.PptToPdf.route) {
            PptToPdfScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 7f. Standalone Scanner to PDF Screen
        composable(route = Screen.ScanToPdf.route) {
            ScanToPdfScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 7g. Standalone PDF to Images Screen
        composable(route = Screen.PdfToImages.route) {
            PdfToImagesScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 7h. Standalone PDF to Word Screen
        composable(route = Screen.PdfToWord.route) {
            PdfToWordScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 7i. Standalone PDF to PPT Screen
        composable(route = Screen.PdfToPpt.route) {
            PdfToPptScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 7j. Standalone PDF to Excel Screen
        composable(route = Screen.PdfToExcel.route) {
            PdfToExcelScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 7k. Standalone PDF Form Filler Screen
        composable(route = Screen.PdfFormFiller.route) {
            PdfFormFillerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 8. On-Demand Text Recognition (OCR) Engine
        composable(route = Screen.Ocr.route) {
            OcrScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 9. Digital Signature Stamp Pad
        composable(
            route = Screen.SignaturePad.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            SignaturePadScreen(
                initialPdfUri = fileUri,
                onBack = {
                    navController.popBackStack()
                },
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                }
            )
        }

        // 10. Document Watermarking Panel
        composable(
            route = Screen.Watermark.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            WatermarkScreen(
                initialPdfUri = fileUri,
                onBack = {
                    navController.popBackStack()
                },
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                }
            )
        }

        // 11. Offline Batch Utilities Screen (Image Lab, PDF Lock)
        composable(route = Screen.BatchTools.route) {
            BatchToolsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 12. Offline ZIP Maker Screen
        composable(route = Screen.ZipMaker.route) {
            ZipMakerScreen(
                onBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 13. Offline Images to PDF Screen
        composable(route = Screen.ImagesToPdf.route) {
            ImagesToPdfScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 14. Standalone PDF Compressor
        composable(route = Screen.PdfCompress.route) {
            PdfCompressScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 15. Standalone PDF Flatten
        composable(route = Screen.PdfFlatten.route) {
            PdfFlattenScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 16. Standalone Excel to PDF
        composable(route = Screen.XlsToPdf.route) {
            XlsToPdfScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 17. Standalone PDF Decrypt Screen
        composable(
            route = Screen.PdfDecrypt.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            PdfDecryptScreen(
                initialPdfUri = fileUri,
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                }
            )
        }

        // 18. Standalone PDF Rotate Screen
        composable(
            route = Screen.PdfRotate.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            PdfRotateScreen(
                initialPdfUri = fileUri,
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                }
            )
        }

        // 19. Standalone PDF Extract Screen
        composable(
            route = Screen.PdfExtract.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            PdfExtractScreen(
                initialPdfUri = fileUri,
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                }
            )
        }

        // 20. Standalone PDF Delete Screen
        composable(
            route = Screen.PdfDelete.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            PdfDeleteScreen(
                initialPdfUri = fileUri,
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                }
            )
        }

        // 21. Standalone Web to PDF Screen
        composable(route = Screen.WebToPdf.route) {
            WebToPdfScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 22. Standalone HTML to PDF Screen
        composable(route = Screen.HtmlToPdf.route) {
            HtmlToPdfScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 23. Standalone Markdown to PDF Screen
        composable(route = Screen.MarkdownToPdf.route) {
            MarkdownToPdfScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }

        // 24. Standalone DOCX to TXT Screen
        composable(
            route = Screen.DocxToTxt.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            DocxToTxtScreen(
                initialUri = fileUri,
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                }
            )
        }

        // 25. Standalone CSV to XLSX Screen
        composable(
            route = Screen.CsvToXlsx.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            CsvToXlsxScreen(
                initialUri = fileUri,
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                }
            )
        }

        // 26. Standalone XLSX to CSV Screen
        composable(
            route = Screen.XlsxToCsv.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            XlsxToCsvScreen(
                initialUri = fileUri,
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                }
            )
        }

        // 27. Standalone PPTX to TXT Screen
        composable(
            route = Screen.PptxToTxt.route,
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")?.let { android.net.Uri.decode(it) }
            PptxToTxtScreen(
                initialUri = fileUri,
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                }
            )
        }

        // 28. Standalone TAR Archive creation/extraction Screen
        composable(route = Screen.TarTools.route) {
            TarToolsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }
    }
}
