package com.yashsomani.birthdayautopilot.people

import androidx.room.withTransaction
import com.yashsomani.birthdayautopilot.auth.CONTACTS_READONLY_SCOPE
import com.yashsomani.birthdayautopilot.configuration.ConfigurationCanonicalHash
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.ConsentDecision
import com.yashsomani.birthdayautopilot.storage.database.ConsentKind
import com.yashsomani.birthdayautopilot.storage.database.ConsentReceiptEntity
import com.yashsomani.birthdayautopilot.storage.database.ConfigurationDao
import java.time.Clock
import kotlinx.coroutines.CancellationException

internal enum class ContactsDisclosureState {
  CURRENT,
  MISSING,
  STORAGE_UNAVAILABLE,
}

internal interface ContactsConsentRecorder {
  suspend fun disclosureState(accountId: String): ContactsDisclosureState

  suspend fun recordGranted(accountId: String, disclosureAcknowledged: Boolean): Boolean
}

/**
 * Appends one exact, idempotent Contacts-consent decision inside the caller's Room transaction.
 * Keeping revocation in the same transaction as destructive contact cleanup prevents a crash from
 * leaving a stale granted disclosure able to authorize a later background sync.
 */
internal suspend fun recordContactsConsentDecision(
  dao: ConfigurationDao,
  accountId: String,
  kind: ConsentKind,
  decision: ConsentDecision,
  nowMillis: Long,
): Boolean {
  if (nowMillis <= 0) return false
  val policy = when (kind) {
    ConsentKind.CONTACTS_DISCLOSURE -> Pair(
      RoomContactsConsentRecorder.CONTACTS_DISCLOSURE_VERSION,
      RoomContactsConsentRecorder.DISCLOSURE_SCOPE_HASH,
    )
    ConsentKind.CONTACTS_READONLY -> Pair(
      RoomContactsConsentRecorder.CONTACTS_SCOPE_VERSION,
      RoomContactsConsentRecorder.CONTACTS_SCOPE_HASH,
    )
    else -> return false
  }
  val prior = dao.latestConsentReceipt(accountId, kind)
  if (
    prior?.decision == decision &&
    prior.disclosureVersion == policy.first &&
    prior.scopeHash == policy.second
  ) return true
  if (prior != null && prior.sequence !in 1 until Long.MAX_VALUE) return false
  val sequence = (prior?.sequence ?: 0L) + 1L
  val receipt = ConsentReceiptEntity(
    receiptId = "cr_${ConfigurationCanonicalHash.content(
      "BirthdayAutopilot.ContactsConsentReceiptId.v1",
      listOf(accountId, kind.name, sequence.toString()),
    )}",
    accountId = accountId,
    kind = kind,
    decision = decision,
    disclosureVersion = policy.first,
    scopeHash = policy.second,
    sequence = sequence,
    supersedesReceiptId = prior?.receiptId,
    recordedAtMillis = maxOf(nowMillis, prior?.recordedAtMillis ?: 0L),
  )
  dao.insertConsentReceipt(receipt)
  return true
}

/**
 * Persists content-free proof of the exact foreground disclosure and SDK-managed read-only scope.
 * OAuth credentials, provider objects, raw contacts, and message content never enter this ledger.
 */
internal class RoomContactsConsentRecorder(
  private val database: BirthdayDatabase,
  private val clock: Clock = Clock.systemUTC(),
) : ContactsConsentRecorder {
  override suspend fun disclosureState(accountId: String): ContactsDisclosureState = try {
    database.withTransaction {
      val dao = database.configurationDao()
      val account = dao.activeAccount()?.takeIf { it.accountId == accountId }
        ?: return@withTransaction ContactsDisclosureState.STORAGE_UNAVAILABLE
      val receipt = dao.latestConsentReceipt(account.accountId, ConsentKind.CONTACTS_DISCLOSURE)
      if (
        receipt?.decision == ConsentDecision.GRANTED &&
        receipt.disclosureVersion == CONTACTS_DISCLOSURE_VERSION &&
        receipt.scopeHash == DISCLOSURE_SCOPE_HASH
      ) {
        ContactsDisclosureState.CURRENT
      } else {
        ContactsDisclosureState.MISSING
      }
    }
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: RuntimeException) {
    ContactsDisclosureState.STORAGE_UNAVAILABLE
  }

  override suspend fun recordGranted(
    accountId: String,
    disclosureAcknowledged: Boolean,
  ): Boolean = try {
    database.withTransaction {
      val dao = database.configurationDao()
      val account = dao.activeAccount()?.takeIf { it.accountId == accountId }
        ?: return@withTransaction false
      var latestDisclosure = dao.latestConsentReceipt(
        account.accountId,
        ConsentKind.CONTACTS_DISCLOSURE,
      )
      val now = clock.millis().takeIf { it > 0 } ?: return@withTransaction false

      val currentDisclosureIsGranted =
        latestDisclosure?.decision == ConsentDecision.GRANTED &&
          latestDisclosure.disclosureVersion == CONTACTS_DISCLOSURE_VERSION &&
          latestDisclosure.scopeHash == DISCLOSURE_SCOPE_HASH
      if (!currentDisclosureIsGranted && disclosureAcknowledged) {
        latestDisclosure = insertGranted(
          accountId = account.accountId,
          kind = ConsentKind.CONTACTS_DISCLOSURE,
          disclosureVersion = CONTACTS_DISCLOSURE_VERSION,
          scopeHash = DISCLOSURE_SCOPE_HASH,
          now = now,
        ) ?: return@withTransaction false
      }
      if (
        latestDisclosure?.decision != ConsentDecision.GRANTED ||
        latestDisclosure.disclosureVersion != CONTACTS_DISCLOSURE_VERSION ||
        latestDisclosure.scopeHash != DISCLOSURE_SCOPE_HASH
      ) return@withTransaction false

      val latestScope = dao.latestConsentReceipt(account.accountId, ConsentKind.CONTACTS_READONLY)
      if (
        latestScope?.decision == ConsentDecision.GRANTED &&
        latestScope.disclosureVersion == CONTACTS_SCOPE_VERSION &&
        latestScope.scopeHash == CONTACTS_SCOPE_HASH
      ) return@withTransaction true

      insertGranted(
        accountId = account.accountId,
        kind = ConsentKind.CONTACTS_READONLY,
        disclosureVersion = CONTACTS_SCOPE_VERSION,
        scopeHash = CONTACTS_SCOPE_HASH,
        now = now,
      ) != null
    }
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: RuntimeException) {
    false
  }

  private suspend fun insertGranted(
    accountId: String,
    kind: ConsentKind,
    disclosureVersion: String,
    scopeHash: String,
    now: Long,
  ): ConsentReceiptEntity? {
    val dao = database.configurationDao()
    val prior = dao.latestConsentReceipt(accountId, kind)
    if (prior != null && prior.sequence !in 1 until Long.MAX_VALUE) return null
    val sequence = (prior?.sequence ?: 0L) + 1L
    val receipt = ConsentReceiptEntity(
      receiptId = "cr_${ConfigurationCanonicalHash.content(
        "BirthdayAutopilot.ContactsConsentReceiptId.v1",
        listOf(accountId, kind.name, sequence.toString()),
      )}",
      accountId = accountId,
      kind = kind,
      decision = ConsentDecision.GRANTED,
      disclosureVersion = disclosureVersion,
      scopeHash = scopeHash,
      sequence = sequence,
      supersedesReceiptId = prior?.receiptId,
      recordedAtMillis = maxOf(now, prior?.recordedAtMillis ?: 0L),
    )
    dao.insertConsentReceipt(receipt)
    return receipt
  }

  internal companion object {
    const val CONTACTS_DISCLOSURE_VERSION = "contacts-device-storage-v1"
    const val CONTACTS_SCOPE_VERSION = "google-contacts-readonly-v1"
    val DISCLOSURE_SCOPE_HASH = ConfigurationCanonicalHash.content(
      "BirthdayAutopilot.ContactsDisclosureScope.v1",
      listOf(CONTACTS_DISCLOSURE_VERSION),
    )
    val CONTACTS_SCOPE_HASH = ConfigurationCanonicalHash.content(
      "BirthdayAutopilot.ContactsOAuthScope.v1",
      listOf(CONTACTS_READONLY_SCOPE),
    )
  }
}
