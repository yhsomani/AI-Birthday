package com.yashsomani.birthdayautopilot.readiness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStandbyBucketDiagnosticPolicyTest {
  @Test
  fun `maps every platform standby bucket to a stable content-free code`() {
    val expected = mapOf(
      5 to AppStandbyBucketDiagnostic.EXEMPTED,
      10 to AppStandbyBucketDiagnostic.ACTIVE,
      20 to AppStandbyBucketDiagnostic.WORKING_SET,
      30 to AppStandbyBucketDiagnostic.FREQUENT,
      40 to AppStandbyBucketDiagnostic.RARE,
      45 to AppStandbyBucketDiagnostic.RESTRICTED,
      50 to AppStandbyBucketDiagnostic.NEVER,
    )

    expected.forEach { (bucket, diagnostic) ->
      assertEquals(
        diagnostic,
        AppStandbyBucketDiagnosticPolicy.evaluate(apiLevel = 36) { bucket },
      )
    }
  }

  @Test
  fun `unknown past and future bucket values stay content-free`() {
    listOf(Int.MIN_VALUE, -1, 0, 15, 35, 46, 51, Int.MAX_VALUE).forEach { bucket ->
      assertEquals(
        AppStandbyBucketDiagnostic.UNKNOWN,
        AppStandbyBucketDiagnosticPolicy.evaluate(apiLevel = 36) { bucket },
      )
    }
  }

  @Test
  fun `unsupported API does not attempt a platform read`() {
    var invoked = false

    assertEquals(
      AppStandbyBucketDiagnostic.API_UNSUPPORTED,
      AppStandbyBucketDiagnosticPolicy.evaluate(apiLevel = 27) {
        invoked = true
        10
      },
    )
    assertFalse(invoked)
  }

  @Test
  fun `missing service is reported without throwing`() {
    assertEquals(
      AppStandbyBucketDiagnostic.SERVICE_UNAVAILABLE,
      AppStandbyBucketDiagnosticPolicy.evaluate(apiLevel = 29) { null },
    )
  }

  @Test
  fun `security runtime checked and linkage failures have distinct safe codes`() {
    assertEquals(
      AppStandbyBucketDiagnostic.ACCESS_DENIED,
      AppStandbyBucketDiagnosticPolicy.evaluate(apiLevel = 29) {
        throw SecurityException("private platform detail")
      },
    )
    assertEquals(
      AppStandbyBucketDiagnostic.RUNTIME_UNAVAILABLE,
      AppStandbyBucketDiagnosticPolicy.evaluate(apiLevel = 29) {
        throw IllegalStateException("private platform detail")
      },
    )
    assertEquals(
      AppStandbyBucketDiagnostic.READ_FAILED,
      AppStandbyBucketDiagnosticPolicy.evaluate(apiLevel = 29) {
        throw Exception("private platform detail")
      },
    )
    assertEquals(
      AppStandbyBucketDiagnostic.PLATFORM_UNAVAILABLE,
      AppStandbyBucketDiagnosticPolicy.evaluate(apiLevel = 29) {
        throw LinkageError("private platform detail")
      },
    )
  }

  @Test
  fun `all projected codes are unique bounded and content-free`() {
    val codes = AppStandbyBucketDiagnostic.entries.map { it.wireCode }

    assertEquals(codes.size, codes.distinct().size)
    assertTrue(codes.all { it.length <= 64 })
    assertTrue(codes.all { SAFE_CODE.matches(it) })
    assertTrue(codes.none { it.contains("exception") || it.any(Char::isDigit) })
  }

  private companion object {
    val SAFE_CODE = Regex("[a-z][a-z-]{1,63}")
  }
}
