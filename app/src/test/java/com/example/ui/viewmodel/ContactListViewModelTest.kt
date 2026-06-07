package com.example.ui.viewmodel

import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ContactListViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var contactRepository: ContactRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init collects contacts and clears loading`() = runTest(testDispatcher) {
        val contacts = listOf(
            ContactEntity(id = "c_1", name = "Alice"),
            ContactEntity(id = "c_2", name = "Bob")
        )
        every { contactRepository.getAll() } returns flowOf(contacts)

        val viewModel = ContactListViewModel(contactRepository)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.contacts.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `refresh flips isRefreshing and settles back to false`() = runTest(testDispatcher) {
        every { contactRepository.getAll() } returns flowOf(listOf(ContactEntity(id = "c_1", name = "Alice")))

        val viewModel = ContactListViewModel(contactRepository)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(1, viewModel.uiState.value.contacts.size)
    }
}
