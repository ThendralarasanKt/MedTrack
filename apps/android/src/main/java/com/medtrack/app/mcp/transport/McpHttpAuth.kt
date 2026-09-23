package com.medtrack.app.mcp.transport

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object McpHttpAuth {
    const val AUTHORIZATION_HEADER = "authorization"
    private val allowedHosts = setOf("localhost", "127.0.0.1")

    /**
     * Returns an HTTP status to reject with, or null if the request may proceed.
     * Origin, when present, must be loopback. A Bearer token is always required.
     */
    fun rejectStatus(headers: Map<String, String>, expectedToken: String): Int? {
        val origin = headers["origin"]
        if (origin != null && !isAllowedOrigin(origin)) return 403
        val authorization = headers[AUTHORIZATION_HEADER]
        if (!matchesBearer(authorization, expectedToken)) return 401
        return null
    }

    fun matchesBearer(authorization: String?, expectedToken: String): Boolean {
        if (authorization.isNullOrBlank() || expectedToken.isBlank()) return false
        val prefix = "Bearer "
        if (!authorization.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) {
            return false
        }
        val provided = authorization.substring(prefix.length).trim()
        val providedBytes = provided.toByteArray(StandardCharsets.UTF_8)
        val expectedBytes = expectedToken.toByteArray(StandardCharsets.UTF_8)
        if (providedBytes.size != expectedBytes.size) return false
        return MessageDigest.isEqual(providedBytes, expectedBytes)
    }

    fun isAllowedOrigin(origin: String): Boolean {
        val uri = runCatching { URI(origin.trim()) }.getOrNull() ?: return false
        if (!uri.isAbsolute || uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) return false
        if (uri.userInfo != null) return false
        if (!uri.scheme.equals("http", ignoreCase = true)) return false
        return uri.host.lowercase() in allowedHosts
    }
}
