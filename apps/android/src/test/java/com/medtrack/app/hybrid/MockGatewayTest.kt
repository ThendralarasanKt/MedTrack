package com.medtrack.app.hybrid

import com.medtrack.app.hybrid.gateway.MockInferenceGateway
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MockGatewayTest {
    @Test
    fun capabilitiesHaveNoCommitEndpoint() {
        val gateway = MockInferenceGateway()
        val caps = gateway.capabilities("token")
        assertNull(caps.commitEndpoint)
        assertTrue(caps.enabledRoutes.isNotEmpty())
    }

    @Test
    fun duplicateRequestSameDigestReusesJob() {
        val gateway = MockInferenceGateway()
        val body = JSONObject()
            .put("requestId", "req-1")
            .put("inputDigest", "sha256:same")
            .put("commandSchemaVersion", "care-commands-1")
            .put("contextDigest", "sha256:ctx")
            .put("text", "note")
        val first = gateway.submitJob("token", body)
        val second = gateway.submitJob("token", body)
        assertTrue(first.jobId == second.jobId)
    }
}
