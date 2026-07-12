package com.yashsomani.birthdayautopilot.contacts

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class RecipientEnrollmentStatus {
  OFF,
  ENABLED,
  EXCLUDED,
}

class RecipientEnrollmentState private constructor(
  val contactId: String,
  val status: RecipientEnrollmentStatus,
  val materialRevision: String,
  val sourceReady: Boolean,
  val destination: CanonicalPhoneNumber?,
  val approvedMaterialRevision: String?,
  val approvalContentHash: String?,
) {
  fun refreshMaterial(
    newMaterialRevision: String,
    ready: Boolean,
    newDestination: CanonicalPhoneNumber?,
  ): RecipientEnrollmentState {
    require(validOpaque(newMaterialRevision)) { "material-revision-invalid" }
    val changed = newMaterialRevision != materialRevision || newDestination != destination
    return RecipientEnrollmentState(
      contactId = contactId,
      status = status,
      materialRevision = newMaterialRevision,
      sourceReady = ready,
      destination = newDestination,
      approvedMaterialRevision = approvedMaterialRevision.takeUnless { changed },
      approvalContentHash = approvalContentHash.takeUnless { changed },
    )
  }

  fun pause(): RecipientEnrollmentState =
    RecipientEnrollmentState(
      contactId,
      RecipientEnrollmentStatus.OFF,
      materialRevision,
      sourceReady,
      destination,
      approvedMaterialRevision,
      approvalContentHash,
    )

  fun exclude(): RecipientEnrollmentState =
    RecipientEnrollmentState(
      contactId,
      RecipientEnrollmentStatus.EXCLUDED,
      materialRevision,
      sourceReady,
      destination,
      approvedMaterialRevision = null,
      approvalContentHash = null,
    )

  fun removeExclusion(): RecipientEnrollmentState =
    if (status == RecipientEnrollmentStatus.EXCLUDED) {
      RecipientEnrollmentState(
        contactId,
        RecipientEnrollmentStatus.OFF,
        materialRevision,
        sourceReady,
        destination,
        approvedMaterialRevision = null,
        approvalContentHash = null,
      )
    } else {
      this
    }

  internal fun enableFromReviewedApproval(approvalHash: String): RecipientEnrollmentState =
    RecipientEnrollmentState(
      contactId,
      RecipientEnrollmentStatus.ENABLED,
      materialRevision,
      sourceReady,
      destination,
      approvedMaterialRevision = materialRevision,
      approvalContentHash = approvalHash,
    )

  companion object {
    fun imported(
      contactId: String,
      materialRevision: String,
      sourceReady: Boolean,
      destination: CanonicalPhoneNumber?,
    ): RecipientEnrollmentState {
      require(validOpaque(contactId)) { "contact-id-invalid" }
      require(validOpaque(materialRevision)) { "material-revision-invalid" }
      return RecipientEnrollmentState(
        contactId = contactId,
        status = RecipientEnrollmentStatus.OFF,
        materialRevision = materialRevision,
        sourceReady = sourceReady,
        destination = destination,
        approvedMaterialRevision = null,
        approvalContentHash = null,
      )
    }

    private fun validOpaque(value: String): Boolean =
      value.length in 1..300 && value.none { it.isISOControl() || it.isWhitespace() }
  }
}

data class EnrollmentReviewItem(
  val contactId: String,
  val materialRevision: String,
  val approvalContentHash: String,
)

class EnrollmentReviewBatch private constructor(
  val items: List<EnrollmentReviewItem>,
  val recipientCount: Int,
  val reviewDigest: String,
) {
  companion object {
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    fun create(items: Collection<EnrollmentReviewItem>): EnrollmentReviewBatch? {
      val sorted = items.sortedBy(EnrollmentReviewItem::contactId)
      if (sorted.isEmpty() || sorted.map(EnrollmentReviewItem::contactId).distinct().size != sorted.size) return null
      if (sorted.any {
          !validOpaque(it.contactId) ||
            !validOpaque(it.materialRevision) ||
            !SHA256.matches(it.approvalContentHash)
        }
      ) return null

      return EnrollmentReviewBatch(
        items = sorted.toList(),
        recipientCount = sorted.size,
        reviewDigest = EnrollmentDigest.hash(
          sorted.flatMap { item ->
            listOf(item.contactId, item.materialRevision, item.approvalContentHash)
          },
        ),
      )
    }

    private fun validOpaque(value: String): Boolean =
      value.length in 1..300 && value.none { it.isISOControl() || it.isWhitespace() }
  }
}

data class EnrollmentConfirmation(
  val reviewDigest: String,
  val reviewedRecipientCount: Int,
  val foregroundUserConfirmed: Boolean,
)

enum class EnrollmentFailure {
  USER_CONFIRMATION_REQUIRED,
  REVIEW_DIGEST_MISMATCH,
  REVIEW_COUNT_MISMATCH,
  CONTACT_NOT_FOUND,
  MATERIAL_CHANGED,
  CONTACT_NOT_READY,
  RECIPIENT_EXCLUDED,
}

sealed interface EnrollmentResult {
  data class Success(val states: Map<String, RecipientEnrollmentState>) : EnrollmentResult
  data class Rejected(val reason: EnrollmentFailure) : EnrollmentResult
}

object RecipientEnrollment {
  /** Atomically enables exactly the reviewed recipients; any mismatch rejects the whole batch. */
  fun confirm(
    current: Map<String, RecipientEnrollmentState>,
    review: EnrollmentReviewBatch,
    confirmation: EnrollmentConfirmation,
  ): EnrollmentResult {
    if (!confirmation.foregroundUserConfirmed) {
      return EnrollmentResult.Rejected(EnrollmentFailure.USER_CONFIRMATION_REQUIRED)
    }
    if (confirmation.reviewedRecipientCount != review.recipientCount) {
      return EnrollmentResult.Rejected(EnrollmentFailure.REVIEW_COUNT_MISMATCH)
    }
    if (!EnrollmentDigest.matches(review.reviewDigest, confirmation.reviewDigest)) {
      return EnrollmentResult.Rejected(EnrollmentFailure.REVIEW_DIGEST_MISMATCH)
    }

    review.items.forEach { item ->
      val state = current[item.contactId]
        ?: return EnrollmentResult.Rejected(EnrollmentFailure.CONTACT_NOT_FOUND)
      if (state.materialRevision != item.materialRevision) {
        return EnrollmentResult.Rejected(EnrollmentFailure.MATERIAL_CHANGED)
      }
      if (state.contactId != item.contactId) {
        return EnrollmentResult.Rejected(EnrollmentFailure.CONTACT_NOT_FOUND)
      }
      if (!state.sourceReady || state.destination == null) {
        return EnrollmentResult.Rejected(EnrollmentFailure.CONTACT_NOT_READY)
      }
      if (state.status == RecipientEnrollmentStatus.EXCLUDED) {
        return EnrollmentResult.Rejected(EnrollmentFailure.RECIPIENT_EXCLUDED)
      }
    }

    val updated = current.toMutableMap()
    review.items.forEach { item ->
      updated[item.contactId] = current.getValue(item.contactId)
        .enableFromReviewedApproval(item.approvalContentHash)
    }
    return EnrollmentResult.Success(updated.toMap())
  }
}

class RecipientBlocklist private constructor(
  private val allRecipientsBlocked: Boolean,
  blockedContactIds: Set<String>,
  blockedDestinations: Set<CanonicalPhoneNumber>,
) {
  private val contacts = blockedContactIds.toSet()
  private val destinations = blockedDestinations.toSet()

  fun blocks(state: RecipientEnrollmentState): Boolean =
    allRecipientsBlocked ||
      state.contactId in contacts ||
      state.destination?.let(destinations::contains) == true

  companion object {
    fun create(
      allRecipientsBlocked: Boolean = false,
      blockedContactIds: Set<String> = emptySet(),
      blockedDestinations: Set<CanonicalPhoneNumber> = emptySet(),
    ): RecipientBlocklist = RecipientBlocklist(
      allRecipientsBlocked,
      blockedContactIds,
      blockedDestinations,
    )
  }
}

enum class RecipientSchedulingBlocker {
  NOT_ENABLED,
  EXCLUDED,
  SOURCE_NOT_READY,
  DESTINATION_MISSING,
  APPROVAL_MISSING_OR_STALE,
  BLOCKLISTED,
}

object RecipientSchedulingEligibility {
  fun blockers(
    state: RecipientEnrollmentState,
    blocklist: RecipientBlocklist,
    approvalStillValid: Boolean,
  ): Set<RecipientSchedulingBlocker> = buildSet {
    when (state.status) {
      RecipientEnrollmentStatus.OFF -> add(RecipientSchedulingBlocker.NOT_ENABLED)
      RecipientEnrollmentStatus.EXCLUDED -> add(RecipientSchedulingBlocker.EXCLUDED)
      RecipientEnrollmentStatus.ENABLED -> Unit
    }
    if (!state.sourceReady) add(RecipientSchedulingBlocker.SOURCE_NOT_READY)
    if (state.destination == null) add(RecipientSchedulingBlocker.DESTINATION_MISSING)
    if (
      state.approvedMaterialRevision != state.materialRevision ||
      state.approvalContentHash == null ||
      !approvalStillValid
    ) {
      add(RecipientSchedulingBlocker.APPROVAL_MISSING_OR_STALE)
    }
    if (blocklist.blocks(state)) add(RecipientSchedulingBlocker.BLOCKLISTED)
  }
}

private object EnrollmentDigest {
  private const val DOMAIN = "BirthdayAutopilot.EnrollmentReview.v1"
  private val HEX = "0123456789abcdef".toCharArray()

  fun hash(values: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    (listOf(DOMAIN) + values).forEach { value ->
      val bytes = value.toByteArray(StandardCharsets.UTF_8)
      digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
      digest.update(bytes)
    }
    val bytes = digest.digest()
    return CharArray(bytes.size * 2).also { output ->
      bytes.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        output[index * 2] = HEX[value ushr 4]
        output[index * 2 + 1] = HEX[value and 0x0f]
      }
    }.concatToString()
  }

  fun matches(expected: String, actual: String): Boolean =
    actual.matches(Regex("^[0-9a-f]{64}$")) &&
      MessageDigest.isEqual(
        expected.toByteArray(StandardCharsets.US_ASCII),
        actual.toByteArray(StandardCharsets.US_ASCII),
      )
}
