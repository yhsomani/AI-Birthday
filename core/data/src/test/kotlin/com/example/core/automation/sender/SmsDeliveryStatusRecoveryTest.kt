package com.example.core.automation.sender

import com.example.core.db.dao.SentMessageDao
import com.example.domain.model.MessageDeliveryStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, manifest = Config.NONE)
class SmsDeliveryStatusRecoveryTest {
    private val sentMessageDao: SentMessageDao = mockk(relaxed = true)

    @Test
    fun recover_marksStalePendingSmsDeliveryRowsUnknown() = runTest {
        coEvery {
            sentMessageDao.markStalePendingSmsDeliveryStatus(any(), any())
        } returns 2

        val recovered = SmsDeliveryStatusRecovery.recover(
            sentMessageDao = sentMessageDao,
            nowMs = 2_000L,
            stalePendingDeliveryMs = 500L,
        )

        assertEquals(2, recovered)
        coVerify {
            sentMessageDao.markStalePendingSmsDeliveryStatus(
                cutoffMs = 1_500L,
                status = MessageDeliveryStatus.UNKNOWN.raw,
            )
        }
    }

    @Test
    fun recover_clampsNegativeStaleWindowToNow() = runTest {
        coEvery {
            sentMessageDao.markStalePendingSmsDeliveryStatus(any(), any())
        } returns 0

        SmsDeliveryStatusRecovery.recover(
            sentMessageDao = sentMessageDao,
            nowMs = 2_000L,
            stalePendingDeliveryMs = -1L,
        )

        coVerify {
            sentMessageDao.markStalePendingSmsDeliveryStatus(
                cutoffMs = 2_000L,
                status = MessageDeliveryStatus.UNKNOWN.raw,
            )
        }
    }
}
