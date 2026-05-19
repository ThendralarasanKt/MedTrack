package com.medtrack.app.ui.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.medtrack.app.data.db.model.PatientListItem
import com.medtrack.app.ui.theme.LocalPaperColors
import java.io.File

@Composable
fun PatientCard(
    patient: PatientListItem,
    onClick: () -> Unit
) {
    val paperColors = LocalPaperColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = paperColors.surface),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, paperColors.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PatientThumb(patient.photoPath, patient.name)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = patient.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.copy(color = paperColors.textPrimary),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    patient.latestRoomNo?.takeIf { it.isNotBlank() }?.let {
                        RoomTag(it)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${patient.age}y • ${patient.sex} • ID #${patient.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = paperColors.textSecondary
                )

                if (patient.medHistory.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = patient.medHistory,
                        style = MaterialTheme.typography.bodySmall,
                        color = paperColors.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PatientThumb(photoPath: String, name: String) {
    Surface(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape
    ) {
        if (photoPath.isNotBlank()) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = "$name photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.trim().firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalPaperColors.current.accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RoomTag(roomNo: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = roomNo.trim().uppercase(),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold
        )
    }
}
