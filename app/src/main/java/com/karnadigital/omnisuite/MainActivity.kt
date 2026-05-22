package com.karnadigital.omnisuite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.karnadigital.omnisuite.ui.navigation.OmniNavGraph
import com.karnadigital.omnisuite.ui.theme.OmniSuiteTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniSuiteTheme {
                val navController = rememberNavController()
                OmniNavGraph(navController = navController)
            }
        }
    }
}
