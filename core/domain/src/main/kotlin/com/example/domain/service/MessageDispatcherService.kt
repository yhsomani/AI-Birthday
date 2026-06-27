package com.example.domain.service

import com.example.domain.model.dispatch.MessageDispatchRequest

interface MessageDispatcherService {
    suspend fun dispatch(request: MessageDispatchRequest)
}
