package com.example.domain.service

interface SchedulerService {
    fun scheduleExactSend(pendingMessageId: String)
    fun cancelExactSend(pendingMessageId: String)
}
