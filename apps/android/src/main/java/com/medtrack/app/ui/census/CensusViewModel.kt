package com.medtrack.app.ui.census

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.query.CareCensusQuery
import com.medtrack.app.data.care.query.CensusPatient
import com.medtrack.app.hybrid.account.AccountSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CensusFilter { Active, Referrals, OnCall, Discharged }

data class CensusUiState(
    val patients: List<CensusPatient> = emptyList(),
    val searchQuery: String = "",
    val filter: CensusFilter = CensusFilter.Active,
    val pendingWorkCount: Int = 0,
    val activeCount: Int = 0,
    val clinicianName: String = ""
)

@HiltViewModel
class CensusViewModel @Inject constructor(
    private val censusQuery: CareCensusQuery,
    private val accountSession: AccountSession
) : ViewModel() {

    private val owner get() = accountSession.ownerAccountId()
    private val _state = MutableStateFlow(CensusUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            load()
            delay(SEED_RETRY_MS)
            load()
        }
    }

    fun onSearchQueryChange(value: String) {
        _state.update { it.copy(searchQuery = value) }
        refresh()
    }

    fun onFilterChange(filter: CensusFilter) {
        _state.update { it.copy(filter = filter) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val active = censusQuery.censusPatients(owner, discharged = false)
        val discharged = censusQuery.censusPatients(owner, discharged = true)
        val query = _state.value.searchQuery.trim()
        val filter = _state.value.filter
        val source = when (filter) {
            CensusFilter.Discharged -> discharged
            CensusFilter.Active -> active.filter {
                it.involvementRole == CareEnums.InvolvementRole.PRIMARY_TEAM.name
            }.ifEmpty { active }
            CensusFilter.Referrals -> active.filter {
                it.involvementRole == CareEnums.InvolvementRole.REFERRAL.name
            }
            CensusFilter.OnCall -> active.filter {
                it.involvementRole == CareEnums.InvolvementRole.ON_CALL.name
            }
        }
        val visible = if (query.isBlank()) source else source.filter { patient ->
            patient.displayName.contains(query, ignoreCase = true) ||
                patient.locationLabel.contains(query, ignoreCase = true) ||
                patient.wardLabel.contains(query, ignoreCase = true) ||
                patient.problemSummary.orEmpty().contains(query, ignoreCase = true)
        }
        _state.update {
            it.copy(
                patients = visible,
                pendingWorkCount = censusQuery.openWorkQueue(owner).size,
                activeCount = active.size,
                clinicianName = accountSession.active()?.displayName.orEmpty()
            )
        }
    }

    companion object {
        private const val SEED_RETRY_MS = 1_000L
    }
}
