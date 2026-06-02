package com.example.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.dao.RelationshipTypeCount
import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.usecase.GetAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val getAnalytics: GetAnalyticsUseCase
) : ViewModel() {

    private val topHealthContacts = MutableStateFlow<List<ContactEntity>>(emptyList())
    private val neglectedContacts = MutableStateFlow<List<ContactEntity>>(emptyList())

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState())

    init {
        viewModelScope.launch {
            getAnalytics(
                topHealthContactsProvider = { topHealthContacts.value },
                neglectedContactsProvider = { neglectedContacts.value }
            ).collect { snapshot ->
                _uiState.value = _uiState.value.copy(
                    totalWishesSent = snapshot.totalWishesSent,
                    pendingApprovals = snapshot.pendingApprovals,
                    totalContacts = snapshot.totalContacts,
                    relationshipCounts = snapshot.relationshipCounts,
                    topHealthContacts = snapshot.topHealthContacts,
                    neglectedContacts = snapshot.neglectedContacts
                )
            }
        }
        viewModelScope.launch {
            contactRepository.getAll().map { list ->
                if (list.isEmpty()) 0 else list.map { it.healthScore }.average().toInt()
            }.collect { avg ->
                _uiState.value = _uiState.value.copy(averageHealthScore = avg)
            }
        }
        refreshHealthContacts()
    }

    fun refreshHealthContacts() {
        viewModelScope.launch {
            topHealthContacts.value = contactRepository.getTopByHealthScore(5)
            neglectedContacts.value = contactRepository.getBottomByHealthScore(5)
        }
    }
}

data class AnalyticsUiState(
    val totalWishesSent: Int = 0,
    val pendingApprovals: Int = 0,
    val totalContacts: Int = 0,
    val averageHealthScore: Int = 0,
    val relationshipCounts: List<RelationshipTypeCount> = emptyList(),
    val topHealthContacts: List<ContactEntity> = emptyList(),
    val neglectedContacts: List<ContactEntity> = emptyList()
)
