package com.yashsomani.birthdayautopilot.approvals

import com.yashsomani.birthdayautopilot.contacts.CanonicalPhoneNumber
import com.yashsomani.birthdayautopilot.messages.MessageLanguage
import com.yashsomani.birthdayautopilot.messages.MessageTemplate
import com.yashsomani.birthdayautopilot.messages.MessageTemplateValidator
import com.yashsomani.birthdayautopilot.messages.BuiltInMessageTemplates
import com.yashsomani.birthdayautopilot.messages.SmsEncoding
import com.yashsomani.birthdayautopilot.messages.SmsEncodingEstimate
import com.yashsomani.birthdayautopilot.messages.SmsLengthCalculator
import com.yashsomani.birthdayautopilot.messages.TemplatePlaceholderMode
import com.yashsomani.birthdayautopilot.messages.TemplateSource
import com.yashsomani.birthdayautopilot.planning.LeapDayPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmutableApprovalSnapshotTest {
  private val validator = MessageTemplateValidator()

  @Test
  fun `factory freezes every required field and self-validation succeeds`() {
    val material = baseMaterial()
    val snapshot = create(material)

    assertEquals(1, snapshot.schemaVersion)
    assertEquals("•••• 3210", snapshot.maskedPhoneDisplay)
    assertEquals(2, snapshot.segmentCount)
    assertEquals(64, snapshot.orderedPartsHash.length)
    assertEquals(64, snapshot.contentHash.length)
    assertTrue(ApprovalSnapshotValidator.validate(snapshot, material).valid)
    assertFalse(snapshot.toString().contains(snapshot.exactText))
    assertFalse(snapshot.toString().contains(snapshot.normalizedPhoneE164))
  }

  @Test
  fun `canonical approval hash is deterministic time-bound and length-delimited`() {
    val material = baseMaterial()
    val first = create(material, approvedAt = 1_720_000_000_000)
    val same = create(material, approvedAt = 1_720_000_000_000)
    val later = create(material, approvedAt = 1_720_000_000_001)

    assertEquals(first.contentHash, same.contentHash)
    assertNotEquals(first.contentHash, later.contentHash)

    val delimiterA = create(material.copy(recipientId = "a|b", carrierCostDisclosureVersion = "c"))
    val delimiterB = create(material.copy(recipientId = "a", carrierCostDisclosureVersion = "b-c"))
    assertNotEquals(delimiterA.contentHash, delimiterB.contentHash)
  }

  @Test
  fun `Unicode Hindi payload hashes and validates without locale-dependent conversion`() {
    val validation = validator.validateAndRender(
      BuiltInMessageTemplates.generic(MessageLanguage.HINDI),
      givenName = null,
    )
    val message = requireNotNull(ApprovedRenderedMessage.from(validation))
    val plan = requireNotNull(
      ApprovedSegmentPlan.bind(
        message.exactText,
        SmsEncoding.UNICODE,
        listOf(message.exactText),
        approvedSegmentCap = 2,
      ),
    )
    val material = baseMaterial().copy(message = message, segmentPlan = plan)
    val first = create(material)
    val second = create(material)
    assertEquals(first.contentHash, second.contentHash)
    assertTrue(ApprovalSnapshotValidator.validate(first, material).valid)
  }

  @Test
  fun `every material mutation invalidates the original approval`() {
    val original = baseMaterial()
    val snapshot = create(original)
    val alternatePhone = CanonicalPhoneNumber.parse("+919999999999")!!
    val changedText = "b" + original.message.exactText.drop(1)
    val changedMessage = approvedGeneric(changedText, "template-v1")
    val changedPlan = plan(changedText, splitAt = 153)
    val personalizedSameText = approvedPersonalizedSameText(original.message.exactText, "template-v1")
    val alternatePlan = plan(original.message.exactText, splitAt = 152)

    val mutations = listOf(
      original.copy(recipientId = "recipient-2") to ApprovalInvalidationReason.RECIPIENT_CHANGED,
      original.copy(normalizedPhone = alternatePhone) to ApprovalInvalidationReason.PHONE_CHANGED,
      original.copy(message = changedMessage, segmentPlan = changedPlan) to ApprovalInvalidationReason.MESSAGE_CHANGED,
      original.copy(message = approvedGeneric(original.message.exactText, "template-v2")) to
        ApprovalInvalidationReason.TEMPLATE_VERSION_CHANGED,
      original.copy(message = personalizedSameText) to ApprovalInvalidationReason.PLACEHOLDER_SEMANTICS_CHANGED,
      original.copy(birthday = original.birthday.copy(day = 15)) to ApprovalInvalidationReason.BIRTHDAY_CHANGED,
      original.copy(sendWindow = original.sendWindow.copy(startMinuteOfDay = 8 * 60)) to
        ApprovalInvalidationReason.SEND_WINDOW_CHANGED,
      original.copy(
        sendWindow = ApprovedSendWindow(
          startMinuteOfDay = 9 * 60,
          endMinuteOfDay = 11 * 60,
          graceEndMinuteOfDay = 12 * 60,
          latePolicy = ApprovedLatePolicy.SAME_DAY_GRACE,
        ),
      ) to ApprovalInvalidationReason.LATE_POLICY_CHANGED,
      original.copy(
        simPolicy = ApprovedSimPolicy(ApprovedSimPolicyKind.EXPLICIT_SUBSCRIPTION, 2),
      ) to ApprovalInvalidationReason.SIM_POLICY_OR_SUBSCRIPTION_CHANGED,
      original.copy(segmentPlan = alternatePlan) to ApprovalInvalidationReason.SEGMENT_PLAN_CHANGED,
      original.copy(carrierCostDisclosureVersion = "carrier-v2") to
        ApprovalInvalidationReason.CARRIER_DISCLOSURE_CHANGED,
      original.copy(consentDisclosureVersion = "consent-v2") to
        ApprovalInvalidationReason.CONSENT_DISCLOSURE_CHANGED,
    )

    mutations.forEachIndexed { index, (changed, expected) ->
      val result = ApprovalSnapshotValidator.validate(snapshot, changed)
      assertFalse("case $index unexpectedly valid", result.valid)
      assertTrue("case $index expected $expected but was ${result.reasons}", expected in result.reasons)
    }
  }

  @Test
  fun `tampering any frozen snapshot field without its hash fails closed`() {
    val material = baseMaterial()
    val snapshot = create(material)
    val mutations = listOf<(ImmutableApprovalSnapshot) -> ImmutableApprovalSnapshot>(
      { it.copy(schemaVersion = 2) },
      { it.copy(recipientId = "recipient-2") },
      { it.copy(normalizedPhoneE164 = "+919999999999") },
      { it.copy(maskedPhoneDisplay = "•••• 9999") },
      { it.copy(exactText = "b" + it.exactText.drop(1)) },
      { it.copy(sourceTemplateVersion = "template-v2") },
      { it.copy(placeholderMode = TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME) },
      { it.copy(birthdayMonth = 8) },
      { it.copy(birthdayDay = 15) },
      { it.copy(leapDayPolicy = LeapDayPolicy.MARCH_1) },
      { it.copy(windowStartMinuteOfDay = 8 * 60) },
      { it.copy(windowEndMinuteOfDay = 12 * 60) },
      { it.copy(graceEndMinuteOfDay = 12 * 60) },
      { it.copy(latePolicy = ApprovedLatePolicy.SAME_DAY_GRACE) },
      { it.copy(simPolicyKind = ApprovedSimPolicyKind.EXPLICIT_SUBSCRIPTION) },
      { it.copy(resolvedSubscriptionId = 2) },
      { it.copy(segmentCount = 1) },
      { it.copy(messageEncoding = SmsEncoding.UNICODE) },
      { it.copy(orderedPartsHash = "f".repeat(64)) },
      { it.copy(carrierCostDisclosureVersion = "carrier-v2") },
      { it.copy(consentDisclosureVersion = "consent-v2") },
      { it.copy(approvedAtEpochMillis = it.approvedAtEpochMillis + 1) },
      { it.copy(contentHash = "f".repeat(64)) },
    )

    mutations.forEachIndexed { index, mutation ->
      val result = ApprovalSnapshotValidator.validate(mutation(snapshot), material)
      assertFalse("tamper case $index", result.valid)
      assertTrue(
        "tamper case $index: ${result.reasons}",
        result.reasons == setOf(ApprovalInvalidationReason.SNAPSHOT_MALFORMED) ||
          result.reasons == setOf(ApprovalInvalidationReason.CONTENT_HASH_MISMATCH),
      )
    }
  }

  @Test
  fun `snapshot construction rejects invalid recurrence policy window SIM disclosure and time`() {
    val base = baseMaterial()
    val cases = listOf(
      base.copy(recipientId = "") to ApprovalBuildError.RECIPIENT_INVALID,
      base.copy(birthday = ApprovedBirthdayRecurrence(4, 31, null)) to ApprovalBuildError.BIRTHDAY_INVALID,
      base.copy(birthday = ApprovedBirthdayRecurrence(2, 29, null)) to ApprovalBuildError.LEAP_POLICY_INVALID,
      base.copy(birthday = ApprovedBirthdayRecurrence(7, 14, LeapDayPolicy.MARCH_1)) to
        ApprovalBuildError.LEAP_POLICY_INVALID,
      base.copy(sendWindow = base.sendWindow.copy(startMinuteOfDay = 700, endMinuteOfDay = 600)) to
        ApprovalBuildError.WINDOW_INVALID,
      base.copy(
        sendWindow = base.sendWindow.copy(
          graceEndMinuteOfDay = 700,
          latePolicy = ApprovedLatePolicy.SAME_DAY_WINDOW_ONLY,
        ),
      ) to ApprovalBuildError.LATE_POLICY_INVALID,
      base.copy(simPolicy = base.simPolicy.copy(resolvedSubscriptionId = -1)) to ApprovalBuildError.SIM_INVALID,
      base.copy(carrierCostDisclosureVersion = "") to ApprovalBuildError.DISCLOSURE_VERSION_INVALID,
    )
    cases.forEachIndexed { index, (material, expected) ->
      val result = ImmutableApprovalSnapshotFactory.create(material, 1_720_000_000_000)
      assertTrue("case $index", result is ApprovalBuildResult.Rejected)
      assertTrue(
        "case $index: ${(result as ApprovalBuildResult.Rejected).errors}",
        expected in result.errors,
      )
    }
    val invalidTime = ImmutableApprovalSnapshotFactory.create(base, 0) as ApprovalBuildResult.Rejected
    assertTrue(ApprovalBuildError.APPROVAL_TIME_INVALID in invalidTime.errors)
  }

  @Test
  fun `segment plan binding rejects fabricated count encoding text and cap`() {
    val text = "a".repeat(161)
    assertNull(ApprovedSegmentPlan.bind(text, SmsEncoding.GSM_7, listOf(text), 2))
    assertNull(ApprovedSegmentPlan.bind(text, SmsEncoding.GSM_7, listOf("", text), 2))
    assertNull(ApprovedSegmentPlan.bind(text, SmsEncoding.UNICODE, listOf(text.take(153), text.drop(153)), 2))
    assertNull(ApprovedSegmentPlan.bind(text, SmsEncoding.GSM_7, listOf(text.take(153), "wrong"), 2))
    assertNull(ApprovedSegmentPlan.bind(text, SmsEncoding.GSM_7, listOf(text.take(153), text.drop(153)), 1))

    val first = plan(text, 153)
    val differentBoundary = plan(text, 152)
    assertNotEquals(first.orderedPartsHash, differentBoundary.orderedPartsHash)

    val mismatchedMaterial = baseMaterial().copy(message = approvedGeneric("b".repeat(161), "template-v1"))
    val rejected = ImmutableApprovalSnapshotFactory.create(mismatchedMaterial, 1_720_000_000_000)
      as ApprovalBuildResult.Rejected
    assertTrue(ApprovalBuildError.SEGMENT_PLAN_INVALID in rejected.errors)
  }

  @Test
  fun `authoritative native national-language GSM plan can override conservative Unicode estimate`() {
    val nativeValidator = MessageTemplateValidator(
      SmsLengthCalculator { text ->
        SmsEncodingEstimate(
          encoding = SmsEncoding.GSM_7,
          characterCount = text.codePointCount(0, text.length),
          encodingUnitCount = text.codePointCount(0, text.length),
          segmentCount = 1,
        )
      },
    )
    val validation = nativeValidator.validateAndRender(
      MessageTemplate(
        version = "template-tr-v1",
        language = MessageLanguage.ENGLISH,
        placeholderMode = TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME,
        source = TemplateSource.USER_EDITED,
        text = "Happy birthday, {firstName}!",
      ),
      "Çağla",
    )
    val approvedMessage = requireNotNull(ApprovedRenderedMessage.from(validation))
    val nativePlan = requireNotNull(
      ApprovedSegmentPlan.bind(
        approvedMessage.exactText,
        SmsEncoding.GSM_7,
        listOf(approvedMessage.exactText),
        approvedSegmentCap = 2,
      ),
    )
    val material = baseMaterial().copy(message = approvedMessage, segmentPlan = nativePlan)
    assertTrue(ApprovalSnapshotValidator.validate(create(material), material).valid)
  }

  private fun baseMaterial(): ApprovalMaterial {
    val exactText = "a".repeat(161)
    return ApprovalMaterial(
      recipientId = "recipient-1",
      normalizedPhone = CanonicalPhoneNumber.parse("+919876543210")!!,
      message = approvedGeneric(exactText, "template-v1"),
      birthday = ApprovedBirthdayRecurrence(month = 7, day = 14, leapDayPolicy = null),
      sendWindow = ApprovedSendWindow(
        startMinuteOfDay = 9 * 60,
        endMinuteOfDay = 11 * 60,
        graceEndMinuteOfDay = null,
        latePolicy = ApprovedLatePolicy.SAME_DAY_WINDOW_ONLY,
      ),
      simPolicy = ApprovedSimPolicy(ApprovedSimPolicyKind.SYSTEM_DEFAULT, resolvedSubscriptionId = 1),
      segmentPlan = plan(exactText, splitAt = 153),
      carrierCostDisclosureVersion = "carrier-v1",
      consentDisclosureVersion = "consent-v1",
    )
  }

  private fun approvedGeneric(text: String, version: String): ApprovedRenderedMessage {
    val validation = validator.validateAndRender(
      MessageTemplate(
        version = version,
        language = MessageLanguage.ENGLISH,
        placeholderMode = TemplatePlaceholderMode.GENERIC_NO_NAME,
        source = TemplateSource.USER_EDITED,
        text = text,
      ),
      givenName = null,
    )
    return requireNotNull(ApprovedRenderedMessage.from(validation)) { validation.errors.toString() }
  }

  private fun approvedPersonalizedSameText(text: String, version: String): ApprovedRenderedMessage {
    val name = text.takeLast(100)
    val templatePrefix = text.dropLast(100)
    val validation = validator.validateAndRender(
      MessageTemplate(
        version = version,
        language = MessageLanguage.ENGLISH,
        placeholderMode = TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME,
        source = TemplateSource.USER_EDITED,
        text = "$templatePrefix{firstName}",
      ),
      givenName = name,
    )
    assertEquals(text, validation.preview?.exactText)
    return requireNotNull(ApprovedRenderedMessage.from(validation)) { validation.errors.toString() }
  }

  private fun plan(text: String, splitAt: Int): ApprovedSegmentPlan = requireNotNull(
    ApprovedSegmentPlan.bind(
      exactText = text,
      encoding = SmsEncoding.GSM_7,
      orderedParts = listOf(text.take(splitAt), text.drop(splitAt)),
      approvedSegmentCap = 2,
    ),
  )

  private fun create(
    material: ApprovalMaterial,
    approvedAt: Long = 1_720_000_000_000,
  ): ImmutableApprovalSnapshot = when (
    val result = ImmutableApprovalSnapshotFactory.create(material, approvedAt)
  ) {
    is ApprovalBuildResult.Created -> result.snapshot
    is ApprovalBuildResult.Rejected -> error(result.errors.toString())
  }
}
