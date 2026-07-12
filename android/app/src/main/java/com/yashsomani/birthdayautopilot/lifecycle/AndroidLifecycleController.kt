package com.yashsomani.birthdayautopilot.lifecycle

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.room.withTransaction
import androidx.core.net.toUri
import com.google.firebase.FirebaseApp
import com.yashsomani.birthdayautopilot.BuildConfig
import com.yashsomani.birthdayautopilot.auth.ForegroundActivityRegistry
import com.yashsomani.birthdayautopilot.auth.TelephonyPermissionResult
import com.yashsomani.birthdayautopilot.automation.orchestration.AutomationOpaqueIds
import com.yashsomani.birthdayautopilot.automation.orchestration.SystemComposerRetirementReceipt
import com.yashsomani.birthdayautopilot.automation.sms.SubmissionGate
import com.yashsomani.birthdayautopilot.configuration.ConfigurationCanonicalHash
import com.yashsomani.birthdayautopilot.configuration.ConfigurationOutcome
import com.yashsomani.birthdayautopilot.configuration.ConfigurationOwnershipPolicy
import com.yashsomani.birthdayautopilot.coordination.CoordinationValuePolicy
import com.yashsomani.birthdayautopilot.contacts.UnicodeTextSafety
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.messages.AndroidUserControlledSmsComposer
import com.yashsomani.birthdayautopilot.messages.SystemSmsComposerIntentPolicy
import com.yashsomani.birthdayautopilot.messages.UserControlledSmsComposer
import com.yashsomani.birthdayautopilot.messages.UserControlledSmsComposerDraft
import com.yashsomani.birthdayautopilot.messages.UserControlledSmsComposerOpenResult
import com.yashsomani.birthdayautopilot.people.recordContactsConsentDecision
import com.yashsomani.birthdayautopilot.readiness.AndroidAppStandbyBucketDiagnosticReader
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.ConfigurationReviewEntity
import com.yashsomani.birthdayautopilot.storage.database.ConsentDecision
import com.yashsomani.birthdayautopilot.storage.database.ConsentKind
import com.yashsomani.birthdayautopilot.storage.database.ControlEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationRecordState
import com.yashsomani.birthdayautopilot.storage.database.ReconcileHeartbeatPolicy
import com.yashsomani.birthdayautopilot.storage.database.ReconcileHeartbeatStatus
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class PrivacyActionPlan(
  val operationId: String,
  val action: String,
  val requiresPause: Boolean,
  val remoteRequired: Boolean,
  val deletionLocalWipeFallback: Boolean = false,
)

internal sealed interface PrivacyConfirmationOutcome {
  data class Ready(val plan: PrivacyActionPlan) : PrivacyConfirmationOutcome
  data class Rejected(val outcome: ConfigurationOutcome) : PrivacyConfirmationOutcome
}

internal data class SenderTransferPlan(
  val operationId: String,
  val requestId: String,
  val accountId: String,
  val activeInstallationId: String,
  val targetInstallationId: String,
  val senderEpoch: Long,
  val resetGeneration: Long,
)

internal sealed interface SenderTransferConfirmationOutcome {
  data class Ready(val plan: SenderTransferPlan) : SenderTransferConfirmationOutcome
  data class Rejected(val outcome: ConfigurationOutcome) : SenderTransferConfirmationOutcome
}

private data class SystemComposerLaunchPlan(
  val draft: UserControlledSmsComposerDraft,
  val retirement: SystemComposerRetirementReceipt,
)

private data class TodayOccurrenceOwnerBinding(
  val installationId: String,
  val senderEpoch: Long,
  val resetGeneration: Long,
)

private sealed interface TodayOccurrenceOwnerCheck {
  data class Ready(val binding: TodayOccurrenceOwnerBinding) : TodayOccurrenceOwnerCheck
  data class Rejected(val outcome: ConfigurationOutcome) : TodayOccurrenceOwnerCheck
}

internal class AndroidLifecycleController(
  context: Context,
  private val database: BirthdayDatabase,
  private val activityProvider: () -> Activity? = ForegroundActivityRegistry::current,
  private val wallClockMillis: () -> Long = System::currentTimeMillis,
  private val accountSessionMatches: (AccountRecordEntity) -> Boolean = { true },
  private val userControlledSmsComposer: UserControlledSmsComposer =
    AndroidUserControlledSmsComposer(),
  private val submissionGate: SubmissionGate = SubmissionGate(context),
  private val appStandbyBucketCode: () -> String = {
    AndroidAppStandbyBucketDiagnosticReader(context).read().wireCode
  },
) {
  private val appContext = context.applicationContext
  private val dao = database.lifecycleProjectionDao()
  private val configurationDao = database.configurationDao()
  private val safetyLedger = database.safetyLedgerDao()
  private val stateStore = LifecycleStateStore(appContext)

  suspend fun activityPayload(request: JSONObject): JSONObject? {
    if (request.keyNames() != setOf("kind", "query") || request.optString("kind") != "list") {
      return null
    }
    val query = request.optJSONObject("query") ?: return null
    if (query.keyNames().any { it !in setOf("cursor", "pageSize") }) return null
    val pageSize = query.strictInt("pageSize") ?: return null
    if (pageSize !in 1..MAX_ACTIVITY_PAGE_SIZE) return null
    if (lifecycleJournalUnreadable()) {
      return JSONObject().put("items", JSONArray())
    }
    val cursor = if (query.has("cursor")) parseCursor(query.optString("cursor")) ?: return null else null
    val rows = dao.activityPage(
      visibilityCutoffMillis = stateStore.activityVisibilityCutoffMillis(),
      hasCursor = cursor != null,
      beforeMillis = cursor?.first ?: Long.MAX_VALUE,
      beforeSourceKey = cursor?.second.orEmpty(),
      limit = pageSize + 1,
    )
    val visible = rows.take(pageSize)
    return JSONObject().put(
      "items",
      JSONArray().apply { visible.forEach { put(activityRecord(it)) } },
    ).apply {
      if (rows.size > pageSize) {
        val last = visible.last()
        put("nextCursor", "activity:${last.occurredAtMillis}:${last.sourceKey}")
      }
    }
  }

  suspend fun birthdayJobPayload(request: JSONObject): JSONObject? {
    if (
      request.keyNames() != setOf("kind", "occurrenceId") ||
      request.optString("kind") != "birthday-job"
    ) return null
    val occurrenceId = request.optString("occurrenceId")
    if (!OCCURRENCE_ID.matches(occurrenceId)) return null
    val account = dao.activeAccount() ?: return null
    val occurrence = dao.occurrence(occurrenceId)
      ?.takeIf { it.accountId == account.accountId }
      ?: return null
    return JSONObject()
      .put("platform", "android")
      .put("occurrenceId", occurrence.occurrenceId)
      .put("occurrenceDate", occurrence.localDate)
      .put("phase", birthdayPhase(occurrence.state.name))
      .put("updatedAt", Instant.ofEpochMilli(occurrence.updatedAtMillis.coerceAtLeast(0)).toString())
      .put("attempt", occurrence.attemptNumber.coerceIn(1, 2))
  }

  suspend fun diagnosticsPayload(): JSONObject {
    val standbyCode = appStandbyBucketCode()
    if (lifecycleJournalUnreadable()) {
      return JSONObject()
        .put("buildLabel", "Birthday Autopilot ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        .put("androidOrIosVersionLabel", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
        .put(
          "capabilityCodes",
          JSONArray(listOf(standbyCode, "coordination-unavailable")),
        )
        .put("transitionCount", 0)
        .put("excludesPrivateContent", true)
    }
    val cutoff = stateStore.activityVisibilityCutoffMillis()
    val bounds = dao.activityBounds(cutoff)
    val heartbeat = dao.activeAccount()?.let { account ->
      ReconcileHeartbeatPolicy.snapshot(safetyLedger.getReadinessState(account.accountId))
    }
    val activityCodes = dao.activityPage(cutoff, false, Long.MAX_VALUE, "", 64)
      .mapNotNull { safeReason(it.safeCode ?: it.state) }
    val schedulerCodes = buildList {
      heartbeat?.safeCode?.let(::safeReason)?.let(::add)
      if (
        heartbeat?.status == ReconcileHeartbeatStatus.RETRYING ||
        heartbeat?.status == ReconcileHeartbeatStatus.FAILED
      ) add("scheduler-delayed")
    }
    val codes = (listOf(standbyCode) + activityCodes + schedulerCodes)
      .distinct()
      .take(64)
    return JSONObject()
      .put("buildLabel", "Birthday Autopilot ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
      .put("androidOrIosVersionLabel", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
      .put("capabilityCodes", JSONArray(codes))
      .put("transitionCount", boundedCount(bounds.eventCount))
      .put("excludesPrivateContent", true)
      .apply {
        bounds.earliestMillis?.takeIf { it >= 0 }?.let {
          put("earliestEventAt", Instant.ofEpochMilli(it).toString())
        }
        bounds.latestMillis?.takeIf { it >= 0 }?.let {
          put("latestEventAt", Instant.ofEpochMilli(it).toString())
        }
        heartbeat?.heartbeatAtMillis?.takeIf { it > 0 }?.let {
          put("schedulerHeartbeatAt", Instant.ofEpochMilli(it).toString())
        }
      }
  }

  suspend fun shareDiagnostics(expectedRevision: Long): JSONObject? {
    if (dao.control()?.revision != expectedRevision) return null
    val activity = activityProvider() ?: return JSONObject().put("kind", "cancelled")
    val preview = diagnosticsPayload()
    val text = buildString {
      append("Birthday Autopilot diagnostics\n")
      append("Build: ").append(preview.getString("buildLabel")).append('\n')
      append("System: ").append(preview.getString("androidOrIosVersionLabel")).append('\n')
      append("Transitions: ").append(preview.getInt("transitionCount")).append('\n')
      if (preview.has("schedulerHeartbeatAt")) {
        append("Scheduler heartbeat: ").append(preview.getString("schedulerHeartbeatAt")).append('\n')
      }
      append("Codes: ").append(
        (0 until preview.getJSONArray("capabilityCodes").length()).joinToString(",") {
          preview.getJSONArray("capabilityCodes").getString(it)
        },
      ).append('\n')
      append("Private contact, birthday, phone, and message content excluded.\n")
    }
    val send = Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, text)
      .putExtra(Intent.EXTRA_SUBJECT, "Birthday Autopilot diagnostics")
    return try {
      activity.startActivity(Intent.createChooser(send, "Share diagnostics"))
      JSONObject().put("kind", "shared")
    } catch (_: RuntimeException) {
      JSONObject().put("kind", "cancelled")
    }
  }

  suspend fun inventoryPayload(): JSONObject {
    val account = dao.activeAccount()
    val accountId = account?.accountId
    val bounds = if (lifecycleJournalUnreadable()) {
      null
    } else {
      dao.activityBounds(stateStore.activityVisibilityCutoffMillis())
    }
    val bytes = databaseBytes().coerceAtLeast(0)
    return JSONObject()
      .put("localContactCount", boundedCount(accountId?.let { dao.localContactCount(it) } ?: 0))
      .put(
        "enabledRecipientCount",
        boundedCount(accountId?.let { dao.enabledRecipientCount(it) } ?: 0),
      )
      .put("approvalCount", boundedCount(accountId?.let { dao.approvalCount(it) } ?: 0))
      .put("activityCount", boundedCount(bounds?.eventCount ?: 0))
      .put("templateCount", boundedCount(accountId?.let { dao.templateCount(it) } ?: 0))
      .put("localStorageBytes", bytes.coerceAtMost(MAX_SAFE_JSON_INTEGER))
      .put("consentVersions", JSONArray(accountId?.let { dao.consentVersions(it) }.orEmpty()))
      .put("externalSmsCopiesNotControlled", true)
      .apply {
        accountId?.let { dao.lastContactsSyncMillis(it) }?.takeIf { it >= 0 }?.let {
          put("lastContactsSyncAt", Instant.ofEpochMilli(it).toString())
        }
      }
  }

  fun publicResourcesPayload(): JSONObject {
    val buildLabel = "Birthday Autopilot ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    val projectId = runCatching {
      FirebaseApp.getApps(appContext)
        .singleOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        ?.options
        ?.projectId
    }.getOrNull()
    val baseUrl = PublicResourcesPolicy.baseUrl(projectId)
    return JSONObject()
      .put("kind", if (baseUrl == null) "unavailable" else "available")
      .put("buildLabel", buildLabel)
      .apply { baseUrl?.let { put("baseUrl", it) } }
  }

  suspend fun prepareTodayOccurrence(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    if (lifecycleJournalUnreadable()) return conflict("coordination-unavailable")
    if (
      request.keyNames() != setOf("occurrenceId", "expectedRevision") ||
      request.optString("expectedRevision") != expectedRevision.toString()
    ) return ConfigurationOutcome.InvalidRequest
    val occurrenceId = request.optString("occurrenceId")
    if (!OCCURRENCE_ID.matches(occurrenceId)) return ConfigurationOutcome.InvalidRequest
    val control = dao.control() ?: return internal("LIFECYCLE_CONTROL_MISSING")
    if (control.revision != expectedRevision) return stale(control.revision)
    val account = dao.activeAccount() ?: return conflict("account-reconnect-required")
    if (!accountSessionMatches(account)) return conflict("account-reconnect-required")
    val ownerBinding = when (val check = todayOccurrenceOwner(account.accountId, control)) {
      is TodayOccurrenceOwnerCheck.Ready -> check.binding
      is TodayOccurrenceOwnerCheck.Rejected -> return check.outcome
    }
    val occurrence = dao.todayOccurrence(occurrenceId)
      ?.takeIf { it.accountId == account.accountId }
      ?: return conflict("approval-invalid")
    val now = wallClockMillis()
    val zone = runCatching { ZoneId.of(occurrence.timeZoneId) }.getOrNull()
      ?: return internal("LIFECYCLE_TIME_ZONE_INVALID")
    val localDate = runCatching { Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString() }
      .getOrNull()
      ?: return internal("LIFECYCLE_TIME_INVALID")
    if (occurrence.localDate != localDate) return conflict("birthday-conflict")
    if (!safeDisplayName(occurrence.recipient) || !safeMessage(occurrence.exactText)) {
      return internal("LIFECYCLE_PRIVATE_REVIEW_INVALID")
    }
    val choices = TodayOccurrenceChoicePolicy.evaluate(
      nowMillis = now,
      windowStartMillis = occurrence.resolvedWindowStartMillis,
      windowEndMillis = occurrence.resolvedWindowEndMillis,
      resetSafetyAllowsBirthday =
        dao.resetSafetyAllowsBirthday(account.accountId, occurrence.localDate) == 1,
    )
    val payloadJson = JSONObject()
      .put("occurrenceId", occurrence.occurrenceId)
      .put("occurrenceRevision", occurrence.occurrenceRevision)
      .put("primaryChoice", choices.primary.wireValue)
      .put("alternativeChoice", choices.alternative?.wireValue.orEmpty())
      .put("installationId", ownerBinding.installationId)
      .put("senderEpoch", ownerBinding.senderEpoch)
      .put("resetGeneration", ownerBinding.resetGeneration)
      .toString()
    val ttlExpiry = safeAdd(now, REVIEW_TTL_MILLIS)
      ?: return internal("LIFECYCLE_TIME_OVERFLOW")
    val expires = if (choices.primary == TodayOccurrenceChoice.NORMAL_PATH) {
      minOf(ttlExpiry, occurrence.resolvedWindowEndMillis)
    } else {
      ttlExpiry
    }
    if (expires <= now) return conflict("birthday-conflict")
    val handle = "to_${UUID.randomUUID().toString().replace("-", "")}" 
    val inserted = dao.insertReview(
      ConfigurationReviewEntity(
        reviewId = handle,
        accountId = account.accountId,
        kind = REVIEW_TODAY,
        payloadJson = payloadJson,
        payloadHash = ConfigurationCanonicalHash.payload(REVIEW_TODAY, payloadJson),
        controlRevision = control.revision,
        blockerRevision = control.blockerRevision,
        createdAtMillis = now,
        expiresAtMillis = expires,
        consumedAtMillis = null,
      ),
    )
    if (inserted == -1L) return internal("LIFECYCLE_REVIEW_CREATE_FAILED")
    return ConfigurationOutcome.Success(
      JSONObject()
        .put("handle", handle)
        .put("recipient", occurrence.recipient)
        .put("maskedDestination", occurrence.maskedDestination)
        .put("exactText", occurrence.exactText)
        .put("choice", choices.primary.wireValue)
        .put("limitationsDisclosure", todayLimitations(choices.primary))
        .apply {
          choices.alternative?.let { put("alternativeChoice", it.wireValue) }
        },
    )
  }

  suspend fun confirmTodayOccurrence(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome = submissionGate.withExclusiveBoundary {
    confirmTodayOccurrenceInsideBoundary(request, expectedRevision)
  }

  private suspend fun confirmTodayOccurrenceInsideBoundary(
    request: JSONObject,
    expectedRevision: Long,
  ): ConfigurationOutcome {
    var composerLaunchPlan: SystemComposerLaunchPlan? = null
    val outcome = database.withTransaction {
      if (lifecycleJournalUnreadable()) {
        return@withTransaction conflict("coordination-unavailable")
      }
      if (
        request.keyNames() != setOf("handle", "choice", "expectedRevision") ||
        request.optString("expectedRevision") != expectedRevision.toString()
      ) return@withTransaction ConfigurationOutcome.InvalidRequest
      val requestedChoice = TodayOccurrenceChoice.fromWire(request.optString("choice"))
        ?: return@withTransaction ConfigurationOutcome.InvalidRequest
      val handle = request.optString("handle")
      if (!TODAY_HANDLE.matches(handle)) return@withTransaction ConfigurationOutcome.InvalidRequest
      val now = wallClockMillis()
      val control = dao.control() ?: return@withTransaction internal("LIFECYCLE_CONTROL_MISSING")
      if (control.revision != expectedRevision) return@withTransaction stale(control.revision)
      val review = dao.review(handle)
        ?: return@withTransaction conflict("approval-invalid")
      if (
        review.kind != REVIEW_TODAY ||
        review.controlRevision != expectedRevision ||
        review.blockerRevision != control.blockerRevision ||
        review.consumedAtMillis != null ||
        review.expiresAtMillis <= now ||
        !ConfigurationCanonicalHash.matches(
          ConfigurationCanonicalHash.payload(review.kind, review.payloadJson),
          review.payloadHash,
        )
      ) return@withTransaction stale(control.revision)
      val payload = runCatching { JSONObject(review.payloadJson) }.getOrNull()
        ?: return@withTransaction internal("LIFECYCLE_REVIEW_CORRUPT")
      if (payload.keyNames() != TODAY_REVIEW_KEYS) {
        return@withTransaction internal("LIFECYCLE_REVIEW_CORRUPT")
      }
      val occurrenceId = payload.optString("occurrenceId")
      val occurrenceRevision = payload.optLong("occurrenceRevision", -1)
      val primaryChoice = TodayOccurrenceChoice.fromWire(payload.optString("primaryChoice"))
      val alternativeChoice = payload.optString("alternativeChoice")
        .takeIf(String::isNotEmpty)
        ?.let(TodayOccurrenceChoice::fromWire)
      val reviewedOwnerBinding = TodayOccurrenceOwnerBinding(
        installationId = payload.optString("installationId"),
        senderEpoch = payload.optLong("senderEpoch", -1),
        resetGeneration = payload.optLong("resetGeneration", -1),
      )
      if (
        !OCCURRENCE_ID.matches(occurrenceId) ||
        occurrenceRevision < 0 ||
        primaryChoice == null ||
        (payload.optString("alternativeChoice").isNotEmpty() && alternativeChoice == null) ||
        !INSTALLATION_ID.matches(reviewedOwnerBinding.installationId) ||
        reviewedOwnerBinding.senderEpoch <= 0 ||
        reviewedOwnerBinding.resetGeneration <= 0
      ) return@withTransaction internal("LIFECYCLE_REVIEW_CORRUPT")
      if (requestedChoice !in setOfNotNull(primaryChoice, alternativeChoice)) {
        return@withTransaction conflict("approval-invalid")
      }
      val account = dao.activeAccount()
        ?: return@withTransaction conflict("account-reconnect-required")
      if (!accountSessionMatches(account)) {
        return@withTransaction conflict("account-reconnect-required")
      }
      if (review.accountId != account.accountId) return@withTransaction stale(control.revision)
      val currentOwnerBinding = when (val check = todayOccurrenceOwner(account.accountId, control)) {
        is TodayOccurrenceOwnerCheck.Ready -> check.binding
        is TodayOccurrenceOwnerCheck.Rejected -> return@withTransaction check.outcome
      }
      if (currentOwnerBinding != reviewedOwnerBinding) {
        return@withTransaction conflict("active-sender-other-device")
      }
      val occurrence = dao.todayOccurrence(occurrenceId)
        ?.takeIf { it.accountId == account.accountId && it.occurrenceRevision == occurrenceRevision }
        ?: return@withTransaction stale(control.revision)
      val zone = runCatching { ZoneId.of(occurrence.timeZoneId) }.getOrNull()
        ?: return@withTransaction internal("LIFECYCLE_TIME_ZONE_INVALID")
      val localDate = runCatching {
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
      }.getOrNull() ?: return@withTransaction internal("LIFECYCLE_TIME_INVALID")
      if (occurrence.localDate != localDate) return@withTransaction conflict("birthday-conflict")
      val currentChoices = TodayOccurrenceChoicePolicy.evaluate(
        nowMillis = now,
        windowStartMillis = occurrence.resolvedWindowStartMillis,
        windowEndMillis = occurrence.resolvedWindowEndMillis,
        resetSafetyAllowsBirthday =
          dao.resetSafetyAllowsBirthday(account.accountId, occurrence.localDate) == 1,
      )
      if (requestedChoice !in setOfNotNull(currentChoices.primary, currentChoices.alternative)) {
        return@withTransaction conflict("birthday-conflict")
      }
      val orchestration = database.automationOrchestrationDao()
      val changed = when (requestedChoice) {
        TodayOccurrenceChoice.NORMAL_PATH -> orchestration.scheduleReviewedOccurrence(
          occurrenceId,
          occurrenceRevision,
          now,
        )
        TodayOccurrenceChoice.NEXT_YEAR -> orchestration.deferReviewedOccurrenceToNextYear(
          occurrenceId,
          occurrenceRevision,
          now,
        )
        TodayOccurrenceChoice.SYSTEM_COMPOSER -> {
          val draft = UserControlledSmsComposerDraft(
            canonicalRecipient = occurrence.canonicalRecipient,
            exactApprovedBody = occurrence.exactText,
          )
          if (
            !SystemSmsComposerIntentPolicy.validDraft(draft) ||
            !userControlledSmsComposer.canOpen(draft)
          ) return@withTransaction conflict("system-composer-unavailable")
          val retirement = orchestration.retireReviewedOccurrenceForSystemComposer(
            occurrenceId,
            occurrenceRevision,
            now,
          )
          if (retirement == null) {
            false
          } else {
            composerLaunchPlan = SystemComposerLaunchPlan(draft, retirement)
            true
          }
        }
      }
      if (!changed) return@withTransaction conflict("birthday-conflict")
      if (
        dao.consumeReview(
          review.reviewId,
          review.kind,
          review.controlRevision,
          review.blockerRevision,
          now,
        ) != 1
      ) error("today-review-consume-failed")
      ConfigurationOutcome.Success(
        JSONObject(),
        setOf("automation", "home", "activity", "readiness"),
      )
    }
    val launchPlan = composerLaunchPlan
    if (outcome is ConfigurationOutcome.Success && launchPlan != null) {
      when (userControlledSmsComposer.open(launchPlan.draft)) {
        UserControlledSmsComposerOpenResult.OPENED -> Unit
        UserControlledSmsComposerOpenResult.KNOWN_FAILURE -> {
          val restored = database.automationOrchestrationDao()
            .restoreKnownFailedSystemComposerRetirement(
              launchPlan.retirement,
              wallClockMillis(),
            )
          return conflict(
            if (restored) {
              "system-composer-unavailable"
            } else {
              "system-composer-outcome-unknown"
            },
          )
        }
        UserControlledSmsComposerOpenResult.UNKNOWN ->
          return conflict("system-composer-outcome-unknown")
      }
    }
    return outcome
  }

  private suspend fun todayOccurrenceOwner(
    accountId: String,
    control: ControlEntity,
  ): TodayOccurrenceOwnerCheck {
    val mode = runCatching { AccountMode.valueOf(control.accountMode) }.getOrNull()
      ?: return TodayOccurrenceOwnerCheck.Rejected(
      internal("LIFECYCLE_CONTROL_INVALID"),
    )
    ConfigurationOwnershipPolicy.blockedReason(mode)?.let { reason ->
      return TodayOccurrenceOwnerCheck.Rejected(conflict(reason))
    }
    val orchestration = database.automationOrchestrationDao()
    val local = orchestration.localInstallation()
      ?: return TodayOccurrenceOwnerCheck.Rejected(conflict("active-sender-other-device"))
    val coordination = orchestration.coordinationState(accountId)
      ?: return TodayOccurrenceOwnerCheck.Rejected(conflict("coordination-unavailable"))
    val epoch = local.senderEpoch
    if (
      local.accountId != accountId ||
      local.localSlot != 1 ||
      local.state != InstallationRecordState.ACTIVE ||
      local.accountMode != mode ||
      epoch == null ||
      epoch <= 0 ||
      local.resetGeneration <= 0 ||
      control.activeInstallationEpoch != epoch ||
      coordination.mode != mode ||
      coordination.activeInstallationId != local.installationId ||
      coordination.senderEpoch != epoch ||
      coordination.resetGeneration != local.resetGeneration
    ) return TodayOccurrenceOwnerCheck.Rejected(conflict("active-sender-other-device"))
    return TodayOccurrenceOwnerCheck.Ready(
      TodayOccurrenceOwnerBinding(
        local.installationId,
        epoch,
        local.resetGeneration,
      ),
    )
  }

  suspend fun preparePrivacyAction(
    request: JSONObject,
    expectedRevision: Long,
    preissuedPermitMayFinish: Boolean,
  ): ConfigurationOutcome {
    if (lifecycleJournalUnreadable()) return conflict("coordination-unavailable")
    if (
      request.keyNames() != setOf("kind", "expectedRevision") ||
      request.optString("expectedRevision") != expectedRevision.toString()
    ) return ConfigurationOutcome.InvalidRequest
    val action = request.optString("kind").takeIf { it in LifecycleStateStore.PRIVACY_ACTIONS }
      ?: return ConfigurationOutcome.InvalidRequest
    val currentOperation = stateStore.latestOperation()
    val deletionLocalWipeFallback = action == "wipe-local-data" &&
      currentOperation?.let(::eligibleDeletionLocalWipeFallback) == true
    if (
      currentOperation?.state !in setOf(null, "complete", "failed") &&
      !deletionLocalWipeFallback
    ) return conflict("policy-suspended")
    val control = dao.control() ?: return internal("LIFECYCLE_CONTROL_MISSING")
    val account = dao.activeAccount() ?: return conflict("account-reconnect-required")
    if (deletionLocalWipeFallback && !accountSessionMatches(account)) {
      return conflict("account-reconnect-required")
    }
    if (control.revision != expectedRevision) return stale(control.revision)
    val now = wallClockMillis()
    val handle = "pv_${UUID.randomUUID().toString().replace("-", "")}"
    val payloadJson = JSONObject()
      .put("action", action)
      .apply {
        if (deletionLocalWipeFallback) {
          put("recoveryOperationId", checkNotNull(currentOperation).id)
        }
      }
      .toString()
    val expires = safeAdd(now, REVIEW_TTL_MILLIS) ?: return internal("LIFECYCLE_TIME_OVERFLOW")
    val inserted = dao.insertReview(
      ConfigurationReviewEntity(
        reviewId = handle,
        accountId = account.accountId,
        kind = REVIEW_PRIVACY,
        payloadJson = payloadJson,
        payloadHash = ConfigurationCanonicalHash.payload(REVIEW_PRIVACY, payloadJson),
        controlRevision = control.revision,
        blockerRevision = control.blockerRevision,
        createdAtMillis = now,
        expiresAtMillis = expires,
        consumedAtMillis = null,
      ),
    )
    if (inserted == -1L) return internal("LIFECYCLE_REVIEW_CREATE_FAILED")
    return ConfigurationOutcome.Success(
      JSONObject()
        .put("handle", handle)
        .put("kind", action)
        .put("titleKey", "privacy.$action")
        .put("consequenceKeys", JSONArray(PrivacyConsequencePolicy.keys(action)))
        .put(
          "preissuedPermitMayFinish",
          preissuedPermitMayFinish || deletionLocalWipeFallback,
        )
        .put(
          "remoteConnectionRequired",
          !deletionLocalWipeFallback && action in REMOTE_PRIVACY_ACTIONS,
        )
        .put("externalSmsCopiesNotErased", true),
    )
  }

  suspend fun prepareSenderTransfer(
    request: JSONObject,
    expectedRevision: Long,
    preissuedPermitMayFinish: Boolean,
  ): ConfigurationOutcome {
    if (lifecycleJournalUnreadable()) return conflict("coordination-unavailable")
    if (stateStore.latestOperation()?.state !in setOf(null, "complete", "failed")) {
      return conflict("policy-suspended")
    }
    if (
      request.keyNames() != setOf("expectedRevision") ||
      request.optString("expectedRevision") != expectedRevision.toString()
    ) return ConfigurationOutcome.InvalidRequest
    val control = dao.control() ?: return internal("LIFECYCLE_CONTROL_MISSING")
    val account = dao.activeAccount() ?: return conflict("account-reconnect-required")
    val orchestration = database.automationOrchestrationDao()
    val local = orchestration.localInstallation()
      ?: return conflict("active-sender-other-device")
    val coordination = orchestration.coordinationState(account.accountId)
      ?: return conflict("coordination-unavailable")
    if (
      control.revision != expectedRevision ||
      local.accountId != account.accountId ||
      local.state != InstallationRecordState.STANDBY ||
      coordination.activeInstallationId == null ||
      coordination.activeInstallationId == local.installationId ||
      coordination.senderEpoch == null ||
      coordination.senderEpoch <= 0 ||
      coordination.resetGeneration <= 0
    ) return conflict("active-sender-other-device")
    val now = wallClockMillis()
    val handle = "st_${UUID.randomUUID().toString().replace("-", "")}" 
    val payloadJson = JSONObject()
      .put("accountId", account.accountId)
      .put("activeInstallationId", coordination.activeInstallationId)
      .put("targetInstallationId", local.installationId)
      .put("senderEpoch", coordination.senderEpoch)
      .put("resetGeneration", coordination.resetGeneration)
      .toString()
    val expires = safeAdd(now, REVIEW_TTL_MILLIS) ?: return internal("LIFECYCLE_TIME_OVERFLOW")
    if (
      dao.insertReview(
        ConfigurationReviewEntity(
          reviewId = handle,
          accountId = account.accountId,
          kind = REVIEW_SENDER_TRANSFER,
          payloadJson = payloadJson,
          payloadHash = ConfigurationCanonicalHash.payload(REVIEW_SENDER_TRANSFER, payloadJson),
          controlRevision = control.revision,
          blockerRevision = control.blockerRevision,
          createdAtMillis = now,
          expiresAtMillis = expires,
          consumedAtMillis = null,
        ),
      ) == -1L
    ) return internal("LIFECYCLE_REVIEW_CREATE_FAILED")
    return ConfigurationOutcome.Success(
      JSONObject()
        .put("kind", "sender-transfer")
        .put("handle", handle)
        .put("preissuedPermitMayFinish", preissuedPermitMayFinish)
        .put("completionRequiresRecentGoogleAuthentication", true)
        .put(
          "consequenceKeys",
          JSONArray(
            listOf(
              "transfer.consequence.old-phone-revoked",
              "transfer.consequence.new-phone-test-only",
              "transfer.consequence.test-required",
            ),
          ),
        ),
    )
  }

  suspend fun beginSenderTransfer(
    request: JSONObject,
    expectedRevision: Long,
  ): SenderTransferConfirmationOutcome = database.withTransaction {
    if (lifecycleJournalUnreadable()) {
      return@withTransaction SenderTransferConfirmationOutcome.Rejected(
        conflict("coordination-unavailable"),
      )
    }
    if (stateStore.latestOperation()?.state !in setOf(null, "complete", "failed")) {
      return@withTransaction SenderTransferConfirmationOutcome.Rejected(
        conflict("policy-suspended"),
      )
    }
    if (
      request.keyNames() != setOf("handle", "expectedRevision") ||
      request.optString("expectedRevision") != expectedRevision.toString()
    ) return@withTransaction SenderTransferConfirmationOutcome.Rejected(
      ConfigurationOutcome.InvalidRequest,
    )
    val handle = request.optString("handle")
    if (!SENDER_TRANSFER_HANDLE.matches(handle)) {
      return@withTransaction SenderTransferConfirmationOutcome.Rejected(
        ConfigurationOutcome.InvalidRequest,
      )
    }
    val now = wallClockMillis()
    val control = dao.control() ?: return@withTransaction SenderTransferConfirmationOutcome.Rejected(
      internal("LIFECYCLE_CONTROL_MISSING"),
    )
    val review = dao.review(handle) ?: return@withTransaction SenderTransferConfirmationOutcome.Rejected(
      conflict("approval-invalid"),
    )
    if (
      review.kind != REVIEW_SENDER_TRANSFER ||
      review.controlRevision != expectedRevision ||
      control.revision != expectedRevision ||
      review.blockerRevision != control.blockerRevision ||
      review.consumedAtMillis != null ||
      review.expiresAtMillis <= now ||
      !ConfigurationCanonicalHash.matches(
        ConfigurationCanonicalHash.payload(review.kind, review.payloadJson),
        review.payloadHash,
      )
    ) return@withTransaction SenderTransferConfirmationOutcome.Rejected(stale(control.revision))
    val payload = runCatching { JSONObject(review.payloadJson) }.getOrNull()
      ?: return@withTransaction SenderTransferConfirmationOutcome.Rejected(
        internal("LIFECYCLE_REVIEW_CORRUPT"),
      )
    if (payload.keyNames() != SENDER_TRANSFER_REVIEW_KEYS) {
      return@withTransaction SenderTransferConfirmationOutcome.Rejected(
        internal("LIFECYCLE_REVIEW_CORRUPT"),
      )
    }
    val accountId = payload.optString("accountId")
    val activeId = payload.optString("activeInstallationId")
    val targetId = payload.optString("targetInstallationId")
    val senderEpoch = payload.optLong("senderEpoch", -1)
    val resetGeneration = payload.optLong("resetGeneration", -1)
    val account = dao.activeAccount()
    val local = database.automationOrchestrationDao().localInstallation()
    val coordination = account?.let {
      database.automationOrchestrationDao().coordinationState(it.accountId)
    }
    if (
      account?.accountId != accountId ||
      local?.installationId != targetId ||
      local.state != InstallationRecordState.STANDBY ||
      coordination?.activeInstallationId != activeId ||
      coordination.senderEpoch != senderEpoch ||
      coordination.resetGeneration != resetGeneration ||
      !INSTALLATION_ID.matches(activeId) ||
      !INSTALLATION_ID.matches(targetId) ||
      activeId == targetId ||
      senderEpoch <= 0 ||
      resetGeneration <= 0
    ) return@withTransaction SenderTransferConfirmationOutcome.Rejected(
      conflict("active-sender-other-device"),
    )
    if (
      dao.consumeReview(
        review.reviewId,
        review.kind,
        review.controlRevision,
        review.blockerRevision,
        now,
      ) != 1
    ) return@withTransaction SenderTransferConfirmationOutcome.Rejected(
      conflict("approval-invalid"),
    )
    val operationId = "transfer_${UUID.randomUUID().toString().replace("-", "")}" 
    val requestId = AutomationOpaqueIds.uuid(
      "SenderTransferOperation.v1",
      operationId,
      accountId,
      activeId,
      targetId,
    )
    val operation = DurablePrivacyOperation(
      id = operationId,
      action = "sender-transfer",
      state = "verifying",
      reason = null,
      updatedAtMillis = now,
      completedAtMillis = null,
      requestId = requestId,
      transferActiveInstallationId = activeId,
      transferTargetInstallationId = targetId,
      transferSenderEpoch = senderEpoch,
      transferResetGeneration = resetGeneration,
    )
    if (!stateStore.putOperation(operation)) error("transfer-operation-journal-failed")
    SenderTransferConfirmationOutcome.Ready(
      SenderTransferPlan(
        operationId,
        requestId,
        accountId,
        activeId,
        targetId,
        senderEpoch,
        resetGeneration,
      ),
    )
  }

  suspend fun recoverSenderTransferPlan(): SenderTransferPlan? {
    val operation = stateStore.latestOperation()?.takeIf {
      it.action == "sender-transfer" && it.state !in setOf("complete", "failed")
    } ?: return null
    val plan = senderTransferPlanIdentity(operation.id) ?: return null
    val orchestration = database.automationOrchestrationDao()
    val local = runCatching { orchestration.localInstallation() }.getOrNull() ?: return null
    val coordination = runCatching { orchestration.coordinationState(plan.accountId) }
      .getOrNull() ?: return null
    if (
      local.installationId != plan.targetInstallationId ||
      local.state != InstallationRecordState.STANDBY ||
      coordination.activeInstallationId != plan.activeInstallationId ||
      coordination.senderEpoch != plan.senderEpoch ||
      coordination.resetGeneration != plan.resetGeneration
    ) return null
    return plan
  }

  suspend fun senderTransferPlanIdentity(operationId: String): SenderTransferPlan? {
    val operation = stateStore.operation(operationId)?.takeIf {
      it.action == "sender-transfer" && it.state !in setOf("complete", "failed")
    } ?: return null
    val requestId = operation.requestId ?: return null
    val persistedActiveId = operation.transferActiveInstallationId ?: return null
    val persistedTargetId = operation.transferTargetInstallationId ?: return null
    val persistedSenderEpoch = operation.transferSenderEpoch ?: return null
    val persistedResetGeneration = operation.transferResetGeneration ?: return null
    val account = runCatching { dao.activeAccount() }.getOrNull() ?: return null
    val expectedRequestId = AutomationOpaqueIds.uuid(
      "SenderTransferOperation.v1",
      operation.id,
      account.accountId,
      persistedActiveId,
      persistedTargetId,
    )
    if (requestId != expectedRequestId) return null
    return SenderTransferPlan(
      operation.id,
      requestId,
      account.accountId,
      persistedActiveId,
      persistedTargetId,
      persistedSenderEpoch,
      persistedResetGeneration,
    )
  }

  fun markSenderTransferDraining(
    plan: SenderTransferPlan,
    drainUntilMillis: Long,
    serverObservedAtMillis: Long,
    acceptedAtElapsedMillis: Long,
    acceptedBootCount: Int,
  ): DurablePrivacyOperation {
    val operation = DurablePrivacyOperation(
      id = plan.operationId,
      action = "sender-transfer",
      state = "remote-draining",
      reason = "transfer-pending",
      updatedAtMillis = wallClockMillis(),
      completedAtMillis = null,
      requestId = plan.requestId,
      remoteDrainUntilMillis = drainUntilMillis,
      serverObservedAtMillis = serverObservedAtMillis,
      acceptedAtElapsedMillis = acceptedAtElapsedMillis,
      acceptedBootCount = acceptedBootCount,
      transferActiveInstallationId = plan.activeInstallationId,
      transferTargetInstallationId = plan.targetInstallationId,
      transferSenderEpoch = plan.senderEpoch,
      transferResetGeneration = plan.resetGeneration,
    )
    check(stateStore.putOperation(operation)) { "transfer-operation-drain-update-failed" }
    return operation
  }

  fun markSenderTransferPending(
    plan: SenderTransferPlan,
    reason: String = "coordination-unavailable",
  ): DurablePrivacyOperation {
    val safeReason = reason.takeIf { it in LifecycleStateStore.SAFE_REASONS }
      ?: "internal-contract-invalid"
    val current = stateStore.operation(plan.operationId)
    val operation = (current ?: DurablePrivacyOperation(
      plan.operationId,
      "sender-transfer",
      "remote-pending",
      safeReason,
      wallClockMillis(),
      null,
      plan.requestId,
      transferActiveInstallationId = plan.activeInstallationId,
      transferTargetInstallationId = plan.targetInstallationId,
      transferSenderEpoch = plan.senderEpoch,
      transferResetGeneration = plan.resetGeneration,
    )).copy(
      state = "remote-pending",
      reason = safeReason,
      updatedAtMillis = wallClockMillis(),
      completedAtMillis = null,
    )
    check(stateStore.putOperation(operation)) { "transfer-operation-pending-update-failed" }
    return operation
  }

  fun failSenderTransferOperation(
    operationId: String,
    reason: String = "transfer-pending",
  ): DurablePrivacyOperation? {
    val current = stateStore.operation(operationId)
      ?.takeIf {
        it.action == "sender-transfer" && it.state !in setOf("complete", "failed")
      }
      ?: return null
    val safeReason = reason.takeIf { it in LifecycleStateStore.SAFE_REASONS }
      ?: "internal-contract-invalid"
    val at = wallClockMillis()
    val operation = current.copy(
      state = "failed",
      reason = safeReason,
      updatedAtMillis = at,
      completedAtMillis = at,
      remoteDrainUntilMillis = null,
      serverObservedAtMillis = null,
      acceptedAtElapsedMillis = null,
      acceptedBootCount = null,
    )
    check(stateStore.putOperation(operation)) { "transfer-operation-failed-update-failed" }
    return operation
  }

  fun completeSenderTransferOperation(operationId: String): DurablePrivacyOperation? {
    val current = stateStore.operation(operationId)
      ?.takeIf {
        it.action == "sender-transfer" && it.state !in setOf("complete", "failed")
      }
      ?: return null
    val at = wallClockMillis()
    val operation = current.copy(
      state = "complete",
      reason = null,
      updatedAtMillis = at,
      completedAtMillis = at,
      remoteDrainUntilMillis = null,
      serverObservedAtMillis = null,
      acceptedAtElapsedMillis = null,
      acceptedBootCount = null,
    )
    check(stateStore.putOperation(operation)) { "transfer-operation-complete-failed" }
    return operation
  }

  suspend fun beginPrivacyAction(
    request: JSONObject,
    expectedRevision: Long,
  ): PrivacyConfirmationOutcome = database.withTransaction {
    if (lifecycleJournalUnreadable()) {
      return@withTransaction PrivacyConfirmationOutcome.Rejected(
        conflict("coordination-unavailable"),
      )
    }
    val currentOperation = stateStore.latestOperation()
    if (
      request.keyNames() != setOf("handle", "expectedRevision") ||
      request.optString("expectedRevision") != expectedRevision.toString()
    ) return@withTransaction PrivacyConfirmationOutcome.Rejected(ConfigurationOutcome.InvalidRequest)
    val handle = request.optString("handle")
    if (!handle.matches(Regex("^pv_[a-f0-9]{32}$"))) {
      return@withTransaction PrivacyConfirmationOutcome.Rejected(ConfigurationOutcome.InvalidRequest)
    }
    val now = wallClockMillis()
    val control = dao.control()
      ?: return@withTransaction PrivacyConfirmationOutcome.Rejected(
        internal("LIFECYCLE_CONTROL_MISSING"),
      )
    val review = dao.review(handle)
      ?: return@withTransaction PrivacyConfirmationOutcome.Rejected(conflict("approval-invalid"))
    if (
      review.kind != REVIEW_PRIVACY ||
      review.controlRevision != expectedRevision ||
      control.revision != expectedRevision ||
      review.blockerRevision != control.blockerRevision ||
      review.consumedAtMillis != null ||
      review.expiresAtMillis <= now ||
      !ConfigurationCanonicalHash.matches(
        ConfigurationCanonicalHash.payload(review.kind, review.payloadJson),
        review.payloadHash,
      )
    ) return@withTransaction PrivacyConfirmationOutcome.Rejected(stale(control.revision))
    val payload = runCatching { JSONObject(review.payloadJson) }.getOrNull()
      ?: return@withTransaction PrivacyConfirmationOutcome.Rejected(
        internal("LIFECYCLE_REVIEW_CORRUPT"),
      )
    if (payload.keyNames() !in setOf(
        setOf("action"),
        setOf("action", "recoveryOperationId"),
      )) {
      return@withTransaction PrivacyConfirmationOutcome.Rejected(
        internal("LIFECYCLE_REVIEW_CORRUPT"),
      )
    }
    val action = payload.optString("action").takeIf { it in LifecycleStateStore.PRIVACY_ACTIONS }
      ?: return@withTransaction PrivacyConfirmationOutcome.Rejected(
        internal("LIFECYCLE_REVIEW_CORRUPT"),
      )
    val recoveryOperationId = payload.optString("recoveryOperationId").takeIf { it.isNotEmpty() }
    val deletionLocalWipeFallback = action == "wipe-local-data" &&
      recoveryOperationId != null &&
      currentOperation?.id == recoveryOperationId &&
      eligibleDeletionLocalWipeFallback(currentOperation)
    if (
      currentOperation?.state !in setOf(null, "complete", "failed") &&
      !deletionLocalWipeFallback
    ) return@withTransaction PrivacyConfirmationOutcome.Rejected(conflict("policy-suspended"))
    if ((recoveryOperationId != null) != deletionLocalWipeFallback) {
      return@withTransaction PrivacyConfirmationOutcome.Rejected(
        internal("LIFECYCLE_REVIEW_CORRUPT"),
      )
    }
    val recoveryAccount = if (deletionLocalWipeFallback) {
      dao.activeAccount()?.takeIf(accountSessionMatches)
        ?: return@withTransaction PrivacyConfirmationOutcome.Rejected(
          conflict("account-reconnect-required"),
        )
    } else {
      null
    }
    val recoveryProof = recoveryAccount?.let(DeletionRecoveryBindingPolicy::from)
    if (deletionLocalWipeFallback && recoveryProof == null) {
      return@withTransaction PrivacyConfirmationOutcome.Rejected(
        internal("LIFECYCLE_RECOVERY_BINDING_INVALID"),
      )
    }
    if (dao.consumeReview(
        review.reviewId,
        review.kind,
        review.controlRevision,
        review.blockerRevision,
        now,
    ) != 1
    ) return@withTransaction PrivacyConfirmationOutcome.Rejected(conflict("approval-invalid"))
    if (deletionLocalWipeFallback) {
      val current = checkNotNull(currentOperation)
      val proof = checkNotNull(recoveryProof)
      val recovery = current.copy(
        state = "local-wiping",
        reason = null,
        updatedAtMillis = now,
        completedAtMillis = null,
        remoteDeletionComplete = false,
        deletionLocalWipeFallback = true,
        recoveryBindingSalt = proof.salt,
        recoveryFirebaseUidHash = proof.firebaseUidHash,
        recoveryGoogleSubjectHash = proof.googleSubjectHash,
        deletionRetryAllowed = false,
        deletionInProgressObserved = current.state == "remote-draining",
      )
      if (!stateStore.putOperation(recovery)) {
        error("privacy-deletion-recovery-journal-failed")
      }
      return@withTransaction PrivacyConfirmationOutcome.Ready(
        PrivacyActionPlan(
          operationId = recovery.id,
          action = recovery.action,
          requiresPause = false,
          remoteRequired = false,
          deletionLocalWipeFallback = true,
        ),
      )
    }
    val operationId = "privacy_${UUID.randomUUID().toString().replace("-", "")}"
    val requestId = UUID.randomUUID().toString().takeIf {
      action in COORDINATED_MUTATION_ACTIONS
    }
    val operation = DurablePrivacyOperation(
      operationId,
      action,
      "queued",
      null,
      now,
      null,
      requestId = requestId,
    )
    if (!stateStore.putOperation(operation)) {
      error("privacy-operation-journal-failed")
    }
    PrivacyConfirmationOutcome.Ready(
      PrivacyActionPlan(
        operationId = operationId,
        action = action,
        requiresPause = action !in setOf("clear-activity"),
        remoteRequired = action in REMOTE_PRIVACY_ACTIONS,
      ),
    )
  }

  suspend fun executeLocalPrivacyAction(plan: PrivacyActionPlan): DurablePrivacyOperation {
    val now = wallClockMillis()
    updateOperation(plan, if (plan.requiresPause) "pausing" else "queued", null, now)
    val account = dao.activeAccount()
    return when (plan.action) {
      "clear-activity" -> {
        database.withTransaction { dao.clearLegacyActivity() }
        if (stateStore.setActivityVisibilityCutoffMillis(safeAdd(now, 1) ?: now)) {
          complete(plan)
        } else {
          fail(plan, "internal-contract-invalid")
        }
      }
      "clear-gemini-templates" -> {
        val accountId = account?.accountId
          ?: return fail(plan, "account-reconnect-required")
        database.withTransaction {
          val changed = dao.clearGeminiTemplates(accountId, now)
          if (changed > 0) {
            configurationDao.invalidateAllApprovals(accountId, now, "GEMINI_TEMPLATES_CLEARED")
            configurationDao.markConfiguredRecipientsForReview(
              accountId,
              now,
              "GEMINI_TEMPLATES_CLEARED",
            )
            configurationDao.invalidateTestReceipts(accountId, now, "GEMINI_TEMPLATES_CLEARED")
          }
        }
        complete(plan)
      }
      "sign-out-retain" -> updateOperation(plan, "verifying", null, now)
      else -> updateOperation(
        plan,
        if (plan.remoteRequired) "remote-pending" else "local-wiping",
        if (plan.remoteRequired) "coordination-unavailable" else null,
        now,
      )
    }
  }

  fun markRemotePending(
    plan: PrivacyActionPlan,
    reason: String = "coordination-unavailable",
  ): DurablePrivacyOperation {
    val current = stateStore.operation(plan.operationId)
    // Once deletion has been accepted, the durable drain receipt is stronger than an ephemeral
    // retry failure. Never destroy it by moving the operation back to an unaccepted state.
    if (
      current?.action == "delete-account" &&
      current.state == "remote-draining" &&
      current.remoteDeletionComplete == false
    ) return current
    if (current?.authoritativeRecoveryKind != null) {
      return markLocalCleanupPending(plan, reason)
    }
    return updateOperation(plan, "remote-pending", reason)
  }

  fun markVerifying(plan: PrivacyActionPlan): DurablePrivacyOperation =
    updateOperation(plan, "verifying", null)

  fun markLocalWiping(plan: PrivacyActionPlan): DurablePrivacyOperation =
    updateOperation(plan, "local-wiping", null)

  fun markLocalCleanupPending(
    plan: PrivacyActionPlan,
    reason: String,
  ): DurablePrivacyOperation = updateOperation(
    plan,
    "local-wiping",
    reason.takeIf { it in LifecycleStateStore.SAFE_REASONS }
      ?: "internal-contract-invalid",
  )

  fun markCoordinatedOperationInProgress(
    plan: PrivacyActionPlan,
    drainUntilMillis: Long?,
    serverObservedAtMillis: Long,
    acceptedAtElapsedMillis: Long,
    acceptedBootCount: Int,
  ): DurablePrivacyOperation {
    require(plan.action in COORDINATED_RESET_RELEASE_ACTIONS)
    val current = stateStore.operation(plan.operationId)
      ?.takeIf {
        it.action == plan.action &&
          it.requestId != null &&
          it.state !in setOf("complete", "failed")
      }
      ?: error("coordinated-operation-missing")
    val operation = current.copy(
      state = "remote-draining",
      reason = "coordination-unavailable",
      updatedAtMillis = wallClockMillis(),
      completedAtMillis = null,
      remoteDrainUntilMillis = drainUntilMillis,
      serverObservedAtMillis = drainUntilMillis?.let { serverObservedAtMillis },
      acceptedAtElapsedMillis = drainUntilMillis?.let { acceptedAtElapsedMillis },
      acceptedBootCount = drainUntilMillis?.let { acceptedBootCount },
    )
    check(stateStore.putOperation(operation)) { "coordinated-operation-progress-update-failed" }
    return operation
  }

  fun markCoordinatedOperationCompleted(plan: PrivacyActionPlan): DurablePrivacyOperation {
    require(plan.action in COORDINATED_RESET_RELEASE_ACTIONS)
    val current = stateStore.operation(plan.operationId)
      ?.takeIf {
        it.action == plan.action &&
          it.requestId != null &&
          it.state !in setOf("complete", "failed")
      }
      ?: error("coordinated-operation-missing")
    val operation = current.copy(
      state = "local-wiping",
      reason = null,
      updatedAtMillis = wallClockMillis(),
      completedAtMillis = null,
      remoteDrainUntilMillis = null,
      serverObservedAtMillis = null,
      acceptedAtElapsedMillis = null,
      acceptedBootCount = null,
    )
    check(stateStore.putOperation(operation)) { "coordinated-operation-completion-update-failed" }
    return operation
  }

  fun markContactResetRemoteCompleted(plan: PrivacyActionPlan): DurablePrivacyOperation? {
    if (plan.action !in setOf("disconnect-contacts", "revoke-google-access")) return null
    val current = stateStore.operation(plan.operationId)?.takeIf {
      it.action == plan.action &&
        it.localDataErased &&
        it.state !in setOf("complete", "failed")
    } ?: return null
    val at = wallClockMillis().coerceAtLeast(0)
    val completed = current.copy(
      state = if (plan.action == "disconnect-contacts") "complete" else "verifying",
      reason = null,
      updatedAtMillis = at,
      completedAtMillis = at.takeIf { plan.action == "disconnect-contacts" },
      remoteDrainUntilMillis = null,
      serverObservedAtMillis = null,
      acceptedAtElapsedMillis = null,
      acceptedBootCount = null,
    )
    return completed.takeIf { stateStore.putOperation(it) }
  }

  suspend fun markGoogleAccessRevoked(plan: PrivacyActionPlan): DurablePrivacyOperation? {
    if (plan.action != "revoke-google-access") return null
    val current = stateStore.operation(plan.operationId)?.takeIf {
      it.action == plan.action &&
        it.localDataErased &&
        it.state == "verifying"
    } ?: return null
    val recorded = database.withTransaction {
      val account = configurationDao.activeAccount() ?: return@withTransaction false
      recordContactsConsentDecision(
        dao = configurationDao,
        accountId = account.accountId,
        kind = ConsentKind.CONTACTS_READONLY,
        decision = ConsentDecision.REVOKED,
        nowMillis = wallClockMillis().coerceAtLeast(0),
      )
    }
    if (!recorded) return null
    val revoked = current.copy(
      reason = "account-reconnect-required",
      updatedAtMillis = wallClockMillis().coerceAtLeast(0),
      remoteAccessRevoked = true,
    )
    return revoked.takeIf { stateStore.putOperation(it) }
  }

  fun markGoogleAccessRevocationPending(
    plan: PrivacyActionPlan,
    reason: String,
  ): DurablePrivacyOperation? {
    if (plan.action != "revoke-google-access") return null
    val current = stateStore.operation(plan.operationId)?.takeIf {
      it.action == plan.action &&
        it.localDataErased &&
        it.state == "verifying"
    } ?: return null
    val pending = current.copy(
      reason = reason.takeIf { it in LifecycleStateStore.SAFE_REASONS }
        ?: "internal-contract-invalid",
      updatedAtMillis = wallClockMillis().coerceAtLeast(0),
    )
    return pending.takeIf { stateStore.putOperation(it) }
  }

  fun markRemoteDraining(
    plan: PrivacyActionPlan,
    requestId: String,
    drainUntilMillis: Long,
    serverObservedAtMillis: Long,
    acceptedAtElapsedMillis: Long,
    acceptedBootCount: Int,
    reason: String = "firebase-account-deleting",
  ): DurablePrivacyOperation {
    val operation = DurablePrivacyOperation(
      id = plan.operationId,
      action = plan.action,
      state = "remote-draining",
      reason = reason,
      updatedAtMillis = wallClockMillis(),
      completedAtMillis = null,
      requestId = requestId,
      remoteDrainUntilMillis = drainUntilMillis,
      serverObservedAtMillis = serverObservedAtMillis,
      acceptedAtElapsedMillis = acceptedAtElapsedMillis,
      acceptedBootCount = acceptedBootCount,
      localDataErased = false,
      remoteDeletionComplete = false,
    )
    check(stateStore.putOperation(operation)) { "privacy-operation-drain-update-failed" }
    return operation
  }

  fun markDeletionLocalDataErased(plan: PrivacyActionPlan): DurablePrivacyOperation {
    val current = stateStore.operation(plan.operationId)
      ?: return fail(plan, "internal-contract-invalid")
    if (
      current.action != "delete-account" ||
      (current.state != "remote-draining" &&
        !(current.deletionLocalWipeFallback && current.state == "local-wiping")) ||
      current.requestId == null ||
      (!current.deletionLocalWipeFallback && current.remoteDrainUntilMillis == null) ||
      !current.localWipeStarted
    ) return fail(plan, "internal-contract-invalid")
    val fallbackWithoutAcceptedDrain = current.deletionLocalWipeFallback &&
      current.remoteDrainUntilMillis == null
    val operation = current.copy(
      state = if (fallbackWithoutAcceptedDrain) "remote-pending" else "remote-draining",
      reason = if (fallbackWithoutAcceptedDrain) {
        "coordination-unavailable"
      } else {
        "firebase-account-deleting"
      },
      updatedAtMillis = wallClockMillis(),
      completedAtMillis = null,
      localDataErased = true,
      remoteDeletionComplete = false,
    )
    check(stateStore.putOperation(operation)) { "privacy-operation-receipt-update-failed" }
    return operation
  }

  fun completeAccountDeletionReceipt(
    receiptId: String,
    completedAtMillis: Long,
  ): DurableDeletionReceipt? {
    if (completedAtMillis < 0) return null
    val receipt = stateStore.completeDeletionReceipt(receiptId, completedAtMillis)
      ?: return null
    val current = stateStore.latestOperation()?.takeIf {
      it.action == "delete-account" &&
        it.requestId == receiptId &&
        it.state in setOf("remote-draining", "remote-pending") &&
        it.localDataErased &&
        it.remoteDeletionComplete == false
    }
    if (current != null) {
      stateStore.putOperation(
        current.copy(
          state = "complete",
          reason = null,
          updatedAtMillis = completedAtMillis,
          completedAtMillis = completedAtMillis,
          remoteDrainUntilMillis = null,
          serverObservedAtMillis = null,
          acceptedAtElapsedMillis = null,
          acceptedBootCount = null,
          remoteDeletionComplete = true,
          deletionLocalWipeFallback = false,
          recoveryBindingSalt = null,
          recoveryFirebaseUidHash = null,
          recoveryGoogleSubjectHash = null,
          deletionRetryAllowed = false,
          deletionInProgressObserved = false,
        ),
      )
    }
    return receipt
  }

  /**
   * Persists the exact immutable release body before the first network dispatch. Replays may only
   * use this binding; a changed installation generation is a new operation, never a retry.
   */
  fun persistReleaseRequestBinding(
    plan: PrivacyActionPlan,
    account: AccountRecordEntity,
    installationId: String,
    senderEpoch: Long,
    resetGeneration: Long,
  ): DurablePrivacyOperation? {
    if (
      plan.action !in setOf("sign-out-wipe", "wipe-local-data") ||
      !INSTALLATION_ID.matches(installationId) ||
      senderEpoch <= 0 ||
      resetGeneration <= 0
    ) return null
    val current = stateStore.operation(plan.operationId)?.takeIf {
      it.action == plan.action &&
        it.state !in setOf("complete", "failed") &&
        it.requestId != null
    } ?: return null
    val existing = listOf(
      current.remoteRequestInstallationId,
      current.remoteRequestSenderEpoch,
      current.remoteRequestResetGeneration,
    )
    if (existing.any { it != null } && (
        current.remoteRequestInstallationId != installationId ||
          current.remoteRequestSenderEpoch != senderEpoch ||
          current.remoteRequestResetGeneration != resetGeneration
      )) return null
    val firstBindingWrite = existing.all { it == null }
    val existingRecoveryProof = senderReleaseRecoveryBindingProof(current)
    val recoveryProof = existingRecoveryProof
      ?: SenderReleaseRecoveryBindingPolicy.from(account)
      ?: return null
    if (
      existingRecoveryProof != null &&
      !SenderReleaseRecoveryBindingPolicy.matchesAccount(existingRecoveryProof, account)
    ) return null
    val updated = current.copy(
      state = if (firstBindingWrite) "remote-pending" else current.state,
      reason = if (firstBindingWrite) "coordination-unavailable" else current.reason,
      updatedAtMillis = wallClockMillis(),
      completedAtMillis = null,
      remoteRequestInstallationId = installationId,
      remoteRequestSenderEpoch = senderEpoch,
      remoteRequestResetGeneration = resetGeneration,
      senderReleaseRecoverySalt = recoveryProof.salt,
      senderReleaseRecoveryFirebaseUidHash = recoveryProof.firebaseUidHash,
      senderReleaseRecoveryGoogleSubjectHash = recoveryProof.googleSubjectHash,
    )
    return updated.takeIf { stateStore.putOperation(it) }
  }

  /** Write-ahead marker for the irreversible database/key/identity teardown boundary. */
  fun markLocalWipeStarted(
    plan: PrivacyActionPlan,
    installationId: String,
    callbackGeneration: String,
  ): DurablePrivacyOperation? {
    if (
      plan.action !in setOf("delete-account", "sign-out-wipe", "wipe-local-data") ||
      !INSTALLATION_ID.matches(installationId) ||
      !INSTALLATION_ID.matches(callbackGeneration)
    ) return null
    val current = stateStore.operation(plan.operationId)?.takeIf {
      it.action == plan.action && it.state !in setOf("complete", "failed")
    } ?: return null
    if (current.localWipeStarted) {
      return current.takeIf {
        it.wipeInstallationId == installationId &&
          it.wipeCallbackGeneration == callbackGeneration
      }
    }
    val eligible = if (plan.action == "delete-account") {
      current.requestId != null &&
        current.remoteDeletionComplete == false &&
        if (current.deletionLocalWipeFallback) {
          current.state in setOf("local-wiping", "remote-pending") &&
            current.recoveryBindingSalt != null &&
            current.recoveryFirebaseUidHash != null &&
            current.recoveryGoogleSubjectHash != null
        } else {
          current.state == "remote-draining" && current.remoteDrainUntilMillis != null
        }
    } else {
      current.state == "local-wiping" &&
        (current.requestId != null || current.authoritativeRecoveryKind == "sender-release") &&
        current.remoteRequestInstallationId == installationId &&
        current.remoteRequestSenderEpoch?.let { it > 0 } == true &&
        current.remoteRequestResetGeneration?.let { it > 0 } == true &&
        (current.authoritativeRecoveryKind == "sender-release" ||
          senderReleaseRecoveryBindingProof(current) != null)
    }
    if (!eligible) return null
    val marked = current.copy(
      updatedAtMillis = wallClockMillis(),
      localWipeStarted = true,
      wipeInstallationId = installationId,
      wipeCallbackGeneration = callbackGeneration,
    )
    return marked.takeIf { stateStore.putOperation(it) }
  }

  suspend fun repairUnreadableAfterAuthoritativeReset(action: String): DurablePrivacyOperation? {
    if (action !in setOf("disconnect-contacts", "revoke-google-access")) return null
    val now = wallClockMillis().coerceAtLeast(0)
    val operation = DurablePrivacyOperation(
      id = "privacy_${UUID.randomUUID().toString().replace("-", "")}",
      action = action,
      state = "local-wiping",
      reason = null,
      updatedAtMillis = now,
      completedAtMillis = null,
      authoritativeRecoveryKind = "contact-reset",
    )
    val cutoff = authoritativeRepairVisibilityCutoff()
    return operation.takeIf {
      stateStore.repairUnreadableWithAuthoritativeOperation(it, cutoff)
    }
  }

  suspend fun repairUnreadableAfterAuthoritativeRelease(
    action: String,
    installationId: String,
    senderEpoch: Long,
    resetGeneration: Long,
  ): DurablePrivacyOperation? {
    if (
      action !in setOf("sign-out-wipe", "wipe-local-data") ||
      !INSTALLATION_ID.matches(installationId) ||
      senderEpoch <= 0 ||
      resetGeneration <= 0
    ) return null
    val now = wallClockMillis().coerceAtLeast(0)
    val operation = DurablePrivacyOperation(
      id = "privacy_${UUID.randomUUID().toString().replace("-", "")}",
      action = action,
      state = "local-wiping",
      reason = null,
      updatedAtMillis = now,
      completedAtMillis = null,
      remoteRequestInstallationId = installationId,
      remoteRequestSenderEpoch = senderEpoch,
      remoteRequestResetGeneration = resetGeneration,
      authoritativeRecoveryKind = "sender-release",
    )
    val cutoff = authoritativeRepairVisibilityCutoff()
    return operation.takeIf {
      stateStore.repairUnreadableWithAuthoritativeOperation(it, cutoff)
    }
  }

  private suspend fun authoritativeRepairVisibilityCutoff(): Long {
    val latest = dao.activityBounds(0).latestMillis?.coerceAtLeast(0) ?: return 0
    return safeAdd(latest, 1) ?: Long.MAX_VALUE
  }

  fun markDestructiveLocalDataErased(plan: PrivacyActionPlan): DurablePrivacyOperation? {
    val current = stateStore.operation(plan.operationId)?.takeIf {
      it.action == plan.action &&
        it.action in setOf("sign-out-wipe", "wipe-local-data") &&
        it.state == "local-wiping" &&
        it.localWipeStarted &&
        senderReleaseRecoveryBindingProof(it) != null
    } ?: return null
    val erased = current.copy(
      state = "remote-pending",
      reason = "coordination-unavailable",
      updatedAtMillis = wallClockMillis().coerceAtLeast(0),
      completedAtMillis = null,
      localDataErased = true,
      localWipeStarted = false,
      wipeInstallationId = null,
      wipeCallbackGeneration = null,
    )
    return erased.takeIf { stateStore.putOperation(it) }
  }

  fun completeSenderReleaseRemoteCleanup(plan: PrivacyActionPlan): DurablePrivacyOperation? {
    val current = stateStore.operation(plan.operationId)?.takeIf {
      it.action == plan.action &&
        it.action in setOf("sign-out-wipe", "wipe-local-data") &&
        it.localDataErased &&
        it.state in setOf("remote-pending", "remote-draining") &&
        senderReleaseRecoveryBindingProof(it) != null
    } ?: return null
    val at = wallClockMillis().coerceAtLeast(0)
    val completed = current.copy(
      state = "complete",
      reason = null,
      updatedAtMillis = at,
      completedAtMillis = at,
      requestId = null,
      remoteDrainUntilMillis = null,
      serverObservedAtMillis = null,
      acceptedAtElapsedMillis = null,
      acceptedBootCount = null,
      remoteRequestInstallationId = null,
      remoteRequestSenderEpoch = null,
      remoteRequestResetGeneration = null,
      senderReleaseRecoverySalt = null,
      senderReleaseRecoveryFirebaseUidHash = null,
      senderReleaseRecoveryGoogleSubjectHash = null,
    )
    return completed.takeIf { stateStore.putOperation(it) }
  }

  fun completeAuthoritativeSenderReleaseLocalErase(
    plan: PrivacyActionPlan,
  ): DurablePrivacyOperation? {
    val current = stateStore.operation(plan.operationId)?.takeIf {
      it.action == plan.action &&
        it.action in setOf("sign-out-wipe", "wipe-local-data") &&
        it.authoritativeRecoveryKind == "sender-release" &&
        it.state == "local-wiping" &&
        it.localWipeStarted
    } ?: return null
    val at = wallClockMillis().coerceAtLeast(0)
    val completed = current.copy(
      state = "complete",
      reason = null,
      updatedAtMillis = at,
      completedAtMillis = at,
      localDataErased = true,
      localWipeStarted = false,
      wipeInstallationId = null,
      wipeCallbackGeneration = null,
      remoteRequestInstallationId = null,
      remoteRequestSenderEpoch = null,
      remoteRequestResetGeneration = null,
      authoritativeRecoveryKind = null,
    )
    return completed.takeIf { stateStore.putOperation(it) }
  }

  fun failExternalPrivacyAction(
    plan: PrivacyActionPlan,
    reason: String = "internal-contract-invalid",
  ): DurablePrivacyOperation = fail(plan, reason)

  suspend fun retainSignedOutAccount(
    plan: PrivacyActionPlan,
    accountId: String,
  ): DurablePrivacyOperation {
    val retained = database.withTransaction {
      val account = dao.account(accountId)
        ?.takeIf { it.activeSlot == 1 }
        ?: return@withTransaction false
      when (account.state) {
        AccountRecordState.ACTIVE -> {
          if (dao.markAccountRetainedSignedOut(accountId, wallClockMillis()) != 1) {
            return@withTransaction false
          }
          check(dao.bumpRetainedSignOutBoundary() == 1) {
            "retained-sign-out-boundary-failed"
          }
          true
        }
        AccountRecordState.RETAINED_SIGNED_OUT -> true
        else -> false
      }
    }
    return if (retained) complete(plan) else markLocalCleanupPending(
      plan,
      "internal-contract-invalid",
    )
  }

  suspend fun purgeContactDerivedState(
    plan: PrivacyActionPlan,
    accountId: String,
  ): DurablePrivacyOperation {
    markLocalWiping(plan)
    return try {
      val purged = database.withTransaction {
        val account = dao.activeAccount()
        val control = dao.control()
        if (
          account?.accountId != accountId ||
          control == null ||
          control.automationDesired ||
          control.accountMode != "PAUSED_REPAIR"
        ) return@withTransaction false

        val now = wallClockMillis().coerceAtLeast(0)
        if (!recordContactsConsentDecision(
            dao = configurationDao,
            accountId = accountId,
            kind = ConsentKind.CONTACTS_DISCLOSURE,
            decision = ConsentDecision.REVOKED,
            nowMillis = now,
          )) return@withTransaction false

        dao.deleteDeliveryEvents(accountId)
        dao.deleteCallbackTokens(accountId)
        dao.deleteSendAttempts(accountId)
        dao.deleteTestReceipts(accountId)
        dao.deleteOutcomeProjections(accountId)
        dao.deleteCoordinationPermits(accountId)
        dao.deleteDestinationGuards(accountId)
        dao.deleteBirthdayOccurrences(accountId)
        dao.deleteTestJobs(accountId)
        dao.deleteConfigurationReviews(accountId)
        dao.deleteApprovalSnapshots(accountId)
        dao.deleteRecipientPolicies(accountId)
        dao.deleteBirthdayChoices(accountId)
        dao.deleteContactPhones(accountId)
        dao.deleteContactSnapshots(accountId)
        dao.deleteStagingBirthdays(accountId)
        dao.deleteStagingPhones(accountId)
        dao.deleteStagingContacts(accountId)
        check(dao.markContactsDisconnected(accountId, now) == 1) {
          "contacts-disconnect-sync-state-failed"
        }
        dao.deleteSyncGenerations(accountId)
        dao.deleteDestinationBlocks(accountId)
        dao.deleteMessageTemplates(accountId)
        dao.deleteAutomationPolicies(accountId)
        dao.deleteLegacyOccurrences()
        dao.deleteLegacyApprovals()
        dao.deleteLegacyContacts()
        true
      }
      if (!purged) {
        markLocalCleanupPending(plan, "internal-contract-invalid")
      } else {
        val current = stateStore.operation(plan.operationId)
          ?.takeIf { it.action == plan.action }
          ?: return markLocalCleanupPending(plan, "internal-contract-invalid")
        val locallyErased = current.copy(
          state = "remote-pending",
          reason = "coordination-unavailable",
          updatedAtMillis = wallClockMillis().coerceAtLeast(0),
          completedAtMillis = null,
          localDataErased = true,
          authoritativeRecoveryKind = null,
        )
        if (stateStore.putOperation(locallyErased)) {
          locallyErased
        } else {
          markLocalCleanupPending(plan, "internal-contract-invalid")
        }
      }
    } catch (_: RuntimeException) {
      markLocalCleanupPending(plan, "internal-contract-invalid")
    }
  }

  /** Local-only portion of disconnect; authoritative remote reset owns terminal completion. */
  suspend fun disconnectContacts(
    plan: PrivacyActionPlan,
    accountId: String,
  ): DurablePrivacyOperation = purgeContactDerivedState(plan, accountId)

  fun operationPayload(id: String): JSONObject? = if (lifecycleJournalUnreadable()) {
    unavailableOrNonePayload()
  } else {
    stateStore.operation(id)?.let(::operationPayload)
  }

  fun currentOperationPayload(): JSONObject = stateStore.latestOperation()
    ?.takeIf { it.action in LifecycleStateStore.PRIVACY_ACTIONS }
    ?.let(::operationPayload)
    ?: unavailableOrNonePayload()

  fun latestDeletionReceiptPayload(): JSONObject {
    return when (val lookup = deletionReceiptLookup()) {
      DeletionReceiptLookup.None -> JSONObject().put("kind", "none")
      DeletionReceiptLookup.Unavailable -> coordinationUnavailablePayload()
      is DeletionReceiptLookup.Present -> when (lookup.receipt.state) {
        DurableDeletionReceipt.State.PENDING -> pendingDeletionReceiptPayload(lookup.receipt)
        DurableDeletionReceipt.State.COMPLETED -> completedDeletionReceiptPayload(lookup.receipt)
      }
    }
  }

  fun deletionReceiptLookup(): DeletionReceiptLookup {
    val lookup = stateStore.deletionReceiptLookup(wallClockMillis().coerceAtLeast(0))
    val receipt = (lookup as? DeletionReceiptLookup.Present)?.receipt
      ?.takeIf { it.state == DurableDeletionReceipt.State.COMPLETED }
      ?: return lookup
    val stale = stateStore.latestOperation()?.takeIf {
      it.id == receipt.operationId &&
        it.action == "delete-account" &&
        it.requestId == receipt.receiptId &&
        it.state in setOf("remote-draining", "remote-pending") &&
        it.localDataErased &&
        it.remoteDeletionComplete == false
    }
    if (stale != null) {
      stateStore.putOperation(
        stale.copy(
          state = "complete",
          reason = null,
          updatedAtMillis = checkNotNull(receipt.completedAtMillis),
          completedAtMillis = receipt.completedAtMillis,
          remoteDrainUntilMillis = null,
          serverObservedAtMillis = null,
          acceptedAtElapsedMillis = null,
          acceptedBootCount = null,
          remoteDeletionComplete = true,
          deletionLocalWipeFallback = false,
          recoveryBindingSalt = null,
          recoveryFirebaseUidHash = null,
          recoveryGoogleSubjectHash = null,
          deletionRetryAllowed = false,
          deletionInProgressObserved = false,
        ),
      )
    }
    return lookup
  }

  fun prepareForOrdinaryAccountIdentity(): Boolean =
    stateStore.prepareForOrdinaryAccountIdentity()

  fun senderTransferOperationProjectionPayload(): JSONObject = stateStore.latestOperation()
    ?.takeIf { it.action == "sender-transfer" }
    ?.let(::senderTransferOperationPayload)
    ?: unavailableOrNonePayload()

  fun lifecycleJournalUnreadable(): Boolean =
    stateStore.journalStatus() == LifecycleJournalStatus.UNREADABLE

  fun deletionLocalWipeReviewAllowed(): Boolean = stateStore.latestOperation()
    ?.let(::eligibleDeletionLocalWipeFallback) == true

  fun deletionRecoveryReauthenticationAllowed(): Boolean = stateStore.latestOperation()
    ?.let { operation ->
      operation.deletionLocalWipeFallback &&
        operation.localDataErased &&
        operation.deletionRetryAllowed &&
        operation.remoteDeletionComplete == false
    } == true

  fun senderReleaseRecoveryReauthenticationAllowed(): Boolean = stateStore.latestOperation()
    ?.let { operation ->
      operation.action in setOf("sign-out-wipe", "wipe-local-data") &&
        operation.localDataErased &&
        operation.state in setOf("remote-pending", "remote-draining") &&
        operation.requestId != null &&
        operation.remoteRequestInstallationId != null &&
        operation.remoteRequestSenderEpoch?.let { it > 0 } == true &&
        operation.remoteRequestResetGeneration?.let { it > 0 } == true &&
        senderReleaseRecoveryBindingProof(operation) != null
    } == true

  fun matchesSenderReleaseRecoveryBinding(
    binding: com.yashsomani.birthdayautopilot.auth.NativeAccountBinding,
  ): Boolean = stateStore.latestOperation()?.let(::senderReleaseRecoveryBindingProof)?.let { proof ->
    SenderReleaseRecoveryBindingPolicy.matches(proof, binding)
  } == true

  fun matchesSenderReleaseRecoveryGoogleSubject(
    googleSubject: String,
  ): Boolean = stateStore.latestOperation()?.let(::senderReleaseRecoveryBindingProof)?.let { proof ->
    SenderReleaseRecoveryBindingPolicy.matchesGoogleSubject(proof, googleSubject)
  } == true

  private fun senderReleaseRecoveryBindingProof(
    operation: DurablePrivacyOperation,
  ): SenderReleaseRecoveryBindingProof? {
    if (operation.action !in setOf("sign-out-wipe", "wipe-local-data")) return null
    return SenderReleaseRecoveryBindingProof(
      salt = operation.senderReleaseRecoverySalt ?: return null,
      firebaseUidHash = operation.senderReleaseRecoveryFirebaseUidHash ?: return null,
      googleSubjectHash = operation.senderReleaseRecoveryGoogleSubjectHash ?: return null,
    ).takeIf(SenderReleaseRecoveryBindingPolicy::valid)
  }

  fun matchesDeletionRecoveryBinding(
    binding: com.yashsomani.birthdayautopilot.auth.NativeAccountBinding,
  ): Boolean = deletionRecoveryBindingProof()?.let { proof ->
    DeletionRecoveryBindingPolicy.matches(proof, binding)
  } == true

  fun matchesDeletionRecoveryGoogleSubject(
    googleSubject: String,
  ): Boolean = deletionRecoveryBindingProof()?.let { proof ->
    DeletionRecoveryBindingPolicy.matchesGoogleSubject(proof, googleSubject)
  } == true

  private fun deletionRecoveryBindingProof(): DeletionRecoveryBindingProof? {
    val operation = stateStore.latestOperation()?.takeIf {
      it.deletionLocalWipeFallback && it.localDataErased && it.deletionRetryAllowed
    } ?: return null
    return DeletionRecoveryBindingProof(
      salt = operation.recoveryBindingSalt ?: return null,
      firebaseUidHash = operation.recoveryFirebaseUidHash ?: return null,
      googleSubjectHash = operation.recoveryGoogleSubjectHash ?: return null,
    )
  }

  fun setDeletionRecoveryStatus(
    retryAllowed: Boolean,
    inProgressObserved: Boolean,
  ): DurablePrivacyOperation? {
    if (retryAllowed && inProgressObserved) return null
    val current = stateStore.latestOperation()?.takeIf {
      it.action == "delete-account" &&
        it.localDataErased &&
        it.remoteDeletionComplete == false &&
        it.state in setOf("remote-pending", "remote-draining")
    } ?: return null
    if (!current.deletionLocalWipeFallback) {
      // The accepted drain tuple is already stronger than a later status response. Never require
      // fallback-only evidence or downgrade this durable proof to unknown/unavailable.
      return current.takeIf {
        it.state == "remote-draining" &&
          it.remoteDrainUntilMillis != null &&
          it.serverObservedAtMillis != null &&
          it.acceptedAtElapsedMillis != null &&
          it.acceptedBootCount != null
      }
    }
    if (current.deletionInProgressObserved && !inProgressObserved) return current
    val updated = current.copy(
      state = if (current.remoteDrainUntilMillis == null) "remote-pending" else current.state,
      reason = if (current.remoteDrainUntilMillis == null) {
        "coordination-unavailable"
      } else {
        current.reason
      },
      updatedAtMillis = wallClockMillis().coerceAtLeast(0),
      deletionRetryAllowed = retryAllowed,
      deletionInProgressObserved = inProgressObserved,
    )
    return updated.takeIf { stateStore.putOperation(it) }
  }

  fun markDeletionRecoveryAccepted(
    requestId: String,
    drainUntilMillis: Long,
    serverObservedAtMillis: Long,
    acceptedAtElapsedMillis: Long,
    acceptedBootCount: Int,
  ): DurablePrivacyOperation? {
    val current = stateStore.latestOperation()?.takeIf {
      it.deletionLocalWipeFallback &&
        it.action == "delete-account" &&
        it.localDataErased &&
        it.requestId == requestId &&
        it.remoteDeletionComplete == false
    } ?: return null
    if (
      drainUntilMillis < 0 ||
      serverObservedAtMillis < 0 ||
      acceptedAtElapsedMillis < 0 ||
      acceptedBootCount < 0
    ) return null
    val accepted = current.copy(
      state = "remote-draining",
      reason = "firebase-account-deleting",
      updatedAtMillis = wallClockMillis().coerceAtLeast(0),
      remoteDrainUntilMillis = drainUntilMillis,
      serverObservedAtMillis = serverObservedAtMillis,
      acceptedAtElapsedMillis = acceptedAtElapsedMillis,
      acceptedBootCount = acceptedBootCount,
      deletionLocalWipeFallback = false,
      recoveryBindingSalt = null,
      recoveryFirebaseUidHash = null,
      recoveryGoogleSubjectHash = null,
      deletionRetryAllowed = false,
      deletionInProgressObserved = false,
    )
    return accepted.takeIf { stateStore.putOperation(it) }
  }

  private fun unavailableOrNonePayload(): JSONObject = if (lifecycleJournalUnreadable()) {
    JSONObject()
      .put("kind", "unavailable")
      .put("reason", "coordination-unavailable")
  } else {
    JSONObject().put("kind", "none")
  }

  fun operationPayload(operation: DurablePrivacyOperation): JSONObject {
    if (
      operation.action == "delete-account" &&
      operation.deletionLocalWipeFallback &&
      operation.localDataErased
    ) {
      return if (operation.deletionInProgressObserved) {
        remoteDrainingDeletionPayload(operation.id, operation.updatedAtMillis)
      } else {
        remoteUnknownDeletionPayload(operation)
      }
    }
    return JSONObject()
      .put("kind", operation.state)
      .put("id", operation.id)
      .put("action", operation.action)
      .apply {
      if (operation.state in setOf("remote-pending", "failed")) {
        operation.reason?.let { put("reason", it) }
      }
      if (operation.state == "complete") {
        put(
          "completedAt",
          Instant.ofEpochMilli(checkNotNull(operation.completedAtMillis)).toString(),
        )
        put("externalSmsCopiesNotErased", true)
      } else {
        put("updatedAt", Instant.ofEpochMilli(operation.updatedAtMillis).toString())
      }
      if (
        operation.action == "delete-account" &&
        operation.state == "remote-draining" &&
        operation.localDataErased
      ) {
        put("localDataErased", true)
        put("remoteDeletionComplete", false)
        put("externalSmsCopiesNotErased", true)
      }
      }
  }

  private fun pendingDeletionReceiptPayload(receipt: DurableDeletionReceipt): JSONObject {
    require(receipt.state == DurableDeletionReceipt.State.PENDING) {
      "pending-deletion-receipt-required"
    }
    val operation = stateStore.latestOperation()?.takeIf {
      it.id == receipt.operationId &&
        it.action == "delete-account" &&
        it.requestId == receipt.receiptId &&
        it.localDataErased &&
        it.remoteDeletionComplete == false
    } ?: return coordinationUnavailablePayload()
    return if (operation.deletionLocalWipeFallback) {
      if (operation.deletionInProgressObserved) {
        remoteDrainingDeletionPayload(receipt.operationId, operation.updatedAtMillis)
      } else {
        remoteUnknownDeletionPayload(operation)
      }
    } else if (
      operation.state == "remote-draining" &&
      operation.remoteDrainUntilMillis != null &&
      operation.serverObservedAtMillis != null &&
      operation.acceptedAtElapsedMillis != null &&
      operation.acceptedBootCount != null
    ) {
      remoteDrainingDeletionPayload(receipt.operationId, operation.updatedAtMillis)
    } else {
      coordinationUnavailablePayload()
    }
  }

  private fun coordinationUnavailablePayload(): JSONObject = JSONObject()
    .put("kind", "unavailable")
    .put("reason", "coordination-unavailable")

  private fun remoteDrainingDeletionPayload(
    operationId: String,
    updatedAtMillis: Long,
  ): JSONObject = JSONObject()
      .put("kind", "remote-draining")
      .put("id", operationId)
      .put("action", "delete-account")
      .put("updatedAt", Instant.ofEpochMilli(updatedAtMillis).toString())
      .put("localDataErased", true)
      .put("remoteDeletionComplete", false)
      .put("externalSmsCopiesNotErased", true)

  private fun remoteUnknownDeletionPayload(
    operation: DurablePrivacyOperation,
  ): JSONObject = JSONObject()
    .put("kind", "remote-unknown")
    .put("id", operation.id)
    .put("action", "delete-account")
    .put("reason", "coordination-unavailable")
    .put("updatedAt", Instant.ofEpochMilli(operation.updatedAtMillis).toString())
    .put("localDataErased", true)
    .put("remoteDeletionComplete", false)
    .put("sameAccountRetryAvailable", operation.deletionRetryAllowed)
    .put("externalSmsCopiesNotErased", true)

  private fun completedDeletionReceiptPayload(receipt: DurableDeletionReceipt): JSONObject {
    require(receipt.state == DurableDeletionReceipt.State.COMPLETED) {
      "completed-deletion-receipt-required"
    }
    return JSONObject()
      .put("kind", "complete")
      .put("id", receipt.operationId)
      .put("action", "delete-account")
      .put(
        "completedAt",
        Instant.ofEpochMilli(checkNotNull(receipt.completedAtMillis)).toString(),
      )
      .put("localDataErased", true)
      .put("remoteDeletionComplete", true)
      .put("externalSmsCopiesNotErased", true)
  }

  fun senderTransferOperationPayload(operation: DurablePrivacyOperation): JSONObject {
    require(operation.action == "sender-transfer") { "sender-transfer-operation-required" }
    return JSONObject()
      .put("kind", operation.state)
      .put("id", operation.id)
      .put("preissuedPermitMayFinish", operation.state != "complete")
      .apply {
        operation.reason?.let { put("reason", it) }
        if (operation.state == "complete") {
          put("completedAt", Instant.ofEpochMilli(checkNotNull(operation.completedAtMillis)).toString())
          put("requiresTest", true)
        } else {
          put("updatedAt", Instant.ofEpochMilli(operation.updatedAtMillis).toString())
        }
        operation.remoteDrainUntilMillis?.let {
          put("drainUntil", Instant.ofEpochMilli(it).toString())
        }
      }
  }

  fun latestOperation(): DurablePrivacyOperation? = stateStore.latestOperation()

  fun privacyPlanForOperation(operationId: String): PrivacyActionPlan? =
    stateStore.operation(operationId)?.takeIf {
      it.action in LifecycleStateStore.PRIVACY_ACTIONS &&
        it.state !in setOf("complete", "failed")
    }?.let { operation ->
      PrivacyActionPlan(
        operationId = operation.id,
        action = operation.action,
        requiresPause = operation.action !in setOf("clear-activity"),
        remoteRequired = operation.action in REMOTE_PRIVACY_ACTIONS,
        deletionLocalWipeFallback = operation.deletionLocalWipeFallback,
      )
    }

  /** Generates once and durably binds the random v4 bearer before any network attempt. */
  fun deletionRequestId(plan: PrivacyActionPlan): String? {
    val current = stateStore.operation(plan.operationId)?.takeIf {
      it.action == "delete-account" && it.state !in setOf("complete", "failed")
    } ?: return null
    current.requestId?.takeIf(CoordinationValuePolicy::isUuidV4)?.let { return it }
    if (
      current.state == "remote-draining" ||
      current.localWipeStarted ||
      current.localDataErased ||
      current.remoteDeletionComplete != null
    ) return null
    val requestId = UUID.randomUUID().toString()
    if (!CoordinationValuePolicy.isUuidV4(requestId)) return null
    val bound = current.copy(requestId = requestId, updatedAtMillis = wallClockMillis())
    return requestId.takeIf { stateStore.putOperation(bound) }
  }

  fun coordinatedRequestId(plan: PrivacyActionPlan): String? = stateStore.operation(plan.operationId)
    ?.takeIf {
      it.action == plan.action &&
        it.action in COORDINATED_MUTATION_ACTIONS &&
        it.state !in setOf("complete", "failed")
    }
    ?.requestId

  suspend fun performNativeAction(handle: String, expectedRevision: Long): JSONObject? {
    val match = ACTION_HANDLE.matchEntire(handle) ?: return null
    val code = match.groupValues[1]
    val revision = match.groupValues[2].toLongOrNull() ?: return null
    if (revision != expectedRevision || dao.control()?.revision != revision) return null
    if (code !in ACTIONABLE_CODES) return null
    if (code == "permission-denied") {
      val result = ForegroundActivityRegistry.currentTelephonyPermissionLauncher()?.request()
        ?: TelephonyPermissionResult.UNAVAILABLE
      return JSONObject().put(
        "kind",
        if (result == TelephonyPermissionResult.UNAVAILABLE) "cancelled" else "opened",
      ).put(
        "permissionResult",
        when (result) {
          TelephonyPermissionResult.GRANTED -> "granted"
          TelephonyPermissionResult.PHONE_STATE_DENIED -> "phone-state-denied"
          TelephonyPermissionResult.PHONE_STATE_PERMANENTLY_DENIED ->
            "phone-state-permanently-denied"
          TelephonyPermissionResult.SMS_DENIED -> "sms-denied"
          TelephonyPermissionResult.SMS_PERMANENTLY_DENIED -> "sms-permanently-denied"
          TelephonyPermissionResult.UNAVAILABLE -> "unavailable"
        },
      )
    }
    val activity = activityProvider() ?: return JSONObject().put("kind", "cancelled")
    val intent = settingsIntent(code) ?: return null
    return try {
      activity.startActivity(intent)
      JSONObject().put("kind", "opened")
    } catch (_: RuntimeException) {
      JSONObject().put("kind", "cancelled")
    }
  }

  fun actionPayload(code: String, revision: Long): JSONObject? =
    code.takeIf { it in ACTIONABLE_CODES }?.let {
      JSONObject()
        .put("kind", "native-action")
        .put("handle", "action:$it:$revision")
        .put("labelKey", "readiness.repair.$it")
    }

  private fun activityRecord(row: LifecycleActivityRow): JSONObject {
    val kind = activityKind(row)
    val reason = safeReason(row.safeCode ?: row.state)
    return JSONObject()
      .put(
        "id",
        AutomationOpaqueIds.prefixed(
          "activity",
          "LifecycleActivity.v1",
          row.sourceKey,
          row.state,
          row.occurredAtMillis.toString(),
        ),
      )
      .put("kind", kind)
      .put("occurredAt", Instant.ofEpochMilli(row.occurredAtMillis.coerceAtLeast(0)).toString())
      .put("actionable", kind in ACTIONABLE_ACTIVITY_KINDS)
      .apply { reason?.let { put("reason", it) } }
  }

  private fun activityKind(row: LifecycleActivityRow): String {
    val state = row.state.uppercase()
    if (row.sourceType == "outcome") return when (state) {
      "DELIVERED", "DELIVERED_LATE" -> "delivered"
      "DELIVERY_FAILED", "DELIVERY_FAILED_LATE" -> "delivery-failed"
      "PARTIAL_DELIVERY", "PARTIAL_DELIVERY_LATE" -> "partial-delivery"
      "DELIVERY_UNKNOWN", "PARTIAL_DELIVERY_UNKNOWN" -> "delivery-unknown"
      "SENT_FROM_DEVICE", "RETRY_SENT_FROM_DEVICE", "SENT_EVIDENCE_LATE", "TEST_PASSED" ->
        "sent-from-device"
      "PARTIAL_UNKNOWN", "SUBMISSION_UNKNOWN" -> "submission-unknown"
      "ZERO_ACCEPTED_RADIO_OFF", "ZERO_ACCEPTED_NO_SERVICE", "ZERO_ACCEPTED_LATE",
      "PERMANENT_FAILURE", "RETRY_EXHAUSTED", "TEST_FAILED",
      -> "submission-failed"
      else -> "submitted"
    }
    return when {
      "PLANNED" in state || "PREPARED" in state || "SCHEDULED" in state -> "planned"
      "COORDINATION_BLOCKED" in state || "COORDINATION_UNKNOWN" in state ||
        "CLOUD_CLAIMED" in state || "ARM_RECONCILING" in state ->
        "coordination-blocked"
      "ARMED_SUPPRESSED" in state -> "armed-suppressed"
      "MISSED" in state -> "missed"
      "SKIPPED" in state || "CANCELLED" in state || "CLEANUP_CANCELLED" in state -> "skipped"
      "DELIVERED" in state && "FAILED" !in state -> "delivered"
      "DELIVERY_FAILED" in state -> "delivery-failed"
      "PARTIAL_DELIVERY_UNKNOWN" in state || "DELIVERY_UNKNOWN" in state -> "delivery-unknown"
      "PARTIAL_DELIVERY" in state -> "partial-delivery"
      "SENT_FROM_DEVICE" in state || state == "PASSED" -> "sent-from-device"
      "SUBMISSION_UNKNOWN" in state || state == "UNKNOWN" || "PARTIAL_UNKNOWN" in state ->
        "submission-unknown"
      "PERMANENT_FAILURE" in state || state == "FAILED" || "RETRY_EXHAUSTED" in state ||
        "RETRYABLE_ZERO" in state ->
        "submission-failed"
      "SUBMITTED" in state || "BARRIER_CONSUMED" in state || "CLOUD_ARMED" in state -> "submitted"
      "PAUSED" in state -> "paused"
      "APPROVAL" in state || "RECEIPT_INVALIDATED" in state -> "approval-invalidated"
      "SYNC" in state -> "sync"
      "TRANSFER" in state -> "transfer"
      else -> "settings-changed"
    }
  }

  private fun birthdayPhase(state: String): String = when (state) {
    "PLANNED" -> "planned"
    "PREPARED" -> "prepared"
    "SCHEDULED" -> "scheduled"
    "CLAIMED", "RETRY_CLAIMED" -> "claimed"
    "COORDINATION_BLOCKED" -> "coordination-blocked"
    "CLOUD_CLAIMED" -> "cloud-claimed"
    "ARM_RECONCILING" -> "arm-reconciling"
    "COORDINATION_UNKNOWN" -> "coordination-unknown"
    "CLOUD_ARMED" -> "cloud-armed"
    "ARMED_SUPPRESSED" -> "armed-suppressed"
    "SUBMISSION_BARRIER_CONSUMED" -> "submission-barrier-consumed"
    "SUBMITTED" -> "submitted"
    "SENT_FROM_DEVICE" -> "sent-from-device"
    "RETRYABLE_ZERO" -> "retryable-failure"
    "RETRY_EXHAUSTED" -> "retry-exhausted"
    "DELIVERED" -> "delivered"
    "DELIVERY_FAILED" -> "delivery-failed"
    "PARTIAL_DELIVERY" -> "partial-delivery"
    "PARTIAL_DELIVERY_UNKNOWN" -> "partial-delivery-unknown"
    "DELIVERY_UNKNOWN" -> "delivery-unknown"
    "PARTIAL_UNKNOWN" -> "partial-unknown"
    "SUBMISSION_UNKNOWN" -> "unknown"
    "PERMANENT_FAILURE" -> "permanent-failure"
    "SKIPPED" -> "skipped"
    "MISSED" -> "missed"
    else -> "cancelled"
  }

  private fun safeReason(raw: String?): String? {
    val value = raw.orEmpty().uppercase()
    return when {
      value.isBlank() -> null
      "NETWORK" in value -> "network-offline"
      "RECEIPT" in value || "TEST_REQUIRED" in value -> "test-receipt-invalid"
      "PERMISSION" in value -> "permission-denied"
      "NO_SERVICE" in value || "SIM" in value -> "sim-invalid"
      "CLOCK" in value -> "clock-untrusted"
      "RESET" in value -> "reset-safety-blocked"
      "CONTACT" in value || "SYNC" in value -> "contacts-stale"
      "APPROVAL" in value || "TEMPLATE" in value -> "approval-invalid"
      "TRANSFER" in value -> "transfer-pending"
      "DELET" in value -> "firebase-account-deleting"
      "BACKGROUND" in value -> "background-restricted"
      "DOZE" in value -> "doze-exemption-missing"
      "DATA_SAVER" in value -> "data-saver-restricted"
      "LOW_POWER" in value -> "low-power-standby-unsafe"
      "UNKNOWN" in value || "COORDINATION" in value || "ARM" in value ->
        "coordination-unavailable"
      else -> null
    }
  }

  private fun parseCursor(raw: String): Pair<Long, String>? {
    if (!raw.startsWith("activity:") || raw.length > 256) return null
    val remainder = raw.removePrefix("activity:")
    val separator = remainder.indexOf(':')
    if (separator <= 0) return null
    val millis = remainder.substring(0, separator).toLongOrNull()?.takeIf { it >= 0 } ?: return null
    val source = remainder.substring(separator + 1)
    if (!CURSOR_SOURCE.matches(source)) return null
    return millis to source
  }

  private fun databaseBytes(): Long = listOf(
    appContext.getDatabasePath(com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase.DATABASE_NAME),
    File(appContext.getDatabasePath(com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase.DATABASE_NAME).path + "-wal"),
    File(appContext.getDatabasePath(com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase.DATABASE_NAME).path + "-shm"),
  ).sumOf { file -> file.length().coerceAtLeast(0) }

  private fun settingsIntent(code: String): Intent? = when (code) {
    "background-restricted", "unused-app-restrictions-unsafe" ->
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData("package:${appContext.packageName}".toUri())
    "permission-permanently-denied" ->
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData("package:${appContext.packageName}".toUri())
    "data-saver-restricted" -> Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS)
      .setData("package:${appContext.packageName}".toUri())
    "doze-exemption-missing" -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    "low-power-standby-unsafe" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
    "no-active-sim", "sim-changed", "sim-invalid" -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
    "clock-untrusted" -> Intent(Settings.ACTION_DATE_SETTINGS)
    else -> null
  }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  private fun updateOperation(
    plan: PrivacyActionPlan,
    state: String,
    reason: String?,
    at: Long = wallClockMillis(),
  ): DurablePrivacyOperation {
    val current = stateStore.operation(plan.operationId)
      ?.takeIf { it.action == plan.action }
      ?: error("privacy-operation-missing")
    val operation = current.copy(
      state = state,
      reason = reason,
      updatedAtMillis = at,
      completedAtMillis = null,
    )
    check(stateStore.putOperation(operation)) { "privacy-operation-update-failed" }
    return operation
  }

  private fun complete(plan: PrivacyActionPlan): DurablePrivacyOperation {
    val at = wallClockMillis()
    val current = stateStore.operation(plan.operationId)
      ?.takeIf { it.action == plan.action }
      ?: error("privacy-operation-missing")
    check(current.action !in setOf("sign-out-wipe", "wipe-local-data", "disconnect-contacts")) {
      "privacy-operation-requires-authoritative-remote-completion"
    }
    check(current.action != "revoke-google-access" || current.remoteAccessRevoked) {
      "google-access-revocation-not-complete"
    }
    val operation = current.copy(
      state = "complete",
      reason = null,
      updatedAtMillis = at,
      completedAtMillis = at,
    )
    check(stateStore.putOperation(operation)) { "privacy-operation-complete-failed" }
    return operation
  }

  private fun fail(plan: PrivacyActionPlan, reason: String): DurablePrivacyOperation {
    val current = stateStore.operation(plan.operationId)
    // A committed wipe marker cannot be rolled back. Startup recovery must finish it.
    if (current?.localWipeStarted == true) return current
    return updateOperation(plan, "failed", reason)
  }

  private fun stale(latest: Long) = ConfigurationOutcome.Problem(
    JSONObject().put("kind", "stale-revision").put("latestRevision", latest.toString()),
  )

  private fun conflict(code: String) = ConfigurationOutcome.Problem(
    JSONObject().put("kind", "conflict").put("code", code),
  )

  private fun eligibleDeletionLocalWipeFallback(
    operation: DurablePrivacyOperation,
  ): Boolean = operation.action == "delete-account" &&
    operation.state in setOf("remote-pending", "remote-draining") &&
    operation.requestId?.let(CoordinationValuePolicy::isUuidV4) == true &&
    !operation.localWipeStarted &&
    !operation.localDataErased &&
    operation.remoteDeletionComplete != true &&
    !operation.deletionLocalWipeFallback

  private fun internal(code: String) = ConfigurationOutcome.Problem(
    JSONObject().put("kind", "internal").put("supportCode", code),
  )

  private fun safeAdd(left: Long, right: Long): Long? = try {
    Math.addExact(left, right)
  } catch (_: ArithmeticException) {
    null
  }

  private fun JSONObject.keyNames(): Set<String> = buildSet {
    val iterator = keys()
    while (iterator.hasNext()) add(iterator.next())
  }

  private fun JSONObject.strictInt(name: String): Int? {
    val value = opt(name) as? Number ?: return null
    val number = value.toDouble()
    if (!number.isFinite() || number % 1.0 != 0.0 || number !in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
      return null
    }
    return number.toInt()
  }

  private fun boundedCount(value: Int): Int = value.coerceIn(0, 1_000_000)

  private fun safeDisplayName(value: String): Boolean =
    value.length in 1..256 && value.isNotBlank() && value.none(::unsafeUiCharacter)

  private fun safeMessage(value: String): Boolean =
    value.length in 1..1_000 &&
      value.isNotBlank() &&
      !UnicodeTextSafety.containsUnsafeMessageCodePoint(value)

  private fun unsafeUiCharacter(char: Char): Boolean =
    char.code == 0x7f || char.isISOControl() || Character.getType(char) == Character.FORMAT.toInt()

  private fun todayLimitations(choice: TodayOccurrenceChoice): String = when (choice) {
    TodayOccurrenceChoice.NORMAL_PATH ->
      "The normal path still requires protected Claim, Arm, and one-shot checks. It does not promise delivery."
    TodayOccurrenceChoice.SYSTEM_COMPOSER ->
      "Android will receive only the freshly reviewed recipient and exact approved draft. Opening the composer retires unattended automation for today; you control edit, Send, or Cancel, and the app cannot observe or claim delivery."
    TodayOccurrenceChoice.NEXT_YEAR ->
      "Starting next year retires today's unattended occurrence and never sends or opens Messages today."
  }

  private companion object {
    const val MAX_ACTIVITY_PAGE_SIZE = 50
    const val REVIEW_PRIVACY = "PRIVACY"
    const val REVIEW_SENDER_TRANSFER = "SENDER_TRANSFER"
    const val REVIEW_TODAY = "TODAY_OCCURRENCE"
    const val REVIEW_TTL_MILLIS = 10 * 60 * 1_000L
    const val MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L
    val CURSOR_SOURCE = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,180}$")
    val OCCURRENCE_ID = Regex("^occ_[a-f0-9]{64}$")
    val TODAY_HANDLE = Regex("^to_[a-f0-9]{32}$")
    val SENDER_TRANSFER_HANDLE = Regex("^st_[a-f0-9]{32}$")
    val INSTALLATION_ID = Regex("^[a-f0-9]{32}$")
    val TODAY_REVIEW_KEYS = setOf(
      "occurrenceId",
      "occurrenceRevision",
      "primaryChoice",
      "alternativeChoice",
      "installationId",
      "senderEpoch",
      "resetGeneration",
    )
    val SENDER_TRANSFER_REVIEW_KEYS = setOf(
      "accountId",
      "activeInstallationId",
      "targetInstallationId",
      "senderEpoch",
      "resetGeneration",
    )
    val ACTION_HANDLE = Regex("^action:([a-z0-9-]+):(0|[1-9][0-9]*)$")
    val ACTIONABLE_CODES = setOf(
      "permission-denied",
      "permission-permanently-denied",
      "background-restricted",
      "unused-app-restrictions-unsafe",
      "data-saver-restricted",
      "doze-exemption-missing",
      "low-power-standby-unsafe",
      "no-active-sim",
      "sim-changed",
      "sim-invalid",
      "clock-untrusted",
    )
    val ACTIONABLE_ACTIVITY_KINDS = setOf(
      "coordination-blocked",
      "armed-suppressed",
      "missed",
      "delivery-failed",
      "partial-delivery",
      "delivery-unknown",
      "submission-failed",
      "submission-unknown",
      "paused",
      "approval-invalidated",
      "transfer",
    )
    val REMOTE_PRIVACY_ACTIONS = setOf(
      "disconnect-contacts",
      "revoke-google-access",
      "sign-out-retain",
      "sign-out-wipe",
      "delete-account",
      "wipe-local-data",
    )
    val COORDINATED_MUTATION_ACTIONS = setOf(
      "disconnect-contacts",
      "revoke-google-access",
      "sign-out-wipe",
      "delete-account",
      "wipe-local-data",
    )
    val COORDINATED_RESET_RELEASE_ACTIONS = setOf(
      "disconnect-contacts",
      "revoke-google-access",
      "sign-out-wipe",
      "wipe-local-data",
    )
  }
}

internal object PrivacyConsequencePolicy {
  fun keys(action: String): List<String> = when (action) {
    "clear-activity" -> listOf(
      "privacy.consequence.activity-hidden",
      "privacy.consequence.safety-retained",
    )
    "clear-gemini-templates" -> listOf(
      "privacy.consequence.gemini-templates-removed",
      "privacy.consequence.reapproval-required",
    )
    "sign-out-retain" -> listOf(
      "privacy.consequence.automation-paused",
      "privacy.consequence.same-account-setup-retained",
    )
    "disconnect-contacts" -> listOf(
      "privacy.consequence.automation-paused",
      "privacy.consequence.google-working-data-removed",
    )
    "revoke-google-access" -> listOf(
      "privacy.consequence.automation-paused",
      "privacy.consequence.all-google-scopes-revoked",
      "privacy.consequence.google-working-data-removed",
    )
    "delete-account" -> listOf(
      "privacy.consequence.automation-paused",
      "privacy.consequence.remote-deletion-drain-started",
      "privacy.consequence.local-data-erased-after-drain",
    )
    else -> listOf(
      "privacy.consequence.automation-paused",
      "privacy.consequence.local-data-erased",
    )
  }
}
