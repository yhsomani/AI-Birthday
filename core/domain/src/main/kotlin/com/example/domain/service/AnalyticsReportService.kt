package com.example.domain.service

data class AnalyticsReport(
    val fileName: String,
    val mimeType: String,
    val content: String,
)

interface AnalyticsReportService {
    suspend fun buildRelationshipReport(): AnalyticsReport
}
