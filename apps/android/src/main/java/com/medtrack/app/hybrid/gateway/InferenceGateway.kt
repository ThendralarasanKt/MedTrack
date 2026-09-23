package com.medtrack.app.hybrid.gateway

import org.json.JSONObject

data class GatewayJob(
    val jobId: String,
    val status: String,
    val requestId: String,
    val errorCode: String? = null,
    val result: JSONObject? = null
)

data class GatewayCapabilities(
    val commandSchemaVersion: String,
    val enabledRoutes: List<String>,
    val commitEndpoint: String?,
    val accountId: String = "",
    val userId: String = "",
    val authIssuer: String = "",
    val authSubject: String = "",
    val actorPersonId: String? = null,
    val role: String = "OWNER",
    val accessState: String = "",
    val accessLabel: String = ""
)

data class GatewayBootstrap(
    val accountId: String,
    val userId: String,
    val authIssuer: String,
    val authSubject: String,
    val displayName: String,
    val email: String? = null,
    val provisioningState: String,
    val accessState: String,
    val accessLabel: String,
    val profileJson: JSONObject
)

interface InferenceGateway {
    fun capabilities(token: String): GatewayCapabilities
    fun registerDevice(token: String, deviceId: String, replaceExisting: Boolean = false): JSONObject
    fun submitJob(token: String, body: JSONObject, fixture: String? = null): GatewayJob
    fun getJob(token: String, jobId: String): GatewayJob
    fun cancelJob(token: String, jobId: String): GatewayJob
    fun bootstrapProfile(token: String, displayName: String): GatewayBootstrap
    fun getProfile(token: String): JSONObject
    fun patchProfile(token: String, body: JSONObject): JSONObject
    fun getAccess(token: String): JSONObject
    fun searchSpecialties(token: String, query: String): JSONObject
    fun searchHospitals(token: String, query: String): JSONObject
}

class GatewayException(val code: String, message: String) : RuntimeException(message)
