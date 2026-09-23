package com.medtrack.app.data.care.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medtrack.app.data.care.model.ClinicalTime

@Entity(
    tableName = "care_referrals",
    indices = [
        Index(value = ["ownerAccountId", "admissionId", "status"]),
        Index(value = ["eventId"])
    ]
)
data class CareReferralEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val direction: String,
    val requesterPersonId: String? = null,
    val requesterTeamId: String? = null,
    val requestedPersonId: String? = null,
    val requestedTeamId: String? = null,
    val requestedSpecialty: String? = null,
    val reason: String,
    val requestedAt: ClinicalTime,
    val priority: String,
    val status: String,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_referral_milestones",
    indices = [Index(value = ["referralId"]), Index(value = ["eventId"])]
)
data class CareReferralMilestoneEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val referralId: String,
    val kind: String,
    val encounterId: String? = null,
    val note: String? = null,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_consultation_advice",
    indices = [
        Index(value = ["ownerAccountId", "admissionId"]),
        Index(value = ["referralId"]),
        Index(value = ["eventId"])
    ]
)
data class CareConsultationAdviceEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val referralId: String? = null,
    val admissionId: String,
    val advisorPersonId: String? = null,
    val text: String,
    val advisedAt: ClinicalTime,
    val disposition: String,
    val reviewedBy: String? = null,
    val reviewedAt: Long? = null,
    val dispositionReason: String? = null,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_advice_decision_links",
    indices = [Index(value = ["adviceId", "decisionId"], unique = true)]
)
data class CareAdviceDecisionLinkEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val adviceId: String,
    val decisionId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_clinical_questions",
    indices = [
        Index(value = ["ownerAccountId", "admissionId", "status"]),
        Index(value = ["eventId"])
    ]
)
data class CareClinicalQuestionEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val askerPersonId: String? = null,
    val text: String,
    val askedAt: ClinicalTime,
    val status: String,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_question_responses",
    indices = [Index(value = ["questionId"]), Index(value = ["eventId"])]
)
data class CareQuestionResponseEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val questionId: String,
    val responderPersonId: String? = null,
    val text: String,
    val decisionId: String? = null,
    val answeredAt: ClinicalTime,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_communication_events",
    indices = [
        Index(value = ["ownerAccountId", "admissionId"]),
        Index(value = ["eventId"])
    ]
)
data class CareCommunicationEventEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val senderPersonId: String? = null,
    val channel: String,
    val state: String,
    val communicatedAt: ClinicalTime,
    val contentSummary: String,
    val precedingCommunicationId: String? = null,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_communication_recipients",
    indices = [Index(value = ["communicationId"])]
)
data class CareCommunicationRecipientEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val communicationId: String,
    val personId: String? = null,
    val teamId: String? = null,
    val stationId: String? = null,
    val reportedLabel: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_communication_subjects",
    indices = [Index(value = ["communicationId"])]
)
data class CareCommunicationSubjectEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val communicationId: String,
    val referralId: String? = null,
    val questionId: String? = null,
    val decisionId: String? = null,
    val medicationOrderId: String? = null,
    val careTaskId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)
