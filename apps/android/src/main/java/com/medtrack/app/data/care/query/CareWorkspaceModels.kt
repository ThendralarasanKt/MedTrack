package com.medtrack.app.data.care.query

import com.medtrack.app.data.care.entity.CareAllergyEntity
import com.medtrack.app.data.care.entity.CareClinicalEventEntity
import com.medtrack.app.data.care.entity.CareProblemEntity
import com.medtrack.app.data.care.entity.CareTaskEntity
import com.medtrack.app.data.care.entity.CareUnassignedIntakeEntity
import com.medtrack.app.data.care.model.ClinicalTime

data class CensusPatient(
    val admissionId: String,
    val patientId: String,
    val personId: String,
    val displayName: String,
    val reportedAge: String?,
    val reportedSex: String?,
    val locationLabel: String,
    val wardLabel: String,
    val openTaskCount: Int,
    val involvementRole: String,
    val episodeStatus: String,
    val problemSummary: String?,
    val nextTaskTitle: String?
)

data class HubMedication(
    val orderId: String,
    val medicationId: String,
    val name: String,
    val dose: String,
    val schedule: String,
    val status: String,
    val active: Boolean
)

data class HubVital(
    val name: String,
    val value: String,
    val observedAt: String,
    val unit: String? = null,
    val scope: VitalScope = VitalScope.CURRENT,
    val scopeLabel: String = "Current admission"
)

enum class VitalScope { CURRENT, PRIOR_ADMISSION, PATIENT_LEVEL }

data class HubTimelineItem(
    val eventId: String,
    val title: String,
    val whenLabel: String,
    val eventType: String
)

data class HubLabItem(
    val id: String,
    val title: String,
    val kind: String,
    val status: String,
    val detail: String?
)

data class PatientHub(
    val census: CensusPatient,
    val problems: List<CareProblemEntity>,
    val tasks: List<CareTaskEntity>,
    val events: List<CareClinicalEventEntity>,
    val planNote: String?,
    val medications: List<HubMedication> = emptyList(),
    val investigations: List<HubLabItem> = emptyList(),
    val reports: List<HubLabItem> = emptyList(),
    val vitals: List<HubVital> = emptyList(),
    val allergies: List<CareAllergyEntity> = emptyList(),
    val timeline: List<HubTimelineItem> = emptyList()
)

data class InboxItem(
    val intake: CareUnassignedIntakeEntity
)

fun ClinicalTime.displayLabel(): String =
    originalText
        ?: localDateIso
        ?: instantEpochMillis?.let { millis ->
            java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US).format(java.util.Date(millis))
        }
        ?: "Unknown"

fun timelineTitle(eventType: String): String = when (eventType) {
    "ADMISSION_CREATED" -> "Admitted"
    "TRANSFER_RECORDED" -> "Bed / location change"
    "ADMISSION_DISCHARGED" -> "Discharged"
    "ENCOUNTER_RECORDED" -> "Bedside review"
    "PROBLEM_RECORDED" -> "Problem recorded"
    "PROBLEM_UPDATED" -> "Problem updated"
    "ALLERGY_RECORDED" -> "Allergy recorded"
    "OBSERVATION_RECORDED" -> "Observation"
    "MEDICATION_ORDER_CHANGED" -> "Medication order"
    "MEDICATION_ADMINISTRATION_RECORDED" -> "Dose given"
    "REPORT_ATTACHED" -> "Report attached"
    "TASK_ASSIGNED" -> "Task assigned"
    "CLINICAL_DECISION_COMMITTED" -> "Clinical decision"
    else -> eventType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
