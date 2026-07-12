package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "activity",
  indices = [Index(value = ["recordedAtMillis"]), Index(value = ["category"])],
)
data class ActivityEntity(
  @PrimaryKey val activityId: String,
  val category: String,
  val safeCode: String,
  val recordedAtMillis: Long,
  val relatedOccurrenceId: String?,
)
