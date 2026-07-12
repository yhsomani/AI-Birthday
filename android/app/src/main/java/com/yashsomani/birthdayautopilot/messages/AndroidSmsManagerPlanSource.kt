package com.yashsomani.birthdayautopilot.messages

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager

/** A native, subscription-bound segmentation result. Private payloads are always redacted. */
data class NativeSmsPlan(
  val encoding: SmsEncoding,
  val characterCount: Int,
  val encodingUnitCount: Int,
  val orderedParts: List<String>,
) {
  val segmentCount: Int get() = orderedParts.size

  override fun toString(): String =
    "NativeSmsPlan(encoding=$encoding, segmentCount=$segmentCount, privateFields=<redacted>)"
}

enum class NativeSmsPlanFailure {
  INVALID_SUBSCRIPTION,
  INVALID_TEXT,
  PLATFORM_UNAVAILABLE,
  MALFORMED_LENGTH_RESULT,
  UNKNOWN_ENCODING,
  EMPTY_OR_REORDERED_PARTS,
  LENGTH_AND_PART_COUNT_DISAGREE,
}

sealed interface NativeSmsPlanResult {
  data class Planned(val plan: NativeSmsPlan) : NativeSmsPlanResult
  data class Rejected(val reason: NativeSmsPlanFailure) : NativeSmsPlanResult
}

fun interface SmsPlatformPlanSource {
  /** Computes the platform plan only. This interface has no operation capable of sending SMS. */
  fun plan(exactText: String, subscriptionId: Int): NativeSmsPlanResult
}

/**
 * Android adapter whose ordered parts come from a SmsManager bound to the approved subscription.
 * It intentionally exposes no send API; submission belongs to the later sole typed gateway.
 */
class AndroidSmsManagerPlanSource private constructor(
  private val partsDivider: BoundSmsPartsDivider,
  private val lengthDetails: SmsLengthDetails,
) : SmsPlatformPlanSource {
  constructor(context: Context) : this(
    partsDivider = AndroidBoundSmsPartsDivider(context.applicationContext),
    lengthDetails = AndroidSmsLengthDetails,
  )

  override fun plan(exactText: String, subscriptionId: Int): NativeSmsPlanResult {
    if (
      subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID ||
      subscriptionId < 0
    ) return NativeSmsPlanResult.Rejected(NativeSmsPlanFailure.INVALID_SUBSCRIPTION)
    if (exactText.isBlank() || exactText.length > MAX_TEXT_CODE_UNITS) {
      return NativeSmsPlanResult.Rejected(NativeSmsPlanFailure.INVALID_TEXT)
    }

    val details = try {
      lengthDetails.calculate(exactText)
    } catch (_: RuntimeException) {
      return NativeSmsPlanResult.Rejected(NativeSmsPlanFailure.PLATFORM_UNAVAILABLE)
    }
    if (
      details.size < LENGTH_DETAILS_SIZE ||
      details[INDEX_SEGMENT_COUNT] <= 0 ||
      details[INDEX_ENCODING_UNITS] <= 0 ||
      details[INDEX_REMAINING_UNITS] < 0
    ) return NativeSmsPlanResult.Rejected(NativeSmsPlanFailure.MALFORMED_LENGTH_RESULT)

    val encoding = when (details[INDEX_CODE_UNIT_SIZE]) {
      ENCODING_7_BIT -> SmsEncoding.GSM_7
      ENCODING_16_BIT -> SmsEncoding.UNICODE
      else -> return NativeSmsPlanResult.Rejected(NativeSmsPlanFailure.UNKNOWN_ENCODING)
    }

    val parts = try {
      partsDivider.divide(exactText, subscriptionId)
    } catch (_: RuntimeException) {
      return NativeSmsPlanResult.Rejected(NativeSmsPlanFailure.PLATFORM_UNAVAILABLE)
    }
    if (
      parts.isEmpty() ||
      parts.any(String::isEmpty) ||
      parts.joinToString(separator = "") != exactText
    ) return NativeSmsPlanResult.Rejected(NativeSmsPlanFailure.EMPTY_OR_REORDERED_PARTS)
    if (parts.size != details[INDEX_SEGMENT_COUNT]) {
      return NativeSmsPlanResult.Rejected(NativeSmsPlanFailure.LENGTH_AND_PART_COUNT_DISAGREE)
    }

    return NativeSmsPlanResult.Planned(
      NativeSmsPlan(
        encoding = encoding,
        characterCount = exactText.codePointCount(0, exactText.length),
        encodingUnitCount = details[INDEX_ENCODING_UNITS],
        orderedParts = parts.toList(),
      ),
    )
  }

  internal constructor(
    partsDivider: (exactText: String, subscriptionId: Int) -> List<String>,
    lengthDetails: (exactText: String) -> IntArray,
  ) : this(
    BoundSmsPartsDivider(partsDivider),
    SmsLengthDetails(lengthDetails),
  )

  private companion object {
    const val MAX_TEXT_CODE_UNITS = 1_000
    const val LENGTH_DETAILS_SIZE = 4
    const val INDEX_SEGMENT_COUNT = 0
    const val INDEX_ENCODING_UNITS = 1
    const val INDEX_REMAINING_UNITS = 2
    const val INDEX_CODE_UNIT_SIZE = 3

    // SmsMessage.calculateLength returns Android's SMS encoding constants. Only the two text
    // encodings represented by the approval schema are accepted; every other value fails closed.
    const val ENCODING_7_BIT = 1
    const val ENCODING_16_BIT = 3
  }
}

private fun interface BoundSmsPartsDivider {
  fun divide(exactText: String, subscriptionId: Int): List<String>
}

private fun interface SmsLengthDetails {
  fun calculate(exactText: String): IntArray
}

private object AndroidSmsLengthDetails : SmsLengthDetails {
  override fun calculate(exactText: String): IntArray =
    SmsMessage.calculateLength(exactText, false)
}

private class AndroidBoundSmsPartsDivider(private val context: Context) : BoundSmsPartsDivider {
  override fun divide(exactText: String, subscriptionId: Int): List<String> =
    smsManager(subscriptionId).divideMessage(exactText)

  @Suppress("DEPRECATION")
  private fun smsManager(subscriptionId: Int): SmsManager = if (Build.VERSION.SDK_INT >= 31) {
    context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
  } else {
    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
  }
}
