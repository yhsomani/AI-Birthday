package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_notes",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["contactId"], name = "idx_memory_notes_contactId")]
)
data class MemoryNoteEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val noteText: String,
    val category: String = "GENERAL",   // GENERAL, PREFERENCE, EVENT, GIFT, MILESTONE
    val dateMs: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)
