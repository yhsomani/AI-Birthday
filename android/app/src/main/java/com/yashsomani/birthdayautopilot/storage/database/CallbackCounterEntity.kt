package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "callback_counter")
data class CallbackCounterEntity(
  @PrimaryKey val singletonId: Int = SINGLETON_ID,
  val generation: String,
  val nextPositiveId: Int,
) {
  companion object {
    const val SINGLETON_ID = 1
  }
}
