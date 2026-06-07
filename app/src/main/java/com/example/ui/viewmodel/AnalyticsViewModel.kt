package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.usecase.GetAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsUiState(
    val totalWishesSent: Int = 0,
    val totalContacts: Int = 0,
    val pendingApprovals: Int = 0,
    val upcomingEventsCount: Int = 0,
    val relationshipCounts: Map<String, Int> = emptyMap(),
    val healthCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val getAnalyticsUseCase: GetAnalyticsUseCase,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getAnalyticsUseCase(
                topHealthContactsProvider = { contactRepository.getTopByHealthScore(5) },
                neglectedContactsProvider = { contactRepository.getBottomByHealthScore(5) },
            ).collect { snapshot ->
                val allContacts = contactRepository.getAllSync()
                val healthyCount = allContacts.count { it.healthScore >= 70 }
                val attentionCount = allContacts.count { it.healthScore in 30..69 }
                val atRiskCount = allContacts.count { it.healthScore < 30 }
                val upcomingCount = eventRepository.getUpcoming(30).size

                _uiState.value = AnalyticsUiState(
                    totalWishesSent = snapshot.totalWishesSent,
                    totalContacts = snapshot.totalContacts,
                    pendingApprovals = snapshot.pendingApprovals,
                    upcomingEventsCount = upcomingCount,
                    relationshipCounts = snapshot.relationshipCounts.associate { it.relationshipType to it.count },
                    healthCounts = mapOf(
                        "Healthy (70%+)" to healthyCount,
                        "Needs Attention" to attentionCount,
                        "At Risk" to atRiskCount
                    ),
                    isLoading = false,
                )
            }
        }
    }
}
