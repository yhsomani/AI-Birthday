package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.db.entities.ActivityLogEntity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ActivityLogType
import com.example.domain.repository.ActivityLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ActivityLogTypeFilter {
    ALL,
    DISPATCH,
    AI,
    SYNC,
    BACKUP,
    SETTINGS,
    MESSAGE,
    EVENT,
    ANALYTICS,
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
    val errorMessageRes: Int? = null,
)

@HiltViewModel
class ActivityHistoryViewModel @Inject constructor(
    private val activityLogRepository: ActivityLogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityHistoryUiState())
    val uiState: StateFlow<ActivityHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            activityLogRepository.getRecent(100)
                .catch {
                    _uiState.value = _uiState.value.copy(
                        allEntries = emptyList(),
                        entries = emptyList(),
                        isLoading = false,
                        errorMessageRes = R.string.activity_history_error_load,
                    )
                }
                .collect { entries ->
                    _uiState.value = _uiState.value.copy(
                        allEntries = entries,
                        isLoading = false,
                        errorMessageRes = null,
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
                selectedTypeFilter.matches(entry)
            }
            .filter { entry ->
                selectedStatusFilter == ActivityLogStatusFilter.ALL ||
                    ActivityLogStatus.fromRaw(entry.status).raw == selectedStatusFilter.name
            }
            .filter { entry -> cutoffMs == null || entry.createdAtMs >= cutoffMs }
            .filter { entry ->
                query.isBlank() ||
                    entry.title.contains(query, ignoreCase = true) ||
                    entry.detail.contains(query, ignoreCase = true) ||
                    entry.type.contains(query, ignoreCase = true) ||
                    entry.severity.contains(query, ignoreCase = true) ||
                    entry.status.contains(query, ignoreCase = true) ||
                    entry.actionRoute.orEmpty().contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.createdAtMs }
            .toList()
        return copy(entries = filtered)
    }

    private fun ActivityLogTypeFilter.matches(entry: ActivityLogEntity): Boolean {
        if (this == ActivityLogTypeFilter.ALL) return true
        val type = ActivityLogType.fromRaw(entry.type)
        return when (this) {
            ActivityLogTypeFilter.ALL -> true
            ActivityLogTypeFilter.DISPATCH -> type == ActivityLogType.DISPATCH ||
                (type == ActivityLogType.MESSAGE && entry.metadataJson.contains("\"decision\"", ignoreCase = true))
            ActivityLogTypeFilter.BACKUP -> type == ActivityLogType.BACKUP ||
                entry.title.containsBackupKeyword() ||
                entry.detail.containsBackupKeyword() ||
                entry.actionRoute.orEmpty().containsBackupKeyword()
            else -> type.raw == name
        }
    }

    private fun String.containsBackupKeyword(): Boolean {
        return contains("backup", ignoreCase = true) ||
            contains("restore", ignoreCase = true)
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
