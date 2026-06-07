package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.MemoryNoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryVaultViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var contactRepository: ContactRepository

    @RelaxedMockK
    private lateinit var memoryNoteRepository: MemoryNoteRepository

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
    fun `loadData emits contact and notes`() = runTest(testDispatcher) {
        val contact = ContactEntity(id = "contact_1", name = "John Doe")
        val notes = listOf(
            MemoryNoteEntity(id = "note_1", contactId = "contact_1", noteText = "First Note", isPinned = false),
            MemoryNoteEntity(id = "note_2", contactId = "contact_1", noteText = "Pinned Note", isPinned = true)
        )

        coEvery { contactRepository.getById("contact_1") } returns contact
        coEvery { memoryNoteRepository.getByContact("contact_1") } returns notes

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = MemoryVaultViewModel(savedStateHandle, contactRepository, memoryNoteRepository)
        advanceUntilIdle()

        assertEquals(contact, viewModel.uiState.value.contact)
        assertEquals(2, viewModel.uiState.value.notes.size)
        // Check order: Pinned note should come first
        assertEquals("note_2", viewModel.uiState.value.notes[0].id)
        assertEquals("note_1", viewModel.uiState.value.notes[1].id)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }
}
