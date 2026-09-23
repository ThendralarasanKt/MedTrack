package com.medtrack.app.hybrid

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.medtrack.app.data.care.CareLocalSession
import com.medtrack.app.data.care.command.CareClinicalAssessmentService
import com.medtrack.app.data.care.command.CareCommandService
import com.medtrack.app.data.care.command.CareTherapyCommandService
import com.medtrack.app.data.care.command.CareWorkCommandService
import com.medtrack.app.data.care.command.CareWorkspace
import com.medtrack.app.data.care.command.CareWritePath
import com.medtrack.app.data.care.command.ChangeMedicationOrderRequest
import com.medtrack.app.data.care.command.CreatePatientAndAdmissionRequest
import com.medtrack.app.data.care.command.RecordTransferRequest
import com.medtrack.app.data.care.entity.CareHospitalEntity
import com.medtrack.app.data.care.entity.CarePersonEntity
import com.medtrack.app.data.care.entity.CarePhysicalLocationEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.db.AppDatabase
import com.medtrack.app.hybrid.account.AccountBindingStore
import com.medtrack.app.hybrid.account.AccountSession
import com.medtrack.app.hybrid.contract.ProposalContract
import com.medtrack.app.hybrid.proposal.ProposalCommitService
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProposalCommitServiceTest {
    private lateinit var db: AppDatabase
    private lateinit var writePath: CareWritePath
    private lateinit var service: ProposalCommitService
    private lateinit var workspace: CareWorkspace
    private lateinit var admissionId: String
    private lateinit var locationId: String
    private lateinit var orderId: String

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        writePath = CareWritePath(
            CareCommandService(db, db.careDao()),
            CareClinicalAssessmentService(db, db.careDao()),
            CareTherapyCommandService(db, db.careDao(), db.careTherapyDao()),
            CareWorkCommandService(db, db.careDao(), db.careWorkDao())
        )
        val sessionStore = AccountBindingStore(context).apply { inMemoryOnly = true }
        val session = AccountSession(sessionStore)
        service = ProposalCommitService(db, writePath, db.hybridDao(), session)
        workspace = CareLocalSession.workspace()
        val now = System.currentTimeMillis()
        db.careDao().insertPerson(
            CarePersonEntity(
                id = CareLocalSession.ACTOR_PERSON_ID,
                ownerAccountId = CareLocalSession.OWNER_ACCOUNT_ID,
                displayName = "Dr Ankita",
                identityState = CareEnums.IdentityState.CONFIRMED.name,
                createdAt = now,
                createdBy = CareLocalSession.ACTOR_PERSON_ID
            )
        )
        db.careDao().insertHospital(
            CareHospitalEntity(
                id = CareLocalSession.HOSPITAL_ID,
                ownerAccountId = CareLocalSession.OWNER_ACCOUNT_ID,
                code = "CITY",
                name = "City Hospital",
                timeZoneId = "Asia/Kolkata",
                status = CareEnums.ActiveStatus.ACTIVE.name,
                createdAt = now,
                createdBy = CareLocalSession.ACTOR_PERSON_ID
            )
        )
        locationId = CareIds.newId()
        db.careDao().insertPhysicalLocation(
            CarePhysicalLocationEntity(
                id = locationId,
                ownerAccountId = CareLocalSession.OWNER_ACCOUNT_ID,
                hospitalId = CareLocalSession.HOSPITAL_ID,
                parentLocationId = null,
                kind = CareEnums.LocationKind.BED.name,
                code = "ICU-04",
                label = "ICU Bed 04",
                status = CareEnums.ActiveStatus.ACTIVE.name,
                structureState = CareEnums.StructureState.COMPLETE.name,
                createdAt = now,
                createdBy = CareLocalSession.ACTOR_PERSON_ID
            )
        )
        admissionId = writePath.admissions.createPatientAndAdmission(
            workspace,
            CreatePatientAndAdmissionRequest("Hybrid Patient", CareLocalSession.HOSPITAL_ID)
        ).admissionId
        writePath.admissions.recordTransfer(
            workspace,
            RecordTransferRequest(
                admissionId = admissionId,
                effectiveAt = ClinicalTime.instant(now),
                locationId = locationId
            )
        )
        orderId = writePath.therapy.changeMedicationOrder(
            workspace,
            ChangeMedicationOrderRequest(
                admissionId = admissionId,
                medicationDisplayName = "Ceftriaxone",
                action = CareEnums.MedicationOrderAction.START
            )
        ).orderId
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun downloadedProposalCommitsThroughWritePath() = runBlocking {
        val location = db.careDao().currentLocationAssignment(admissionId)!!
        val order = db.careTherapyDao().getMedicationOrder(orderId)!!
        val bundle = bundle(location.version, order.version)
        val digest = ProposalContract.payloadDigest(bundle)
        val results = service.commit(bundle, digest)
        assertTrue(results.joinToString { it.error ?: "ok" }, results.all { it.error == null })
        assertEquals(CareEnums.MedicationOrderStatus.STOPPED.name, db.careTherapyDao().getMedicationOrder(orderId)!!.status)
    }

    @Test
    fun staleOrderRequiresRenewedReview() = runBlocking {
        val location = db.careDao().currentLocationAssignment(admissionId)!!
        val order = db.careTherapyDao().getMedicationOrder(orderId)!!
        writePath.therapy.changeMedicationOrder(
            workspace,
            ChangeMedicationOrderRequest(
                admissionId = admissionId,
                orderId = orderId,
                medicationId = order.medicationId,
                action = CareEnums.MedicationOrderAction.HOLD
            )
        )
        val bundle = bundle(location.version, order.version)
        service.persistUncommitted(bundle, "req-test", "job-test")
        val digest = ProposalContract.payloadDigest(bundle)
        val results = service.commit(bundle, digest)
        assertTrue(results.any { it.error?.contains("Stale") == true })
        assertEquals("NEEDS_REVIEW", db.hybridDao().getProposal("prop-test")!!.status)
    }

    @Test
    fun failedGroupDoesNotMarkProposalCommitted() = runBlocking {
        val location = db.careDao().currentLocationAssignment(admissionId)!!
        val order = db.careTherapyDao().getMedicationOrder(orderId)!!
        val bundle = bundle(location.version, order.version)
        service.persistUncommitted(bundle, "req-test", "job-test")
        val digest = ProposalContract.payloadDigest(bundle)
        val results = service.commit(bundle, digest)
        assertTrue(results.joinToString { it.error ?: "ok" }, results.all { it.error == null })
        assertEquals("COMMITTED", db.hybridDao().getProposal("prop-test")!!.status)
        val again = service.commit(bundle, digest)
        assertTrue(again.all { it.error == null })
        assertEquals("COMMITTED", db.hybridDao().getProposal("prop-test")!!.status)
    }

    private suspend fun bundle(locationVersion: Int, orderVersion: Int): JSONObject {
        val patient = db.careDao().getEpisode(admissionId)!!
        return JSONObject()
            .put("proposalId", "prop-test")
            .put("jobId", "job-test")
            .put("requestId", "req-test")
            .put("commandSchemaVersion", "care-commands-1")
            .put("contextDigest", "sha256:test")
            .put(
                "identity",
                JSONObject()
                    .put("state", "RESOLVED")
                    .put("patientId", patient.patientId)
                    .put("admissionId", admissionId)
            )
            .put("summary", "Transfer to location and stop the medication order.")
            .put(
                "operations",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("operationId", CareIds.newId())
                            .put("type", "MEDICATION_STOP")
                            .put("atomicGroupId", "g-meds")
                            .put(
                                "target",
                                JSONObject()
                                    .put("kind", "MEDICATION_ORDER")
                                    .put("id", orderId)
                                    .put("expectedVersion", orderVersion)
                            )
                            .put("fields", JSONObject().put("admissionId", admissionId))
                            .put("unresolvedFields", JSONArray())
                    )
            )
    }
}
