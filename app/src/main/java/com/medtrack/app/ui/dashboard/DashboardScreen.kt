package com.medtrack.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.ui.common.components.PatientCard
import com.medtrack.app.ui.followup.FollowUpScreen
import com.medtrack.app.ui.theme.LocalPaperColors

private val HeaderMessages = listOf(
    "Have a beautiful day.",
    "Are you ready to save a life today?",
    "A calm mind heals faster.",
    "Care is your superpower.",
    "Every patient remembers kindness.",
    "Your focus changes outcomes.",
    "Healing starts with listening.",
    "Small decisions create big recoveries.",
    "Compassion is clinical strength.",
    "You bring hope into every room.",
    "Steady hands, strong heart.",
    "Warriors do not always wear armor, sometimes they wear white coats.",
    "Today is another chance to heal.",
    "Your presence is medicine too.",
    "One more patient, one more difference."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddPatientClick: () -> Unit,
    onPatientClick: (Int) -> Unit,
    onFollowUpClick: (Int) -> Unit,
    onAssistantClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val patients by viewModel.patients.collectAsState()
    val dischargedPatients by viewModel.dischargedPatients.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val paperColors = LocalPaperColors.current
    val headerMessage = remember { HeaderMessages.random() }
    val visiblePatients = if (selectedTab == 2) dischargedPatients else patients

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Hey Doctor,",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = headerMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    TextButton(onClick = onAssistantClick) {
                        Text("AI")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = onAddPatientClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Patient")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = paperColors.outline) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Patients", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Follow-Up", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Discharged", fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedTab == 1) {
                FollowUpScreen(onFollowUpClick = onFollowUpClick)
                return@Column
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = if (selectedTab == 2) "Discharged Patients" else "Patient Records",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (selectedTab == 2) {
                        "${dischargedPatients.size} discharged patients"
                    } else {
                        "${patients.size} active patients"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Search by name, ID, contact, or room") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = paperColors.accent) },
                shape = MaterialTheme.shapes.large,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = paperColors.surface,
                    unfocusedContainerColor = paperColors.surface,
                    focusedBorderColor = paperColors.accent,
                    unfocusedBorderColor = paperColors.outline
                )
            )

            if (visiblePatients.isEmpty()) {
                EmptyDashboard(
                    isSearch = searchQuery.isNotEmpty(),
                    isDischarged = selectedTab == 2
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visiblePatients) { patient ->
                        PatientCard(
                            patient = patient,
                            onClick = { onPatientClick(patient.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyDashboard(isSearch: Boolean, isDischarged: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isSearch) {
                    "No matches found."
                } else if (isDischarged) {
                    "No discharged patients."
                } else {
                    "No records found."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = LocalPaperColors.current.textSecondary
            )
            if (!isSearch && !isDischarged) {
                Text(
                    text = "Tap + to start a new record.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalPaperColors.current.textSecondary
                )
            }
        }
    }
}
