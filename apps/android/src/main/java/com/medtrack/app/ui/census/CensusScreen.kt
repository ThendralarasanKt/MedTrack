package com.medtrack.app.ui.census

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.data.care.query.CensusPatient
import com.medtrack.app.ui.common.components.ChipTone
import com.medtrack.app.ui.common.components.FilterPill
import com.medtrack.app.ui.common.components.ScreenHeader
import com.medtrack.app.ui.common.components.StatusChip
import com.medtrack.app.ui.theme.LocalPaperColors

@Composable
fun CensusScreen(
    onAdmitClick: () -> Unit,
    onPatientClick: (String) -> Unit,
    onCaptureClick: () -> Unit,
    onRoundsClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    viewModel: CensusViewModel = hiltViewModel()
) {
    val colors = LocalPaperColors.current
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val grouped = remember(state.patients) {
        state.patients.groupBy { it.wardLabel }
            .toSortedMap(compareBy { if (it == "Unassigned") "zzz" else it })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Census",
            subtitle = "City Hospital • ${state.activeCount} active inpatients",
            actions = {
                IconButton(onClick = onAdmitClick) {
                    Surface(color = colors.highlight, shape = CircleShape) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Admit inpatient",
                            tint = colors.accent,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Surface(
                    color = colors.accent,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = onProfileClick)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            initials(state.clinicianName),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        )
        Column(
            modifier = Modifier
                .background(colors.surface)
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search patient, bed, diagnosis...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.searchFill,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = colors.accent
                )
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterPill("Active (${state.activeCount})", state.filter == CensusFilter.Active) {
                    viewModel.onFilterChange(CensusFilter.Active)
                }
                FilterPill("Referrals", state.filter == CensusFilter.Referrals) {
                    viewModel.onFilterChange(CensusFilter.Referrals)
                }
                FilterPill("On-Call", state.filter == CensusFilter.OnCall) {
                    viewModel.onFilterChange(CensusFilter.OnCall)
                }
                FilterPill("Discharged", state.filter == CensusFilter.Discharged) {
                    viewModel.onFilterChange(CensusFilter.Discharged)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.searchFill)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Grouped by floor & ward", style = MaterialTheme.typography.labelSmall, color = colors.body)
            Text(
                text = "${state.pendingWorkCount} tasks due",
                style = MaterialTheme.typography.labelSmall,
                color = colors.danger,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onRoundsClick)
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            if (state.patients.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            state.searchQuery.isNotBlank() -> "No patients found matching current filter."
                            state.filter == CensusFilter.Referrals -> "No referral inpatients yet."
                            state.filter == CensusFilter.OnCall -> "No on-call cover list for this shift."
                            state.filter == CensusFilter.Discharged -> "No discharged patients."
                            else -> "No inpatients on the census."
                        },
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    grouped.forEach { (ward, wardPatients) ->
                        item(key = "ward-$ward") {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    ward.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${wardPatients.size} patient(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                        items(wardPatients, key = { it.admissionId }) { patient ->
                            CensusPatientCard(patient) { onPatientClick(patient.admissionId) }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onCaptureClick,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                color = colors.searchFill
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    Text(
                        "Spoken rounds note or WhatsApp paste...",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1
                    )
                }
            }
            FloatingActionButton(
                onClick = onAdmitClick,
                containerColor = colors.accent,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Admit inpatient")
            }
        }
    }
}

@Composable
private fun CensusPatientCard(patient: CensusPatient, onClick: () -> Unit) {
    val colors = LocalPaperColors.current
    val acuity = when {
        patient.wardLabel.contains("ICU", ignoreCase = true) -> "High acuity" to ChipTone.Danger
        patient.openTaskCount > 0 -> "Watch" to ChipTone.Warning
        else -> "Stable" to ChipTone.Success
    }
    val bedTone = if (patient.wardLabel.contains("ICU", ignoreCase = true)) ChipTone.Warning else ChipTone.Info
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(patient.locationLabel, bedTone)
                    Text(patient.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                StatusChip(acuity.first, acuity.second)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(patient.reportedAge?.let { "${it}y" }, patient.involvementRole.replace('_', ' ').lowercase())
                    .joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
            Spacer(Modifier.height(10.dp))
            Surface(color = colors.background, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        if (patient.openTaskCount > 0) "${patient.openTaskCount} open task(s)" else "No open tasks",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (patient.openTaskCount > 0) colors.danger else colors.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        patient.nextTaskTitle ?: patient.problemSummary ?: "No active problem recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.body,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() && it != "Dr." && it != "Dr" }
    val letters = when {
        parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}"
        parts.size == 1 -> parts[0].take(2)
        else -> "ME"
    }
    return letters.uppercase()
}
