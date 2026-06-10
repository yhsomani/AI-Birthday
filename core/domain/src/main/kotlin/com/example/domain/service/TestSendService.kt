package com.example.domain.service

interface TestSendService {
    suspend fun sendEmailToSelf(messageText: String)
}
