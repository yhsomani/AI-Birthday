package com.example.domain.event

import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionConflictKind
import com.example.domain.model.occasion.OccasionType
import java.util.Locale

typealias EventConflictKind = OccasionConflictKind

object EventResolutionPolicy {
    private const val KEEP_SEPARATE_SOURCE_SUFFIX = "_SEPARATE"

    fun conflictStates(occasions: List<Occasion>): Map<String, EventConflictKind> {
        val states = mutableMapOf<String, EventConflictKind>()
        occasions
            .filter { it.isActive && !isMarkedKeepSeparate(it) }
            .groupBy { it.conflictGroupKey() }
            .values
            .forEach { groupedOccasions ->
                val dates = groupedOccasions.map { OccasionConflictDate(it.date.month, it.date.dayOfMonth) }.toSet()
                val conflict = when {
                    dates.size > 1 -> EventConflictKind.DATE_CONFLICT
                    groupedOccasions.size > 1 -> EventConflictKind.DUPLICATE
                    else -> EventConflictKind.NONE
                }
                if (conflict != EventConflictKind.NONE) {
                    groupedOccasions.forEach { occasion ->
                        states[occasion.id.value] = conflict
                    }
                }
            }
        return states
    }

    fun conflictGroupFor(occasions: List<Occasion>, selectedOccasion: Occasion): List<Occasion> {
        val selectedKey = selectedOccasion.conflictGroupKey()
        return occasions.filter { occasion ->
            occasion.isActive && occasion.conflictGroupKey() == selectedKey
        }
    }

    fun baseSource(source: String): String {
        val normalized = source.trim().uppercase(Locale.US)
        return if (normalized.endsWith(KEEP_SEPARATE_SOURCE_SUFFIX)) {
            normalized.removeSuffix(KEEP_SEPARATE_SOURCE_SUFFIX)
        } else {
            source.trim()
        }
    }

    fun keepSeparateSource(source: String): String {
        val base = baseSource(source).ifBlank { "UNKNOWN" }.uppercase(Locale.US)
        return "$base$KEEP_SEPARATE_SOURCE_SUFFIX"
    }

    fun isMarkedKeepSeparate(occasion: Occasion): Boolean {
        return occasion.source.trim().uppercase(Locale.US).endsWith(KEEP_SEPARATE_SOURCE_SUFFIX)
    }

    fun isSourceConflict(occasion: Occasion): Boolean {
        return !isMarkedKeepSeparate(occasion) && baseSource(occasion.source).equals("CONFLICT", ignoreCase = true)
    }

    private data class OccasionConflictDate(
        val month: Int,
        val dayOfMonth: Int,
    )

    private fun Occasion.conflictGroupKey(): String {
        val normalizedType = type.raw
        val normalizedLabel = if (OccasionType.fromRaw(normalizedType) == OccasionType.CUSTOM) {
            label.orEmpty().trim().lowercase(Locale.US)
        } else {
            ""
        }
        return listOf(contactId.value, normalizedType, normalizedLabel).joinToString(separator = "|")
    }
}
