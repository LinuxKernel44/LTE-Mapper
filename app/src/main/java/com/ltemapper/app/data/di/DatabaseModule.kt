package com.ltemapper.app.data.di

import android.content.Context
import androidx.room.Room
import com.ltemapper.app.data.dao.MappingSessionDao
import com.ltemapper.app.data.dao.OfflineRegionDao
import com.ltemapper.app.data.dao.SignalMeasurementDao
import com.ltemapper.app.data.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideMappingSessionDao(db: AppDatabase): MappingSessionDao = db.mappingSessionDao()

    @Provides
    fun provideSignalMeasurementDao(db: AppDatabase): SignalMeasurementDao = db.signalMeasurementDao()

    @Provides
    fun provideOfflineRegionDao(db: AppDatabase): OfflineRegionDao = db.offlineRegionDao()
}
