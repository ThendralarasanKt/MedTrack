package com.medtrack.app.ui.patient

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.ui.common.PatientPhotoPickerDialog
import com.medtrack.app.ui.theme.LocalPaperColors
import kotlinx.coroutines.flow.collectLatest
import coil.compose.AsyncImage

/**
 * AddPatientScreen: Refined with the "Classic Paper" aesthetic.
 * 
 * WHY: Clean inputs and high-contrast typography reduce cognitive load 
 * when entering new patient data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPatientScreen(
    onBack: () -> Unit,
    viewModel: AddPatientViewModel = hiltViewModel()
) {
    val paperColors = LocalPaperColors.current
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("Male") }
    var contact by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var history by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoPicker by remember { mutableStateOf(false) }
    
    var showSexMenu by remember { mutableStateOf(false) }
    val sexOptions = listOf("Male", "Female", "Other")

    LaunchedEffect(Unit) {
        viewModel.saveSuccess.collectLatest { success ->
            if (success) onBack()
        }
    }

    if (showPhotoPicker) {
        PatientPhotoPickerDialog(
            onDismiss = { showPhotoPicker = false },
            onPhotoPicked = { photoUri = it }
        )
    }

    Scaffold(
        containerColor = paperColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Registration", fontWeight = FontWeight.Bold) },
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "PATIENT IDENTITY",
                style = MaterialTheme.typography.labelLarge,
                color = paperColors.accent,
                fontWeight = FontWeight.Bold
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = paperColors.surface,
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, paperColors.outline)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PatientPhotoPreview(uri = photoUri)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Patient Photo",
                            style = MaterialTheme.typography.titleMedium,
                            color = paperColors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Optional now. You can add or change it later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = paperColors.textSecondary
                        )
                    }
                    OutlinedButton(onClick = { showPhotoPicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (photoUri == null) "Add" else "Change")
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium, // 16dp radius
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = paperColors.accent,
                    unfocusedBorderColor = paperColors.outline,
                    focusedContainerColor = paperColors.surface,
                    unfocusedContainerColor = paperColors.surface,
                    focusedLabelColor = paperColors.accent,
                    unfocusedLabelColor = paperColors.textSecondary
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = age,
                    onValueChange = { if (it.all { c -> c.isDigit() }) age = it },
                    label = { Text("Age") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = paperColors.accent,
                        unfocusedBorderColor = paperColors.outline,
                        focusedContainerColor = paperColors.surface,
                        unfocusedContainerColor = paperColors.surface
                    )
                )

                ExposedDropdownMenuBox(
                    expanded = showSexMenu,
                    onExpandedChange = { showSexMenu = !showSexMenu },
                    modifier = Modifier.weight(1.2f)
                ) {
                    OutlinedTextField(
                        value = sex,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sex") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSexMenu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = paperColors.accent,
                            unfocusedBorderColor = paperColors.outline,
                            focusedContainerColor = paperColors.surface,
                            unfocusedContainerColor = paperColors.surface
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showSexMenu,
                        onDismissRequest = { showSexMenu = false },
                        modifier = Modifier.background(paperColors.surface)
                    ) {
                        sexOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, color = paperColors.textPrimary) },
                                onClick = {
                                    sex = option
                                    showSexMenu = false
                                },
                                modifier = Modifier.background(paperColors.surface)
                            )
                        }
                    }
                }
            }

            Text(
                text = "CLINICAL CONTEXT",
                style = MaterialTheme.typography.labelLarge,
                color = paperColors.accent,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = paperColors.accent,
                    unfocusedBorderColor = paperColors.outline,
                    focusedContainerColor = paperColors.surface,
                    unfocusedContainerColor = paperColors.surface
                )
            )

            OutlinedTextField(
                value = history,
                onValueChange = { history = it },
                label = { Text("Known Allergies or Conditions") },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = paperColors.accent,
                    unfocusedBorderColor = paperColors.outline,
                    focusedContainerColor = paperColors.surface,
                    unfocusedContainerColor = paperColors.surface
                )
            )

            Button(
                onClick = { viewModel.savePatient(name, age, sex, contact, address, history, photoUri) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = name.isNotBlank() && age.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = paperColors.accent,
                    contentColor = Color.White
                )
            ) {
                Text("SAVE RECORD", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun PatientPhotoPreview(uri: Uri?) {
    val paperColors = LocalPaperColors.current
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = "Patient photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Photo",
                    style = MaterialTheme.typography.labelSmall,
                    color = paperColors.textSecondary
                )
            }
        }
    }
}
