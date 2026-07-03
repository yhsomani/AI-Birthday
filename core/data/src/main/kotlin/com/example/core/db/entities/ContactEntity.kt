package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["healthScore", "lastRevivalAttemptMs"], name = "idx_contacts_revival"),
        Index(value = ["isDeleted", "healthScore"], name = "idx_contacts_active")
    ]
)
data class ContactEntity(
    @PrimaryKey val id: String,
    val googleContactId: String? = null,
    val name: String,
    val nickname: String? = null,

    // Birthday
    val birthdayDay: Int? = null,
    val birthdayMonth: Int? = null,
    val birthdayYear: Int? = null,

    // Anniversary
    val anniversaryDay: Int? = null,
    val anniversaryMonth: Int? = null,
    val anniversaryYear: Int? = null,

    // Work anniversary
    val workStartDay: Int? = null,
    val workStartMonth: Int? = null,
    val workStartYear: Int? = null,

    // Contact info
    val primaryPhone: String? = null,
    val secondaryPhone: String? = null,
    val primaryEmail: String? = null,
    val company: String? = null,
    val jobTitle: String? = null,
    val address: String? = null,
    val profilePhotoUri: String? = null,
    val contactGroup: String? = null,

    // AI classification
    val relationshipType: String = "UNKNOWN",
    val relationshipSubtype: String? = null,   // e.g. "maternal_aunt", "college_roommate"
    val preferredLanguage: String = "en",
    val preferredChannel: String = MessageChannel.SMS.raw,
    val formalityLevel: String = "CASUAL",
    val communicationStyle: String = "WARM",   // WARM, FUNNY, PROFESSIONAL, EMOTIONAL

    // AI Classification Confidence
    val classificationConfidence: Double = 0.0,

    // Relationship health
    val healthScore: Int = 50,
    val engagementScore: Int = 50,
    val interactionFrequencyPerMonth: Float = 0f,
    val lastInteractionDate: Long? = null,
    val lastWishedDate: Long? = null,
    val consecutiveYearsWished: Int = 0,
    val lastRevivalAttemptMs: Long = 0L,

    // Automation
    val automationMode: String = ApprovalMode.DEFAULT.raw,
    val giftBudgetInr: Int = 500,
    val annualBudgetInr: Int = 0,
    val skipAutoWish: Boolean = false,
    val customSendTimeHour: Int? = null,
    val customSendTimeMinute: Int? = null,

    // Enrichment (JSON fields)
    val interestsJson: String = "[]",          // ["cricket", "travel", "movies"]
    val hobbiesJson: String = "[]",
    val sharedHistoryJson: String = "[]",      // ["College roommates 2015-2019"]
    val favoritesJson: String = "{}",          // {"color":"blue","food":"biryani","team":"CSK"}
    val relationsJson: String = "[]",          // [{"person":"Jane Smith","type":"spouse"}]
    val notesText: String = "",

    // NEW v2: Emotion intelligence
    val typicalMoodWhenContacted: String = "NEUTRAL",  // HAPPY, STRESSED, BUSY, NEUTRAL
    val sensitiveTopicsJson: String = "[]",     // Topics to avoid in messages
    val currentLifePhaseJson: String = "{}",    // {"phase":"new_job","since":"2024-11"}

    // Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false
)
