package com.yashsomani.birthdayautopilot.contacts

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.ShortNumberInfo
import com.google.i18n.phonenumbers.ShortNumberInfo.ShortNumberCost
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import java.util.Locale

/**
 * Production phone metadata adapter backed by Google's maintained libphonenumber metadata.
 *
 * Parsing is deliberately not treated as evidence that a destination can receive SMS. The pure
 * [PhoneNormalizer] consumes the validity, type, emergency, short-code, premium-rate, and
 * extension signals returned here and permits only MOBILE or FIXED_LINE_OR_MOBILE numbers.
 */
class LibPhoneNumberMetadataEngine(
  private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
  private val shortNumberInfo: ShortNumberInfo = ShortNumberInfo.getInstance(),
) : PhoneMetadataEngine {
  override fun analyze(rawValue: String, region: String?): PhoneMetadataResult {
    val raw = rawValue.trim()
    if (raw.isEmpty() || raw.length > MAX_RAW_LENGTH) return PhoneMetadataResult.Malformed

    val normalizedRegion = region?.uppercase(Locale.ROOT)
    if (normalizedRegion != null && normalizedRegion !in phoneUtil.supportedRegions) {
      return PhoneMetadataResult.Malformed
    }
    if (normalizedRegion == null && !raw.startsWith('+')) {
      return PhoneMetadataResult.Ambiguous
    }

    val number = try {
      phoneUtil.parse(raw, normalizedRegion)
    } catch (_: NumberParseException) {
      return PhoneMetadataResult.Malformed
    }

    val parsedRegion = phoneUtil.getRegionCodeForNumber(number)
    val candidateRegions = candidateRegions(number, normalizedRegion, parsedRegion)
    val nationalNumber = phoneUtil.getNationalSignificantNumber(number)
    val emergency = candidateRegions.any { candidate ->
      shortNumberInfo.isEmergencyNumber(raw, candidate) ||
        shortNumberInfo.connectsToEmergencyNumber(raw, candidate) ||
        shortNumberInfo.isEmergencyNumber(nationalNumber, candidate) ||
        shortNumberInfo.connectsToEmergencyNumber(nationalNumber, candidate)
    }
    val shortCode = shortNumberInfo.isPossibleShortNumber(number) ||
      shortNumberInfo.isValidShortNumber(number) ||
      candidateRegions.any { candidate ->
        shortNumberInfo.isPossibleShortNumberForRegion(number, candidate) ||
          shortNumberInfo.isValidShortNumberForRegion(number, candidate)
      }
    val premiumShortCode = candidateRegions.any { candidate ->
      shortNumberInfo.getExpectedCostForRegion(number, candidate) == ShortNumberCost.PREMIUM_RATE
    }

    // A shared calling code that cannot be resolved to a region is not safe to normalize. Short
    // and emergency classifications above still surface as explicit blockers instead of being
    // hidden behind ambiguity.
    val unresolvedSharedCallingCode = normalizedRegion == null &&
      parsedRegion == null &&
      candidateRegions.size > 1
    if (unresolvedSharedCallingCode && !emergency && !shortCode) {
      return PhoneMetadataResult.Ambiguous
    }

    val type = phoneUtil.getNumberType(number)
    val kind = when {
      premiumShortCode -> PhoneNumberKind.PREMIUM_RATE
      else -> type.toDomainKind()
    }
    val e164 = try {
      phoneUtil.format(number, PhoneNumberFormat.E164)
    } catch (_: IllegalArgumentException) {
      return PhoneMetadataResult.Malformed
    }

    return PhoneMetadataResult.Parsed(
      e164 = e164,
      kind = kind,
      possible = phoneUtil.isPossibleNumber(number),
      valid = phoneUtil.isValidNumber(number),
      emergency = emergency,
      shortCode = shortCode,
      extension = number.extension.takeIf(String::isNotBlank),
    )
  }

  private fun candidateRegions(
    number: PhoneNumber,
    suppliedRegion: String?,
    parsedRegion: String?,
  ): List<String> {
    suppliedRegion?.let { return listOf(it) }
    parsedRegion?.takeUnless { it == PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY }
      ?.let { return listOf(it) }
    return phoneUtil.getRegionCodesForCountryCode(number.countryCode)
      .filterNot { it == PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY }
      .distinct()
      .sorted()
  }

  private fun PhoneNumberType.toDomainKind(): PhoneNumberKind = when (this) {
    PhoneNumberType.MOBILE -> PhoneNumberKind.MOBILE
    PhoneNumberType.FIXED_LINE_OR_MOBILE -> PhoneNumberKind.FIXED_LINE_OR_MOBILE
    PhoneNumberType.FIXED_LINE -> PhoneNumberKind.FIXED_LINE
    PhoneNumberType.PREMIUM_RATE -> PhoneNumberKind.PREMIUM_RATE
    PhoneNumberType.TOLL_FREE -> PhoneNumberKind.TOLL_FREE
    PhoneNumberType.VOIP -> PhoneNumberKind.VOIP
    PhoneNumberType.PAGER -> PhoneNumberKind.PAGER
    PhoneNumberType.PERSONAL_NUMBER -> PhoneNumberKind.PERSONAL
    PhoneNumberType.SHARED_COST,
    PhoneNumberType.UAN,
    PhoneNumberType.VOICEMAIL,
    PhoneNumberType.UNKNOWN,
    -> PhoneNumberKind.UNKNOWN
  }

  private companion object {
    const val MAX_RAW_LENGTH = 200
  }
}
