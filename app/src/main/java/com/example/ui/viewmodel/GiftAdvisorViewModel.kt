package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.GiftHistoryId
import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.service.AiService
import com.example.domain.service.GiftSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

enum class GiftSuggestionBudgetStatus {
    UNKNOWN,
    WITHIN_REMAINING_BUDGET,
    OVER_REMAINING_BUDGET,
}

data class GiftSuggestionUiModel(
    val name: String,
    val reason: String,
    val estimatedCostInr: Int,
    val confidencePercent: Int = DEFAULT_SUGGESTION_CONFIDENCE_PERCENT,
    val budgetStatus: GiftSuggestionBudgetStatus = GiftSuggestionBudgetStatus.UNKNOWN,
    val budgetOverageInr: Int = 0,
    val duplicateGiftName: String? = null,
    val checkedAgainstHistory: Boolean = false,
) {
    fun toGiftSuggestion(): GiftSuggestion {
        return GiftSuggestion(
            name = name,
            reason = reason,
            estimatedCostInr = estimatedCostInr,
        )
    }

    companion object {
        const val DEFAULT_SUGGESTION_CONFIDENCE_PERCENT = 70
    }
}

data class GiftAdvisorUiState(
    val contact: ContactGiftAdvisorProfile? = null,
    val giftHistory: List<GiftHistoryRecord> = emptyList(),
    val suggestions: List<GiftSuggestionUiModel> = emptyList(),
    val totalSpentThisYear: Int = 0,
    val remainingBudget: Int = 0,
    val isLoading: Boolean = true,
    val isGeneratingSuggestions: Boolean = false,
    val errorMessageRes: Int? = null
)

private data class GiftAdvisorData(
    val contact: ContactGiftAdvisorProfile?,
    val history: List<GiftHistoryRecord>,
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
    private var loadJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessageRes = null)
            try {
                combine(
                    contactRepository.getGiftAdvisorProfileFlow(contactId),
                    giftHistoryRepository.getRecordsByContactFlow(contactId),
                ) { contact, history ->
                    GiftAdvisorData(contact = contact, history = history)
                }.collect { data ->
                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                    val spentThisYear = data.history
                        .filter { it.year == currentYear }
                        .sumOf { it.approxCostInr }
                    val budget = data.contact?.giftBudgetInr ?: 500
                    val remaining = (budget - spentThisYear).coerceAtLeast(0)
                    val sortedHistory = data.history.sortedByDescending { it.year }
                    val refreshedSuggestions = _uiState.value.suggestions
                        .map { enrichGiftSuggestion(it.toGiftSuggestion(), sortedHistory, remaining) }
                    _uiState.value = _uiState.value.copy(
                        contact = data.contact,
                        giftHistory = sortedHistory,
                        suggestions = refreshedSuggestions,
                        totalSpentThisYear = spentThisYear,
                        remainingBudget = remaining,
                        isLoading = false,
                        errorMessageRes = null,
                    )
                }
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
                val newGift = GiftHistoryRecord(
                    id = GiftHistoryId(UUID.randomUUID().toString()),
                    contactId = ContactId(contactId),
                    giftName = cleanedName,
                    giftCategory = cleanedCategory,
                    occasionType = cleanedOccasion,
                    year = currentYear,
                    approxCostInr = validatedCost,
                    receivedWell = liked,
                    notes = cleanedNotes
                )
                giftHistoryRepository.upsertRecord(newGift)
                _uiState.value = _uiState.value.copy(errorMessageRes = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessageRes = R.string.gift_advisor_error_add)
            }
        }
        return true
    }

    fun deleteGiftRecord(gift: GiftHistoryRecord) {
        viewModelScope.launch {
            try {
                giftHistoryRepository.deleteRecord(gift.id)
                _uiState.value = _uiState.value.copy(errorMessageRes = null)
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
                val currentState = _uiState.value
                val suggestions = aiService.generateGiftSuggestions(contact, currentState.giftHistory)
                    .map {
                        enrichGiftSuggestion(
                            suggestion = it,
                            giftHistory = currentState.giftHistory,
                            remainingBudget = currentState.remainingBudget,
                        )
                    }
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

    fun dismissGiftSuggestion(index: Int) {
        _uiState.update { state ->
            if (index !in state.suggestions.indices) {
                state
            } else {
                state.copy(
                    suggestions = state.suggestions.filterIndexed { suggestionIndex, _ ->
                        suggestionIndex != index
                    },
                    errorMessageRes = null,
                )
            }
        }
    }

    private fun enrichGiftSuggestion(
        suggestion: GiftSuggestion,
        giftHistory: List<GiftHistoryRecord>,
        remainingBudget: Int,
    ): GiftSuggestionUiModel {
        val duplicate = findPotentialDuplicate(suggestion.name, giftHistory)
        val safeCost = suggestion.estimatedCostInr.coerceAtLeast(0)
        val budgetStatus = when {
            safeCost <= 0 -> GiftSuggestionBudgetStatus.UNKNOWN
            safeCost <= remainingBudget -> GiftSuggestionBudgetStatus.WITHIN_REMAINING_BUDGET
            else -> GiftSuggestionBudgetStatus.OVER_REMAINING_BUDGET
        }
        val budgetOverage = (safeCost - remainingBudget).coerceAtLeast(0)
        val confidence = suggestionConfidencePercent(
            suggestion = suggestion,
            hasGiftHistory = giftHistory.isNotEmpty(),
            duplicateGiftName = duplicate?.giftName,
            budgetStatus = budgetStatus,
        )

        return GiftSuggestionUiModel(
            name = suggestion.name.trim().ifBlank { "Gift idea" },
            reason = suggestion.reason.trim(),
            estimatedCostInr = safeCost,
            confidencePercent = confidence,
            budgetStatus = budgetStatus,
            budgetOverageInr = budgetOverage,
            duplicateGiftName = duplicate?.giftName,
            checkedAgainstHistory = giftHistory.isNotEmpty(),
        )
    }

    private fun findPotentialDuplicate(
        suggestionName: String,
        giftHistory: List<GiftHistoryRecord>,
    ): GiftHistoryRecord? {
        val suggestionTokens = giftNameTokens(suggestionName)
        val suggestionKey = suggestionTokens.joinToString(" ")
        if (suggestionKey.isBlank()) return null

        return giftHistory.firstOrNull { gift ->
            val historyTokens = giftNameTokens(gift.giftName)
            val historyKey = historyTokens.joinToString(" ")
            if (historyKey.isBlank()) return@firstOrNull false

            val sharedTokens = suggestionTokens.intersect(historyTokens).size
            suggestionKey == historyKey ||
                (suggestionKey.length >= MIN_DUPLICATE_PHRASE_LENGTH &&
                    historyKey.length >= MIN_DUPLICATE_PHRASE_LENGTH &&
                    (suggestionKey.contains(historyKey) || historyKey.contains(suggestionKey))) ||
                sharedTokens >= MIN_DUPLICATE_SHARED_TOKENS ||
                (sharedTokens == 1 && minOf(suggestionTokens.size, historyTokens.size) == 1 &&
                    suggestionTokens.intersect(historyTokens).first().length >= MIN_SINGLE_TOKEN_DUPLICATE_LENGTH)
        }
    }

    private fun giftNameTokens(name: String): Set<String> {
        return NON_GIFT_NAME_TOKEN_CHARS
            .replace(name.lowercase(Locale.ROOT), " ")
            .split(" ")
            .mapNotNull { token ->
                token.trim()
                    .takeIf { it.length >= MIN_GIFT_NAME_TOKEN_LENGTH }
                    ?.takeUnless { it in GIFT_NAME_STOP_WORDS }
            }
            .toSet()
    }

    private fun suggestionConfidencePercent(
        suggestion: GiftSuggestion,
        hasGiftHistory: Boolean,
        duplicateGiftName: String?,
        budgetStatus: GiftSuggestionBudgetStatus,
    ): Int {
        var score = 60
        if (suggestion.reason.trim().length >= MIN_REASON_FOR_CONFIDENCE) score += 15
        if (hasGiftHistory) score += 10
        score += when (budgetStatus) {
            GiftSuggestionBudgetStatus.WITHIN_REMAINING_BUDGET -> 10
            GiftSuggestionBudgetStatus.OVER_REMAINING_BUDGET -> -15
            GiftSuggestionBudgetStatus.UNKNOWN -> 0
        }
        if (duplicateGiftName != null) score -= 30
        return score.coerceIn(MIN_SUGGESTION_CONFIDENCE_PERCENT, MAX_SUGGESTION_CONFIDENCE_PERCENT)
    }

    companion object {
        const val MAX_TEXT_FIELD_LENGTH = 80
        const val MAX_NOTES_LENGTH = 500
        const val MAX_GIFT_COST_INR = 10_000_000
        private const val MIN_GIFT_NAME_TOKEN_LENGTH = 3
        private const val MIN_DUPLICATE_PHRASE_LENGTH = 8
        private const val MIN_DUPLICATE_SHARED_TOKENS = 2
        private const val MIN_SINGLE_TOKEN_DUPLICATE_LENGTH = 5
        private const val MIN_REASON_FOR_CONFIDENCE = 20
        private const val MIN_SUGGESTION_CONFIDENCE_PERCENT = 20
        private const val MAX_SUGGESTION_CONFIDENCE_PERCENT = 95
        private val NON_GIFT_NAME_TOKEN_CHARS = Regex("[^\\p{L}\\p{N}]+")
        private val GIFT_NAME_STOP_WORDS = setOf(
            "the",
            "and",
            "for",
            "with",
            "gift",
            "set",
            "box",
            "kit",
            "pack",
        )

        fun parseCostInput(input: String): Int? {
            val normalized = input.trim().replace(",", "")
            if (normalized.isBlank() || !normalized.all { it.isDigit() }) return null
            val parsed = normalized.toLongOrNull() ?: return null
            if (parsed > MAX_GIFT_COST_INR) return null
            return parsed.toInt()
        }
    }
}
