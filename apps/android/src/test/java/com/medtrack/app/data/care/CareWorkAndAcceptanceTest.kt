package com.medtrack.app.data.care

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.medtrack.app.data.care.command.AssignTaskRequest
import com.medtrack.app.data.care.command.CareCommandService
import com.medtrack.app.data.care.command.CareWorkCommandService
import com.medtrack.app.data.care.command.CareWorkspace
import com.medtrack.app.data.care.command.CareWritePath
import com.medtrack.app.data.care.command.CareClinicalAssessmentService
import com.medtrack.app.data.care.command.CareTherapyCommandService
import com.medtrack.app.data.care.command.CreatePatientAndAdmissionRequest
import com.medtrack.app.data.care.command.CreateReferralRequest
import com.medtrack.app.data.care.command.RecordReferralMilestoneRequest
import com.medtrack.app.data.care.command.RespondToTaskRequest
import com.medtrack.app.data.care.entity.CareHospitalEntity
import com.medtrack.app.data.care.entity.CarePersonEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.care.query.CareCensusQuery
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
class CareWorkAndAcceptanceTest {
    private lateinit var db: AppDatabase
    private lateinit var writePath: CareWritePath
    private lateinit var census: CareCensusQuery
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
        val admissions = CareCommandService(db, db.careDao())
        val clinical = CareClinicalAssessmentService(db, db.careDao())
        val therapy = CareTherapyCommandService(db, db.careDao(), db.careTherapyDao())
        val work = CareWorkCommandService(db, db.careDao(), db.careWorkDao())
        writePath = CareWritePath(admissions, clinical, therapy, work)
        census = CareCensusQuery(db.careDao(), db.careWorkDao(), db.careTherapyDao())
        workspace = CareWorkspace(ownerId, doctorId)
        val now = System.currentTimeMillis()
        db.careDao().insertPerson(
            CarePersonEntity(
                id = doctorId, ownerAccountId = ownerId, displayName = "Dr Ankita",
                identityState = CareEnums.IdentityState.CONFIRMED.name,
                createdAt = now, createdBy = doctorId
            )
        )
        db.careDao().insertHospital(
            CareHospitalEntity(
                id = hospitalId, ownerAccountId = ownerId, code = "SYN",
                name = "Synthetic Hospital", timeZoneId = "Asia/Kolkata",
                status = CareEnums.ActiveStatus.ACTIVE.name,
                createdAt = now, createdBy = doctorId
            )
        )
        admissionId = writePath.admissions.createPatientAndAdmission(
            workspace, CreatePatientAndAdmissionRequest("Work Patient", hospitalId)
        ).admissionId
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun referralMilestonePatientSeenRequiresEvidence() = runBlocking {
        val referralId = writePath.work.createReferral(
            workspace,
            CreateReferralRequest(
                admissionId = admissionId,
                direction = CareEnums.ReferralDirection.OUTGOING,
                reason = "Cardiology opinion",
                requestedAt = ClinicalTime.instant(1_700_400_000_000L),
                requestedSpecialty = "Cardiology"
            )
        )
        try {
            writePath.work.recordReferralMilestone(
                workspace,
                RecordReferralMilestoneRequest(
                    referralId = referralId,
                    kind = CareEnums.ReferralMilestoneKind.PATIENT_SEEN
                )
            )
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        writePath.work.recordReferralMilestone(
            workspace,
            RecordReferralMilestoneRequest(
                referralId = referralId,
                kind = CareEnums.ReferralMilestoneKind.PATIENT_SEEN,
                patientSeenReported = true,
                note = "Seen on ward round"
            )
        )
        writePath.work.recordReferralMilestone(
            workspace,
            RecordReferralMilestoneRequest(
                referralId = referralId,
                kind = CareEnums.ReferralMilestoneKind.CLOSED
            )
        )
        assertEquals(
            CareEnums.ReferralStatus.CLOSED.name,
            db.careWorkDao().getReferral(referralId)!!.status
        )
        assertEquals(2, db.careWorkDao().milestonesForReferral(referralId).size)
        Unit
    }

    @Test
    fun assignRespondAndRescheduleUsesOutboxWithoutAutoCompleteDependent() = runBlocking {
        val taskId = writePath.work.assignTask(
            workspace,
            AssignTaskRequest(
                admissionId = admissionId,
                title = "Review labs",
                followUpOwnerPersonId = doctorId,
                dueAt = 1_700_500_000_000L,
                dueZoneId = "Asia/Kolkata",
                scheduleReminder = true
            )
        )
        assertEquals(1, db.careWorkDao().remindersForTask(taskId).size)
        assertEquals(1, db.careWorkDao().outboxForSchedule(
            db.careWorkDao().remindersForTask(taskId).single().id
        ).size)

        writePath.work.respondToTask(
            workspace,
            RespondToTaskRequest(
                taskId = taskId,
                action = CareEnums.TaskResponseAction.RESCHEDULE,
                newDueAt = 1_700_600_000_000L
            )
        )
        val reminders = db.careWorkDao().remindersForTask(taskId)
        assertEquals(2, reminders.size)
        assertEquals(CareEnums.ReminderState.CANCELLED.name, reminders.first().state)
        assertEquals(CareEnums.ReminderState.ACTIVE.name, reminders.last().state)
        assertEquals(2, reminders.last().revision)

        writePath.work.respondToTask(
            workspace,
            RespondToTaskRequest(
                taskId = taskId,
                action = CareEnums.TaskResponseAction.COMPLETE_WITH_NOTE,
                note = "Labs reviewed"
            )
        )
        assertEquals(
            CareEnums.CareTaskStatus.COMPLETED.name,
            db.careWorkDao().getCareTask(taskId)!!.status
        )
        assertTrue(census.openWorkQueue(ownerId).none { it.id == taskId })
        val rows = census.activeCensus(ownerId)
        assertEquals(1, rows.size)
        assertEquals(admissionId, rows.single().admissionId)
        Unit
    }

    @Test
    fun writePathExposesAllCommandSurfaces() {
        assertTrue(writePath.admissions === writePath.admissions)
        assertTrue(writePath.clinical !== null)
        assertTrue(writePath.therapy !== null)
        assertTrue(writePath.work !== null)
    }

    @Test
    fun admitPersistsAgeAndSexAndIntakeCanLink() = runBlocking {
        val created = writePath.admissions.createPatientAndAdmission(
            workspace,
            CreatePatientAndAdmissionRequest(
                patientDisplayName = "Age Patient",
                hospitalId = hospitalId,
                reportedAge = "54",
                sexConceptId = "Male"
            )
        )
        val patient = db.careDao().getPatient(created.patientId)!!
        assertEquals("54", patient.reportedAge)
        assertEquals("Male", patient.sexConceptId)
        val hub = census.patientHub(created.admissionId)!!
        assertEquals("54", hub.census.reportedAge)
        assertEquals("Male", hub.census.reportedSex)

        val intakeId = writePath.admissions.createUnassignedIntake(
            workspace,
            "Opinion needed in ICU",
            """{"source":"whatsapp","requester":"Dr Sharma"}"""
        )
        writePath.admissions.resolveIntake(workspace, intakeId, created.admissionId, "linked")
        val intake = db.careDao().getIntake(intakeId)!!
        assertEquals(CareEnums.IntakeStatus.RESOLVED.name, intake.status)
        assertEquals(created.admissionId, intake.resolvedAdmissionId)
        assertTrue(census.inbox(ownerId).none { it.intake.id == intakeId })
    }
}
