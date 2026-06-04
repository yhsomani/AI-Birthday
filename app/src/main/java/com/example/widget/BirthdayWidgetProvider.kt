package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.R
import com.example.core.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

                val today = Calendar.getInstance()
                val todayDay = today.get(Calendar.DAY_OF_MONTH)
                val todayMonth = today.get(Calendar.MONTH) + 1

                val todayBirthdays = events.filter {
                    it.type == "BIRTHDAY" && it.dayOfMonth == todayDay && it.month == todayMonth
                }

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_birthday)
                    if (todayBirthdays.isEmpty()) {
                        views.setTextViewText(R.id.widget_title, "No birthdays today")
                        views.setTextViewText(R.id.widget_subtitle, "Enjoy your day!")
                    } else {
                        val names = todayBirthdays.mapNotNull { bday ->
                            contacts.find { it.id == bday.contactId }?.name ?: "Unknown Contact"
                        }.joinToString(", ")

                        views.setTextViewText(R.id.widget_title, "🎂 ${todayBirthdays.size} Birthday(s) Today!")
                        views.setTextViewText(R.id.widget_subtitle, names.ifEmpty { "Wishes to send!" })
                    }
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
