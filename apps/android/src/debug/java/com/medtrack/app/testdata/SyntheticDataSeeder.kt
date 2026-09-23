package com.medtrack.app.testdata

import com.medtrack.app.data.care.CareCatalogBootstrap
import com.medtrack.app.data.care.CareLocalSession
import com.medtrack.app.data.care.command.AssignTaskRequest
import com.medtrack.app.data.care.command.AttachAndMatchReportRequest
import com.medtrack.app.data.care.command.CareWorkspace
import com.medtrack.app.data.care.command.CareWritePath
import com.medtrack.app.data.care.command.ChangeMedicationOrderRequest
import com.medtrack.app.data.care.command.CreatePatientAndAdmissionRequest
import com.medtrack.app.data.care.command.RecordAllergyRequest
import com.medtrack.app.data.care.command.RecordObservationRequest
import com.medtrack.app.data.care.command.RecordProblemRequest
import com.medtrack.app.data.care.command.RecordTransferRequest
import com.medtrack.app.data.care.dao.CareWorkDao
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.care.model.Regimen
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the care model with synthetic inpatients for debug builds.
 */
@Singleton
class SyntheticDataSeeder @Inject constructor(
    private val catalog: CareCatalogBootstrap,
    private val writePath: CareWritePath,
    private val workDao: CareWorkDao
) {
    suspend fun seedIfEmpty(): Boolean {
        val workspace = CareLocalSession.workspace()
        val cat = catalog.ensure()
        if (workDao.activeEpisodes(workspace.ownerAccountId).isNotEmpty()) return false

        val now = System.currentTimeMillis()
        SEED_PATIENTS.forEach { seed ->
            val created = writePath.admissions.createPatientAndAdmission(
                workspace,
                CreatePatientAndAdmissionRequest(
                    patientDisplayName = seed.name,
                    hospitalId = cat.hospitalId
                )
            )
            catalog.findBed(seed.bed)?.let { bed ->
                writePath.admissions.recordTransfer(
                    workspace,
                    RecordTransferRequest(
                        admissionId = created.admissionId,
                        effectiveAt = ClinicalTime.instant(now),
                        locationId = bed.id
                    )
                )
            }
            writePath.clinical.recordProblem(
                workspace,
                RecordProblemRequest(
                    patientId = created.patientId,
                    admissionId = created.admissionId,
                    description = seed.problem,
                    onset = ClinicalTime.instant(now)
                )
            )
            if (seed.task.isNotBlank()) {
                writePath.work.assignTask(
                    workspace,
                    AssignTaskRequest(
                        admissionId = created.admissionId,
                        title = seed.task,
                        followUpOwnerPersonId = workspace.actorPersonId,
                        dueAt = now + 3_600_000,
                        dueZoneId = CareLocalSession.TIME_ZONE,
                        scheduleReminder = true
                    )
                )
            }
            if (seed.discharge) {
                writePath.admissions.dischargeAdmission(workspace, created.admissionId)
            }
            if (seed.name == "Rajesh Kumar") {
                seedRajeshChart(workspace, created.patientId, created.admissionId, now)
            }
            if (seed.name == "Vikram Shah") {
                writePath.clinical.recordObservation(
                    workspace,
                    RecordObservationRequest(
                        patientId = created.patientId,
                        admissionId = created.admissionId,
                        name = "Blood pressure",
                        valueKind = CareEnums.ObservationValueKind.TEXT,
                        textValue = "180/100",
                        unit = "mmHg",
                        observedAt = ClinicalTime.instant(now)
                    )
                )
            }
        }
        writePath.admissions.createUnassignedIntake(
            workspace,
            "Medicine opinion needed in ICU Bed 12 for persistent metabolic acidosis.",
            """{"source":"whatsapp","requester":"Dr Sharma"}"""
        )
        return true
    }

    private suspend fun seedRajeshChart(
        workspace: CareWorkspace,
        patientId: String,
        admissionId: String,
        now: Long
    ) {
        writePath.clinical.recordAllergy(
            workspace,
            RecordAllergyRequest(
                patientId = patientId,
                substance = "Penicillin",
                reaction = "Rash",
                severity = "Moderate"
            )
        )
        writePath.clinical.recordObservation(
            workspace,
            RecordObservationRequest(
                patientId = patientId,
                admissionId = admissionId,
                name = "Potassium",
                valueKind = CareEnums.ObservationValueKind.NUMBER,
                numericValue = "5.8",
                unit = "mmol/L",
                observedAt = ClinicalTime.instant(now)
            )
        )
        writePath.therapy.changeMedicationOrder(
            workspace,
            ChangeMedicationOrderRequest(
                admissionId = admissionId,
                medicationDisplayName = "Meropenem",
                action = CareEnums.MedicationOrderAction.START,
                doseText = "1 g IV",
                schedule = Regimen.textOnly("TDS"),
                orderedAt = ClinicalTime.instant(now)
            )
        )
        writePath.therapy.attachAndMatchReport(
            workspace,
            AttachAndMatchReportRequest(
                admissionId = admissionId,
                fileName = "abg.txt",
                filePath = "/synthetic/abg.txt",
                fileType = "TEXT",
                reportedAt = ClinicalTime.instant(now),
                narrative = "Metabolic acidosis; repeat potassium in 4 hours."
            )
        )
    }

    private data class SeedPatient(
        val name: String,
        val bed: String,
        val problem: String,
        val task: String = "",
        val discharge: Boolean = false
    )

    companion object {
        private val SEED_PATIENTS = listOf(
            SeedPatient("Rajesh Kumar", "ICU Bed 04", "Sepsis / AKI", "Repeat ABG and potassium"),
            SeedPatient("Anita Desai", "ICU Bed 12", "Post-laparotomy acidosis", "Metabolic panel at 10:30"),
            SeedPatient("Meenakshi Sundaram", "Bed 402", "Ortho referral fever", "Blood culture pending"),
            SeedPatient("Sunita Patel", "Bed 409", "Cellulitis resolving"),
            SeedPatient("Vikram Shah", "Bed 512", "Hypertension watch", "Recheck BP"),
            SeedPatient("Arun Menon", "Bed 518", "COPD exacerbation"),
            SeedPatient("Priya Nair", "", "Recovered UTI", discharge = true)
        )
    }
}
