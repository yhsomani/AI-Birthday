package com.yashsomani.birthdayautopilot.messages

import com.yashsomani.birthdayautopilot.contacts.UnicodeTextSafety

enum class MessageLanguage {
  ENGLISH,
  HINDI,
}

enum class TemplatePlaceholderMode {
  PERSONALIZED_FIRST_NAME,
  GENERIC_NO_NAME,
}

enum class TemplateSource {
  BUILT_IN,
  USER_EDITED,
  GEMINI_SELECTED,
}

data class MessageTemplate(
  val version: String,
  val language: MessageLanguage,
  val placeholderMode: TemplatePlaceholderMode,
  val source: TemplateSource,
  val text: String,
) {
  override fun toString(): String =
    "MessageTemplate(version=$version, language=$language, placeholderMode=$placeholderMode, source=$source, text=<redacted>)"
}

enum class TemplateValidationError {
  VERSION_INVALID,
  SEGMENT_CAP_INVALID,
  EMPTY,
  TOO_LARGE_TO_VALIDATE,
  UNSAFE_UNICODE,
  PLACEHOLDER_REQUIRED,
  PLACEHOLDER_NOT_ALLOWED,
  PLACEHOLDER_COUNT_INVALID,
  UNRESOLVED_VARIABLE,
  GIVEN_NAME_REQUIRED_OR_UNSAFE,
  URL_NOT_ALLOWED,
  TRACKING_OR_HASHTAG_NOT_ALLOWED,
  PROMOTIONAL_CONTENT_NOT_ALLOWED,
  SENSITIVE_OR_INVENTED_CLAIM_NOT_ALLOWED,
  LANGUAGE_MISMATCH,
  SEGMENT_CAP_EXCEEDED,
}

data class RenderedMessagePreview(
  val exactText: String,
  val metrics: SmsEncodingEstimate,
  val templateVersion: String,
  val placeholderMode: TemplatePlaceholderMode,
  val validatorVersion: String,
) {
  override fun toString(): String =
    "RenderedMessagePreview(metrics=$metrics, templateVersion=$templateVersion, " +
      "placeholderMode=$placeholderMode, validatorVersion=$validatorVersion, exactText=<redacted>)"
}

data class TemplateValidationResult(
  val preview: RenderedMessagePreview?,
  val errors: Set<TemplateValidationError>,
) {
  val valid: Boolean get() = preview != null && errors.isEmpty()
}

object BuiltInMessageTemplates {
  const val CATALOG_VERSION = "birthday-defaults-v1"

  fun personalized(language: MessageLanguage): MessageTemplate = when (language) {
    MessageLanguage.ENGLISH -> MessageTemplate(
      version = "$CATALOG_VERSION-en-personalized",
      language = language,
      placeholderMode = TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME,
      source = TemplateSource.BUILT_IN,
      text = "Happy birthday, {firstName}! Wishing you a wonderful day.",
    )
    MessageLanguage.HINDI -> MessageTemplate(
      version = "$CATALOG_VERSION-hi-personalized",
      language = language,
      placeholderMode = TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME,
      source = TemplateSource.BUILT_IN,
      text = "जन्मदिन मुबारक हो, {firstName}! आपका दिन शानदार हो।",
    )
  }

  fun generic(language: MessageLanguage): MessageTemplate = when (language) {
    MessageLanguage.ENGLISH -> MessageTemplate(
      version = "$CATALOG_VERSION-en-generic",
      language = language,
      placeholderMode = TemplatePlaceholderMode.GENERIC_NO_NAME,
      source = TemplateSource.BUILT_IN,
      text = "Happy birthday! Wishing you a wonderful day.",
    )
    MessageLanguage.HINDI -> MessageTemplate(
      version = "$CATALOG_VERSION-hi-generic",
      language = language,
      placeholderMode = TemplatePlaceholderMode.GENERIC_NO_NAME,
      source = TemplateSource.BUILT_IN,
      text = "जन्मदिन मुबारक हो! आपका दिन शानदार हो।",
    )
  }
}

class MessageTemplateValidator(
  private val smsLengthCalculator: SmsLengthCalculator = SmsEncodingEstimator,
) {
  fun validateAndRender(
    template: MessageTemplate,
    givenName: String?,
    segmentCap: Int = DEFAULT_SEGMENT_CAP,
  ): TemplateValidationResult {
    val errors = linkedSetOf<TemplateValidationError>()
    if (!VERSION.matches(template.version)) errors += TemplateValidationError.VERSION_INVALID
    if (segmentCap !in 1..DEFAULT_SEGMENT_CAP) errors += TemplateValidationError.SEGMENT_CAP_INVALID
    if (template.text.length > MAX_TEMPLATE_INPUT_UTF16_UNITS) {
      errors += TemplateValidationError.TOO_LARGE_TO_VALIDATE
      return TemplateValidationResult(preview = null, errors = errors.toSet())
    }

    val normalized = UnicodeTextSafety.normalizeNfc(template.text)
    val codePointCount = normalized.codePointCount(0, normalized.length)
    if (normalized.isBlank()) errors += TemplateValidationError.EMPTY
    if (codePointCount > MAX_TEMPLATE_CODE_POINTS) {
      errors += TemplateValidationError.TOO_LARGE_TO_VALIDATE
      return TemplateValidationResult(preview = null, errors = errors.toSet())
    }
    if (UnicodeTextSafety.containsUnsafeMessageCodePoint(normalized)) {
      errors += TemplateValidationError.UNSAFE_UNICODE
    }
    if (!matchesDeclaredLanguage(normalized, template.language)) {
      errors += TemplateValidationError.LANGUAGE_MISMATCH
    }

    val placeholderCount = countOccurrences(normalized, FIRST_NAME_PLACEHOLDER)
    when (template.placeholderMode) {
      TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME -> when (placeholderCount) {
        0 -> errors += TemplateValidationError.PLACEHOLDER_REQUIRED
        1 -> Unit
        else -> errors += TemplateValidationError.PLACEHOLDER_COUNT_INVALID
      }
      TemplatePlaceholderMode.GENERIC_NO_NAME -> if (placeholderCount != 0) {
        errors += TemplateValidationError.PLACEHOLDER_NOT_ALLOWED
      }
    }
    if (containsUnresolvedVariable(normalized)) errors += TemplateValidationError.UNRESOLVED_VARIABLE
    errors += MessageContentPolicy.validate(normalized)

    val safeName = when (template.placeholderMode) {
      TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME -> UnicodeTextSafety.smsGivenName(givenName)
        ?: run {
          errors += TemplateValidationError.GIVEN_NAME_REQUIRED_OR_UNSAFE
          null
        }
      TemplatePlaceholderMode.GENERIC_NO_NAME -> null
    }
    val rendered = when {
      template.placeholderMode == TemplatePlaceholderMode.GENERIC_NO_NAME -> normalized
      safeName != null && placeholderCount == 1 -> normalized.replace(FIRST_NAME_PLACEHOLDER, safeName)
      else -> null
    }

    val preview = rendered?.let { exactText ->
      if (containsUnresolvedVariable(exactText)) errors += TemplateValidationError.UNRESOLVED_VARIABLE
      errors += MessageContentPolicy.validate(exactText)
      val metrics = smsLengthCalculator.calculate(exactText)
      if (segmentCap in 1..DEFAULT_SEGMENT_CAP && metrics.segmentCount > segmentCap) {
        errors += TemplateValidationError.SEGMENT_CAP_EXCEEDED
      }
      RenderedMessagePreview(
        exactText = exactText,
        metrics = metrics,
        templateVersion = template.version,
        placeholderMode = template.placeholderMode,
        validatorVersion = VALIDATOR_VERSION,
      )
    }

    return TemplateValidationResult(
      preview = preview,
      errors = errors.toSet(),
    )
  }

  private fun matchesDeclaredLanguage(value: String, language: MessageLanguage): Boolean {
    val templateOnly = value.replace(FIRST_NAME_PLACEHOLDER, "")
    val letterScripts = templateOnly.codePoints().toArray()
      .filter(Character::isLetter)
      .map(Character.UnicodeScript::of)
    return when (language) {
      MessageLanguage.ENGLISH -> letterScripts.isNotEmpty() && letterScripts.all { it == Character.UnicodeScript.LATIN }
      MessageLanguage.HINDI ->
        Character.UnicodeScript.DEVANAGARI in letterScripts &&
          letterScripts.all { it == Character.UnicodeScript.DEVANAGARI }
    }
  }

  private fun countOccurrences(value: String, needle: String): Int {
    var count = 0
    var from = 0
    while (true) {
      val index = value.indexOf(needle, from)
      if (index < 0) return count
      count += 1
      from = index + needle.length
    }
  }

  private fun containsUnresolvedVariable(value: String): Boolean {
    val withoutSupported = value.replace(FIRST_NAME_PLACEHOLDER, "")
    return '{' in withoutSupported || '}' in withoutSupported
  }

  companion object {
    const val DEFAULT_SEGMENT_CAP = 2
    const val VALIDATOR_VERSION = "sms-template-validator-v1"
    const val FIRST_NAME_PLACEHOLDER = "{firstName}"
    private const val MAX_TEMPLATE_CODE_POINTS = 1_000
    private const val MAX_TEMPLATE_INPUT_UTF16_UNITS = 2_000
    private val VERSION = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$")
  }
}

internal object MessageContentPolicy {
  private val URL = Regex(
    "(?:\\b(?:https?|ftp)://|\\bwww\\.)\\S+|" +
      "\\b(?:[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,62}[\\p{L}\\p{N}])?\\.)+" +
      "(?:[A-Za-z]{2,63}|xn--[A-Za-z0-9-]{2,59})(?:[/?:#]\\S*)?|" +
      "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b",
    RegexOption.IGNORE_CASE,
  )
  private val TRACKING_OR_HASHTAG = Regex("(?:\\butm_[a-z]+\\s*=|\\bref\\s*=|#[\\p{L}\\p{N}_]+)", RegexOption.IGNORE_CASE)
  private val PROMOTIONAL = Regex(
    "\\b(?:sale|discount|coupon|promo|buy now|limited offer|free offer)\\b|(?:छूट|ऑफर|कूपन|अभी खरीदें|मुफ़्त ऑफर)",
    RegexOption.IGNORE_CASE,
  )
  private val SENSITIVE_OR_INVENTED = Regex(
    "\\b(?:turning\\s+[0-9]{1,3}|[0-9]{1,3}(?:st|nd|rd|th)?\\s+birthday|[0-9]{1,3}\\s+years old|" +
      "remember (?:when|our)|our secret|as your (?:wife|husband|girlfriend|boyfriend)|" +
      "your (?:illness|diagnosis|religion|caste|race|disability|politics))\\b|" +
      "(?:[0-9०-९]{1,3}\\s*(?:वां|वाँ)?\\s*जन्मदिन|साल के हो गए|हमारा राज़|आपकी बीमारी|आपका धर्म)",
    RegexOption.IGNORE_CASE,
  )

  fun validate(text: String): Set<TemplateValidationError> = buildSet {
    if (URL.containsMatchIn(text)) add(TemplateValidationError.URL_NOT_ALLOWED)
    if (TRACKING_OR_HASHTAG.containsMatchIn(text)) {
      add(TemplateValidationError.TRACKING_OR_HASHTAG_NOT_ALLOWED)
    }
    if (PROMOTIONAL.containsMatchIn(text)) {
      add(TemplateValidationError.PROMOTIONAL_CONTENT_NOT_ALLOWED)
    }
    if (SENSITIVE_OR_INVENTED.containsMatchIn(text)) {
      add(TemplateValidationError.SENSITIVE_OR_INVENTED_CLAIM_NOT_ALLOWED)
    }
  }
}
