package com.yashsomani.birthdayautopilot.messages

enum class SmsEncoding {
  GSM_7,
  UNICODE,
}

data class SmsEncodingEstimate(
  val encoding: SmsEncoding,
  val characterCount: Int,
  val encodingUnitCount: Int,
  val segmentCount: Int,
)

fun interface SmsLengthCalculator {
  fun calculate(text: String): SmsEncodingEstimate
}

/**
 * Deterministic authoring estimate for the GSM default alphabet and Unicode multipart limits.
 * SmsManager.divideMessage remains authoritative and must be rebound into the approval plan.
 */
object SmsEncodingEstimator : SmsLengthCalculator {
  override fun calculate(text: String): SmsEncodingEstimate = estimate(text)

  fun estimate(text: String): SmsEncodingEstimate {
    val codePoints = text.codePoints().toArray()
    val gsm = codePoints.all { it in GSM_BASIC || it in GSM_EXTENSION }
    val units = if (gsm) {
      codePoints.fold(0) { total, codePoint -> total + if (codePoint in GSM_EXTENSION) 2 else 1 }
    } else {
      text.length
    }
    val segments = when {
      units == 0 -> 0
      gsm && units <= GSM_SINGLE_SEGMENT -> 1
      gsm -> ceilingDivide(units, GSM_MULTIPART_SEGMENT)
      units <= UNICODE_SINGLE_SEGMENT -> 1
      else -> ceilingDivide(units, UNICODE_MULTIPART_SEGMENT)
    }
    return SmsEncodingEstimate(
      encoding = if (gsm) SmsEncoding.GSM_7 else SmsEncoding.UNICODE,
      characterCount = codePoints.size,
      encodingUnitCount = units,
      segmentCount = segments,
    )
  }

  private fun ceilingDivide(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor

  private val GSM_BASIC = (
    "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ" +
      "ÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡" +
      "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    ).codePoints().toArray().toSet()
  private val GSM_EXTENSION = "^{}\\[~]|€".codePoints().toArray().toSet()

  private const val GSM_SINGLE_SEGMENT = 160
  private const val GSM_MULTIPART_SEGMENT = 153
  private const val UNICODE_SINGLE_SEGMENT = 70
  private const val UNICODE_MULTIPART_SEGMENT = 67
}
