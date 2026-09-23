package com.medtrack.app.safeguard

import com.medtrack.app.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Debug-only BuildConfig checks for MT-001 Phase 0 safeguards.
 */
class DebugSafeguardRegressionTest {
    @Test
    fun syntheticSeedIsEnabledByDefaultInDebugBuildConfig() {
        assertTrue(
            "Debug builds seed synthetic patients by default. " +
                "Set medtrack.seedSyntheticData=false in local.properties to disable.",
            BuildConfig.SEED_SYNTHETIC_DATA
        )
    }
}
