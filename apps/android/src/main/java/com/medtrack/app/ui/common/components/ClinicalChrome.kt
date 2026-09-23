package com.medtrack.app.ui.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medtrack.app.ui.navigation.Screen
import com.medtrack.app.ui.theme.LocalPaperColors
import com.medtrack.app.ui.theme.PaperCustomColors

@Composable
fun ClinicalCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalPaperColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.outline),
        shadowElevation = 1.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (accent != null) {
                        Modifier.background(colors.surface)
                    } else {
                        Modifier
                    }
                )
        ) {
            if (accent != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(vertical = 10.dp)
                        .background(accent, RoundedCornerShape(2.dp))
                        .padding(start = 4.dp)
                )
            }
            Box(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    }
}

@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalPaperColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.highlight else Color.Transparent,
        contentColor = if (selected) colors.accent else colors.textSecondary
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
fun StatusChip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier
) {
    val colors = LocalPaperColors.current
    val (bg, fg) = tone.colors(colors)
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

enum class ChipTone {
    Info, Danger, Warning, Success, Purple, Neutral
}

private fun ChipTone.colors(colors: PaperCustomColors): Pair<Color, Color> = when (this) {
    ChipTone.Info -> colors.highlight to colors.accent
    ChipTone.Danger -> colors.dangerContainer to colors.danger
    ChipTone.Warning -> colors.warningContainer to colors.warning
    ChipTone.Success -> colors.successContainer to colors.success
    ChipTone.Purple -> colors.purpleContainer to colors.purple
    ChipTone.Neutral -> colors.searchFill to colors.textSecondary
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = LocalPaperColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

data class WorkspaceTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val WorkspaceTabs by lazy {
    listOf(
        WorkspaceTab(Screen.Census.route, "Census", Icons.Default.Person),
        WorkspaceTab(Screen.Rounds.route, "Rounds", Icons.Default.CheckCircle),
        WorkspaceTab(Screen.Capture.route, "AI Gate", Icons.Default.Star),
        WorkspaceTab(Screen.Inbox.route, "Inbox", Icons.Default.Email),
        WorkspaceTab(Screen.Handover.route, "Handover", Icons.Default.Share)
    )
}

@Composable
fun WorkspaceBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val colors = LocalPaperColors.current
    Surface(
        color = colors.surface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(color = colors.outline, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WorkspaceTabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    Column(
                        modifier = Modifier
                            .clickable { onNavigate(tab.route) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selected) colors.highlight else Color.Transparent,
                                    CircleShape
                                )
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) colors.accent else colors.textSecondary
                            )
                        }
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.accent else colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClinicalFormDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "Save",
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
