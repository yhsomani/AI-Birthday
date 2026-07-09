package com.example.ui.viewmodel

import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.service.GiftSuggestion
import java.util.Locale

internal data class GiftAdvisorData(
    val contact: ContactGiftAdvisorProfile?,
    val history: List<GiftHistoryRecord>,
)

internal fun GiftAdvisorUiState.withGiftAdvisorData(
    data: GiftAdvisorData,
    currentYear: Int,
): GiftAdvisorUiState {
    val spentThisYear = data.history
        .filter { it.year == currentYear }
        .sumOf { it.approxCostInr }
    val budget = data.contact?.giftBudgetInr ?: 500
    val remaining = (budget - spentThisYear).coerceAtLeast(0)
    val sortedHistory = data.history.sortedByDescending { it.year }
    val refreshedSuggestions = suggestions.map {
        enrichGiftSuggestion(
            suggestion = it.toGiftSuggestion(),
            giftHistory = sortedHistory,
            remainingBudget = remaining,
        )
    }
    return copy(
        contact = data.contact,
        giftHistory = sortedHistory,
        suggestions = refreshedSuggestions,
        totalSpentThisYear = spentThisYear,
        remainingBudget = remaining,
        isLoading = false,
        errorMessageRes = null,
    )
}

internal fun enrichGiftSuggestion(
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
