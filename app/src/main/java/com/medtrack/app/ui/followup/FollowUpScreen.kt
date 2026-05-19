package com.medtrack.app.ui.followup

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.data.db.model.FollowUpWithPatient
import com.medtrack.app.ui.common.formatAppDate
import com.medtrack.app.ui.common.formatAppTime
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpScreen(
    onFollowUpClick: (Int) -> Unit,
    viewModel: FollowUpViewModel = hiltViewModel()
) {
    val followUps by viewModel.followUps.collectAsState()
    val grouped = rememberFollowUpGroups(followUps)
    var editingFollowUp by remember { mutableStateOf<FollowUpWithPatient?>(null) }

    editingFollowUp?.let { followUp ->
        EditFollowUpDialog(
            followUp = followUp,
            onDismiss = { editingFollowUp = null },
            onSave = { date, time, reason ->
                viewModel.updateFollowUp(followUp, date, time, reason)
                editingFollowUp = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Follow-Ups",
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "${followUps.size} scheduled appointments",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (followUps.isEmpty()) {
            EmptyFollowUps()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                grouped.forEach { group ->
                    if (group.items.isNotEmpty()) {
                        item(key = group.title) {
                            SectionTitle(group.title)
                        }
                        items(group.items, key = { it.id }) { followUp ->
                            FollowUpCard(
                                followUp = followUp,
                                onClick = { onFollowUpClick(followUp.visitId) },
                                onEditClick = { editingFollowUp = followUp },
                                onDoneClick = { viewModel.markDone(followUp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFollowUps() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "No follow-ups scheduled",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Follow-ups added during a visit will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun FollowUpCard(
    followUp: FollowUpWithPatient,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDoneClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = followUp.patientName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Column(horizontalAlignment = Alignment.End) {
                    RoomTag(roomNo = followUp.roomNo)
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatAppTime(followUp.scheduledTime),
                                modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = onEditClick) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit follow-up",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = formatAppDate(followUp.scheduledDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = followUp.reason.ifBlank { "Follow-up appointment" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDoneClick) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun RoomTag(roomNo: String) {
    val label = roomNo.trim().uppercase()
    if (label.isBlank()) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EditFollowUpDialog(
    followUp: FollowUpWithPatient,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    var date by remember(followUp.id) { mutableStateOf(followUp.scheduledDate) }
    var time by remember(followUp.id) { mutableStateOf(followUp.scheduledTime.take(5)) }
    var reason by remember(followUp.id) { mutableStateOf(followUp.reason) }

    val parsedDate = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
    val parsedTime = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(8, 0))

    val datePicker = remember(date) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                date = LocalDate.of(year, month + 1, dayOfMonth).toString()
            },
            parsedDate.year,
            parsedDate.monthValue - 1,
            parsedDate.dayOfMonth
        )
    }

    val timePicker = remember(time) {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                time = LocalTime.of(hour, minute).toString().take(5)
            },
            parsedTime.hour,
            parsedTime.minute,
            false
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Follow-Up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = followUp.patientName,
                    onValueChange = {},
                    label = { Text("Patient") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { datePicker.show() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(formatAppDate(date))
                    }
                    Button(
                        onClick = { timePicker.show() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(formatAppTime(time))
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(date, time, reason) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private data class FollowUpGroup(
    val title: String,
    val items: List<FollowUpWithPatient>
)

@Composable
private fun rememberFollowUpGroups(followUps: List<FollowUpWithPatient>): List<FollowUpGroup> {
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val weekEnd = today.plusDays(7)

    fun dateOf(followUp: FollowUpWithPatient): LocalDate? =
        runCatching { LocalDate.parse(followUp.scheduledDate) }.getOrNull()

    return listOf(
        FollowUpGroup("Overdue", followUps.filter { dateOf(it)?.isBefore(today) == true }),
        FollowUpGroup("Today", followUps.filter { dateOf(it) == today }),
        FollowUpGroup("Tomorrow", followUps.filter { dateOf(it) == tomorrow }),
        FollowUpGroup(
            "This Week",
            followUps.filter {
                val date = dateOf(it)
                date != null && date.isAfter(tomorrow) && !date.isAfter(weekEnd)
            }
        ),
        FollowUpGroup("Later", followUps.filter { dateOf(it)?.isAfter(weekEnd) == true })
    )
}
