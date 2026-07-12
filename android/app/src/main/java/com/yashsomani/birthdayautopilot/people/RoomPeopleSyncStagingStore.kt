package com.yashsomani.birthdayautopilot.people

import com.yashsomani.birthdayautopilot.contacts.BirthdayNormalizer
import com.yashsomani.birthdayautopilot.contacts.ContactNormalizer
import com.yashsomani.birthdayautopilot.contacts.ContactReadiness
import com.yashsomani.birthdayautopilot.contacts.ContactSelections
import com.yashsomani.birthdayautopilot.contacts.GoogleContactSource
import com.yashsomani.birthdayautopilot.contacts.GoogleContactSourceType
import com.yashsomani.birthdayautopilot.contacts.LibPhoneNumberMetadataEngine
import com.yashsomani.birthdayautopilot.contacts.NormalizedPhone
import com.yashsomani.birthdayautopilot.contacts.PhoneLabel
import com.yashsomani.birthdayautopilot.contacts.PhoneNormalizer
import com.yashsomani.birthdayautopilot.contacts.PhoneRejectionReason
import com.yashsomani.birthdayautopilot.contacts.RawBirthday
import com.yashsomani.birthdayautopilot.contacts.RawContactPhone
import com.yashsomani.birthdayautopilot.contacts.RawGoogleContact
import com.yashsomani.birthdayautopilot.planning.LeapDayPolicy
import com.yashsomani.birthdayautopilot.storage.database.ContactPhoneEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactSnapshotEntity
import com.yashsomani.birthdayautopilot.storage.database.PeopleStagingContactEntity
import com.yashsomani.birthdayautopilot.storage.database.PeopleStagingBirthdayEntity
import com.yashsomani.birthdayautopilot.storage.database.PeopleStagingPhoneEntity
import com.yashsomani.birthdayautopilot.storage.database.PeopleSyncDao
import com.yashsomani.birthdayautopilot.storage.database.PeopleSyncGenerationEntity
import com.yashsomani.birthdayautopilot.storage.database.PhoneRecordState
import com.yashsomani.birthdayautopilot.storage.database.RecipientPolicyEntity
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface PeopleWallClock {
  fun nowMillis(): Long
}

internal data class ExistingPeopleContactContext(
  val contact: ContactSnapshotEntity,
  val phones: List<ContactPhoneEntity>,
  val policy: RecipientPolicyEntity?,
)

internal data class PreparedPeoplePage(
  val contacts: List<PeopleStagingContactEntity>,
  val phones: List<PeopleStagingPhoneEntity>,
  val birthdays: List<PeopleStagingBirthdayEntity>,
)

/** Converts parsed People fields into bounded, normalized rows without retaining provider JSON. */
internal class PeopleDeltaStageMapper(
  private val accountId: String,
  private val generationId: String,
  private val homeRegion: String?,
  private val stagedAtMillis: Long,
  private val phoneNormalizer: PhoneNormalizer = PhoneNormalizer(LibPhoneNumberMetadataEngine()),
  private val normalizer: ContactNormalizer = ContactNormalizer(
    BirthdayNormalizer(),
    phoneNormalizer,
  ),
) {
  fun sourceFingerprint(contactSourceId: String): String = StablePrivateId.hash(
    "PeopleSource.v1",
    accountId,
    contactSourceId,
  )

  fun prepare(
    deltas: List<PeopleContactDelta>,
    previous: Map<String, ExistingPeopleContactContext>,
  ): PreparedPeoplePage {
    val contacts = ArrayList<PeopleStagingContactEntity>(deltas.size)
    val phones = ArrayList<PeopleStagingPhoneEntity>()
    val birthdays = ArrayList<PeopleStagingBirthdayEntity>()
    deltas.forEach { delta ->
      val sourceFingerprint = sourceFingerprint(delta.contactSourceId)
      val prior = previous[sourceFingerprint]
      val prepared = prepareOne(delta, sourceFingerprint, prior)
      contacts += prepared.first
      phones += prepared.second.first
      birthdays += prepared.second.second
    }
    return PreparedPeoplePage(contacts, phones, birthdays)
  }

  private fun prepareOne(
    delta: PeopleContactDelta,
    sourceFingerprint: String,
    previous: ExistingPeopleContactContext?,
  ): Pair<PeopleStagingContactEntity, Pair<List<PeopleStagingPhoneEntity>, List<PeopleStagingBirthdayEntity>>> {
    val contactId = StablePrivateId.prefixed("c", "Contact.v1", accountId, delta.contactSourceId)
    val stagingContactId = "$generationId:$contactId"
    if (delta.deleted) {
      val digest = StablePrivateId.hash(
        "ContactMaterial.v1",
        accountId,
        delta.resourceName,
        delta.contactSourceId,
        "deleted",
      )
      return PeopleStagingContactEntity(
        stagingContactId = stagingContactId,
        generationId = generationId,
        accountId = accountId,
        contactId = contactId,
        peopleResourceName = delta.resourceName,
        sourceFingerprint = sourceFingerprint,
        displayName = null,
        safeGivenName = null,
        birthdayMonth = null,
        birthdayDay = null,
        birthdayYear = null,
        leapDayPolicy = null,
        deleted = true,
        selectedPhoneId = null,
        selectedBirthdayId = null,
        readiness = ContactReadiness.UNAVAILABLE.name,
        normalizationIssues = "DELETED",
        materialDigest = digest,
        stagedAtMillis = stagedAtMillis,
      ) to (emptyList<PeopleStagingPhoneEntity>() to emptyList())
    }

    val sourcePhones = delta.phoneNumbers
      .distinctBy { listOf(it.value.trim(), it.type.orEmpty()) }
      .map { phone ->
        val sourcePhoneFingerprint = StablePrivateId.hash(
          "PeoplePhoneSource.v1",
          accountId,
          delta.contactSourceId,
          phone.value.trim(),
          phone.type.orEmpty(),
        )
        SourcePhone(
          source = phone,
          sourceFingerprint = sourcePhoneFingerprint,
          phoneId = StablePrivateId.prefixed(
            "p",
            "Phone.v1",
            accountId,
            delta.contactSourceId,
            sourcePhoneFingerprint,
          ),
        )
      }
    val distinctNames = delta.names
      .map { it.displayName to it.givenName }
      .distinct()
    val selectedName = distinctNames.singleOrNull()
    val priorBirthday = previous?.contact?.let { contact ->
      val month = contact.birthdayMonth ?: return@let null
      val day = contact.birthdayDay ?: return@let null
      RawBirthday(contact.birthdayYear, month, day)
    }
    val priorLeapPolicy = previous?.contact?.leapDayPolicy?.let { value ->
      runCatching { LeapDayPolicy.valueOf(value) }.getOrNull()
    }
    val selectedPhoneId = previous?.policy?.chosenPhoneId
    val previousRegion = selectedPhoneId?.let { selected ->
      previous.phones.singleOrNull { it.phoneId == selected }?.regionCode
    }
    val raw = RawGoogleContact(
      localId = contactId,
      resourceName = delta.resourceName,
      sources = listOf(GoogleContactSource(GoogleContactSourceType.CONTACT, delta.contactSourceId)),
      displayName = selectedName?.first?.takeIf { it.length <= MAX_BRIDGED_DISPLAY_NAME_UTF16 },
      givenName = selectedName?.second,
      birthdays = delta.birthdays.map { RawBirthday(it.year, it.month, it.day) }.distinct(),
      phones = sourcePhones.map { source ->
        RawContactPhone(
          phoneId = source.phoneId,
          value = source.source.value,
          label = source.source.type.toPhoneLabel(),
        )
      },
      deleted = false,
    )
    val normalized = normalizer.normalize(
      raw,
      ContactSelections(
        birthday = priorBirthday,
        leapDayPolicy = priorLeapPolicy,
        phoneId = selectedPhoneId,
        homeRegion = previousRegion ?: homeRegion,
      ),
    )
    // ContactNormalizer intentionally exposes only domain issues. PhoneNormalizer is repeated here
    // to retain a safe state for every parsed source phone without persisting a provider payload.
    val phoneResolution = phoneNormalizer.resolve(
      raw.phones,
      selectedPhoneId,
      previousRegion ?: homeRegion,
    )
    val rejected = phoneResolution.rejected.associate { it.phoneId to it.reason }
    val normalizedCandidates = phoneResolution.candidates
      .flatMap { candidate -> candidate.sourcePhoneIds.map { it to candidate } }
      .toMap()
    val stagedPhones = sourcePhones.map { source ->
      val candidate = normalizedCandidates[source.phoneId]
      val rejection = rejected[source.phoneId]
      val state = if (candidate != null) PhoneRecordState.READY else rejection.toPhoneState()
      val stagingPhoneId = "$generationId:${source.phoneId}"
      PeopleStagingPhoneEntity(
        stagingPhoneId = stagingPhoneId,
        stagingContactId = stagingContactId,
        generationId = generationId,
        phoneId = source.phoneId,
        contactId = contactId,
        sourceFingerprint = source.sourceFingerprint,
        rawNumber = source.source.value,
        normalizedE164 = candidate?.canonical?.value,
        destinationFingerprint = candidate?.let {
          StablePrivateId.hash("Destination.v1", accountId, it.canonical.value)
        },
        maskedDisplay = candidate?.maskedDisplay ?: maskInvalidSource(source.source.value),
        typeLabel = source.source.type,
        regionCode = (previousRegion ?: homeRegion)
          ?.takeUnless { source.source.value.trim().startsWith('+') },
        isSmsCapableType = candidate != null,
        state = state,
        stagedAtMillis = stagedAtMillis,
      )
    }
    val sourceBirthdays = delta.birthdays.distinct().map { source ->
      val sourceMaterial = listOf(
        source.year?.toString().orEmpty(),
        source.month?.toString().orEmpty(),
        source.day?.toString().orEmpty(),
      )
      val sourceBirthdayFingerprint = StablePrivateId.hash(
        "PeopleBirthdaySource.v1",
        accountId,
        delta.contactSourceId,
        *sourceMaterial.toTypedArray(),
      )
      val birthdayId = StablePrivateId.prefixed(
        "b",
        "Birthday.v1",
        accountId,
        delta.contactSourceId,
        sourceBirthdayFingerprint,
      )
      val selectable = validBirthday(source.month, source.day)
      PeopleStagingBirthdayEntity(
        stagingBirthdayId = "$generationId:$birthdayId",
        stagingContactId = stagingContactId,
        generationId = generationId,
        birthdayId = birthdayId,
        contactId = contactId,
        sourceFingerprint = sourceBirthdayFingerprint,
        birthdayYear = source.year,
        birthdayMonth = source.month,
        birthdayDay = source.day,
        selectable = selectable,
        issueCode = when {
          !selectable -> "birthday-missing"
          source.month == 2 && source.day == 29 -> "leap-policy-required"
          else -> null
        },
        stagedAtMillis = stagedAtMillis,
      )
    }
    val chosenPhoneId = normalized.selectedPhone
      ?.chooseStableSourcePhoneId(selectedPhoneId)
      ?: phoneResolution.selected?.chooseStableSourcePhoneId(selectedPhoneId)
    val birthday = normalized.birthday?.selectedSource
    val chosenBirthdayId = birthday?.let { selected ->
      sourceBirthdays.singleOrNull {
        it.birthdayYear == selected.year &&
          it.birthdayMonth == selected.month &&
          it.birthdayDay == selected.day
      }?.birthdayId
    }
    val issues = normalized.issues.map(Enum<*>::name).sorted()
    val providerBirthdayMaterial = delta.birthdays
      .map { "${it.year.orEmpty()}:${it.month.orEmpty()}:${it.day.orEmpty()}" }
      .distinct()
      .sorted()
    val materialValues = buildList {
      add(accountId)
      add(delta.resourceName)
      add(sourceFingerprint)
      add(normalized.displayName.orEmpty())
      add(normalized.safeGivenName.orEmpty())
      add(normalized.readiness.name)
      add(issues.joinToString(","))
      addAll(providerBirthdayMaterial)
      addAll(stagedPhones
        .map { phone ->
          listOf(
            phone.sourceFingerprint,
            phone.normalizedE164.orEmpty(),
            phone.destinationFingerprint.orEmpty(),
            phone.state.name,
          ).joinToString("|")
        }
        .sorted())
    }
    // User selections and leap policy are approval material, not provider source material. Their
    // own CAS mutations invalidate approvals; excluding them prevents a harmless later sync from
    // incrementing material revision a second time.
    val materialDigest = StablePrivateId.hash(
      "ContactMaterial.v1",
      *materialValues.toTypedArray(),
    )
    val stagedContact = PeopleStagingContactEntity(
      stagingContactId = stagingContactId,
      generationId = generationId,
      accountId = accountId,
      contactId = contactId,
      peopleResourceName = delta.resourceName,
      sourceFingerprint = sourceFingerprint,
      displayName = normalized.displayName,
      safeGivenName = normalized.safeGivenName,
      birthdayMonth = birthday?.month,
      birthdayDay = birthday?.day,
      birthdayYear = birthday?.year,
      leapDayPolicy = normalized.birthday?.rule?.leapDayPolicy?.name,
      deleted = false,
      selectedPhoneId = chosenPhoneId,
      selectedBirthdayId = chosenBirthdayId,
      readiness = normalized.readiness.name,
      normalizationIssues = issues.joinToString(","),
      materialDigest = materialDigest,
      stagedAtMillis = stagedAtMillis,
    )
    return stagedContact to (stagedPhones to sourceBirthdays)
  }

  private data class SourcePhone(
    val source: PeoplePhone,
    val sourceFingerprint: String,
    val phoneId: String,
  )

  private fun String?.toPhoneLabel(): PhoneLabel = when (this?.trim()?.lowercase(Locale.ROOT)) {
    "mobile" -> PhoneLabel.MOBILE
    "home" -> PhoneLabel.HOME
    "work" -> PhoneLabel.WORK
    "main" -> PhoneLabel.MAIN
    "fixed", "fixedline", "landline", "homefax", "workfax" -> PhoneLabel.FIXED_LINE
    else -> PhoneLabel.OTHER
  }

  private fun PhoneRejectionReason?.toPhoneState(): PhoneRecordState = when (this) {
    PhoneRejectionReason.REGION_REQUIRED,
    PhoneRejectionReason.REGION_INVALID,
    PhoneRejectionReason.AMBIGUOUS,
    -> PhoneRecordState.NEEDS_REGION
    PhoneRejectionReason.EMERGENCY_NUMBER,
    PhoneRejectionReason.SHORT_CODE,
    PhoneRejectionReason.PREMIUM_RATE,
    -> PhoneRecordState.UNSAFE_DESTINATION
    PhoneRejectionReason.NOT_SMS_CAPABLE -> PhoneRecordState.NON_SMS
    else -> PhoneRecordState.INVALID
  }

  private fun NormalizedPhone.chooseStableSourcePhoneId(previous: String?): String =
    previous?.takeIf(sourcePhoneIds::contains) ?: sourcePhoneIds.sorted().first()

  private fun maskInvalidSource(raw: String): String {
    val suffix = raw.filter(Char::isDigit).takeLast(4)
    return if (suffix.isEmpty()) "Unavailable" else "•••• $suffix"
  }

  private fun Int?.orEmpty(): String = this?.toString().orEmpty()

  private fun validBirthday(month: Int?, day: Int?): Boolean {
    if (month == null || day == null) return false
    return try {
      LocalDate.of(2000, month, day)
      true
    } catch (_: DateTimeException) {
      false
    }
  }

  private companion object {
    const val MAX_BRIDGED_DISPLAY_NAME_UTF16 = 256
  }
}

/**
 * Room-backed staging adapter. Each page and the final generation swap are independent Room
 * transactions; rollback deletes only short-lived rows and leaves the prior active generation.
 */
internal class RoomPeopleSyncStagingStore(
  private val dao: PeopleSyncDao,
  private val accountId: String,
  accountLocaleTag: String,
  private val parameterFingerprint: String,
  private val clock: PeopleWallClock = PeopleWallClock(System::currentTimeMillis),
) : PeopleSyncStagingStore {
  private val homeRegion = Locale.forLanguageTag(accountLocaleTag)
    .country
    .uppercase(Locale.ROOT)
    .takeIf { it.matches(Regex("^[A-Z]{2}$")) }

  override suspend fun begin(mode: PeopleSyncMode): PeopleStagingTransaction? {
    val now = clock.nowMillis().takeIf { it >= 0 } ?: return null
    val staleCutoff = now.subtractExactOrNull(STAGING_RETENTION_MILLIS) ?: 0L
    val state = dao.contactSyncState(accountId) ?: return null
    val generationId = "g_${UUID.randomUUID().toString().replace("-", "")}" 
    val modeName = when (mode) {
      PeopleSyncMode.Full -> MODE_FULL
      is PeopleSyncMode.Incremental -> MODE_INCREMENTAL
    }
    val requestedToken = (mode as? PeopleSyncMode.Incremental)?.syncToken
    val generation = PeopleSyncGenerationEntity(
      generationId = generationId,
      accountId = accountId,
      mode = modeName,
      baseActiveGeneration = state.activeGeneration,
      expectedSyncRevision = state.revision,
      parameterFingerprint = parameterFingerprint,
      startedAtMillis = now,
      nextPageIndex = 0,
      stagedContactCount = 0,
    )
    if (!dao.beginGeneration(generation, requestedToken, staleCutoff)) return null
    return RoomTransaction(generationId, now)
  }

  private inner class RoomTransaction(
    private val generationId: String,
    private val startedAtMillis: Long,
  ) : PeopleStagingTransaction {
    private val mutex = Mutex()
    private var finished = false

    override suspend fun stagePage(
      pageIndex: Int,
      contacts: List<PeopleContactDelta>,
    ): Boolean = mutex.withLock {
      if (finished) return@withLock false
      val mapper = PeopleDeltaStageMapper(
        accountId = accountId,
        generationId = generationId,
        homeRegion = homeRegion,
        stagedAtMillis = clock.nowMillis().takeIf { it >= startedAtMillis }
          ?: return@withLock false,
      )
      val fingerprints = contacts.map { mapper.sourceFingerprint(it.contactSourceId) }.distinct()
      val previousContacts = fingerprints.chunked(SQLITE_SAFE_BIND_CHUNK)
        .flatMap { chunk ->
          if (chunk.isEmpty()) emptyList() else dao.contactsBySourceFingerprints(accountId, chunk)
        }
      val contactIds = previousContacts.map(ContactSnapshotEntity::contactId)
      val previousPhones = contactIds.chunked(SQLITE_SAFE_BIND_CHUNK)
        .flatMap { chunk -> if (chunk.isEmpty()) emptyList() else dao.phonesForContacts(chunk) }
        .groupBy(ContactPhoneEntity::contactId)
      val previousPolicies = contactIds.chunked(SQLITE_SAFE_BIND_CHUNK)
        .flatMap { chunk -> if (chunk.isEmpty()) emptyList() else dao.policiesForContacts(chunk) }
        .associateBy(RecipientPolicyEntity::contactId)
      val contexts = previousContacts.associate { contact ->
        contact.sourceFingerprint to ExistingPeopleContactContext(
          contact,
          previousPhones[contact.contactId].orEmpty(),
          previousPolicies[contact.contactId],
        )
      }
      val prepared = mapper.prepare(contacts, contexts)
      dao.stagePreparedPage(
        generationId,
        pageIndex,
        prepared.contacts,
        prepared.phones,
        prepared.birthdays,
      )
    }

    override suspend fun commit(completion: PeopleSyncCompletion): Boolean = mutex.withLock {
      if (finished) return@withLock false
      val completedAt = clock.nowMillis().takeIf { it >= startedAtMillis } ?: return@withLock false
      if (dao.commitGeneration(
        generationId = generationId,
        nextSyncToken = completion.nextSyncToken,
        parameterFingerprint = completion.parameterFingerprint,
        changedPeople = completion.changedPeople,
        pages = completion.pages,
        completedAtMillis = completedAt,
      ) == null) return@withLock false
      finished = true
      true
    }

    override suspend fun rollback() {
      mutex.withLock {
        if (!finished) {
          dao.rollbackGeneration(generationId)
          finished = true
        }
      }
    }
  }

  private fun Long.subtractExactOrNull(value: Long): Long? =
    if (this < Long.MIN_VALUE + value) null else this - value

  private companion object {
    const val MODE_FULL = "FULL"
    const val MODE_INCREMENTAL = "INCREMENTAL"
    const val STAGING_RETENTION_MILLIS = 15 * 60 * 1_000L
    const val SQLITE_SAFE_BIND_CHUNK = 400
  }
}

internal object StablePrivateId {
  private val hex = "0123456789abcdef".toCharArray()

  fun prefixed(prefix: String, domain: String, vararg values: String): String =
    "${prefix}_${hash(domain, *values)}"

  fun hash(domain: String, vararg values: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    (listOf(domain) + values).forEach { value ->
      val bytes = value.toByteArray(StandardCharsets.UTF_8)
      digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
      digest.update(bytes)
    }
    return digest.digest().toHex()
  }

  private fun ByteArray.toHex(): String = CharArray(size * 2).also { output ->
    forEachIndexed { index, byte ->
      val value = byte.toInt() and 0xff
      output[index * 2] = hex[value ushr 4]
      output[index * 2 + 1] = hex[value and 0x0f]
    }
  }.concatToString()
}
