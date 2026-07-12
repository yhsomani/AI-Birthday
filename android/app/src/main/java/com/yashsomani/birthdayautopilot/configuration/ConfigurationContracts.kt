package com.yashsomani.birthdayautopilot.configuration

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.yashsomani.birthdayautopilot.R
import com.yashsomani.birthdayautopilot.planning.BirthdayRule
import com.yashsomani.birthdayautopilot.planning.BirthdayCapacityPolicy
import com.yashsomani.birthdayautopilot.planning.LeapDayPolicy
import com.yashsomani.birthdayautopilot.planning.RecurrencePlanner
import com.yashsomani.birthdayautopilot.storage.database.ConfiguredBirthdayRow
import com.yashsomani.birthdayautopilot.storage.database.AutomationPolicyEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationRecordState
import com.yashsomani.birthdayautopilot.storage.database.MessageTemplateEntity
import com.yashsomani.birthdayautopilot.storage.database.TestJobEntity
import com.yashsomani.birthdayautopilot.storage.database.TestReceiptBindingValidator
import com.yashsomani.birthdayautopilot.storage.database.TestReceiptEntity
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal sealed interface ConfigurationOutcome {
  data class Success(
    val payload: JSONObject,
    val invalidatedAreas: Set<String> = emptySet(),
  ) : ConfigurationOutcome

  data class Problem(val payload: JSONObject) : ConfigurationOutcome

  data object InvalidRequest : ConfigurationOutcome
}

internal fun interface ConfigurationClock {
  fun nowMillis(): Long
}

internal fun interface ConfigurationZoneProvider {
  fun zoneId(): ZoneId
}

internal sealed interface SubscriptionResolution {
  data class Ready(val subscriptionId: Int, val label: String) : SubscriptionResolution
  data class Rejected(val code: String) : SubscriptionResolution
}

internal fun interface ConfigurationSubscriptionResolver {
  fun resolveDefault(): SubscriptionResolution
}

internal object ConfigurationDefaultSimLabel {
  fun format(context: Context, slotNumber: Int?): String = slotNumber
    ?.takeIf { it > 0 }
    ?.let { context.getString(R.string.configuration_default_sim_slot, it) }
    ?: context.getString(R.string.configuration_default_sms_sim)
}

/** Default-SIM resolution used only for foreground configuration and approval. */
internal class AndroidConfigurationSubscriptionResolver(
  context: Context,
) : ConfigurationSubscriptionResolver {
  private val appContext = context.applicationContext

  @SuppressLint("MissingPermission")
  override fun resolveDefault(): SubscriptionResolution {
    val subscriptionId = try {
      SubscriptionManager.getDefaultSmsSubscriptionId()
    } catch (_: RuntimeException) {
      return SubscriptionResolution.Rejected("no-active-sim")
    } catch (_: LinkageError) {
      return SubscriptionResolution.Rejected("no-active-sim")
    }
    if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
      return SubscriptionResolution.Rejected("no-active-sim")
    }
    // SYSTEM_DEFAULT authoring can bind the public default subscription identity before the
    // just-in-time telephony permission flow. When READ_PHONE_STATE is present, strengthen this
    // foreground check with the active-subscription list. The later Test and every send boundary
    // still require the permission and repeat the exact active-subscription validation.
    if (
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) !=
      PackageManager.PERMISSION_GRANTED
    ) return SubscriptionResolution.Ready(
      subscriptionId,
      ConfigurationDefaultSimLabel.format(appContext, null),
    )
    val active = try {
      appContext.getSystemService(SubscriptionManager::class.java)
        .activeSubscriptionInfoList
        .orEmpty()
    } catch (_: SecurityException) {
      return SubscriptionResolution.Rejected("permission-denied")
    } catch (_: RuntimeException) {
      return SubscriptionResolution.Rejected("no-active-sim")
    } catch (_: LinkageError) {
      return SubscriptionResolution.Rejected("no-active-sim")
    }
    val selected = active.singleOrNull { it.subscriptionId == subscriptionId }
      ?: return SubscriptionResolution.Rejected("sim-invalid")
    val slot = selected.simSlotIndex.takeIf { it >= 0 }?.plus(1)
    return SubscriptionResolution.Ready(
      subscriptionId,
      ConfigurationDefaultSimLabel.format(appContext, slot),
    )
  }
}

internal object ConfigurationCanonicalHash {
  private val HEX = "0123456789abcdef".toCharArray()

  fun payload(kind: String, payloadJson: String): String = hash(
    "BirthdayAutopilot.ConfigurationReview.v1",
    listOf(kind, payloadJson),
  )

  fun content(domain: String, values: List<String>): String = hash(domain, values)

  fun matches(expected: String, actual: String): Boolean =
    expected.matches(Regex("^[0-9a-f]{64}$")) &&
      actual.matches(Regex("^[0-9a-f]{64}$")) &&
      MessageDigest.isEqual(
        expected.toByteArray(StandardCharsets.US_ASCII),
        actual.toByteArray(StandardCharsets.US_ASCII),
      )

  private fun hash(domain: String, values: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    (listOf(domain) + values).forEach { value ->
      val bytes = value.toByteArray(StandardCharsets.UTF_8)
      digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
      digest.update(bytes)
    }
    val result = digest.digest()
    return CharArray(result.size * 2).also { output ->
      result.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        output[index * 2] = HEX[value ushr 4]
        output[index * 2 + 1] = HEX[value and 0x0f]
      }
    }.concatToString()
  }
}

/** Shared creation/readiness binding for the mandatory foreground Test. */
internal object AndroidTestConfigurationBinding {
  const val APP_CHECK_POLICY_VERSION = "firebase-app-check-limited-use-v1"

  fun buildHash(
    appVersionCode: Long,
    distributionChannel: String,
    signingCertificateSha256: String,
  ): String = ConfigurationCanonicalHash.content(
    "BirthdayAutopilot.AndroidTestBuild.v1",
    listOf(
      appVersionCode.toString(),
      distributionChannel,
      signingCertificateSha256,
    ),
  )

  fun configurationHash(
    template: MessageTemplateEntity,
    policy: AutomationPolicyEntity,
    resolvedSubscriptionId: Int,
    smsPolicyVersion: String,
  ): String = ConfigurationCanonicalHash.content(
    "BirthdayAutopilot.AndroidTestConfiguration.v1",
    listOf(
      template.templateId,
      template.contentHash,
      template.templateVersion,
      template.requestedSegmentCap.toString(),
      policy.policyId,
      policy.revision.toString(),
      policy.timeZoneId,
      policy.windowStartMinute.toString(),
      policy.windowEndMinute.toString(),
      policy.graceEndMinute?.toString() ?: "NONE",
      policy.latePolicy,
      policy.dailyCap.toString(),
      policy.simPolicyKind,
      policy.resolvedSubscriptionId.toString(),
      resolvedSubscriptionId.toString(),
      APP_CHECK_POLICY_VERSION,
      smsPolicyVersion,
    ),
  )

  fun matchesCurrent(
    test: TestJobEntity,
    installation: InstallationBindingEntity,
    receipt: TestReceiptEntity,
    template: MessageTemplateEntity,
    policy: AutomationPolicyEntity,
    currentAppVersionCode: Long,
    currentDistributionChannel: String,
    currentSigningCertificateSha256: String,
    currentResolvedSubscriptionId: Int,
    currentSmsPolicyVersion: String,
  ): Boolean {
    if (
      installation.localSlot != 1 ||
      installation.state != InstallationRecordState.ACTIVE ||
      installation.senderEpoch == null ||
      installation.appVersionCode != currentAppVersionCode ||
      installation.distributionChannel != currentDistributionChannel ||
      installation.signingCertificateSha256 != currentSigningCertificateSha256 ||
      test.state != TestJobState.PASSED ||
      test.simPolicyKind != policy.simPolicyKind ||
      policy.resolvedSubscriptionId != currentResolvedSubscriptionId ||
      receipt.resolvedSubscriptionId != currentResolvedSubscriptionId ||
      test.resolvedSubscriptionId != currentResolvedSubscriptionId ||
      receipt.smsPolicyVersion != currentSmsPolicyVersion ||
      receipt.appCheckPolicyVersion != APP_CHECK_POLICY_VERSION ||
      test.appCheckPolicyVersion != APP_CHECK_POLICY_VERSION ||
      test.buildBindingHash != buildHash(
        currentAppVersionCode,
        currentDistributionChannel,
        currentSigningCertificateSha256,
      ) ||
      receipt.buildBindingHash != test.buildBindingHash ||
      test.configHash != configurationHash(
        template,
        policy,
        currentResolvedSubscriptionId,
        currentSmsPolicyVersion,
      ) ||
      receipt.configHash != test.configHash
    ) return false
    return TestReceiptBindingValidator.matches(test, installation, receipt)
  }
}

internal data class ParsedWindowDraft(
  val startMinute: Int,
  val endMinute: Int,
  val graceEndMinute: Int?,
  val dailyCap: Int,
) {
  val latePolicy: String = if (graceEndMinute == null) {
    "SAME_DAY_WINDOW_ONLY"
  } else {
    "SAME_DAY_GRACE"
  }
}

internal data class PolicySimulation(
  val simulatedOccurrenceCount: Int,
  val maximumLocalDay: Int,
  val maximumRolling24Hours: Int,
  /** Number of Arm submissions that can fit before the strict effective window end. */
  val strictWindowSlotCapacity: Int,
  val firstConflictDate: LocalDate?,
) {
  fun isAcceptableFor(draft: ParsedWindowDraft): Boolean =
    maximumLocalDay <= minOf(draft.dailyCap, strictWindowSlotCapacity) &&
      maximumRolling24Hours <= ConfigurationPolicyValidator.ROLLING_24_HOUR_CEILING
}

internal object ConfigurationPolicyValidator {
  fun projectConfiguredBirthday(
    contacts: List<ConfiguredBirthdayRow>,
    contactId: String,
    birthdayMonth: Int?,
    birthdayDay: Int?,
    leapDayPolicy: String?,
    included: Boolean,
  ): List<ConfiguredBirthdayRow> = contacts.filterNot { it.contactId == contactId } + buildList {
    if (included && birthdayMonth != null && birthdayDay != null) {
      add(ConfiguredBirthdayRow(contactId, birthdayMonth, birthdayDay, leapDayPolicy))
    }
  }

  fun parse(draft: JSONObject): Pair<ParsedWindowDraft?, JSONArray> {
    val issues = JSONArray()
    if (draft.keyNames() != setOf("primaryStart", "primaryEnd", "latePolicy", "dailyCap")) {
      issues.put(issue("window", "invalid-window"))
      return null to issues
    }
    val start = parseMinute(draft.optString("primaryStart"))
    val end = parseMinute(draft.optString("primaryEnd"))
    val dailyCap = draft.optInt("dailyCap", -1)
    if (dailyCap !in 1..20) issues.put(issue("dailyCap", "invalid-daily-cap"))
    val late = draft.optJSONObject("latePolicy")
    var grace: Int? = null
    when (late?.optString("kind")) {
      "none" -> if (late.keyNames() != setOf("kind")) {
        issues.put(issue("window", "invalid-window"))
      }
      "same-day-grace" -> {
        if (late.keyNames() != setOf("kind", "graceEnd")) {
          issues.put(issue("window", "invalid-window"))
        }
        grace = parseMinute(late.optString("graceEnd"))
      }
      else -> issues.put(issue("window", "invalid-window"))
    }
    if (start == null || end == null || start >= end) {
      issues.put(issue("window", "invalid-window"))
    } else {
      val primaryLength = end - start
      val totalLength = (grace ?: end) - start
      if (
        primaryLength !in MIN_WINDOW_MINUTES..MAX_WINDOW_MINUTES ||
        totalLength !in MIN_WINDOW_MINUTES..MAX_WINDOW_MINUTES ||
        (grace != null && grace <= end)
      ) issues.put(issue("window", "invalid-window"))
    }
    return if (issues.length() == 0) {
      ParsedWindowDraft(start!!, end!!, grace, dailyCap) to issues
    } else {
      null to deduplicateIssues(issues)
    }
  }

  fun simulate(
    draft: ParsedWindowDraft,
    contacts: List<ConfiguredBirthdayRow>,
    today: LocalDate,
    zoneId: ZoneId,
    planner: RecurrencePlanner,
  ): PolicySimulation {
    val strictWindowSlotCapacity = strictWindowSlotCapacity(draft)
    val endDate = today.plusDays(SIMULATED_DAYS - 1L)
    val occurrences = contacts.flatMap { contact ->
      val leap = contact.leapDayPolicy?.let { runCatching { LeapDayPolicy.valueOf(it) }.getOrNull() }
      val rule = BirthdayRule(contact.birthdayMonth, contact.birthdayDay, leap)
      try {
        (today.year..endDate.year).mapNotNull { year ->
          planner.occurrenceInYear(year, rule)
            ?.takeUnless { it.isBefore(today) || it.isAfter(endDate) }
            ?.let { occurrence -> occurrence to resolveStart(occurrence, draft.startMinute, zoneId) }
        }
      } catch (_: IllegalArgumentException) {
        emptyList()
      }
    }.sortedBy { it.second }
    val daily = occurrences.groupingBy(Pair<LocalDate, Instant>::first).eachCount()
    val maxDaily = daily.values.maxOrNull() ?: 0
    var left = 0
    var rollingMaximum = 0
    occurrences.indices.forEach { right ->
      val lowerExclusive = occurrences[right].second.minusSeconds(ROLLING_WINDOW_SECONDS)
      while (left <= right && !occurrences[left].second.isAfter(lowerExclusive)) left++
      rollingMaximum = maxOf(rollingMaximum, right - left + 1)
    }
    val localDayConflict = occurrences.asSequence()
      .map(Pair<LocalDate, Instant>::first)
      .distinct()
      .firstOrNull {
        (daily[it] ?: 0) > minOf(draft.dailyCap, strictWindowSlotCapacity)
      }
    val rollingConflict = rollingConflictDate(occurrences, ROLLING_24_HOUR_CEILING)
    val firstConflict = listOfNotNull(localDayConflict, rollingConflict).minOrNull()
    return PolicySimulation(
      simulatedOccurrenceCount = occurrences.size,
      maximumLocalDay = maxDaily,
      maximumRolling24Hours = rollingMaximum,
      strictWindowSlotCapacity = strictWindowSlotCapacity,
      firstConflictDate = firstConflict,
    )
  }

  /**
   * The server enforces five minutes between completed Arm outcomes. A fresh request also needs
   * ordering/submit headroom, so the product contract budgets one Arm slot per six started
   * minutes. The end is strict: a partial final six-minute interval contributes one slot.
   */
  fun strictWindowSlotCapacity(draft: ParsedWindowDraft): Int {
    val effectiveEndMinute = draft.graceEndMinute ?: draft.endMinute
    val totalMinutes = effectiveEndMinute - draft.startMinute
    return (totalMinutes + ARM_SLOT_MINUTES - 1) / ARM_SLOT_MINUTES
  }

  fun summary(draft: ParsedWindowDraft): String {
    val primary = "${formatMinute(draft.startMinute)}–${formatMinute(draft.endMinute)}"
    val grace = draft.graceEndMinute?.let { " · grace to ${formatMinute(it)}" } ?: " · no grace"
    return "$primary$grace · daily cap ${draft.dailyCap}"
  }

  private fun rollingConflictDate(
    occurrences: List<Pair<LocalDate, Instant>>,
    cap: Int,
  ): LocalDate? {
    var left = 0
    occurrences.indices.forEach { right ->
      val lowerExclusive = occurrences[right].second.minusSeconds(ROLLING_WINDOW_SECONDS)
      while (left <= right && !occurrences[left].second.isAfter(lowerExclusive)) left++
      if (right - left + 1 > cap) return occurrences[right].first
    }
    return null
  }

  /**
   * Resolves a civil minute using the product's shared DST rule. Kept internal so the
   * cross-platform acceptance matrix can verify real gap/overlap behavior without duplicating
   * this security-relevant calendar decision in a test oracle.
   */
  internal fun resolveStart(date: LocalDate, minute: Int, zoneId: ZoneId): Instant {
    return BirthdayCapacityPolicy.resolve(date, minute, zoneId)
  }

  private fun parseMinute(raw: String): Int? = try {
    val parsed = LocalTime.parse(raw)
    if (parsed.second != 0 || parsed.nano != 0) null else parsed.hour * 60 + parsed.minute
  } catch (_: DateTimeException) {
    null
  }

  private fun formatMinute(minute: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    minute / 60,
    minute % 60,
  )

  private fun deduplicateIssues(issues: JSONArray): JSONArray {
    val seen = linkedSetOf<String>()
    return JSONArray().apply {
      repeat(issues.length()) { index ->
        val value = issues.getJSONObject(index)
        val key = "${value.getString("field")}:${value.getString("code")}"
        if (seen.add(key)) put(value)
      }
    }
  }

  private fun issue(field: String, code: String) = JSONObject()
    .put("field", field)
    .put("code", code)

  private fun JSONObject.keyNames(): Set<String> = buildSet {
    val iterator = keys()
    while (iterator.hasNext()) add(iterator.next())
  }

  private const val MIN_WINDOW_MINUTES = 30
  private const val MAX_WINDOW_MINUTES = 240
  private const val ARM_SLOT_MINUTES = 6
  const val SIMULATED_DAYS = 400
  const val ROLLING_24_HOUR_CEILING = 20
  private const val ROLLING_WINDOW_SECONDS = 24L * 60 * 60
}
