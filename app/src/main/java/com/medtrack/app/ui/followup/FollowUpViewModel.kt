package com.medtrack.app.ui.followup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.data.db.model.FollowUpWithPatient
import com.medtrack.app.data.repository.ClinicalRepository
import com.medtrack.app.notification.FollowUpNotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class FollowUpViewModel @Inject constructor(
    private val repository: ClinicalRepository,
    private val followUpNotificationScheduler: FollowUpNotificationScheduler
) : ViewModel() {
    val followUps: StateFlow<List<FollowUpWithPatient>> =
        repository.getAllFollowUpsWithPatients()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun updateFollowUp(
        followUp: FollowUpWithPatient,
        scheduledDate: String,
        scheduledTime: String,
        reason: String
    ) {
        viewModelScope.launch {
            followUpNotificationScheduler.cancel(followUp.id)
            repository.updateFollowUp(
                followUp.toEntity().copy(
                    scheduledDate = scheduledDate,
                    scheduledTime = scheduledTime,
                    reason = reason,
                    status = "PENDING",
                    isNotified = false,
                    notifiedAt = null,
                    completedAt = null
                )
            )
            followUpNotificationScheduler.schedule(followUp.id, scheduledDate, scheduledTime)
        }
    }

    fun markDone(followUp: FollowUpWithPatient) {
        viewModelScope.launch {
            followUpNotificationScheduler.cancel(followUp.id)
            repository.markFollowUpDone(followUp.id, LocalDateTime.now().toString())
        }
    }
}
