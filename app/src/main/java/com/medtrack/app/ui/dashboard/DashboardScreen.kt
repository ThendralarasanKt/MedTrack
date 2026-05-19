package com.medtrack.app.ui.dashboard

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.R
import com.medtrack.app.ui.common.components.PatientCard
import com.medtrack.app.ui.followup.FollowUpScreen
import com.medtrack.app.ui.theme.LocalPaperColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddPatientClick: () -> Unit,
    onPatientClick: (Int) -> Unit,
    onFollowUpClick: (Int) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val patients by viewModel.patients.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val paperColors = LocalPaperColors.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Using the logo image provided by user
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = "MedTrack Logo",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "MedTrack", 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            letterSpacing = 1.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = onAddPatientClick,
                    containerColor = MaterialTheme.colorScheme.primary, // #8B5E3C Accent
                    contentColor = Color.White,
                    shape = MaterialTheme.shapes.medium // 16dp radius
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
            }

            if (selectedTab == 1) {
                FollowUpScreen(onFollowUpClick = onFollowUpClick)
                return@Column
            }

            // Patients Header
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = "Medical Records",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${patients.size} active patients",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Search Bar with Paper Aesthetics
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Search by name or ID") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = paperColors.accent) },
                shape = MaterialTheme.shapes.medium, // 16dp radius
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = paperColors.surface,
                    unfocusedContainerColor = paperColors.surface,
                    focusedBorderColor = paperColors.accent,
                    unfocusedBorderColor = paperColors.outline
                )
            )

            if (patients.isEmpty()) {
                EmptyDashboard(isSearch = searchQuery.isNotEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(patients) { patient ->
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
fun EmptyDashboard(isSearch: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isSearch) "No matches found." else "No records found.",
                style = MaterialTheme.typography.bodyLarge,
                color = LocalPaperColors.current.textSecondary
            )
            if (!isSearch) {
                Text(
                    text = "Tap + to start a new record.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalPaperColors.current.textSecondary
                )
            }
        }
    }
}
