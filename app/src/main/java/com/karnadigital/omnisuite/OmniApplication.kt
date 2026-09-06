package com.karnadigital.omnisuite

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OmniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Critical Apache POI JVM XML Input Factory setting override for Android environments
        System.setProperty(
            "org.apache.poi.javax.xml.stream.XMLInputFactory",
            "com.sun.xml.internal.stream.XMLInputFactoryImpl"
        )
        // Initialize Tool Preferences for persistent favorites
        com.karnadigital.omnisuite.core.util.ToolPreferences.init(this)
    }
}
