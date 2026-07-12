package com.yashsomani.birthdayautopilot.approvals

import com.yashsomani.birthdayautopilot.contacts.CanonicalPhoneNumber
import com.yashsomani.birthdayautopilot.contacts.UnicodeTextSafety
import com.yashsomani.birthdayautopilot.messages.RenderedMessagePreview
import com.yashsomani.birthdayautopilot.messages.MessageContentPolicy
import com.yashsomani.birthdayautopilot.messages.MessageTemplateValidator
import com.yashsomani.birthdayautopilot.messages.SmsEncoding
import com.yashsomani.birthdayautopilot.messages.SmsEncodingEstimator
import com.yashsomani.birthdayautopilot.messages.TemplatePlaceholderMode
import com.yashsomani.birthdayautopilot.messages.TemplateValidationResult
import com.yashsomani.birthdayautopilot.planning.LeapDayPolicy
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.LocalDate

data class ApprovedBirthdayRecurrence(
  val month: Int,
  val day: Int,
  val leapDayPolicy: LeapDayPolicy?,
)

enum class ApprovedLatePolicy {
  SAME_DAY_WINDOW_ONLY,
  SAME_DAY_GRACE,
}

data class ApprovedSendWindow(
  val startMinuteOfDay: Int,
  val endMinuteOfDay: Int,
  val graceEndMinuteOfDay: Int?,
  val latePolicy: ApprovedLatePolicy,
)

enum class ApprovedSimPolicyKind {
  SYSTEM_DEFAULT,
  EXPLICIT_SUBSCRIPTION,
}

data class ApprovedSimPolicy(
  val kind: ApprovedSimPolicyKind,
  val resolvedSubscriptionId: Int,
)

class ApprovedRenderedMessage private constructor(
  val exactText: String,
  val templateVersion: String,
  val placeholderMode: TemplatePlaceholderMode,
) {
  companion object {
    fun from(validation: TemplateValidationResult): ApprovedRenderedMessage? {
      val preview = validation.preview ?: return null
      if (!validation.valid) return null
      val deterministicEstimate = SmsEncodingEstimator.estimate(preview.exactText)
      if (
        preview.validatorVersion != MessageTemplateValidator.VALIDATOR_VERSION ||
        preview.exactText.isBlank() ||
        preview.exactText != UnicodeTextSafety.normalizeNfc(preview.exactText) ||
        UnicodeTextSafety.containsUnsafeMessageCodePoint(preview.exactText) ||
        '{' in preview.exactText ||
        '}' in preview.exactText ||
        !TEMPLATE_VERSION.matches(preview.templateVersion) ||
        MessageContentPolicy.validate(preview.exactText).isNotEmpty() ||
        preview.metrics.characterCount != preview.exactText.codePointCount(0, preview.exactText.length) ||
        preview.metrics.encodingUnitCount <= 0 ||
        (deterministicEstimate.encoding == SmsEncoding.GSM_7 && preview.metrics.encoding != SmsEncoding.GSM_7) ||
        (deterministicEstimate.encoding == preview.metrics.encoding &&
          deterministicEstimate.segmentCount != preview.metrics.segmentCount) ||
        preview.metrics.segmentCount !in 1..2
      ) return null
      return from(preview)
    }

    private fun from(preview: RenderedMessagePreview): ApprovedRenderedMessage =
      ApprovedRenderedMessage(preview.exactText, preview.templateVersion, preview.placeholderMode)

    private val TEMPLATE_VERSION = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$")
  }
}

class ApprovedSegmentPlan private constructor(
  val segmentCount: Int,
  val encoding: SmsEncoding,
  val orderedPartsHash: String,
  private val boundExactTextHash: String,
) {
  internal fun isBoundTo(exactText: String): Boolean = ApprovalCanonicalHash.matches(
    boundExactTextHash,
    ApprovalCanonicalHash.hash(
      listOf("exactText" to exactText),
      domain = "BirthdayAutopilot.AndroidSegmentText.v1",
    ),
  )

  companion object {
    fun bind(
      exactText: String,
      encoding: SmsEncoding,
      orderedParts: List<String>,
      approvedSegmentCap: Int,
    ): ApprovedSegmentPlan? {
      if (approvedSegmentCap !in 1..MAX_SEGMENT_CAP) return null
      if (orderedParts.isEmpty() || orderedParts.size > approvedSegmentCap) return null
      if (orderedParts.any(String::isEmpty)) return null
      if (orderedParts.joinToString(separator = "") != exactText) return null
      val estimate = SmsEncodingEstimator.estimate(exactText)
      if (estimate.encoding == SmsEncoding.GSM_7 && encoding != SmsEncoding.GSM_7) return null
      if (estimate.encoding == encoding && estimate.segmentCount != orderedParts.size) return null
      val hash = ApprovalCanonicalHash.hash(
        listOf("encoding" to encoding.name) +
          orderedParts.mapIndexed { index, part -> "part-$index" to part },
        domain = "BirthdayAutopilot.AndroidSegmentPlan.v1",
      )
      val boundTextHash = ApprovalCanonicalHash.hash(
        listOf("exactText" to exactText),
        domain = "BirthdayAutopilot.AndroidSegmentText.v1",
      )
      return ApprovedSegmentPlan(orderedParts.size, encoding, hash, boundTextHash)
    }

    private const val MAX_SEGMENT_CAP = 2
  }
}

data class ApprovalMaterial(
  val recipientId: String,
  val normalizedPhone: CanonicalPhoneNumber,
  val message: ApprovedRenderedMessage,
  val birthday: ApprovedBirthdayRecurrence,
  val sendWindow: ApprovedSendWindow,
  val simPolicy: ApprovedSimPolicy,
  val segmentPlan: ApprovedSegmentPlan,
  val carrierCostDisclosureVersion: String,
  val consentDisclosureVersion: String,
) {
  override fun toString(): String = "ApprovalMaterial(<redacted>)"
}

/**
 * Immutable Android approval data. Persistence must encrypt normalizedPhoneE164 and exactText;
 * this pure domain value never authorizes a send unless [ApprovalSnapshotValidator] also matches
 * it against current material immediately before planning and again inside the submission gate.
 */
data class ImmutableApprovalSnapshot(
  val schemaVersion: Int,
  val recipientId: String,
  val normalizedPhoneE164: String,
  val maskedPhoneDisplay: String,
  val exactText: String,
  val sourceTemplateVersion: String,
  val placeholderMode: TemplatePlaceholderMode,
  val birthdayMonth: Int,
  val birthdayDay: Int,
  val leapDayPolicy: LeapDayPolicy?,
  val windowStartMinuteOfDay: Int,
  val windowEndMinuteOfDay: Int,
  val graceEndMinuteOfDay: Int?,
  val latePolicy: ApprovedLatePolicy,
  val simPolicyKind: ApprovedSimPolicyKind,
  val resolvedSubscriptionId: Int,
  val segmentCount: Int,
  val messageEncoding: SmsEncoding,
  val orderedPartsHash: String,
  val carrierCostDisclosureVersion: String,
  val consentDisclosureVersion: String,
  val approvedAtEpochMillis: Long,
  val contentHash: String,
) {
  override fun toString(): String = "ImmutableApprovalSnapshot(schemaVersion=$schemaVersion, privateFields=<redacted>)"
}

enum class ApprovalBuildError {
  RECIPIENT_INVALID,
  MESSAGE_INVALID,
  BIRTHDAY_INVALID,
  LEAP_POLICY_INVALID,
  WINDOW_INVALID,
  LATE_POLICY_INVALID,
  SIM_INVALID,
  SEGMENT_PLAN_INVALID,
  DISCLOSURE_VERSION_INVALID,
  APPROVAL_TIME_INVALID,
}

sealed interface ApprovalBuildResult {
  data class Created(val snapshot: ImmutableApprovalSnapshot) : ApprovalBuildResult
  data class Rejected(val errors: Set<ApprovalBuildError>) : ApprovalBuildResult
}

object ImmutableApprovalSnapshotFactory {
  const val SCHEMA_VERSION = 1

  fun create(material: ApprovalMaterial, approvedAtEpochMillis: Long): ApprovalBuildResult {
    val errors = ApprovalShapeValidator.validateMaterial(material, approvedAtEpochMillis)
    if (errors.isNotEmpty()) return ApprovalBuildResult.Rejected(errors)

    val unsigned = ImmutableApprovalSnapshot(
      schemaVersion = SCHEMA_VERSION,
      recipientId = material.recipientId,
      normalizedPhoneE164 = material.normalizedPhone.value,
      maskedPhoneDisplay = mask(material.normalizedPhone),
      exactText = material.message.exactText,
      sourceTemplateVersion = material.message.templateVersion,
      placeholderMode = material.message.placeholderMode,
      birthdayMonth = material.birthday.month,
      birthdayDay = material.birthday.day,
      leapDayPolicy = material.birthday.leapDayPolicy,
      windowStartMinuteOfDay = material.sendWindow.startMinuteOfDay,
      windowEndMinuteOfDay = material.sendWindow.endMinuteOfDay,
      graceEndMinuteOfDay = material.sendWindow.graceEndMinuteOfDay,
      latePolicy = material.sendWindow.latePolicy,
      simPolicyKind = material.simPolicy.kind,
      resolvedSubscriptionId = material.simPolicy.resolvedSubscriptionId,
      segmentCount = material.segmentPlan.segmentCount,
      messageEncoding = material.segmentPlan.encoding,
      orderedPartsHash = material.segmentPlan.orderedPartsHash,
      carrierCostDisclosureVersion = material.carrierCostDisclosureVersion,
      consentDisclosureVersion = material.consentDisclosureVersion,
      approvedAtEpochMillis = approvedAtEpochMillis,
      contentHash = "",
    )
    return ApprovalBuildResult.Created(
      unsigned.copy(contentHash = ApprovalCanonicalHash.hash(unsigned.canonicalFields())),
    )
  }

  private fun mask(number: CanonicalPhoneNumber): String = "•••• ${number.value.takeLast(4)}"
}

enum class ApprovalInvalidationReason {
  SNAPSHOT_MALFORMED,
  CONTENT_HASH_MISMATCH,
  RECIPIENT_CHANGED,
  PHONE_CHANGED,
  MESSAGE_CHANGED,
  TEMPLATE_VERSION_CHANGED,
  PLACEHOLDER_SEMANTICS_CHANGED,
  BIRTHDAY_CHANGED,
  SEND_WINDOW_CHANGED,
  LATE_POLICY_CHANGED,
  SIM_POLICY_OR_SUBSCRIPTION_CHANGED,
  SEGMENT_PLAN_CHANGED,
  CARRIER_DISCLOSURE_CHANGED,
  CONSENT_DISCLOSURE_CHANGED,
}

data class ApprovalValidation(
  val reasons: Set<ApprovalInvalidationReason>,
) {
  val valid: Boolean get() = reasons.isEmpty()
}

object ApprovalSnapshotValidator {
  fun validate(
    snapshot: ImmutableApprovalSnapshot,
    current: ApprovalMaterial,
  ): ApprovalValidation {
    if (!ApprovalShapeValidator.validSnapshotShape(snapshot)) {
      return ApprovalValidation(setOf(ApprovalInvalidationReason.SNAPSHOT_MALFORMED))
    }
    val expectedHash = ApprovalCanonicalHash.hash(snapshot.canonicalFields())
    if (!ApprovalCanonicalHash.matches(expectedHash, snapshot.contentHash)) {
      return ApprovalValidation(setOf(ApprovalInvalidationReason.CONTENT_HASH_MISMATCH))
    }

    val reasons = buildSet {
      if (snapshot.recipientId != current.recipientId) add(ApprovalInvalidationReason.RECIPIENT_CHANGED)
      if (snapshot.normalizedPhoneE164 != current.normalizedPhone.value) add(ApprovalInvalidationReason.PHONE_CHANGED)
      if (snapshot.exactText != current.message.exactText) add(ApprovalInvalidationReason.MESSAGE_CHANGED)
      if (snapshot.sourceTemplateVersion != current.message.templateVersion) {
        add(ApprovalInvalidationReason.TEMPLATE_VERSION_CHANGED)
      }
      if (snapshot.placeholderMode != current.message.placeholderMode) {
        add(ApprovalInvalidationReason.PLACEHOLDER_SEMANTICS_CHANGED)
      }
      if (
        snapshot.birthdayMonth != current.birthday.month ||
        snapshot.birthdayDay != current.birthday.day ||
        snapshot.leapDayPolicy != current.birthday.leapDayPolicy
      ) add(ApprovalInvalidationReason.BIRTHDAY_CHANGED)
      if (
        snapshot.windowStartMinuteOfDay != current.sendWindow.startMinuteOfDay ||
        snapshot.windowEndMinuteOfDay != current.sendWindow.endMinuteOfDay ||
        snapshot.graceEndMinuteOfDay != current.sendWindow.graceEndMinuteOfDay
      ) add(ApprovalInvalidationReason.SEND_WINDOW_CHANGED)
      if (snapshot.latePolicy != current.sendWindow.latePolicy) {
        add(ApprovalInvalidationReason.LATE_POLICY_CHANGED)
      }
      if (
        snapshot.simPolicyKind != current.simPolicy.kind ||
        snapshot.resolvedSubscriptionId != current.simPolicy.resolvedSubscriptionId
      ) add(ApprovalInvalidationReason.SIM_POLICY_OR_SUBSCRIPTION_CHANGED)
      if (
        snapshot.segmentCount != current.segmentPlan.segmentCount ||
        snapshot.messageEncoding != current.segmentPlan.encoding ||
        snapshot.orderedPartsHash != current.segmentPlan.orderedPartsHash
      ) add(ApprovalInvalidationReason.SEGMENT_PLAN_CHANGED)
      if (snapshot.carrierCostDisclosureVersion != current.carrierCostDisclosureVersion) {
        add(ApprovalInvalidationReason.CARRIER_DISCLOSURE_CHANGED)
      }
      if (snapshot.consentDisclosureVersion != current.consentDisclosureVersion) {
        add(ApprovalInvalidationReason.CONSENT_DISCLOSURE_CHANGED)
      }
    }
    return ApprovalValidation(reasons)
  }
}

private object ApprovalShapeValidator {
  private val VERSION = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$")
  private val SHA256 = Regex("^[0-9a-f]{64}$")

  fun validateMaterial(material: ApprovalMaterial, approvedAtEpochMillis: Long): Set<ApprovalBuildError> =
    buildSet {
      if (!validOpaque(material.recipientId)) add(ApprovalBuildError.RECIPIENT_INVALID)
      if (
        material.message.exactText.isBlank() ||
        material.message.exactText != UnicodeTextSafety.normalizeNfc(material.message.exactText) ||
        UnicodeTextSafety.containsUnsafeMessageCodePoint(material.message.exactText) ||
        '{' in material.message.exactText ||
        '}' in material.message.exactText ||
        !VERSION.matches(material.message.templateVersion)
      ) add(ApprovalBuildError.MESSAGE_INVALID)
      if (!validBirthday(material.birthday)) add(ApprovalBuildError.BIRTHDAY_INVALID)
      if (!validLeapPolicy(material.birthday)) add(ApprovalBuildError.LEAP_POLICY_INVALID)
      if (!validWindow(material.sendWindow)) add(ApprovalBuildError.WINDOW_INVALID)
      if (!validLatePolicy(material.sendWindow)) add(ApprovalBuildError.LATE_POLICY_INVALID)
      if (material.simPolicy.resolvedSubscriptionId < 0) add(ApprovalBuildError.SIM_INVALID)
      if (
        material.segmentPlan.segmentCount !in 1..2 ||
        !SHA256.matches(material.segmentPlan.orderedPartsHash) ||
        !material.segmentPlan.isBoundTo(material.message.exactText) ||
        SmsEncodingEstimator.estimate(material.message.exactText).let {
          (it.encoding == SmsEncoding.GSM_7 && material.segmentPlan.encoding != SmsEncoding.GSM_7) ||
            (it.encoding == material.segmentPlan.encoding && it.segmentCount != material.segmentPlan.segmentCount)
        }
      ) add(ApprovalBuildError.SEGMENT_PLAN_INVALID)
      if (
        !VERSION.matches(material.carrierCostDisclosureVersion) ||
        !VERSION.matches(material.consentDisclosureVersion)
      ) add(ApprovalBuildError.DISCLOSURE_VERSION_INVALID)
      if (approvedAtEpochMillis <= 0) add(ApprovalBuildError.APPROVAL_TIME_INVALID)
    }

  fun validSnapshotShape(snapshot: ImmutableApprovalSnapshot): Boolean {
    if (snapshot.schemaVersion != ImmutableApprovalSnapshotFactory.SCHEMA_VERSION) return false
    val canonicalPhone = CanonicalPhoneNumber.parse(snapshot.normalizedPhoneE164) ?: return false
    if (snapshot.maskedPhoneDisplay != "•••• ${canonicalPhone.value.takeLast(4)}") return false
    if (!SHA256.matches(snapshot.contentHash) || !SHA256.matches(snapshot.orderedPartsHash)) return false
    val materialShape =
      validOpaque(snapshot.recipientId) &&
        snapshot.exactText.isNotBlank() &&
        snapshot.exactText == UnicodeTextSafety.normalizeNfc(snapshot.exactText) &&
        !UnicodeTextSafety.containsUnsafeMessageCodePoint(snapshot.exactText) &&
        '{' !in snapshot.exactText &&
        '}' !in snapshot.exactText &&
        VERSION.matches(snapshot.sourceTemplateVersion) &&
        validBirthday(ApprovedBirthdayRecurrence(snapshot.birthdayMonth, snapshot.birthdayDay, snapshot.leapDayPolicy)) &&
        validLeapPolicy(ApprovedBirthdayRecurrence(snapshot.birthdayMonth, snapshot.birthdayDay, snapshot.leapDayPolicy)) &&
        validWindow(
          ApprovedSendWindow(
            snapshot.windowStartMinuteOfDay,
            snapshot.windowEndMinuteOfDay,
            snapshot.graceEndMinuteOfDay,
            snapshot.latePolicy,
          ),
        ) &&
        validLatePolicy(
          ApprovedSendWindow(
            snapshot.windowStartMinuteOfDay,
            snapshot.windowEndMinuteOfDay,
            snapshot.graceEndMinuteOfDay,
            snapshot.latePolicy,
          ),
        ) &&
        snapshot.resolvedSubscriptionId >= 0 &&
        snapshot.segmentCount in 1..2 &&
        SmsEncodingEstimator.estimate(snapshot.exactText).let {
          (it.encoding != SmsEncoding.GSM_7 || snapshot.messageEncoding == SmsEncoding.GSM_7) &&
            (it.encoding != snapshot.messageEncoding || it.segmentCount == snapshot.segmentCount)
        } &&
        VERSION.matches(snapshot.carrierCostDisclosureVersion) &&
        VERSION.matches(snapshot.consentDisclosureVersion) &&
        snapshot.approvedAtEpochMillis > 0
    return materialShape
  }

  private fun validBirthday(birthday: ApprovedBirthdayRecurrence): Boolean = try {
    LocalDate.of(2000, birthday.month, birthday.day)
    true
  } catch (_: DateTimeException) {
    false
  }

  private fun validLeapPolicy(birthday: ApprovedBirthdayRecurrence): Boolean =
    if (birthday.month == 2 && birthday.day == 29) {
      birthday.leapDayPolicy != null
    } else {
      birthday.leapDayPolicy == null
    }

  private fun validWindow(window: ApprovedSendWindow): Boolean =
    window.startMinuteOfDay in 0..1439 &&
      window.endMinuteOfDay in 1..1440 &&
      window.startMinuteOfDay < window.endMinuteOfDay &&
      (window.graceEndMinuteOfDay == null || window.graceEndMinuteOfDay in 1..1440)

  private fun validLatePolicy(window: ApprovedSendWindow): Boolean = when (window.latePolicy) {
    ApprovedLatePolicy.SAME_DAY_WINDOW_ONLY -> window.graceEndMinuteOfDay == null
    ApprovedLatePolicy.SAME_DAY_GRACE ->
      window.graceEndMinuteOfDay != null &&
        window.graceEndMinuteOfDay > window.endMinuteOfDay &&
        window.graceEndMinuteOfDay <= 1440
  }

  private fun validOpaque(value: String): Boolean =
    value.length in 1..300 && value.none { it.isISOControl() || it.isWhitespace() }
}

private fun ImmutableApprovalSnapshot.canonicalFields(): List<Pair<String, String>> = listOf(
  "schemaVersion" to schemaVersion.toString(),
  "recipientId" to recipientId,
  "normalizedPhoneE164" to normalizedPhoneE164,
  "maskedPhoneDisplay" to maskedPhoneDisplay,
  "exactText" to exactText,
  "sourceTemplateVersion" to sourceTemplateVersion,
  "placeholderMode" to placeholderMode.name,
  "birthdayMonth" to birthdayMonth.toString(),
  "birthdayDay" to birthdayDay.toString(),
  "leapDayPolicy" to (leapDayPolicy?.name ?: "NONE"),
  "windowStartMinuteOfDay" to windowStartMinuteOfDay.toString(),
  "windowEndMinuteOfDay" to windowEndMinuteOfDay.toString(),
  "graceEndMinuteOfDay" to (graceEndMinuteOfDay?.toString() ?: "NONE"),
  "latePolicy" to latePolicy.name,
  "simPolicyKind" to simPolicyKind.name,
  "resolvedSubscriptionId" to resolvedSubscriptionId.toString(),
  "segmentCount" to segmentCount.toString(),
  "messageEncoding" to messageEncoding.name,
  "orderedPartsHash" to orderedPartsHash,
  "carrierCostDisclosureVersion" to carrierCostDisclosureVersion,
  "consentDisclosureVersion" to consentDisclosureVersion,
  "approvedAtEpochMillis" to approvedAtEpochMillis.toString(),
)

private object ApprovalCanonicalHash {
  private const val APPROVAL_DOMAIN = "BirthdayAutopilot.AndroidApproval.v1"
  private val HEX = "0123456789abcdef".toCharArray()

  fun hash(
    fields: List<Pair<String, String>>,
    domain: String = APPROVAL_DOMAIN,
  ): String {
    val digest = MessageDigest.getInstance("SHA-256")
    updateLengthPrefixed(digest, domain)
    fields.forEach { (name, value) ->
      updateLengthPrefixed(digest, name)
      updateLengthPrefixed(digest, value)
    }
    val bytes = digest.digest()
    return CharArray(bytes.size * 2).also { output ->
      bytes.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        output[index * 2] = HEX[value ushr 4]
        output[index * 2 + 1] = HEX[value and 0x0f]
      }
    }.concatToString()
  }

  fun matches(expected: String, actual: String): Boolean =
    MessageDigest.isEqual(
      expected.toByteArray(StandardCharsets.US_ASCII),
      actual.toByteArray(StandardCharsets.US_ASCII),
    )

  private fun updateLengthPrefixed(digest: MessageDigest, value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
    digest.update(bytes)
  }
}
