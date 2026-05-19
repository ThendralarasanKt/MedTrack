package com.medtrack.app.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun PatientPhotoPickerDialog(
    onDismiss: () -> Unit,
    onPhotoPicked: (Uri) -> Unit
) {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraLaunch by remember { mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let(onPhotoPicked)
        onDismiss()
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) {
            onPhotoPicked(uri)
        }
        onDismiss()
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingCameraLaunch) {
            val uri = createCameraImageUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            onDismiss()
        }
        pendingCameraLaunch = false
    }

    fun launchCamera() {
        pendingCameraLaunch = true
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val uri = createCameraImageUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
            pendingCameraLaunch = false
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Patient Photo") },
        text = { Text("Choose a photo from gallery or take a live picture now.") },
        confirmButton = {
            TextButton(
                onClick = { launchCamera() }
            ) {
                Text("Camera")
            }
        },
        dismissButton = {
            TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                Text("Gallery")
            }
        }
    )
}

private fun createCameraImageUri(context: Context): Uri {
    val directory = File(context.cacheDir, "camera")
    directory.mkdirs()
    val file = File(directory, "patient_photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}
