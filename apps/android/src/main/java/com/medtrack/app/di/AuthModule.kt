package com.medtrack.app.di

import com.medtrack.app.hybrid.account.AuthorizedCallback
import com.medtrack.app.hybrid.profile.ProfileLocalMapper
import com.medtrack.app.hybrid.profile.ProfileSyncCallback
import com.medtrack.app.hybrid.reminder.SchedulingOutboxProcessor
import com.medtrack.app.hybrid.request.AuthTokenProvider
import com.medtrack.app.hybrid.request.AuthTokenStore
import com.medtrack.app.hybrid.request.FirebaseAuthTokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideAuthTokenProvider(store: AuthTokenStore): AuthTokenProvider =
        FirebaseAuthTokenProvider(store)

    @Provides
    @Singleton
    fun provideAuthorizedCallback(processor: SchedulingOutboxProcessor): AuthorizedCallback =
        AuthorizedCallback { processor.processPending() }

    @Provides
    @Singleton
    fun provideProfileSyncCallback(mapper: ProfileLocalMapper): ProfileSyncCallback =
        ProfileSyncCallback { account, json -> mapper.apply(account, json) }
}
