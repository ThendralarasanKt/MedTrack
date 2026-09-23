package com.medtrack.app.hybrid.request

import com.medtrack.app.data.care.dao.CareDao
import com.medtrack.app.data.care.dao.CareTherapyDao
import com.medtrack.app.hybrid.account.AccountSession
import com.medtrack.app.hybrid.contract.ProposalContract
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

data class SelectedContext(
    val purpose: String,
    val patientId: String?,
    val admissionId: String?,
    val manifest: JSONObject,
    val digest: String,
    val snapshotAtMillis: Long
)

@Singleton
class ContextSelector @Inject constructor(
    private val careDao: CareDao,
    private val therapyDao: CareTherapyDao,
    private val accountSession: AccountSession
) {
    suspend fun forAdmission(admissionId: String): SelectedContext {
        val episode = careDao.getEpisode(admissionId) ?: error("Admission not found")
        require(episode.ownerAccountId == accountSession.ownerAccountId())
        val location = careDao.currentLocationAssignment(admissionId)
        val records = JSONArray()
            .put(record("Admission", episode.id, episode.version))
        if (location != null) {
            records.put(record("LocationAssignment", location.id, location.version))
        }
        therapyDao.ordersForAdmission(admissionId)
            .filter { it.status == "ACTIVE" }
            .forEach { order ->
                records.put(record("MedicationOrder", order.id, order.version))
            }
        val snapshotAt = System.currentTimeMillis()
        val manifest = JSONObject()
            .put("snapshotAt", snapshotAt.toString())
            .put("selectionScope", "SINGLE_PATIENT")
            .put("records", records)
            .put("missing", JSONArray())
        return SelectedContext(
            purpose = "SINGLE_PATIENT_CAPTURE",
            patientId = episode.patientId,
            admissionId = episode.id,
            manifest = manifest,
            digest = ProposalContract.sha256(manifest.toString()),
            snapshotAtMillis = snapshotAt
        )
    }

    fun unresolved(): SelectedContext {
        val snapshotAt = System.currentTimeMillis()
        val manifest = JSONObject()
            .put("snapshotAt", snapshotAt.toString())
            .put("selectionScope", "UNRESOLVED_IDENTITY")
            .put("records", JSONArray())
            .put("missing", JSONArray().put("patientId").put("admissionId"))
        return SelectedContext(
            purpose = "GLOBAL_NEW_PATIENT",
            patientId = null,
            admissionId = null,
            manifest = manifest,
            digest = ProposalContract.sha256(manifest.toString()),
            snapshotAtMillis = snapshotAt
        )
    }

    private fun record(type: String, id: String, version: Int) = JSONObject()
        .put("type", type)
        .put("id", id)
        .put("version", version)
}
