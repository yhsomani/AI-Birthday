package com.example.domain.model.memory

data class MemoryNoteCategoryCount(
    val category: String,
    val count: Int,
)

data class MemoryNoteSummary(
    val totalCount: Int,
    val categoryCounts: List<MemoryNoteCategoryCount>,
) {
    companion object {
        val EMPTY = MemoryNoteSummary(
            totalCount = 0,
            categoryCounts = emptyList(),
        )
    }
}
