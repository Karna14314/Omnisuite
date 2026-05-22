package com.karnadigital.omnisuite.di

import android.content.Context
import androidx.room.Room
import com.karnadigital.omnisuite.core.repository.OmniDatabase
import com.karnadigital.omnisuite.core.repository.RecentFileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module responsible for providing database and DAO dependencies offline.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideOmniDatabase(
        @ApplicationContext context: Context
    ): OmniDatabase {
        return Room.databaseBuilder(
            context,
            OmniDatabase::class.java,
            "omnisuite_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideRecentFileDao(database: OmniDatabase): RecentFileDao {
        return database.recentFileDao()
    }
}
