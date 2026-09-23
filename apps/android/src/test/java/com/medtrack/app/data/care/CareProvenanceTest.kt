package com.medtrack.app.data.care

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.medtrack.app.data.care.command.CareCommandService
import com.medtrack.app.data.care.command.CareWorkspace
import com.medtrack.app.data.care.command.CreatePatientAndAdmissionRequest
import com.medtrack.app.data.care.command.OperationPayloadConflictException
import com.medtrack.app.data.care.command.RecordTransferRequest
import com.medtrack.app.data.care.entity.CareHospitalEntity
import com.medtrack.app.data.care.entity.CarePersonEntity
import com.medtrack.app.data.care.entity.CarePhysicalLocationEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CareProvenanceTest {
    private lateinit var db: AppDatabase
    private lateinit var commands: CareCommandService
    private lateinit var workspace: CareWorkspace
    private val ownerId = CareIds.newId()
    private val doctorId = CareIds.newId()
    private val hospitalId = CareIds.newId()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        commands = CareCommandService(db, db.careDao())
        workspace = CareWorkspace(ownerAccountId = ownerId, actorPersonId = doctorId)
        val now = System.currentTimeMillis()
        db.careDao().insertPerson(
            CarePersonEntity(
                id = doctorId,
                ownerAccountId = ownerId,
                displayName = "Dr Ankita",
                identityState = CareEnums.IdentityState.CONFIRMED.name,
                createdAt = now,
                createdBy = doctorId
            )
        )
        db.careDao().insertHospital(
            CareHospitalEntity(
                id = hospitalId,
                ownerAccountId = ownerId,
                code = "SYN",
                name = "Synthetic Hospital",
                timeZoneId = "Asia/Kolkata",
                status = CareEnums.ActiveStatus.ACTIVE.name,
                createdAt = now,
                createdBy = doctorId
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun sameOperationIdRetryReturnsOriginalReceipt() = runBlocking {
        val operationId = CareIds.newId()
        val request = CreatePatientAndAdmissionRequest(
            patientDisplayName = "Idempotent Patient",
            hospitalId = hospitalId,
            operationId = operationId
        )
        val first = commands.createPatientAndAdmission(workspace, request)
        val second = commands.createPatientAndAdmission(workspace, request)

        assertFalse(first.receipt!!.reused)
        assertTrue(second.receipt!!.reused)
        assertEquals(first.patientId, second.patientId)
        assertEquals(first.admissionId, second.admissionId)
        assertEquals(1, db.careDao().eventsForOperation(operationId).size)
        assertEquals(1, db.careDao().auditForOperation(operationId).size)
        assertNotNull(db.careDao().getAppliedOperation(ownerId, operationId))
    }

    @Test
    fun differentPayloadUnderSameOperationIdRejected() = runBlocking {
        val operationId = CareIds.newId()
        commands.createPatientAndAdmission(
            workspace,
            CreatePatientAndAdmissionRequest(
                patientDisplayName = "First Name",
                hospitalId = hospitalId,
                operationId = operationId
            )
        )
        try {
            commands.createPatientAndAdmission(
                workspace,
                CreatePatientAndAdmissionRequest(
                    patientDisplayName = "Different Name",
                    hospitalId = hospitalId,
                    operationId = operationId
                )
            )
            fail("Expected OperationPayloadConflictException")
        } catch (_: OperationPayloadConflictException) {
            // expected
        }
    }

    @Test
    fun failedTransferLeavesNoPartialAssignments() = runBlocking {
        val created = commands.createPatientAndAdmission(
            workspace,
            CreatePatientAndAdmissionRequest(
                patientDisplayName = "Partial Fail Patient",
                hospitalId = hospitalId
            )
        )
        try {
            commands.recordTransfer(
                workspace,
                RecordTransferRequest(
                    admissionId = created.admissionId,
                    effectiveAt = ClinicalTime.instant(1_700_000_000_000L),
                    nursingProfessionalRoleId = CareIds.newId()
                )
            )
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected: nursing change without person or station
        }
        assertEquals(0, db.careDao().currentNursingAssignments(created.admissionId).size)
        assertEquals(null, db.careDao().currentLocationAssignment(created.admissionId))
    }

    @Test
    fun transferBumpsEpisodeVersionWithAudit() = runBlocking {
        val created = commands.createPatientAndAdmission(
            workspace,
            CreatePatientAndAdmissionRequest(
                patientDisplayName = "Versioned Transfer",
                hospitalId = hospitalId
            )
        )
        assertEquals(1, db.careDao().getEpisode(created.admissionId)!!.version)

        val bedId = CareIds.newId()
        val now = System.currentTimeMillis()
        db.careDao().insertPhysicalLocation(
            CarePhysicalLocationEntity(
                id = bedId,
                ownerAccountId = ownerId,
                hospitalId = hospitalId,
                parentLocationId = null,
                kind = CareEnums.LocationKind.BED.name,
                code = "B1",
                label = "Bed 1",
                structureState = CareEnums.StructureState.COMPLETE.name,
                status = CareEnums.ActiveStatus.ACTIVE.name,
                createdAt = now,
                createdBy = doctorId
            )
        )
        val transfer = commands.recordTransfer(
            workspace,
            RecordTransferRequest(
                admissionId = created.admissionId,
                effectiveAt = ClinicalTime.instant(1_700_000_100_000L),
                locationId = bedId
            )
        )
        assertEquals(2, db.careDao().getEpisode(created.admissionId)!!.version)
        val audits = db.careDao().auditForOperation(transfer.receipt.operationId)
        assertEquals(1, audits.size)
        assertEquals(1, audits.single().priorVersion)
        assertEquals(2, audits.single().newVersion)
        assertNotNull(transfer.eventId)
    }
}
