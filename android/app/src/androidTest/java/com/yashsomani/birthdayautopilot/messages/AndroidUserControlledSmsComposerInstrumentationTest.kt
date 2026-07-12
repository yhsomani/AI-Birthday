package com.yashsomani.birthdayautopilot.messages

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidUserControlledSmsComposerInstrumentationTest {
  @Test
  fun intentIsRecipientScopedSendToWithOnlyTheReviewedBodyPrefilled() {
    val draft = UserControlledSmsComposerDraft(
      canonicalRecipient = "+919876543210",
      exactApprovedBody = "Happy birthday, Ada! 🎂",
    )

    val intent = requireNotNull(SystemSmsComposerIntentPolicy.create(draft))

    assertEquals(Intent.ACTION_SENDTO, intent.action)
    assertEquals("smsto", intent.data?.scheme)
    assertEquals(draft.canonicalRecipient, intent.data?.schemeSpecificPart)
    assertEquals(draft.exactApprovedBody, intent.getStringExtra("sms_body"))
    assertNull(intent.type)
    assertNull(intent.clipData)
    assertFalse(intent.action == Intent.ACTION_SEND)
    assertFalse(intent.action == Intent.ACTION_SEND_MULTIPLE)
  }

  @Test
  fun malformedRecipientOrUnsafeBodyCannotCreateAnIntent() {
    val invalidDrafts = listOf(
      UserControlledSmsComposerDraft("9876543210", "Happy birthday!"),
      UserControlledSmsComposerDraft("+019876543210", "Happy birthday!"),
      UserControlledSmsComposerDraft("+919876543210", ""),
      UserControlledSmsComposerDraft("+919876543210", "Hello\u202e"),
      UserControlledSmsComposerDraft("+919876543210", "x".repeat(1_001)),
    )

    invalidDrafts.forEach { draft ->
      assertNull(SystemSmsComposerIntentPolicy.create(draft))
      assertFalse(SystemSmsComposerIntentPolicy.validDraft(draft))
    }
  }

  @Test
  fun approvedEmojiJoinerSequenceIsPreservedExactly() {
    val body = "Happy birthday! 👨‍👩‍👧‍👦"
    val intent = requireNotNull(
      SystemSmsComposerIntentPolicy.create(
        UserControlledSmsComposerDraft("+919876543210", body),
      ),
    )

    assertEquals(body, intent.getStringExtra("sms_body"))
  }

  @Test
  fun absentForegroundActivityFailsClosedBeforeHandlerOrLaunch() {
    var boundaryCalls = 0
    val composer = AndroidUserControlledSmsComposer(
      foregroundBoundary = {
        boundaryCalls += 1
        null
      },
    )
    val draft = UserControlledSmsComposerDraft("+919876543210", "Happy birthday!")

    assertFalse(composer.canOpen(draft))
    assertEquals(UserControlledSmsComposerOpenResult.KNOWN_FAILURE, composer.open(draft))
    assertEquals(2, boundaryCalls)
    assertTrue(SystemSmsComposerIntentPolicy.validDraft(draft))
  }
}
