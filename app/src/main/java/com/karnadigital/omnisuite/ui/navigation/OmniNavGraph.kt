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
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
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
                }
            )
        }

        // 4. CameraX Scanner
        composable(route = Screen.BarcodeScanner.route) {
            BarcodeScannerScreen(
                onBack = {
                    navController.popBackStack()
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
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 6. Local Offline Image Utilities
        composable(route = Screen.ImageTools.route) {
            ImageToolsScreen(
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
        composable(route = Screen.PdfLock.route) {
            PdfLockScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenFile = { fileUri ->
                    navController.navigate(Screen.ViewerDispatcher.createRoute(fileUri))
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
                }
            )
        }

        // 9. Digital Signature Stamp Pad
        composable(route = Screen.SignaturePad.route) {
            SignaturePadScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 10. Document Watermarking Panel
        composable(route = Screen.Watermark.route) {
            WatermarkScreen(
                onBack = {
                    navController.popBackStack()
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
    }
}

