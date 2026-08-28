package com.karnadigital.omnisuite.di

import android.content.Context
import com.karnadigital.omnisuite.core.engine.document.OfficeConverter
import com.karnadigital.omnisuite.core.util.FileOutputManager
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CoreEntryPoint {
    fun fileOutputManager(): FileOutputManager
    fun uriCacheUtils(): UriCacheUtils
    fun officeConverter(): OfficeConverter
    fun recentFileRepository(): com.karnadigital.omnisuite.core.repository.RecentFileRepository
}

fun coreEntryPoint(context: Context): CoreEntryPoint =
    EntryPointAccessors.fromApplication(context, CoreEntryPoint::class.java)
