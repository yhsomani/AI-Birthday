package com.yashsomani.birthdayautopilot.automation.sms

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsPlatformSubmissionRuntimeInstrumentationTest {
  @Test
  fun exactSingleAndMultipartPlansChooseOnlyTheirMatchingRuntimeCall() {
    val observations = mutableListOf<String>()
    val single = checkNotNull(
      SmsPlatformSubmissionPlan.create(
        exactText = "one part",
        orderedParts = listOf("one part"),
        sentIntents = listOf("sent-0"),
        deliveryIntents = listOf("delivery-0"),
      ),
    )
    SmsPlatformSubmissionDispatcher.dispatch(
      submission = single,
      sendSingle = { text, sent, delivery ->
        observations += "single:$text:$sent:$delivery"
      },
      sendMultipart = { _, _, _ -> observations += "unexpected-multipart" },
    )

    val multipart = checkNotNull(
      SmsPlatformSubmissionPlan.create(
        exactText = "part onepart two",
        orderedParts = listOf("part one", "part two"),
        sentIntents = listOf("sent-0", "sent-1"),
        deliveryIntents = listOf("delivery-0", "delivery-1"),
      ),
    )
    SmsPlatformSubmissionDispatcher.dispatch(
      submission = multipart,
      sendSingle = { _, _, _ -> observations += "unexpected-single" },
      sendMultipart = { parts, sent, delivery ->
        observations += "multipart:${parts.joinToString("|")}:${sent.size}:${delivery.size}"
      },
    )

    assertEquals(
      listOf(
        "single:one part:sent-0:delivery-0",
        "multipart:part one|part two:2:2",
      ),
      observations,
    )
  }

  @Test
  fun invalidCardinalityNeverProducesARuntimeSubmission() {
    assertNull(
      SmsPlatformSubmissionPlan.create(
        exactText = "one part",
        orderedParts = emptyList(),
        sentIntents = emptyList<String>(),
        deliveryIntents = emptyList(),
      ),
    )
    assertNull(
      SmsPlatformSubmissionPlan.create(
        exactText = "one part",
        orderedParts = listOf("one part"),
        sentIntents = listOf("sent-0", "sent-1"),
        deliveryIntents = listOf("delivery-0"),
      ),
    )
  }
}
