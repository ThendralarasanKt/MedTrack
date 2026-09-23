package com.medtrack.app.startup

/**
 * Optional work run after Application onCreate.
 * Debug source sets may contribute hooks such as synthetic seeding.
 */
fun interface AppStartupHook {
    suspend fun onApplicationCreate()
}
