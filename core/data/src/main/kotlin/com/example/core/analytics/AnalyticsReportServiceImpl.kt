package com.example.core.analytics

import com.example.core.db.entities.ActivityLogEntity
import com.example.domain.model.ActivityLogType
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.AnalyticsReport
import com.example.domain.service.AnalyticsReportService
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsReportServiceImpl @Inject constructor(
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val messageRepository: MessageRepository,
    private val activityLogRepository: ActivityLogRepository,
) : AnalyticsReportService {

    override suspend fun buildRelationshipReport(): AnalyticsReport {
        val generatedAt = System.currentTimeMillis()
        val contacts = contactRepository.getAllSync()
        val relationshipCounts = contactRepository.countByRelationshipType().first()
        val totalSent = messageRepository.countAllSent().first()
        val pendingApprovals = messageRepository.countPending().first()
        val upcomingEvents = eventRepository.getUpcoming(30)
        val sentThisYear = messageRepository.getSentSinceYearStart(yearStartMs())

        val healthyCount = contacts.count { it.healthScore >= 70 }
        val attentionCount = contacts.count { it.healthScore in 30..69 }
        val atRiskCount = contacts.count { it.healthScore < 30 }

        val content = buildString {
            appendLine("section,metric,value")
            appendCsv("summary", "generated_at", timestampFormat.format(Date(generatedAt)))
            appendCsv("summary", "total_contacts", contacts.size.toString())
            appendCsv("summary", "total_wishes_sent", totalSent.toString())
            appendCsv("summary", "pending_approvals", pendingApprovals.toString())
            appendCsv("summary", "upcoming_events_30_days", upcomingEvents.size.toString())
            appendCsv("health", "healthy_70_plus", healthyCount.toString())
            appendCsv("health", "needs_attention_30_to_69", attentionCount.toString())
            appendCsv("health", "at_risk_under_30", atRiskCount.toString())
            appendCsv("messages", "sent_this_year", sentThisYear.size.toString())
            relationshipCounts.sortedBy { it.relationshipType }.forEach { item ->
                appendCsv("relationship_type", item.relationshipType, item.count.toString())
            }
        }

        activityLogRepository.record(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                type = ActivityLogType.ANALYTICS.raw,
                title = "Analytics report exported",
                detail = "Relationship report generated for sharing.",
                createdAtMs = generatedAt,
            )
        )

        return AnalyticsReport(
            fileName = "relateai-relationship-report-${fileNameFormat.format(Date(generatedAt))}.csv",
            mimeType = "text/csv",
            content = content,
        )
    }

    private fun StringBuilder.appendCsv(section: String, metric: String, value: String) {
        append(escapeCsv(section))
        append(',')
        append(escapeCsv(metric))
        append(',')
        append(escapeCsv(value))
        appendLine()
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun yearStartMs(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private companion object {
        val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val fileNameFormat = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
    }
}
