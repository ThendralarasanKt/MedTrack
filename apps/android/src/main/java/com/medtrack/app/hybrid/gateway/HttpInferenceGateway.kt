package com.medtrack.app.hybrid.gateway

import com.medtrack.app.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class HttpInferenceGateway @Inject constructor() : InferenceGateway {
    override fun capabilities(token: String): GatewayCapabilities {
        val json = request("GET", "/v1/capabilities", token, null)
        val routes = json.optJSONArray("enabledRoutes") ?: JSONArray()
        val enabled = buildList {
            for (i in 0 until routes.length()) add(routes.getString(i))
        }
        val commit = json.opt("commitEndpoint")
        return GatewayCapabilities(
            commandSchemaVersion = json.optString("commandSchemaVersion"),
            enabledRoutes = enabled,
            commitEndpoint = if (commit == null || commit == JSONObject.NULL) null else commit.toString(),
            accountId = json.optString("accountId"),
            userId = json.optString("userId"),
            authIssuer = json.optString("authIssuer"),
            authSubject = json.optString("authSubject"),
            actorPersonId = json.optString("actorPersonId").ifBlank { null },
            role = json.optString("role", "OWNER"),
            accessState = json.optJSONObject("access")?.optJSONObject("entitlement")?.optString("accessState").orEmpty(),
            accessLabel = json.optJSONObject("access")?.optJSONObject("entitlement")?.optString("label").orEmpty()
        )
    }

    override fun bootstrapProfile(token: String, displayName: String): GatewayBootstrap {
        val json = request("POST", "/v1/me/bootstrap", token, JSONObject().put("displayName", displayName))
        val profile = json.getJSONObject("profile")
        val identity = json.getJSONObject("identity")
        val entitlement = json.optJSONObject("access")?.optJSONObject("entitlement")
        val email = identity.optString("email")
        return GatewayBootstrap(
            accountId = profile.optString("accountId"),
            userId = identity.optString("userId"),
            authIssuer = identity.optString("authIssuer"),
            authSubject = identity.optString("authSubject"),
            displayName = profile.optString("displayName"),
            email = email.ifBlank { null },
            provisioningState = json.optString("provisioningState"),
            accessState = entitlement?.optString("accessState").orEmpty(),
            accessLabel = entitlement?.optString("label").orEmpty(),
            profileJson = profile
        )
    }

    override fun getProfile(token: String): JSONObject = request("GET", "/v1/me/profile", token, null)

    override fun patchProfile(token: String, body: JSONObject): JSONObject =
        request("POST", "/v1/me/profile", token, body)

    override fun getAccess(token: String): JSONObject = request("GET", "/v1/me/access", token, null)

    override fun searchSpecialties(token: String, query: String): JSONObject =
        request("GET", "/v1/catalogues/specialties?q=${java.net.URLEncoder.encode(query, "UTF-8")}", token, null)

    override fun searchHospitals(token: String, query: String): JSONObject =
        request("GET", "/v1/catalogues/hospitals?q=${java.net.URLEncoder.encode(query, "UTF-8")}", token, null)

    override fun registerDevice(token: String, deviceId: String, replaceExisting: Boolean): JSONObject {
        return request(
            "POST",
            "/v1/devices",
            token,
            JSONObject().put("deviceId", deviceId).put("replaceExisting", replaceExisting)
        )
    }

    override fun submitJob(token: String, body: JSONObject, fixture: String?): GatewayJob {
        val extra = fixture?.let { mapOf("X-MedTrack-Fixture" to it) } ?: emptyMap()
        val json = request("POST", "/v1/inference-jobs", token, body, extra)
        return parseJob(json)
    }

    override fun getJob(token: String, jobId: String): GatewayJob {
        return parseJob(request("GET", "/v1/inference-jobs/$jobId", token, null))
    }

    override fun cancelJob(token: String, jobId: String): GatewayJob {
        return parseJob(request("POST", "/v1/inference-jobs/$jobId/cancel", token, JSONObject()))
    }

    private fun parseJob(json: JSONObject): GatewayJob {
        val error = json.optJSONObject("error")
        if (error != null && !json.has("jobId")) {
            throw GatewayException(error.optString("code"), error.optString("message"))
        }
        return GatewayJob(
            jobId = json.getString("jobId"),
            status = json.getString("status"),
            requestId = json.optString("requestId"),
            errorCode = json.optString("errorCode").ifBlank { null },
            result = json.optJSONObject("result")
        )
    }

    private fun request(
        method: String,
        path: String,
        token: String,
        body: JSONObject?,
        extraHeaders: Map<String, String> = emptyMap()
    ): JSONObject {
        val base = BuildConfig.GATEWAY_BASE_URL.trim().trimEnd('/')
        if (base.isBlank()) {
            throw GatewayException("UNAVAILABLE", "Gateway URL is not configured.")
        }
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
            }
        }
        return runCatching {
            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
            val json = JSONObject(text.ifBlank { "{}" })
            if (connection.responseCode !in 200..299) {
                val err = json.optJSONObject("error") ?: JSONObject()
                throw GatewayException(
                    err.optString("code", "UNAVAILABLE"),
                    err.optString("message", "Gateway request failed (${connection.responseCode})")
                )
            }
            json
        }.getOrElse { error ->
            if (error is GatewayException) throw error
            throw GatewayException("TRANSIENT_PROVIDER", error.message ?: "Gateway request failed")
        }
    }
}
