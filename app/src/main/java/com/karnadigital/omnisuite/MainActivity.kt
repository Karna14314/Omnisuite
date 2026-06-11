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
        val externalUriString = if (savedInstanceState == null) getExternalFileUri(intent) else null
        
        setContent {
            val themeMode = ThemePreferences.currentThemeState.value
            OmniSuiteTheme(themeMode = themeMode) {
                val controller = rememberNavController()
                navController = controller
                
                OmniNavGraph(
                    navController = controller,
                    startDestination = Screen.MainShell.route
                )
                
                androidx.compose.runtime.LaunchedEffect(externalUriString) {
                    if (externalUriString != null) {
                        controller.navigate(Screen.ViewerDispatcher.createRoute(externalUriString))
                    }
                }
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
        
        if ((action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND) && data != null) {
            return data.toString()
        }
        
        val clipData = intent.clipData
        if (clipData != null && clipData.itemCount > 0) {
            val uri = clipData.getItemAt(0).uri
            if (uri != null) {
                return uri.toString()
            }
        }
        
        if (action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                return uri.toString()
            }
        }
        
        return null
    }
}
