package com.yashsomani.birthdayautopilot.coordination

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionReceiptTransportTest {
  @Test
  fun `factory freezes exact receipt request and rejects malformed ids`() {
    val request = ready(CoordinationRequestFactory.accountDeletionReceipt(RECEIPT_ID))
    assertEquals(CoordinationEndpointPolicy.ACCOUNT_DELETION_RECEIPT, request.functionName)
    assertEquals(
      mapOf("contractVersion" to 1, "receiptId" to RECEIPT_ID),
      request.callablePayload(),
    )
    assertFalse(request.toString().contains(RECEIPT_ID))
    assertTrue(
      CoordinationRequestFactory.accountDeletionReceipt(RECEIPT_ID.uppercase()) is
        RequestBuildResult.Invalid,
    )
  }

  @Test
  fun `parser accepts only exact ordered receipt evidence`() {
    assertEquals(
      AccountDeletionReceiptOutcome.NotFound,
      CoordinationResponseParser.accountDeletionReceipt(mapOf("kind" to "NOT_FOUND")),
    )
    assertEquals(
      AccountDeletionReceiptOutcome.InProgress(1_000, 1_001),
      CoordinationResponseParser.accountDeletionReceipt(
        mapOf("kind" to "IN_PROGRESS", "requestedAtMs" to 1_000L, "updatedAtMs" to 1_001L),
      ),
    )
    assertEquals(
      AccountDeletionReceiptOutcome.Completed(1_000, 2_000),
      CoordinationResponseParser.accountDeletionReceipt(completedResponse()),
    )

    assertNull(
      CoordinationResponseParser.accountDeletionReceipt(
        completedResponse() + ("serverDataDeleted" to false),
      ),
    )
    assertNull(
      CoordinationResponseParser.accountDeletionReceipt(
        completedResponse() + ("unexpected" to true),
      ),
    )
    assertNull(
      CoordinationResponseParser.accountDeletionReceipt(
        mapOf("kind" to "IN_PROGRESS", "requestedAtMs" to 2_000L, "updatedAtMs" to 1_000L),
      ),
    )
    assertNull(
      CoordinationResponseParser.accountDeletionReceipt(
        mapOf("kind" to "NOT_FOUND", "completedAtMs" to 2_000L),
      ),
    )
  }

  @Test
  fun `receipt dispatch needs app check but never firebase auth`() = runTest {
    var authenticatedPreflightCalls = 0
    var appCheckCalls = 0
    val transport = RecordingTransport(
      CallableTransportResult.Response(completedResponse()),
    )
    val client = FirebaseCoordinationClient(
      preflight = CoordinationPreflight {
        authenticatedPreflightCalls += 1
        NativePreflightResult.NotAuthenticated
      },
      transport = transport,
      appCheckOnlyPreflight = AppCheckOnlyPreflight {
        appCheckCalls += 1
        true
      },
    )
    val result = client.accountDeletionReceipt(
      ready(CoordinationRequestFactory.accountDeletionReceipt(RECEIPT_ID)),
    )
    assertTrue(result is CoordinationCallResult.Authoritative)
    assertEquals(0, authenticatedPreflightCalls)
    assertEquals(1, appCheckCalls)
    assertEquals(1, transport.calls)
    assertEquals(CoordinationEndpointPolicy.ACCOUNT_DELETION_RECEIPT, transport.functionName)
    assertEquals(
      mapOf("contractVersion" to 1, "receiptId" to RECEIPT_ID),
      transport.payload,
    )
  }

  @Test
  fun `missing app check and malformed response never become completion`() = runTest {
    val transport = RecordingTransport(
      CallableTransportResult.Response(completedResponse()),
    )
    val noAppCheck = FirebaseCoordinationClient(
      preflight = CoordinationPreflight { NativePreflightResult.NotAuthenticated },
      transport = transport,
      appCheckOnlyPreflight = AppCheckOnlyPreflight { false },
    )
    assertEquals(
      CoordinationCallResult.Unavailable(CoordinationUnavailableReason.APP_CHECK_UNAVAILABLE),
      noAppCheck.accountDeletionReceipt(
        ready(CoordinationRequestFactory.accountDeletionReceipt(RECEIPT_ID)),
      ),
    )
    assertEquals(0, transport.calls)

    transport.result = CallableTransportResult.Response(
      completedResponse() + ("externalCopiesNotDeleted" to false),
    )
    val malformed = FirebaseCoordinationClient(
      preflight = CoordinationPreflight { NativePreflightResult.NotAuthenticated },
      transport = transport,
      appCheckOnlyPreflight = AppCheckOnlyPreflight { true },
    ).accountDeletionReceipt(
      ready(CoordinationRequestFactory.accountDeletionReceipt(RECEIPT_ID)),
    )
    assertEquals(
      CoordinationCallResult.Unavailable(CoordinationUnavailableReason.INVALID_SERVER_RESPONSE),
      malformed,
    )
    assertEquals(1, transport.calls)
  }

  private class RecordingTransport(
    var result: CallableTransportResult,
  ) : CoordinationCallableTransport {
    var calls = 0
    var functionName: String? = null
    var payload: Map<String, Any>? = null

    override suspend fun call(
      functionName: String,
      payload: Map<String, Any>,
    ): CallableTransportResult {
      calls += 1
      this.functionName = functionName
      this.payload = payload
      return result
    }
  }

  private fun completedResponse(): Map<String, Any> = mapOf(
    "kind" to "COMPLETED",
    "requestedAtMs" to 1_000L,
    "completedAtMs" to 2_000L,
    "appAccountDeleted" to true,
    "serverDataDeleted" to true,
    "externalCopiesNotDeleted" to true,
  )

  private fun <T> ready(result: RequestBuildResult<T>): T =
    (result as RequestBuildResult.Ready).request

  private companion object {
    const val RECEIPT_ID = "abcdefab-cdef-4abc-8def-abcdefabcdef"
  }
}
