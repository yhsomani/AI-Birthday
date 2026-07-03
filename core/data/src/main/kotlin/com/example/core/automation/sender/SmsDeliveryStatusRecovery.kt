package com.example.core.automation.sender

import android.content.Context
import com.example.core.db.AppDatabase
import com.example.core.db.dao.SentMessageDao
import com.example.core.resilience.StructuredLogger
import com.example.domain.automation.SmsDeliveryStatusRecoveryPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object SmsDeliveryStatusRecovery {
    private const val TAG = "SmsDeliveryStatusRecovery"

    fun recoverAsync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            recover(context)
        }
    }

    suspend fun recover(context: Context) {
        val db = AppDatabase.getInstance(context)
        recover(db.sentMessageDao())
    }

    suspend fun recover(
        sentMessageDao: SentMessageDao,
        nowMs: Long = System.currentTimeMillis(),
        stalePendingDeliveryMs: Long = SmsDeliveryStatusRecoveryPolicy.DEFAULT_STALE_PENDING_DELIVERY_MS,
    ): Int {
        val decision = SmsDeliveryStatusRecoveryPolicy.stalePendingDeliveryDecision(
            nowMs = nowMs,
            stalePendingDeliveryMs = stalePendingDeliveryMs,
        )
        val recovered = sentMessageDao.markStalePendingSmsDeliveryStatus(
            cutoffMs = decision.cutoffMs,
            status = decision.recoveredStatus.raw,
        )
        if (recovered > 0) {
            StructuredLogger.w(
                TAG,
                "Marked stale SMS pending-delivery records as UNKNOWN",
                extras = mapOf("count" to recovered.toString()),
            )
        }
        return recovered
    }
}
