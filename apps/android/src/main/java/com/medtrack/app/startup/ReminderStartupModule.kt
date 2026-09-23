package com.medtrack.app.startup

import com.medtrack.app.hybrid.reminder.SchedulingOutboxProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object ReminderStartupModule {
    @Provides
    @IntoSet
    fun provideReminderReconcileHook(processor: SchedulingOutboxProcessor): AppStartupHook =
        AppStartupHook { processor.processPending() }
}
