package com.example.domain.model.message

data class MessageGenerationHistory(
    val previousWishes: List<String> = emptyList(),
    val routeHistory: List<DeliveryRouteHistoryRecord> = emptyList(),
)
