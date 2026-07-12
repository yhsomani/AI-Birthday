package com.yashsomani.birthdayautopilot.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseExchangeMetadataPolicyTest {
  @Test
  fun `invalid metadata removes only a freshly created Firebase user`() {
    assertEquals(
      FirebaseExchangeFailureCleanup.REMOVE_NEW_USER,
      FirebaseExchangeMetadataPolicy.cleanupForInvalidMetadata(isNewUser = true),
    )
    assertEquals(
      FirebaseExchangeFailureCleanup.NONE,
      FirebaseExchangeMetadataPolicy.cleanupForInvalidMetadata(isNewUser = false),
    )
  }
}
