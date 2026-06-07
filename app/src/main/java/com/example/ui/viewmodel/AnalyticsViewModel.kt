package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.usecase.GetAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AnalyticsUiState(
    val totalWishesSent: Int = 0,
    val totalContacts: Int = 0,
    val pendingApprovals: Int = 0,
    val upcomingEventsCount: Int = 0,
    val relationshipCounts: Map<String, Int> = emptyMap(),
    val healthCounts: Map<String, Int> = emptyMap(),
    /** Real monthly sent counts for the current year, index 0 = January */
    val monthlyCounts: List<Pair<String, Float>> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val getAnalyticsUseCase: GetAnalyticsUseCase,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val messageRepository: MessageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val monthAbbrevs = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    init {
        viewModelScope.launch {
            getAnalyticsUseCase(
                topHealthContactsProvider = { contactRepository.getTopByHealthScore(5) },
                neglectedContactsProvider = { contactRepository.getBottomByHealthScore(5) },
            ).collect { snapshot ->
                val allContacts = contactRepository.getAllSync()
                val healthyCount  = allContacts.count { it.healthScore >= 70 }
                val attentionCount = allContacts.count { it.healthScore in 30..69 }
                val atRiskCount   = allContacts.count { it.healthScore < 30 }
                val upcomingCount = eventRepository.getUpcoming(30).size

                // Monthly chart: group sent messages by month for current year
                val yearStartMs = Calendar.getInstance().apply {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val sentThisYear = messageRepository.getSentSinceYearStart(yearStartMs)
                val countsByMonth = IntArray(12)
                sentThisYear.forEach { msg ->
                    val cal = Calendar.getInstance().apply { timeInMillis = msg.sentAtMs }
                    val monthIndex = cal.get(Calendar.MONTH) // 0-based
                    countsByMonth[monthIndex]++
                }
                // Only include months up to current month for readability
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
                val monthlyCounts = (0..currentMonth).map { i ->
                    monthAbbrevs[i] to countsByMonth[i].toFloat()
                }

                _uiState.value = AnalyticsUiState(
                    totalWishesSent = snapshot.totalWishesSent,
                    totalContacts = snapshot.totalContacts,
                    pendingApprovals = snapshot.pendingApprovals,
                    upcomingEventsCount = upcomingCount,
                    relationshipCounts = snapshot.relationshipCounts.associate {
                        it.relationshipType to it.count
                    },
                    healthCounts = mapOf(
                        "Healthy (70%+)" to healthyCount,
                        "Needs Attention" to attentionCount,
                        "At Risk" to atRiskCount,
                    ),
                    monthlyCounts = monthlyCounts,
                    isLoading = false,
                )
            }
        }
    }
}
