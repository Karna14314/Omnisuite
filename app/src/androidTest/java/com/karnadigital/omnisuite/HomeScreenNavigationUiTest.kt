package com.karnadigital.omnisuite

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.karnadigital.omnisuite.feature.home.HomeScreen
import com.karnadigital.omnisuite.ui.theme.OmniSuiteTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI test to verify bottom navigation and tab selection
 * inside HomeScreen. Runs in connected Android device / emulator.
 */
class HomeScreenNavigationUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testTabNavigationClick() {
        composeTestRule.setContent {
            OmniSuiteTheme {
                HomeScreen(
                    onNavigateToSettings = {},
                    onNavigateToQrGenerator = {},
                    onNavigateToBarcodeScanner = {},
                    onNavigateToImageTools = {},
                    onNavigateToPdfMerge = {},
                    onNavigateToPdfSplit = {},
                    onNavigateToPdfLock = {},
                    onNavigateToDocToPdf = {},
                    onNavigateToPptToPdf = {},
                    onNavigateToScanToPdf = {},
                    onNavigateToPdfToImages = {},
                    onNavigateToOcr = {},
                    onNavigateToSignaturePad = {},
                    onNavigateToWatermark = {},
                    onNavigateToPdfToWord = {},
                    onNavigateToPdfToPpt = {},
                    onNavigateToPdfToExcel = {},
                    onNavigateToPdfFormFiller = {},
                    onNavigateToBatchTools = {},
                    onNavigateToZipMaker = {},
                    onOpenFile = {}
                )
            }
        }

        // Verify the two-tab system structure
        composeTestRule.onNodeWithContentDescription("Dedicated Tools Hub").assertExists().performClick()
        composeTestRule.onNodeWithContentDescription("Home Cockpit").assertExists().performClick()
        // Storage Browser is now nested inside the home view, verify it exists after clicking Home Cockpit
        composeTestRule.onNodeWithContentDescription("Search files...").assertExists()
    }
}
