package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.service.AiService
import com.example.domain.service.GiftSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class GiftAdvisorUiState(
    val contact: ContactEntity? = null,
    val giftHistory: List<GiftHistoryEntity> = emptyList(),
    val suggestions: List<GiftSuggestion> = emptyList(),
    val totalSpentThisYear: Int = 0,
    val remainingBudget: Int = 0,
    val isLoading: Boolean = true,
    val isGeneratingSuggestions: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GiftAdvisorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val giftHistoryRepository: GiftHistoryRepository,
    private val aiService: AiService
) : ViewModel() {

    private val contactId: String = savedStateHandle.get<String>("contactId") ?: ""

    private val _uiState = MutableStateFlow(GiftAdvisorUiState())
    val uiState: StateFlow<GiftAdvisorUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val contact = contactRepository.getById(contactId)
                val history = giftHistoryRepository.getByContact(contactId)
                
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val spentThisYear = history.filter { it.year == currentYear }.sumOf { it.approxCostInr }
                val budget = contact?.giftBudgetInr ?: 500
                val remaining = (budget - spentThisYear).coerceAtLeast(0)

                _uiState.value = _uiState.value.copy(
                    contact = contact,
                    giftHistory = history.sortedByDescending { it.year },
                    totalSpentThisYear = spentThisYear,
                    remainingBudget = remaining,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Failed to load gift details"
                )
            }
        }
    }

    fun addGiftRecord(name: String, category: String, occasion: String, cost: Int, liked: Boolean?, notes: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val newGift = GiftHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    contactId = contactId,
                    giftName = name,
                    giftCategory = category,
                    occasionType = occasion,
                    year = currentYear,
                    approxCostInr = cost,
                    receivedWell = liked,
                    notes = notes
                )
                giftHistoryRepository.upsert(newGift)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage ?: "Failed to add gift record")
            }
        }
    }

    fun deleteGiftRecord(gift: GiftHistoryEntity) {
        viewModelScope.launch {
            try {
                giftHistoryRepository.delete(gift)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage ?: "Failed to delete gift record")
            }
        }
    }

    fun generateGiftSuggestions() {
        val contact = _uiState.value.contact ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingSuggestions = true, error = null)
            try {
                val suggestions = aiService.generateGiftSuggestions(contact, _uiState.value.giftHistory)
                _uiState.value = _uiState.value.copy(
                    suggestions = suggestions,
                    isGeneratingSuggestions = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingSuggestions = false,
                    error = e.localizedMessage ?: "Failed to generate AI gift suggestions"
                )
            }
        }
    }
}
