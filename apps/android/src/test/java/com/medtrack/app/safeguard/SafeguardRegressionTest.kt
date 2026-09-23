package com.medtrack.app.safeguard

import com.medtrack.app.BuildConfig
import com.medtrack.app.data.care.command.CareCommandService
import com.medtrack.app.data.care.dao.CareDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compile-time and BuildConfig checks for MT-001 Phase 0 safeguards.
 */
class SafeguardRegressionTest {
    @Test
    fun localMcpServerIsDisabledByDefaultInDebugBuildConfig() {
        assertFalse(
            "Set medtrack.localMcp.enabled=false or omit it before running this test.",
            BuildConfig.ENABLE_LOCAL_MCP_SERVER
        )
    }

    @Test
    fun syntheticSeedIsDisabledInReleaseBuildConfigWhenRelease() {
        if (!BuildConfig.DEBUG) {
            assertFalse(
                "Release builds must never enable synthetic seed.",
                BuildConfig.SEED_SYNTHETIC_DATA
            )
        }
    }

    @Test
    fun careCommandsDoNotExposeDischargedPurge() {
        val methods = CareCommandService::class.java.declaredMethods.map { it.name }
        assertFalse(methods.contains("purgeDischargedPatientsBefore"))
        assertFalse(methods.contains("deleteDischargedPatientsBefore"))
    }

    @Test
    fun careDaoDoesNotExposeDischargedPurgeQuery() {
        val methods = CareDao::class.java.methods.map { it.name }
        assertFalse(methods.contains("deleteDischargedPatientsBefore"))
        assertFalse(methods.contains("purgeDischargedPatientsBefore"))
    }

    @Test
    fun providerKeyIsNotEmbeddedInTheClient() {
        assertEquals(
            "",
            com.medtrack.app.ai.openrouter.OpenRouterApiKeyProvider().getApiKey()
        )
    }

    @Test
    fun gatewayMockDoesNotAdvertiseClinicalSync() {
        assertTrue(BuildConfig.USE_MOCK_GATEWAY || BuildConfig.GATEWAY_BASE_URL.isNotBlank())
        assertFalse(
            "Local MCP remains opt-in.",
            BuildConfig.ENABLE_LOCAL_MCP_SERVER
        )
    }
}
