package com.medtrack.app.hybrid.gateway

import com.medtrack.app.BuildConfig
import com.medtrack.app.data.care.CareLocalSession
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-process gateway. Used by debug builds and unit tests. No provider credentials.
 */
@Singleton
class MockInferenceGateway @Inject constructor() : InferenceGateway {
    private val jobs = linkedMapOf<String, GatewayJob>()
    private val requestIndex = linkedMapOf<String, String>()
    private val devices = linkedMapOf<String, String>()
    private val profiles = linkedMapOf<String, JSONObject>()

    override fun capabilities(token: String): GatewayCapabilities {
        requireToken(token)
        val identity = identityFrom(token)
        val entitled = entitled(identity)
        return GatewayCapabilities(
            commandSchemaVersion = "care-commands-1",
            enabledRoutes = if (entitled) listOf("capture", "reasoning") else emptyList(),
            commitEndpoint = null,
            accountId = identity.accountId,
            userId = identity.userId,
            authIssuer = identity.issuer,
            authSubject = identity.subject,
            actorPersonId = identity.actorPersonId,
            role = "OWNER",
            accessState = if (entitled) "AUTHORIZED" else "PENDING",
            accessLabel = if (entitled) "Pilot access" else "No subscription"
        )
    }

    override fun registerDevice(token: String, deviceId: String, replaceExisting: Boolean): JSONObject {
        requireToken(token)
        val identity = identityFrom(token)
        val previous = devices[identity.accountId]
        if (previous != null && previous != deviceId && !replaceExisting) {
            throw GatewayException("DEVICE_REPLACEMENT_REQUIRED", "Another device is already registered.")
        }
        if (previous != null && previous != deviceId && replaceExisting) {
            devices.remove(identity.accountId)
        }
        devices[identity.accountId] = deviceId
        return JSONObject()
            .put("deviceId", deviceId)
            .put("status", "ACTIVE")
            .put("accountId", identity.accountId)
    }

    override fun submitJob(token: String, body: JSONObject, fixture: String?): GatewayJob {
        requireToken(token)
        val identity = identityFrom(token)
        if (!entitled(identity)) {
            throw GatewayException("UNAUTHORIZED", "Account is not entitled to inference.")
        }
        val requestId = body.getString("requestId")
        val digest = body.optString("inputDigest")
        val existingId = requestIndex[accountKey(token) + requestId]
        if (existingId != null) {
            val existing = jobs.getValue(existingId)
            val priorDigest = existing.result?.optString("inputDigest")
            if (priorDigest != null && priorDigest != digest) {
                throw GatewayException("CONFLICT", "requestId reused with a different input digest")
            }
            return existing
        }
        if (body.optString("commandSchemaVersion") != "care-commands-1") {
            throw GatewayException("UNSUPPORTED_SCHEMA", "commandSchemaVersion is not supported.")
        }
        val jobId = "job-${UUID.randomUUID()}"
        val result = when (fixture) {
            "timeout" -> JSONObject().put(
                "error",
                JSONObject().put("code", "TRANSIENT_PROVIDER").put("message", "Provider timed out")
            )
            "clarification" -> JSONObject()
                .put("kind", "clarification")
                .put("proposal", clarification(jobId, body))
            else -> JSONObject()
                .put("kind", "proposal")
                .put("proposal", successOrHeuristic(jobId, body))
                .put("inputDigest", digest)
        }
        val status = if (fixture == "timeout") "FAILED" else "SUCCEEDED"
        val job = GatewayJob(
            jobId = jobId,
            status = status,
            requestId = requestId,
            errorCode = if (status == "FAILED") "TRANSIENT_PROVIDER" else null,
            result = result
        )
        jobs[jobId] = job
        requestIndex[accountKey(token) + requestId] = jobId
        return job
    }

    override fun getJob(token: String, jobId: String): GatewayJob {
        requireToken(token)
        return jobs[jobId] ?: throw GatewayException("NOT_FOUND", "Job not found.")
    }

    override fun cancelJob(token: String, jobId: String): GatewayJob {
        val existing = getJob(token, jobId)
        val cancelled = existing.copy(status = "CANCELLED", errorCode = "CANCELLED", result = null)
        jobs[jobId] = cancelled
        return cancelled
    }

    private fun successOrHeuristic(jobId: String, body: JSONObject): JSONObject {
        val patientId = body.optString("patientId").ifBlank { null }
        val admissionId = body.optString("admissionId").ifBlank { null }
        val resolved = patientId != null && admissionId != null
        val operations = JSONArray()
        val text = body.optString("text")
        if (resolved && text.contains("loc-")) {
            val locationId = text.split(" ", "(", ")").firstOrNull { it.startsWith("loc-") }
            if (locationId != null) {
                operations.put(
                    JSONObject()
                        .put("operationId", "op-transfer-$jobId")
                        .put("type", "TRANSFER")
                        .put("atomicGroupId", "g-location")
                        .put("dependsOn", JSONArray())
                        .put(
                            "target",
                            JSONObject()
                                .put("kind", "LOCATION")
                                .put("id", locationId)
                                .put("expectedVersion", 1)
                        )
                        .put("fields", JSONObject().put("admissionId", admissionId))
                        .put("unresolvedFields", JSONArray())
                )
            }
        }
        return JSONObject()
            .put("proposalId", "prop-$jobId")
            .put("jobId", jobId)
            .put("requestId", body.optString("requestId"))
            .put("commandSchemaVersion", "care-commands-1")
            .put("contextDigest", body.optString("contextDigest"))
            .put(
                "identity",
                JSONObject()
                    .put("state", if (resolved) "RESOLVED" else "UNRESOLVED")
                    .put("patientId", patientId)
                    .put("admissionId", admissionId)
            )
            .put(
                "summary",
                if (operations.length() == 0) "No mutating operations."
                else "Proposed local clinical commands for review."
            )
            .put("operations", operations)
            .put("provenance", JSONObject().put("routeVersion", "route-v1"))
    }

    private fun clarification(jobId: String, body: JSONObject): JSONObject =
        JSONObject()
            .put("proposalId", "prop-$jobId")
            .put("jobId", jobId)
            .put("requestId", body.optString("requestId"))
            .put("commandSchemaVersion", "care-commands-1")
            .put("contextDigest", body.optString("contextDigest"))
            .put("identity", JSONObject().put("state", "UNRESOLVED"))
            .put("summary", "No mutating operations.")
            .put("clarification", "Which patient should receive this change?")
            .put("operations", JSONArray())
            .put("provenance", JSONObject().put("routeVersion", "route-v1"))

    override fun bootstrapProfile(token: String, displayName: String): GatewayBootstrap {
        requireToken(token)
        val identity = identityFrom(token)
        val profile = profiles.getOrPut(identity.userId) {
            JSONObject()
                .put("profileId", "prof-${identity.userId}")
                .put("userId", identity.userId)
                .put("accountId", identity.accountId)
                .put("displayName", displayName)
                .put("preferredName", JSONObject.NULL)
                .put("professionCode", JSONObject.NULL)
                .put("phone", JSONObject.NULL)
                .put("timeZoneId", "Asia/Kolkata")
                .put("preferredLanguage", JSONObject.NULL)
                .put("onboardingStatus", "NOT_STARTED")
                .put("version", 1)
                .put("specialties", JSONArray())
                .put("affiliations", JSONArray())
        }
        if (profile.optString("displayName").isBlank()) {
            profile.put("displayName", displayName)
        }
        val entitled = entitled(identity)
        return GatewayBootstrap(
            accountId = identity.accountId,
            userId = identity.userId,
            authIssuer = identity.issuer,
            authSubject = identity.subject,
            displayName = profile.optString("displayName"),
            email = null,
            provisioningState = if (entitled) "PRODUCT_ENABLED" else "ACCESS_PENDING",
            accessState = if (entitled) "AUTHORIZED" else "PENDING",
            accessLabel = if (entitled) "Pilot access" else "No subscription",
            profileJson = profile
        )
    }

    override fun getProfile(token: String): JSONObject {
        val boot = bootstrapProfile(token, "Clinician")
        return boot.profileJson
    }

    override fun patchProfile(token: String, body: JSONObject): JSONObject {
        if (body.has("isSubscriber") || body.has("entitlement") || body.has("grant")) {
            throw GatewayException("FORBIDDEN_FIELD", "Cannot write subscription or entitlement fields.")
        }
        val existing = getProfile(token)
        if (body.optInt("expectedVersion") != existing.optInt("version")) {
            throw GatewayException("CONFLICT", "Profile version does not match expectedVersion.")
        }
        val keys = body.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key != "expectedVersion" && key != "operationId") {
                existing.put(key, body.get(key))
            }
        }
        existing.put("version", existing.optInt("version") + 1)
        val identity = identityFrom(token)
        profiles[identity.userId] = existing
        return existing
    }

    override fun getAccess(token: String): JSONObject {
        val boot = bootstrapProfile(token, "Clinician")
        return JSONObject()
            .put("subscription", JSONObject.NULL)
            .put("grants", JSONArray().put(JSONObject().put("kind", "PILOT").put("label", boot.accessLabel)))
            .put(
                "entitlement",
                JSONObject()
                    .put("accessState", boot.accessState)
                    .put("label", boot.accessLabel)
                    .put("features", if (boot.accessState == "AUTHORIZED") JSONArray().put("inference") else JSONArray())
            )
    }

    override fun searchSpecialties(token: String, query: String): JSONObject {
        requireToken(token)
        return JSONObject().put(
            "items",
            JSONArray()
                .put(JSONObject().put("specialtyId", "spec-nephrology").put("displayName", "Nephrology"))
                .put(JSONObject().put("specialtyId", "spec-cardiology").put("displayName", "Cardiology"))
        )
    }

    override fun searchHospitals(token: String, query: String): JSONObject {
        requireToken(token)
        return JSONObject().put(
            "items",
            JSONArray().put(JSONObject().put("hospitalId", "hosp-city").put("name", "City Hospital"))
        )
    }

    private fun entitled(identity: MockIdentity): Boolean =
        identity.accountId == CareLocalSession.OWNER_ACCOUNT_ID ||
            identity.subject == "synthetic-second" ||
            identity.issuer == "mock"

    private fun requireToken(token: String) {
        if (token.isBlank()) throw GatewayException("UNAUTHENTICATED", "Missing token")
    }

    private fun accountKey(token: String): String = identityFrom(token).accountId

    private data class MockIdentity(
        val issuer: String,
        val subject: String,
        val accountId: String,
        val userId: String,
        val actorPersonId: String
    )

    private fun identityFrom(token: String): MockIdentity {
        val claims = decodeClaims(token)
        val issuer = claims.optString("iss")
        val subject = claims.optString("sub")
        val ankitaSubject = BuildConfig.ANKITA_AUTH_SUBJECT.trim()
        val isAnkitaOwner =
            (issuer == "medtrack-test" && subject == "synthetic-ankita") ||
                (ankitaSubject.isNotBlank() && subject == ankitaSubject)
        if (isAnkitaOwner) {
            return MockIdentity(
                issuer = issuer,
                subject = subject,
                accountId = CareLocalSession.OWNER_ACCOUNT_ID,
                userId = "user-ankita",
                actorPersonId = CareLocalSession.ACTOR_PERSON_ID
            )
        }
        val stable = UUID.nameUUIDFromBytes("$issuer:$subject".toByteArray()).toString()
        return MockIdentity(
            issuer = issuer.ifBlank { "mock" },
            subject = subject.ifBlank { "opaque" },
            accountId = stable,
            userId = "user-$stable",
            actorPersonId = stable
        )
    }

    private fun decodeClaims(token: String): JSONObject {
        if (token.startsWith("mt-dev.")) {
            val encoded = token.removePrefix("mt-dev.")
            val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
            val json = String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
            return JSONObject(json)
        }
        val parts = token.split(".")
        if (parts.size == 3) {
            val padded = parts[1] + "=".repeat((4 - parts[1].length % 4) % 4)
            return runCatching {
                JSONObject(String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8))
            }.getOrElse { JSONObject().put("iss", "mock").put("sub", token.take(24)) }
        }
        return JSONObject().put("iss", "mock").put("sub", token.take(24))
    }
}
