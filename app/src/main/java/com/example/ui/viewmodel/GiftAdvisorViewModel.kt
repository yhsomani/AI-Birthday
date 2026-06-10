package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
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
import kotlinx.coroutines.flow.update
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
    val errorMessageRes: Int? = null
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
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessageRes = null)
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
                    isLoading = false,
                    errorMessageRes = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessageRes = R.string.gift_advisor_error_load
                )
            }
        }
    }

    fun addGiftRecord(
        name: String,
        category: String,
        occasion: String,
        costInput: String,
        liked: Boolean?,
        notes: String
    ): Boolean {
        val cost = parseCostInput(costInput)
        return addGiftRecordInternal(name, category, occasion, cost, liked, notes)
    }

    fun addGiftRecord(name: String, category: String, occasion: String, cost: Int, liked: Boolean?, notes: String) {
        addGiftRecordInternal(name, category, occasion, cost.takeIf { it in 0..MAX_GIFT_COST_INR }, liked, notes)
    }

    private fun addGiftRecordInternal(
        name: String,
        category: String,
        occasion: String,
        cost: Int?,
        liked: Boolean?,
        notes: String
    ): Boolean {
        val cleanedName = name.trim()
        val cleanedCategory = category.trim()
        val cleanedOccasion = occasion.trim()
        val cleanedNotes = notes.trim()
        val validationError = when {
            cleanedName.isBlank() -> R.string.gift_advisor_error_missing_gift_name
            cleanedCategory.isBlank() -> R.string.gift_advisor_error_missing_category
            cleanedOccasion.isBlank() -> R.string.gift_advisor_error_missing_occasion
            cost == null -> R.string.gift_advisor_error_invalid_cost
            cleanedName.length > MAX_TEXT_FIELD_LENGTH -> R.string.gift_advisor_error_text_too_long
            cleanedCategory.length > MAX_TEXT_FIELD_LENGTH -> R.string.gift_advisor_error_text_too_long
            cleanedOccasion.length > MAX_TEXT_FIELD_LENGTH -> R.string.gift_advisor_error_text_too_long
            cleanedNotes.length > MAX_NOTES_LENGTH -> R.string.gift_advisor_error_notes_too_long
            else -> null
        }
        if (validationError != null) {
            _uiState.update { it.copy(errorMessageRes = validationError) }
            return false
        }
        val validatedCost = cost ?: return false

        viewModelScope.launch {
            try {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val newGift = GiftHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    contactId = contactId,
                    giftName = cleanedName,
                    giftCategory = cleanedCategory,
                    occasionType = cleanedOccasion,
                    year = currentYear,
                    approxCostInr = validatedCost,
                    receivedWell = liked,
                    notes = cleanedNotes
                )
                giftHistoryRepository.upsert(newGift)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessageRes = R.string.gift_advisor_error_add)
            }
        }
        return true
    }

    fun deleteGiftRecord(gift: GiftHistoryEntity) {
        viewModelScope.launch {
            try {
                giftHistoryRepository.delete(gift)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessageRes = R.string.gift_advisor_error_delete)
            }
        }
    }

    fun generateGiftSuggestions() {
        val contact = _uiState.value.contact
        if (contact == null) {
            _uiState.update { it.copy(errorMessageRes = R.string.gift_advisor_error_missing_contact) }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingSuggestions = true, errorMessageRes = null)
            try {
                val suggestions = aiService.generateGiftSuggestions(contact, _uiState.value.giftHistory)
                _uiState.value = _uiState.value.copy(
                    suggestions = suggestions,
                    isGeneratingSuggestions = false,
                    errorMessageRes = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingSuggestions = false,
                    errorMessageRes = R.string.gift_advisor_error_suggestions
                )
            }
        }
    }

    companion object {
        const val MAX_TEXT_FIELD_LENGTH = 80
        const val MAX_NOTES_LENGTH = 500
        const val MAX_GIFT_COST_INR = 10_000_000

        fun parseCostInput(input: String): Int? {
            val normalized = input.trim().replace(",", "")
            if (normalized.isBlank() || !normalized.all { it.isDigit() }) return null
            val parsed = normalized.toLongOrNull() ?: return null
            if (parsed > MAX_GIFT_COST_INR) return null
            return parsed.toInt()
        }
    }
}
