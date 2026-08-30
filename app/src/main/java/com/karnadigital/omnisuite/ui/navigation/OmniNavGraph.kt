package com.karnadigital.omnisuite.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.karnadigital.omnisuite.feature.home.HomeScreen
import com.karnadigital.omnisuite.feature.home.NavigationEvent
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
                onEvent = { event ->
                    when (event) {
                        is NavigationEvent.NavigateToSettings -> navController.navigate(Screen.Settings.route)
                        is NavigationEvent.NavigateToQrGenerator -> navController.navigate(Screen.QrGenerator.route)
                        is NavigationEvent.NavigateToBarcodeScanner -> navController.navigate(Screen.BarcodeScanner.route)
                        is NavigationEvent.NavigateToImageTools -> navController.navigate(Screen.ImageTools.createRoute(tab = 0))
                        is NavigationEvent.NavigateToImageToolsWithTab -> navController.navigate(Screen.ImageTools.createRoute(tab = event.tab))
                        is NavigationEvent.NavigateToPdfMerge -> navController.navigate(Screen.PdfMerge.route)
                        is NavigationEvent.NavigateToPdfSplit -> navController.navigate(Screen.PdfSplit.route)
                        is NavigationEvent.NavigateToPdfLock -> navController.navigate(Screen.PdfLock.route)
                        is NavigationEvent.NavigateToDocToPdf -> navController.navigate(Screen.DocToPdf.route)
                        is NavigationEvent.NavigateToPptToPdf -> navController.navigate(Screen.PptToPdf.route)
                        is NavigationEvent.NavigateToScanToPdf -> navController.navigate(Screen.ScanToPdf.route)
                        is NavigationEvent.NavigateToPdfToImages -> navController.navigate(Screen.PdfToImages.route)
                        is NavigationEvent.NavigateToOcr -> navController.navigate(Screen.Ocr.route)
                        is NavigationEvent.NavigateToSignaturePad -> navController.navigate(Screen.SignaturePad.route)
                        is NavigationEvent.NavigateToWatermark -> navController.navigate(Screen.Watermark.route)
                        is NavigationEvent.NavigateToPdfToWord -> navController.navigate(Screen.PdfToWord.route)
                        is NavigationEvent.NavigateToPdfToPpt -> navController.navigate(Screen.PdfToPpt.route)
                        is NavigationEvent.NavigateToPdfToExcel -> navController.navigate(Screen.PdfToExcel.route)
                        is NavigationEvent.NavigateToPdfFormFiller -> navController.navigate(Screen.PdfFormFiller.route)
                        is NavigationEvent.NavigateToImagesToPdf -> navController.navigate(Screen.ImagesToPdf.route)
                        is NavigationEvent.NavigateToPdfCompress -> navController.navigate(Screen.PdfCompress.route)
                        is NavigationEvent.NavigateToPdfFlatten -> navController.navigate(Screen.PdfFlatten.route)
                        is NavigationEvent.NavigateToXlsToPdf -> navController.navigate(Screen.XlsToPdf.route)
                        is NavigationEvent.NavigateToBatchTools -> navController.navigate(Screen.BatchTools.route)
                        is NavigationEvent.NavigateToZipMaker -> navController.navigate(Screen.ZipMaker.route)
                        is NavigationEvent.NavigateToPdfDecrypt -> navController.navigate(Screen.PdfDecrypt.createRoute())
                        is NavigationEvent.NavigateToPdfRotate -> navController.navigate(Screen.PdfRotate.createRoute())
                        is NavigationEvent.NavigateToPdfExtract -> navController.navigate(Screen.PdfExtract.createRoute())
                        is NavigationEvent.NavigateToPdfDelete -> navController.navigate(Screen.PdfDelete.createRoute())
                        is NavigationEvent.NavigateToWebToPdf -> navController.navigate(Screen.WebToPdf.route)
                        is NavigationEvent.NavigateToHtmlToPdf -> navController.navigate(Screen.HtmlToPdf.route)
                        is NavigationEvent.NavigateToMarkdownToPdf -> navController.navigate(Screen.MarkdownToPdf.route)
                        is NavigationEvent.NavigateToDocxToTxt -> navController.navigate(Screen.DocxToTxt.createRoute())
                        is NavigationEvent.NavigateToCsvToXlsx -> navController.navigate(Screen.CsvToXlsx.createRoute())
                        is NavigationEvent.NavigateToXlsxToCsv -> navController.navigate(Screen.XlsxToCsv.createRoute())
                        is NavigationEvent.NavigateToPptxToTxt -> navController.navigate(Screen.PptxToTxt.createRoute())
                        is NavigationEvent.NavigateToTarTools -> navController.navigate(Screen.TarTools.route)
                        is NavigationEvent.NavigateToHistory -> navController.navigate(Screen.History.route)
                        is NavigationEvent.OpenFile -> navController.navigate(Screen.ViewerDispatcher.createRoute(event.fileUri))
                        is NavigationEvent.OpenSequentialImages -> navController.navigate(Screen.SequentialImageViewer.createRoute(event.imageUris, event.title))
                        is NavigationEvent.SelectFileForType -> { /* handled internally by HomeScreen */ }
                    }
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
            ViewerDispatcherScreen(
                fileUri = fileUri,
                onOpenFile = { targetUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(targetUri))
                },
                onNavigate = { route ->
                    navController.navigate(route)
                },
                onNavigateImageTool = { uri, tab ->
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
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
            val fileUri = backStackEntry.arguments?.getString("fileUri")
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

        // 29. Standalone Sequential Image Viewer Screen
        composable(
            route = Screen.SequentialImageViewer.route,
            arguments = listOf(
                navArgument("uris") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = "Extracted Images"
                }
            )
        ) { backStackEntry ->
            val urisParam = backStackEntry.arguments?.getString("uris").orEmpty()
            val titleParam = backStackEntry.arguments?.getString("title").orEmpty().ifBlank { "Extracted Images" }
            val decodedUris = if (urisParam.isNotBlank()) {
                urisParam.split("|||").filter { it.isNotBlank() }
            } else {
                emptyList()
            }
            com.karnadigital.omnisuite.feature.viewer.SequentialImageViewerScreen(
                imageUris = decodedUris,
                title = titleParam,
                onBack = { navController.popBackStack() }
            )
        }

        // History Screen (Full Page)
        composable(route = Screen.History.route) {
            com.karnadigital.omnisuite.feature.history.HistoryScreen(
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
