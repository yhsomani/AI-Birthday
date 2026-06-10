package com.example.ui.viewmodel

import com.example.core.db.entities.ActivityLogEntity
import com.example.domain.repository.ActivityLogRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
                    type = "MESSAGE",
                    title = "Message approved",
                    detail = "A message was approved.",
                    createdAtMs = now,
                ),
                ActivityLogEntity(
                    id = "a2",
                    type = "ANALYTICS",
                    title = "Report exported",
                    detail = "A report was generated.",
                    createdAtMs = now - 40 * 86_400_000L,
                ),
            )
        )

        val viewModel = ActivityHistoryViewModel(activityLogRepository)
        advanceUntilIdle()

        assertEquals(listOf("a1", "a2"), viewModel.uiState.value.entries.map { it.id })

        viewModel.selectTypeFilter(ActivityLogTypeFilter.MESSAGE)
        assertEquals(listOf("a1"), viewModel.uiState.value.entries.map { it.id })

        viewModel.selectTypeFilter(ActivityLogTypeFilter.ALL)
        viewModel.selectDateFilter(ActivityLogDateFilter.LAST_30_DAYS)
        assertEquals(listOf("a1"), viewModel.uiState.value.entries.map { it.id })
    }
}
