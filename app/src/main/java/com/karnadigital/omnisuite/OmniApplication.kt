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

        // Harden OOXML (ZIP) parsing against zip bombs. POI ships permissive defaults:
        // MIN_INFLATE_RATIO 0.01 (only trips above 100:1) and maxEntrySize ~4 GB, so a small
        // archive could expand to multiple gigabytes in the heap. Nothing in this repo
        // configured ZipSecureFile, and the app also does its own java.util.zip.ZipFile
        // parsing on XLSX media, so tighten both ratios and the per-entry ceiling.
        org.apache.poi.openxml4j.util.ZipSecureFile.setMinInflateRatio(0.02)
        org.apache.poi.openxml4j.util.ZipSecureFile.setMaxEntrySize(64L * 1024L * 1024L)
        org.apache.poi.openxml4j.util.ZipSecureFile.setMaxTextSize(16L * 1024L * 1024L)
    }
}
