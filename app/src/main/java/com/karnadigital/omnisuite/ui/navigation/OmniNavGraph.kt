package com.karnadigital.omnisuite.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.karnadigital.omnisuite.feature.archive.*
import com.karnadigital.omnisuite.feature.history.*
import com.karnadigital.omnisuite.feature.home.*
import com.karnadigital.omnisuite.feature.pdf_tools.*
import com.karnadigital.omnisuite.feature.settings.*
import com.karnadigital.omnisuite.feature.tools.*
import com.karnadigital.omnisuite.feature.utility.*
import com.karnadigital.omnisuite.feature.viewer.*


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
                        is NavigationEvent.NavigateToPdfPageNumber -> navController.navigate(Screen.PdfPageNumber.route)
                        is NavigationEvent.NavigateToPdfReorder -> navController.navigate(Screen.PdfReorder.route)
                        is NavigationEvent.NavigateToPdfExtractImages -> navController.navigate(Screen.PdfExtractImages.route)
                        is NavigationEvent.NavigateToTxtToPdf -> navController.navigate(Screen.TxtToPdf.route)
                        is NavigationEvent.NavigateToCsvToPdf -> navController.navigate(Screen.CsvToPdf.route)
                        is NavigationEvent.NavigateToPdfToTxt -> navController.navigate(Screen.PdfToTxt.route)
                        is NavigationEvent.NavigateToImagesToPdfLayout -> navController.navigate(Screen.ImagesToPdfLayout.route)
                        is NavigationEvent.NavigateToPdfHeaderFooter -> navController.navigate(Screen.PdfHeaderFooter.route)
                        is NavigationEvent.NavigateToPdfResize -> navController.navigate(Screen.PdfResize.route)
                        is NavigationEvent.NavigateToPasswordZip -> navController.navigate(Screen.PasswordZip.route)
                        is NavigationEvent.NavigateToPdfMetadata -> navController.navigate(Screen.PdfMetadata.route)
                        is NavigationEvent.NavigateToPdfCropMargins -> navController.navigate(Screen.PdfCropMargins.route)
                        is NavigationEvent.NavigateToPdfRedact -> navController.navigate(Screen.PdfRedact.route)
                        is NavigationEvent.NavigateToPdfCompare -> navController.navigate(Screen.PdfCompare.route)
                        is NavigationEvent.NavigateToPdfInsertPages -> navController.navigate(Screen.PdfInsertPages.route)
                        is NavigationEvent.NavigateToPdfReplacePages -> navController.navigate(Screen.PdfReplacePages.route)
                        is NavigationEvent.NavigateToPdfBookmarks -> navController.navigate(Screen.PdfBookmarks.route)
                        is NavigationEvent.NavigateToPasswordZipExtract -> navController.navigate(Screen.PasswordZipExtract.route)
                        is NavigationEvent.NavigateToFileEncrypt -> navController.navigate(Screen.FileEncrypt.route)
                        is NavigationEvent.NavigateToFileDecrypt -> navController.navigate(Screen.FileDecrypt.route)
                        is NavigationEvent.NavigateToPdfSelectiveImageExtract -> navController.navigate(Screen.PdfSelectiveImageExtract.route)
                        is NavigationEvent.NavigateToPdfAllPagesToImage -> navController.navigate(Screen.PdfAllPagesToImage.route)
                        is NavigationEvent.NavigateToFileChecksum -> navController.navigate(Screen.FileChecksum.route)
                        is NavigationEvent.NavigateToTextCompare -> navController.navigate(Screen.TextCompare.route)
                        is NavigationEvent.NavigateToUnitConverter -> navController.navigate(Screen.UnitConverter.route)
                        is NavigationEvent.NavigateToColorPicker -> navController.navigate(Screen.ColorPicker.route)
                        is NavigationEvent.NavigateToCollageMaker -> navController.navigate(Screen.CollageMaker.route)
                        is NavigationEvent.NavigateToMemeMaker -> navController.navigate(Screen.MemeMaker.route)
                        is NavigationEvent.NavigateToPdfBookmarkReader -> navController.navigate(Screen.PdfBookmarkReader.route)
                        is NavigationEvent.NavigateToPdfToWordEnhanced -> navController.navigate(Screen.PdfToWordEnhanced.route)
                        is NavigationEvent.NavigateToMarkdownToPdfEnhanced -> navController.navigate(Screen.MarkdownToPdfEnhanced.route)
                        is NavigationEvent.NavigateToAdvancedWordCount -> navController.navigate(Screen.AdvancedWordCount.route)
                        is NavigationEvent.NavigateToExactResize -> navController.navigate(Screen.ExactResize.route)
                        is NavigationEvent.NavigateToReadAloud -> navController.navigate(Screen.ReadAloud.route)
                        is NavigationEvent.NavigateToPdfBlockEditor -> navController.navigate(Screen.PdfBlockEditor.route)
                        is NavigationEvent.NavigateToPrintImpositionStudio -> navController.navigate(Screen.PrintImpositionStudio.route)
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
                fileUri = fileUri,
                onBack = {
                    navController.popBackStack()
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
                onOpenFile = { uri, name ->
                    val encodedUri = android.net.Uri.encode(uri.toString())
                    navController.navigate(Screen.ViewerDispatcher.createRoute(encodedUri))
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
                onBack = { navController.popBackStack() }
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
                fileUri = fileUri,
                onBack = { navController.popBackStack() }
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
                fileUri = fileUri,
                onBack = { navController.popBackStack() }
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
                fileUri = fileUri,
                onBack = { navController.popBackStack() }
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
                fileUri = fileUri,
                onBack = { navController.popBackStack() }
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
                onBack = { navController.popBackStack() }
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

        // 30. PDF Page Numbering Screen
        composable(route = Screen.PdfPageNumber.route) {
            PdfPageNumberScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 31. PDF Reorder Pages Screen
        composable(route = Screen.PdfReorder.route) {
            PdfReorderScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 32. PDF Extract Images Screen
        composable(route = Screen.PdfExtractImages.route) {
            PdfExtractImagesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 33. TXT to PDF Screen
        composable(route = Screen.TxtToPdf.route) {
            TxtToPdfScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 34. CSV to PDF Screen
        composable(route = Screen.CsvToPdf.route) {
            CsvToPdfScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 35. PDF to TXT Screen
        composable(route = Screen.PdfToTxt.route) {
            PdfToTxtScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 36. Images to PDF with Layout Screen
        composable(route = Screen.ImagesToPdfLayout.route) {
            ImagesToPdfLayoutScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 37. PDF Header & Footer Screen
        composable(route = Screen.PdfHeaderFooter.route) {
            PdfHeaderFooterScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 38. PDF Resize Pages Screen
        composable(route = Screen.PdfResize.route) {
            PdfResizeScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 39. Password-Protected ZIP Screen
        composable(route = Screen.PasswordZip.route) {
            PasswordZipScreen(
                onBack = { navController.popBackStack() }
            )
        }


        // 41. PDF Metadata Editor Screen
        composable(route = Screen.PdfMetadata.route) {
            PdfMetadataScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 42. PDF Crop Margins Screen
        composable(route = Screen.PdfCropMargins.route) {
            PdfCropMarginsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 43. PDF Redact Screen
        composable(route = Screen.PdfRedact.route) {
            PdfRedactScreen(
                onBack = { navController.popBackStack() }
            )
        }



        // 46. PDF Compare Screen
        composable(route = Screen.PdfCompare.route) {
            PdfCompareScreen(
                onBack = { navController.popBackStack() }
            )
        }



        // 49. PDF Insert Pages Screen
        composable(route = Screen.PdfInsertPages.route) {
            PdfInsertPagesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 50. PDF Replace Pages Screen
        composable(route = Screen.PdfReplacePages.route) {
            PdfReplacePagesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 51. PDF Bookmark Editor Screen
        composable(route = Screen.PdfBookmarks.route) {
            PdfBookmarkScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 52. Password ZIP Extract Screen
        composable(route = Screen.PasswordZipExtract.route) {
            PasswordZipExtractScreen(
                onBack = { navController.popBackStack() }
            )
        }




        // 56. File Encrypt Screen
        composable(route = Screen.FileEncrypt.route) {
            FileEncryptScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 57. File Decrypt Screen
        composable(route = Screen.FileDecrypt.route) {
            FileDecryptScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 60. File Checksum Screen
        composable(route = Screen.FileChecksum.route) {
            FileChecksumScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 61. Text Compare Screen
        composable(route = Screen.TextCompare.route) {
            TextCompareScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 62. Unit Converter Screen
        composable(route = Screen.UnitConverter.route) {
            UnitConverterScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 63. Color Picker Screen
        composable(route = Screen.ColorPicker.route) {
            ColorPickerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 64. Collage Maker Screen
        composable(route = Screen.CollageMaker.route) {
            CollageMakerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 65. Meme Maker Screen
        composable(route = Screen.MemeMaker.route) {
            MemeMakerScreen(
                onBack = { navController.popBackStack() }
            )
        }



        // 71. Advanced Word Count Screen
        composable(route = Screen.AdvancedWordCount.route) {
            AdvancedWordCountScreen(
                onBack = { navController.popBackStack() }
            )
        }



        // 74. Exact Resize Screen
        composable(route = Screen.ExactResize.route) {
            ExactResizeScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 75. Read Aloud Screen
        composable(route = Screen.ReadAloud.route) {
            ReadAloudScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 76. PDF Block Editor Screen (Experimental)
        composable(route = Screen.PdfBlockEditor.route) {
            PdfBlockEditorScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 77. Print & Imposition Studio Screen
        composable(route = Screen.PrintImpositionStudio.route) {
            com.karnadigital.omnisuite.feature.imposition.PrintImpositionStudioScreen(
                onBack = { navController.popBackStack() },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
                }
            )
        }
    }
}
