package com.medtrack.app.hybrid.profile

import com.medtrack.app.hybrid.account.AccountBindingStore
import com.medtrack.app.hybrid.gateway.GatewayException
import com.medtrack.app.hybrid.gateway.InferenceGateway
import com.medtrack.app.hybrid.request.AuthTokenProvider
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

private val FORBIDDEN_PROFILE_FIELDS = setOf(
    "isSubscriber",
    "subscription",
    "subscriptionStatus",
    "grant",
    "grants",
    "entitlement",
    "accessState",
    "verificationStatus",
    "accountId",
    "userId",
    "profileId",
    "providerCustomerRef",
    "providerSubscriptionRef"
)

data class ProfileDraft(
    val displayName: String = "",
    val preferredName: String = "",
    val professionCode: String = "",
    val phone: String = "",
    val timeZoneId: String = "Asia/Kolkata",
    val preferredLanguage: String = "",
    val specialties: List<SpecialtyDraft> = emptyList(),
    val affiliations: List<AffiliationDraft> = emptyList()
)

data class SpecialtyDraft(
    val specialtyId: String? = null,
    val reportedSpecialtyText: String? = null,
    val isPrimary: Boolean = false,
    val label: String = ""
)

data class AffiliationDraft(
    val hospitalId: String? = null,
    val reportedHospitalName: String? = null,
    val reportedCity: String? = null,
    val departmentName: String = "",
    val jobTitle: String = "",
    val startsOn: String = "",
    val endsOn: String = "",
    val isPrimary: Boolean = false,
    val label: String = ""
)

data class CatalogueHit(
    val id: String,
    val label: String,
    val detail: String = ""
)

data class ProfileView(
    val userId: String = "",
    val accountId: String = "",
    val email: String? = null,
    val version: Int = 0,
    val onboardingStatus: String = "NOT_STARTED",
    val draft: ProfileDraft = ProfileDraft(),
    val accessState: String = "",
    val accessLabel: String = "No subscription",
    val subscriptionLabel: String? = null,
    val grantLabels: List<String> = emptyList(),
    val features: List<String> = emptyList(),
    val fetchedAt: Long = 0L,
    val stale: Boolean = false,
    val syncStatus: String = "Not loaded",
    val error: String? = null,
    val conflict: Boolean = false
)

@Singleton
class ProfileRepository @Inject constructor(
    private val gateway: InferenceGateway,
    private val tokenProvider: AuthTokenProvider,
    private val cache: ProfileCache,
    private val bindingStore: AccountBindingStore,
    private val mapper: ProfileLocalMapper
) {
    fun cachedView(): ProfileView? {
        val userId = bindingStore.active()?.userId
        val cached = (userId?.let { cache.snapshot(it) } ?: cache.latest()) ?: return null
        return toView(cached, pending = cache.outboxFor(cached.userId).any { it.state != "SAVED" })
    }

    suspend fun refresh(): ProfileView {
        val token = tokenProvider.bearerToken()
            ?: return cachedOrError("Sign in to refresh your profile.")
        return try {
            val profile = gateway.getProfile(token)
            val access = runCatching { gateway.getAccess(token) }.getOrNull()
            val identity = bindingStore.active()
            val userId = identity?.userId ?: profile.optString("userId")
            cache.saveConfirmed(
                accountId = profile.optString("accountId"),
                userId = userId,
                email = cachedEmail(userId),
                profileJson = profile,
                accessJson = access
            )
            identity?.let { mapper.apply(it, profile) }
            flushOutbox(token, userId)
            val cached = cache.snapshot(userId)!!
            toView(cached, pending = cache.outboxFor(userId).any { pending(it) })
        } catch (error: GatewayException) {
            val cached = cachedView()?.copy(
                stale = true,
                error = error.message,
                syncStatus = "Offline — showing last saved profile."
            )
            cached ?: ProfileView(error = error.message, syncStatus = "Could not load profile.")
        }
    }

    suspend fun save(draft: ProfileDraft): ProfileView {
        val forbidden = detectForbidden(draft)
        if (forbidden != null) {
            return (cachedView() ?: ProfileView()).copy(error = forbidden)
        }
        val current = cachedView() ?: refresh()
        if (current.userId.isBlank()) {
            return current.copy(error = "Profile is not available yet.")
        }
        val operationId = UUID.randomUUID().toString()
        val item = ProfileOutboxItem(
            operationId = operationId,
            accountId = current.accountId,
            userId = current.userId,
            baseVersion = current.version,
            patchJson = draftToPatch(draft, current.version, operationId),
            queuedAt = System.currentTimeMillis(),
            state = "QUEUED",
            lastErrorCode = null
        )
        cache.enqueue(item)
        val token = tokenProvider.bearerToken()
        if (token == null) {
            cache.markStale(current.userId)
            return (cachedView() ?: current).copy(
                draft = draft,
                stale = true,
                syncStatus = "Saved on this device; waiting to sync.",
                error = null
            )
        }
        flushOutbox(token, current.userId)
        return cachedView() ?: current
    }

    suspend fun searchSpecialties(query: String): List<CatalogueHit> {
        val token = tokenProvider.bearerToken() ?: return emptyList()
        return parseCatalogue(gateway.searchSpecialties(token, query), "specialtyId", "displayName")
    }

    suspend fun searchHospitals(query: String): List<CatalogueHit> {
        val token = tokenProvider.bearerToken() ?: return emptyList()
        return parseCatalogue(gateway.searchHospitals(token, query), "hospitalId", "name") { item ->
            listOf(item.optString("city"), item.optString("stateOrRegion")).filter { it.isNotBlank() }.joinToString(", ")
        }
    }

    private suspend fun flushOutbox(token: String, userId: String) {
        val bound = bindingStore.active()
        cache.outboxFor(userId).filter { pending(it) }.forEach { item ->
            if (bound != null && bound.userId != item.userId) return
            cache.updateOutbox(item.operationId, "SENDING")
            try {
                val saved = gateway.patchProfile(token, item.patchJson)
                cache.saveConfirmed(
                    accountId = saved.optString("accountId", item.accountId),
                    userId = userId,
                    email = cachedEmail(userId),
                    profileJson = saved,
                    accessJson = cache.snapshot(userId)?.accessJson
                )
                bound?.let { mapper.apply(it, saved) }
                cache.removeOutbox(item.operationId)
            } catch (error: GatewayException) {
                val state = if (error.code == "CONFLICT") "CONFLICT" else "FAILED"
                cache.updateOutbox(item.operationId, state, error.code)
                cache.markStale(userId)
            }
        }
    }

    private fun pending(item: ProfileOutboxItem) =
        item.state in setOf("QUEUED", "SENDING", "FAILED")

    private fun draftToPatch(draft: ProfileDraft, expectedVersion: Int, operationId: String): JSONObject {
        val specialties = JSONArray()
        draft.specialties.forEach { row ->
            specialties.put(
                JSONObject()
                    .put("specialtyId", row.specialtyId ?: JSONObject.NULL)
                    .put("reportedSpecialtyText", row.reportedSpecialtyText ?: JSONObject.NULL)
                    .put("isPrimary", row.isPrimary)
            )
        }
        val affiliations = JSONArray()
        draft.affiliations.forEach { row ->
            affiliations.put(
                JSONObject()
                    .put("hospitalId", row.hospitalId ?: JSONObject.NULL)
                    .put("reportedHospitalName", row.reportedHospitalName ?: JSONObject.NULL)
                    .put("reportedCity", row.reportedCity ?: JSONObject.NULL)
                    .put("departmentName", row.departmentName.ifBlank { JSONObject.NULL })
                    .put("jobTitle", row.jobTitle.ifBlank { JSONObject.NULL })
                    .put("startsOn", row.startsOn.ifBlank { JSONObject.NULL })
                    .put("endsOn", row.endsOn.ifBlank { JSONObject.NULL })
                    .put("isPrimary", row.isPrimary)
            )
        }
        return JSONObject()
            .put("expectedVersion", expectedVersion)
            .put("operationId", operationId)
            .put("displayName", draft.displayName)
            .put("preferredName", draft.preferredName.ifBlank { JSONObject.NULL })
            .put("professionCode", draft.professionCode.ifBlank { JSONObject.NULL })
            .put("phone", draft.phone.ifBlank { JSONObject.NULL })
            .put("timeZoneId", draft.timeZoneId.ifBlank { JSONObject.NULL })
            .put("preferredLanguage", draft.preferredLanguage.ifBlank { JSONObject.NULL })
            .put("specialties", specialties)
            .put("affiliations", affiliations)
    }

    private fun detectForbidden(draft: ProfileDraft): String? {
        val keys = draftToPatch(draft, 0, "inspect").keys()
        while (keys.hasNext()) {
            if (keys.next() in FORBIDDEN_PROFILE_FIELDS) {
                return "Cannot write subscription or entitlement fields."
            }
        }
        return null
    }

    private fun toView(cached: CachedProfile, pending: Boolean): ProfileView {
        val profile = cached.profileJson
        val access = cached.accessJson
        val entitlement = access?.optJSONObject("entitlement")
        val subscription = access?.optJSONObject("subscription")
        val grants = access?.optJSONArray("grants") ?: JSONArray()
        val grantLabels = buildList {
            for (i in 0 until grants.length()) {
                val row = grants.optJSONObject(i) ?: continue
                add(row.optString("label").ifBlank { row.optString("kind") })
            }
        }
        val features = entitlement?.optJSONArray("features") ?: JSONArray()
        val outbox = cache.outboxFor(cached.userId)
        val conflict = outbox.any { it.state == "CONFLICT" }
        val failed = outbox.firstOrNull { it.state == "FAILED" }
        val syncStatus = when {
            conflict -> "Server profile changed. Review the fields below; nothing was overwritten."
            pending -> "Saved on this device; waiting to sync."
            cached.stale -> "Showing last known profile."
            else -> "Synced"
        }
        return ProfileView(
            userId = cached.userId,
            accountId = cached.accountId,
            email = cached.email,
            version = profile.optInt("version"),
            onboardingStatus = profile.optString("onboardingStatus"),
            draft = parseDraft(profile),
            accessState = entitlement?.optString("accessState").orEmpty(),
            accessLabel = entitlement?.optString("label").orEmpty().ifBlank { "No subscription" },
            subscriptionLabel = subscription?.optString("planLabel")?.ifBlank { null },
            grantLabels = grantLabels,
            features = buildList { for (i in 0 until features.length()) add(features.getString(i)) },
            fetchedAt = cached.fetchedAt,
            stale = cached.stale || pending,
            syncStatus = syncStatus,
            error = failed?.lastErrorCode,
            conflict = conflict
        )
    }

    private fun parseDraft(profile: JSONObject): ProfileDraft {
        val specialties = profile.optJSONArray("specialties") ?: JSONArray()
        val affiliations = profile.optJSONArray("affiliations") ?: JSONArray()
        return ProfileDraft(
            displayName = profile.optString("displayName"),
            preferredName = profile.optString("preferredName").takeUnless { it == "null" }.orEmpty(),
            professionCode = profile.optString("professionCode").takeUnless { it == "null" }.orEmpty(),
            phone = profile.optString("phone").takeUnless { it == "null" }.orEmpty(),
            timeZoneId = profile.optString("timeZoneId").ifBlank { "Asia/Kolkata" },
            preferredLanguage = profile.optString("preferredLanguage").takeUnless { it == "null" }.orEmpty(),
            specialties = buildList {
                for (i in 0 until specialties.length()) {
                    val row = specialties.optJSONObject(i) ?: continue
                    val reported = row.optString("reportedSpecialtyText").takeUnless { it.isBlank() || it == "null" }
                    val id = row.optString("specialtyId").takeUnless { it.isBlank() || it == "null" }
                    add(
                        SpecialtyDraft(
                            specialtyId = id,
                            reportedSpecialtyText = reported,
                            isPrimary = row.optBoolean("isPrimary"),
                            label = reported ?: id.orEmpty()
                        )
                    )
                }
            },
            affiliations = buildList {
                for (i in 0 until affiliations.length()) {
                    val row = affiliations.optJSONObject(i) ?: continue
                    val reported = row.optString("reportedHospitalName").takeUnless { it.isBlank() || it == "null" }
                    val id = row.optString("hospitalId").takeUnless { it.isBlank() || it == "null" }
                    add(
                        AffiliationDraft(
                            hospitalId = id,
                            reportedHospitalName = reported,
                            reportedCity = row.optString("reportedCity").takeUnless { it.isBlank() || it == "null" },
                            departmentName = row.optString("departmentName").takeUnless { it == "null" }.orEmpty(),
                            jobTitle = row.optString("jobTitle").takeUnless { it == "null" }.orEmpty(),
                            startsOn = row.optString("startsOn").takeUnless { it == "null" }.orEmpty(),
                            endsOn = row.optString("endsOn").takeUnless { it == "null" }.orEmpty(),
                            isPrimary = row.optBoolean("isPrimary"),
                            label = reported ?: id.orEmpty()
                        )
                    )
                }
            }
        )
    }

    private fun parseCatalogue(
        json: JSONObject,
        idKey: String,
        nameKey: String,
        detail: (JSONObject) -> String = { "" }
    ): List<CatalogueHit> {
        val items = json.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (i in 0 until items.length()) {
                val row = items.optJSONObject(i) ?: continue
                add(CatalogueHit(id = row.optString(idKey), label = row.optString(nameKey), detail = detail(row)))
            }
        }
    }

    private fun cachedEmail(userId: String): String? = cache.snapshot(userId)?.email

    private fun cachedOrError(message: String): ProfileView =
        cachedView()?.copy(error = message, stale = true)
            ?: ProfileView(error = message, syncStatus = message)
}
