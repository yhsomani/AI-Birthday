package com.yashsomani.ai_birthday

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : FlutterActivity() {
    private val NOTIFICATIONS_CHANNEL = "com.yashsomani.ai_birthday/notifications"
    private val NANO_CHANNEL = "com.yashsomani.ai_birthday/nano"
    private val SHARE_CHANNEL = "com.yashsomani.ai_birthday/share"
    private val CONTACTS_CHANNEL = "com.yashsomani.ai_birthday/contacts"

    private var pendingNotificationResult: MethodChannel.Result? = null
    private var pendingContactsResult: MethodChannel.Result? = null
    private var initialPersonId: String? = null
    private var notificationsChannel: MethodChannel? = null
    private val nativeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val generativeModel by lazy { Generation.getClient() }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val pId = intent?.getStringExtra(BirthdayNotificationReceiver.EXTRA_PERSON_ID)
        if (!pId.isNullOrBlank()) {
            initialPersonId = pId
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pId = intent.getStringExtra(BirthdayNotificationReceiver.EXTRA_PERSON_ID)
        if (!pId.isNullOrBlank()) {
            initialPersonId = pId
            notificationsChannel?.invokeMethod("onNotificationOpened", mapOf("personId" to pId))
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Setup Notifications MethodChannel
        val notifChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, NOTIFICATIONS_CHANNEL)
        notificationsChannel = notifChannel
        notifChannel.setMethodCallHandler { call, result ->
                when (call.method) {
                    "getInitialNotification" -> {
                        val pId = initialPersonId
                        initialPersonId = null
                        if (pId != null) {
                            result.success(mapOf("personId" to pId))
                        } else {
                            result.success(null)
                        }
                    }
                    "hasPermission" -> {
                        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
                        result.success(enabled)
                    }
                    "requestPermission" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                                == PackageManager.PERMISSION_GRANTED) {
                                result.success(true)
                            } else {
                                pendingNotificationResult = result
                                ActivityCompat.requestPermissions(
                                    this,
                                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                    1001
                                )
                            }
                        } else {
                            val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
                            result.success(enabled)
                        }
                    }
                    "hasExactAlarmPermission" -> {
                        result.success(
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                                (getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                                    ?.canScheduleExactAlarms() == true
                        )
                    }
                    "requestExactAlarmPermission" -> {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            result.success(true)
                        } else {
                            val alarmManager =
                                getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                            if (alarmManager?.canScheduleExactAlarms() == true) {
                                result.success(true)
                            } else {
                                try {
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                                    ).apply {
                                        data = android.net.Uri.parse("package:$packageName")
                                    }
                                    startActivity(intent)
                                } catch (_: Exception) {
                                    result.success(false)
                                    return@setMethodCallHandler
                                }
                                result.success(false)
                            }
                        }
                    }
                    "apply" -> {
                        val triggers = call.argument<List<Map<String, Any>>>("triggers") ?: emptyList()
                        result.success(scheduleTriggers(triggers))
                    }
                    "cancelAll" -> {
                        result.success(cancelAllReminders())
                    }
                    "testNotification" -> {
                        val title = call.argument<String>("title") ?: "Birthday Reminder"
                        val body = call.argument<String>("body") ?: "Testing notification delivery"
                        val personId = call.argument<String>("personId")
                        result.success(showImmediateNotification(title, body, personId))
                    }
                    else -> result.notImplemented()
                }
            }

        // Setup Gemini Nano MethodChannel (real ML Kit GenAI Prompt API / Gemini Nano)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, NANO_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "currentState" -> {
                        nativeScope.launch {
                            try {
                                result.success(mapFeatureStatus(generativeModel.checkStatus()))
                            } catch (_: Exception) {
                                result.success("error")
                            }
                        }
                    }
                    "startDownload" -> {
                        nativeScope.launch {
                            try {
                                when (generativeModel.checkStatus()) {
                                    FeatureStatus.AVAILABLE -> result.success("ready")
                                    FeatureStatus.DOWNLOADING -> result.success("downloading")
                                    FeatureStatus.DOWNLOADABLE -> {
                                        generativeModel.download().collect { status ->
                                            when (status) {
                                                is DownloadStatus.DownloadStarted,
                                                is DownloadStatus.DownloadProgress,
                                                DownloadStatus.DownloadCompleted,
                                                is DownloadStatus.DownloadFailed -> Unit
                                                else -> Unit
                                            }
                                        }
                                        result.success(mapFeatureStatus(generativeModel.checkStatus()))
                                    }
                                    FeatureStatus.UNAVAILABLE -> result.success("unavailable")
                                    else -> result.success("error")
                                }
                            } catch (_: Exception) {
                                result.error(
                                    "NANO_DOWNLOAD_ERROR",
                                    "Gemini Nano model download failed.",
                                    null
                                )
                            }
                        }
                    }
                    "generate" -> {
                        val prompt = call.argument<String>("prompt")?.trim().orEmpty()
                        if (prompt.isEmpty()) {
                            result.error("INVALID_PROMPT", "Prompt cannot be empty.", null)
                        } else {
                            nativeScope.launch {
                                try {
                                    val status = generativeModel.checkStatus()
                                    if (status != FeatureStatus.AVAILABLE) {
                                        result.error(
                                            "NANO_NOT_READY",
                                            "Gemini Nano is not ready on this device.",
                                            mapFeatureStatus(status)
                                        )
                                        return@launch
                                    }

                                    val response = generativeModel.generateContent(prompt)
                                    val generatedText = response.candidates.firstOrNull()?.text?.trim()
                                    if (generatedText.isNullOrEmpty()) {
                                        result.error(
                                            "NANO_EMPTY_RESPONSE",
                                            "Gemini Nano returned no text.",
                                            null
                                        )
                                    } else {
                                        result.success(generatedText)
                                    }
                                } catch (_: Exception) {
                                    result.error(
                                        "NANO_GENERATION_ERROR",
                                        "Gemini Nano generation failed.",
                                        null
                                    )
                                }
                            }
                        }
                    }
                    else -> result.notImplemented()
                }
            }

        // Setup Share MethodChannel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SHARE_CHANNEL)
            .setMethodCallHandler { call, result ->
                if (call.method == "shareText") {
                    val text = call.argument<String>("text") ?: ""
                    val title = call.argument<String>("title") ?: "Share Birthday Message"
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, text)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, title)
                    startActivity(shareIntent)
                    result.success(true)
                } else {
                    result.notImplemented()
                }
            }

        // Setup Contacts MethodChannel (SSOT §18)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CONTACTS_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "hasPermission" -> {
                        val granted = ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED
                        result.success(granted)
                    }
                    "requestPermission" -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                            == PackageManager.PERMISSION_GRANTED) {
                            result.success(true)
                        } else {
                            pendingContactsResult = result
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(Manifest.permission.READ_CONTACTS),
                                1002
                            )
                        }
                    }
                    "fetchDeviceContacts" -> {
                        val granted = ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            result.error("PERMISSION_DENIED", "READ_CONTACTS permission is required.", null)
                        } else {
                            try {
                                val contacts = fetchDeviceContacts()
                                result.success(contacts)
                            } catch (e: Exception) {
                                result.error("QUERY_FAILED", e.message, null)
                            }
                        }
                    }
                    else -> result.notImplemented()
                }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            pendingNotificationResult?.success(granted)
            pendingNotificationResult = null
        } else if (requestCode == 1002) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            pendingContactsResult?.success(granted)
            pendingContactsResult = null
        }
    }

    private fun fetchDeviceContacts(): List<Map<String, Any?>> {
        val contactMap = mutableMapOf<String, MutableMap<String, Any?>>()
        val cr = contentResolver ?: return emptyList()

        // 1. Query contact names and IDs
        val cursor = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ),
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        )

        cursor?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (c.moveToNext()) {
                val id = if (idIdx >= 0) c.getString(idIdx) else null ?: continue
                val name = if (nameIdx >= 0) c.getString(nameIdx) else null ?: continue
                if (name.isBlank()) continue
                contactMap[id] = mutableMapOf(
                    "id" to id,
                    "name" to name.trim(),
                    "phone" to null,
                    "birthday" to null
                )
            }
        }

        // 2. Query phone numbers
        val phoneCursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )

        phoneCursor?.use { pc ->
            val contactIdIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numberIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (pc.moveToNext()) {
                val contactId = if (contactIdIdx >= 0) pc.getString(contactIdIdx) else null ?: continue
                val number = if (numberIdx >= 0) pc.getString(numberIdx) else null
                val entry = contactMap[contactId]
                if (entry != null && entry["phone"] == null && !number.isNullOrBlank()) {
                    entry["phone"] = number.trim()
                }
            }
        }

        // 3. Query birthdays from ContactsContract.Data
        val birthdayCursor = cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.Event.START_DATE
            ),
            "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
            arrayOf(
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
            ),
            null
        )

        birthdayCursor?.use { bc ->
            val contactIdIdx = bc.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val dateIdx = bc.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE)
            while (bc.moveToNext()) {
                val contactId = if (contactIdIdx >= 0) bc.getString(contactIdIdx) else null ?: continue
                val date = if (dateIdx >= 0) bc.getString(dateIdx) else null
                val entry = contactMap[contactId]
                if (entry != null && !date.isNullOrBlank()) {
                    entry["birthday"] = date.trim()
                }
            }
        }

        return contactMap.values.toList()
    }

    private fun scheduleTriggers(triggers: List<Map<String, Any>>): Boolean {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        val now = System.currentTimeMillis()
        val prefs = getSharedPreferences(BirthdayNotificationReceiver.PREFS_NAME, Context.MODE_PRIVATE)

        // Cancel previous alarms first to avoid duplicate pending intents.
        var allScheduled = cancelAllReminders()

        val scheduledIds = mutableSetOf<String>()
        val scheduledTriggers = JSONArray()
        for (trigger in triggers) {
            val id = (trigger["id"] as? Number)?.toInt()
            if (id == null) {
                allScheduled = false
                continue
            }
            val timestampMs = (trigger["timestampMs"] as? Number)?.toLong()
            if (timestampMs == null) {
                allScheduled = false
                continue
            }
            if (timestampMs <= now) {
                allScheduled = false
                continue
            }

            val title = trigger["title"] as? String ?: "Birthday Reminder"
            val body = trigger["body"] as? String ?: ""
            val personId = trigger["personId"] as? String

            val intent = Intent(this, BirthdayNotificationReceiver::class.java).apply {
                action = BirthdayNotificationReceiver.ACTION_BIRTHDAY_REMINDER
                putExtra(BirthdayNotificationReceiver.EXTRA_ID, id)
                putExtra(BirthdayNotificationReceiver.EXTRA_TITLE, title)
                putExtra(BirthdayNotificationReceiver.EXTRA_BODY, body)
                putExtra(BirthdayNotificationReceiver.EXTRA_PERSON_ID, personId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()
                ) {
                    Log.w("BirthdayReminder", "Exact alarm permission is unavailable; skipping id=$id")
                    allScheduled = false
                    continue
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timestampMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, timestampMs, pendingIntent)
                }

                scheduledIds.add(id.toString())
                scheduledTriggers.put(
                    JSONObject().apply {
                        put("id", id)
                        put("timestampMs", timestampMs)
                        put("title", title)
                        put("body", body)
                        if (personId != null) put("personId", personId)
                    }
                )
            } catch (e: SecurityException) {
                Log.e("BirthdayReminder", "Exact alarm scheduling rejected for id=$id", e)
                allScheduled = false
            }
        }

        prefs.edit()
            .putStringSet(BirthdayNotificationReceiver.KEY_SCHEDULED_ALARM_IDS, scheduledIds)
            .putString(BirthdayNotificationReceiver.KEY_SCHEDULED_TRIGGERS_JSON, scheduledTriggers.toString())
            .apply()

        return allScheduled
    }

    private fun cancelAllReminders(): Boolean {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val prefs = getSharedPreferences(BirthdayNotificationReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val scheduledIds = prefs.getStringSet(
            BirthdayNotificationReceiver.KEY_SCHEDULED_ALARM_IDS,
            emptySet()
        ) ?: emptySet()
        var allCancelled = true

        if (alarmManager != null) {
            for (idStr in scheduledIds) {
                val id = idStr.toIntOrNull() ?: continue
                val intent = Intent(this, BirthdayNotificationReceiver::class.java).apply {
                    action = BirthdayNotificationReceiver.ACTION_BIRTHDAY_REMINDER
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    this,
                    id,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    try {
                        alarmManager.cancel(pendingIntent)
                        pendingIntent.cancel()
                    } catch (e: Exception) {
                        Log.e("BirthdayReminder", "Failed to cancel reminder id=$id", e)
                        allCancelled = false
                    }
                }
            }
        }
        prefs.edit()
            .remove(BirthdayNotificationReceiver.KEY_SCHEDULED_ALARM_IDS)
            .remove(BirthdayNotificationReceiver.KEY_SCHEDULED_TRIGGERS_JSON)
            .apply()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            notificationManager?.cancelAll()
        } catch (e: Exception) {
            Log.e("BirthdayReminder", "Failed to clear posted notifications", e)
            allCancelled = false
        }
        return allCancelled
    }

    private fun showImmediateNotification(title: String, body: String, personId: String? = null): Boolean {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BirthdayNotificationReceiver.CHANNEL_ID,
                BirthdayNotificationReceiver.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = BirthdayNotificationReceiver.CHANNEL_DESCRIPTION
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (personId != null) {
                putExtra(BirthdayNotificationReceiver.EXTRA_PERSON_ID, personId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            9999,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, BirthdayNotificationReceiver.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            notificationManager.notify(9999, notification)
            true
        } catch (e: SecurityException) {
            Log.e("BirthdayReminder", "Test notification was rejected", e)
            false
        }
    }

    private fun mapFeatureStatus(status: Int): String {
        return when (status) {
            FeatureStatus.AVAILABLE -> "ready"
            FeatureStatus.DOWNLOADABLE -> "downloadable"
            FeatureStatus.DOWNLOADING -> "downloading"
            FeatureStatus.UNAVAILABLE -> "unavailable"
            else -> "error"
        }
    }

    override fun onDestroy() {
        nativeScope.cancel()
        super.onDestroy()
    }
}
