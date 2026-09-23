package com.medtrack.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.hybrid.account.AuthCoordinator
import com.medtrack.app.hybrid.profile.AffiliationDraft
import com.medtrack.app.hybrid.profile.CatalogueHit
import com.medtrack.app.hybrid.profile.ProfileDraft
import com.medtrack.app.hybrid.profile.ProfileRepository
import com.medtrack.app.hybrid.profile.ProfileView
import com.medtrack.app.hybrid.profile.SpecialtyDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val view: ProfileView = ProfileView(),
    val draft: ProfileDraft = ProfileDraft(),
    val specialtyQuery: String = "",
    val hospitalQuery: String = "",
    val specialtyHits: List<CatalogueHit> = emptyList(),
    val hospitalHits: List<CatalogueHit> = emptyList(),
    val extraSpecialtyText: String = "",
    val extraHospitalText: String = "",
    val workspaceUnlocked: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val coordinator: AuthCoordinator
) : ViewModel() {
    private val _state = MutableStateFlow(
        ProfileUiState(
            view = repository.cachedView() ?: ProfileView(),
            draft = repository.cachedView()?.draft ?: ProfileDraft(),
            workspaceUnlocked = coordinator.isWorkspaceUnlocked()
        )
    )
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val view = repository.refresh()
            _state.update {
                it.copy(
                    loading = false,
                    view = view,
                    draft = view.draft,
                    workspaceUnlocked = coordinator.isWorkspaceUnlocked()
                )
            }
        }
    }

    fun onDisplayName(value: String) = updateDraft { it.copy(displayName = value) }
    fun onPreferredName(value: String) = updateDraft { it.copy(preferredName = value) }
    fun onProfession(value: String) = updateDraft { it.copy(professionCode = value) }
    fun onLanguage(value: String) = updateDraft { it.copy(preferredLanguage = value) }
    fun onTimeZone(value: String) = updateDraft { it.copy(timeZoneId = value) }
    fun onDepartment(value: String) = updatePrimaryAffiliation { it.copy(departmentName = value) }
    fun onJobTitle(value: String) = updatePrimaryAffiliation { it.copy(jobTitle = value) }

    fun onSpecialtyQuery(value: String) {
        _state.update { it.copy(specialtyQuery = value) }
        viewModelScope.launch {
            val hits = repository.searchSpecialties(value)
            _state.update { it.copy(specialtyHits = hits) }
        }
    }

    fun onHospitalQuery(value: String) {
        _state.update { it.copy(hospitalQuery = value) }
        viewModelScope.launch {
            val hits = repository.searchHospitals(value)
            _state.update { it.copy(hospitalHits = hits) }
        }
    }

    fun chooseSpecialty(hit: CatalogueHit, primary: Boolean) {
        updateDraft { draft ->
            val remaining = draft.specialties.filterNot { it.specialtyId == hit.id }
            val next = remaining.map { if (primary) it.copy(isPrimary = false) else it } +
                SpecialtyDraft(specialtyId = hit.id, isPrimary = primary, label = hit.label)
            draft.copy(specialties = ensurePrimarySpecialty(next))
        }
        _state.update { it.copy(specialtyQuery = "", specialtyHits = emptyList()) }
    }

    fun addUnlistedSpecialty(text: String, primary: Boolean) {
        val label = text.trim()
        if (label.isBlank()) return
        updateDraft { draft ->
            val next = draft.specialties.map { if (primary) it.copy(isPrimary = false) else it } +
                SpecialtyDraft(reportedSpecialtyText = label, isPrimary = primary, label = label)
            draft.copy(specialties = ensurePrimarySpecialty(next))
        }
        _state.update { it.copy(extraSpecialtyText = "") }
    }

    fun chooseHospital(hit: CatalogueHit, primary: Boolean) {
        updateDraft { draft ->
            val remaining = draft.affiliations.filterNot { it.hospitalId == hit.id }
            val next = remaining.map { if (primary) it.copy(isPrimary = false) else it } +
                AffiliationDraft(hospitalId = hit.id, isPrimary = primary, label = hit.label)
            draft.copy(affiliations = ensurePrimaryAffiliation(next))
        }
        _state.update { it.copy(hospitalQuery = "", hospitalHits = emptyList()) }
    }

    fun addUnlistedHospital(text: String, primary: Boolean) {
        val label = text.trim()
        if (label.isBlank()) return
        updateDraft { draft ->
            val next = draft.affiliations.map { if (primary) it.copy(isPrimary = false) else it } +
                AffiliationDraft(reportedHospitalName = label, isPrimary = primary, label = label)
            draft.copy(affiliations = ensurePrimaryAffiliation(next))
        }
        _state.update { it.copy(extraHospitalText = "") }
    }

    fun onExtraSpecialtyText(value: String) = _state.update { it.copy(extraSpecialtyText = value) }
    fun onExtraHospitalText(value: String) = _state.update { it.copy(extraHospitalText = value) }

    fun removeSpecialty(label: String) {
        updateDraft { draft ->
            draft.copy(specialties = ensurePrimarySpecialty(draft.specialties.filterNot { it.label == label }))
        }
    }

    fun removeAffiliation(label: String) {
        updateDraft { draft ->
            draft.copy(affiliations = ensurePrimaryAffiliation(draft.affiliations.filterNot { it.label == label }))
        }
    }

    fun save() {
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val view = repository.save(_state.value.draft)
            _state.update {
                it.copy(
                    saving = false,
                    view = view,
                    draft = view.draft.takeIf { saved -> saved.displayName.isNotBlank() } ?: it.draft
                )
            }
        }
    }

    fun signOut() {
        coordinator.signOut()
    }

    private fun updateDraft(transform: (ProfileDraft) -> ProfileDraft) {
        _state.update { it.copy(draft = transform(it.draft), view = it.view.copy(error = null)) }
    }

    private fun updatePrimaryAffiliation(transform: (AffiliationDraft) -> AffiliationDraft) {
        updateDraft { draft ->
            val affiliations = draft.affiliations.toMutableList()
            val index = affiliations.indexOfFirst { it.isPrimary }
            if (index >= 0) {
                affiliations[index] = transform(affiliations[index])
            } else {
                affiliations += transform(AffiliationDraft(isPrimary = true, reportedHospitalName = "Not provided yet"))
            }
            draft.copy(affiliations = affiliations)
        }
    }

    private fun ensurePrimarySpecialty(items: List<SpecialtyDraft>): List<SpecialtyDraft> {
        if (items.isEmpty() || items.count { it.isPrimary } == 1) return items
        return items.mapIndexed { index, item -> item.copy(isPrimary = index == 0) }
    }

    private fun ensurePrimaryAffiliation(items: List<AffiliationDraft>): List<AffiliationDraft> {
        val current = items.filter { it.endsOn.isBlank() }
        if (current.isEmpty() || current.count { it.isPrimary } == 1) return items
        return items.mapIndexed { index, item -> item.copy(isPrimary = index == 0) }
    }
}
