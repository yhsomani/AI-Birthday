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
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val NOTIFICATIONS_CHANNEL = "com.yashsomani.ai_birthday/notifications"
    private val NANO_CHANNEL = "com.yashsomani.ai_birthday/nano"
    private val SHARE_CHANNEL = "com.yashsomani.ai_birthday/share"
    private val CONTACTS_CHANNEL = "com.yashsomani.ai_birthday/contacts"

    private var pendingNotificationResult: MethodChannel.Result? = null
    private var pendingContactsResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Setup Notifications MethodChannel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, NOTIFICATIONS_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
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
                    "apply" -> {
                        val triggers = call.argument<List<Map<String, Any>>>("triggers") ?: emptyList()
                        scheduleTriggers(triggers)
                        result.success(null)
                    }
                    "cancelAll" -> {
                        cancelAllReminders()
                        result.success(null)
                    }
                    "testNotification" -> {
                        val title = call.argument<String>("title") ?: "Birthday Reminder"
                        val body = call.argument<String>("body") ?: "Testing notification delivery"
                        showImmediateNotification(title, body)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }

        // Setup Gemini Nano MethodChannel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, NANO_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "currentState" -> {
                        // Check if Google AICore is installed on device hardware
                        val isAiCoreInstalled = try {
                            packageManager.getPackageInfo("com.google.android.aicore", 0)
                            true
                        } catch (e: Exception) {
                            false
                        }
                        if (isAiCoreInstalled) {
                            result.success("available")
                        } else {
                            result.success("unavailable")
                        }
                    }
                    "startDownload" -> {
                        result.success("unavailable")
                    }
                    "generate" -> {
                        result.error(
                            "NANO_UNAVAILABLE",
                            "Gemini Nano (AICore) is not available on this device hardware.",
                            null
                        )
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

    private fun scheduleTriggers(triggers: List<Map<String, Any>>) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        for (trigger in triggers) {
            val id = (trigger["id"] as? Number)?.toInt() ?: continue
            val timestampMs = (trigger["timestampMs"] as? Number)?.toLong() ?: continue
            if (timestampMs <= now) continue

            val title = trigger["title"] as? String ?: "Birthday Reminder"
            val body = trigger["body"] as? String ?: ""
            val personId = trigger["personId"] as? String

            val intent = Intent(this, BirthdayNotificationReceiver::class.java).apply {
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestampMs, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, timestampMs, pendingIntent)
                }
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, timestampMs, pendingIntent)
            }
        }
    }

    private fun cancelAllReminders() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancelAll()
    }

    private fun showImmediateNotification(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
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

        notificationManager.notify(9999, notification)
    }
}
