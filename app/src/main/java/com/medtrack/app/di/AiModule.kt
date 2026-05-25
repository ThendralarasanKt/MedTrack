package com.medtrack.app.di

import com.medtrack.app.ai.FakeLocalLlmEngine
import com.medtrack.app.ai.LocalLlmEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindLocalLlmEngine(
        fakeLocalLlmEngine: FakeLocalLlmEngine
    ): LocalLlmEngine
}
