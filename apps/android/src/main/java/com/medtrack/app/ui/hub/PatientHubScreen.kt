package com.medtrack.app.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.query.HubLabItem
import com.medtrack.app.data.care.query.VitalScope
import com.medtrack.app.ui.common.components.ChipTone
import com.medtrack.app.ui.common.components.ClinicalFormDialog
import com.medtrack.app.ui.common.components.StatusChip
import com.medtrack.app.ui.theme.LocalPaperColors

private val HubTabs = listOf("Overview", "Meds", "Labs", "Vitals", "Timeline", "Tasks")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHubScreen(
    admissionId: String,
    onBack: () -> Unit,
    onHandover: () -> Unit = {},
    onCapture: () -> Unit = {},
    viewModel: PatientHubViewModel = hiltViewModel()
) {
    val colors = LocalPaperColors.current
    val hub by viewModel.hub.collectAsState()
    val discharged by viewModel.discharged.collectAsState()
    val error by viewModel.error.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<HubDialog?>(null) }
    var selectedReport by remember { mutableStateOf<HubLabItem?>(null) }
    var textA by remember { mutableStateOf("") }
    var textB by remember { mutableStateOf("") }
    var textC by remember { mutableStateOf("") }

    LaunchedEffect(admissionId) { viewModel.load(admissionId) }
    LaunchedEffect(discharged) { if (discharged) onBack() }

    fun open(kind: HubDialog) {
        textA = if (kind == HubDialog.Task) "4" else ""
        textB = ""
        textC = if (kind == HubDialog.Med) "as directed" else ""
        dialog = kind
    }

    val patient = hub?.census
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Surface(color = colors.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Text("Census")
                    }
                    Text(
                        "Admission ${admissionId.take(8)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            patient?.displayName ?: "Patient",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            listOfNotNull(
                                patient?.reportedAge?.let { "${it}y" },
                                patient?.reportedSex,
                                patient?.involvementRole?.replace('_', ' ')
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                    StatusChip(patient?.locationLabel ?: "No bed", ChipTone.Warning)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { open(HubDialog.Move) }) { Text("Move Bed") }
                    OutlinedButton(onClick = onCapture) { Text("AI capture") }
                    TextButton(onClick = { dialog = HubDialog.Discharge }) { Text("Discharge") }
                }
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = colors.surface) {
                    HubTabs.forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                    }
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                0 -> {
                    HubCard(title = "ACTIVE CLINICAL PROBLEMS", action = "+ Problem", onAction = { open(HubDialog.Problem) }) {
                        val problems = hub?.problems.orEmpty().filter { it.status == CareEnums.ProblemStatus.ACTIVE.name }
                        if (problems.isEmpty()) Text("No problems recorded.", color = colors.textSecondary)
                        problems.forEach { problem ->
                            Text(problem.description, fontWeight = FontWeight.SemiBold)
                            Text(problem.certainty, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                        }
                    }
                    HubCard(title = "ALLERGIES", action = "+ Allergy", onAction = { open(HubDialog.Allergy) }) {
                        val allergies = hub?.allergies.orEmpty()
                        if (allergies.isEmpty()) Text("Allergy status not recorded.", color = colors.textSecondary)
                        allergies.forEach { allergy ->
                            Text(allergy.substance, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(allergy.reaction, allergy.severity, allergy.status).joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                    HubCard(title = "CURRENT PLAN") {
                        Text(hub?.planNote ?: "No rounds note yet.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                1 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Prescription • orders", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { open(HubDialog.Med) }) { Text("+ New Order") }
                    }
                    val activeMeds = hub?.medications.orEmpty().filter { it.active }
                    val historicalMeds = hub?.medications.orEmpty().filter { !it.active }
                    if (activeMeds.isEmpty() && historicalMeds.isEmpty()) {
                        HubCard(title = "MEDICATION ORDERS") {
                            Text("No medication orders.", color = colors.textSecondary)
                        }
                    }
                    if (activeMeds.isNotEmpty()) {
                        Text("Active treatment", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                    activeMeds.forEach { med ->
                        Surface(shape = MaterialTheme.shapes.medium, color = colors.surface, shadowElevation = 1.dp) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(med.name, fontWeight = FontWeight.Bold)
                                    StatusChip(med.status, ChipTone.Success)
                                }
                                Text(med.dose, style = MaterialTheme.typography.bodyMedium)
                                Text(med.schedule, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                TextButton(onClick = {
                                    viewModel.stopMedication(admissionId, med.orderId, med.medicationId)
                                }) { Text("Stop") }
                            }
                        }
                    }
                    if (historicalMeds.isNotEmpty()) {
                        Text("Stopped / historical orders", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                    historicalMeds.forEach { med ->
                        Surface(shape = MaterialTheme.shapes.medium, color = colors.surface, shadowElevation = 1.dp) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(med.name, fontWeight = FontWeight.Bold)
                                    StatusChip(med.status, ChipTone.Warning)
                                }
                                Text(med.dose, style = MaterialTheme.typography.bodyMedium)
                                Text(med.schedule, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                            }
                        }
                    }
                }
                2 -> {
                    val orders = hub?.investigations.orEmpty()
                    val reports = hub?.reports.orEmpty()
                    if (orders.isEmpty() && reports.isEmpty()) {
                        HubCard(title = "LABS & REPORTS") {
                            Text("No investigations or reports.", color = colors.textSecondary)
                        }
                    }
                    if (orders.isNotEmpty()) {
                        Text("Investigation requests", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                    orders.forEach { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReport = item },
                            shape = MaterialTheme.shapes.medium,
                            color = colors.surface,
                            shadowElevation = 1.dp
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(item.title, fontWeight = FontWeight.Bold)
                                    StatusChip(item.status, ChipTone.Info)
                                }
                                Text("Request · ${item.kind.replace('_', ' ')}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                item.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    if (reports.isNotEmpty()) {
                        Text("Reports", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                    reports.forEach { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReport = item },
                            shape = MaterialTheme.shapes.medium,
                            color = colors.surface,
                            shadowElevation = 1.dp
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(item.title, fontWeight = FontWeight.Bold)
                                    StatusChip(item.status, ChipTone.Info)
                                }
                                Text("Report · ${item.kind.replace('_', ' ')}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                item.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Text("View →", color = colors.accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                3 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Vitals & observations", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { open(HubDialog.Vital) }) { Text("+ Vital") }
                    }
                    val currentVitals = hub?.vitals.orEmpty().filter { it.scope == VitalScope.CURRENT }
                    val priorVitals = hub?.vitals.orEmpty().filter { it.scope != VitalScope.CURRENT }
                    if (currentVitals.isEmpty() && priorVitals.isEmpty()) {
                        HubCard(title = "VITALS & OBSERVATIONS") {
                            Text("No observations recorded.", color = colors.textSecondary)
                        }
                    }
                    currentVitals.forEach { vital ->
                        Surface(shape = MaterialTheme.shapes.medium, color = colors.surface, shadowElevation = 1.dp) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(vital.name, fontWeight = FontWeight.Bold)
                                Text(listOfNotNull(vital.value, vital.unit).joinToString(" "), style = MaterialTheme.typography.titleMedium)
                                Text(vital.observedAt, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                            }
                        }
                    }
                    if (priorVitals.isNotEmpty()) {
                        Text("Prior admission / longitudinal history", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                    priorVitals.forEach { vital ->
                        Surface(shape = MaterialTheme.shapes.medium, color = colors.surface, shadowElevation = 1.dp) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(vital.name, fontWeight = FontWeight.Bold)
                                Text(listOfNotNull(vital.value, vital.unit).joinToString(" "), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${vital.scopeLabel} · ${vital.observedAt}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
                4 -> {
                    val items = hub?.timeline.orEmpty()
                    if (items.isEmpty()) Text("No events yet.", color = colors.textSecondary)
                    items.forEach { item ->
                        Surface(shape = MaterialTheme.shapes.medium, color = colors.surface) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.title, fontWeight = FontWeight.Bold, color = colors.accent)
                                Text(item.whenLabel, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                            }
                        }
                    }
                }
                else -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Patient-specific tasks", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { open(HubDialog.Task) }) { Text("+ Add Task") }
                    }
                    val tasks = hub?.tasks.orEmpty()
                    if (tasks.isEmpty()) Text("No tasks.", color = colors.textSecondary)
                    tasks.forEach { task ->
                        Surface(shape = MaterialTheme.shapes.medium, color = colors.surface, shadowElevation = 1.dp) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(task.title, fontWeight = FontWeight.Bold)
                                    StatusChip(task.status, if (task.status == "COMPLETED") ChipTone.Success else ChipTone.Warning)
                                }
                                task.instructions?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                if (task.status != CareEnums.CareTaskStatus.COMPLETED.name) {
                                    Button(onClick = { viewModel.completeTask(task.id, admissionId) }) { Text("Done") }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { open(HubDialog.Note) }, modifier = Modifier.weight(1f)) {
                Text("+ Bedside Review")
            }
            Button(onClick = { open(HubDialog.Task) }, modifier = Modifier.weight(1f)) {
                Text("+ Order / Task")
            }
            TextButton(onClick = onHandover) { Text("Handover") }
        }
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Could not save") },
            text = { Text(error.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }

    when (dialog) {
        HubDialog.Discharge -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Discharge patient?") },
            text = { Text("The admission stays in history as discharged. Nothing is deleted.") },
            confirmButton = {
                TextButton(onClick = { dialog = null; viewModel.discharge(admissionId) }) { Text("Discharge") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
        HubDialog.Move -> ClinicalFormDialog(
            title = "Move bed",
            onDismiss = { dialog = null },
            confirmLabel = "Move",
            confirmEnabled = textA.isNotBlank(),
            onConfirm = {
                viewModel.moveBed(admissionId, textA)
                dialog = null
            }
        ) {
            OutlinedTextField(
                textA,
                { textA = it },
                label = { Text("Bed code or label") },
                placeholder = { Text("ICU Bed 04") }
            )
        }
        HubDialog.Note -> ClinicalFormDialog(
            title = "Bedside review",
            onDismiss = { dialog = null },
            confirmEnabled = textA.isNotBlank() || textB.isNotBlank(),
            onConfirm = {
                viewModel.addBedsideNote(admissionId, textA, textB)
                dialog = null
            }
        ) {
            OutlinedTextField(textA, { textA = it }, label = { Text("Observations") }, minLines = 3)
            OutlinedTextField(textB, { textB = it }, label = { Text("Plan updates") })
        }
        HubDialog.Task -> ClinicalFormDialog(
            title = "Add clinical task",
            onDismiss = { dialog = null },
            confirmEnabled = textB.isNotBlank(),
            onConfirm = {
                viewModel.addTask(admissionId, textB, textA)
                dialog = null
            }
        ) {
            OutlinedTextField(textB, { textB = it }, label = { Text("Task title") }, placeholder = { Text("Repeat potassium") })
            OutlinedTextField(textA, { textA = it }, label = { Text("Due in hours") })
        }
        HubDialog.Med -> ClinicalFormDialog(
            title = "New medication order",
            onDismiss = { dialog = null },
            confirmEnabled = textA.isNotBlank(),
            onConfirm = {
                viewModel.addMedication(admissionId, textA, textB, textC)
                dialog = null
            }
        ) {
            OutlinedTextField(textA, { textA = it }, label = { Text("Medicine") })
            OutlinedTextField(textB, { textB = it }, label = { Text("Dose") }, placeholder = { Text("1 g IV") })
            OutlinedTextField(textC, { textC = it }, label = { Text("Schedule") })
        }
        HubDialog.Problem -> ClinicalFormDialog(
            title = "Add problem",
            onDismiss = { dialog = null },
            confirmEnabled = textA.isNotBlank() && patient != null,
            onConfirm = {
                patient?.let { viewModel.addProblem(admissionId, it.patientId, textA) }
                dialog = null
            }
        ) {
            OutlinedTextField(textA, { textA = it }, label = { Text("Description") }, minLines = 2)
        }
        HubDialog.Allergy -> ClinicalFormDialog(
            title = "Add allergy",
            onDismiss = { dialog = null },
            confirmEnabled = textA.isNotBlank() && patient != null,
            onConfirm = {
                patient?.let { viewModel.addAllergy(admissionId, it.patientId, textA, textB) }
                dialog = null
            }
        ) {
            OutlinedTextField(textA, { textA = it }, label = { Text("Substance") })
            OutlinedTextField(textB, { textB = it }, label = { Text("Reaction") })
        }
        HubDialog.Vital -> ClinicalFormDialog(
            title = "Record observation",
            onDismiss = { dialog = null },
            confirmEnabled = textA.isNotBlank() && textB.isNotBlank() && patient != null,
            onConfirm = {
                patient?.let { viewModel.addVital(admissionId, it.patientId, textA, textB, textC) }
                dialog = null
            }
        ) {
            OutlinedTextField(textA, { textA = it }, label = { Text("Name") }, placeholder = { Text("Blood pressure") })
            OutlinedTextField(textB, { textB = it }, label = { Text("Value") })
            OutlinedTextField(textC, { textC = it }, label = { Text("Unit") }, placeholder = { Text("mmHg") })
        }
        null -> Unit
    }

    selectedReport?.let { report ->
        AlertDialog(
            onDismissRequest = { selectedReport = null },
            title = { Text(report.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${report.kind.replace('_', ' ')} • ${report.status}", style = MaterialTheme.typography.labelSmall)
                    Text(report.detail ?: "No narrative stored with this report.")
                    Text(
                        "Encrypted document is stored with the care record. This viewer shows the attached narrative.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            },
            confirmButton = { TextButton(onClick = { selectedReport = null }) { Text("Done") } }
        )
    }
}

private enum class HubDialog { Move, Discharge, Note, Task, Med, Problem, Allergy, Vital }

@Composable
private fun HubCard(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalPaperColors.current
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                if (action != null && onAction != null) {
                    Text(
                        action,
                        color = colors.accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onAction)
                    )
                }
            }
            content()
        }
    }
}
