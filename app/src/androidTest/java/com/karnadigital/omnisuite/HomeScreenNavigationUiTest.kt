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
                    onNavigateToPdfTools = {},
                    onNavigateToOcr = {},
                    onNavigateToSignaturePad = {},
                    onNavigateToWatermark = {},
                    onNavigateToBatchTools = {},
                    onOpenFile = {}
                )
            }
        }

        // Verify that all 4 navigation tabs are displayed and interactive
        composeTestRule.onNodeWithContentDescription("Dedicated Tools Hub").assertExists().performClick()
        composeTestRule.onNodeWithContentDescription("Storage Browser").assertExists().performClick()
        composeTestRule.onNodeWithContentDescription("Recent Files History").assertExists().performClick()
        composeTestRule.onNodeWithContentDescription("Home Workspace Dashboard").assertExists().performClick()
    }
}
