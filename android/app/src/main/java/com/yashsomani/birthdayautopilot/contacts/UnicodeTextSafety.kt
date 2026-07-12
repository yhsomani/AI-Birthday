package com.yashsomani.birthdayautopilot.contacts

import java.text.Normalizer

/**
 * Shared handling for untrusted People text.
 *
 * Display text may have ordinary control characters removed, but text used for SMS interpolation
 * is intentionally stricter: any control, explicit bidi instruction, or invisible spoofing mark
 * makes the given name unavailable. Natural RTL letters are not rejected.
 */
object UnicodeTextSafety {
  private const val MAX_DISPLAY_NAME_CODE_POINTS = 200
  private const val MAX_GIVEN_NAME_CODE_POINTS = 100

  fun displayName(raw: String?): String? {
    if (raw == null) return null
    if (raw.length > MAX_DISPLAY_INPUT_UTF16_UNITS) return null
    val normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
    if (normalized.codePoints().anyMatch { isBidiControl(it) || isForbiddenInvisible(it) }) return null

    val output = StringBuilder(normalized.length)
    var pendingSpace = false
    normalized.codePoints().forEach { codePoint ->
      when {
        isOrdinaryControl(codePoint) -> Unit
        Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) -> pendingSpace = output.isNotEmpty()
        else -> {
          if (pendingSpace) output.append(' ')
          output.appendCodePoint(codePoint)
          pendingSpace = false
        }
      }
    }

    val result = output.toString().trim()
    return result.takeIf {
      it.isNotEmpty() &&
        it.codePointCount(0, it.length) <= MAX_DISPLAY_NAME_CODE_POINTS &&
        it.codePoints().anyMatch { codePoint -> Character.isLetterOrDigit(codePoint) }
    }
  }

  fun smsGivenName(raw: String?): String? {
    if (raw == null) return null
    if (raw.length > MAX_GIVEN_INPUT_UTF16_UNITS) return null
    val normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
    if (normalized.codePoints().anyMatch(::isUnsafeForSmsInterpolation)) return null
    if ('{' in normalized || '}' in normalized) return null

    val output = StringBuilder(normalized.length)
    var pendingSpace = false
    normalized.codePoints().forEach { codePoint ->
      if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
        pendingSpace = output.isNotEmpty()
      } else {
        if (pendingSpace) output.append(' ')
        output.appendCodePoint(codePoint)
        pendingSpace = false
      }
    }

    val result = output.toString().trim()
    return result.takeIf {
      it.isNotEmpty() &&
        it.codePointCount(0, it.length) <= MAX_GIVEN_NAME_CODE_POINTS &&
        it.codePoints().anyMatch { codePoint -> Character.isLetterOrDigit(codePoint) }
    }
  }

  fun containsUnsafeMessageCodePoint(value: String): Boolean =
    value.codePoints().anyMatch(::isUnsafeForSmsInterpolation)

  fun normalizeNfc(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)

  private fun isUnsafeForSmsInterpolation(codePoint: Int): Boolean =
    isOrdinaryControl(codePoint) || isBidiControl(codePoint) || isForbiddenInvisible(codePoint)

  private fun isOrdinaryControl(codePoint: Int): Boolean {
    val type = Character.getType(codePoint)
    return type == Character.CONTROL.toInt() ||
      type == Character.LINE_SEPARATOR.toInt() ||
      type == Character.PARAGRAPH_SEPARATOR.toInt()
  }

  private fun isBidiControl(codePoint: Int): Boolean =
    codePoint == 0x061C ||
      codePoint == 0x200E ||
      codePoint == 0x200F ||
      codePoint in 0x202A..0x202E ||
      codePoint in 0x2066..0x2069

  private fun isForbiddenInvisible(codePoint: Int): Boolean =
    codePoint == 0x200B || codePoint == 0x2060 || codePoint == 0xFEFF

  private const val MAX_DISPLAY_INPUT_UTF16_UNITS = 4_000
  private const val MAX_GIVEN_INPUT_UTF16_UNITS = 2_000
}
