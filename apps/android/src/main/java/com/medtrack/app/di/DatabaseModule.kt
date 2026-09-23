package com.medtrack.app.di

import android.content.Context
import com.medtrack.app.data.care.dao.CareDao
import com.medtrack.app.data.care.dao.CareTherapyDao
import com.medtrack.app.data.care.dao.CareWorkDao
import com.medtrack.app.data.db.AppDatabase
import com.medtrack.app.data.db.AppDatabaseFactory
import com.medtrack.app.hybrid.data.HybridDao
import com.medtrack.app.security.DatabasePassphraseProvider
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
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider
    ): AppDatabase = AppDatabaseFactory.create(context, passphraseProvider)

    @Provides
    fun provideCareDao(db: AppDatabase): CareDao = db.careDao()

    @Provides
    fun provideCareTherapyDao(db: AppDatabase): CareTherapyDao = db.careTherapyDao()

    @Provides
    fun provideCareWorkDao(db: AppDatabase): CareWorkDao = db.careWorkDao()

    @Provides
    fun provideHybridDao(db: AppDatabase): HybridDao = db.hybridDao()
}
