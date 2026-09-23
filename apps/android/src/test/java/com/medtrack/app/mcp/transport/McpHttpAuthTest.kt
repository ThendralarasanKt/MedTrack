package com.medtrack.app.mcp.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpHttpAuthTest {
    @Test
    fun missingAuthorizationIsUnauthorizedEvenWithoutOrigin() {
        val status = McpHttpAuth.rejectStatus(
            headers = emptyMap(),
            expectedToken = "secret-token"
        )
        assertEquals(401, status)
    }

    @Test
    fun disallowedOriginIsForbiddenBeforeTokenCheck() {
        val status = McpHttpAuth.rejectStatus(
            headers = mapOf(
                "origin" to "https://evil.example",
                "authorization" to "Bearer secret-token"
            ),
            expectedToken = "secret-token"
        )
        assertEquals(403, status)
    }

    @Test
    fun validBearerOnLoopbackIsAllowed() {
        val status = McpHttpAuth.rejectStatus(
            headers = mapOf(
                "origin" to "http://127.0.0.1:1234",
                "authorization" to "Bearer secret-token"
            ),
            expectedToken = "secret-token"
        )
        assertNull(status)
    }

    @Test
    fun lookalikeLocalhostOriginIsRejected() {
        assertEquals(
            403,
            McpHttpAuth.rejectStatus(
                headers = mapOf(
                    "origin" to "http://localhost.evil.example",
                    "authorization" to "Bearer secret-token"
                ),
                expectedToken = "secret-token"
            )
        )
        assertEquals(
            403,
            McpHttpAuth.rejectStatus(
                headers = mapOf(
                    "origin" to "http://127.0.0.1.attacker.test",
                    "authorization" to "Bearer secret-token"
                ),
                expectedToken = "secret-token"
            )
        )
        assertFalse(McpHttpAuth.isAllowedOrigin("http://localhost.evil.example"))
        assertFalse(McpHttpAuth.isAllowedOrigin("https://127.0.0.1"))
        assertTrue(McpHttpAuth.isAllowedOrigin("http://127.0.0.1:1234"))
        assertTrue(McpHttpAuth.isAllowedOrigin("http://localhost:8765"))
    }

    @Test
    fun bearerMatchIsConstantTimeEquality() {
        assertTrue(McpHttpAuth.matchesBearer("Bearer secret-token", "secret-token"))
        assertTrue(McpHttpAuth.matchesBearer("bearer secret-token", "secret-token"))
        assertFalse(McpHttpAuth.matchesBearer("Bearer other", "secret-token"))
        assertFalse(McpHttpAuth.matchesBearer(null, "secret-token"))
        assertFalse(McpHttpAuth.matchesBearer("Token secret-token", "secret-token"))
    }
}
