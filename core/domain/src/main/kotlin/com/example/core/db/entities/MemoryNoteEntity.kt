package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_notes", indices = [androidx.room.Index(value = ["contactId"])])
data class MemoryNoteEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val noteText: String,
    val category: String = "GENERAL",   // GENERAL, PREFERENCE, EVENT, GIFT, MILESTONE
    val dateMs: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)
