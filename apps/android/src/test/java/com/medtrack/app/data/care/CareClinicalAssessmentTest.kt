package com.medtrack.app.data.care

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.medtrack.app.data.care.command.CareClinicalAssessmentService
import com.medtrack.app.data.care.command.CareCommandService
import com.medtrack.app.data.care.command.CareWorkspace
import com.medtrack.app.data.care.command.CommitClinicalDecisionRequest
import com.medtrack.app.data.care.command.CreatePatientAndAdmissionRequest
import com.medtrack.app.data.care.command.RecordAllergyAssessmentRequest
import com.medtrack.app.data.care.command.RecordAllergyRequest
import com.medtrack.app.data.care.command.RecordEncounterRequest
import com.medtrack.app.data.care.command.RecordObservationRequest
import com.medtrack.app.data.care.command.RecordProblemRequest
import com.medtrack.app.data.care.command.UpdateProblemRequest
import com.medtrack.app.data.care.entity.CareHospitalEntity
import com.medtrack.app.data.care.entity.CarePersonEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CareClinicalAssessmentTest {
    private lateinit var db: AppDatabase
    private lateinit var commands: CareCommandService
    private lateinit var clinical: CareClinicalAssessmentService
    private lateinit var workspace: CareWorkspace
    private val ownerId = CareIds.newId()
    private val doctorId = CareIds.newId()
    private val consultantId = CareIds.newId()
    private val hospitalId = CareIds.newId()
    private lateinit var patientId: String
    private lateinit var admissionId: String

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        commands = CareCommandService(db, db.careDao())
        clinical = CareClinicalAssessmentService(db, db.careDao())
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
        db.careDao().insertPerson(
            CarePersonEntity(
                id = consultantId,
                ownerAccountId = ownerId,
                displayName = "Dr Consultant",
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
        val created = commands.createPatientAndAdmission(
            workspace,
            CreatePatientAndAdmissionRequest(
                patientDisplayName = "Assessment Patient",
                hospitalId = hospitalId
            )
        )
        patientId = created.patientId
        admissionId = created.admissionId
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recordEncounterWritesNoteEventAndIsIdempotent() = runBlocking {
        val operationId = CareIds.newId()
        val request = RecordEncounterRequest(
            admissionId = admissionId,
            kind = CareEnums.EncounterKind.ROUND,
            reviewedAt = ClinicalTime.instant(1_700_000_000_000L),
            summary = "Morning round",
            noteText = "Stable overnight",
            operationId = operationId
        )
        val first = clinical.recordEncounter(workspace, request)
        val second = clinical.recordEncounter(workspace, request)

        assertFalse(first.receipt.reused)
        assertTrue(second.receipt.reused)
        assertEquals(first.encounterId, second.encounterId)
        assertNotNull(first.noteId)
        assertEquals(1, db.careDao().encountersForAdmission(admissionId).size)
        assertEquals(1, db.careDao().notesForEncounter(first.encounterId).size)
        assertEquals(1, db.careDao().eventsForOperation(operationId).size)
    }

    @Test
    fun problemCodeRequiresCodeSystemAndStatusChangeCreatesRevision() = runBlocking {
        try {
            clinical.recordProblem(
                workspace,
                RecordProblemRequest(
                    patientId = patientId,
                    admissionId = admissionId,
                    description = "Pneumonia",
                    code = "J18.9",
                    codeSystem = null,
                    onset = ClinicalTime.unknown("onset unknown")
                )
            )
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        val created = clinical.recordProblem(
            workspace,
            RecordProblemRequest(
                patientId = patientId,
                admissionId = admissionId,
                description = "Pneumonia",
                codeSystem = "ICD-10",
                code = "J18.9",
                certainty = CareEnums.ProblemCertainty.SUSPECTED,
                onset = ClinicalTime.date("2026-08-01")
            )
        )
        clinical.updateProblem(
            workspace,
            UpdateProblemRequest(
                problemId = created.problemId,
                certainty = CareEnums.ProblemCertainty.CONFIRMED,
                status = CareEnums.ProblemStatus.RESOLVED,
                resolvedAt = ClinicalTime.date("2026-08-10")
            )
        )
        val problem = db.careDao().getProblem(created.problemId)!!
        assertEquals(CareEnums.ProblemStatus.RESOLVED.name, problem.status)
        assertEquals(CareEnums.ProblemCertainty.CONFIRMED.name, problem.certainty)
        assertEquals(2, problem.version)
        assertEquals(2, db.careDao().revisionsForRecord("Problem", created.problemId).size)
    }

    @Test
    fun emptyAllergiesAreNotNoneKnownWithoutAssessment() = runBlocking {
        assertEquals(
            0,
            db.careDao().allergiesForPatient(ownerId, patientId).size
        )
        assertEquals(
            0,
            db.careDao().allergyAssessmentsForPatient(ownerId, patientId).size
        )

        clinical.recordAllergyAssessment(
            workspace,
            RecordAllergyAssessmentRequest(
                patientId = patientId,
                result = CareEnums.AllergyAssessmentResult.NONE_KNOWN
            )
        )
        assertEquals(
            CareEnums.AllergyAssessmentResult.NONE_KNOWN.name,
            db.careDao().allergyAssessmentsForPatient(ownerId, patientId).single().result
        )

        clinical.recordAllergy(
            workspace,
            RecordAllergyRequest(
                patientId = patientId,
                substance = "Penicillin",
                reaction = "Rash"
            )
        )
        assertEquals(1, db.careDao().allergiesForPatient(ownerId, patientId).size)
        // Prior NONE_KNOWN assessment is retained; allergies are separate.
        assertEquals(1, db.careDao().allergyAssessmentsForPatient(ownerId, patientId).size)
    }

    @Test
    fun observationRequiresExactlyOneTypedValue() = runBlocking {
        try {
            clinical.recordObservation(
                workspace,
                RecordObservationRequest(
                    patientId = patientId,
                    admissionId = admissionId,
                    name = "SpO2",
                    valueKind = CareEnums.ObservationValueKind.NUMBER,
                    numericValue = "98",
                    textValue = "also text",
                    observedAt = ClinicalTime.instant(1_700_000_100_000L)
                )
            )
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        val recorded = clinical.recordObservation(
            workspace,
            RecordObservationRequest(
                patientId = patientId,
                admissionId = admissionId,
                name = "SpO2",
                valueKind = CareEnums.ObservationValueKind.NUMBER,
                numericValue = "98",
                unit = "%",
                observedAt = ClinicalTime.instant(1_700_000_100_000L)
            )
        )
        val row = db.careDao().getObservation(recorded.observationId)!!
        assertEquals("98", row.numericValue)
        assertNull(row.textValue)
        assertNull(row.booleanValue)
        assertEquals("%", row.unit)
    }

    @Test
    fun commitDecisionLinksProblemsAndOptionalPlanWithoutDefaultingMaker() = runBlocking {
        val problem = clinical.recordProblem(
            workspace,
            RecordProblemRequest(
                patientId = patientId,
                admissionId = admissionId,
                description = "Hypotension",
                onset = ClinicalTime.unknown("onset unknown")
            )
        )
        val encounter = clinical.recordEncounter(
            workspace,
            RecordEncounterRequest(
                admissionId = admissionId,
                reviewedAt = ClinicalTime.instant(1_700_000_200_000L),
                summary = "Review"
            )
        )
        val decision = clinical.commitClinicalDecision(
            workspace,
            CommitClinicalDecisionRequest(
                admissionId = admissionId,
                encounterId = encounter.encounterId,
                description = "Start fluids",
                rationale = "MAP low",
                decidedAt = ClinicalTime.instant(1_700_000_200_500L),
                decisionMakerPersonId = consultantId,
                linkedProblemIds = listOf(problem.problemId),
                carePlanGoals = "Restore perfusion",
                carePlanInstructions = "NS bolus then reassess",
                carePlanEffectiveAt = ClinicalTime.instant(1_700_000_200_500L)
            )
        )

        assertNotNull(decision.planRevisionId)
        assertEquals(1, db.careDao().linksForDecision(decision.decisionId).size)
        val participants = db.careDao().participantsForEvent(decision.eventId)
        assertTrue(participants.any { it.role == "AUTHOR" && it.personId == doctorId })
        assertTrue(participants.any { it.role == "DECISION_MAKER" && it.personId == consultantId })

        val withoutMaker = clinical.commitClinicalDecision(
            workspace,
            CommitClinicalDecisionRequest(
                admissionId = admissionId,
                description = "Watchful waiting",
                decidedAt = ClinicalTime.instant(1_700_000_300_000L)
            )
        )
        val makerRoles = db.careDao().participantsForEvent(withoutMaker.eventId)
            .filter { it.role == "DECISION_MAKER" }
        assertTrue(makerRoles.isEmpty())

        val plans = db.careDao().planRevisionsForAdmission(admissionId)
        assertEquals(1, plans.size)
        assertEquals(decision.decisionId, plans.single().decisionId)
        assertNull(plans.single().previousRevisionId)
    }
}
