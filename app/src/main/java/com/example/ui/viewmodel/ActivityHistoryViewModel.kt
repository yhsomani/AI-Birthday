package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.ActivityLogEntity
import com.example.domain.repository.ActivityLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ActivityLogTypeFilter {
    ALL,
    MESSAGE,
    EVENT,
    SYNC,
    ANALYTICS,
    SETTINGS,
    AI,
}

enum class ActivityLogDateFilter {
    ALL,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
}

enum class ActivityLogStatusFilter {
    ALL,
    OPEN,
    RESOLVED,
}

data class ActivityHistoryUiState(
    val allEntries: List<ActivityLogEntity> = emptyList(),
    val entries: List<ActivityLogEntity> = emptyList(),
    val selectedTypeFilter: ActivityLogTypeFilter = ActivityLogTypeFilter.ALL,
    val selectedDateFilter: ActivityLogDateFilter = ActivityLogDateFilter.ALL,
    val selectedStatusFilter: ActivityLogStatusFilter = ActivityLogStatusFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
)

@HiltViewModel
class ActivityHistoryViewModel @Inject constructor(
    private val activityLogRepository: ActivityLogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityHistoryUiState())
    val uiState: StateFlow<ActivityHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            activityLogRepository.getRecent(100).collect { entries ->
                _uiState.value = _uiState.value.copy(
                    allEntries = entries,
                    isLoading = false,
                ).withFilteredEntries()
            }
        }
    }

    fun selectTypeFilter(filter: ActivityLogTypeFilter) {
        _uiState.value = _uiState.value.copy(selectedTypeFilter = filter).withFilteredEntries()
    }

    fun selectDateFilter(filter: ActivityLogDateFilter) {
        _uiState.value = _uiState.value.copy(selectedDateFilter = filter).withFilteredEntries()
    }

    fun selectStatusFilter(filter: ActivityLogStatusFilter) {
        _uiState.value = _uiState.value.copy(selectedStatusFilter = filter).withFilteredEntries()
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query).withFilteredEntries()
    }

    private fun ActivityHistoryUiState.withFilteredEntries(): ActivityHistoryUiState {
        val cutoffMs = selectedDateFilter.cutoffMs()
        val query = searchQuery.trim()
        val filtered = allEntries
            .asSequence()
            .filter { entry ->
                selectedTypeFilter == ActivityLogTypeFilter.ALL ||
                    entry.type.equals(selectedTypeFilter.name, ignoreCase = true)
            }
            .filter { entry ->
                selectedStatusFilter == ActivityLogStatusFilter.ALL ||
                    entry.status.equals(selectedStatusFilter.name, ignoreCase = true)
            }
            .filter { entry -> cutoffMs == null || entry.createdAtMs >= cutoffMs }
            .filter { entry ->
                query.isBlank() ||
                    entry.title.contains(query, ignoreCase = true) ||
                    entry.detail.contains(query, ignoreCase = true) ||
                    entry.type.contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.createdAtMs }
            .toList()
        return copy(entries = filtered)
    }

    private fun ActivityLogDateFilter.cutoffMs(): Long? {
        val now = System.currentTimeMillis()
        return when (this) {
            ActivityLogDateFilter.ALL -> null
            ActivityLogDateFilter.TODAY -> now - 86_400_000L
            ActivityLogDateFilter.LAST_7_DAYS -> now - 7 * 86_400_000L
            ActivityLogDateFilter.LAST_30_DAYS -> now - 30 * 86_400_000L
        }
    }
}
