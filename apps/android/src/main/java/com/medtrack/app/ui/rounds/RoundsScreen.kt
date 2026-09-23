package com.medtrack.app.ui.rounds

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.ui.common.components.ChipTone
import com.medtrack.app.ui.common.components.ClinicalFormDialog
import com.medtrack.app.ui.common.components.FilterPill
import com.medtrack.app.ui.common.components.ScreenHeader
import com.medtrack.app.ui.common.components.StatusChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.medtrack.app.ui.theme.LocalPaperColors

@Composable
fun RoundsScreen(
    onPatientClick: (String) -> Unit,
    viewModel: RoundsViewModel = hiltViewModel()
) {
    val colors = LocalPaperColors.current
    val items by viewModel.items.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val patients by viewModel.patients.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var taskTitle by remember { mutableStateOf("") }
    var selectedAdmission by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.refresh() }
    val grouped = remember(items, filter) { items.groupBy { it.wardLabel } }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Rounds Work Queue",
            subtitle = "Tasks by floor • ${items.size} pending",
            actions = {
                TextButton(onClick = {
                    selectedAdmission = patients.firstOrNull()?.admissionId
                    taskTitle = ""
                    showAdd = true
                }) { Text("+ New Task") }
                if (items.any { it.task.dueAt != null && it.task.dueAt!! < System.currentTimeMillis() }) {
                    StatusChip("Overdue", ChipTone.Danger)
                }
            }
        )
        Row(
            Modifier
                .background(colors.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterPill("By Floor / Ward", filter == RoundsFilter.Ward) { viewModel.onFilter(RoundsFilter.Ward) }
            FilterPill("By Due Time", filter == RoundsFilter.Time) { viewModel.onFilter(RoundsFilter.Time) }
            FilterPill("Awaiting Reports", filter == RoundsFilter.Awaiting) { viewModel.onFilter(RoundsFilter.Awaiting) }
        }
        if (items.isEmpty()) {
            Text(
                "No pending round tasks.",
                modifier = Modifier.padding(24.dp),
                color = colors.textSecondary
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                grouped.forEach { (ward, wardItems) ->
                    item(key = "w-$ward") {
                        Text(ward, color = colors.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    items(wardItems, key = { it.task.id }) { item ->
                        Surface(
                            onClick = { onPatientClick(item.task.admissionId) },
                            shape = MaterialTheme.shapes.medium,
                            color = colors.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    StatusChip(item.locationLabel, ChipTone.Info)
                                    Text(item.patientName, fontWeight = FontWeight.Bold)
                                }
                                Text(item.task.title, style = MaterialTheme.typography.bodyMedium)
                                item.task.instructions?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { viewModel.complete(item.task.id) }) { Text("Done + Note") }
                                    OutlinedButton(onClick = { viewModel.snooze(item.task.id) }) { Text("+2h Snooze") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        ClinicalFormDialog(
            title = "Add clinical task",
            onDismiss = { showAdd = false },
            confirmEnabled = taskTitle.isNotBlank() && selectedAdmission != null,
            onConfirm = {
                selectedAdmission?.let { viewModel.addTask(it, taskTitle) }
                showAdd = false
            }
        ) {
            OutlinedTextField(
                taskTitle,
                { taskTitle = it },
                label = { Text("Task title") },
                placeholder = { Text("Repeat potassium") }
            )
            Text("Assign to", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            patients.forEach { patient ->
                FilterPill(
                    "${patient.displayName} • ${patient.locationLabel}",
                    selectedAdmission == patient.admissionId
                ) { selectedAdmission = patient.admissionId }
            }
            if (patients.isEmpty()) {
                Text("Admit a patient first.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
