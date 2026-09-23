package com.medtrack.app.ui.aigate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.ui.assistant.AssistantViewModel
import com.medtrack.app.ui.common.components.ChipTone
import com.medtrack.app.ui.common.components.ScreenHeader
import com.medtrack.app.ui.common.components.StatusChip
import com.medtrack.app.ui.theme.LocalPaperColors

@Composable
fun AiGateScreen(
    onDiscard: () -> Unit,
    onConfirmed: () -> Unit = onDiscard,
    viewModel: AssistantViewModel = hiltViewModel()
) {
    val colors = LocalPaperColors.current
    val messages by viewModel.messages.collectAsState()
    val input by viewModel.input.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val proposals by viewModel.proposals.collectAsState()
    val commitError by viewModel.commitError.collectAsState()
    val lastAssistant = messages.lastOrNull { !it.fromUser }
    val selectedCount = proposals.count { it.selected }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "AI Quick Capture & Staging Gate",
            subtitle = viewModel.contextLabel + " Cloud proposals stay uncommitted until you confirm."
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = colors.surface, shadowElevation = 1.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Clinician input / dictation", fontWeight = FontWeight.Bold)
                        StatusChip("Review required", ChipTone.Danger)
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = viewModel::onInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("Spoken rounds note or WhatsApp paste...") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            viewModel.onInputChange(
                                "Patient transferred to ICU Bed 04. Stop Ceftriaxone. Start Meropenem 1g IV TDS. Check potassium in 4 hours."
                            )
                        }) { Text("ICU Transfer") }
                        OutlinedButton(onClick = {
                            viewModel.onInputChange(
                                "Nurse reports Bed 512 BP 180/100. Advise on nifedipine and next check."
                            )
                        }) { Text("Nurse Query") }
                        Button(onClick = viewModel::sendMessage, enabled = !isLoading && input.isNotBlank()) {
                            Text("Parse with AI")
                        }
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, colors.accent, MaterialTheme.shapes.medium)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PROPOSED CARE MODEL CHANGES", color = colors.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    Text(
                        "Writes stay off the record until you confirm. Discard leaves the database unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    if (isLoading) {
                        CircularProgressIndicator()
                    } else if (proposals.isNotEmpty()) {
                        proposals.forEach { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleProposal(item.proposal.toolCallId) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = item.selected,
                                    onCheckedChange = { viewModel.toggleProposal(item.proposal.toolCallId) }
                                )
                                Column(Modifier.padding(start = 4.dp)) {
                                    Text(item.proposal.toolName.replace('_', ' '), fontWeight = FontWeight.SemiBold)
                                    Text(item.proposal.summary, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                }
                            }
                        }
                    } else {
                        Text("Nothing staged yet. Parse a note to propose actions.", color = colors.textSecondary)
                    }
                    if (commitError != null) {
                        Text(commitError.orEmpty(), color = colors.danger, style = MaterialTheme.typography.bodySmall)
                    }
                    if (lastAssistant != null) {
                        Text(lastAssistant.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = {
                    viewModel.discardProposals()
                    onDiscard()
                },
                enabled = !isLoading
            ) { Text("Discard") }
            Button(
                onClick = { viewModel.confirmSelected(onConfirmed) },
                enabled = !isLoading && lastAssistant != null
            ) {
                Text(
                    if (selectedCount > 0) {
                        "Confirm $selectedCount change${if (selectedCount == 1) "" else "s"}"
                    } else {
                        "Confirm & return to census"
                    }
                )
            }
        }
    }
}
