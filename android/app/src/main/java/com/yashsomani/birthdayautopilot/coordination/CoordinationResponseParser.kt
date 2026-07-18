package com.yashsomani.birthdayautopilot.coordination

internal object CoordinationResponseParser {
  fun registration(value: Any?, request: RegistrationRequest): RegistrationOutcome? = guarded(value) {
    val root = strictObject(value, setOf("kind"), setOf("fence", "installation", "reason"))
      ?: return@guarded null
    when (val kind = root.string("kind")) {
      "SUPPRESSED" -> {
        if (!root.hasExactly("kind", "reason")) return@guarded null
        RegistrationOutcome.Suppressed(
          root.reason("reason", REGISTRATION_REASONS) ?: return@guarded null,
        )
      }
      "REGISTERED_ACTIVE", "REGISTERED_STANDBY", "REPLAYED" -> {
        if (!root.hasExactly("kind", "fence", "installation")) return@guarded null
        val fence = parseFence(root["fence"]) ?: return@guarded null
        val installation = parseInstallation(root["installation"]) ?: return@guarded null
        if (
          installation.installationId != request.installationId ||
          installation.appBuildNumber != request.appBuildNumber ||
          installation.policyVersion != request.policyVersion ||
          installation.distributionChannel != request.distributionChannel
        ) return@guarded null
        if (fence.binding.mode == ServerAccountMode.DELETING) return@guarded null
        val disposition = when (kind) {
          "REGISTERED_ACTIVE" -> RegistrationOutcome.Disposition.REGISTERED_ACTIVE
          "REGISTERED_STANDBY" -> RegistrationOutcome.Disposition.REGISTERED_STANDBY
          else -> RegistrationOutcome.Disposition.REPLAYED
        }
        when (disposition) {
          RegistrationOutcome.Disposition.REGISTERED_ACTIVE,
          RegistrationOutcome.Disposition.REPLAYED,
          -> if (
            installation.state != ServerInstallationState.ACTIVE ||
            installation.epoch != fence.binding.senderEpoch ||
            fence.binding.activeInstallationId != request.installationId
          ) return@guarded null
          RegistrationOutcome.Disposition.REGISTERED_STANDBY -> if (
            installation.state != ServerInstallationState.STANDBY ||
            installation.epoch != 0L ||
            fence.binding.activeInstallationId == request.installationId
          ) return@guarded null
        }
        RegistrationOutcome.Registered(
          disposition,
          fence.binding,
          installation.state,
          installation.epoch,
        )
      }
      else -> null
    }
  }

  fun lease(value: Any?): LeaseOutcome? = guarded(value) {
    val root = strictObject(value, setOf("kind"), setOf("leaseUntilMs", "reason"))
      ?: return@guarded null
    when (root.string("kind")) {
      "RENEWED" -> {
        if (!root.hasExactly("kind", "leaseUntilMs")) return@guarded null
        LeaseOutcome.Renewed(root.time("leaseUntilMs") ?: return@guarded null)
      }
      "REFUSED" -> {
        if (!root.hasExactly("kind", "reason")) return@guarded null
        LeaseOutcome.Refused(root.reason("reason", LEASE_REASONS) ?: return@guarded null)
      }
      else -> null
    }
  }

  fun accountMode(value: Any?, request: AccountModeRequest): AccountModeOutcome? = guarded(value) {
    val root = strictObject(value, setOf("kind"), setOf("mode", "reason"))
      ?: return@guarded null
    when (root.string("kind")) {
      "CHANGED" -> {
        if (!root.hasExactly("kind", "mode")) return@guarded null
        val mode = root.enum<ServerAccountMode>("mode") ?: return@guarded null
        val expected = when (request.action) {
          AccountModeAction.PAUSE_FOR_REPAIR -> ServerAccountMode.PAUSED_REPAIR
          AccountModeAction.ACTIVATE_AUTOMATION -> ServerAccountMode.AUTOMATION_ACTIVE
        }
        mode.takeIf { it == expected }?.let(AccountModeOutcome::Changed)
      }
      "REFUSED" -> {
        if (!root.hasExactly("kind", "reason")) return@guarded null
        val allowed = if (request.action == AccountModeAction.PAUSE_FOR_REPAIR) {
          PAUSE_MODE_REASONS
        } else {
          MODE_REASONS
        }
        AccountModeOutcome.Refused(root.reason("reason", allowed) ?: return@guarded null)
      }
      else -> null
    }
  }

  fun claim(value: Any?, request: ClaimRequest<*>): ClaimOutcome? = guarded(value) {
    val root = strictObject(
      value,
      setOf("kind"),
      setOf("claim", "requestRecord", "occurrenceKeys", "destinationGuards", "reason"),
    ) ?: return@guarded null
    when (root.string("kind")) {
      "REFUSED" -> {
        if (!root.hasExactly("kind", "reason")) return@guarded null
        ClaimOutcome.Refused(root.reason("reason", CLAIM_REASONS) ?: return@guarded null)
      }
      "REPLAYED" -> {
        if (!root.hasExactly("kind", "claim")) return@guarded null
        val claim = parseExpectedClaim(root["claim"], request) ?: return@guarded null
        ClaimOutcome.Accepted(ClaimOutcome.Disposition.REPLAYED, claim.claim)
      }
      "CLAIMED" -> {
        if (!root.hasExactly(
            "kind",
            "claim",
            "requestRecord",
            "occurrenceKeys",
            "destinationGuards",
          )
        ) return@guarded null
        val claim = parseExpectedClaim(root["claim"], request) ?: return@guarded null
        if (claim.claim.state != ServerClaimState.CLAIMED || claim.claim.attempt != 1) {
          return@guarded null
        }
        if (!parseClaimRequestRecord(root["requestRecord"], claim, request)) return@guarded null
        if (!parseOccurrenceKeys(root["occurrenceKeys"], claim, "RESERVED")) return@guarded null
        if (!parseDestinationGuards(root["destinationGuards"], claim, "RESERVED")) {
          return@guarded null
        }
        ClaimOutcome.Accepted(ClaimOutcome.Disposition.CLAIMED, claim.claim)
      }
      else -> null
    }
  }

  fun arm(value: Any?, request: ArmRequest): ArmDecisionOutcome? = guarded(value) {
    val root = armRoot(value) ?: return@guarded null
    when (root.string("kind")) {
      "SUPPRESSED" -> {
        if (!root.hasExactly("kind", "reason")) return@guarded null
        ArmDecisionOutcome.Suppressed(
          root.reason("reason", SUPPRESSION_REASONS) ?: return@guarded null,
        )
      }
      "REPLAYED" -> {
        if (!root.hasExactly("kind", "outcome")) return@guarded null
        ArmDecisionOutcome.Replayed(parseArmOutcome(root["outcome"], request) ?: return@guarded null)
      }
      "NO_WRITE" -> {
        if (!root.keysAre(
            required = setOf("kind", "outcome"),
            optional = setOf("claim", "destinationGuards", "occurrenceKeyState"),
          )
        ) return@guarded null
        val outcome = parseArmOutcome(root["outcome"], request) as? AuthoritativeArmOutcome.NoWrite
          ?: return@guarded null
        if (!parseOptionalNoWriteDetails(root, request, outcome)) return@guarded null
        ArmDecisionOutcome.NoWrite(outcome)
      }
      "ARMED" -> {
        if (!root.hasExactly(
            "kind",
            "outcome",
            "fence",
            "claim",
            "destinationGuards",
            "occurrenceKeyState",
            "budget",
          )
        ) return@guarded null
        val outcome = parseArmOutcome(root["outcome"], request) as? AuthoritativeArmOutcome.Armed
          ?: return@guarded null
        val fence = parseFence(root["fence"]) ?: return@guarded null
        val claim = parseClaim(root["claim"]) ?: return@guarded null
        if (!claim.matches(request) || claim.claim.state != ServerClaimState.ARMED) return@guarded null
        if (claim.claim.serverSubmitNotAfterMillis != outcome.serverSubmitNotAfterMillis) {
          return@guarded null
        }
        if (
          fence.binding.activeInstallationId != request.binding.installationId ||
          fence.binding.senderEpoch != request.binding.senderEpoch ||
          fence.binding.resetGeneration != request.binding.resetGeneration ||
          fence.binding.ownerLeaseUntilMillis <= outcome.resolvedAtMillis ||
          fence.binding.nextArmNotBeforeMillis < outcome.serverSubmitNotAfterMillis ||
          fence.binding.latestIssuedSubmitNotAfterMillis < outcome.serverSubmitNotAfterMillis ||
          !fence.binding.mode.allows(request.purpose) ||
          root.string("occurrenceKeyState") != "ARMED" ||
          !parseDestinationGuards(root["destinationGuards"], claim, "ARMED") ||
          !parseBudget(root["budget"], request, claim.claim.claimId)
        ) return@guarded null
        ArmDecisionOutcome.Armed(outcome)
      }
      else -> null
    }
  }

  fun armStatus(value: Any?, request: ArmRequest): ArmStatusOutcome? = guarded(value) {
    val root = armRoot(value) ?: return@guarded null
    when (root.string("kind")) {
      "UNKNOWN" -> if (root.hasExactly("kind")) ArmStatusOutcome.Unknown else null
      "SUPPRESSED" -> {
        if (!root.hasExactly("kind", "reason")) return@guarded null
        ArmStatusOutcome.Suppressed(
          root.reason("reason", SUPPRESSION_REASONS) ?: return@guarded null,
        )
      }
      "REPLAYED" -> {
        if (!root.hasExactly("kind", "outcome")) return@guarded null
        ArmStatusOutcome.Replayed(parseArmOutcome(root["outcome"], request) ?: return@guarded null)
      }
      "NO_WRITE" -> {
        if (!root.keysAre(
            required = setOf("kind", "outcome"),
            optional = setOf("claim", "destinationGuards", "occurrenceKeyState"),
          )
        ) return@guarded null
        val outcome = parseArmOutcome(root["outcome"], request) as? AuthoritativeArmOutcome.NoWrite
          ?: return@guarded null
        if (
          outcome.reason !in setOf(
            CoordinationServerReason.EXPIRED,
            CoordinationServerReason.EXPIRED_RETRY,
          ) ||
          !parseOptionalNoWriteDetails(root, request, outcome)
        ) return@guarded null
        ArmStatusOutcome.NoWrite(outcome)
      }
      else -> null
    }
  }

  fun retry(value: Any?, request: RetryRequest): RetryOutcome? = guarded(value) {
    val root = strictObject(value, setOf("kind"), setOf("claim", "reason"))
      ?: return@guarded null
    when (root.string("kind")) {
      "REFUSED" -> {
        if (!root.hasExactly("kind", "reason")) return@guarded null
        RetryOutcome.Refused(root.reason("reason", RETRY_REASONS) ?: return@guarded null)
      }
      "AUTHORIZED" -> {
        if (!root.hasExactly("kind", "claim")) return@guarded null
        val claim = parseClaim(root["claim"]) ?: return@guarded null
        if (
          claim.claim.claimId != request.claimId ||
          claim.claim.purpose != CoordinationPurpose.BIRTHDAY ||
          claim.claim.ownerInstallationId != request.binding.installationId ||
          claim.claim.ownerEpoch != request.binding.senderEpoch ||
          claim.claim.resetGeneration != request.binding.resetGeneration ||
          claim.claim.state != ServerClaimState.RETRY_CLAIMED ||
          claim.claim.attempt != 2 ||
          claim.claim.retryRequestId != request.retryRequestId ||
          claim.claim.retryProof != request.proof
        ) return@guarded null
        RetryOutcome.Authorized(claim.claim)
      }
      else -> null
    }
  }

  fun testReport(value: Any?, request: TestReportRequest): TestReportOutcome? = guarded(value) {
    val root = strictObject(value, setOf("kind"), setOf("claim", "outcome", "reason"))
      ?: return@guarded null
    when (root.string("kind")) {
      "SUPPRESSED" -> {
        if (!root.hasExactly("kind", "reason")) return@guarded null
        TestReportOutcome.Suppressed(
          root.reason("reason", SUPPRESSION_REASONS) ?: return@guarded null,
        )
      }
      "REFUSED" -> {
        if (!root.hasExactly("kind", "reason")) return@guarded null
        TestReportOutcome.Refused(
          root.reason("reason", TEST_REPORT_REFUSAL_REASONS) ?: return@guarded null,
        )
      }
      "REPLAYED" -> {
        if (!root.hasExactly("kind", "outcome")) return@guarded null
        TestReportOutcome.Replayed(root.enum<TestBarrierOutcome>("outcome") ?: return@guarded null)
      }
      "RECORDED" -> {
        if (!root.hasExactly("kind", "claim")) return@guarded null
        val claim = parseClaim(root["claim"]) ?: return@guarded null
        if (
          claim.claim.claimId != request.testClaimId ||
          claim.claim.purpose != CoordinationPurpose.TEST ||
          claim.claim.ownerInstallationId != request.binding.installationId ||
          claim.claim.ownerEpoch != request.binding.senderEpoch ||
          claim.claim.resetGeneration != request.binding.resetGeneration ||
          claim.claim.state != ServerClaimState.TERMINAL
        ) return@guarded null
        val outcome = claim.claim.testBarrierOutcome ?: return@guarded null
        val expectedOutcomes = when (request.result) {
          TestReportResult.SENT_ALL_PARTS -> setOf(
            TestBarrierOutcome.SENT_ALL_PARTS_IN_WINDOW,
            TestBarrierOutcome.SENT_EVIDENCE_LATE,
          )
          TestReportResult.FAILED_ZERO_ACCEPTED -> setOf(TestBarrierOutcome.FAILED_ZERO_ACCEPTED)
          TestReportResult.FAILED_OR_UNKNOWN -> setOf(TestBarrierOutcome.FAILED_OR_UNKNOWN)
          TestReportResult.CLEANUP_CANCELLED -> setOf(TestBarrierOutcome.CLEANUP_CANCELLED)
        }
        outcome.takeIf { it in expectedOutcomes }?.let(TestReportOutcome::Recorded)
      }
      else -> null
    }
  }

  fun companionStatus(
    value: Any?,
    request: CompanionStatusRequest,
  ): CompanionStatusOutcome? = guarded(value) {
    val root = strictObject(
      value,
      setOf("composerAllowed", "state", "serverNowMs"),
      setOf("ledgerGeneration"),
    ) ?: return@guarded null
    val state = root.enum<CompanionSafetyState>("state") ?: return@guarded null
    val allowed = root.boolean("composerAllowed") ?: return@guarded null
    val ledger = root.optionalString("ledgerGeneration")
    if (root.containsKey("ledgerGeneration") && ledger == null) return@guarded null
    if (ledger != null && ledger != request.ledgerGeneration) return@guarded null
    if (
      (state == CompanionSafetyState.NO_ANDROID_STATE) != allowed ||
      (state == CompanionSafetyState.NO_ANDROID_STATE && ledger == null) ||
      (state != CompanionSafetyState.SAFETY_STATUS_UNAVAILABLE && ledger == null)
    ) return@guarded null
    CompanionStatusOutcome(
      composerAllowed = allowed,
      state = state,
      serverNowMillis = root.time("serverNowMs") ?: return@guarded null,
      ledgerGeneration = ledger,
    )
  }

  fun senderTransfer(
    value: Any?,
    request: SenderTransferRequest,
    completing: Boolean,
  ): SenderTransferOutcome? = guarded(value) {
    val root = strictObject(
      value,
      required = setOf("kind"),
      optional = setOf("fence", "oldInstallation", "targetInstallation", "reason"),
    ) ?: return@guarded null
    when (root.string("kind")) {
      "REFUSED" -> {
        if (!root.hasExactly("kind", "reason")) return@guarded null
        SenderTransferOutcome.Refused(
          root.reason("reason", TRANSFER_REASONS) ?: return@guarded null,
        )
      }
      "STARTED" -> {
        if (completing || !root.hasExactly("kind", "fence")) return@guarded null
        val binding = parseFence(root["fence"])?.binding ?: return@guarded null
        if (
          binding.mode != ServerAccountMode.TRANSFER_PENDING ||
          binding.activeInstallationId != request.binding.installationId ||
          binding.senderEpoch != request.binding.senderEpoch ||
          binding.resetGeneration != request.binding.resetGeneration ||
          binding.transferTargetInstallationId != request.targetInstallationId ||
          binding.transferDrainUntilMillis == null
        ) return@guarded null
        SenderTransferOutcome.Started(binding)
      }
      "COMPLETED" -> {
        if (!completing || !root.hasExactly(
            "kind",
            "fence",
            "oldInstallation",
            "targetInstallation",
          )
        ) return@guarded null
        val binding = parseFence(root["fence"])?.binding ?: return@guarded null
        val old = parseInstallation(root["oldInstallation"]) ?: return@guarded null
        val target = parseInstallation(root["targetInstallation"]) ?: return@guarded null
        if (
          binding.mode != ServerAccountMode.TEST_ONLY ||
          binding.activeInstallationId != request.targetInstallationId ||
          binding.senderEpoch != request.binding.senderEpoch + 1 ||
          binding.resetGeneration != request.binding.resetGeneration ||
          binding.transferTargetInstallationId != null ||
          binding.transferDrainUntilMillis != null ||
          old.installationId != request.binding.installationId ||
          old.state != ServerInstallationState.REVOKED ||
          old.epoch != request.binding.senderEpoch ||
          target.installationId != request.targetInstallationId ||
          target.state != ServerInstallationState.ACTIVE ||
          target.epoch != binding.senderEpoch
        ) return@guarded null
        SenderTransferOutcome.Completed(
          binding,
          old.installationId,
          target.installationId,
        )
      }
      else -> null
    }
  }

  fun accountDeletion(
    value: Any?,
    request: AccountDeletionRequest,
  ): AccountDeletionAcceptance? = guarded(value) {
    val root = strictObject(value, setOf("kind", "receiptId", "tombstone", "fence"))
      ?: return@guarded null
    val disposition = when (root.string("kind")) {
      "STARTED" -> AccountDeletionAcceptance.Disposition.STARTED
      "REPLAYED" -> AccountDeletionAcceptance.Disposition.REPLAYED
      else -> return@guarded null
    }
    if (root.string("receiptId") != request.requestId) return@guarded null
    val tombstone = strictObject(
      root["tombstone"],
      required = setOf(
        "schemaVersion",
        "requestKey",
        "stage",
        "drainUntilMs",
        "createdAtMs",
        "updatedAtMs",
      ),
      optional = setOf("cleanupAtMs"),
    ) ?: return@guarded null
    if (
      tombstone.long("schemaVersion") != COORDINATION_CONTRACT_VERSION.toLong() ||
      !DeletionReceiptKeyPolicy.matches(request.requestId, tombstone.string("requestKey")) ||
      (tombstone.containsKey("cleanupAtMs") && tombstone.optionalTime("cleanupAtMs") == null)
    ) return@guarded null
    val stage = tombstone.enum<DeletionStage>("stage") ?: return@guarded null
    val drainUntil = tombstone.time("drainUntilMs") ?: return@guarded null
    val createdAt = tombstone.time("createdAtMs") ?: return@guarded null
    val updatedAt = tombstone.time("updatedAtMs") ?: return@guarded null
    val cleanupAt = tombstone.optionalTime("cleanupAtMs")
    if (
      updatedAt < createdAt ||
      drainUntil < createdAt ||
      (cleanupAt != null && cleanupAt < updatedAt)
    ) return@guarded null
    val deletingFence = when (val rawFence = root["fence"]) {
      null -> null
      else -> {
        val fence = strictObject(
          rawFence,
          setOf("mode", "senderEpoch", "resetGeneration", "deletionDrainUntilMs"),
        ) ?: return@guarded null
        val senderEpoch = fence.positiveSafeLong("senderEpoch") ?: return@guarded null
        val resetGeneration = fence.positiveSafeLong("resetGeneration") ?: return@guarded null
        val fenceDrain = fence.time("deletionDrainUntilMs") ?: return@guarded null
        if (fence.string("mode") != "DELETING" || fenceDrain != drainUntil) {
          return@guarded null
        }
        AccountDeletionFence(senderEpoch, resetGeneration, fenceDrain)
      }
    }
    AccountDeletionAcceptance(
      disposition,
      request.requestId,
      stage,
      drainUntil,
      updatedAt,
      deletingFence,
    )
  }

  fun contactDerivedReset(value: Any?): CoordinationOperationOutcome? =
    coordinationOperation(value, CoordinationOperationKind.CONTACT_DERIVED_RESET)

  fun accountDeletionReceipt(value: Any?): AccountDeletionReceiptOutcome? = guarded(value) {
    val root = strictObject(
      value,
      required = setOf("kind"),
      optional = setOf(
        "requestedAtMs",
        "updatedAtMs",
        "completedAtMs",
        "appAccountDeleted",
        "serverDataDeleted",
        "externalCopiesNotDeleted",
      ),
    ) ?: return@guarded null
    when (root.string("kind")) {
      "NOT_FOUND" -> if (root.hasExactly("kind")) {
        AccountDeletionReceiptOutcome.NotFound
      } else {
        null
      }
      "IN_PROGRESS" -> {
        if (!root.hasExactly("kind", "requestedAtMs", "updatedAtMs")) {
          return@guarded null
        }
        val requestedAt = root.time("requestedAtMs") ?: return@guarded null
        val updatedAt = root.time("updatedAtMs") ?: return@guarded null
        if (updatedAt < requestedAt) return@guarded null
        AccountDeletionReceiptOutcome.InProgress(requestedAt, updatedAt)
      }
      "COMPLETED" -> {
        if (!root.hasExactly(
            "kind",
            "requestedAtMs",
            "completedAtMs",
            "appAccountDeleted",
            "serverDataDeleted",
            "externalCopiesNotDeleted",
          ) ||
          root.boolean("appAccountDeleted") != true ||
          root.boolean("serverDataDeleted") != true ||
          root.boolean("externalCopiesNotDeleted") != true
        ) return@guarded null
        val requestedAt = root.time("requestedAtMs") ?: return@guarded null
        val completedAt = root.time("completedAtMs") ?: return@guarded null
        if (completedAt < requestedAt) return@guarded null
        AccountDeletionReceiptOutcome.Completed(requestedAt, completedAt)
      }
      else -> null
    }
  }

  fun senderRelease(value: Any?): CoordinationOperationOutcome? =
    coordinationOperation(value, CoordinationOperationKind.SENDER_RELEASE)

  fun coordinationLifecycleStatus(value: Any?): CoordinationLifecycleStatusOutcome? =
    guarded(value) {
      val root = strictObject(
        value,
        required = setOf("kind"),
        optional = LIFECYCLE_STATUS_FIELDS - "kind",
      ) ?: return@guarded null
      val serverNow = root.time("serverNowMs") ?: return@guarded null
      when (root.string("kind")) {
        "OPERATION_IN_PROGRESS" -> {
          val progress = parseOperationProgress(
            root,
            wireKind = "OPERATION_IN_PROGRESS",
            additionalRequiredFields = setOf("serverNowMs"),
          ) ?: return@guarded null
          CoordinationLifecycleStatusOutcome.OperationInProgress(serverNow, progress)
        }
        "ACCOUNT_DELETION_IN_PROGRESS" -> {
          if (!root.hasExactly("kind", "serverNowMs", "stage", "drainUntilMs")) {
            return@guarded null
          }
          CoordinationLifecycleStatusOutcome.AccountDeletionInProgress(
            serverNowMillis = serverNow,
            stage = root.enum<DeletionStage>("stage") ?: return@guarded null,
            drainUntilMillis = root.time("drainUntilMs") ?: return@guarded null,
          )
        }
        "ANDROID_STATE" -> parseLifecycleAndroidState(root, serverNow)
        "NO_ANDROID_STATE" -> {
          if (!root.keysAre(
              required = setOf("kind", "serverNowMs"),
              optional = setOf("latestCompletion"),
            )
          ) return@guarded null
          val latest = parseOptionalCompletion(root)
          if (root.containsKey("latestCompletion") && latest == null) return@guarded null
          CoordinationLifecycleStatusOutcome.NoAndroidState(serverNow, latest)
        }
        "SAFETY_STATUS_UNAVAILABLE" -> if (
          root.hasExactly("kind", "serverNowMs")
        ) {
          CoordinationLifecycleStatusOutcome.SafetyStatusUnavailable(serverNow)
        } else {
          null
        }
        else -> null
      }
    }

  private fun coordinationOperation(
    value: Any?,
    expectedOperation: CoordinationOperationKind,
  ): CoordinationOperationOutcome? = guarded(value) {
    val root = strictObject(
      value,
      required = setOf("kind"),
      optional = OPERATION_RESPONSE_FIELDS - "kind",
    ) ?: return@guarded null
    when (root.string("kind")) {
      "IN_PROGRESS" -> {
        val progress = parseOperationProgress(root, "IN_PROGRESS") ?: return@guarded null
        if (progress.operation != expectedOperation) return@guarded null
        CoordinationOperationOutcome.InProgress(progress)
      }
      "COMPLETED" -> {
        val completion = parseCompletion(root) ?: return@guarded null
        if (completion.operation != expectedOperation) return@guarded null
        CoordinationOperationOutcome.Completed(completion)
      }
      "REFUSED" -> {
        if (!root.hasExactly("kind", "reason")) return@guarded null
        CoordinationOperationOutcome.Refused(
          root.reason("reason", COORDINATION_OPERATION_REFUSAL_REASONS)
            ?: return@guarded null,
        )
      }
      else -> null
    }
  }

  private fun parseOperationProgress(
    root: Map<String, Any?>,
    wireKind: String,
    additionalRequiredFields: Set<String> = emptySet(),
  ): CoordinationOperationProgress? {
    if (root.string("kind") != wireKind) return null
    val operation = root.enum<CoordinationOperationKind>("operation") ?: return null
    val stage = root.enum<CoordinationOperationStage>("stage") ?: return null
    val androidStateExisted = root.boolean("androidStateExisted") ?: return null
    val conditional = when (operation) {
      CoordinationOperationKind.CONTACT_DERIVED_RESET -> when {
        !androidStateExisted && stage == CoordinationOperationStage.RESET_PURGING -> emptySet()
        androidStateExisted && stage == CoordinationOperationStage.RESET_DRAINING -> setOf(
          "senderEpochAfter",
          "resetGenerationAfter",
          "birthdayAutomationNotBeforeMs",
          "drainUntilMs",
        )
        androidStateExisted && stage == CoordinationOperationStage.RESET_PURGING -> setOf(
          "senderEpochAfter",
          "resetGenerationAfter",
          "birthdayAutomationNotBeforeMs",
        )
        else -> return null
      }
      CoordinationOperationKind.SENDER_RELEASE -> when {
        !androidStateExisted -> return null
        stage == CoordinationOperationStage.RELEASE_DRAINING -> setOf(
          "senderEpochAfter",
          "resetGenerationAfter",
          "drainUntilMs",
        )
        stage == CoordinationOperationStage.RELEASE_PURGING -> setOf(
          "senderEpochAfter",
          "resetGenerationAfter",
        )
        else -> return null
      }
    }
    val required = setOf("kind", "operation", "stage", "androidStateExisted") +
      conditional + additionalRequiredFields
    if (root.keys != required) return null
    val senderEpoch = root.optionalPositiveSafeLong("senderEpochAfter")
    val resetGeneration = root.optionalPositiveSafeLong("resetGenerationAfter")
    val birthdayFence = root.optionalTime("birthdayAutomationNotBeforeMs")
    val drainUntil = root.optionalTime("drainUntilMs")
    if (
      (root.containsKey("senderEpochAfter") && senderEpoch == null) ||
      (root.containsKey("resetGenerationAfter") && resetGeneration == null) ||
      (root.containsKey("birthdayAutomationNotBeforeMs") && birthdayFence == null) ||
      (root.containsKey("drainUntilMs") && drainUntil == null)
    ) return null
    return CoordinationOperationProgress(
      operation = operation,
      stage = stage,
      androidStateExisted = androidStateExisted,
      senderEpochAfter = senderEpoch,
      resetGenerationAfter = resetGeneration,
      birthdayAutomationNotBeforeMillis = birthdayFence,
      drainUntilMillis = drainUntil,
    )
  }

  private fun parseCompletion(value: Any?): CoordinationCompletion? {
    val root = strictObject(
      value,
      required = setOf("kind", "operation", "androidStateExisted"),
      optional = COMPLETION_FIELDS - setOf("kind", "operation", "androidStateExisted"),
    ) ?: return null
    if (root.string("kind") != "COMPLETED") return null
    val operation = root.enum<CoordinationOperationKind>("operation") ?: return null
    val androidStateExisted = root.boolean("androidStateExisted") ?: return null
    val completedAt = root.time("completedAtMs") ?: return null
    if (root.boolean("firebaseAuthPreserved") != true) return null
    return when (operation) {
      CoordinationOperationKind.CONTACT_DERIVED_RESET -> {
        val conditional = if (androidStateExisted) {
          setOf(
            "senderEpochAfter",
            "resetGenerationAfter",
            "birthdayAutomationNotBeforeMs",
          )
        } else {
          emptySet()
        }
        val required = setOf(
          "kind",
          "operation",
          "androidStateExisted",
          "contactDerivedStateErased",
          "firebaseAuthPreserved",
          "completedAtMs",
        ) + conditional
        if (root.keys != required || root.boolean("contactDerivedStateErased") != true) return null
        val senderEpoch = root.optionalPositiveSafeLong("senderEpochAfter")
        val resetGeneration = root.optionalPositiveSafeLong("resetGenerationAfter")
        val birthdayFence = root.optionalTime("birthdayAutomationNotBeforeMs")
        if (
          androidStateExisted &&
          (senderEpoch == null || resetGeneration == null || birthdayFence == null)
        ) return null
        CoordinationCompletion.ContactDerivedReset(
          androidStateExisted = androidStateExisted,
          senderEpochAfter = senderEpoch,
          resetGenerationAfter = resetGeneration,
          birthdayAutomationNotBeforeMillis = birthdayFence,
          completedAtMillis = completedAt,
        )
      }
      CoordinationOperationKind.SENDER_RELEASE -> {
        if (!androidStateExisted || !root.hasExactly(
            "kind",
            "operation",
            "androidStateExisted",
            "senderEpochAfter",
            "resetGenerationAfter",
            "androidSenderStateErased",
            "firebaseAuthPreserved",
            "completedAtMs",
          ) || root.boolean("androidSenderStateErased") != true
        ) return null
        CoordinationCompletion.SenderRelease(
          senderEpochAfter = root.positiveSafeLong("senderEpochAfter") ?: return null,
          resetGenerationAfter = root.positiveSafeLong("resetGenerationAfter") ?: return null,
          completedAtMillis = completedAt,
        )
      }
    }
  }

  private fun parseLifecycleAndroidState(
    root: Map<String, Any?>,
    serverNow: Long,
  ): CoordinationLifecycleStatusOutcome.AndroidState? {
    val mode = root.enum<ServerAccountMode>("mode")
      ?.takeUnless { it == ServerAccountMode.DELETING } ?: return null
    val required = mutableSetOf(
      "kind",
      "serverNowMs",
      "mode",
      "activeInstallationId",
      "senderEpoch",
      "resetGeneration",
      "ownerLeaseUntilMs",
      "latestIssuedSubmitNotAfterMs",
      "birthdayAutomationNotBeforeMs",
    )
    if (mode == ServerAccountMode.TRANSFER_PENDING) {
      required += "transferTargetInstallationId"
      required += "transferDrainUntilMs"
    }
    val optional = if (root.containsKey("latestCompletion")) setOf("latestCompletion") else emptySet()
    if (root.keys != required + optional) return null
    val latest = parseOptionalCompletion(root)
    if (root.containsKey("latestCompletion") && latest == null) return null
    val activeInstallationId = root.string("activeInstallationId")
      ?.takeIf(CoordinationValuePolicy::isInstallationId) ?: return null
    val transferTarget = root.optionalString("transferTargetInstallationId")
      ?.takeIf(CoordinationValuePolicy::isInstallationId)
    val transferDrain = root.optionalTime("transferDrainUntilMs")
    if (
      mode == ServerAccountMode.TRANSFER_PENDING &&
      (transferTarget == null || transferTarget == activeInstallationId || transferDrain == null)
    ) return null
    return CoordinationLifecycleStatusOutcome.AndroidState(
      serverNowMillis = serverNow,
      state = CoordinationAndroidState(
        mode = mode,
        activeInstallationId = activeInstallationId,
        senderEpoch = root.positiveSafeLong("senderEpoch") ?: return null,
        resetGeneration = root.positiveSafeLong("resetGeneration") ?: return null,
        ownerLeaseUntilMillis = root.time("ownerLeaseUntilMs") ?: return null,
        latestIssuedSubmitNotAfterMillis = root.time("latestIssuedSubmitNotAfterMs") ?: return null,
        birthdayAutomationNotBeforeMillis = root.time("birthdayAutomationNotBeforeMs") ?: return null,
        transferTargetInstallationId = transferTarget,
        transferDrainUntilMillis = transferDrain,
      ),
      latestCompletion = latest,
    )
  }

  private fun parseOptionalCompletion(root: Map<String, Any?>): CoordinationCompletion? =
    root["latestCompletion"]?.let(::parseCompletion)

  private fun parseExpectedClaim(value: Any?, request: ClaimRequest<*>): ParsedClaim? {
    val claim = parseClaim(value) ?: return null
    return claim.takeIf {
      it.claimRequestId == request.claimRequestId &&
        it.claim.claimId == request.claimRequestId &&
        it.claim.purpose == request.purpose &&
        it.claim.ownerInstallationId == request.binding.installationId &&
        it.claim.ownerEpoch == request.binding.senderEpoch &&
        it.claim.resetGeneration == request.binding.resetGeneration
    }
  }

  private fun parseClaim(value: Any?): ParsedClaim? {
    val root = strictObject(
      value,
      required = setOf(
        "schemaVersion",
        "claimId",
        "purpose",
        "claimRequestId",
        "ownerInstallationId",
        "ownerEpoch",
        "resetGeneration",
        "state",
        "attempt",
        "retryAuthorizationGeneration",
        "claimExpiresAtMs",
        "maxPossibleSubmitNotAfterMs",
        "occurrenceAliasKeys",
        "destinationAliasKeys",
        "testMaterialAliasKeys",
        "createdAtMs",
        "updatedAtMs",
        "cleanupAtMs",
      ),
      optional = setOf(
        "serverSubmitNotAfterMs",
        "testBarrierOutcome",
        "retryRequestId",
        "retryProof",
      ),
    ) ?: return null
    if (root.long("schemaVersion") != COORDINATION_CONTRACT_VERSION.toLong()) return null
    val claimId = root.safeOpaque("claimId", 1, 128) ?: return null
    val purpose = root.enum<CoordinationPurpose>("purpose") ?: return null
    val claimRequestId = root.safeOpaque("claimRequestId", 1, 128) ?: return null
    val ownerInstallationId = root.string("ownerInstallationId")
      ?.takeIf(CoordinationValuePolicy::isInstallationId) ?: return null
    val state = root.enum<ServerClaimState>("state") ?: return null
    val attempt = root.int("attempt")?.takeIf { it in 1..2 } ?: return null
    val retryGeneration = root.long("retryAuthorizationGeneration")
      ?.takeIf { it in 0..MAX_SAFE_JSON_INTEGER } ?: return null
    val claimExpires = root.time("claimExpiresAtMs") ?: return null
    val maxSubmit = root.time("maxPossibleSubmitNotAfterMs") ?: return null
    val serverSubmit = root.optionalTime("serverSubmitNotAfterMs")
    val testOutcome = root.optionalEnum<TestBarrierOutcome>("testBarrierOutcome")
    val retryRequestId = root.string("retryRequestId")
      ?.takeIf(CoordinationValuePolicy::isUuid)
    val retryProof = root.optionalEnum<RetryProof>("retryProof")
    if (
      (root.containsKey("serverSubmitNotAfterMs") && serverSubmit == null) ||
      (root.containsKey("testBarrierOutcome") && testOutcome == null) ||
      (root.containsKey("retryRequestId") && retryRequestId == null) ||
      (root.containsKey("retryProof") && retryProof !in setOf(
        RetryProof.ALL_PARTS_RADIO_OFF,
        RetryProof.ALL_PARTS_NO_SERVICE,
      )) ||
      maxSubmit < claimExpires ||
      (serverSubmit != null && serverSubmit > maxSubmit) ||
      (purpose == CoordinationPurpose.BIRTHDAY && testOutcome != null) ||
      (state == ServerClaimState.TERMINAL) != (testOutcome != null) ||
      (state == ServerClaimState.TERMINAL && purpose != CoordinationPurpose.TEST) ||
      (attempt == 1 && retryGeneration != 0L) ||
      (attempt == 2 && retryGeneration <= 0L) ||
      (attempt == 1 && (retryRequestId != null || retryProof != null)) ||
      (attempt == 2 && (retryRequestId == null || retryProof == null)) ||
      (state in setOf(
        ServerClaimState.CLAIMED,
        ServerClaimState.EXPIRED_NO_ARM,
      ) && attempt != 1) ||
      (state in setOf(
        ServerClaimState.RETRY_CLAIMED,
        ServerClaimState.RETRY_EXPIRED_NO_ARM,
      ) && attempt != 2) ||
      (state in setOf(
        ServerClaimState.ARMED,
        ServerClaimState.RETRYABLE_ZERO,
        ServerClaimState.RETRY_CLAIMED,
        ServerClaimState.RETRY_EXPIRED_NO_ARM,
        ServerClaimState.TERMINAL,
      ) && serverSubmit == null) ||
      (state in setOf(
        ServerClaimState.CLAIMED,
        ServerClaimState.EXPIRED_NO_ARM,
      ) && serverSubmit != null)
    ) return null
    val occurrenceAliases = parseOpaqueList(root["occurrenceAliasKeys"], 4) ?: return null
    val destinationAliases = parseOpaqueList(root["destinationAliasKeys"], 4) ?: return null
    val testAliases = parseOpaqueList(root["testMaterialAliasKeys"], 2) ?: return null
    if (
      purpose == CoordinationPurpose.BIRTHDAY &&
      (occurrenceAliases.isEmpty() || destinationAliases.isEmpty() || testAliases.isNotEmpty())
    ) return null
    if (
      purpose == CoordinationPurpose.TEST &&
      (occurrenceAliases.isNotEmpty() || destinationAliases.isNotEmpty() || testAliases.isEmpty())
    ) return null
    val ownerEpoch = root.positiveSafeLong("ownerEpoch") ?: return null
    val resetGeneration = root.positiveSafeLong("resetGeneration") ?: return null
    val createdAt = root.time("createdAtMs") ?: return null
    val updatedAt = root.time("updatedAtMs") ?: return null
    val cleanupAt = root.time("cleanupAtMs") ?: return null
    if (updatedAt < createdAt || cleanupAt < updatedAt) return null
    return ParsedClaim(
      claim = ServerClaim(
        claimId,
        purpose,
        ownerInstallationId,
        ownerEpoch,
        resetGeneration,
        state,
        attempt,
        claimExpires,
        maxSubmit,
        serverSubmit,
        testOutcome,
        updatedAt,
        retryRequestId,
        retryProof,
      ),
      claimRequestId = claimRequestId,
      occurrenceAliases = occurrenceAliases,
      destinationAliases = destinationAliases,
      testMaterialAliases = testAliases,
    )
  }

  private fun parseClaimRequestRecord(
    value: Any?,
    claim: ParsedClaim,
    request: ClaimRequest<*>,
  ): Boolean {
    val root = strictObject(
      value,
      setOf("schemaVersion", "requestKey", "purpose", "linkedClaimId", "createdAtMs", "cleanupAtMs"),
    ) ?: return false
    return root.long("schemaVersion") == 1L &&
      root.safeOpaque("requestKey", 1, 128) == request.claimRequestId &&
      root.enum<CoordinationPurpose>("purpose") == request.purpose &&
      root.safeOpaque("linkedClaimId", 1, 128) == claim.claim.claimId &&
      root.time("createdAtMs") != null &&
      root.time("cleanupAtMs") != null
  }

  private fun parseOccurrenceKeys(
    value: Any?,
    claim: ParsedClaim,
    expectedState: String,
  ): Boolean {
    val values = value as? List<*> ?: return false
    if (values.size != claim.occurrenceAliases.size || values.size > 4) return false
    val aliases = values.map { item ->
      val root = strictObject(
        item,
        setOf("schemaVersion", "aliasKey", "linkedClaimId", "state", "createdAtMs", "updatedAtMs", "cleanupAtMs"),
      ) ?: return false
      if (
        root.long("schemaVersion") != 1L ||
        root.safeOpaque("linkedClaimId", 1, 128) != claim.claim.claimId ||
        root.string("state") != expectedState ||
        root.time("createdAtMs") == null ||
        root.time("updatedAtMs") == null ||
        root.time("cleanupAtMs") == null
      ) return false
      root.safeOpaque("aliasKey", 1, 128) ?: return false
    }
    return aliases.toSet() == claim.occurrenceAliases.toSet()
  }

  private fun parseDestinationGuards(
    value: Any?,
    claim: ParsedClaim,
    expectedState: String? = null,
  ): Boolean {
    val values = value as? List<*> ?: return false
    if (values.size != claim.destinationAliases.size || values.size > 4) return false
    val aliases = values.map { item ->
      val root = strictObject(
        item,
        setOf(
          "schemaVersion",
          "aliasKey",
          "linkedClaimId",
          "ownerEpoch",
          "state",
          "createdAtMs",
          "updatedAtMs",
          "cleanupAtMs",
        ),
      ) ?: return false
      if (
        root.long("schemaVersion") != 1L ||
        root.safeOpaque("linkedClaimId", 1, 128) != claim.claim.claimId ||
        root.positiveSafeLong("ownerEpoch") != claim.claim.ownerEpoch ||
        root.string("state") !in setOf("RESERVED", "EXPIRED_NO_ARM_RECLAIMABLE", "ARMED") ||
        (expectedState != null && root.string("state") != expectedState) ||
        root.time("createdAtMs") == null ||
        root.time("updatedAtMs") == null ||
        root.time("cleanupAtMs") == null
      ) return false
      root.safeOpaque("aliasKey", 1, 128) ?: return false
    }
    return aliases.toSet() == claim.destinationAliases.toSet()
  }

  private fun parseFence(value: Any?): ParsedFence? {
    val root = strictObject(
      value,
      setOf(
        "schemaVersion",
        "mode",
        "activeInstallationId",
        "senderEpoch",
        "ownerLeaseUntilMs",
        "nextArmNotBeforeMs",
        "latestIssuedSubmitNotAfterMs",
        "resetGeneration",
        "birthdayAutomationNotBeforeMs",
        "createdAtMs",
        "updatedAtMs",
      ),
      setOf("transferTargetInstallationId", "transferDrainUntilMs", "deletionDrainUntilMs"),
    ) ?: return null
    if (root.long("schemaVersion") != 1L) return null
    val transferTarget = root.optionalString("transferTargetInstallationId")
    if (
      root.containsKey("transferTargetInstallationId") &&
      transferTarget?.takeIf(CoordinationValuePolicy::isInstallationId) == null
    ) return null
    for (field in listOf("transferDrainUntilMs", "deletionDrainUntilMs")) {
      if (root.containsKey(field) && root.optionalTime(field) == null) return null
    }
    val created = root.time("createdAtMs") ?: return null
    val updated = root.time("updatedAtMs") ?: return null
    if (updated < created) return null
    val mode = root.enum<ServerAccountMode>("mode") ?: return null
    val transferDrain = root.optionalTime("transferDrainUntilMs")
    val deletionDrain = root.optionalTime("deletionDrainUntilMs")
    if (
      (mode == ServerAccountMode.TRANSFER_PENDING &&
        (transferTarget == null || transferDrain == null)) ||
      (mode !in setOf(ServerAccountMode.TRANSFER_PENDING, ServerAccountMode.DELETING) &&
        (transferTarget != null || transferDrain != null)) ||
      (mode == ServerAccountMode.DELETING && deletionDrain == null) ||
      (mode != ServerAccountMode.DELETING && deletionDrain != null)
    ) return null
    return ParsedFence(
      ServerBinding(
        activeInstallationId = root.string("activeInstallationId")
          ?.takeIf(CoordinationValuePolicy::isInstallationId) ?: return null,
        senderEpoch = root.positiveSafeLong("senderEpoch") ?: return null,
        resetGeneration = root.positiveSafeLong("resetGeneration") ?: return null,
        mode = mode,
        ownerLeaseUntilMillis = root.time("ownerLeaseUntilMs") ?: return null,
        nextArmNotBeforeMillis = root.time("nextArmNotBeforeMs") ?: return null,
        latestIssuedSubmitNotAfterMillis = root.time("latestIssuedSubmitNotAfterMs") ?: return null,
        birthdayAutomationNotBeforeMillis = root.time("birthdayAutomationNotBeforeMs") ?: return null,
        serverObservedAtMillis = updated,
        transferTargetInstallationId = transferTarget,
        transferDrainUntilMillis = transferDrain,
        deletionDrainUntilMillis = deletionDrain,
      ),
    )
  }

  private fun parseInstallation(value: Any?): ParsedInstallation? {
    val root = strictObject(
      value,
      setOf(
        "schemaVersion",
        "installationId",
        "state",
        "epoch",
        "appBuildNumber",
        "policyVersion",
        "distributionChannel",
        "lastSeenAtMs",
      ),
      setOf("cleanupAtMs"),
    ) ?: return null
    if (
      root.long("schemaVersion") != 1L ||
      root.time("lastSeenAtMs") == null ||
      (root.containsKey("cleanupAtMs") && root.optionalTime("cleanupAtMs") == null)
    ) return null
    val state = root.enum<ServerInstallationState>("state") ?: return null
    val epoch = root.long("epoch")?.takeIf { it in 0..MAX_SAFE_JSON_INTEGER } ?: return null
    if (
      (state == ServerInstallationState.ACTIVE && epoch <= 0) ||
      (state == ServerInstallationState.STANDBY && epoch != 0L) ||
      (state == ServerInstallationState.REVOKED && epoch <= 0)
    ) return null
    return ParsedInstallation(
      installationId = root.string("installationId")
        ?.takeIf(CoordinationValuePolicy::isInstallationId) ?: return null,
      state = state,
      epoch = epoch,
      appBuildNumber = root.int("appBuildNumber")?.takeIf { it > 0 } ?: return null,
      policyVersion = root.int("policyVersion")?.takeIf { it > 0 } ?: return null,
      distributionChannel = root.enum<DistributionChannel>("distributionChannel") ?: return null,
    )
  }

  private fun parseArmOutcome(value: Any?, request: ArmRequest): AuthoritativeArmOutcome? {
    val raw = value as? Map<*, *> ?: return null
    val kind = raw["kind"] as? String ?: return null
    val root = when (kind) {
      "ARMED" -> strictObject(
        value,
        setOf(
          "schemaVersion",
          "armRequestId",
          "purpose",
          "claimId",
          "ownerInstallationId",
          "ownerEpoch",
          "resetGeneration",
          "attempt",
          "kind",
          "serverSubmitNotAfterMs",
          "resolvedAtMs",
          "cleanupAtMs",
        ),
      )
      "NO_WRITE" -> strictObject(
        value,
        setOf(
          "schemaVersion",
          "armRequestId",
          "purpose",
          "claimId",
          "ownerInstallationId",
          "ownerEpoch",
          "resetGeneration",
          "attempt",
          "kind",
          "reason",
          "resolvedAtMs",
          "cleanupAtMs",
        ),
      )
      else -> null
    } ?: return null
    if (
      root.long("schemaVersion") != 1L ||
      root.string("armRequestId") != request.armRequestId ||
      root.enum<CoordinationPurpose>("purpose") != request.purpose ||
      root.safeOpaque("claimId", 1, 128) != request.claimId ||
      root.string("ownerInstallationId") != request.binding.installationId ||
      root.positiveSafeLong("ownerEpoch") != request.binding.senderEpoch ||
      root.positiveSafeLong("resetGeneration") != request.binding.resetGeneration ||
      root.int("attempt") != request.attempt
    ) return null
    val cleanupAt = root.time("cleanupAtMs") ?: return null
    val resolvedAt = root.time("resolvedAtMs") ?: return null
    if (cleanupAt < resolvedAt) return null
    return if (kind == "ARMED") {
      val deadline = root.time("serverSubmitNotAfterMs") ?: return null
      if (deadline < resolvedAt) return null
      AuthoritativeArmOutcome.Armed(
        request.armRequestId,
        request.purpose,
        request.claimId,
        request.binding.installationId,
        request.binding.senderEpoch,
        request.binding.resetGeneration,
        request.attempt,
        deadline,
        resolvedAt,
      )
    } else {
      AuthoritativeArmOutcome.NoWrite(
        request.armRequestId,
        request.purpose,
        request.claimId,
        request.binding.installationId,
        request.binding.senderEpoch,
        request.binding.resetGeneration,
        request.attempt,
        root.reason("reason", NO_WRITE_REASONS) ?: return null,
        resolvedAt,
      )
    }
  }

  private fun parseOptionalNoWriteDetails(
    root: Map<String, Any?>,
    request: ArmRequest,
    outcome: AuthoritativeArmOutcome.NoWrite,
  ): Boolean {
    val isExpired = outcome.reason == CoordinationServerReason.EXPIRED
    val isExpiredRetry = outcome.reason == CoordinationServerReason.EXPIRED_RETRY
    if (!isExpired && !isExpiredRetry) {
      return root.hasExactly("kind", "outcome")
    }
    if (!root.containsKey("claim") || !root.containsKey("destinationGuards")) return false
    val parsedClaim = parseClaim(root["claim"])?.takeIf { it.matches(request) } ?: return false
    val expectedClaimState = if (isExpired) {
      ServerClaimState.EXPIRED_NO_ARM
    } else {
      ServerClaimState.RETRY_EXPIRED_NO_ARM
    }
    if (parsedClaim.claim.state != expectedClaimState) return false
    val expectedGuardState = if (isExpired) "EXPIRED_NO_ARM_RECLAIMABLE" else "ARMED"
    if (!parseDestinationGuards(root["destinationGuards"], parsedClaim, expectedGuardState)) {
      return false
    }
    return if (isExpired) {
      root.string("occurrenceKeyState") == "EXPIRED_NO_ARM_RECLAIMABLE"
    } else {
      !root.containsKey("occurrenceKeyState")
    }
  }

  private fun parseBudget(value: Any?, request: ArmRequest, claimId: String): Boolean {
    val root = strictObject(
      value,
      setOf("schemaVersion", "purpose", "entries", "newestEntryAtMs", "cleanupAtMs"),
    ) ?: return false
    if (
      root.long("schemaVersion") != 1L ||
      root.enum<CoordinationPurpose>("purpose") != request.purpose ||
      root.time("newestEntryAtMs") == null ||
      root.time("cleanupAtMs") == null
    ) return false
    val entries = root["entries"] as? List<*> ?: return false
    if (entries.size !in 1..20) return false
    var containsClaim = false
    val ids = mutableSetOf<String>()
    for (valueEntry in entries) {
      val entry = strictObject(valueEntry, setOf("id", "armedAtMs")) ?: return false
      val id = entry.safeOpaque("id", 1, 128) ?: return false
      if (!ids.add(id) || entry.time("armedAtMs") == null) return false
      if (id == claimId) containsClaim = true
    }
    val newest = root.time("newestEntryAtMs") ?: return false
    val cleanup = root.time("cleanupAtMs") ?: return false
    val latestEntry = entries.maxOf { entry ->
      strictObject(entry, setOf("id", "armedAtMs"))!!.time("armedAtMs")!!
    }
    return containsClaim && newest == latestEntry && cleanup >= newest
  }

  private fun ParsedClaim.matches(request: ArmRequest): Boolean =
    claim.claimId == request.claimId &&
      claim.purpose == request.purpose &&
      claim.ownerInstallationId == request.binding.installationId &&
      claim.ownerEpoch == request.binding.senderEpoch &&
      claim.resetGeneration == request.binding.resetGeneration &&
      claim.attempt == request.attempt

  private fun armRoot(value: Any?): Map<String, Any?>? = strictObject(
    value,
    setOf("kind"),
    setOf(
      "outcome",
      "reason",
      "fence",
      "claim",
      "destinationGuards",
      "occurrenceKeyState",
      "budget",
    ),
  )

  private inline fun <T> guarded(value: Any?, block: () -> T?): T? = try {
    if (!RawResponseBounds.accept(value)) null else block()
  } catch (_: RuntimeException) {
    null
  }

  private fun strictObject(
    value: Any?,
    required: Set<String>,
    optional: Set<String> = emptySet(),
  ): Map<String, Any?>? {
    val raw = value as? Map<*, *> ?: return null
    val result = LinkedHashMap<String, Any?>(raw.size)
    for ((key, item) in raw) {
      if (key !is String) return null
      result[key] = item
    }
    return result.takeIf { it.keysAre(required, optional) }
  }

  private fun Map<String, Any?>.keysAre(required: Set<String>, optional: Set<String>): Boolean =
    keys.containsAll(required) && keys.all { it in required || it in optional }

  private fun Map<String, Any?>.hasExactly(vararg fields: String): Boolean =
    keys == fields.toSet()

  private fun Map<String, Any?>.string(field: String): String? = this[field] as? String

  private fun Map<String, Any?>.optionalString(field: String): String? = this[field] as? String

  private fun Map<String, Any?>.safeOpaque(field: String, minimum: Int, maximum: Int): String? =
    string(field)?.takeIf { it.length in minimum..maximum && SafeWireText.accept(it) }

  private fun Map<String, Any?>.boolean(field: String): Boolean? = this[field] as? Boolean

  private fun Map<String, Any?>.long(field: String): Long? = integralLong(this[field])

  private fun Map<String, Any?>.int(field: String): Int? =
    integralLong(this[field])?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

  private fun Map<String, Any?>.time(field: String): Long? =
    integralLong(this[field])?.takeIf { it in 0..MAX_SAFE_JSON_INTEGER }

  private fun Map<String, Any?>.optionalTime(field: String): Long? = time(field)

  private fun Map<String, Any?>.positiveSafeLong(field: String): Long? =
    integralLong(this[field])?.takeIf { it in 1..MAX_SAFE_JSON_INTEGER }

  private fun Map<String, Any?>.optionalPositiveSafeLong(field: String): Long? =
    positiveSafeLong(field)

  private inline fun <reified T : Enum<T>> Map<String, Any?>.enum(field: String): T? =
    string(field)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

  private inline fun <reified T : Enum<T>> Map<String, Any?>.optionalEnum(field: String): T? =
    enum<T>(field)

  private fun Map<String, Any?>.reason(
    field: String,
    allowed: Set<CoordinationServerReason>,
  ): CoordinationServerReason? = enum<CoordinationServerReason>(field)?.takeIf { it in allowed }

  private fun integralLong(value: Any?): Long? = when (value) {
    is Byte -> value.toLong()
    is Short -> value.toLong()
    is Int -> value.toLong()
    is Long -> value
    is Float -> value.takeIf { it.isFinite() && it % 1f == 0f }?.toLong()
    is Double -> value.takeIf {
      it.isFinite() && it % 1.0 == 0.0 && it >= Long.MIN_VALUE.toDouble() && it <= Long.MAX_VALUE.toDouble()
    }?.toLong()
    else -> null
  }

  private fun parseOpaqueList(value: Any?, maximum: Int): List<String>? {
    val raw = value as? List<*> ?: return null
    if (raw.size > maximum) return null
    val strings = raw.map { item ->
      (item as? String)?.takeIf { it.length in 1..128 && SafeWireText.accept(it) } ?: return null
    }
    return strings.takeIf { it.toSet().size == it.size }
  }

  private data class ParsedFence(val binding: ServerBinding)

  private data class ParsedInstallation(
    val installationId: String,
    val state: ServerInstallationState,
    val epoch: Long,
    val appBuildNumber: Int,
    val policyVersion: Int,
    val distributionChannel: DistributionChannel,
  )

  private data class ParsedClaim(
    val claim: ServerClaim,
    val claimRequestId: String,
    val occurrenceAliases: List<String>,
    val destinationAliases: List<String>,
    val testMaterialAliases: List<String>,
  )

  private fun ServerAccountMode.allows(purpose: CoordinationPurpose): Boolean = when (purpose) {
    CoordinationPurpose.BIRTHDAY -> this == ServerAccountMode.AUTOMATION_ACTIVE
    CoordinationPurpose.TEST -> this == ServerAccountMode.TEST_ONLY || this == ServerAccountMode.PAUSED_REPAIR
  }

  private val NO_WRITE_REASONS = setOf(
    CoordinationServerReason.EXPIRED,
    CoordinationServerReason.EXPIRED_RETRY,
    CoordinationServerReason.GLOBAL_ARMING_DISABLED,
    CoordinationServerReason.CONTINUITY_UNAVAILABLE,
    CoordinationServerReason.LEDGER_GENERATION_MISMATCH,
    CoordinationServerReason.BUILD_UNSUPPORTED,
    CoordinationServerReason.POLICY_UNSUPPORTED,
    CoordinationServerReason.CHANNEL_UNSUPPORTED,
    CoordinationServerReason.MODE_BLOCKED,
    CoordinationServerReason.LEASE_EXPIRED,
    CoordinationServerReason.INSTALLATION_MISMATCH,
    CoordinationServerReason.EPOCH_MISMATCH,
    CoordinationServerReason.RESET_GENERATION_MISMATCH,
    CoordinationServerReason.TOO_EARLY,
    CoordinationServerReason.BIRTHDAY_RESET_FENCE,
    CoordinationServerReason.BUDGET_EXCEEDED,
    CoordinationServerReason.CLAIM_STATE_MISMATCH,
  )
  private val SUPPRESSION_REASONS = setOf(
    CoordinationServerReason.DELETION_SUPPRESSED,
    CoordinationServerReason.RESET_SUPPRESSED,
    CoordinationServerReason.MISSING_FENCE,
    CoordinationServerReason.MISSING_CLAIM,
    CoordinationServerReason.UNKNOWN_HISTORY,
    CoordinationServerReason.IOS_COMPOSER_RESERVED,
  )
  private val REGISTRATION_REASONS = setOf(
    CoordinationServerReason.DELETION_SUPPRESSED,
    CoordinationServerReason.CONTINUITY_UNAVAILABLE,
    CoordinationServerReason.LEDGER_GENERATION_MISMATCH,
    CoordinationServerReason.BUILD_UNSUPPORTED,
    CoordinationServerReason.POLICY_UNSUPPORTED,
    CoordinationServerReason.CHANNEL_UNSUPPORTED,
    CoordinationServerReason.IOS_COMPOSER_RESERVED,
  )
  private val LEASE_REASONS = setOf(
    CoordinationServerReason.DELETION_SUPPRESSED,
    CoordinationServerReason.MISSING_FENCE,
    CoordinationServerReason.CONTINUITY_UNAVAILABLE,
    CoordinationServerReason.LEDGER_GENERATION_MISMATCH,
    CoordinationServerReason.BUILD_UNSUPPORTED,
    CoordinationServerReason.POLICY_UNSUPPORTED,
    CoordinationServerReason.CHANNEL_UNSUPPORTED,
    CoordinationServerReason.BINDING_MISMATCH,
    CoordinationServerReason.MODE_BLOCKED,
    CoordinationServerReason.IOS_COMPOSER_RESERVED,
  )
  private val MODE_REASONS = setOf(
    CoordinationServerReason.DELETION_SUPPRESSED,
    CoordinationServerReason.BINDING_MISMATCH,
    CoordinationServerReason.CONTINUITY_UNAVAILABLE,
    CoordinationServerReason.LEDGER_GENERATION_MISMATCH,
    CoordinationServerReason.GLOBAL_ARMING_DISABLED,
    CoordinationServerReason.BUILD_UNSUPPORTED,
    CoordinationServerReason.POLICY_UNSUPPORTED,
    CoordinationServerReason.CHANNEL_UNSUPPORTED,
    CoordinationServerReason.TEST_LEASE_OR_MODE_INVALID,
    CoordinationServerReason.BOUND_TEST_RECEIPT_REQUIRED,
    CoordinationServerReason.IOS_COMPOSER_RESERVED,
  )
  private val PAUSE_MODE_REASONS = setOf(
    CoordinationServerReason.DELETION_SUPPRESSED,
    CoordinationServerReason.BINDING_MISMATCH,
    CoordinationServerReason.IOS_COMPOSER_RESERVED,
  )
  private val CLAIM_REASONS = NO_WRITE_REASONS + SUPPRESSION_REASONS + setOf(
    CoordinationServerReason.OCCURRENCE_RESERVED,
    CoordinationServerReason.DESTINATION_RESERVED,
    CoordinationServerReason.TEST_MATERIAL_MISMATCH,
    CoordinationServerReason.REQUEST_RECORD_CORRUPT,
  )
  private val RETRY_REASONS = setOf(
    CoordinationServerReason.DELETION_SUPPRESSED,
    CoordinationServerReason.MISSING_FENCE,
    CoordinationServerReason.MISSING_CLAIM,
    CoordinationServerReason.RESET_SUPPRESSED,
    CoordinationServerReason.UNKNOWN_HISTORY,
    CoordinationServerReason.NOT_ARMED_ATTEMPT_ONE,
    CoordinationServerReason.UNSUPPORTED_ZERO_ACCEPTANCE_PROOF,
    CoordinationServerReason.RETRY_REQUEST_MISMATCH,
    CoordinationServerReason.IOS_COMPOSER_RESERVED,
  )
  private val TEST_REPORT_REFUSAL_REASONS = setOf(
    CoordinationServerReason.BINDING_MISMATCH,
    CoordinationServerReason.MODE_BLOCKED,
    CoordinationServerReason.ARMED_OUTCOME_REQUIRED,
  )
  private val TRANSFER_REASONS = setOf(
    CoordinationServerReason.DELETION_SUPPRESSED,
    CoordinationServerReason.MISSING_FENCE,
    CoordinationServerReason.RESET_SUPPRESSED,
    CoordinationServerReason.TARGET_NOT_STANDBY,
    CoordinationServerReason.WRONG_MODE,
    CoordinationServerReason.DRAIN_NOT_COMPLETE,
    CoordinationServerReason.IOS_COMPOSER_RESERVED,
  )
  private val COORDINATION_OPERATION_REFUSAL_REASONS = setOf(
    CoordinationServerReason.DELETION_SUPPRESSED,
    CoordinationServerReason.COORDINATION_OPERATION_IN_PROGRESS,
    CoordinationServerReason.REQUEST_MISMATCH,
    CoordinationServerReason.RESET_SUPPRESSED,
    CoordinationServerReason.CONTINUITY_UNAVAILABLE,
    CoordinationServerReason.GENERATION_EXHAUSTED,
    CoordinationServerReason.IOS_COMPOSER_RESERVED,
  )
  private val OPERATION_RESPONSE_FIELDS = setOf(
    "kind",
    "operation",
    "stage",
    "androidStateExisted",
    "senderEpochAfter",
    "resetGenerationAfter",
    "birthdayAutomationNotBeforeMs",
    "drainUntilMs",
    "contactDerivedStateErased",
    "androidSenderStateErased",
    "firebaseAuthPreserved",
    "completedAtMs",
    "reason",
  )
  private val COMPLETION_FIELDS = setOf(
    "kind",
    "operation",
    "androidStateExisted",
    "senderEpochAfter",
    "resetGenerationAfter",
    "birthdayAutomationNotBeforeMs",
    "contactDerivedStateErased",
    "androidSenderStateErased",
    "firebaseAuthPreserved",
    "completedAtMs",
  )
  private val LIFECYCLE_STATUS_FIELDS = setOf(
    "kind",
    "serverNowMs",
    "operation",
    "stage",
    "androidStateExisted",
    "senderEpochAfter",
    "resetGenerationAfter",
    "birthdayAutomationNotBeforeMs",
    "drainUntilMs",
    "mode",
    "activeInstallationId",
    "senderEpoch",
    "resetGeneration",
    "ownerLeaseUntilMs",
    "latestIssuedSubmitNotAfterMs",
    "transferTargetInstallationId",
    "transferDrainUntilMs",
    "latestCompletion",
  )
}

private object RawResponseBounds {
  private const val MAX_DEPTH = 10
  private const val MAX_NODES = 512
  private const val MAX_MAP_ENTRIES = 64
  private const val MAX_LIST_ENTRIES = 64
  private const val MAX_STRING_CHARS = 512
  private const val MAX_TOTAL_STRING_CHARS = 16_384
  private val keyPattern = Regex("^[A-Za-z][A-Za-z0-9]{0,63}$")

  fun accept(root: Any?): Boolean {
    var nodes = 0
    var stringChars = 0
    val pending = ArrayDeque<Pair<Any?, Int>>()
    pending.add(root to 0)
    while (pending.isNotEmpty()) {
      val (value, depth) = pending.removeLast()
      nodes += 1
      if (nodes > MAX_NODES || depth > MAX_DEPTH) return false
      when (value) {
        is Map<*, *> -> {
          if (value.size > MAX_MAP_ENTRIES) return false
          for ((key, item) in value) {
            if (key !is String || !keyPattern.matches(key)) return false
            stringChars += key.length
            pending.add(item to depth + 1)
          }
        }
        is List<*> -> {
          if (value.size > MAX_LIST_ENTRIES) return false
          value.forEach { pending.add(it to depth + 1) }
        }
        is String -> {
          if (value.length > MAX_STRING_CHARS || !SafeWireText.accept(value)) return false
          stringChars += value.length
        }
        null, is Boolean, is Byte, is Short, is Int, is Long -> Unit
        is Float -> if (!value.isFinite()) return false
        is Double -> if (!value.isFinite()) return false
        else -> return false
      }
      if (stringChars > MAX_TOTAL_STRING_CHARS) return false
    }
    return true
  }
}

private object SafeWireText {
  fun accept(value: String): Boolean {
    var index = 0
    while (index < value.length) {
      val char = value[index]
      if (char.isISOControl() || Character.getType(char) == Character.FORMAT.toInt()) return false
      when {
        char.isHighSurrogate() -> {
          if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) return false
          index += 2
        }
        char.isLowSurrogate() -> return false
        else -> index += 1
      }
    }
    return true
  }
}
