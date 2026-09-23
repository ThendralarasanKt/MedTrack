package com.medtrack.app.hybrid

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.medtrack.app.data.care.CareLocalSession
import com.medtrack.app.data.care.command.CareClinicalAssessmentService
import com.medtrack.app.data.care.command.CareCommandService
import com.medtrack.app.data.care.command.CareTherapyCommandService
import com.medtrack.app.data.care.command.CareWorkCommandService
import com.medtrack.app.data.care.command.CareWritePath
import com.medtrack.app.data.care.command.CreatePatientAndAdmissionRequest
import com.medtrack.app.data.care.entity.CareHospitalEntity
import com.medtrack.app.data.care.entity.CarePersonEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.db.AppDatabase
import com.medtrack.app.hybrid.account.AccountBindingStore
import com.medtrack.app.hybrid.account.AccountSession
import com.medtrack.app.hybrid.assistant.AssistantActionPolicy
import com.medtrack.app.hybrid.assistant.AssistantContractException
import com.medtrack.app.hybrid.assistant.AssistantRegistries
import com.medtrack.app.hybrid.assistant.AssistantTurnDecoder
import com.medtrack.app.hybrid.assistant.AssistantTurnPipeline
import com.medtrack.app.hybrid.proposal.ProposalCommitService
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AssistantTurnPipelineTest {
    private lateinit var db: AppDatabase
    private lateinit var pipeline: AssistantTurnPipeline
    private lateinit var writePath: CareWritePath
    private lateinit var admissionId: String

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
        val session = AccountSession(AccountBindingStore(context).apply { inMemoryOnly = true })
        val commit = ProposalCommitService(db, writePath, db.hybridDao(), session)
        pipeline = AssistantTurnPipeline(commit, db.careWorkDao())
        val now = System.currentTimeMillis()
        db.careDao().insertPerson(
            CarePersonEntity(
                id = CareLocalSession.ACTOR_PERSON_ID,
                ownerAccountId = CareLocalSession.OWNER_ACCOUNT_ID,
                displayName = "Dr Synthetic",
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
        admissionId = writePath.admissions.createPatientAndAdmission(
            CareLocalSession.workspace(),
            CreatePatientAndAdmissionRequest("Synthetic Patient", CareLocalSession.HOSPITAL_ID)
        ).admissionId
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun unknownCardKindFailsClosed() {
        try {
            AssistantTurnDecoder.decode(envelope("CLARIFICATION", "WIDGET", JSONArray().put("SUBMIT_ANSWER")))
            fail("unknown card must fail")
        } catch (error: AssistantContractException) {
            assertEquals("UNSUPPORTED_CARD", error.code)
        }
    }

    @Test
    fun clarificationCannotApprove() {
        try {
            AssistantTurnDecoder.decode(
                envelope("CLARIFICATION", "CLARIFICATION", JSONArray().put("APPROVE_GROUP"))
            )
            fail("approve on clarification must fail")
        } catch (error: AssistantContractException) {
            assertEquals("UNSUPPORTED_ACTION", error.code)
        }
    }

    @Test
    fun clarificationAnswerDoesNotWriteClinicalRecords() = runBlocking {
        val before = db.careWorkDao().openTasksForOwner(CareLocalSession.OWNER_ACCOUNT_ID).size
        val turn = AssistantTurnDecoder.decode(
            envelope(
                "CLARIFICATION",
                "CLARIFICATION",
                JSONArray().put("SUBMIT_ANSWER"),
                payload = JSONObject()
                    .put("responseType", "PATIENT_SELECTION")
                    .put("questionId", "q-1")
                    .put("questionDigest", "sha256:abc")
            )
        )
        val outcome = pipeline.submitClarification(
            turn,
            questionId = "q-1",
            questionDigest = "sha256:abc",
            answerKind = "PATIENT_SELECTION",
            selectedRefs = JSONArray().put(JSONObject().put("entityType", "Patient").put("id", "pat-1"))
        )
        assertFalse(outcome.clinicalWrite)
        assertEquals("CLARIFICATION", outcome.nextResultKind)
        assertEquals(before, db.careWorkDao().openTasksForOwner(CareLocalSession.OWNER_ACCOUNT_ID).size)
    }

    @Test
    fun sixthClarificationTurnRequiresManualWorkflow() {
        val turn = AssistantTurnDecoder.decode(
            envelope(
                "CLARIFICATION",
                "CLARIFICATION",
                JSONArray().put("SUBMIT_ANSWER"),
                payload = JSONObject().put("responseType", "SHORT_TEXT").put("questionId", "q-6")
            )
        )
        val outcome = pipeline.submitClarification(
            turn,
            questionId = "q-6",
            questionDigest = "sha256:q",
            answerKind = "SHORT_TEXT",
            turnCountBefore = AssistantRegistries.MAX_CLARIFICATION_TURNS
        )
        assertEquals("MANUAL_REQUIRED", outcome.nextResultKind)
        assertFalse(outcome.clinicalWrite)
    }

    @Test
    fun narrativeMismatchBlocksApproval() {
        val bundle = taskBundle("Remind me in four hours.")
        try {
            AssistantActionPolicy.prepareApproval(bundle, "Stop ceftriaxone and remind me.")
            fail("narrative mismatch must block")
        } catch (error: AssistantContractException) {
            assertEquals("NARRATIVE_MISMATCH", error.code)
        }
    }

    @Test
    fun approvedTaskReceiptStartsPendingThenCanReportScheduled() = runBlocking {
        val patientId = db.careDao().getEpisode(admissionId)!!.patientId
        val bundle = taskBundle("Remind me to review labs.").apply {
            getJSONObject("identity").put("patientId", patientId).put("admissionId", admissionId)
            getJSONArray("operations").getJSONObject(0)
                .getJSONObject("fields").put("admissionId", admissionId)
        }
        val receipt = pipeline.approveProposal(bundle, "Remind me to review labs.")
        assertEquals("RECEIPT", receipt.getString("cardKind"))
        assertFalse(receipt.getBoolean("derivedFromModel"))
        val effect = receipt.getJSONArray("groupResults")
            .getJSONObject(0)
            .getJSONArray("effects")
            .getJSONObject(0)
        assertEquals("PENDING", effect.getString("status"))
        assertEquals("Saved; scheduling pending", effect.getString("label"))

        val scheduleId = effect.getString("ref")
        val row = db.careWorkDao().outboxForSchedule(scheduleId).single()
        db.careWorkDao().updateSchedulingOutbox(
            row.copy(state = CareEnums.SchedulingOutboxState.APPLIED.name)
        )
        val updated = pipeline.receiptCard(
            bundle.getString("proposalId"),
            receipt.getString("approvedDigest"),
            listOf(
                com.medtrack.app.hybrid.proposal.GroupCommitResult(
                    "g-task",
                    listOf(
                        com.medtrack.app.data.care.command.OperationReceipt(
                            "op-task",
                            JSONObject().put("scheduleId", scheduleId).put("taskId", "t").toString(),
                            false
                        )
                    )
                )
            )
        )
        val label = updated.getJSONArray("groupResults")
            .getJSONObject(0)
            .getJSONArray("effects")
            .getJSONObject(0)
            .getString("label")
        assertEquals("Reminder scheduled", label)
        assertTrue(db.careWorkDao().openTasksForOwner(CareLocalSession.OWNER_ACCOUNT_ID).isNotEmpty())
    }

    @Test
    fun transmissionDraftNeverClaimsSent() {
        val draft = pipeline.draftTransmission("WHATSAPP", "Please review bed 4.")
        assertEquals("DRAFT", draft.status)
        assertFalse(draft.sent)
        assertTrue(draft.message.contains("nothing was sent", ignoreCase = true))
    }

    @Test
    fun unknownOperationFailsClosed() {
        try {
            AssistantRegistries.commandFor("SEND_WHATSAPP")
            fail("unknown operation must fail")
        } catch (error: AssistantContractException) {
            assertEquals("UNSUPPORTED_OPERATION", error.code)
        }
    }

    private fun envelope(
        resultKind: String,
        cardKind: String,
        actions: JSONArray,
        payload: JSONObject = JSONObject().put("responseType", "PATIENT_SELECTION")
    ): JSONObject = JSONObject()
        .put("assistantSchemaVersion", AssistantRegistries.ASSISTANT_SCHEMA)
        .put("resultKind", resultKind)
        .put("workflowId", "wf-1")
        .put("turnId", "turn-1")
        .put("requestId", "req-1")
        .put("contextDigest", "sha256:ctx")
        .put("planVersion", 1)
        .put("narrative", JSONObject().put("text", "Which patient?").put("derivedFromTypedResult", true))
        .put(
            "cards",
            JSONArray().put(
                JSONObject()
                    .put("cardId", "card-1")
                    .put("cardKind", cardKind)
                    .put("allowedActions", actions)
                    .put("payload", payload)
            )
        )

    private fun taskBundle(summary: String): JSONObject = JSONObject(
        """
        {
          "proposalId":"prop-task",
          "commandSchemaVersion":"care-commands-1",
          "contextDigest":"sha256:ctx",
          "identity":{"state":"RESOLVED","patientId":"p","admissionId":"a"},
          "summary":${JSONObject.quote(summary)},
          "operations":[{
            "operationId":"op-task",
            "atomicGroupId":"g-task",
            "type":"ASSIGN_TASK",
            "dependsOn":[],
            "target":{"kind":"ADMISSION","id":"a"},
            "fields":{"title":"Review labs","kind":"OTHER","admissionId":"a"},
            "unresolvedFields":[],
            "effectiveTime":{"relative":{
              "amount":4,"unit":"HOURS","anchor":"now",
              "resolvedAt":"2026-09-23T18:00:00+05:30","zoneId":"Asia/Kolkata"
            }}
          }]
        }
        """.trimIndent()
    )
}
