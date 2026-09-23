package com.medtrack.app.debug

import com.medtrack.app.BuildConfig
import com.medtrack.app.startup.AppStartupHook
import com.medtrack.app.testdata.SyntheticDataSeeder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object DebugSeedModule {
    @Provides
    @IntoSet
    fun provideSyntheticSeedHook(seeder: SyntheticDataSeeder): AppStartupHook =
        AppStartupHook {
            if (BuildConfig.SEED_SYNTHETIC_DATA) {
                seeder.seedIfEmpty()
            }
        }
}
