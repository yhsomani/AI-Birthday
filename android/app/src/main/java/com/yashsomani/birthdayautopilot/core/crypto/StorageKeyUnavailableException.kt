package com.yashsomani.birthdayautopilot.core.crypto

class StorageKeyUnavailableException(
  val safeCode: String,
  cause: Throwable? = null,
) : Exception(safeCode, cause)
