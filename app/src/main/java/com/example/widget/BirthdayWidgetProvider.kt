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
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val pendingResult = (context.getSystemService(Context.APPWIDGET_SERVICE) as? AppWidgetManager)?.let {
                // Not strictly needed since AppWidgetProvider goes out of scope quickly
            }

            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val views = RemoteViews(context.packageName, R.layout.widget_birthday)
                val db = AppDatabase.getInstance(context)
                val events = db.eventDao().getAll().first()
                val contacts = db.contactDao().getAll().first()

                val today = Calendar.getInstance()
                val todayDay = today.get(Calendar.DAY_OF_MONTH)
                val todayMonth = today.get(Calendar.MONTH) + 1

                val todayBirthdays = events.filter {
                    it.type == "BIRTHDAY" && it.dayOfMonth == todayDay && it.month == todayMonth
                }

                if (todayBirthdays.isEmpty()) {
                    views.setTextViewText(R.id.widget_title, "No birthdays today")
                    views.setTextViewText(R.id.widget_subtitle, "Enjoy your day!")
                } else {
                    val names = todayBirthdays.mapNotNull { bday ->
                        contacts.find { it.id == bday.contactId }?.name
                    }.joinToString(", ")

                    views.setTextViewText(R.id.widget_title, "🎂 ${todayBirthdays.size} Birthday(s) Today!")
                    views.setTextViewText(R.id.widget_subtitle, names.ifEmpty { "Wishes to send!" })
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
