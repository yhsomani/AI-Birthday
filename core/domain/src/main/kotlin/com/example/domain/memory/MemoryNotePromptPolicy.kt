package com.example.domain.memory

import com.example.domain.model.memory.MemoryNoteRecord

object MemoryNotePromptPolicy {
    const val PRIVATE_REFERENCE_CATEGORY = "PRIVATE"

    fun canUseInAiPrompts(note: MemoryNoteRecord): Boolean {
        return note.category != PRIVATE_REFERENCE_CATEGORY
    }
}
