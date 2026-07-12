package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "contacts",
  indices = [
    Index(value = ["sourceFingerprint"], unique = true),
    Index(value = ["normalizedDestinationBasis"]),
    Index(value = ["birthdayMonth", "birthdayDay"]),
  ],
)
data class ContactEntity(
  @PrimaryKey val localId: String,
  val sourceFingerprint: String,
  val displayName: String,
  val safeGivenName: String?,
  val birthdayMonth: Int?,
  val birthdayDay: Int?,
  val birthdayYear: Int?,
  val leapDayPolicy: String?,
  val phoneE164: String?,
  val normalizedDestinationBasis: String?,
  val maskedPhone: String?,
  val sourceUpdatedAtMillis: Long,
  val readiness: String,
  val enrollment: String,
  val sourceDeleted: Boolean,
)
