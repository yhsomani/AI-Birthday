package com.yashsomani.birthdayautopilot.people

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.ConsentDecision
import com.yashsomani.birthdayautopilot.storage.database.ConsentKind
import com.yashsomani.birthdayautopilot.storage.database.IdentityAttachDecision
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactsConsentRecorderInstrumentationTest {
  private lateinit var database: BirthdayDatabase
  private lateinit var recorder: RoomContactsConsentRecorder

  @Before
  fun setUp() = runBlocking {
    val context: Context = ApplicationProvider.getApplicationContext()
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
          createdAtMillis = NOW,
          updatedAtMillis = NOW,
        ),
        "people-parameter-fingerprint",
      ),
    )
    recorder = RoomContactsConsentRecorder(
      database,
      Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC),
    )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun backgroundGrantCannotInventMissingForegroundDisclosure() = runBlocking {
    assertEquals(ContactsDisclosureState.MISSING, recorder.disclosureState(ACCOUNT_ID))
    assertFalse(recorder.recordGranted(ACCOUNT_ID, disclosureAcknowledged = false))
    assertNull(
      database.configurationDao().latestConsentReceipt(
        ACCOUNT_ID,
        ConsentKind.CONTACTS_DISCLOSURE,
      ),
    )
    assertNull(
      database.configurationDao().latestConsentReceipt(
        ACCOUNT_ID,
        ConsentKind.CONTACTS_READONLY,
      ),
    )
  }

  @Test
  fun foregroundGrantAtomicallyRecordsDisclosureAndExactReadonlyScope() = runBlocking {
    assertTrue(recorder.recordGranted(ACCOUNT_ID, disclosureAcknowledged = true))
    assertEquals(ContactsDisclosureState.CURRENT, recorder.disclosureState(ACCOUNT_ID))

    val disclosure = requireNotNull(
      database.configurationDao().latestConsentReceipt(
        ACCOUNT_ID,
        ConsentKind.CONTACTS_DISCLOSURE,
      ),
    )
    val scope = requireNotNull(
      database.configurationDao().latestConsentReceipt(
        ACCOUNT_ID,
        ConsentKind.CONTACTS_READONLY,
      ),
    )
    assertEquals(ConsentDecision.GRANTED, disclosure.decision)
    assertEquals(RoomContactsConsentRecorder.CONTACTS_DISCLOSURE_VERSION, disclosure.disclosureVersion)
    assertEquals(RoomContactsConsentRecorder.DISCLOSURE_SCOPE_HASH, disclosure.scopeHash)
    assertEquals(ConsentDecision.GRANTED, scope.decision)
    assertEquals(RoomContactsConsentRecorder.CONTACTS_SCOPE_VERSION, scope.disclosureVersion)
    assertEquals(RoomContactsConsentRecorder.CONTACTS_SCOPE_HASH, scope.scopeHash)
    assertEquals(NOW, disclosure.recordedAtMillis)
    assertEquals(NOW, scope.recordedAtMillis)
  }

  @Test
  fun foregroundReauthorizationAndBackgroundRefreshAreIdempotentForCurrentVersions() =
    runBlocking {
      assertTrue(recorder.recordGranted(ACCOUNT_ID, disclosureAcknowledged = true))
      val firstDisclosure = requireNotNull(
        database.configurationDao().latestConsentReceipt(
          ACCOUNT_ID,
          ConsentKind.CONTACTS_DISCLOSURE,
        ),
      )
      val firstScope = requireNotNull(
        database.configurationDao().latestConsentReceipt(
          ACCOUNT_ID,
          ConsentKind.CONTACTS_READONLY,
        ),
      )

      assertTrue(recorder.recordGranted(ACCOUNT_ID, disclosureAcknowledged = false))
      assertEquals(
        firstScope.receiptId,
        database.configurationDao().latestConsentReceipt(
          ACCOUNT_ID,
          ConsentKind.CONTACTS_READONLY,
        )?.receiptId,
      )

      assertTrue(recorder.recordGranted(ACCOUNT_ID, disclosureAcknowledged = true))
      val secondDisclosure = requireNotNull(
        database.configurationDao().latestConsentReceipt(
          ACCOUNT_ID,
          ConsentKind.CONTACTS_DISCLOSURE,
        ),
      )
      val secondScope = requireNotNull(
        database.configurationDao().latestConsentReceipt(
          ACCOUNT_ID,
          ConsentKind.CONTACTS_READONLY,
        ),
      )
      assertEquals(firstDisclosure.receiptId, secondDisclosure.receiptId)
      assertEquals(1L, secondDisclosure.sequence)
      assertNull(secondDisclosure.supersedesReceiptId)
      assertEquals(firstScope.receiptId, secondScope.receiptId)
      assertEquals(1L, secondScope.sequence)
      assertNull(secondScope.supersedesReceiptId)
    }

  @Test
  fun localDisconnectRevocationRequiresTheDedicatedDisclosureBeforeAnotherSync() = runBlocking {
    assertTrue(recorder.recordGranted(ACCOUNT_ID, disclosureAcknowledged = true))
    assertTrue(
      database.withTransaction {
        recordContactsConsentDecision(
          dao = database.configurationDao(),
          accountId = ACCOUNT_ID,
          kind = ConsentKind.CONTACTS_DISCLOSURE,
          decision = ConsentDecision.REVOKED,
          nowMillis = NOW + 1,
        )
      },
    )
    assertEquals(ContactsDisclosureState.MISSING, recorder.disclosureState(ACCOUNT_ID))
    assertFalse(recorder.recordGranted(ACCOUNT_ID, disclosureAcknowledged = false))
    assertTrue(recorder.recordGranted(ACCOUNT_ID, disclosureAcknowledged = true))
    val latest = requireNotNull(
      database.configurationDao().latestConsentReceipt(
        ACCOUNT_ID,
        ConsentKind.CONTACTS_DISCLOSURE,
      ),
    )
    assertEquals(ConsentDecision.GRANTED, latest.decision)
    assertEquals(3L, latest.sequence)
    assertTrue(latest.supersedesReceiptId != null)
  }

  private companion object {
    const val NOW = 1_800_000_000_000L
    val ACCOUNT_ID = "a_${"1".repeat(64)}"
  }
}
