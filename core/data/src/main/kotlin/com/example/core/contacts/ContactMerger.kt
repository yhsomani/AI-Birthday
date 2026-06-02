package com.example.contacts

import com.example.core.db.entities.ContactEntity

object ContactMerger {
    fun merge(device: List<ContactEntity>, google: List<ContactEntity>): List<ContactEntity> {
        val mapped = mutableMapOf<String, ContactEntity>()
        device.forEach { mapped[it.name] = it }
        google.forEach { 
            if (mapped.containsKey(it.name)) {
                // Merge emails etc.
                val existing = mapped[it.name]!!
                mapped[it.name] = existing.copy(
                    googleContactId = it.googleContactId ?: existing.googleContactId,
                    primaryEmail = it.primaryEmail ?: existing.primaryEmail
                )
            } else {
                mapped[it.name] = it
            }
        }
        return mapped.values.toList()
    }
}
