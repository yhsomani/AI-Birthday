package com.example.domain.service

interface SchedulerService {
    fun scheduleExactSend(eventId: String)
    fun cancelExactSend(eventId: String)
}
