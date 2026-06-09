package com.example.core.automation.scheduler

import android.content.Context
import com.example.domain.service.SchedulerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchedulerServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SchedulerService {

    override fun scheduleExactSend(pendingMessageId: String) {
        DailyScheduler.scheduleExactSend(context, pendingMessageId)
    }

    override fun cancelExactSend(pendingMessageId: String) {
        DailyScheduler.cancelExactSend(context, pendingMessageId)
    }
}
