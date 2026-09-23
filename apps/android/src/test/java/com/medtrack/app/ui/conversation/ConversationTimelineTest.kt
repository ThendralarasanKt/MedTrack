package com.medtrack.app.ui.conversation

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConversationTimelineTest {
    @Test
    fun timelineAppendsAndKeepsEarlierTurns() {
        val timeline = ConversationTimeline(ConversationState(activeScope = PatientScope.selected("adm-1")))
        timeline.applyStarter("Record an update")
        val first = timeline.state.items.first().id
        timeline.sendFromComposer()
        timeline.onComposerChange("Another note about rounds")
        timeline.sendFromComposer()
        assertEquals(first, timeline.state.items.first().id)
        assertTrue(timeline.state.items.count { it.kind == TimelineKind.DOCTOR } >= 2)
    }

    @Test
    fun readingOlderContentShowsNewResponseInsteadOfClaimingBottom() {
        val timeline = ConversationTimeline(ConversationState(activeScope = PatientScope.selected("adm-1"), nearBottom = false))
        timeline.onComposerChange("Record the evening round")
        timeline.sendFromComposer()
        assertTrue(timeline.state.showNewResponse)
        timeline.jumpToLatest()
        assertFalse(timeline.state.showNewResponse)
    }

    @Test
    fun answeredClarificationCollapsesAndStays() {
        val timeline = ConversationTimeline()
        timeline.onComposerChange("Stop the antibiotic and check her later")
        timeline.sendFromComposer()
        val question = timeline.state.activeClarification
        assertNotNull(question)
        assertTrue(question!!.largeSelector)
        timeline.openSelector(question.id)
        timeline.closeSelector("Mrs Anjali Rao — ICU, Bed 4")
        val collapsed = timeline.state.items.first { it.id == question.id }
        assertFalse(collapsed.expanded)
        assertTrue(timeline.state.items.any { it.kind == TimelineKind.ANSWER && it.text.contains("Anjali") })
        assertTrue(timeline.state.items.any { it.kind == TimelineKind.CLARIFICATION && it.expanded })
    }

    @Test
    fun approvalBecomesReceiptThenScheduled() {
        val timeline = answeredThroughTime()
        val proposal = timeline.state.items.last { it.kind == TimelineKind.PROPOSAL && it.expanded }
        timeline.approveProposal(proposal.id)
        val receipt = timeline.state.items.last { it.kind == TimelineKind.RECEIPT }
        assertEquals("Saved; scheduling pending", receipt.receiptLabel)
        timeline.markReminderScheduled(receipt.id)
        assertEquals("Reminder scheduled", timeline.state.items.first { it.id == receipt.id }.receiptLabel)
    }

    @Test
    fun reportJobDoesNotBlockComposer() {
        val timeline = ConversationTimeline(ConversationState(activeScope = PatientScope.selected("adm-1")))
        timeline.onComposerChange("Attach a report from the lab")
        timeline.sendFromComposer()
        assertTrue(timeline.state.items.any { it.kind == TimelineKind.JOB })
        assertTrue(timeline.state.composerEnabled)
        timeline.onComposerChange("Also note the evening fluids")
        timeline.sendFromComposer()
        assertTrue(timeline.state.items.count { it.kind == TimelineKind.DOCTOR } == 2)
    }

    @Test
    fun changingAnswerAppendsRevisionAndInvalidatesDependents() {
        val timeline = ConversationTimeline()
        timeline.onComposerChange("Stop the antibiotic")
        timeline.sendFromComposer()
        val patient = timeline.state.activeClarification!!
        timeline.answerCard(patient.id, patient.options.first())
        val answer = timeline.state.items.last { it.kind == TimelineKind.ANSWER }
        val later = timeline.state.items.last { it.kind == TimelineKind.CLARIFICATION }
        timeline.changeAnswer(answer.id)
        assertTrue(timeline.state.items.any { it.kind == TimelineKind.REVISION })
        assertTrue(timeline.state.items.first { it.id == later.id }.invalidated)
        assertTrue(timeline.state.activeClarification != null)
    }

    @Test
    fun composerDoesNotSilentlyAnswerClarification() {
        val timeline = ConversationTimeline()
        timeline.onComposerChange("Stop the antibiotic and check her")
        timeline.sendFromComposer()
        val before = timeline.state.items.size
        timeline.onComposerChange("Ceftriaxone")
        timeline.sendFromComposer()
        assertEquals(before, timeline.state.items.size)
        assertEquals(ComposerIntent.CHOOSE, timeline.state.composerIntent)
        timeline.sendComposerAsNewMessage()
        assertTrue(timeline.state.items.last { it.kind == TimelineKind.DOCTOR }.text == "Ceftriaxone")
        assertTrue(timeline.state.activeClarification != null)
    }

    @Test
    fun useAsAnswerSubmitsTheOpenCard() {
        val timeline = ConversationTimeline(ConversationState(activeScope = PatientScope.selected("adm-1")))
        timeline.onComposerChange("Stop the antibiotic")
        timeline.sendFromComposer()
        timeline.onComposerChange("Ceftriaxone — 1 g IV twice daily")
        timeline.sendFromComposer()
        timeline.useComposerAsAnswer()
        assertTrue(timeline.state.items.any { it.kind == TimelineKind.ANSWER && it.text.contains("Ceftriaxone") })
    }

    @Test
    fun scopeChangeDoesNotRelabelEarlierMessages() {
        val timeline = ConversationTimeline(ConversationState(activeScope = PatientScope.selected("adm-1")))
        timeline.onComposerChange("Record an update")
        timeline.sendFromComposer()
        val original = timeline.state.items.first { it.kind == TimelineKind.DOCTOR }.scope.admissionId
        timeline.clearScope()
        timeline.onComposerChange("A different patient note")
        timeline.sendFromComposer()
        assertEquals(original, timeline.state.items.first { it.kind == TimelineKind.DOCTOR }.scope.admissionId)
        assertNull(timeline.state.items.last { it.kind == TimelineKind.DOCTOR }.scope.admissionId)
    }

    @Test
    fun crossPatientProposalsStaySeparate() {
        val timeline = ConversationTimeline()
        timeline.onComposerChange("Rao needs potassium and Sharma can move")
        timeline.sendFromComposer()
        val proposals = timeline.state.items.filter { it.kind == TimelineKind.PROPOSAL }
        assertEquals(2, proposals.size)
        assertTrue(proposals.map { it.scope.patientLabel }.toSet().size == 2)
    }

    @Test
    fun offlineSendIsSavedOnDevice() {
        val timeline = ConversationTimeline(ConversationState(online = false, activeScope = PatientScope.selected("adm-1")))
        timeline.onComposerChange("Record an update while offline")
        timeline.sendFromComposer()
        val doctor = timeline.state.items.first { it.kind == TimelineKind.DOCTOR }
        assertTrue(doctor.offlineSaved)
        assertTrue(doctor.waitingForConnection)
    }

    @Test
    fun restoreKeepsUnansweredCard() {
        val timeline = ConversationTimeline()
        timeline.onComposerChange("Stop the antibiotic and check her later")
        timeline.sendFromComposer()
        val restored = ConversationTimeline(ConversationState.fromJson(timeline.state.toJson()))
        assertEquals(
            timeline.state.activeClarification?.text,
            restored.state.activeClarification?.text
        )
        assertTrue(restored.state.items.isNotEmpty())
    }

    private fun answeredThroughTime(): ConversationTimeline {
        val timeline = ConversationTimeline(ConversationState(activeScope = PatientScope.selected("adm-1")))
        timeline.onComposerChange("Stop the antibiotic")
        timeline.sendFromComposer()
        val med = timeline.state.activeClarification!!
        timeline.answerCard(med.id, med.options.first())
        val time = timeline.state.activeClarification!!
        timeline.answerCard(time.id, "4 hours")
        return timeline
    }
}
