package com.yashsomani.birthdayautopilot.configuration

import com.yashsomani.birthdayautopilot.automation.sms.AndroidSmsPolicyBinding
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.planning.RecurrencePlanner
import com.yashsomani.birthdayautopilot.storage.database.AutomationPolicyEntity
import com.yashsomani.birthdayautopilot.storage.database.ConfiguredBirthdayRow
import com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationRecordState
import com.yashsomani.birthdayautopilot.storage.database.MessageTemplateEntity
import com.yashsomani.birthdayautopilot.storage.database.PolicyRecordState
import com.yashsomani.birthdayautopilot.storage.database.TemplateSource
import com.yashsomani.birthdayautopilot.storage.database.TemplateValidationState
import com.yashsomani.birthdayautopilot.storage.database.TestJobEntity
import com.yashsomani.birthdayautopilot.storage.database.TestReceiptFactory
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationContractsTest {
  @Test
  fun `review hashes are domain separated deterministic and constant-time comparable`() {
    val payload = "{\"value\":\"private\"}"
    val first = ConfigurationCanonicalHash.payload("MESSAGE", payload)
    val second = ConfigurationCanonicalHash.payload("MESSAGE", payload)
    val otherKind = ConfigurationCanonicalHash.payload("POLICY", payload)

    assertEquals(64, first.length)
    assertEquals(first, second)
    assertNotEquals(first, otherKind)
    assertTrue(ConfigurationCanonicalHash.matches(first, second))
    assertFalse(ConfigurationCanonicalHash.matches(first, "not-a-hash"))
  }

  @Test
  fun `four-hundred-day simulation detects local-day and rolling capacity conflicts`() {
    val draft = ParsedWindowDraft(540, 660, null, dailyCap = 1)
    val birthdays = listOf(
      ConfiguredBirthdayRow("c1", 7, 12, null),
      ConfiguredBirthdayRow("c2", 7, 12, null),
    )
    val simulation = ConfigurationPolicyValidator.simulate(
      draft,
      birthdays,
      LocalDate.of(2026, 1, 1),
      ZoneId.of("Asia/Kolkata"),
      RecurrencePlanner(),
    )

    assertEquals(2, simulation.maximumLocalDay)
    assertEquals(2, simulation.maximumRolling24Hours)
    assertEquals(LocalDate.of(2026, 7, 12), simulation.firstConflictDate)
  }

  @Test
  fun `current test binding rejects policy and SIM drift even when receipt remains VALID`() {
    val installation = installation()
    val template = template()
    val policy = policy()
    val buildHash = AndroidTestConfigurationBinding.buildHash(
      installation.appVersionCode,
      installation.distributionChannel,
      installation.signingCertificateSha256,
    )
    val configHash = AndroidTestConfigurationBinding.configurationHash(
      template,
      policy,
      policy.resolvedSubscriptionId,
      AndroidSmsPolicyBinding.VERSION,
    )
    val test = testJob(buildHash, configHash)
    val receipt = TestReceiptFactory.create(
      test,
      installation,
      "receipt-1",
      AndroidSmsPolicyBinding.VERSION,
      2_000,
    )

    assertTrue(
      AndroidTestConfigurationBinding.matchesCurrent(
        test,
        installation,
        receipt,
        template,
        policy,
        installation.appVersionCode,
        installation.distributionChannel,
        installation.signingCertificateSha256,
        policy.resolvedSubscriptionId,
        AndroidSmsPolicyBinding.VERSION,
      ),
    )
    assertFalse(
      AndroidTestConfigurationBinding.matchesCurrent(
        test,
        installation,
        receipt,
        template,
        policy,
        installation.appVersionCode,
        installation.distributionChannel,
        installation.signingCertificateSha256,
        9,
        AndroidSmsPolicyBinding.VERSION,
      ),
    )
    assertFalse(
      AndroidTestConfigurationBinding.matchesCurrent(
        test,
        installation,
        receipt,
        template,
        policy.copy(revision = 2),
        installation.appVersionCode,
        installation.distributionChannel,
        installation.signingCertificateSha256,
        policy.resolvedSubscriptionId,
        AndroidSmsPolicyBinding.VERSION,
      ),
    )
  }

  private fun installation() = InstallationBindingEntity(
    installationId = "installation-1",
    accountId = "account-1",
    localSlot = 1,
    callbackGeneration = "callback-generation",
    state = InstallationRecordState.ACTIVE,
    accountMode = AccountMode.TEST_ONLY,
    senderEpoch = 1,
    resetGeneration = 1,
    ownerLeaseUntilMillis = 10_000,
    appVersionCode = 123,
    distributionChannel = "RESTRICTED_LAB",
    signingCertificateSha256 = "a".repeat(64),
    lastVerifiedServerMillis = 1_000,
    revision = 0,
    createdAtMillis = 1_000,
    updatedAtMillis = 1_000,
  )

  private fun template() = MessageTemplateEntity(
    templateId = "template-1",
    accountId = "account-1",
    source = TemplateSource.BUILT_IN,
    exactTemplateText = "Happy birthday, {firstName}!",
    languageTag = "en",
    tone = "warm",
    placeholderMode = "PERSONALIZED_FIRST_NAME",
    templateVersion = "template-v1",
    promptPolicyVersion = null,
    validatorVersion = "validator-v1",
    modelIdentifier = null,
    contentHash = "b".repeat(64),
    validationState = TemplateValidationState.VALID,
    revision = 1,
    createdAtMillis = 1_000,
    updatedAtMillis = 1_000,
    requestedSegmentCap = 2,
  )

  private fun policy() = AutomationPolicyEntity(
    policyId = "policy-1",
    accountId = "account-1",
    revision = 1,
    state = PolicyRecordState.ACTIVE,
    timeZoneId = "Asia/Kolkata",
    windowStartMinute = 540,
    windowEndMinute = 660,
    graceEndMinute = null,
    latePolicy = "SAME_DAY_WINDOW_ONLY",
    dailyCap = 10,
    simPolicyKind = "SYSTEM_DEFAULT",
    resolvedSubscriptionId = 4,
    roamingAllowed = false,
    policyVersion = "policy-v1",
    createdAtMillis = 1_000,
    invalidatedAtMillis = null,
    invalidationReason = null,
  )

  private fun testJob(buildHash: String, configHash: String) = TestJobEntity(
    testJobId = "test-1",
    accountId = "account-1",
    installationId = "installation-1",
    senderEpoch = 1,
    testRequestId = "request-1",
    configHash = configHash,
    destinationPrehash = "c".repeat(64),
    normalizedDestination = "+919999999999",
    maskedDestination = "•••• 9999",
    exactMessage = "Birthday Autopilot test",
    payloadHash = "d".repeat(64),
    simPolicyKind = "SYSTEM_DEFAULT",
    resolvedSubscriptionId = 4,
    segmentCount = 1,
    messageEncoding = "GSM_7",
    orderedPartsHash = "e".repeat(64),
    buildBindingHash = buildHash,
    appCheckPolicyVersion = AndroidTestConfigurationBinding.APP_CHECK_POLICY_VERSION,
    state = TestJobState.PASSED,
    revision = 1,
    foregroundConfirmationNonceHash = "f".repeat(64),
    foregroundConfirmedAtMillis = 1_000,
    createdAtMillis = 1_000,
    updatedAtMillis = 2_000,
    terminalAtMillis = 2_000,
    invalidationReason = null,
    retentionUntilMillis = 10_000,
  )
}
