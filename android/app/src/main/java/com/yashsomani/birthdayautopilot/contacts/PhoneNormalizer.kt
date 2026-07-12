package com.yashsomani.birthdayautopilot.contacts

import java.util.Locale

/**
 * Syntactically canonical E.164. Creation after import must still follow a successful
 * [PhoneMetadataEngine] validity, type, emergency, premium, and short-number classification.
 */
class CanonicalPhoneNumber private constructor(val value: String) {
  override fun equals(other: Any?): Boolean =
    other is CanonicalPhoneNumber && value == other.value

  override fun hashCode(): Int = value.hashCode()

  override fun toString(): String = "CanonicalPhoneNumber(<redacted>)"

  companion object {
    private val E164 = Regex("^\\+[1-9][0-9]{1,14}$")

    fun parse(value: String): CanonicalPhoneNumber? =
      value.takeIf(E164::matches)?.let(::CanonicalPhoneNumber)
  }
}

enum class PhoneNumberKind {
  MOBILE,
  FIXED_LINE_OR_MOBILE,
  FIXED_LINE,
  PREMIUM_RATE,
  TOLL_FREE,
  VOIP,
  PAGER,
  PERSONAL,
  UNKNOWN,
}

/**
 * Result of parsing with a maintained libphonenumber implementation.
 *
 * The platform adapter must use both PhoneNumberUtil and ShortNumberInfo. In particular, it must
 * not treat parse success as SMS capability, and it must surface extensions instead of silently
 * dropping them from an E.164 rendering.
 */
sealed interface PhoneMetadataResult {
  data class Parsed(
    val e164: String,
    val kind: PhoneNumberKind,
    val possible: Boolean,
    val valid: Boolean,
    val emergency: Boolean,
    val shortCode: Boolean,
    val extension: String?,
  ) : PhoneMetadataResult {
    override fun toString(): String = "PhoneMetadataResult.Parsed(<redacted>)"
  }

  data object Malformed : PhoneMetadataResult
  data object Ambiguous : PhoneMetadataResult
}

fun interface PhoneMetadataEngine {
  /** Region is an uppercase ISO-3166 alpha-2 code, or null only for an international `+` number. */
  fun analyze(rawValue: String, region: String?): PhoneMetadataResult
}

enum class PhoneRejectionReason {
  REGION_REQUIRED,
  REGION_INVALID,
  MALFORMED,
  AMBIGUOUS,
  EXTENSION_NOT_SUPPORTED,
  EMERGENCY_NUMBER,
  SHORT_CODE,
  PREMIUM_RATE,
  NOT_VALID,
  NOT_SMS_CAPABLE,
  ENGINE_UNAVAILABLE,
}

data class NormalizedPhone(
  val canonical: CanonicalPhoneNumber,
  val maskedDisplay: String,
  val kind: PhoneNumberKind,
  val sourcePhoneIds: Set<String>,
  val mobileSuggestion: Boolean,
) {
  override fun toString(): String = "NormalizedPhone(<redacted>)"
}

data class RejectedPhone(
  val phoneId: String,
  val reason: PhoneRejectionReason,
) {
  override fun toString(): String = "RejectedPhone(reason=$reason, phoneId=<redacted>)"
}

data class PhoneResolution(
  val candidates: List<NormalizedPhone>,
  val rejected: List<RejectedPhone>,
  val selected: NormalizedPhone?,
  val suggestedPhoneId: String?,
  val selectionRequired: Boolean,
  val selectedPhoneInvalid: Boolean,
)

class PhoneNormalizer(private val metadata: PhoneMetadataEngine) {
  fun resolve(
    phones: List<RawContactPhone>,
    selectedPhoneId: String?,
    homeRegion: String?,
  ): PhoneResolution {
    val normalizedRegion = normalizeRegion(homeRegion)
    val analyzed = phones.map { phone -> normalizeOne(phone, normalizedRegion, homeRegion != null) }
    val rejected = analyzed.mapNotNull(PhoneAttempt::rejection)
    val candidates = analyzed
      .mapNotNull(PhoneAttempt::candidate)
      .groupBy { it.canonical }
      .values
      .map(::mergeSameDestination)
      .sortedBy { it.canonical.value }

    val selected = when {
      selectedPhoneId != null -> candidates.singleOrNull { selectedPhoneId in it.sourcePhoneIds }
      candidates.size == 1 -> candidates.single()
      else -> null
    }
    val suggested = candidates
      .filter(NormalizedPhone::mobileSuggestion)
      .takeIf { it.size == 1 }
      ?.single()
      ?.sourcePhoneIds
      ?.sorted()
      ?.firstOrNull()

    return PhoneResolution(
      candidates = candidates,
      rejected = rejected,
      selected = selected,
      suggestedPhoneId = suggested,
      selectionRequired = candidates.size > 1 && selectedPhoneId == null,
      selectedPhoneInvalid = selectedPhoneId != null && selected == null,
    )
  }

  private fun normalizeOne(
    phone: RawContactPhone,
    normalizedRegion: String?,
    regionWasProvided: Boolean,
  ): PhoneAttempt {
    if (!validOpaqueId(phone.phoneId) || phone.value.isBlank() || phone.value.length > MAX_RAW_PHONE_LENGTH) {
      return rejected(phone, PhoneRejectionReason.MALFORMED)
    }
    if (phone.label == PhoneLabel.FIXED_LINE) {
      return rejected(phone, PhoneRejectionReason.NOT_SMS_CAPABLE)
    }

    val trimmed = phone.value.trim()
    if (EXPLICIT_EXTENSION.containsMatchIn(trimmed)) {
      return rejected(phone, PhoneRejectionReason.EXTENSION_NOT_SUPPORTED)
    }
    val international = trimmed.startsWith('+')
    if (!international && !regionWasProvided) {
      return rejected(phone, PhoneRejectionReason.REGION_REQUIRED)
    }
    if (!international && normalizedRegion == null) {
      return rejected(phone, PhoneRejectionReason.REGION_INVALID)
    }

    val result = try {
      metadata.analyze(trimmed, normalizedRegion.takeUnless { international })
    } catch (_: RuntimeException) {
      return rejected(phone, PhoneRejectionReason.ENGINE_UNAVAILABLE)
    }

    return when (result) {
      PhoneMetadataResult.Malformed -> rejected(phone, PhoneRejectionReason.MALFORMED)
      PhoneMetadataResult.Ambiguous -> rejected(phone, PhoneRejectionReason.AMBIGUOUS)
      is PhoneMetadataResult.Parsed -> parsed(phone, result)
    }
  }

  private fun parsed(phone: RawContactPhone, result: PhoneMetadataResult.Parsed): PhoneAttempt {
    val rejection = when {
      !result.extension.isNullOrBlank() -> PhoneRejectionReason.EXTENSION_NOT_SUPPORTED
      result.emergency -> PhoneRejectionReason.EMERGENCY_NUMBER
      result.shortCode -> PhoneRejectionReason.SHORT_CODE
      result.kind == PhoneNumberKind.PREMIUM_RATE -> PhoneRejectionReason.PREMIUM_RATE
      !result.possible || !result.valid -> PhoneRejectionReason.NOT_VALID
      result.kind !in SMS_CAPABLE_KINDS -> PhoneRejectionReason.NOT_SMS_CAPABLE
      else -> null
    }
    if (rejection != null) return rejected(phone, rejection)

    val canonical = CanonicalPhoneNumber.parse(result.e164)
      ?: return rejected(phone, PhoneRejectionReason.MALFORMED)
    return PhoneAttempt(
      candidate = NormalizedPhone(
        canonical = canonical,
        maskedDisplay = mask(canonical),
        kind = result.kind,
        sourcePhoneIds = setOf(phone.phoneId),
        mobileSuggestion = phone.label == PhoneLabel.MOBILE || result.kind == PhoneNumberKind.MOBILE,
      ),
      rejection = null,
    )
  }

  private fun mergeSameDestination(phones: List<NormalizedPhone>): NormalizedPhone {
    val first = phones.first()
    val strongestKind = when {
      phones.any { it.kind == PhoneNumberKind.MOBILE } -> PhoneNumberKind.MOBILE
      else -> PhoneNumberKind.FIXED_LINE_OR_MOBILE
    }
    return first.copy(
      kind = strongestKind,
      sourcePhoneIds = phones.flatMapTo(sortedSetOf(), NormalizedPhone::sourcePhoneIds),
      mobileSuggestion = phones.any(NormalizedPhone::mobileSuggestion),
    )
  }

  private fun rejected(phone: RawContactPhone, reason: PhoneRejectionReason): PhoneAttempt =
    PhoneAttempt(candidate = null, rejection = RejectedPhone(phone.phoneId, reason))

  private fun normalizeRegion(region: String?): String? {
    val candidate = region?.trim()?.uppercase(Locale.ROOT) ?: return null
    return candidate.takeIf { it.matches(ISO_REGION) && it in ISO_COUNTRIES }
  }

  private fun mask(number: CanonicalPhoneNumber): String =
    "•••• ${number.value.takeLast(4)}"

  private data class PhoneAttempt(
    val candidate: NormalizedPhone?,
    val rejection: RejectedPhone?,
  )

  private companion object {
    val ISO_REGION = Regex("^[A-Z]{2}$")
    val ISO_COUNTRIES = Locale.getISOCountries().toSet()
    val EXPLICIT_EXTENSION = Regex(
      "(?i)(?:ext(?:ension)?\\.?|x|#)\\s*=*\\s*[0-9]+\\s*$",
    )
    const val MAX_RAW_PHONE_LENGTH = 200
    val SMS_CAPABLE_KINDS = setOf(PhoneNumberKind.MOBILE, PhoneNumberKind.FIXED_LINE_OR_MOBILE)

    fun validOpaqueId(value: String): Boolean =
      value.length in 1..200 && value.none { it.isISOControl() || it.isWhitespace() }
  }
}
