package com.medtrack.app.data.db.model

import com.medtrack.app.data.db.entity.MedicineEntity
import com.medtrack.app.data.db.entity.ReportEntity
import com.medtrack.app.data.db.entity.TaskEntity

data class PatientCurrentProcess(
    val context: PatientVisitContext,
    val medicines: List<MedicineEntity>,
    val tasks: List<TaskEntity>,
    val reports: List<ReportEntity>,
    val followUp: FollowUpWithPatient?
)
