package com.medtrack.app.ui.rounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.data.care.command.AssignTaskRequest
import com.medtrack.app.hybrid.account.AccountSession
import com.medtrack.app.data.care.command.CareWritePath
import com.medtrack.app.data.care.command.RespondToTaskRequest
import com.medtrack.app.data.care.entity.CareTaskEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.query.CareCensusQuery
import com.medtrack.app.data.care.query.CensusPatient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RoundsTaskItem(
    val task: CareTaskEntity,
    val patientName: String,
    val locationLabel: String,
    val wardLabel: String
)

enum class RoundsFilter { Ward, Time, Awaiting }

@HiltViewModel
class RoundsViewModel @Inject constructor(
    private val censusQuery: CareCensusQuery,
    private val writePath: CareWritePath,
    private val accountSession: AccountSession
) : ViewModel() {
    private val owner get() = accountSession.ownerAccountId()
    private val workspace get() = accountSession.workspace()

    private val _items = MutableStateFlow<List<RoundsTaskItem>>(emptyList())
    val items = _items.asStateFlow()
    private val _filter = MutableStateFlow(RoundsFilter.Ward)
    val filter = _filter.asStateFlow()

    private val _patients = MutableStateFlow<List<CensusPatient>>(emptyList())
    val patients = _patients.asStateFlow()

    init { refresh() }

    fun onFilter(filter: RoundsFilter) {
        _filter.value = filter
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val tasks = censusQuery.openWorkQueue(owner)
            val hydrated = tasks.mapNotNull { task ->
                val hub = censusQuery.patientHub(task.admissionId) ?: return@mapNotNull null
                RoundsTaskItem(task, hub.census.displayName, hub.census.locationLabel, hub.census.wardLabel)
            }
            _items.value = when (_filter.value) {
                RoundsFilter.Ward -> hydrated.sortedBy { it.wardLabel }
                RoundsFilter.Time -> hydrated.sortedBy { it.task.dueAt ?: Long.MAX_VALUE }
                RoundsFilter.Awaiting -> hydrated.filter {
                    it.task.kind == CareEnums.CareTaskKind.INVESTIGATION.name ||
                        it.task.kind == CareEnums.CareTaskKind.RESULT_REVIEW.name ||
                        it.task.title.contains("lab", true) ||
                        it.task.title.contains("report", true)
                }
            }
            _patients.value = censusQuery.censusPatients(owner, discharged = false)
        }
    }

    fun complete(taskId: String) {
        viewModelScope.launch {
            runCatching {
                writePath.work.respondToTask(
                    workspace,
                    RespondToTaskRequest(
                        taskId = taskId,
                        action = CareEnums.TaskResponseAction.COMPLETE,
                    )
                )
            }
            refresh()
        }
    }

    fun addTask(admissionId: String, title: String) {
        viewModelScope.launch {
            runCatching {
                writePath.work.assignTask(
                    workspace,
                    AssignTaskRequest(
                        admissionId = admissionId,
                        title = title.trim(),
                        followUpOwnerPersonId = workspace.actorPersonId,
                        dueAt = System.currentTimeMillis() + 4 * 60 * 60 * 1000,
                        dueZoneId = accountSession.timeZone(),
                        scheduleReminder = true
                    )
                )
            }
            refresh()
        }
    }

    fun snooze(taskId: String) {
        viewModelScope.launch {
            runCatching {
                writePath.work.respondToTask(
                    workspace,
                    RespondToTaskRequest(
                        taskId = taskId,
                        action = CareEnums.TaskResponseAction.RESCHEDULE,
                        newDueAt = System.currentTimeMillis() + 2 * 60 * 60 * 1000
                    )
                )
            }
            refresh()
        }
    }
}
