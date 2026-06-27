package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.R
import com.example.core.db.AppDatabase
import com.example.domain.contact.toHeader
import com.example.domain.event.toOccasions
import com.example.domain.model.MessageStatus
import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

class BirthdayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val occasions = db.eventDao().getAll().first().toOccasions()
                val contacts = db.contactDao().getAll().first().map { it.toHeader() }
                val pendingApprovals = db.pendingMessageDao().getAllSync()
                    .count { MessageStatus.fromRaw(it.status) == MessageStatus.PENDING }

                val today = Calendar.getInstance()
                val summary = buildBirthdayWidgetSummary(
                    occasions = occasions,
                    contacts = contacts,
                    pendingApprovals = pendingApprovals,
                    todayDay = today.get(Calendar.DAY_OF_MONTH),
                    todayMonth = today.get(Calendar.MONTH) + 1,
                    unknownContactLabel = context.getString(R.string.widget_unknown_contact),
                )

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_birthday)
                    if (summary.todayBirthdayCount == 0) {
                        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_no_birthdays_today))
                    } else {
                        views.setTextViewText(
                            R.id.widget_title,
                            context.getString(R.string.widget_birthdays_today, summary.todayBirthdayCount),
                        )
                    }
                    val subtitleParts = buildList {
                        if (summary.todayBirthdayNames.isNotEmpty()) {
                            add(summary.todayBirthdayNames.joinToString(", "))
                        }
                        if (summary.nextEvents.isNotEmpty()) {
                            add(context.getString(R.string.widget_next_events, summary.nextEvents.joinToString(" | ")))
                        }
                        if (summary.pendingApprovals > 0) {
                            add(context.getString(R.string.widget_pending_approvals, summary.pendingApprovals))
                        }
                    }
                    views.setTextViewText(
                        R.id.widget_subtitle,
                        subtitleParts.filter { it.isNotBlank() }
                            .joinToString("\n")
                            .ifBlank { context.getString(R.string.widget_enjoy_day) },
                    )
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                android.util.Log.e("BirthdayWidgetProvider", "Failed to update widget", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal data class BirthdayWidgetSummary(
    val todayBirthdayCount: Int,
    val todayBirthdayNames: List<String>,
    val nextEvents: List<String>,
    val pendingApprovals: Int,
)

internal fun buildBirthdayWidgetSummary(
    occasions: List<Occasion>,
    contacts: List<ContactHeader>,
    pendingApprovals: Int,
    todayDay: Int,
    todayMonth: Int,
    unknownContactLabel: String,
): BirthdayWidgetSummary {
    val contactNames = contacts.associate { it.id to it.displayName }
    val todayBirthdays = occasions.filter { occasion ->
        occasion.type == OccasionType.BIRTHDAY &&
            occasion.date.dayOfMonth == todayDay &&
            occasion.date.month == todayMonth
    }
    val nextEvents = occasions
        .filter { it.isActive }
        .sortedBy { it.nextOccurrenceMs }
        .take(3)
        .map { occasion ->
            val name = contactNames[occasion.contactId] ?: unknownContactLabel
            val label = occasion.label ?: occasion.type.raw.replace("_", " ")
            "$name: $label"
        }

    return BirthdayWidgetSummary(
        todayBirthdayCount = todayBirthdays.size,
        todayBirthdayNames = todayBirthdays.mapNotNull { contactNames[it.contactId] },
        nextEvents = nextEvents,
        pendingApprovals = pendingApprovals,
    )
}
