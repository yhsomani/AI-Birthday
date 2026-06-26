package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.R
import com.example.core.db.AppDatabase
import com.example.domain.model.EventType
import com.example.domain.model.MessageStatus
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
                val events = db.eventDao().getAll().first()
                val contacts = db.contactDao().getAll().first()
                val pendingApprovals = db.pendingMessageDao().getAllSync()
                    .count { MessageStatus.fromRaw(it.status) == MessageStatus.PENDING }

                val today = Calendar.getInstance()
                val todayDay = today.get(Calendar.DAY_OF_MONTH)
                val todayMonth = today.get(Calendar.MONTH) + 1

                val todayBirthdays = events.filter {
                    EventType.fromRaw(it.type) == EventType.BIRTHDAY &&
                        it.dayOfMonth == todayDay &&
                        it.month == todayMonth
                }
                val contactNames = contacts.associate { it.id to it.name }
                val nextEvents = events
                    .filter { it.isActive }
                    .sortedBy { it.nextOccurrenceMs }
                    .take(3)
                    .map { event ->
                        val name = contactNames[event.contactId] ?: context.getString(R.string.widget_unknown_contact)
                        val label = event.label ?: event.type.replace("_", " ")
                        "$name: $label"
                    }

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_birthday)
                    if (todayBirthdays.isEmpty()) {
                        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_no_birthdays_today))
                    } else {
                        val names = todayBirthdays.mapNotNull { bday ->
                            contactNames[bday.contactId] ?: context.getString(R.string.widget_unknown_contact)
                        }.joinToString(", ")

                        views.setTextViewText(
                            R.id.widget_title,
                            context.getString(R.string.widget_birthdays_today, todayBirthdays.size),
                        )
                    }
                    val subtitleParts = buildList {
                        if (todayBirthdays.isNotEmpty()) {
                            add(todayBirthdays.mapNotNull { contactNames[it.contactId] }.joinToString(", "))
                        }
                        if (nextEvents.isNotEmpty()) {
                            add(context.getString(R.string.widget_next_events, nextEvents.joinToString(" | ")))
                        }
                        if (pendingApprovals > 0) {
                            add(context.getString(R.string.widget_pending_approvals, pendingApprovals))
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
