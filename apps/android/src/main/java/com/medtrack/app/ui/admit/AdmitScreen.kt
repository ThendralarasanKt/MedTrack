package com.medtrack.app.ui.admit

import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.medtrack.app.ui.common.components.FilterPill
import com.medtrack.app.ui.common.components.ScreenHeader
import com.medtrack.app.ui.theme.LocalPaperColors
import kotlinx.coroutines.flow.collectLatest

private val SexOptions = listOf("Male", "Female", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdmitScreen(
    onBack: () -> Unit,
    onAdmitted: (String) -> Unit,
    viewModel: AdmitViewModel = hiltViewModel()
) {
    val colors = LocalPaperColors.current
    val state by viewModel.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("Male") }
    var problem by remember { mutableStateOf("") }
    var bed by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        viewModel.admitted.collectLatest(onAdmitted)
    }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Admit inpatient",
            subtitle = "Saves through CareWritePath and opens the chart",
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
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("Patient full name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                age,
                { age = it },
                label = { Text("Age (years)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Sex", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SexOptions.forEach { option ->
                    FilterPill(option, sex == option) { sex = option }
                }
            }
            Text("Bed", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.beds.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { label ->
                            FilterPill(label, bed == label) { bed = label }
                        }
                    }
                }
            }
            OutlinedTextField(
                bed,
                { bed = it },
                label = { Text("Bed (or type a catalog label)") },
                placeholder = { Text("ICU Bed 04") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                problem,
                { problem = it },
                label = { Text("Primary diagnosis / presenting problem") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            if (state.error != null) {
                Text(state.error.orEmpty(), color = colors.danger, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { viewModel.admit(name, age, sex, problem, bed) },
                enabled = name.isNotBlank() && !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.saving) "Saving…" else "Save & open record")
            }
        }
    }
}
