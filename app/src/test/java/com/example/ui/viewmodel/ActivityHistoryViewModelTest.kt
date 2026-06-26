package com.example.ui.viewmodel

import com.example.core.db.entities.ActivityLogEntity
import com.example.R
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ActivityLogType
import com.example.domain.repository.ActivityLogRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityHistoryViewModelTest {
    private val activityLogRepository: ActivityLogRepository = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `type and date filters are applied in viewmodel`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        every { activityLogRepository.getRecent(100) } returns MutableStateFlow(
            listOf(
                ActivityLogEntity(
                    id = "a1",
                    type = ActivityLogType.MESSAGE.raw,
                    title = "Message approved",
                    detail = "A message was approved.",
                    createdAtMs = now,
                ),
                ActivityLogEntity(
                    id = "a2",
                    type = ActivityLogType.MESSAGE.raw,
                    title = "Dispatch deferred",
                    detail = "Message waits until scheduled time.",
                    metadataJson = "{\"decision\":\"deferred\"}",
                    createdAtMs = now - 1_000L,
                ),
                ActivityLogEntity(
                    id = "a3",
                    type = ActivityLogType.BACKUP.raw,
                    title = "Backup exported",
                    detail = "Encrypted backup created.",
                    createdAtMs = now - 2_000L,
                ),
                ActivityLogEntity(
                    id = "a4",
                    type = ActivityLogType.ANALYTICS.raw,
                    title = "Report exported",
                    detail = "A report was generated.",
                    createdAtMs = now - 40 * 86_400_000L,
                ),
            )
        )

        val viewModel = ActivityHistoryViewModel(activityLogRepository)
        advanceUntilIdle()

        assertEquals(listOf("a1", "a2", "a3", "a4"), viewModel.uiState.value.entries.map { it.id })

        viewModel.selectTypeFilter(ActivityLogTypeFilter.MESSAGE)
        assertEquals(listOf("a1", "a2"), viewModel.uiState.value.entries.map { it.id })

        viewModel.selectTypeFilter(ActivityLogTypeFilter.DISPATCH)
        assertEquals(listOf("a2"), viewModel.uiState.value.entries.map { it.id })

        viewModel.selectTypeFilter(ActivityLogTypeFilter.BACKUP)
        assertEquals(listOf("a3"), viewModel.uiState.value.entries.map { it.id })

        viewModel.selectTypeFilter(ActivityLogTypeFilter.ALL)
        viewModel.selectDateFilter(ActivityLogDateFilter.LAST_30_DAYS)
        assertEquals(listOf("a1", "a2", "a3"), viewModel.uiState.value.entries.map { it.id })
    }

    @Test
    fun `search matches severity status and action route`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        every { activityLogRepository.getRecent(100) } returns MutableStateFlow(
            listOf(
                ActivityLogEntity(
                    id = "a1",
                    type = ActivityLogType.SYNC.raw,
                    title = "Contacts refreshed",
                    detail = "Background sync completed.",
                    severity = ActivityLogSeverity.ERROR.raw,
                    status = ActivityLogStatus.RESOLVED.raw,
                    actionRoute = "settings",
                    createdAtMs = now,
                ),
                ActivityLogEntity(
                    id = "a2",
                    type = ActivityLogType.MESSAGE.raw,
                    title = "Message approved",
                    detail = "Approval completed.",
                    createdAtMs = now - 1_000L,
                ),
            )
        )

        val viewModel = ActivityHistoryViewModel(activityLogRepository)
        advanceUntilIdle()

        viewModel.updateSearchQuery("error")
        assertEquals(listOf("a1"), viewModel.uiState.value.entries.map { it.id })

        viewModel.updateSearchQuery("resolved")
        assertEquals(listOf("a1"), viewModel.uiState.value.entries.map { it.id })

        viewModel.updateSearchQuery("settings")
        assertEquals(listOf("a1"), viewModel.uiState.value.entries.map { it.id })
    }

    @Test
    fun `repository failure exposes load error state`() = runTest(dispatcher) {
        every { activityLogRepository.getRecent(100) } returns flow<List<ActivityLogEntity>> {
            throw IllegalStateException("database unavailable")
        }

        val viewModel = ActivityHistoryViewModel(activityLogRepository)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(R.string.activity_history_error_load, viewModel.uiState.value.errorMessageRes)
        assertEquals(emptyList<String>(), viewModel.uiState.value.entries.map { it.id })
    }
}
