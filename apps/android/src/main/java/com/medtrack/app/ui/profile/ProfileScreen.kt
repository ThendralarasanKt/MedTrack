package com.medtrack.app.ui.profile

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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.hybrid.profile.AffiliationDraft
import com.medtrack.app.hybrid.profile.SpecialtyDraft
import com.medtrack.app.ui.common.components.ChipTone
import com.medtrack.app.ui.common.components.ScreenHeader
import com.medtrack.app.ui.common.components.StatusChip
import com.medtrack.app.ui.theme.LocalPaperColors

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val colors = LocalPaperColors.current
    val state by viewModel.state.collectAsState()
    val view = state.view
    Column(Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "My Profile",
            subtitle = view.syncStatus,
            actions = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (view.onboardingStatus != "COMPLETE") {
                Text(
                    if (state.workspaceUnlocked) {
                        "Add your specialty and hospital when you can. Missing details do not lock you out of patient care."
                    } else {
                        "Complete your professional details. Care records and AI stay disabled until the server grants access."
                    },
                    color = colors.textSecondary
                )
            }
            if (view.stale || view.conflict || view.error != null) {
                StatusChip(
                    text = when {
                        view.conflict -> "Conflict"
                        view.stale -> "Offline"
                        else -> "Error"
                    },
                    tone = if (view.conflict) ChipTone.Warning else ChipTone.Danger
                )
            }
            if (view.error != null) {
                Text(view.error!!, color = colors.danger)
            }
            SectionTitle("Identity")
            OutlinedTextField(
                state.draft.displayName,
                viewModel::onDisplayName,
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                state.draft.preferredName,
                viewModel::onPreferredName,
                label = { Text("Preferred name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                view.email.orEmpty().ifBlank { "Signed-in email is managed by Google" },
                {},
                label = { Text("Signed-in email") },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                state.draft.professionCode,
                viewModel::onProfession,
                label = { Text("Profession") },
                modifier = Modifier.fillMaxWidth()
            )
            SectionTitle("Professional details")
            OutlinedTextField(
                state.specialtyQuery,
                viewModel::onSpecialtyQuery,
                label = { Text("Search specialties") },
                modifier = Modifier.fillMaxWidth()
            )
            state.specialtyHits.forEach { hit ->
                Text(
                    hit.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.chooseSpecialty(hit, primary = state.draft.specialties.none { it.isPrimary }) }
                        .padding(vertical = 6.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    state.extraSpecialtyText,
                    viewModel::onExtraSpecialtyText,
                    label = { Text("Not listed specialty") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { viewModel.addUnlistedSpecialty(state.extraSpecialtyText, primary = true) }) {
                    Text("Add")
                }
            }
            state.draft.specialties.forEach { row -> SpecialtyRow(row, viewModel::removeSpecialty) }
            OutlinedTextField(
                state.hospitalQuery,
                viewModel::onHospitalQuery,
                label = { Text("Search hospitals") },
                modifier = Modifier.fillMaxWidth()
            )
            state.hospitalHits.forEach { hit ->
                Text(
                    listOf(hit.label, hit.detail).filter { it.isNotBlank() }.joinToString(" • "),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.chooseHospital(hit, primary = state.draft.affiliations.none { it.isPrimary }) }
                        .padding(vertical = 6.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    state.extraHospitalText,
                    viewModel::onExtraHospitalText,
                    label = { Text("Not listed hospital") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { viewModel.addUnlistedHospital(state.extraHospitalText, primary = true) }) {
                    Text("Add")
                }
            }
            state.draft.affiliations.forEach { row -> AffiliationRow(row, viewModel::removeAffiliation) }
            OutlinedTextField(
                state.draft.affiliations.firstOrNull { it.isPrimary }?.departmentName.orEmpty(),
                viewModel::onDepartment,
                label = { Text("Department") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                state.draft.affiliations.firstOrNull { it.isPrimary }?.jobTitle.orEmpty(),
                viewModel::onJobTitle,
                label = { Text("Job title") },
                modifier = Modifier.fillMaxWidth()
            )
            SectionTitle("Preferences")
            OutlinedTextField(
                state.draft.preferredLanguage,
                viewModel::onLanguage,
                label = { Text("Language") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                state.draft.timeZoneId,
                viewModel::onTimeZone,
                label = { Text("Time zone") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Changing time zone does not reinterpret previously scheduled clinical times.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            SectionTitle("Membership")
            Text(view.accessLabel, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    view.grantLabels.any { it.contains("Pilot", ignoreCase = true) } ->
                        "Pilot access is recorded separately from a paid subscription."
                    view.subscriptionLabel != null -> "Paid subscription: ${view.subscriptionLabel}"
                    else -> "No paid subscription. Status is set by the server."
                },
                color = colors.textSecondary
            )
            if (view.features.isNotEmpty()) {
                Text("Features: ${view.features.joinToString()}", color = colors.textSecondary)
            }
            SectionTitle("Account")
            Text("Patient records stay on this device. Sign-out does not erase local history.")
            Button(
                onClick = viewModel::save,
                enabled = !state.saving && !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.saving) "Saving…" else "Save profile")
            }
            OutlinedButton(
                onClick = {
                    viewModel.signOut()
                    onSignedOut()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalPaperColors.current
    HorizontalDivider(color = colors.outline)
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SpecialtyRow(row: SpecialtyDraft, onRemove: (String) -> Unit) {
    val colors = LocalPaperColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            buildString {
                append(row.label.ifBlank { "Specialty" })
                if (row.isPrimary) append(" • Primary")
                if (row.reportedSpecialtyText != null) append(" • Not listed")
            }
        )
        Text("Remove", color = colors.accent, modifier = Modifier.clickable { onRemove(row.label) })
    }
}

@Composable
private fun AffiliationRow(row: AffiliationDraft, onRemove: (String) -> Unit) {
    val colors = LocalPaperColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            buildString {
                append(row.label.ifBlank { "Hospital" })
                if (row.isPrimary) append(" • Primary")
                if (row.reportedHospitalName != null) append(" • Not listed")
            }
        )
        Text("Remove", color = colors.accent, modifier = Modifier.clickable { onRemove(row.label) })
    }
}
