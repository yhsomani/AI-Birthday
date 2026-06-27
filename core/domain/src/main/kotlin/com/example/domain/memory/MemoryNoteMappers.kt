package com.example.domain.memory

import com.example.core.db.entities.MemoryNoteEntity
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MemoryNoteId
import com.example.domain.model.memory.MemoryNoteRecord

fun MemoryNoteEntity.toRecord(): MemoryNoteRecord {
    return MemoryNoteRecord(
        id = MemoryNoteId(id),
        contactId = ContactId(contactId),
        noteText = noteText,
        category = category,
        dateMs = dateMs,
        isPinned = isPinned,
    )
}

fun MemoryNoteRecord.toEntity(): MemoryNoteEntity {
    return MemoryNoteEntity(
        id = id.value,
        contactId = contactId.value,
        noteText = noteText,
        category = category,
        dateMs = dateMs,
        isPinned = isPinned,
    )
}
