package com.yashsomani.birthdayautopilot.auth

import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleTaskAwaitTest {
  @Test
  fun `cancelled caller cannot be resumed by a late provider result`() = runTest {
    val source = TaskCompletionSource<String>()
    var continuedAfterAwait = false
    val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
      source.task.awaitSanitized()
      continuedAfterAwait = true
    }

    waiter.cancel()
    source.setResult("credential-material")
    waiter.cancelAndJoin()

    assertTrue(waiter.isCancelled)
    assertFalse(continuedAfterAwait)
  }

  @Test
  fun `provider task that never completes fails within the bounded wait`() = runTest {
    val source = TaskCompletionSource<String>()

    val result = source.task.awaitSanitized(timeoutMillis = 10)

    assertEquals(TaskResult.Failure(TaskFailureCategory.FAILED), result)
  }
}
