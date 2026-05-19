package com.medtrack.app.ui.visit

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.graphics.Color
import com.medtrack.app.data.db.entity.MedicineEntity
import com.medtrack.app.data.db.entity.TaskEntity
import com.medtrack.app.ui.common.formatAppDate
import com.medtrack.app.ui.common.formatAppTime
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewVisitScreen(
    patientId: Int,
    onBack: () -> Unit,
    viewModel: NewVisitViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var symptoms by remember { mutableStateOf("") }
    var diagnosis by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var roomNo by remember { mutableStateOf("") }

    val tasks by viewModel.tasks.collectAsState()
    val medicines by viewModel.medicines.collectAsState()

    var showTaskDialog by remember { mutableStateOf(false) }
    var showMedDialog by remember { mutableStateOf(false) }
    var reportUris by remember { mutableStateOf<List<NamedReport>>(emptyList()) }
    var reportNameQueue by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var namingReportUri by remember { mutableStateOf<Uri?>(null) }
    val reportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            reportNameQueue = uris.drop(1)
            namingReportUri = uris.first()
        }
    }

    // Follow-up state
    var followUpDate by remember { mutableStateOf<LocalDate?>(null) }
    var followUpTime by remember { mutableStateOf<LocalTime?>(null) }
    var followUpReason by remember { mutableStateOf("") }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saveSuccess.collectLatest { success ->
            if (success) onBack()
        }
    }

    // Date Picker Logic
    val datePickerDialog = remember {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                followUpDate = LocalDate.of(year, month + 1, dayOfMonth)
                requestNotificationPermissionIfNeeded()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = System.currentTimeMillis() }
    }

    // Time Picker Logic
    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                followUpTime = LocalTime.of(hour, minute)
            },
            8, 0, false
        )
    }

    if (showTaskDialog) {
        AddTaskDialog(
            onDismiss = { showTaskDialog = false },
            onConfirm = { name, assignee, role, ins, isDone ->
                viewModel.addTask(name, assignee, role, ins, isDone)
                showTaskDialog = false
            }
        )
    }

    if (showMedDialog) {
        AddMedicineDialog(
            onDismiss = { showMedDialog = false },
            onConfirm = { name, dosage, duration ->
                viewModel.addMedicine(name, dosage, duration)
                showMedDialog = false
            }
        )
    }

    namingReportUri?.let { uri ->
        NameReportDialog(
            uri = uri,
            onDismiss = {
                namingReportUri = null
                reportNameQueue = emptyList()
            },
            onConfirm = { name ->
                reportUris = reportUris + NamedReport(uri, name)
                namingReportUri = reportNameQueue.firstOrNull()
                reportNameQueue = reportNameQueue.drop(1)
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("New Consultation", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Consultation",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Record symptoms, tasks, prescriptions, and follow-up timing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            VisitSection(title = "Consultation") {
                OutlinedTextField(
                    value = roomNo,
                    onValueChange = { roomNo = it },
                    label = { Text("Room / Bed") },
                    placeholder = { Text("132 or ICU3") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                OutlinedTextField(
                    value = symptoms,
                    onValueChange = { symptoms = it },
                    label = { Text("Symptoms") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = MaterialTheme.shapes.medium
                )

                OutlinedTextField(
                    value = diagnosis,
                    onValueChange = { diagnosis = it },
                    label = { Text("Diagnosis") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = MaterialTheme.shapes.medium
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Progress Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = MaterialTheme.shapes.medium
                )
            }

            VisitSection(
                title = "Clinical Tasks",
                actionText = "Add",
                onActionClick = { showTaskDialog = true }
            ) {
                if (tasks.isEmpty()) {
                    EmptySectionText("No clinical tasks added.")
                } else {
                    tasks.forEach { task ->
                        TaskItem(task = task, onDelete = { viewModel.removeTask(task) })
                    }
                }
            }

            VisitSection(
                title = "Prescriptions",
                actionText = "Add",
                onActionClick = { showMedDialog = true }
            ) {
                if (medicines.isEmpty()) {
                    EmptySectionText("No medicines prescribed.")
                } else {
                    medicines.forEach { med ->
                        MedicineItem(medicine = med, onDelete = { viewModel.removeMedicine(med) })
                    }
                }
            }

            VisitSection(
                title = "Reports",
                actionText = "Upload",
                onActionClick = { reportPicker.launch(arrayOf("image/*", "application/pdf")) }
            ) {
                if (reportUris.isEmpty()) {
                    EmptySectionText("Upload PDF, image, X-ray, or scan files for this visit.")
                } else {
                    reportUris.forEach { uri ->
                        PendingReportItem(
                            report = uri,
                            onDelete = { reportUris = reportUris - uri }
                        )
                    }
                }
            }

            VisitSection(title = "Next Appointment") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { datePickerDialog.show() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatAppDate(followUpDate).ifBlank { "Pick follow-up date" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = { timePickerDialog.show() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = followUpDate != null,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = formatAppTime(followUpTime).ifBlank { "Pick follow-up time" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (followUpDate == null) {
                        Text(
                            text = "Optional. Add this when the patient needs a reminder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        OutlinedTextField(
                            value = followUpReason,
                            onValueChange = { followUpReason = it },
                            label = { Text("Follow-up purpose") },
                            placeholder = { Text("Check blood report, review X-ray, wound dressing") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.saveVisit(
                        patientId = patientId,
                        roomNo = roomNo.trim(),
                        symptoms = symptoms.trim(),
                        diagnosis = diagnosis.trim(),
                        notes = notes.trim(),
                        followUpDate = followUpDate,
                        followUpTime = followUpTime,
                        followUpReason = followUpReason.trim(),
                        reportUris = reportUris.map { it.uri to it.displayName }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = symptoms.isNotBlank() || diagnosis.isNotBlank(),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Finalize & Save Visit", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

data class NamedReport(
    val uri: Uri,
    val displayName: String
)

@Composable
fun VisitSection(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (actionText != null && onActionClick != null) {
                    TextButton(onClick = onActionClick) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(actionText)
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun EmptySectionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun PendingReportItem(report: NamedReport, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = report.displayName.ifBlank { report.uri.lastPathSegment ?: "Selected report" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun NameReportDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(uri) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name Document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = uri.lastPathSegment ?: "Selected file",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    placeholder = { Text("Blood report, X-ray, discharge note") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim().ifBlank { "Medical report" }) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SectionHeader(title: String, onAddClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun TaskItem(task: TaskEntity, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = task.taskName,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(task.role, task.assignedTo)
                        .filter { it.isNotBlank() }
                        .joinToString(": ")
                        .ifBlank { "Unassigned" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusChip(task.status)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MedicineItem(medicine: MedicineEntity, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = medicine.name,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(medicine.dosage, medicine.duration)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                        .ifBlank { "Dosage not specified" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val done = status == "DONE"
    Surface(
        color = if (done) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (done) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var isDone by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Clinical Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Task") },
                    placeholder = { Text("Blood test") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = assignee,
                    onValueChange = { assignee = it },
                    label = { Text("Assignee") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Role") },
                    placeholder = { Text("Nurse / Lab") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isDone, onCheckedChange = { isDone = it })
                    Text("Mark as DONE immediately")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, assignee, role, "", isDone) }, enabled = name.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddMedicineDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prescribe Medicine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medicine") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosage") },
                    placeholder = { Text("1-0-1 after food") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duration") },
                    placeholder = { Text("5 days") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim(), dosage.trim(), duration.trim()) }, enabled = name.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
