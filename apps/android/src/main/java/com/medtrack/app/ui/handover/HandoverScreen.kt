package com.medtrack.app.ui.handover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.ui.common.components.ChipTone
import com.medtrack.app.ui.common.components.ScreenHeader
import com.medtrack.app.ui.common.components.StatusChip
import com.medtrack.app.ui.theme.LocalPaperColors

@Composable
fun HandoverScreen(viewModel: HandoverViewModel = hiltViewModel()) {
    val colors = LocalPaperColors.current
    val high by viewModel.high.collectAsState()
    val stable by viewModel.stable.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Shift Handover Generator",
            subtitle = "Evening / night • auto-compiled",
            actions = { StatusChip("Auto-Compiled", ChipTone.Info) }
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
                    Text("HIGH ATTENTION (${high.size})", color = colors.danger, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    if (high.isEmpty()) Text("None.", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    high.forEach { patient ->
                        Text("${patient.locationLabel}: ${patient.displayName}", fontWeight = FontWeight.Bold)
                        Text(patient.problemSummary ?: "See chart", style = MaterialTheme.typography.bodySmall, color = colors.body)
                    }
                }
            }
            Surface(shape = MaterialTheme.shapes.medium, color = colors.surface, shadowElevation = 1.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("STABLE INPATIENTS (${stable.size})", color = colors.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    if (stable.isEmpty()) Text("None.", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    stable.forEach { patient ->
                        Text("${patient.locationLabel}: ${patient.displayName} • ${patient.problemSummary ?: "stable"}", style = MaterialTheme.typography.bodySmall)
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
            Button(onClick = viewModel::copyForWhatsApp, modifier = Modifier.weight(1f)) {
                Text("Copy for WhatsApp")
            }
            OutlinedButton(onClick = viewModel::share) { Text("Share") }
        }
    }
}
