package com.yashsomani.ai_birthday

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native Android background receiver and executor for automated birthday SMS.
 *
 * Enforces strict safety rules:
 * 1. Only sends approved, non-empty content to a valid phone number.
 * 2. Checks SEND_SMS permission at runtime before invoking SmsManager.
 * 3. Does not turn on or wake the display.
 * 4. Never performs network calls or AI generation inside the receiver.
 * 5. Uses distinct callbacks for SUBMISSION (SMS_SENT) and CARRIER DELIVERY (SMS_DELIVERED).
 * 6. Deduplicates using a stable idempotency key to prevent double-sends on reboot or retry.
 */
class BirthdaySmsReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BirthdaySmsReceiver"
        const val ACTION_SEND_BIRTHDAY_SMS = "com.yashsomani.ai_birthday.SEND_BIRTHDAY_SMS"
        const val ACTION_SMS_SENT = "com.yashsomani.ai_birthday.SMS_SENT_ACTION"
        const val ACTION_SMS_DELIVERED = "com.yashsomani.ai_birthday.SMS_DELIVERED_ACTION"

        const val EXTRA_JOB_ID = "extra_job_id"
        const val EXTRA_SCHEDULE_TOKEN = "extra_schedule_token"
        const val KEY_SCHEDULE_TOKEN = "scheduleToken"
        const val EXTRA_PERSON_ID = "extra_person_id"
        const val EXTRA_BIRTHDAY_ID = "extra_birthday_id"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_TOTAL_PARTS = "extra_total_parts"

        const val PREFS_NAME = "scheduled_sms_prefs"
        const val KEY_SCHEDULED_SMS_JOBS_JSON = "scheduled_sms_jobs_json"
        const val KEY_SMS_ATTEMPTS_LOG_JSON = "sms_attempts_log_json"

        // Max validity window for a late-firing alarm: 12 hours
        const val MAX_STALE_WINDOW_MS = 12 * 60 * 60 * 1000L
        private const val AUTOMATIC_SMS_ENABLED = false
        private val queueLock = Any()

        private fun persistCancellation(
            prefs: SharedPreferences,
            jobs: JSONArray,
            description: String
        ): Boolean {
            val retained = pruneTerminalJobs(jobs)
            if (prefs.edit().putString(KEY_SCHEDULED_SMS_JOBS_JSON, retained.toString()).commit()) {
                return true
            }
            Log.e(TAG, "Failed to persist $description; attempting to clear the SMS queue.")
            val cleared = prefs.edit().putString(KEY_SCHEDULED_SMS_JOBS_JSON, "[]").commit()
            if (!cleared) {
                Log.e(TAG, "Failed to persist clearing the SMS queue after $description failure.")
            }
            return cleared
        }

        private fun isTerminalStatus(status: String): Boolean =
            status in setOf("delivered", "failed", "cancelled", "canceled", "expired", "skipped")

        private fun pruneTerminalJobs(jobs: JSONArray): JSONArray {
            val retained = JSONArray()
            for (index in 0 until jobs.length()) {
                val item = jobs.optJSONObject(index) ?: continue
                if (!isTerminalStatus(item.optString("status"))) retained.put(item)
            }
            return retained
        }

        /**
         * Reschedule all active pending SMS jobs after reboot or package update.
         */
        fun reschedulePersisted(context: Context) {
            if (!AUTOMATIC_SMS_ENABLED) {
                synchronized(queueLock) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(KEY_SCHEDULED_SMS_JOBS_JSON, "[]").commit()
                }
                return
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms post-reboot; SMS jobs remain queued until permission is checked.")
                return
            }

            val now = System.currentTimeMillis()
            val dueJobs = mutableListOf<JSONObject>()
            synchronized(queueLock) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, null) ?: return
                val jobsArray = try {
                    JSONArray(raw)
                } catch (e: org.json.JSONException) {
                    Log.e(TAG, "Malformed persisted SMS jobs payload; clearing.", e)
                    prefs.edit().remove(KEY_SCHEDULED_SMS_JOBS_JSON).commit()
                    return
                }
                val remainingArray = JSONArray()

                for (i in 0 until jobsArray.length()) {
                    val item = jobsArray.optJSONObject(i) ?: continue
                    val jobId = item.optString("jobId")
                    val timestampMs = item.optLong("timestampMs", 0L)
                    val status = item.optString("status")
                    val scheduleToken = item.optString(KEY_SCHEDULE_TOKEN)

                    if (status != "scheduled") {
                        if (isTerminalStatus(status)) {
                            cancelAlarm(context, jobId, scheduleToken.takeIf { it.isNotBlank() })
                            continue
                        }
                        remainingArray.put(item)
                        continue
                    }

                    if (scheduleToken.isBlank()) {
                        Log.w(TAG, "Cancelling legacy SMS job $jobId without a schedule token.")
                        item.put("status", "cancelled")
                        item.put("failureReason", "Missing schedule token")
                        cancelAlarm(context, jobId)
                        remainingArray.put(item)
                        continue
                    }

                    if (timestampMs <= now) {
                        if (now - timestampMs > MAX_STALE_WINDOW_MS) {
                            Log.w(TAG, "Skipping expired SMS job $jobId scheduled for $timestampMs (now=$now)")
                            item.put("status", "expired")
                            item.put("failureReason", "Missed schedule window (>12h)")
                            recordAttempt(context, jobId, "expired", "Missed schedule window (>12h)")
                            cancelAlarm(context, jobId, scheduleToken)
                            continue
                        } else {
                            // Persist recovery decisions before execution; the sender must claim afresh.
                            dueJobs.add(item)
                        }
                    } else {
                        scheduleAlarm(context, alarmManager, jobId, scheduleToken, timestampMs)
                    }
                    if (!isTerminalStatus(item.optString("status"))) remainingArray.put(item)
                }
                prefs.edit().putString(KEY_SCHEDULED_SMS_JOBS_JSON, remainingArray.toString()).commit()
            }

            for (job in dueJobs) {
                val jobId = job.optString("jobId")
                val timestampMs = job.optLong("timestampMs", 0L)
                Log.i(TAG, "Executing freshly missed SMS job $jobId (scheduled for $timestampMs, now=$now)")
                executeSmsSend(context, jobId, job.optString(KEY_SCHEDULE_TOKEN))
            }
        }

        fun persistScheduledJob(context: Context, job: JSONObject, timestampMs: Long): Boolean =
            synchronized(queueLock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, "[]") ?: "[]"
            val array = try {
                JSONArray(raw)
            } catch (e: org.json.JSONException) {
                Log.e(TAG, "Cannot update malformed SMS job queue.", e)
                return@synchronized false
            }
            val previousJobs = JSONArray(array.toString())
            val jobId = job.optString("jobId")
            val scheduleToken = job.optString(KEY_SCHEDULE_TOKEN)
            if (
                jobId.isBlank() ||
                scheduleToken.isBlank() ||
                timestampMs <= 0L ||
                job.optString("status") != "scheduled"
            ) {
                Log.e(TAG, "Refusing to persist invalid SMS job identity, token, timestamp, or status.")
                return@synchronized false
            }
            val alarmManager = if (timestampMs > System.currentTimeMillis()) {
                context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            } else {
                null
            }
            if (timestampMs > System.currentTimeMillis() && alarmManager == null) {
                Log.e(TAG, "Cannot arm SMS job $jobId: AlarmManager is unavailable.")
                return@synchronized false
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                alarmManager != null &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                Log.w(TAG, "Cannot arm SMS job $jobId without exact-alarm permission.")
                return@synchronized false
            }
            if (alarmManager != null) {
                try {
                    scheduleAlarm(context, alarmManager, jobId, scheduleToken, timestampMs)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not arm SMS alarm for job $jobId.", e)
                    cancelAlarm(context, jobId, scheduleToken)
                    return@synchronized false
                }
            }
            var replaced = false
            var oldScheduleToken: String? = null
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("jobId") == jobId) {
                    val status = item.optString("status")
                    if (status != "scheduled" && status != "cancelled") {
                        return@synchronized false
                    }
                    oldScheduleToken = item.optString(KEY_SCHEDULE_TOKEN).takeIf { it.isNotBlank() }
                    array.put(i, job)
                    replaced = true
                    break
                }
            }
            if (!replaced) array.put(job)
            if (!prefs.edit().putString(KEY_SCHEDULED_SMS_JOBS_JSON, array.toString()).commit()) {
                Log.e(TAG, "Failed to persist replacement for SMS job $jobId.")
                cancelAlarm(context, jobId, scheduleToken)
                if (replaced) {
                    for (i in 0 until previousJobs.length()) {
                        val previousJob = previousJobs.optJSONObject(i) ?: continue
                        if (previousJob.optString("jobId") == jobId) {
                            previousJob.put("status", "cancelled")
                            previousJob.put("updatedAt", System.currentTimeMillis())
                        }
                    }
                    persistCancellation(prefs, previousJobs, "failed replacement for SMS job $jobId")
                    if (oldScheduleToken != null) {
                        cancelAlarm(context, jobId, oldScheduleToken)
                    } else {
                        cancelAlarm(context, jobId)
                    }
                }
                return@synchronized false
            }
            if (oldScheduleToken != null) {
                cancelAlarm(context, jobId, oldScheduleToken)
            } else if (replaced) {
                cancelAlarm(context, jobId)
            }
            true
        }

        fun cancelPersistedJob(context: Context, jobId: String): Boolean = synchronized(queueLock) {
            if (jobId.isBlank()) return@synchronized false
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, "[]") ?: "[]"
            val array = try {
                JSONArray(raw)
            } catch (e: org.json.JSONException) {
                Log.e(TAG, "Cannot cancel job in malformed SMS job queue.", e)
                val cleared = prefs.edit().putString(KEY_SCHEDULED_SMS_JOBS_JSON, "[]").commit()
                if (!cleared) {
                    Log.e(TAG, "Failed to persist clearing malformed SMS job queue.")
                }
                cancelAlarm(context, jobId)
                return@synchronized cleared
            }
            var foundJob = false
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("jobId") == jobId) {
                    item.put("status", "cancelled")
                    item.put("updatedAt", System.currentTimeMillis())
                    foundJob = true
                }
            }
            val persisted = if (foundJob) {
                persistCancellation(prefs, array, "cancellation for SMS job $jobId")
            } else {
                true
            }
            cancelAlarm(context, jobId)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("jobId") == jobId) {
                    val token = item.optString(KEY_SCHEDULE_TOKEN)
                    if (token.isNotBlank()) cancelAlarm(context, jobId, token)
                }
            }
            persisted
        }

        fun cancelAllPersisted(context: Context): Boolean = synchronized(queueLock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, "[]") ?: "[]"
            val array = try {
                JSONArray(raw)
            } catch (e: org.json.JSONException) {
                Log.e(TAG, "Cannot cancel jobs in malformed SMS job queue.", e)
                val cleared = prefs.edit().putString(KEY_SCHEDULED_SMS_JOBS_JSON, "[]").commit()
                if (!cleared) {
                    Log.e(TAG, "Failed to persist clearing malformed SMS job queue.")
                }
                return@synchronized cleared
            }
            val alarmsToCancel = mutableListOf<Pair<String, String?>>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val jobId = item.optString("jobId")
                if (jobId.isNotEmpty()) {
                    val token = item.optString(KEY_SCHEDULE_TOKEN)
                    alarmsToCancel.add(jobId to token.takeIf { it.isNotBlank() })
                }
                item.put("status", "cancelled")
                item.put("updatedAt", System.currentTimeMillis())
            }
            val persisted = persistCancellation(prefs, array, "cancellation of all SMS jobs")
            for ((jobId, token) in alarmsToCancel) {
                if (token != null) cancelAlarm(context, jobId, token)
                cancelAlarm(context, jobId)
            }
            persisted
        }

        fun cancelPersistedJobsForPerson(
            context: Context,
            personId: String,
            exceptJobId: String? = null
        ): Boolean = synchronized(queueLock) {
            if (personId.isBlank()) return@synchronized false
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, "[]") ?: "[]"
            val array = try {
                JSONArray(raw)
            } catch (e: org.json.JSONException) {
                Log.e(TAG, "Cannot reconcile jobs for person in malformed SMS job queue.", e)
                val cleared = prefs.edit().putString(KEY_SCHEDULED_SMS_JOBS_JSON, "[]").commit()
                if (!cleared) {
                    Log.e(TAG, "Failed to persist clearing malformed SMS job queue.")
                }
                return@synchronized cleared
            }

            var changed = false
            val alarmsToCancel = mutableListOf<Pair<String, String?>>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val jobId = item.optString("jobId")
                if (
                    item.optString("personId") != personId ||
                    jobId == exceptJobId ||
                    item.optString("status") != "scheduled"
                ) {
                    continue
                }
                val scheduleToken = item.optString(KEY_SCHEDULE_TOKEN)
                if (jobId.isNotBlank()) {
                    alarmsToCancel.add(jobId to scheduleToken.takeIf { it.isNotBlank() })
                }
                item.put("status", "cancelled")
                item.put("updatedAt", System.currentTimeMillis())
                changed = true
            }
            if (!changed) return@synchronized true

            val persisted = persistCancellation(prefs, array, "SMS job reconciliation for person $personId")
            for ((jobId, scheduleToken) in alarmsToCancel) {
                if (scheduleToken != null) cancelAlarm(context, jobId, scheduleToken)
                cancelAlarm(context, jobId)
            }
            persisted
        }

        fun scheduleAlarm(
            context: Context,
            alarmManager: AlarmManager,
            jobId: String,
            scheduleToken: String,
            triggerAtMs: Long
        ) {
            val intent = Intent(context, BirthdaySmsReceiver::class.java).apply {
                action = ACTION_SEND_BIRTHDAY_SMS
                putExtra(EXTRA_JOB_ID, jobId)
                putExtra(EXTRA_SCHEDULE_TOKEN, scheduleToken)
                data = alarmIntentData(jobId, scheduleToken)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val requestCode = jobId.hashCode() and 0x7FFFFFFF
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
            Log.i(TAG, "Scheduled SMS alarm for job $jobId at $triggerAtMs (requestCode=$requestCode)")
        }

        private fun alarmIntentData(jobId: String, scheduleToken: String): Uri =
            Uri.parse("birthday-sms://alarm/${Uri.encode(jobId)}/${Uri.encode(scheduleToken)}")

        fun cancelAlarm(context: Context, jobId: String, scheduleToken: String? = null) {
            val intent = Intent(context, BirthdaySmsReceiver::class.java).apply {
                action = ACTION_SEND_BIRTHDAY_SMS
                putExtra(EXTRA_JOB_ID, jobId)
                if (scheduleToken != null) {
                    putExtra(EXTRA_SCHEDULE_TOKEN, scheduleToken)
                    data = alarmIntentData(jobId, scheduleToken)
                }
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
            val requestCode = jobId.hashCode() and 0x7FFFFFFF
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)
            if (pendingIntent != null) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.i(TAG, "Cancelled SMS alarm for job $jobId")
            }
        }

        private fun findJob(context: Context, jobId: String): JSONObject? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, null) ?: return null
            val array = try {
                JSONArray(raw)
            } catch (e: org.json.JSONException) {
                Log.e(TAG, "Cannot read job from malformed SMS job queue.", e)
                return null
            }
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("jobId") == jobId) return item
            }
            return null
        }

        private fun executeSmsSend(context: Context, jobId: String, scheduleToken: String) {
            synchronized(queueLock) {
                val jobJson = findJob(context, jobId) ?: return
                if (jobJson.optString(KEY_SCHEDULE_TOKEN) != scheduleToken ||
                    jobJson.optString("status") != "scheduled"
                ) {
                    Log.w(TAG, "Ignoring stale or non-scheduled SMS alarm for job $jobId.")
                    return
                }
                val timestampMs = jobJson.optLong("timestampMs", 0L)
                val now = System.currentTimeMillis()
                if (timestampMs <= 0L) {
                    Log.w(TAG, "Ignoring SMS job $jobId with an invalid timestamp.")
                    return
                }
                if (timestampMs > now) {
                    Log.w(TAG, "Ignoring early SMS alarm for job $jobId.")
                    return
                }
                if (timestampMs > 0L && now - timestampMs > MAX_STALE_WINDOW_MS) {
                    updateJobStatus(context, jobId, "expired", "Alarm triggered too late (>12h)")
                    recordAttempt(context, jobId, "expired", "Stale alarm trigger")
                    return
                }
                if (!AUTOMATIC_SMS_ENABLED) {
                    updateJobStatus(context, jobId, "cancelled", "Automatic SMS is disabled")
                    return
                }
                val phoneNumber = jobJson.optString("phoneNumber")
                val message = jobJson.optString("message")
                val isApproved = jobJson.optBoolean("isApproved", false)
                val birthdayId = jobJson.optString("birthdayId")
                val personId = jobJson.optString("personId")

                // Strict eligibility checks
                if (!isApproved) {
                    Log.w(TAG, "Job $jobId rejected: message is not approved by user.")
                    recordAttempt(context, jobId, "skipped", "Draft was not approved for auto-sending")
                    updateJobStatus(context, jobId, "skipped", "Not approved")
                    return
                }

                if (phoneNumber.isBlank() || message.isBlank()) {
                    Log.w(TAG, "Job $jobId rejected: missing phone number or message.")
                    recordAttempt(context, jobId, "failed", "Invalid phone number or empty message")
                    updateJobStatus(context, jobId, "failed", "Invalid input")
                    return
                }

                // Check SEND_SMS permission
                val hasSmsPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasSmsPermission) {
                    Log.e(TAG, "Job $jobId failed: SEND_SMS permission is not granted.")
                    recordAttempt(context, jobId, "failed", "Permission SEND_SMS not granted")
                    updateJobStatus(context, jobId, "failed", "Permission denied")
                    return
                }

                // Hold the queue lock through the SMS handoff so cancellation and claiming are ordered.
                if (!markJobSending(context, jobId, scheduleToken)) {
                    Log.w(TAG, "Job $jobId is cancelled, already claimed, or processed. Skipping.")
                    return
                }

                try {
                    val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }

                    val cleanPhone = phoneNumber.trim()
                    val parts = smsManager.divideMessage(message)
                    val totalParts = parts.size

                    val sentIntents = ArrayList<PendingIntent>()
                    val deliveredIntents = ArrayList<PendingIntent>()

                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }

                    for (idx in 0 until totalParts) {
                        val sentIntent = Intent(context, BirthdaySmsReceiver::class.java).apply {
                            action = ACTION_SMS_SENT
                            putExtra(EXTRA_JOB_ID, jobId)
                            putExtra(EXTRA_SCHEDULE_TOKEN, scheduleToken)
                            putExtra(EXTRA_PERSON_ID, personId)
                            putExtra(EXTRA_BIRTHDAY_ID, birthdayId)
                            putExtra(EXTRA_PART_INDEX, idx)
                            putExtra(EXTRA_TOTAL_PARTS, totalParts)
                            data = callbackIntentData(ACTION_SMS_SENT, jobId, scheduleToken, idx)
                        }
                        val sentPI = PendingIntent.getBroadcast(
                            context,
                            (jobId.hashCode() + idx * 31) and 0x7FFFFFFF,
                            sentIntent,
                            flags
                        )
                        sentIntents.add(sentPI)

                        val deliveredIntent = Intent(context, BirthdaySmsReceiver::class.java).apply {
                            action = ACTION_SMS_DELIVERED
                            putExtra(EXTRA_JOB_ID, jobId)
                            putExtra(EXTRA_SCHEDULE_TOKEN, scheduleToken)
                            putExtra(EXTRA_PERSON_ID, personId)
                            putExtra(EXTRA_BIRTHDAY_ID, birthdayId)
                            putExtra(EXTRA_PART_INDEX, idx)
                            putExtra(EXTRA_TOTAL_PARTS, totalParts)
                            data = callbackIntentData(ACTION_SMS_DELIVERED, jobId, scheduleToken, idx)
                        }
                        val deliveredPI = PendingIntent.getBroadcast(
                            context,
                            (jobId.hashCode() + idx * 37 + 1000) and 0x7FFFFFFF,
                            deliveredIntent,
                            flags
                        )
                        deliveredIntents.add(deliveredPI)
                    }

                    if (parts.size == 1) {
                        smsManager.sendTextMessage(cleanPhone, null, parts[0], sentIntents[0], deliveredIntents[0])
                    } else {
                        smsManager.sendMultipartTextMessage(cleanPhone, null, parts, sentIntents, deliveredIntents)
                    }

                    Log.i(TAG, "Submitted SMS for job $jobId ($totalParts parts) to radio system.")
                    recordAttempt(context, jobId, "submitted", "Submitted to carrier subsystem")
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during SmsManager send for job $jobId", e)
                    recordAttempt(context, jobId, "failed", "SmsManager exception: ${e.message}")
                    updateJobStatus(context, jobId, "failed", e.message ?: "Exception")
                }
            }
        }

        private fun callbackIntentData(
            action: String,
            jobId: String,
            scheduleToken: String,
            partIndex: Int
        ): Uri = Uri.parse(
            "birthday-sms://callback/${Uri.encode(action)}/${Uri.encode(jobId)}/" +
                "${Uri.encode(scheduleToken)}/$partIndex"
        )

        private fun markJobSending(
            context: Context,
            jobId: String,
            scheduleToken: String
        ): Boolean {
            synchronized(queueLock) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, null) ?: return false
                val array = try {
                    JSONArray(raw)
                } catch (e: org.json.JSONException) {
                    Log.e(TAG, "Cannot claim job in malformed SMS job queue.", e)
                    return false
                }

                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    if (item.optString("jobId") == jobId) {
                        if (item.optString("status") != "scheduled" ||
                            item.optString(KEY_SCHEDULE_TOKEN) != scheduleToken
                        ) {
                            return false
                        }
                        val timestampMs = item.optLong("timestampMs", 0L)
                        if (timestampMs <= 0L || timestampMs > System.currentTimeMillis()) {
                            Log.w(TAG, "Refusing to claim SMS job $jobId before its valid scheduled time.")
                            return false
                        }
                        item.put("status", "sending")
                        item.put("submittedAt", System.currentTimeMillis())
                        return prefs.edit().putString(KEY_SCHEDULED_SMS_JOBS_JSON, array.toString()).commit()
                    }
                }
                return false
            }
        }

        private fun hasCurrentCallbackToken(
            context: Context,
            jobId: String,
            scheduleToken: String
        ): Boolean = synchronized(queueLock) {
            val item = findJob(context, jobId) ?: return@synchronized false
            item.optString(KEY_SCHEDULE_TOKEN) == scheduleToken &&
                item.optString("status") in setOf("sending", "acceptedByCarrier", "delivered")
        }

        private fun updateJobStatusForToken(
            context: Context,
            jobId: String,
            scheduleToken: String,
            status: String,
            detail: String
        ): Boolean = synchronized(queueLock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, null) ?: return@synchronized false
            val array = try {
                JSONArray(raw)
            } catch (e: org.json.JSONException) {
                Log.e(TAG, "Cannot update callback status in malformed SMS job queue.", e)
                return@synchronized false
            }

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("jobId") != jobId) continue
                if (
                    item.optString(KEY_SCHEDULE_TOKEN) != scheduleToken ||
                    item.optString("status") !in setOf("sending", "acceptedByCarrier")
                ) {
                    return@synchronized false
                }
                item.put("status", status)
                item.put("updatedAt", System.currentTimeMillis())
                item.put("statusDetail", detail)
                val persisted = prefs.edit().putString(
                    KEY_SCHEDULED_SMS_JOBS_JSON,
                    pruneTerminalJobs(array).toString()
                ).commit()
                if (!persisted) {
                    Log.e(TAG, "Failed to persist callback status $status for SMS job $jobId.")
                }
                return@synchronized persisted
            }
            false
        }

        fun updateJobStatus(context: Context, jobId: String, status: String, detail: String? = null) {
            synchronized(queueLock) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, null) ?: return
                val array = try {
                    JSONArray(raw)
                } catch (e: org.json.JSONException) {
                    Log.e(TAG, "Cannot update job in malformed SMS job queue.", e)
                    return
                }

                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    if (item.optString("jobId") == jobId) {
                        if (item.optString("status") == "cancelled") return
                        item.put("status", status)
                        item.put("updatedAt", System.currentTimeMillis())
                        if (detail != null) item.put("statusDetail", detail)
                        break
                    }
                }
                prefs.edit().putString(
                    KEY_SCHEDULED_SMS_JOBS_JSON,
                    pruneTerminalJobs(array).toString()
                ).commit()
            }
        }

        fun recordAttempt(context: Context, jobId: String, result: String, details: String?) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SMS_ATTEMPTS_LOG_JSON, "[]") ?: "[]"
            val array = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }

            val entry = JSONObject().apply {
                put("jobId", jobId)
                put("timestampMs", System.currentTimeMillis())
                put("result", result)
                if (details != null) put("details", details)
            }
            array.put(entry)

            // Keep only the most recent 100 log entries
            val trimmedArray = if (array.length() > 100) {
                val newArr = JSONArray()
                val start = array.length() - 100
                for (k in start until array.length()) {
                    newArr.put(array.get(k))
                }
                newArr
            } else {
                array
            }

            prefs.edit().putString(KEY_SMS_ATTEMPTS_LOG_JSON, trimmedArray.toString()).apply()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "BirthdaySmsReceiver received action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                reschedulePersisted(context)
            }

            ACTION_SEND_BIRTHDAY_SMS -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
                val scheduleToken = intent.getStringExtra(EXTRA_SCHEDULE_TOKEN) ?: return
                executeSmsSend(context, jobId, scheduleToken)
            }

            ACTION_SMS_SENT -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
                val scheduleToken = intent.getStringExtra(EXTRA_SCHEDULE_TOKEN) ?: return
                if (!hasCurrentCallbackToken(context, jobId, scheduleToken)) {
                    Log.w(TAG, "Ignoring stale SMS_SENT callback for job $jobId.")
                    return
                }
                val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
                val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1)

                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Log.i(TAG, "SMS part $partIndex/$totalParts sent successfully for job $jobId.")
                        if (partIndex == totalParts - 1) {
                            if (
                                updateJobStatusForToken(
                                    context,
                                    jobId,
                                    scheduleToken,
                                    "acceptedByCarrier",
                                    "Message accepted by cellular network"
                                )
                            ) {
                                recordAttempt(context, jobId, "acceptedByCarrier", "All parts sent to cellular tower")
                            } else {
                                Log.w(TAG, "Ignoring stale SMS_SENT status update for job $jobId.")
                            }
                        }
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        Log.e(TAG, "SMS generic failure for job $jobId.")
                        if (updateJobStatusForToken(context, jobId, scheduleToken, "failed", "Generic carrier failure")) {
                            recordAttempt(context, jobId, "failed", "RESULT_ERROR_GENERIC_FAILURE")
                        }
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        Log.e(TAG, "SMS no service for job $jobId.")
                        if (updateJobStatusForToken(context, jobId, scheduleToken, "failed", "No cellular service")) {
                            recordAttempt(context, jobId, "failed", "RESULT_ERROR_NO_SERVICE")
                        }
                    }
                    SmsManager.RESULT_ERROR_NULL_PDU -> {
                        Log.e(TAG, "SMS null PDU for job $jobId.")
                        if (updateJobStatusForToken(context, jobId, scheduleToken, "failed", "Null PDU")) {
                            recordAttempt(context, jobId, "failed", "RESULT_ERROR_NULL_PDU")
                        }
                    }
                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        Log.e(TAG, "SMS radio off for job $jobId.")
                        if (
                            updateJobStatusForToken(
                                context,
                                jobId,
                                scheduleToken,
                                "failed",
                                "Airplane mode / Radio off"
                            )
                        ) {
                            recordAttempt(context, jobId, "failed", "RESULT_ERROR_RADIO_OFF")
                        }
                    }
                    else -> {
                        Log.e(TAG, "SMS send failed with code $resultCode for job $jobId.")
                        if (
                            updateJobStatusForToken(
                                context,
                                jobId,
                                scheduleToken,
                                "failed",
                                "Carrier error code $resultCode"
                            )
                        ) {
                            recordAttempt(context, jobId, "failed", "Error code $resultCode")
                        }
                    }
                }
            }

            ACTION_SMS_DELIVERED -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
                val scheduleToken = intent.getStringExtra(EXTRA_SCHEDULE_TOKEN) ?: return
                if (!hasCurrentCallbackToken(context, jobId, scheduleToken)) {
                    Log.w(TAG, "Ignoring stale SMS_DELIVERED callback for job $jobId.")
                    return
                }
                val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
                val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1)

                if (resultCode == Activity.RESULT_OK) {
                    Log.i(TAG, "SMS delivery receipt confirmed for job $jobId part $partIndex/$totalParts.")
                    if (partIndex == totalParts - 1) {
                        if (
                            updateJobStatusForToken(
                                context,
                                jobId,
                                scheduleToken,
                                "delivered",
                                "Carrier delivery report confirmed"
                            )
                        ) {
                            recordAttempt(context, jobId, "delivered", "Delivery receipt verified")
                        } else {
                            Log.w(TAG, "Ignoring stale SMS_DELIVERED status update for job $jobId.")
                        }
                    }
                } else {
                    Log.w(TAG, "SMS delivery receipt indicated failure or unknown for job $jobId: $resultCode")
                    recordAttempt(context, jobId, "deliveryUnconfirmed", "Delivery receipt status code: $resultCode")
                }
            }
        }
    }
}
