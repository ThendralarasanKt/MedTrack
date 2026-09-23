package com.medtrack.app.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.medtrack.app.data.care.query.InboxItem
import com.medtrack.app.ui.common.components.ChipTone
import com.medtrack.app.ui.common.components.ScreenHeader
import com.medtrack.app.ui.common.components.StatusChip
import com.medtrack.app.ui.theme.LocalPaperColors
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject

@Composable
fun InboxScreen(
    onOpenPatient: (String) -> Unit,
    viewModel: InboxViewModel = hiltViewModel()
) {
    val colors = LocalPaperColors.current
    val items by viewModel.items.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val error by viewModel.error.collectAsState()
    var linking by remember { mutableStateOf<InboxItem?>(null) }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(Unit) { viewModel.opened.collectLatest(onOpenPatient) }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Referral & Intake Triage",
            subtitle = "Informal requests • ${items.size} unresolved",
            actions = { StatusChip("Doctor's Inbox", ChipTone.Purple) }
        )
        if (items.isEmpty()) {
            Text(
                "No unassigned intake. WhatsApp and phone referrals will land here.",
                modifier = Modifier.padding(24.dp),
                color = colors.textSecondary
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.intake.id }) { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = colors.surface,
                        shadowElevation = 1.dp
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                StatusChip(sourceLabel(item.intake.identityHintsJson), ChipTone.Warning)
                                Text(item.intake.status, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                            }
                            Surface(color = colors.background, shape = MaterialTheme.shapes.small) {
                                Text(
                                    item.intake.summary,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            hintLine(item.intake.identityHintsJson)?.let { hints ->
                                Text(hints, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { linking = item }) { Text("Link to inpatient") }
                                OutlinedButton(onClick = { viewModel.dismiss(item.intake.id) }) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }
        }
    }

    linking?.let { item ->
        AlertDialog(
            onDismissRequest = { linking = null },
            title = { Text("Link to inpatient") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose the admission this request belongs to.", style = MaterialTheme.typography.bodySmall)
                    if (candidates.isEmpty()) {
                        Text("No active inpatients. Admit the patient first.", color = colors.textSecondary)
                    }
                    candidates.forEach { patient ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.link(item.intake.id, patient.admissionId)
                                    linking = null
                                },
                            shape = MaterialTheme.shapes.small,
                            color = colors.highlight
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(patient.displayName, fontWeight = FontWeight.Bold)
                                Text(
                                    "${patient.locationLabel} • ${patient.problemSummary ?: "See chart"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linking = null }) { Text("Cancel") } }
        )
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Inbox") },
            text = { Text(error.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }
}

private fun sourceLabel(hintsJson: String): String =
    runCatching { JSONObject(hintsJson).optString("source").ifBlank { "Unassigned intake" } }
        .getOrElse { "Unassigned intake" }
        .replaceFirstChar { it.uppercase() }

private fun hintLine(hintsJson: String): String? = runCatching {
    val json = JSONObject(hintsJson)
    listOfNotNull(
        json.optString("requester").takeIf { it.isNotBlank() }?.let { "Requester: $it" },
        json.optString("priority").takeIf { it.isNotBlank() }?.let { "Priority: $it" }
    ).takeIf { it.isNotEmpty() }?.joinToString(" • ")
}.getOrNull()
