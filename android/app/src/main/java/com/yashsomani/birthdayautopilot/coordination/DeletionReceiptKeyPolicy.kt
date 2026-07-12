package com.yashsomani.birthdayautopilot.coordination

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object DeletionReceiptKeyPolicy {
  private const val DOMAIN = "birthday-deletion-receipt-v1\u0000"
  private val encodedKey = Regex("^[a-f0-9]{64}$")
  private val hex = "0123456789abcdef".toCharArray()

  fun derive(receiptId: String): String = MessageDigest.getInstance("SHA-256")
    .digest((DOMAIN + receiptId).toByteArray(StandardCharsets.UTF_8))
    .toHex()

  fun matches(receiptId: String, requestKey: String?): Boolean {
    if (requestKey == null || !encodedKey.matches(requestKey)) return false
    return MessageDigest.isEqual(
      derive(receiptId).toByteArray(StandardCharsets.US_ASCII),
      requestKey.toByteArray(StandardCharsets.US_ASCII),
    )
  }

  private fun ByteArray.toHex(): String = CharArray(size * 2).also { output ->
    forEachIndexed { index, byte ->
      val value = byte.toInt() and 0xff
      output[index * 2] = hex[value ushr 4]
      output[index * 2 + 1] = hex[value and 0x0f]
    }
  }.concatToString()
}
