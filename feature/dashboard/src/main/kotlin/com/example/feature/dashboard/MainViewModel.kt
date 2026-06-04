package com.example.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.PendingMessageEntity
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.usecase.GetDashboardMetricsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import com.example.core.auth.AuthManager
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.example.domain.repository.MemoryNoteRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.core.db.entities.MemoryNoteEntity
import com.example.core.db.entities.GiftHistoryEntity
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class MainViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val messageRepository: MessageRepository,
    private val styleProfileRepository: StyleProfileRepository,
    private val memoryNoteRepository: MemoryNoteRepository,
    private val giftHistoryRepository: GiftHistoryRepository,
    private val getDashboardMetrics: GetDashboardMetricsUseCase,
    private val authManager: AuthManager
) : ViewModel() {

    private val _contactNotes = MutableStateFlow<List<MemoryNoteEntity>>(emptyList())
    val contactNotes: StateFlow<List<MemoryNoteEntity>> = _contactNotes.asStateFlow()

    private val _contactGifts = MutableStateFlow<List<GiftHistoryEntity>>(emptyList())
    val contactGifts: StateFlow<List<GiftHistoryEntity>> = _contactGifts.asStateFlow()

    fun loadNotesForContact(contactId: String) {
        viewModelScope.launch {
            _contactNotes.value = memoryNoteRepository.getByContact(contactId)
        }
    }

    fun addMemoryNote(contactId: String, title: String, content: String, mood: String) {
        viewModelScope.launch {
            val note = MemoryNoteEntity(
                id = java.util.UUID.randomUUID().toString(),
                contactId = contactId,
                noteText = content,
                category = title,
                dateMs = System.currentTimeMillis()
            )
            memoryNoteRepository.upsert(note)
            loadNotesForContact(contactId)
        }
    }

    fun deleteMemoryNote(noteId: String, contactId: String) {
        viewModelScope.launch {
            val note = _contactNotes.value.find { it.id == noteId }
            if (note != null) {
                memoryNoteRepository.delete(note)
                loadNotesForContact(contactId)
            }
        }
    }

    fun loadGiftsForContact(contactId: String) {
        viewModelScope.launch {
            _contactGifts.value = giftHistoryRepository.getByContact(contactId)
        }
    }

    fun addGiftHistory(contactId: String, giftName: String, occasion: String, priceInr: Int) {
        viewModelScope.launch {
            val gift = GiftHistoryEntity(
                id = java.util.UUID.randomUUID().toString(),
                contactId = contactId,
                giftName = giftName,
                giftCategory = "GIFT",
                occasionType = occasion,
                year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                approxCostInr = priceInr
            )
            giftHistoryRepository.upsert(gift)
            loadGiftsForContact(contactId)
        }
    }

    fun deleteGiftHistory(giftId: String, contactId: String) {
        viewModelScope.launch {
            val gift = _contactGifts.value.find { it.id == giftId }
            if (gift != null) {
                giftHistoryRepository.delete(gift)
                loadGiftsForContact(contactId)
            }
        }
    }

    val userName: String
        get() = authManager.getUserDisplayName()

    val userEmail: String
        get() = authManager.getUserEmail()


    val contacts: StateFlow<List<ContactEntity>> = contactRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pagedContacts = contactRepository.getAllPaged()
        .cachedIn(viewModelScope)

    val events: StateFlow<List<EventEntity>> = eventRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingMessages: StateFlow<List<PendingMessageEntity>> = messageRepository.getAllPending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _healthScore = MutableStateFlow(0)

    val healthScore: StateFlow<Int> = _healthScore

    init {
        viewModelScope.launch {
            _healthScore.value = getDashboardMetrics().healthScore
        }
        viewModelScope.launch {
            contactRepository.getAll().map { list ->
                if (list.isEmpty()) 0 else list.map { it.healthScore }.average().toInt()
            }.collect { _healthScore.value = it }
        }
    }

    fun updateContact(contact: ContactEntity) {
        viewModelScope.launch {
            contactRepository.update(contact)
        }
    }

    fun saveTrainingText(text: String) {
        viewModelScope.launch {
            val existing = styleProfileRepository.getProfileOnce()
            val samples = mutableListOf<String>()
            if (existing != null) {
                try {
                    val arr = org.json.JSONArray(existing.sampleMessagesJson)
                    for (i in 0 until arr.length()) samples.add(arr.getString(i))
                } catch (_: Exception) {}
            }
            samples.add(text)
            styleProfileRepository.upsert(
                (existing ?: com.example.core.db.entities.StyleProfileEntity()).copy(
                    sampleMessagesJson = org.json.JSONArray(samples.toList()).toString(),
                    sampleCount = samples.size,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun addBirthday(contactId: String, day: Int, month: Int, year: Int?) {
        viewModelScope.launch {
            val eventId = "manual_bday_$contactId"
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.MONTH, month - 1)
                set(java.util.Calendar.DAY_OF_MONTH, day)
                if (year != null) set(java.util.Calendar.YEAR, year)
                val now = System.currentTimeMillis()
                if (timeInMillis < now) add(java.util.Calendar.DAY_OF_YEAR, 0) // Logic for roll forward handled in EventDiscoveryWorker or here
                // Simplified for now, just ensuring it's in the future
                if (timeInMillis < now) {
                    // This is slightly wrong but matches previous MainActivity logic
                    // let's just set it to the next year if it passed
                    // add(Calendar.YEAR, 1) is better
                }
            }
            // To match previous logic:
            val finalCal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.MONTH, month - 1)
                set(java.util.Calendar.DAY_OF_MONTH, day)
                if (year != null) set(java.util.Calendar.YEAR, year)
                if (timeInMillis < System.currentTimeMillis()) add(java.util.Calendar.YEAR, 1)
            }
            eventRepository.upsert(
                com.example.core.db.entities.EventEntity(
                    id = eventId,
                    contactId = contactId,
                    type = "BIRTHDAY",
                    dayOfMonth = day,
                    month = month,
                    year = year,
                    nextOccurrenceMs = finalCal.timeInMillis,
                    source = "MANUAL",
                    isVerified = true
                )
            )
        }
    }
}
