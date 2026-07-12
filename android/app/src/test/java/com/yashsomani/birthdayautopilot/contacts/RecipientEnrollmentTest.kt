package com.yashsomani.birthdayautopilot.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientEnrollmentTest {
  private val phoneA = CanonicalPhoneNumber.parse("+919876543210")!!
  private val phoneB = CanonicalPhoneNumber.parse("+919999999999")!!
  private val approvalA = "a".repeat(64)
  private val approvalB = "b".repeat(64)

  @Test
  fun `every imported contact is Off and cannot schedule without a foreground reviewed event`() {
    val imported = RecipientEnrollmentState.imported("a", "revision-1", true, phoneA)
    assertEquals(RecipientEnrollmentStatus.OFF, imported.status)
    assertTrue(
      RecipientSchedulingBlocker.NOT_ENABLED in
        RecipientSchedulingEligibility.blockers(imported, RecipientBlocklist.create(), true),
    )
    assertTrue(
      RecipientSchedulingBlocker.APPROVAL_MISSING_OR_STALE in
        RecipientSchedulingEligibility.blockers(imported, RecipientBlocklist.create(), true),
    )

    val review = review(EnrollmentReviewItem("a", "revision-1", approvalA))
    val denied = RecipientEnrollment.confirm(
      mapOf("a" to imported),
      review,
      EnrollmentConfirmation(review.reviewDigest, 1, foregroundUserConfirmed = false),
    )
    assertEquals(
      EnrollmentResult.Rejected(EnrollmentFailure.USER_CONFIRMATION_REQUIRED),
      denied,
    )
  }

  @Test
  fun `exact reviewed approval enables only the named recipient`() {
    val current = mapOf(
      "a" to RecipientEnrollmentState.imported("a", "revision-1", true, phoneA),
      "b" to RecipientEnrollmentState.imported("b", "revision-1", true, phoneB),
    )
    val review = review(EnrollmentReviewItem("a", "revision-1", approvalA))
    val result = RecipientEnrollment.confirm(current, review, confirmation(review)) as EnrollmentResult.Success

    assertEquals(RecipientEnrollmentStatus.ENABLED, result.states.getValue("a").status)
    assertEquals(approvalA, result.states.getValue("a").approvalContentHash)
    assertEquals(RecipientEnrollmentStatus.OFF, result.states.getValue("b").status)
    assertTrue(
      RecipientSchedulingEligibility.blockers(
        result.states.getValue("a"),
        RecipientBlocklist.create(),
        approvalStillValid = true,
      ).isEmpty(),
    )
  }

  @Test
  fun `bulk enablement is atomic when any recipient changed or is not ready`() {
    val current = mapOf(
      "a" to RecipientEnrollmentState.imported("a", "revision-2", true, phoneA),
      "b" to RecipientEnrollmentState.imported("b", "revision-1", false, phoneB),
    )
    val stale = review(
      EnrollmentReviewItem("a", "revision-1", approvalA),
      EnrollmentReviewItem("b", "revision-1", approvalB),
    )
    assertEquals(
      EnrollmentResult.Rejected(EnrollmentFailure.MATERIAL_CHANGED),
      RecipientEnrollment.confirm(current, stale, confirmation(stale)),
    )
    assertTrue(current.values.all { it.status == RecipientEnrollmentStatus.OFF })

    val currentRevision = review(
      EnrollmentReviewItem("a", "revision-2", approvalA),
      EnrollmentReviewItem("b", "revision-1", approvalB),
    )
    assertEquals(
      EnrollmentResult.Rejected(EnrollmentFailure.CONTACT_NOT_READY),
      RecipientEnrollment.confirm(current, currentRevision, confirmation(currentRevision)),
    )
    assertTrue(current.values.all { it.status == RecipientEnrollmentStatus.OFF })
  }

  @Test
  fun `review count digest and duplicate IDs fail closed`() {
    val state = RecipientEnrollmentState.imported("a", "revision-1", true, phoneA)
    val review = review(EnrollmentReviewItem("a", "revision-1", approvalA))
    val wrongCount = RecipientEnrollment.confirm(
      mapOf("a" to state),
      review,
      confirmation(review).copy(reviewedRecipientCount = 2),
    )
    assertEquals(EnrollmentResult.Rejected(EnrollmentFailure.REVIEW_COUNT_MISMATCH), wrongCount)

    val wrongDigest = RecipientEnrollment.confirm(
      mapOf("a" to state),
      review,
      confirmation(review).copy(reviewDigest = "f".repeat(64)),
    )
    assertEquals(EnrollmentResult.Rejected(EnrollmentFailure.REVIEW_DIGEST_MISMATCH), wrongDigest)

    assertEquals(
      null,
      EnrollmentReviewBatch.create(
        listOf(
          EnrollmentReviewItem("a", "revision-1", approvalA),
          EnrollmentReviewItem("a", "revision-1", approvalA),
        ),
      ),
    )
  }

  @Test
  fun `material change preserves desired enrollment but invalidates approval until reviewed again`() {
    val enabled = enabledState()
    val changed = enabled.refreshMaterial("revision-2", ready = true, newDestination = phoneB)

    assertEquals(RecipientEnrollmentStatus.ENABLED, changed.status)
    assertEquals(null, changed.approvalContentHash)
    assertTrue(
      RecipientSchedulingBlocker.APPROVAL_MISSING_OR_STALE in
        RecipientSchedulingEligibility.blockers(changed, RecipientBlocklist.create(), true),
    )

    val review = review(EnrollmentReviewItem("a", "revision-2", approvalB))
    val reapproved = RecipientEnrollment.confirm(mapOf("a" to changed), review, confirmation(review))
      as EnrollmentResult.Success
    assertTrue(
      RecipientSchedulingEligibility.blockers(
        reapproved.states.getValue("a"),
        RecipientBlocklist.create(),
        true,
      ).isEmpty(),
    )
  }

  @Test
  fun `destination change invalidates approval even if an upstream revision is accidentally reused`() {
    val changed = enabledState().refreshMaterial("revision-1", ready = true, newDestination = phoneB)
    assertEquals(null, changed.approvalContentHash)
    assertTrue(
      RecipientSchedulingBlocker.APPROVAL_MISSING_OR_STALE in
        RecipientSchedulingEligibility.blockers(changed, RecipientBlocklist.create(), true),
    )
  }

  @Test
  fun `pause exclusion and every master blocklist form dominate schedules`() {
    val enabled = enabledState()
    val blocklists = listOf(
      RecipientBlocklist.create(allRecipientsBlocked = true),
      RecipientBlocklist.create(blockedContactIds = setOf("a")),
      RecipientBlocklist.create(blockedDestinations = setOf(phoneA)),
    )
    blocklists.forEach { blocklist ->
      assertTrue(
        RecipientSchedulingBlocker.BLOCKLISTED in
          RecipientSchedulingEligibility.blockers(enabled, blocklist, true),
      )
    }

    val paused = enabled.pause()
    assertTrue(
      RecipientSchedulingBlocker.NOT_ENABLED in
        RecipientSchedulingEligibility.blockers(paused, RecipientBlocklist.create(), true),
    )
    val excluded = enabled.exclude()
    assertTrue(
      RecipientSchedulingBlocker.EXCLUDED in
        RecipientSchedulingEligibility.blockers(excluded, RecipientBlocklist.create(), true),
    )
    assertEquals(null, excluded.approvalContentHash)

    val excludedReview = review(EnrollmentReviewItem("a", "revision-1", approvalA))
    assertEquals(
      EnrollmentResult.Rejected(EnrollmentFailure.RECIPIENT_EXCLUDED),
      RecipientEnrollment.confirm(mapOf("a" to excluded), excludedReview, confirmation(excludedReview)),
    )
    assertEquals(RecipientEnrollmentStatus.OFF, excluded.removeExclusion().status)
  }

  @Test
  fun `blocklist defensively copies mutable caller sets`() {
    val contacts = mutableSetOf("a")
    val blocklist = RecipientBlocklist.create(blockedContactIds = contacts)
    contacts.clear()
    assertTrue(blocklist.blocks(enabledState()))
  }

  private fun enabledState(): RecipientEnrollmentState {
    val imported = RecipientEnrollmentState.imported("a", "revision-1", true, phoneA)
    val review = review(EnrollmentReviewItem("a", "revision-1", approvalA))
    val result = RecipientEnrollment.confirm(mapOf("a" to imported), review, confirmation(review))
      as EnrollmentResult.Success
    return result.states.getValue("a")
  }

  private fun review(vararg items: EnrollmentReviewItem): EnrollmentReviewBatch =
    requireNotNull(EnrollmentReviewBatch.create(items.toList()))

  private fun confirmation(review: EnrollmentReviewBatch) = EnrollmentConfirmation(
    reviewDigest = review.reviewDigest,
    reviewedRecipientCount = review.recipientCount,
    foregroundUserConfirmed = true,
  )
}
