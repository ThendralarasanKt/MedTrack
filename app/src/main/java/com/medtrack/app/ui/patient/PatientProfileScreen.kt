package com.medtrack.app.ui.patient

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.data.db.entity.PatientEntity
import com.medtrack.app.data.db.model.VisitHistorySummary
import com.medtrack.app.ui.common.PatientPhotoPickerDialog
import com.medtrack.app.ui.common.formatAppDate
import com.medtrack.app.ui.common.formatAppTime
import com.medtrack.app.ui.theme.LocalPaperColors
import coil.compose.AsyncImage
import java.io.File

/**
 * PatientProfileScreen: Refined with the "Classic Paper" aesthetic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientProfileScreen(
    patientId: Int,
    initialTab: Int = 0,
    onBack: () -> Unit,
    onNewVisitClick: (Int) -> Unit,
    onVisitClick: (Int) -> Unit,
    viewModel: PatientProfileViewModel = hiltViewModel()
) {
    val paperColors = LocalPaperColors.current
    val patient by viewModel.patient.collectAsState()
    val visits by viewModel.visits.collectAsState()
    var selectedTab by rememberSaveable(patientId) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    LaunchedEffect(patientId) {
        viewModel.loadPatientData(patientId)
    }

    Scaffold(
        containerColor = paperColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(patient?.name ?: "Patient Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = paperColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = paperColors.background,
                    titleContentColor = paperColors.textPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNewVisitClick(patientId) },
                containerColor = paperColors.accent,
                contentColor = Color.White,
                shape = MaterialTheme.shapes.medium // 16dp radius
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Visit")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = paperColors.background,
                contentColor = paperColors.accent,
                divider = { HorizontalDivider(color = paperColors.outline) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Profile Info", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Visit History (${visits.size})", fontWeight = FontWeight.Bold) }
                )
            }

            Box(modifier = Modifier.fillMaxSize().background(paperColors.background)) {
                when (selectedTab) {
                    0 -> patient?.let { ProfileInfoTab(it, onPhotoPicked = { uri ->
                        viewModel.updatePatientPhoto(patientId, uri)
                    }) }
                    1 -> VisitTimelineTab(visits, onVisitClick)
                }
            }
        }
    }
}

@Composable
fun ProfileInfoTab(patient: PatientEntity, onPhotoPicked: (android.net.Uri) -> Unit) {
    val paperColors = LocalPaperColors.current
    var showPhotoPicker by remember { mutableStateOf(false) }
    if (showPhotoPicker) {
        PatientPhotoPickerDialog(
            onDismiss = { showPhotoPicker = false },
            onPhotoPicked = onPhotoPicked
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        PaperInfoSection(title = "Photo") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PatientProfilePhoto(patient.photoPath)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = patient.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = paperColors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Use photo to avoid confusion with similar names or shared rooms.",
                        style = MaterialTheme.typography.bodySmall,
                        color = paperColors.textSecondary
                    )
                }
                OutlinedButton(onClick = { showPhotoPicker = true }) {
                    Text(if (patient.photoPath.isBlank()) "Add" else "Change")
                }
            }
        }

        PaperInfoSection(title = "Clinical Identity") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileRow(label = "Patient ID", value = "#${patient.id}")
                ProfileRow(label = "Age", value = "${patient.age} years")
                ProfileRow(label = "Sex", value = patient.sex)
                ProfileRow(label = "Contact", value = patient.contact.ifBlank { "None" })
            }
        }

        PaperInfoSection(title = "Permanent Medical History") {
            Text(
                text = patient.medHistory.ifBlank { "No permanent history recorded." },
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 22.sp,
                color = paperColors.textPrimary
            )
        }
    }
}

@Composable
private fun PatientProfilePhoto(photoPath: String) {
    Surface(
        modifier = Modifier
            .size(86.dp)
            .clip(CircleShape),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape
    ) {
        if (photoPath.isNotBlank()) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = "Patient photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Photo",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalPaperColors.current.textSecondary
                )
            }
        }
    }
}

@Composable
fun VisitTimelineTab(visits: List<VisitHistorySummary>, onVisitClick: (Int) -> Unit) {
    val paperColors = LocalPaperColors.current
    if (visits.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No consultations yet.", color = paperColors.textSecondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(visits) { visit ->
                VisitHistoryCard(visit = visit, onClick = { onVisitClick(visit.id) })
            }
        }
    }
}

@Composable
private fun VisitHistoryCard(visit: VisitHistorySummary, onClick: () -> Unit) {
    val paperColors = LocalPaperColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = paperColors.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, paperColors.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatAppDate(visit.visitDate),
                        fontWeight = FontWeight.Bold,
                        color = paperColors.accent,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = formatAppTime(visit.visitTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = paperColors.textSecondary
                    )
                }
                if (visit.roomNo.isNotBlank()) {
                    SmallInfoChip(visit.roomNo.trim().uppercase(), strong = true)
                }
            }

            Text(
                text = visit.diagnosis.ifBlank { "General observation" },
                maxLines = 2,
                style = MaterialTheme.typography.bodyMedium,
                color = paperColors.textPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallInfoChip("${visit.medicineCount} Rx")
                SmallInfoChip("${visit.doneTaskCount}/${visit.taskCount} Tasks")
                SmallInfoChip("${visit.documentCount} Docs")
            }

            visit.followUpStatus?.let {
                FollowUpHistoryChip(
                    status = it,
                    reason = visit.followUpReason.orEmpty(),
                    date = visit.followUpDate.orEmpty(),
                    time = visit.followUpTime.orEmpty()
                )
            }
        }
    }
}

@Composable
private fun SmallInfoChip(text: String, strong: Boolean = false) {
    val paperColors = LocalPaperColors.current
    Surface(
        color = if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, paperColors.outline),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (strong) MaterialTheme.colorScheme.onPrimaryContainer else paperColors.textSecondary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FollowUpHistoryChip(status: String, reason: String, date: String, time: String) {
    val label = buildString {
        append("Follow-up: ")
        append(status)
        if (date.isNotBlank()) append(" • ${formatAppDate(date)}")
        if (time.isNotBlank()) append(" ${formatAppTime(time)}")
        if (reason.isNotBlank()) append(" • $reason")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
    }
}

@Composable
fun PaperInfoSection(title: String, content: @Composable () -> Unit) {
    val paperColors = LocalPaperColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = paperColors.accent,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Surface(
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            color = paperColors.surface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, paperColors.outline)
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = LocalPaperColors.current.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, fontWeight = FontWeight.Bold, color = LocalPaperColors.current.textPrimary)
    }
}
