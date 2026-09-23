package com.medtrack.app.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.ui.theme.LocalPaperColors
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    onBack: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val colors = LocalPaperColors.current
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selector = state.items.firstOrNull { it.id == state.selectorItemId }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 2
        }.distinctUntilChanged().collect { nearBottom ->
            viewModel.setNearBottom(nearBottom)
        }
    }

    LaunchedEffect(state.items.size, state.nearBottom) {
        if (state.nearBottom && state.items.isNotEmpty()) {
            listState.animateScrollToItem(state.items.lastIndex)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        ConversationHeader(state.activeScope.chip, onBack, onClearScope = viewModel::clearScope)
        BoxTimeline(
            modifier = Modifier.weight(1f),
            state = state,
            listState = listState,
            onAnswer = viewModel::answerCard,
            onChange = viewModel::changeAnswer,
            onApprove = viewModel::approveProposal,
            onDiscard = viewModel::discardProposal,
            onOpenSelector = viewModel::openSelector,
            onSchedule = viewModel::markReminderScheduled,
            onStarter = viewModel::applyStarter
        )
        if (state.showNewResponse) {
            TextButton(
                onClick = {
                    viewModel.jumpToLatest()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("New response") }
        }
        if (state.composerIntent == ComposerIntent.CHOOSE) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = viewModel::useComposerAsAnswer, modifier = Modifier.weight(1f)) {
                    Text("Use as answer")
                }
                OutlinedButton(onClick = viewModel::sendComposerAsNewMessage, modifier = Modifier.weight(1f)) {
                    Text("Send as new message")
                }
            }
        }
        ComposerBar(
            value = state.composer,
            enabled = state.composerEnabled,
            online = state.online,
            onChange = viewModel::onComposerChange,
            onSend = viewModel::sendFromComposer,
            onToggleOnline = { viewModel.setOnline(!state.online) }
        )
    }

    if (selector != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeSelector(null) },
            sheetState = sheetState
        ) {
            Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selector.text, fontWeight = FontWeight.Bold)
                selector.options.forEach { option ->
                    Text(
                        option,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.closeSelector(option) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationHeader(chip: String, onBack: () -> Unit, onClearScope: () -> Unit) {
    val colors = LocalPaperColors.current
    Column(Modifier.fillMaxWidth().background(colors.surface).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("MedTrack Assistant", fontWeight = FontWeight.Bold)
            Text(" ", modifier = Modifier.padding(8.dp))
        }
        Surface(shape = RoundedCornerShape(20.dp), color = colors.highlight) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(chip, color = colors.accent, fontWeight = FontWeight.SemiBold)
                if (chip != "All patients") {
                    TextButton(onClick = onClearScope) { Text("×") }
                }
            }
        }
    }
}

@Composable
private fun BoxTimeline(
    modifier: Modifier,
    state: ConversationState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onAnswer: (String, String) -> Unit,
    onChange: (String) -> Unit,
    onApprove: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onOpenSelector: (String) -> Unit,
    onSchedule: (String) -> Unit,
    onStarter: (String) -> Unit
) {
    if (state.items.isEmpty()) {
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Start a note")
            listOf("Add a patient", "Record an update", "Review pending work", "Attach a report").forEach { starter ->
                OutlinedButton(onClick = { onStarter(starter) }) { Text(starter) }
            }
        }
        return
    }
    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.items, key = { it.id }) { item ->
            TimelineBubble(
                item = item,
                onAnswer = onAnswer,
                onChange = onChange,
                onApprove = onApprove,
                onDiscard = onDiscard,
                onOpenSelector = onOpenSelector,
                onSchedule = onSchedule
            )
        }
    }
}

@Composable
private fun TimelineBubble(
    item: TimelineItem,
    onAnswer: (String, String) -> Unit,
    onChange: (String) -> Unit,
    onApprove: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onOpenSelector: (String) -> Unit,
    onSchedule: (String) -> Unit
) {
    val colors = LocalPaperColors.current
    val mine = item.kind == TimelineKind.DOCTOR
    val align = if (mine) Alignment.End else Alignment.Start
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Text(item.scope.chip, color = colors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Surface(
            color = if (mine) colors.highlight else colors.surface,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 1.dp
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when (item.kind) {
                        TimelineKind.DOCTOR -> "You"
                        TimelineKind.ANSWER -> "Answered"
                        TimelineKind.REVISION -> "Revision"
                        TimelineKind.PROPOSAL -> "Proposal"
                        TimelineKind.RECEIPT -> "Receipt"
                        TimelineKind.JOB -> "Report"
                        TimelineKind.CLARIFICATION -> "Question"
                        TimelineKind.ASSISTANT -> "MedTrack"
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (item.invalidated) colors.textSecondary else colors.textPrimary
                )
                Text(item.text, color = if (item.invalidated) colors.textSecondary else colors.textPrimary)
                if (item.offlineSaved) Text("Saved on device", color = colors.warning)
                if (item.waitingForConnection) Text("Waiting for connection", color = colors.warning)
                if (item.invalidated) Text("Needs a new answer", color = colors.danger)
                if (item.kind == TimelineKind.CLARIFICATION && item.expanded && !item.invalidated) {
                    if (item.largeSelector) {
                        Button(onClick = { onOpenSelector(item.id) }) { Text("Choose") }
                    } else {
                        item.options.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onAnswer(item.id, option) }) {
                                RadioButton(selected = false, onClick = { onAnswer(item.id, option) })
                                Text(option)
                            }
                        }
                    }
                }
                if (item.kind == TimelineKind.ANSWER && !item.invalidated) {
                    TextButton(onClick = { onChange(item.id) }) { Text("Change") }
                }
                if (item.kind == TimelineKind.PROPOSAL && item.expanded && !item.invalidated) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onApprove(item.id) }) { Text("Approve") }
                        OutlinedButton(onClick = { onDiscard(item.id) }) { Text("Discard") }
                    }
                }
                if (item.kind == TimelineKind.PROPOSAL && !item.expanded) {
                    Text(item.selectedLabel ?: "Closed", color = colors.textSecondary)
                }
                if (item.kind == TimelineKind.RECEIPT) {
                    Text(item.receiptLabel ?: "Saved locally", fontWeight = FontWeight.SemiBold, color = colors.success)
                    if (item.receiptLabel != "Reminder scheduled") {
                        TextButton(onClick = { onSchedule(item.id) }) { Text("Scheduling finished") }
                    }
                }
                if (item.kind == TimelineKind.JOB) {
                    Text("You can keep writing while this runs.", color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun ComposerBar(
    value: String,
    enabled: Boolean,
    online: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleOnline: () -> Unit
) {
    val colors = LocalPaperColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(onClick = onToggleOnline) { Text(if (online) "Online" else "Offline") }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message MedTrack…") },
            maxLines = 4
        )
        Button(onClick = onSend, enabled = enabled && value.isNotBlank()) { Text("Send") }
    }
}
