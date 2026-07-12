package com.yashsomani.birthdayautopilot.configuration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashsomani.birthdayautopilot.messages.NativeSmsPlan
import com.yashsomani.birthdayautopilot.messages.NativeSmsPlanResult
import com.yashsomani.birthdayautopilot.messages.SmsEncodingEstimator
import com.yashsomani.birthdayautopilot.coordination.DistributionChannel
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.gemini.AndroidGeminiSuggestionGateway
import com.yashsomani.birthdayautopilot.gemini.GeminiNativeClient
import com.yashsomani.birthdayautopilot.gemini.GeminiRateState
import com.yashsomani.birthdayautopilot.gemini.GeminiRateStore
import com.yashsomani.birthdayautopilot.gemini.GeminiSuggestionPolicy
import com.yashsomani.birthdayautopilot.gemini.GeminiUxRateGuard
import com.yashsomani.birthdayautopilot.lifecycle.AndroidLifecycleController
import com.yashsomani.birthdayautopilot.lifecycle.DurablePrivacyOperation
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleStateStore
import com.yashsomani.birthdayautopilot.lifecycle.PrivacyActionPlan
import com.yashsomani.birthdayautopilot.lifecycle.PrivacyConfirmationOutcome
import com.yashsomani.birthdayautopilot.people.PeopleBirthday
import com.yashsomani.birthdayautopilot.people.PeopleContactDelta
import com.yashsomani.birthdayautopilot.people.PeopleName
import com.yashsomani.birthdayautopilot.people.PeoplePhone
import com.yashsomani.birthdayautopilot.people.PeopleRequestFactory
import com.yashsomani.birthdayautopilot.people.PeopleSyncCompletion
import com.yashsomani.birthdayautopilot.people.PeopleSyncMode
import com.yashsomani.birthdayautopilot.people.PeopleWallClock
import com.yashsomani.birthdayautopilot.people.RoomPeopleSyncStagingStore
import com.yashsomani.birthdayautopilot.planning.RecurrencePlanner
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import com.yashsomani.birthdayautopilot.storage.database.ApprovalRecordState
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.CoordinationStateEntity
import com.yashsomani.birthdayautopilot.storage.database.IdentityAttachDecision
import com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationRecordState
import com.yashsomani.birthdayautopilot.storage.database.RecipientEnrollmentState
import com.yashsomani.birthdayautopilot.storage.database.TemplateSource
import com.yashsomani.birthdayautopilot.storage.database.SyncFreshness
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidConfigurationControllerInstrumentationTest {
  private lateinit var database: BirthdayDatabase
  private lateinit var controller: AndroidConfigurationController
  private lateinit var context: Context
  private var sessionMatches = true
  private var now = 1_800_000_000_000L
  private val fingerprint = PeopleRequestFactory(1_000).parameterFingerprint

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    lifecycleFiles().forEach(java.io.File::delete)
    database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    database.birthdayDao().initializeIfAbsent("callback-generation")
    assertEquals(
      IdentityAttachDecision.ATTACHED,
      database.peopleSyncDao().attachIdentity(
        AccountRecordEntity(
          accountId = ACCOUNT_ID,
          activeSlot = 1,
          googleSubjectHash = "2".repeat(64),
          firebaseUid = "firebase-uid",
          displayEmail = "person@example.test",
          localeTag = "en-IN",
          state = AccountRecordState.ACTIVE,
          revision = 0,
          createdAtMillis = now,
          updatedAtMillis = now,
        ),
        fingerprint,
      ),
    )
    database.safetyLedgerDao().insertInstallation(
      InstallationBindingEntity(
        installationId = INSTALLATION_ID,
        accountId = ACCOUNT_ID,
        localSlot = 1,
        callbackGeneration = "callback-generation",
        state = InstallationRecordState.ACTIVE,
        accountMode = AccountMode.PAUSED_REPAIR,
        senderEpoch = 1,
        resetGeneration = 1,
        ownerLeaseUntilMillis = now + 60_000,
        appVersionCode = TEST_VERSION_CODE,
        distributionChannel = DistributionChannel.RESTRICTED_LAB.name,
        signingCertificateSha256 = TEST_SIGNING_CERTIFICATE,
        lastVerifiedServerMillis = now,
        revision = 0,
        createdAtMillis = now,
        updatedAtMillis = now,
      ),
    )
    database.safetyLedgerDao().putCoordinationState(
      CoordinationStateEntity(
        accountId = ACCOUNT_ID,
        mode = AccountMode.PAUSED_REPAIR,
        activeInstallationId = INSTALLATION_ID,
        senderEpoch = 1,
        resetGeneration = 1,
        continuityGeneration = 1,
        ownerLeaseUntilMillis = now + 60_000,
        nextArmNotBeforeMillis = null,
        latestIssuedSubmitNotAfterMillis = null,
        birthdayAutomationNotBeforeMillis = null,
        transferDrainUntilMillis = null,
        deletionDrainUntilMillis = null,
        lastSuccessfulCoordinationMillis = now,
        lastSafeCode = null,
        revision = 0,
        updatedAtMillis = now,
      ),
    )
    controller = newController()
  }

  private fun newController(
    geminiGateway: AndroidGeminiSuggestionGateway? = null,
  ) = AndroidConfigurationController(
      context = context,
      database = database,
      recurrencePlanner = RecurrencePlanner(),
      clock = ConfigurationClock { now++ },
      zoneProvider = ConfigurationZoneProvider { ZoneId.of("Asia/Kolkata") },
      subscriptionResolver = ConfigurationSubscriptionResolver {
        SubscriptionResolution.Ready(1, "Default SIM · slot 1")
      },
      smsPlanSource = { exactText, _ ->
        val estimate = SmsEncodingEstimator.estimate(exactText)
        NativeSmsPlanResult.Planned(
          NativeSmsPlan(
            encoding = estimate.encoding,
            characterCount = estimate.characterCount,
            encodingUnitCount = estimate.encodingUnitCount,
            orderedParts = listOf(exactText),
          ),
        )
      },
      buildSignalSource = ConfigurationBuildSignalSource {
        ConfigurationBuildSignals(
          versionCode = TEST_VERSION_CODE,
          distributionChannel = DistributionChannel.RESTRICTED_LAB,
          signingCertificateSha256 = TEST_SIGNING_CERTIFICATE,
        )
      },
      geminiGateway = geminiGateway,
      accountSessionMatches = { sessionMatches },
    )

  @After
  fun tearDown() {
    database.close()
    lifecycleFiles().forEach(java.io.File::delete)
  }

  @Test
  fun enrollmentMessagePolicyAndImmutableApprovalRequireFreshSingleUseReviews() = runBlocking {
    importContact(singleContact())
    val contactId = activeContactId()
    val initialDetail = requireNotNull(controller.contactDetail(contactId))
    assertEquals("ready", initialDetail.getJSONObject("summary").getJSONObject("readiness").getString("kind"))

    val enrollmentRevision = revision()
    val enrollmentReview = controller.prepareEnrollment(
      JSONObject()
        .put("contactIds", JSONArray(listOf(contactId)))
        .put("expectedRevision", enrollmentRevision.toString()),
      enrollmentRevision,
    ).successPayload()
    val enrollmentHandle = enrollmentReview.getString("handle")
    controller.confirmEnrollment(handleRequest(enrollmentHandle, enrollmentRevision), enrollmentRevision)
      .requireSuccess()
    assertEquals(
      RecipientEnrollmentState.NEEDS_REVIEW,
      database.configurationDao().recipientPolicy(contactId)?.state,
    )
    assertTrue(
      controller.confirmEnrollment(handleRequest(enrollmentHandle, enrollmentRevision), enrollmentRevision) is
        ConfigurationOutcome.Problem,
    )

    val messageRevision = revision()
    val draft = JSONObject()
      .put("language", "en")
      .put("tone", "warm")
      .put("placeholderMode", JSONObject().put("kind", "given-name").put("requiredCount", 1))
      .put("text", "Happy birthday, {firstName}! Wishing you a wonderful day.")
      .put("requestedSegmentCap", 2)
    val messagePreview = controller.previewMessage(
      JSONObject().put("draft", draft).put("expectedRevision", messageRevision.toString()),
      messageRevision,
    ).successPayload()
    val messageHandle = messagePreview.getString("handle")
    controller.saveMessage(handleRequest(messageHandle, messageRevision), messageRevision).requireSuccess()

    val policyRevision = revision()
    val policyPreview = controller.previewPolicy(
      JSONObject()
        .put(
          "draft",
          JSONObject()
            .put("primaryStart", "09:00")
            .put("primaryEnd", "11:00")
            .put("latePolicy", JSONObject().put("kind", "none"))
            .put("dailyCap", 10),
        )
        .put("expectedRevision", policyRevision.toString()),
      policyRevision,
    ).successPayload()
    val policyHandle = policyPreview.getString("handle")
    controller.savePolicy(handleRequest(policyHandle, policyRevision), policyRevision).requireSuccess()
    val policyEditor = controller.policyEditor()
    assertEquals("configured", policyEditor.getString("kind"))
    policyEditor.getJSONObject("draft").let { storedDraft ->
      assertEquals("09:00", storedDraft.getString("primaryStart"))
      assertEquals("11:00", storedDraft.getString("primaryEnd"))
      assertEquals("none", storedDraft.getJSONObject("latePolicy").getString("kind"))
      assertEquals(10, storedDraft.getInt("dailyCap"))
    }

    val approvalRevision = revision()
    val approvalReview = controller.prepareApprovals(
      JSONObject()
        .put("contactIds", JSONArray(listOf(contactId)))
        .put("expectedRevision", approvalRevision.toString()),
      approvalRevision,
    ).successPayload()
    assertEquals(1, approvalReview.getInt("readyCount"))
    val approvalHandle = approvalReview.getString("handle")
    controller.confirmApprovals(handleRequest(approvalHandle, approvalRevision), approvalRevision)
      .requireSuccess()

    val recipient = requireNotNull(database.configurationDao().recipientPolicy(contactId))
    assertEquals(RecipientEnrollmentState.ENABLED, recipient.state)
    val approval = requireNotNull(recipient.approvalId?.let { database.configurationDao().approval(it) })
    assertEquals(ApprovalRecordState.ACTIVE, approval.state)
    assertEquals("Happy birthday, Ada! Wishing you a wonderful day.", approval.exactMessage)
    assertEquals(64, approval.contentHash.length)
    assertTrue(
      controller.confirmApprovals(handleRequest(approvalHandle, approvalRevision), approvalRevision) is
        ConfigurationOutcome.Problem,
    )

    val testRevision = revision()
    val testReview = controller.prepareTest(
      JSONObject()
        .put("destination", "+919876543299")
        .put("expectedRevision", testRevision.toString()),
      testRevision,
    ).successPayload()
    assertEquals("android", testReview.getString("platform"))
    assertEquals(1, testReview.getInt("segmentCount"))
    assertEquals("•••• 3299", testReview.getString("maskedDestination"))
    val testHandle = testReview.getString("handle")
    assertTrue(
      controller.preflightTestStart(handleRequest(testHandle, testRevision), testRevision) is
        ConfigurationOutcome.Success,
    )
    val started = controller.startTest(
      handleRequest(testHandle, testRevision),
      testRevision,
    ) as TestStartOutcome.Ready
    val storedTest = requireNotNull(database.safetyLedgerDao().getTestJob(started.testJobId))
    assertEquals(INSTALLATION_ID, storedTest.installationId)
    assertEquals("PREPARED", storedTest.state.name)
    assertEquals(64, storedTest.payloadHash.length)
    assertEquals(64, storedTest.destinationPrehash.length)
    assertEquals(64, storedTest.foregroundConfirmationNonceHash.length)
    assertEquals("prepared", controller.latestTestProjection()?.getString("phase"))
    assertTrue(
      controller.startTest(handleRequest(testHandle, testRevision), testRevision) is
        TestStartOutcome.Rejected,
    )

    val pauseRevision = revision()
    controller.mutateRecipient(
      "pause",
      JSONObject().put("contactId", contactId).put("expectedRevision", pauseRevision.toString()),
      pauseRevision,
    ).requireSuccess()
    assertEquals(
      RecipientEnrollmentState.PAUSED,
      database.configurationDao().recipientPolicy(contactId)?.state,
    )
    val pausedRevision = revision()
    assertTrue(
      controller.prepareApprovals(
        JSONObject()
          .put("contactIds", JSONArray(listOf(contactId)))
          .put("expectedRevision", pausedRevision.toString()),
        pausedRevision,
      ) is ConfigurationOutcome.Problem,
    )
    controller.mutateRecipient(
      "restore",
      JSONObject().put("contactId", contactId).put("expectedRevision", pausedRevision.toString()),
      pausedRevision,
    ).requireSuccess()
    assertEquals(
      RecipientEnrollmentState.ENABLED,
      database.configurationDao().recipientPolicy(contactId)?.state,
    )

    val home = controller.homePayload(
      JSONObject().put("platform", "android"),
      JSONObject().put("kind", "fresh").put("completedAt", "2026-01-01T00:00:00Z").put("contactCount", 1),
    )
    assertEquals(1, home.getJSONObject("counts").getInt("enabled"))
    assertTrue(home.has("next"))

    val excludeRevision = revision()
    controller.mutateRecipient(
      "exclude",
      JSONObject().put("contactId", contactId).put("expectedRevision", excludeRevision.toString()),
      excludeRevision,
    ).requireSuccess()
    assertEquals(
      RecipientEnrollmentState.EXCLUDED,
      database.configurationDao().recipientPolicy(contactId)?.state,
    )
    assertEquals(ApprovalRecordState.REVOKED, database.configurationDao().approval(approval.approvalId)?.state)
    val restoreRevision = revision()
    controller.mutateRecipient(
      "restore",
      JSONObject().put("contactId", contactId).put("expectedRevision", restoreRevision.toString()),
      restoreRevision,
    ).requireSuccess()
    assertEquals(
      RecipientEnrollmentState.NEEDS_REVIEW,
      database.configurationDao().recipientPolicy(contactId)?.state,
    )
  }

  @Test
  fun explicitPhoneAndBirthdayChoicesUseControlCasAndInvalidateStaleRevision() = runBlocking {
    importContact(multipleChoiceContact())
    val contactId = activeContactId()
    val initial = requireNotNull(controller.contactDetail(contactId))
    assertEquals(2, initial.getJSONArray("phoneChoices").length())
    assertEquals(2, initial.getJSONArray("birthdayChoices").length())

    val firstRevision = revision()
    val phoneId = initial.getJSONArray("phoneChoices").getJSONObject(0).getString("id")
    controller.choosePhone(
      JSONObject()
        .put("contactId", contactId)
        .put("phoneId", phoneId)
        .put("expectedRevision", firstRevision.toString()),
      firstRevision,
    ).requireSuccess()
    val secondRevision = revision()
    assertNotEquals(firstRevision, secondRevision)

    val birthdayId = initial.getJSONArray("birthdayChoices").getJSONObject(0).getString("id")
    controller.chooseBirthday(
      JSONObject()
        .put("contactId", contactId)
        .put("birthdayId", birthdayId)
        .put("expectedRevision", secondRevision.toString()),
      secondRevision,
    ).requireSuccess()
    val final = requireNotNull(controller.contactDetail(contactId))
    assertEquals(phoneId, final.getString("selectedPhoneId"))
    assertEquals(birthdayId, final.getString("selectedBirthdayId"))

    assertTrue(
      controller.choosePhone(
        JSONObject()
          .put("contactId", contactId)
          .put("phoneId", phoneId)
          .put("expectedRevision", firstRevision.toString()),
        firstRevision,
      ) is ConfigurationOutcome.Problem,
    )
  }

  @Test
  fun mismatchedIdentityCannotCreateOrCommitConfigurationButCanStillPause() = runBlocking {
    importContact(singleContact())
    val contactId = activeContactId()
    val expectedRevision = revision()
    sessionMatches = false

    val prepared = controller.prepareEnrollment(
      JSONObject()
        .put("contactIds", JSONArray(listOf(contactId)))
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    )
    assertTrue(prepared is ConfigurationOutcome.Problem)
    assertEquals(expectedRevision, revision())

    val paused = controller.pauseAll(
      JSONObject().put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
    )
    assertTrue(paused is AccountModePreparationOutcome.Ready)
  }

  @Test
  fun geminiProvenanceLookupRunsOutsideRoomTransactionAndOnlyExactCandidateIsAttributed() =
    runBlocking {
      importContact(singleContact())
      val gateway = AndroidGeminiSuggestionGateway(
        client = RoomBoundGeminiClient {
          runBlocking {
            database.peopleSyncDao().activeAccount()
              ?.takeIf { it.accountId == ACCOUNT_ID && sessionMatches }
              ?.accountId
          }
        },
        rateGuard = GeminiUxRateGuard(MemoryGeminiRateStore(), cooldownMillis = 0),
        wallClockMillis = { 0 },
        elapsedClockMillis = { 0 },
      )
      controller = newController(gateway)
      val suggestionRequest = JSONObject()
        .put("language", "en")
        .put("tone", "warm")
        .put("placeholderMode", JSONObject().put("kind", "given-name").put("requiredCount", 1))
        .put("requestedSegmentCap", 2)
      assertEquals("candidates", gateway.generate(suggestionRequest).getString("kind"))

      val exactDraft = JSONObject(suggestionRequest.toString())
        .put("text", GEMINI_CANDIDATE)
      val exactRevision = revision()
      val exactReview = controller.previewMessage(
        JSONObject().put("draft", exactDraft).put("expectedRevision", exactRevision.toString()),
        exactRevision,
      ).successPayload()
      withTimeout(5_000) {
        controller.saveMessage(
          handleRequest(exactReview.getString("handle"), exactRevision),
          exactRevision,
        ).requireSuccess()
      }
      val geminiTemplate = requireNotNull(database.configurationDao().activeTemplate(ACCOUNT_ID))
      assertEquals(TemplateSource.GEMINI, geminiTemplate.source)
      assertEquals(GeminiSuggestionPolicy.MODEL_IDENTIFIER, geminiTemplate.modelIdentifier)
      assertEquals(GeminiSuggestionPolicy.PROMPT_POLICY_VERSION, geminiTemplate.promptPolicyVersion)

      assertEquals("candidates", gateway.generate(suggestionRequest).getString("kind"))
      val editedDraft = JSONObject(suggestionRequest.toString())
        .put("text", "$GEMINI_CANDIDATE Have a lovely day.")
      val editedRevision = revision()
      val editedReview = controller.previewMessage(
        JSONObject().put("draft", editedDraft).put("expectedRevision", editedRevision.toString()),
        editedRevision,
      ).successPayload()
      controller.saveMessage(
        handleRequest(editedReview.getString("handle"), editedRevision),
        editedRevision,
      ).requireSuccess()
      val userTemplate = requireNotNull(database.configurationDao().activeTemplate(ACCOUNT_ID))
      assertEquals(TemplateSource.USER, userTemplate.source)
      assertEquals(null, userTemplate.modelIdentifier)
      assertEquals(null, userTemplate.promptPolicyVersion)
    }

  @Test
  fun deletionFailureAllowsOnlyReviewedLocalWipeOnTheExistingOperation() = runBlocking {
    val operation = DurablePrivacyOperation(
      id = "privacy_${UUID.randomUUID().toString().replace("-", "")}",
      action = "delete-account",
      state = "remote-pending",
      reason = "coordination-unavailable",
      updatedAtMillis = now++,
      completedAtMillis = null,
      requestId = "00000000-0000-4000-8000-000000000001",
    )
    assertTrue(LifecycleStateStore(context).putOperation(operation))
    val lifecycle = AndroidLifecycleController(
      context = context,
      database = database,
      wallClockMillis = { now++ },
      accountSessionMatches = { true },
    )
    val expectedRevision = revision()
    val review = lifecycle.preparePrivacyAction(
      JSONObject()
        .put("kind", "wipe-local-data")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
      preissuedPermitMayFinish = false,
    ).successPayload()
    assertEquals("wipe-local-data", review.getString("kind"))
    assertTrue(review.getBoolean("preissuedPermitMayFinish"))
    assertFalse(review.getBoolean("remoteConnectionRequired"))
    val wrongAccountReview = AndroidLifecycleController(
      context = context,
      database = database,
      accountSessionMatches = { false },
    ).preparePrivacyAction(
      JSONObject()
        .put("kind", "wipe-local-data")
        .put("expectedRevision", expectedRevision.toString()),
      expectedRevision,
      preissuedPermitMayFinish = false,
    )
    assertFalse(wrongAccountReview is ConfigurationOutcome.Success)

    val confirmation = lifecycle.beginPrivacyAction(
      handleRequest(review.getString("handle"), expectedRevision),
      expectedRevision,
    )
    assertTrue(confirmation is PrivacyConfirmationOutcome.Ready)
    val plan = (confirmation as PrivacyConfirmationOutcome.Ready).plan
    assertEquals(operation.id, plan.operationId)
    assertEquals("delete-account", plan.action)
    assertTrue(plan.deletionLocalWipeFallback)

    val persisted = checkNotNull(LifecycleStateStore(context).latestOperation())
    assertEquals(operation.requestId, persisted.requestId)
    assertEquals("local-wiping", persisted.state)
    assertTrue(persisted.deletionLocalWipeFallback)
    assertFalse(persisted.deletionRetryAllowed)
    assertNotEquals(null, persisted.recoveryBindingSalt)
    assertNotEquals(null, persisted.recoveryFirebaseUidHash)
    assertNotEquals(null, persisted.recoveryGoogleSubjectHash)
  }

  @Test
  fun disconnectContactsPurgesPrivateWorkingDataButRetainsAccountAndInstallation() = runBlocking {
    importContact(singleContact())
    assertEquals(1, database.peopleSyncDao().activeContactCount(ACCOUNT_ID))
    val lifecycle = AndroidLifecycleController(
      context = context,
      database = database,
      wallClockMillis = { now++ },
    )
    val plan = PrivacyActionPlan(
      operationId = "privacy_${UUID.randomUUID().toString().replace("-", "")}",
      action = "disconnect-contacts",
      requiresPause = true,
      remoteRequired = true,
    )
    assertTrue(
      LifecycleStateStore(context).putOperation(
        DurablePrivacyOperation(
          id = plan.operationId,
          action = plan.action,
          state = "local-wiping",
          reason = null,
          updatedAtMillis = now++,
          completedAtMillis = null,
          requestId = "00000000-0000-4000-8000-000000000001",
        ),
      ),
    )
    val operation = lifecycle.disconnectContacts(
      plan,
      ACCOUNT_ID,
    )

    assertEquals("complete", operation.state)
    assertEquals(ACCOUNT_ID, database.peopleSyncDao().activeAccount()?.accountId)
    assertEquals(INSTALLATION_ID, database.automationOrchestrationDao().localInstallation()?.installationId)
    assertEquals(0, database.peopleSyncDao().activeContactCount(ACCOUNT_ID))
    assertEquals(
      SyncFreshness.AUTH_ACTION_REQUIRED,
      database.peopleSyncDao().contactSyncState(ACCOUNT_ID)?.freshness,
    )
    assertEquals(null, database.configurationDao().activeTemplate(ACCOUNT_ID))
    assertEquals(null, database.configurationDao().activeAutomationPolicy(ACCOUNT_ID))
  }

  private suspend fun importContact(delta: PeopleContactDelta) {
    val store = RoomPeopleSyncStagingStore(
      dao = database.peopleSyncDao(),
      accountId = ACCOUNT_ID,
      accountLocaleTag = "en-IN",
      parameterFingerprint = fingerprint,
      clock = PeopleWallClock { now++ },
    )
    val transaction = requireNotNull(store.begin(PeopleSyncMode.Full))
    assertTrue(transaction.stagePage(0, listOf(delta)))
    assertTrue(
      transaction.commit(
        PeopleSyncCompletion(
          nextSyncToken = "sync-token",
          parameterFingerprint = fingerprint,
          changedPeople = 1,
          pages = 1,
        ),
      ),
    )
  }

  private suspend fun activeContactId(): String =
    database.peopleSyncDao().contactPage(ACCOUNT_ID, "all", "%", 10, 0).single().contactId

  private fun lifecycleFiles(): List<java.io.File> {
    val base = java.io.File(context.noBackupFilesDir, "birthday-lifecycle-state-v1")
    val receipt = java.io.File(context.noBackupFilesDir, "birthday-deletion-receipt-v1")
    return listOf(
      base,
      java.io.File(base.path + ".bak"),
      java.io.File(base.path + ".new"),
      receipt,
      java.io.File(receipt.path + ".bak"),
      java.io.File(receipt.path + ".new"),
    )
  }

  private suspend fun revision(): Long = requireNotNull(database.configurationDao().control()).revision

  private fun handleRequest(handle: String, revision: Long) = JSONObject()
    .put("handle", handle)
    .put("expectedRevision", revision.toString())

  private fun ConfigurationOutcome.successPayload(): JSONObject =
    (this as ConfigurationOutcome.Success).payload

  private fun ConfigurationOutcome.requireSuccess() {
    assertTrue(toString(), this is ConfigurationOutcome.Success)
  }

  private fun singleContact() = PeopleContactDelta(
    resourceName = "people/ada",
    contactSourceId = "contacts/ada",
    deleted = false,
    names = listOf(PeopleName("Ada Lovelace", "Ada")),
    birthdays = listOf(PeopleBirthday(1815, 12, 10)),
    phoneNumbers = listOf(PeoplePhone("+919876543210", "mobile")),
  )

  private fun multipleChoiceContact() = PeopleContactDelta(
    resourceName = "people/grace",
    contactSourceId = "contacts/grace",
    deleted = false,
    names = listOf(PeopleName("Grace Hopper", "Grace")),
    birthdays = listOf(PeopleBirthday(1906, 12, 9), PeopleBirthday(null, 12, 10)),
    phoneNumbers = listOf(
      PeoplePhone("+919876543210", "mobile"),
      PeoplePhone("+919876543211", "mobile"),
    ),
  )

  private class MemoryGeminiRateStore : GeminiRateStore {
    private var value: GeminiRateState? = null

    override fun read(): GeminiRateState? = value

    override fun write(value: GeminiRateState): Boolean {
      this.value = value
      return true
    }
  }

  private class RoomBoundGeminiClient(
    private val sessionKeyProvider: () -> String?,
  ) : GeminiNativeClient {
    override fun accountSessionKey(): String? = sessionKeyProvider()

    override fun isOnline(): Boolean = true

    override suspend fun appCheckReady(): Boolean = true

    override suspend fun generate(systemInstruction: String, prompt: String): String =
      "{\"candidates\":[{\"text\":\"$GEMINI_CANDIDATE\",\"language\":\"en\"}]}"
  }

  private companion object {
    val ACCOUNT_ID = "a_${"1".repeat(64)}"
    const val INSTALLATION_ID = "installation-test"
    const val TEST_VERSION_CODE = 42L
    const val GEMINI_CANDIDATE = "Happy birthday, {firstName}!"
    val TEST_SIGNING_CERTIFICATE = "a".repeat(64)
  }
}
