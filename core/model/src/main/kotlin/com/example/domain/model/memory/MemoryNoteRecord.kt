package com.example.domain.model.memory

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MemoryNoteId

data class MemoryNoteRecord(
    val id: MemoryNoteId,
    val contactId: ContactId,
    val noteText: String,
    val category: String,
    val dateMs: Long,
    val isPinned: Boolean,
)
