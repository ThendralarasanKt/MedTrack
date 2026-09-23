package com.medtrack.app.startup

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class StartupHooksModule {
    @Multibinds
    abstract fun startupHooks(): Set<AppStartupHook>
}
