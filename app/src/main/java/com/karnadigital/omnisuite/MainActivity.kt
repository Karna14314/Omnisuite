package com.karnadigital.omnisuite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import com.karnadigital.omnisuite.ui.navigation.OmniNavGraph
import com.karnadigital.omnisuite.ui.navigation.Screen
import com.karnadigital.omnisuite.ui.theme.OmniSuiteTheme
import com.karnadigital.omnisuite.core.util.ThemePreferences
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var navController: androidx.navigation.NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ThemePreferences.initialize(this)
        val externalUriString = getExternalFileUri(intent)
        
        setContent {
            val themeMode = ThemePreferences.currentThemeState.value
            OmniSuiteTheme(themeMode = themeMode) {
                val controller = rememberNavController()
                navController = controller
                
                // If there's an external file open intent, set it as start destination or navigate immediately!
                val startDestination = if (externalUriString != null) {
                    Screen.ViewerDispatcher.createRoute(externalUriString)
                } else {
                    Screen.MainShell.route
                }
                
                OmniNavGraph(
                    navController = controller,
                    startDestination = startDestination
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        getExternalFileUri(intent)?.let { uri ->
            navController?.navigate(Screen.ViewerDispatcher.createRoute(uri)) {
                // Pop up to the main shell so we don't pile up view dispatchers in the backstack
                popUpTo(Screen.MainShell.route) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    private fun getExternalFileUri(intent: Intent?): String? {
        if (intent == null) return null
        val action = intent.action
        val data = intent.data
        if (action == Intent.ACTION_VIEW && data != null) {
            return data.toString()
        }
        return null
    }
}
