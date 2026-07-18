package com.yashsomani.birthdayautopilot.automation.sms

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.telephony.SmsManager
import java.util.ArrayList

internal sealed interface SmsPlatformSubmission<out Callback> {
  data class Single<Callback>(
    val exactText: String,
    val sentIntent: Callback,
    val deliveryIntent: Callback,
  ) : SmsPlatformSubmission<Callback>

  data class Multipart<Callback>(
    val orderedParts: List<String>,
    val sentIntents: List<Callback>,
    val deliveryIntents: List<Callback>,
  ) : SmsPlatformSubmission<Callback>
}

/**
 * Binds the platform API shape to the already-verified segment plan. A single-part plan retains
 * the exact immutable text and exactly one callback of each kind; only a two-part plan may reach
 * Android's multipart API. Invalid callback cardinality or an empty/over-cap plan is rejected
 * before either platform call can be selected.
 */
internal object SmsPlatformSubmissionPlan {
  fun <Callback> create(
    exactText: String,
    orderedParts: List<String>,
    sentIntents: List<Callback>,
    deliveryIntents: List<Callback>,
  ): SmsPlatformSubmission<Callback>? {
    if (
      exactText.isEmpty() ||
      orderedParts.size !in 1..2 ||
      orderedParts.any(String::isEmpty) ||
      sentIntents.size != orderedParts.size ||
      deliveryIntents.size != orderedParts.size ||
      orderedParts.joinToString(separator = "") != exactText
    ) return null

    return if (orderedParts.size == 1) {
      SmsPlatformSubmission.Single(
        exactText = exactText,
        sentIntent = sentIntents.single(),
        deliveryIntent = deliveryIntents.single(),
      )
    } else {
      SmsPlatformSubmission.Multipart(
        orderedParts = orderedParts.toList(),
        sentIntents = sentIntents.toList(),
        deliveryIntents = deliveryIntents.toList(),
      )
    }
  }
}

/** Pure dispatch seam so the single/multipart platform selection is executable without a carrier. */
internal object SmsPlatformSubmissionDispatcher {
  fun <Callback> dispatch(
    submission: SmsPlatformSubmission<Callback>,
    sendSingle: (String, Callback, Callback) -> Unit,
    sendMultipart: (List<String>, List<Callback>, List<Callback>) -> Unit,
  ) {
    when (submission) {
      is SmsPlatformSubmission.Single -> sendSingle(
        submission.exactText,
        submission.sentIntent,
        submission.deliveryIntent,
      )
      is SmsPlatformSubmission.Multipart -> sendMultipart(
        submission.orderedParts,
        submission.sentIntents,
        submission.deliveryIntents,
      )
    }
  }
}

/**
 * Narrow adapter around the one Android API that can submit an SMS. The gateway remains the only
 * owner of the durable barrier, final gate and callback allocation; injecting this adapter cannot
 * bypass any of them.
 */
internal fun interface SmsPlatformSubmitter {
  fun submit(
    manager: SmsManager,
    destination: String,
    exactText: String,
    orderedParts: List<String>,
    sentIntents: List<PendingIntent>,
    deliveryIntents: List<PendingIntent>,
  )
}

internal object AndroidSmsPlatformSubmitter : SmsPlatformSubmitter {
  @SuppressLint("MissingPermission")
  override fun submit(
    manager: SmsManager,
    destination: String,
    exactText: String,
    orderedParts: List<String>,
    sentIntents: List<PendingIntent>,
    deliveryIntents: List<PendingIntent>,
  ) {
    val submission = SmsPlatformSubmissionPlan.create(
      exactText = exactText,
      orderedParts = orderedParts,
      sentIntents = sentIntents,
      deliveryIntents = deliveryIntents,
    ) ?: throw IllegalArgumentException("invalid SMS platform submission plan")

    SmsPlatformSubmissionDispatcher.dispatch(
      submission = submission,
      sendSingle = { text, sentIntent, deliveryIntent ->
        manager.sendTextMessage(
          destination,
          null,
          text,
          sentIntent,
          deliveryIntent,
        )
      },
      sendMultipart = { parts, sentCallbacks, deliveryCallbacks ->
        manager.sendMultipartTextMessage(
          destination,
          null,
          ArrayList(parts),
          ArrayList(sentCallbacks),
          ArrayList(deliveryCallbacks),
        )
      },
    )
  }
}

internal sealed interface SmsPlatformBoundaryResult {
  data object Accepted : SmsPlatformBoundaryResult
  data object NotCalled : SmsPlatformBoundaryResult
  data object OutcomeUnknown : SmsPlatformBoundaryResult
}

/**
 * Last in-memory decision immediately surrounding the platform call. A false final gate is proven
 * no-call; once the call is entered, every throwable platform outcome is conservatively unknown.
 */
internal object SmsPlatformSubmissionBoundary {
  fun execute(
    finalGateOpen: () -> Boolean,
    submit: () -> Unit,
  ): SmsPlatformBoundaryResult {
    val open = try {
      finalGateOpen()
    } catch (_: RuntimeException) {
      return SmsPlatformBoundaryResult.NotCalled
    } catch (_: LinkageError) {
      return SmsPlatformBoundaryResult.NotCalled
    }
    if (!open) return SmsPlatformBoundaryResult.NotCalled
    return try {
      submit()
      SmsPlatformBoundaryResult.Accepted
    } catch (_: RuntimeException) {
      SmsPlatformBoundaryResult.OutcomeUnknown
    } catch (_: LinkageError) {
      SmsPlatformBoundaryResult.OutcomeUnknown
    }
  }
}
