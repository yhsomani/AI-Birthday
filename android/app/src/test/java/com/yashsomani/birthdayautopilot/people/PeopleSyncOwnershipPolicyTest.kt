package com.yashsomani.birthdayautopilot.people

import com.yashsomani.birthdayautopilot.core.model.AccountMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeopleSyncOwnershipPolicyTest {
  @Test
  fun `standby transfer and deletion block contact reads`() {
    assertEquals(
      PeopleSyncOwnershipBlock.ACTIVE_SENDER_OTHER_DEVICE,
      PeopleSyncOwnershipPolicy.blockedReason(AccountMode.STANDBY),
    )
    assertEquals(
      PeopleSyncOwnershipBlock.TRANSFER_PENDING,
      PeopleSyncOwnershipPolicy.blockedReason(AccountMode.TRANSFER_PENDING),
    )
    assertEquals(
      PeopleSyncOwnershipBlock.ACCOUNT_DELETING,
      PeopleSyncOwnershipPolicy.blockedReason(AccountMode.DELETING),
    )
    assertEquals(
      PeopleSyncOwnershipBlock.OWNERSHIP_UNVERIFIED,
      PeopleSyncOwnershipPolicy.blockedReason(null),
    )
  }

  @Test
  fun `current sender modes can synchronize`() {
    assertNull(PeopleSyncOwnershipPolicy.blockedReason(AccountMode.TEST_ONLY))
    assertNull(PeopleSyncOwnershipPolicy.blockedReason(AccountMode.PAUSED_REPAIR))
    assertNull(PeopleSyncOwnershipPolicy.blockedReason(AccountMode.AUTOMATION_ACTIVE))
  }
}
