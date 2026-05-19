package com.medtrack.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * MedTrackApp: The entry point of the application.
 * 
 * WHY: We need this class to initialize Hilt Dependency Injection. 
 * The @HiltAndroidApp annotation triggers Hilt's code generation.
 */
@HiltAndroidApp
class MedTrackApp : Application()
