package com.medtrack.app.hybrid.request

import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.hybrid.account.AccountSession
import com.medtrack.app.hybrid.contract.ProposalContract
import com.medtrack.app.hybrid.data.HybridDao
import com.medtrack.app.hybrid.data.HybridInferenceRequestEntity
import com.medtrack.app.hybrid.gateway.GatewayException
import com.medtrack.app.hybrid.gateway.InferenceGateway
import com.medtrack.app.hybrid.request.AuthTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

enum class InferenceUiState {
    DRAFT, QUEUED, SUBMITTED, AWAITING_REVIEW, FAILED, OFFLINE
}

data class LocalInferenceCapture(
    val request: HybridInferenceRequestEntity,
    val jobId: String?,
    val proposalJson: JSONObject?,
    val uiState: InferenceUiState,
    val narrative: String
)

@Singleton
class InferenceRequestRepository @Inject constructor(
    private val hybridDao: HybridDao,
    private val accountSession: AccountSession,
    private val gateway: InferenceGateway,
    private val tokenProvider: AuthTokenProvider
) {
    suspend fun submit(
        text: String,
        context: SelectedContext,
        fixture: String? = null
    ): LocalInferenceCapture {
        val bound = accountSession.active()
        val owner = accountSession.ownerAccountId()
        val requestId = CareIds.newId()
        val inputDigest = ProposalContract.sha256(text)
        val now = System.currentTimeMillis()
        val row = HybridInferenceRequestEntity(
            id = CareIds.newId(),
            ownerAccountId = owner,
            accountId = bound?.accountId ?: owner,
            deviceId = accountSession.deviceId(),
            requestId = requestId,
            purpose = context.purpose,
            status = "QUEUED",
            clientVersion = "1.0",
            commandSchemaVersion = ProposalContract.COMMAND_SCHEMA,
            contextDigest = context.digest,
            inputDigest = inputDigest,
            patientId = context.patientId,
            admissionId = context.admissionId,
            draftText = text,
            contextJson = context.manifest.toString(),
            createdAt = now,
            updatedAt = now
        )
        hybridDao.insertRequest(row)
        if (bound?.onlineAuthorized != true) {
            return LocalInferenceCapture(row, null, null, InferenceUiState.OFFLINE, "Queued until the device can reach the gateway.")
        }
        return dispatch(row, fixture)
    }

    suspend fun retryPending() {
        val owner = accountSession.ownerAccountId()
        hybridDao.pendingRequests(owner).forEach { dispatch(it, null) }
    }

    private suspend fun dispatch(
        row: HybridInferenceRequestEntity,
        fixture: String?
    ): LocalInferenceCapture {
        val token = tokenProvider.bearerToken() ?: return LocalInferenceCapture(
            row.copy(status = "QUEUED", errorCode = "UNAUTHENTICATED"),
            null,
            null,
            InferenceUiState.QUEUED,
            "Queued. Sign in to send this capture to the gateway."
        )
        val body = JSONObject()
            .put("requestId", row.requestId)
            .put("deviceId", row.deviceId)
            .put("clientVersion", row.clientVersion)
            .put("commandSchemaVersion", row.commandSchemaVersion)
            .put("purpose", row.purpose)
            .put("patientId", row.patientId)
            .put("admissionId", row.admissionId)
            .put("contextDigest", row.contextDigest)
            .put("inputDigest", row.inputDigest)
            .put("contextManifest", JSONObject(row.contextJson))
            .put("sourceRefs", JSONArray().put(JSONObject().put("kind", "TEXT").put("ref", row.id)))
            .put("text", row.draftText)
        return try {
            val job = submitWithRefresh(token, body, fixture)
            val updated = row.copy(
                status = if (job.status == "SUCCEEDED") "AWAITING_REVIEW" else job.status,
                jobId = job.jobId,
                errorCode = job.errorCode,
                updatedAt = System.currentTimeMillis()
            )
            hybridDao.updateRequest(updated)
            val proposal = job.result?.optJSONObject("proposal")
            val narrative = when {
                job.status == "FAILED" -> job.errorCode ?: "Inference failed."
                proposal?.optString("clarification")?.isNotBlank() == true ->
                    proposal.optString("clarification")
                proposal != null -> proposal.optString("summary")
                else -> "Awaiting review."
            }
            LocalInferenceCapture(
                request = updated,
                jobId = job.jobId,
                proposalJson = proposal,
                uiState = if (job.status == "SUCCEEDED") InferenceUiState.AWAITING_REVIEW else InferenceUiState.FAILED,
                narrative = narrative
            )
        } catch (error: GatewayException) {
            if (error.code == "UNAUTHENTICATED") {
                val queued = row.copy(
                    status = "QUEUED",
                    errorCode = "UNAUTHENTICATED",
                    updatedAt = System.currentTimeMillis()
                )
                hybridDao.updateRequest(queued)
                return LocalInferenceCapture(
                    queued,
                    row.jobId,
                    null,
                    InferenceUiState.QUEUED,
                    "Queued. Sign in to send this capture to the gateway."
                )
            }
            val updated = row.copy(
                status = "FAILED",
                errorCode = error.code,
                updatedAt = System.currentTimeMillis()
            )
            hybridDao.updateRequest(updated)
            LocalInferenceCapture(updated, row.jobId, null, InferenceUiState.FAILED, error.message ?: error.code)
        }
    }

    private suspend fun submitWithRefresh(
        token: String,
        body: JSONObject,
        fixture: String?
    ) = withContext(Dispatchers.IO) {
        try {
            gateway.submitJob(token, body, fixture)
        } catch (error: GatewayException) {
            if (error.code != "UNAUTHENTICATED") throw error
            val refreshed = tokenProvider.bearerToken(forceRefresh = true)
                ?: throw error
            gateway.submitJob(refreshed, body, fixture)
        }
    }
}
