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
import com.karnadigital.omnisuite.feature.pdf_tools.PdfToolsScreen
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
                onNavigateToPdfTools = {
                    navController.navigate(Screen.PdfTools.route)
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

        // 7. PDF Modification Toolkit (Merge, Split, Password Lock)
        composable(route = Screen.PdfTools.route) {
            PdfToolsScreen(
                onNavigateBack = {
                    navController.popBackStack()
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

