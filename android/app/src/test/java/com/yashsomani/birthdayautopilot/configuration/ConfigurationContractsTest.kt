package com.yashsomani.birthdayautopilot.configuration

import com.yashsomani.birthdayautopilot.automation.sms.AndroidSmsPolicyBinding
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.lifecycle.TodayOccurrenceChoice
import com.yashsomani.birthdayautopilot.lifecycle.TodayOccurrenceChoicePolicy
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
import java.time.Duration
import java.time.Instant
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
    assertEquals(20, simulation.strictWindowSlotCapacity)
    assertEquals(LocalDate.of(2026, 7, 12), simulation.firstConflictDate)
  }

  @Test
  fun `adding cap plus one recipient after policy save is rejected without choosing a subset`() {
    val draft = ParsedWindowDraft(540, 660, null, dailyCap = 2)
    val saved = listOf(
      ConfiguredBirthdayRow("c1", 7, 12, null),
      ConfiguredBirthdayRow("c2", 7, 12, null),
    )
    val projected = ConfigurationPolicyValidator.projectConfiguredBirthday(
      contacts = saved,
      contactId = "c3",
      birthdayMonth = 7,
      birthdayDay = 12,
      leapDayPolicy = null,
      included = true,
    )
    val simulation = ConfigurationPolicyValidator.simulate(
      draft,
      projected,
      LocalDate.of(2026, 1, 1),
      ZoneId.of("Asia/Kolkata"),
      RecurrencePlanner(),
    )

    assertFalse(simulation.isAcceptableFor(draft))
    assertEquals(3, simulation.maximumLocalDay)
    assertEquals(LocalDate.of(2026, 7, 12), simulation.firstConflictDate)
    assertEquals(3, projected.size)
  }

  @Test
  fun `resume and birthday move projections rerun against the whole configured set`() {
    val draft = ParsedWindowDraft(540, 660, null, dailyCap = 1)
    val current = listOf(ConfiguredBirthdayRow("enabled", 8, 20, null))
    val resumed = ConfigurationPolicyValidator.projectConfiguredBirthday(
      contacts = current,
      contactId = "paused",
      birthdayMonth = 8,
      birthdayDay = 20,
      leapDayPolicy = null,
      included = true,
    )
    val moved = ConfigurationPolicyValidator.projectConfiguredBirthday(
      contacts = listOf(
        ConfiguredBirthdayRow("enabled", 8, 20, null),
        ConfiguredBirthdayRow("moving", 9, 1, null),
      ),
      contactId = "moving",
      birthdayMonth = 8,
      birthdayDay = 20,
      leapDayPolicy = null,
      included = true,
    )

    listOf(resumed, moved).forEach { projected ->
      val simulation = ConfigurationPolicyValidator.simulate(
        draft,
        projected,
        LocalDate.of(2026, 1, 1),
        ZoneId.of("Asia/Kolkata"),
        RecurrencePlanner(),
      )
      assertFalse(simulation.isAcceptableFor(draft))
      assertEquals(LocalDate.of(2026, 8, 20), simulation.firstConflictDate)
    }
  }

  @Test
  fun `four-hundred-day horizon includes the second annual occurrence when present`() {
    val draft = ParsedWindowDraft(540, 660, null, dailyCap = 1)
    val simulation = ConfigurationPolicyValidator.simulate(
      draft,
      listOf(ConfiguredBirthdayRow("c1", 1, 2, null)),
      LocalDate.of(2026, 1, 1),
      ZoneId.of("UTC"),
      RecurrencePlanner(),
    )

    assertTrue(simulation.isAcceptableFor(draft))
    assertEquals(2, simulation.simulatedOccurrenceCount)
    assertEquals(1, simulation.maximumLocalDay)
    assertEquals(1, simulation.maximumRolling24Hours)
  }

  @Test
  fun `strict window slot capacity rounds up exact six-minute submission buckets`() {
    assertEquals(
      5,
      ConfigurationPolicyValidator.strictWindowSlotCapacity(
        ParsedWindowDraft(540, 570, null, dailyCap = 20),
      ),
    )
    assertEquals(
      6,
      ConfigurationPolicyValidator.strictWindowSlotCapacity(
        ParsedWindowDraft(540, 576, null, dailyCap = 20),
      ),
    )
    assertEquals(
      20,
      ConfigurationPolicyValidator.strictWindowSlotCapacity(
        ParsedWindowDraft(540, 660, null, dailyCap = 20),
      ),
    )
  }

  @Test
  fun `simulation applies local cap and strict slots independently of rolling ceiling`() {
    val sixOnOneDay = (1..6).map { index ->
      ConfiguredBirthdayRow("same-day-$index", 7, 12, null)
    }
    val slotLimited = ConfigurationPolicyValidator.simulate(
      ParsedWindowDraft(540, 570, null, dailyCap = 20),
      sixOnOneDay,
      LocalDate.of(2026, 1, 1),
      ZoneId.of("Asia/Kolkata"),
      RecurrencePlanner(),
    )
    val userCapLimited = ConfigurationPolicyValidator.simulate(
      ParsedWindowDraft(540, 660, null, dailyCap = 5),
      sixOnOneDay,
      LocalDate.of(2026, 1, 1),
      ZoneId.of("Asia/Kolkata"),
      RecurrencePlanner(),
    )

    assertFalse(slotLimited.isAcceptableFor(ParsedWindowDraft(540, 570, null, dailyCap = 20)))
    assertFalse(userCapLimited.isAcceptableFor(ParsedWindowDraft(540, 660, null, dailyCap = 5)))
    assertEquals(LocalDate.of(2026, 7, 12), slotLimited.firstConflictDate)
    assertEquals(LocalDate.of(2026, 7, 12), userCapLimited.firstConflictDate)
  }

  @Test
  fun `rolling ceiling is fixed at twenty rather than the user local-day cap`() {
    val tenAcrossTwoDates = (1..5).map { index ->
      ConfiguredBirthdayRow("first-$index", 3, 13, null)
    } + (1..5).map { index ->
      ConfiguredBirthdayRow("second-$index", 3, 14, null)
    }
    val simulation = ConfigurationPolicyValidator.simulate(
      ParsedWindowDraft(540, 660, null, dailyCap = 5),
      tenAcrossTwoDates,
      LocalDate.of(2027, 1, 1),
      ZoneId.of("America/New_York"),
      RecurrencePlanner(),
    )

    // The 2027 spring-forward transition makes these local starts 23 hours apart. Each local
    // day respects cap 5; a rolling count of 10 is still valid because the safety ceiling is 20.
    assertEquals(5, simulation.maximumLocalDay)
    assertEquals(10, simulation.maximumRolling24Hours)
    assertTrue(simulation.isAcceptableFor(ParsedWindowDraft(540, 660, null, dailyCap = 5)))
    assertNull(simulation.firstConflictDate)
  }

  @Test
  fun `rolling count above twenty conflicts even when both local days fit`() {
    val twentyOneAcrossTwoDates = (1..10).map { index ->
      ConfiguredBirthdayRow("first-overflow-$index", 3, 13, null)
    } + (1..11).map { index ->
      ConfiguredBirthdayRow("second-overflow-$index", 3, 14, null)
    }
    val draft = ParsedWindowDraft(540, 780, null, dailyCap = 20)
    val simulation = ConfigurationPolicyValidator.simulate(
      draft,
      twentyOneAcrossTwoDates,
      LocalDate.of(2027, 1, 1),
      ZoneId.of("America/New_York"),
      RecurrencePlanner(),
    )

    assertEquals(11, simulation.maximumLocalDay)
    assertEquals(21, simulation.maximumRolling24Hours)
    assertFalse(simulation.isAcceptableFor(draft))
    assertEquals(LocalDate.of(2027, 3, 14), simulation.firstConflictDate)
  }

  @Test
  fun `DST gaps advance and overlaps choose the first instant in representative zones`() {
    data class DstCase(
      val zone: String,
      val date: LocalDate,
      val minute: Int,
      val expected: String,
    )

    val cases = listOf(
      // One-hour northern-hemisphere transitions.
      DstCase("America/New_York", LocalDate.of(2027, 3, 14), 2 * 60 + 30, "2027-03-14T07:00:00Z"),
      DstCase("America/New_York", LocalDate.of(2027, 11, 7), 1 * 60 + 30, "2027-11-07T05:30:00Z"),
      DstCase("Europe/Berlin", LocalDate.of(2027, 3, 28), 2 * 60 + 30, "2027-03-28T01:00:00Z"),
      DstCase("Europe/Berlin", LocalDate.of(2027, 10, 31), 2 * 60 + 30, "2027-10-31T00:30:00Z"),
      // Lord Howe proves the algorithm does not assume every DST transition is one hour.
      DstCase("Australia/Lord_Howe", LocalDate.of(2027, 10, 3), 2 * 60 + 15, "2027-10-02T15:30:00Z"),
      DstCase("Australia/Lord_Howe", LocalDate.of(2027, 4, 4), 1 * 60 + 45, "2027-04-03T14:45:00Z"),
    )

    for (case in cases) {
      assertEquals(
        "${case.zone} ${case.date} minute ${case.minute}",
        Instant.parse(case.expected),
        ConfigurationPolicyValidator.resolveStart(
          case.date,
          case.minute,
          ZoneId.of(case.zone),
        ),
      )
    }
  }

  @Test
  fun `UTC plus fourteen to UTC minus twelve travel recalculates every window phase`() {
    val civilDate = LocalDate.of(2026, 7, 12)
    val east = ZoneId.of("Pacific/Kiritimati")
    val west = ZoneId.of("Etc/GMT+12")
    val eastStart = ConfigurationPolicyValidator.resolveStart(civilDate, 9 * 60, east)
    val eastEnd = ConfigurationPolicyValidator.resolveStart(civilDate, 11 * 60, east)
    val westStart = ConfigurationPolicyValidator.resolveStart(civilDate, 9 * 60, west)
    val westEnd = ConfigurationPolicyValidator.resolveStart(civilDate, 11 * 60, west)

    assertEquals(Instant.parse("2026-07-11T19:00:00Z"), eastStart)
    assertEquals(Instant.parse("2026-07-11T21:00:00Z"), eastEnd)
    assertEquals(Instant.parse("2026-07-12T21:00:00Z"), westStart)
    assertEquals(Instant.parse("2026-07-12T23:00:00Z"), westEnd)
    assertEquals(26L, Duration.between(eastStart, westStart).toHours())

    fun phase(now: Instant, start: Instant, end: Instant, resetClear: Boolean = true) =
      TodayOccurrenceChoicePolicy.evaluate(
        now.toEpochMilli(),
        start.toEpochMilli(),
        end.toEpochMilli(),
        resetClear,
      ).primary

    assertEquals(
      TodayOccurrenceChoice.NEXT_YEAR,
      phase(eastStart.minusMillis(1), eastStart, eastEnd),
    )
    assertEquals(
      TodayOccurrenceChoice.NORMAL_PATH,
      phase(eastStart.plusSeconds(60), eastStart, eastEnd),
    )
    assertEquals(
      TodayOccurrenceChoice.SYSTEM_COMPOSER,
      phase(eastEnd, eastStart, eastEnd),
    )
    // The same trusted instant is after the UTC+14 window but still before the recalculated
    // UTC-12 window. No prior-zone eligibility leaks across travel.
    assertEquals(
      TodayOccurrenceChoice.NEXT_YEAR,
      phase(eastEnd.plusSeconds(60), westStart, westEnd),
    )
    assertEquals(
      TodayOccurrenceChoice.NORMAL_PATH,
      phase(westStart.plusSeconds(60), westStart, westEnd),
    )
    assertEquals(
      TodayOccurrenceChoice.SYSTEM_COMPOSER,
      phase(westEnd, westStart, westEnd),
    )
    assertEquals(
      TodayOccurrenceChoice.SYSTEM_COMPOSER,
      phase(westStart.plusSeconds(60), westStart, westEnd, resetClear = false),
    )
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
