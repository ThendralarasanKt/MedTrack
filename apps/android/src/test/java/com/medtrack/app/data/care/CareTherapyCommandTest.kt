package com.medtrack.app.data.care

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.medtrack.app.data.care.command.AttachAndMatchReportRequest
import com.medtrack.app.data.care.command.CareCommandService
import com.medtrack.app.data.care.command.CareTherapyCommandService
import com.medtrack.app.data.care.command.CareWorkspace
import com.medtrack.app.data.care.command.ChangeMedicationOrderRequest
import com.medtrack.app.data.care.command.CreatePatientAndAdmissionRequest
import com.medtrack.app.data.care.command.RecordAdministrationRequest
import com.medtrack.app.data.care.command.RecordReportReviewRequest
import com.medtrack.app.data.care.entity.CareHospitalEntity
import com.medtrack.app.data.care.entity.CarePersonEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.care.model.Regimen
import com.medtrack.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CareTherapyCommandTest {
    private lateinit var db: AppDatabase
    private lateinit var commands: CareCommandService
    private lateinit var therapy: CareTherapyCommandService
    private lateinit var workspace: CareWorkspace
    private val ownerId = CareIds.newId()
    private val doctorId = CareIds.newId()
    private val hospitalId = CareIds.newId()
    private lateinit var admissionId: String

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        commands = CareCommandService(db, db.careDao())
        therapy = CareTherapyCommandService(db, db.careDao(), db.careTherapyDao())
        workspace = CareWorkspace(ownerId, doctorId)
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
        admissionId = commands.createPatientAndAdmission(
            workspace,
            CreatePatientAndAdmissionRequest("Therapy Patient", hospitalId)
        ).admissionId
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun changeOrderAndHoldThenAdminister() = runBlocking {
        val started = therapy.changeMedicationOrder(
            workspace,
            ChangeMedicationOrderRequest(
                admissionId = admissionId,
                medicationDisplayName = "Ceftriaxone",
                action = CareEnums.MedicationOrderAction.START,
                doseText = "1 g IV",
                schedule = Regimen.textOnly("once daily"),
                orderedAt = ClinicalTime.instant(1_700_100_000_000L)
            )
        )
        therapy.changeMedicationOrder(
            workspace,
            ChangeMedicationOrderRequest(
                admissionId = admissionId,
                medicationId = started.medicationId,
                orderId = started.orderId,
                action = CareEnums.MedicationOrderAction.HOLD,
                reason = "Await culture",
                orderedAt = ClinicalTime.instant(1_700_100_100_000L)
            )
        )
        val order = db.careTherapyDao().getMedicationOrder(started.orderId)!!
        assertEquals(CareEnums.MedicationOrderStatus.HELD.name, order.status)
        assertEquals(2, db.careTherapyDao().orderEvents(started.orderId).size)

        therapy.changeMedicationOrder(
            workspace,
            ChangeMedicationOrderRequest(
                admissionId = admissionId,
                medicationId = started.medicationId,
                orderId = started.orderId,
                action = CareEnums.MedicationOrderAction.RESUME,
                orderedAt = ClinicalTime.instant(1_700_100_200_000L)
            )
        )
        val given = therapy.recordAdministration(
            workspace,
            RecordAdministrationRequest(
                admissionId = admissionId,
                medicationId = started.medicationId,
                orderId = started.orderId,
                outcome = CareEnums.AdministrationOutcome.GIVEN,
                administeredAt = ClinicalTime.instant(1_700_100_300_000L),
                performerId = doctorId
            )
        )
        assertEquals(1, db.careTherapyDao().administrationsForOrder(started.orderId).size)
        assertTrue(given.administrationId.isNotBlank())
    }

    @Test
    fun omittedAdministrationRequiresReason() = runBlocking {
        val started = therapy.changeMedicationOrder(
            workspace,
            ChangeMedicationOrderRequest(
                admissionId = admissionId,
                medicationDisplayName = "Metformin",
                orderedAt = ClinicalTime.instant(1_700_200_000_000L)
            )
        )
        try {
            therapy.recordAdministration(
                workspace,
                RecordAdministrationRequest(
                    admissionId = admissionId,
                    medicationId = started.medicationId,
                    orderId = started.orderId,
                    outcome = CareEnums.AdministrationOutcome.OMITTED,
                    administeredAt = ClinicalTime.instant(1_700_200_100_000L)
                )
            )
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        therapy.recordAdministration(
            workspace,
            RecordAdministrationRequest(
                admissionId = admissionId,
                medicationId = started.medicationId,
                orderId = started.orderId,
                outcome = CareEnums.AdministrationOutcome.OMITTED,
                administeredAt = ClinicalTime.instant(1_700_200_100_000L),
                reasonUnknown = true
            )
        )
        assertEquals(
            "UNKNOWN",
            db.careTherapyDao().administrationsForOrder(started.orderId).single().reason
        )
    }

    @Test
    fun attachReportAndReview() = runBlocking {
        val attached = therapy.attachAndMatchReport(
            workspace,
            AttachAndMatchReportRequest(
                admissionId = admissionId,
                fileName = "cbc.pdf",
                filePath = "/tmp/cbc.pdf",
                fileType = "PDF",
                reportedAt = ClinicalTime.instant(1_700_300_000_000L),
                narrative = "WBC elevated"
            )
        )
        assertEquals(1, db.careTherapyDao().documentsForReport(attached.reportId).size)
        therapy.recordReportReview(
            workspace,
            RecordReportReviewRequest(
                reportId = attached.reportId,
                outcome = CareEnums.ReportReviewOutcome.NO_CHANGE,
                note = "Noted"
            )
        )
        assertEquals(1, db.careTherapyDao().reviewsForReport(attached.reportId).size)

        try {
            therapy.attachAndMatchReport(
                workspace,
                AttachAndMatchReportRequest(
                    admissionId = admissionId,
                    fileName = "cbc-amended.pdf",
                    filePath = "/tmp/cbc-amended.pdf",
                    fileType = "PDF",
                    reportedAt = ClinicalTime.instant(1_700_300_100_000L),
                    status = CareEnums.DiagnosticReportStatus.AMENDED
                )
            )
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        therapy.attachAndMatchReport(
            workspace,
            AttachAndMatchReportRequest(
                admissionId = admissionId,
                fileName = "cbc-amended.pdf",
                filePath = "/tmp/cbc-amended.pdf",
                fileType = "PDF",
                reportedAt = ClinicalTime.instant(1_700_300_100_000L),
                status = CareEnums.DiagnosticReportStatus.AMENDED,
                previousReportId = attached.reportId
            )
        )
        Unit
    }

    @Test
    fun textOnlyRegimenIsNotNormalized() {
        val regimen = Regimen.textOnly("bd after food")
        assertEquals(Regimen.Kind.TEXT_ONLY, regimen.kind)
        try {
            Regimen(kind = Regimen.Kind.FIXED_TIMES, originalText = "bd", times = null, zoneId = null)
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
