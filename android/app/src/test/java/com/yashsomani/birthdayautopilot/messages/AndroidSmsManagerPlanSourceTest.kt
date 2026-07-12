package com.yashsomani.birthdayautopilot.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSmsManagerPlanSourceTest {
  @Test
  fun `subscription-bound GSM plan preserves exact ordered parts`() {
    val seenSubscriptions = mutableListOf<Int>()
    val text = "a".repeat(161)
    val source = source(
      parts = { value, subscriptionId ->
        seenSubscriptions += subscriptionId
        listOf(value.take(153), value.drop(153))
      },
      length = { intArrayOf(2, 161, 145, ENCODING_7_BIT) },
    )

    val result = source.plan(text, 7) as NativeSmsPlanResult.Planned

    assertEquals(listOf(7), seenSubscriptions)
    assertEquals(SmsEncoding.GSM_7, result.plan.encoding)
    assertEquals(161, result.plan.characterCount)
    assertEquals(161, result.plan.encodingUnitCount)
    assertEquals(text, result.plan.orderedParts.joinToString(""))
    assertEquals(2, result.plan.segmentCount)
    assertFalse(result.toString().contains(text))
  }

  @Test
  fun `Unicode plan counts code points separately from UTF16 units`() {
    val text = "🎂".repeat(36)
    val firstPart = "🎂".repeat(35)
    val source = source(
      parts = { _, _ -> listOf(firstPart, "🎂") },
      length = { intArrayOf(2, text.length, 65, ENCODING_16_BIT) },
    )

    val result = source.plan(text, 3) as NativeSmsPlanResult.Planned

    assertEquals(SmsEncoding.UNICODE, result.plan.encoding)
    assertEquals(36, result.plan.characterCount)
    assertEquals(72, result.plan.encodingUnitCount)
    assertEquals(text, result.plan.orderedParts.joinToString(""))
  }

  @Test
  fun `invalid subscription and bounded text are rejected before platform calls`() {
    var calls = 0
    val source = source(
      parts = { _, _ -> calls++; emptyList() },
      length = { calls++; intArrayOf(1, 1, 159, ENCODING_7_BIT) },
    )

    assertRejected(source.plan("hello", -1), NativeSmsPlanFailure.INVALID_SUBSCRIPTION)
    assertRejected(source.plan("", 1), NativeSmsPlanFailure.INVALID_TEXT)
    assertRejected(source.plan(" ", 1), NativeSmsPlanFailure.INVALID_TEXT)
    assertRejected(source.plan("a".repeat(1_001), 1), NativeSmsPlanFailure.INVALID_TEXT)
    assertEquals(0, calls)
  }

  @Test
  fun `malformed and unsupported native length metadata fail closed`() {
    val malformed = listOf(
      intArrayOf(),
      intArrayOf(1, 5, 155),
      intArrayOf(0, 5, 155, ENCODING_7_BIT),
      intArrayOf(1, 0, 155, ENCODING_7_BIT),
      intArrayOf(1, 5, -1, ENCODING_7_BIT),
    )
    malformed.forEachIndexed { index, details ->
      val result = source(
        parts = { value, _ -> listOf(value) },
        length = { details },
      ).plan("hello", 1)
      assertRejected(result, NativeSmsPlanFailure.MALFORMED_LENGTH_RESULT, "case $index")
    }

    listOf(0, 2, 4, Int.MAX_VALUE).forEach { encoding ->
      val result = source(
        parts = { value, _ -> listOf(value) },
        length = { intArrayOf(1, 5, 155, encoding) },
      ).plan("hello", 1)
      assertRejected(result, NativeSmsPlanFailure.UNKNOWN_ENCODING, "encoding $encoding")
    }
  }

  @Test
  fun `empty reordered truncated and count-mismatched parts fail closed`() {
    val text = "abcdef"
    val badParts = listOf(
      emptyList(),
      listOf(""),
      listOf("def", "abc"),
      listOf("abc"),
    )
    badParts.forEachIndexed { index, parts ->
      val result = source(
        parts = { _, _ -> parts },
        length = { intArrayOf(parts.size.coerceAtLeast(1), 6, 154, ENCODING_7_BIT) },
      ).plan(text, 1)
      assertRejected(result, NativeSmsPlanFailure.EMPTY_OR_REORDERED_PARTS, "case $index")
    }

    val countMismatch = source(
      parts = { value, _ -> listOf(value) },
      length = { intArrayOf(2, 6, 300, ENCODING_7_BIT) },
    ).plan(text, 1)
    assertRejected(countMismatch, NativeSmsPlanFailure.LENGTH_AND_PART_COUNT_DISAGREE)
  }

  @Test
  fun `platform exceptions become content-free rejection reasons`() {
    val lengthFailure = source(
      parts = { value, _ -> listOf(value) },
      length = { throw IllegalStateException("private payload") },
    ).plan("secret text", 1)
    assertRejected(lengthFailure, NativeSmsPlanFailure.PLATFORM_UNAVAILABLE)
    assertFalse(lengthFailure.toString().contains("secret"))
    assertFalse(lengthFailure.toString().contains("private payload"))

    val divideFailure = source(
      parts = { _, _ -> throw SecurityException("private payload") },
      length = { intArrayOf(1, 11, 149, ENCODING_7_BIT) },
    ).plan("secret text", 1)
    assertRejected(divideFailure, NativeSmsPlanFailure.PLATFORM_UNAVAILABLE)
    assertFalse(divideFailure.toString().contains("secret"))
    assertFalse(divideFailure.toString().contains("private payload"))
  }

  private fun source(
    parts: (String, Int) -> List<String>,
    length: (String) -> IntArray,
  ) = AndroidSmsManagerPlanSource(parts, length)

  private fun assertRejected(
    result: NativeSmsPlanResult,
    reason: NativeSmsPlanFailure,
    message: String? = null,
  ) {
    assertTrue(message, result is NativeSmsPlanResult.Rejected)
    assertEquals(message, reason, (result as NativeSmsPlanResult.Rejected).reason)
  }

  private companion object {
    const val ENCODING_7_BIT = 1
    const val ENCODING_16_BIT = 3
  }
}
