package com.yashsomani.ai_birthday

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        const val EXTRA_PERSON_ID = "extra_person_id"
        const val EXTRA_BIRTHDAY_ID = "extra_birthday_id"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_TOTAL_PARTS = "extra_total_parts"

        const val PREFS_NAME = "scheduled_sms_prefs"
        const val KEY_SCHEDULED_SMS_JOBS_JSON = "scheduled_sms_jobs_json"
        const val KEY_SMS_ATTEMPTS_LOG_JSON = "sms_attempts_log_json"

        // Max validity window for a late-firing alarm: 12 hours
        const val MAX_STALE_WINDOW_MS = 12 * 60 * 60 * 1000L

        /**
         * Reschedule all active pending SMS jobs after reboot or package update.
         */
        fun reschedulePersisted(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, null) ?: return
            val jobsArray = try {
                JSONArray(raw)
            } catch (e: Exception) {
                Log.e(TAG, "Malformed persisted SMS jobs payload; clearing.", e)
                prefs.edit().remove(KEY_SCHEDULED_SMS_JOBS_JSON).apply()
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms post-reboot; SMS jobs remain queued until permission is checked.")
                return
            }

            val now = System.currentTimeMillis()
            val remainingArray = JSONArray()

            for (i in 0 until jobsArray.length()) {
                val item = jobsArray.optJSONObject(i) ?: continue
                val jobId = item.optString("jobId")
                val timestampMs = item.optLong("timestampMs", 0L)
                val status = item.optString("status", "scheduled")

                if (status != "scheduled") {
                    remainingArray.put(item)
                    continue
                }

                if (timestampMs <= now) {
                    if (now - timestampMs > MAX_STALE_WINDOW_MS) {
                        Log.w(TAG, "Skipping expired SMS job $jobId scheduled for $timestampMs (now=$now)")
                        item.put("status", "expired")
                        item.put("failureReason", "Missed schedule window (>12h)")
                        recordAttempt(context, jobId, "expired", "Missed schedule window (>12h)")
                    } else {
                        // Job was due recently during reboot/shutdown; execute now
                        Log.i(TAG, "Executing freshly missed SMS job $jobId (scheduled for $timestampMs, now=$now)")
                        executeSmsSend(context, item)
                    }
                } else {
                    // Schedule future alarm
                    scheduleAlarm(context, alarmManager, jobId, timestampMs)
                }
                remainingArray.put(item)
            }

            prefs.edit().putString(KEY_SCHEDULED_SMS_JOBS_JSON, remainingArray.toString()).apply()
        }

        fun scheduleAlarm(context: Context, alarmManager: AlarmManager, jobId: String, triggerAtMs: Long) {
            val intent = Intent(context, BirthdaySmsReceiver::class.java).apply {
                action = ACTION_SEND_BIRTHDAY_SMS
                putExtra(EXTRA_JOB_ID, jobId)
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

        fun cancelAlarm(context: Context, jobId: String) {
            val intent = Intent(context, BirthdaySmsReceiver::class.java).apply {
                action = ACTION_SEND_BIRTHDAY_SMS
                putExtra(EXTRA_JOB_ID, jobId)
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

        private fun executeSmsSend(context: Context, jobJson: JSONObject) {
            val jobId = jobJson.optString("jobId")
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

            // Atomic state lock: mark job as "sending" to prevent duplicate entry
            if (!markJobSending(context, jobId)) {
                Log.w(TAG, "Job $jobId is already sending or processed. Skipping.")
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
                        putExtra(EXTRA_PERSON_ID, personId)
                        putExtra(EXTRA_BIRTHDAY_ID, birthdayId)
                        putExtra(EXTRA_PART_INDEX, idx)
                        putExtra(EXTRA_TOTAL_PARTS, totalParts)
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
                        putExtra(EXTRA_PERSON_ID, personId)
                        putExtra(EXTRA_BIRTHDAY_ID, birthdayId)
                        putExtra(EXTRA_PART_INDEX, idx)
                        putExtra(EXTRA_TOTAL_PARTS, totalParts)
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

        private fun markJobSending(context: Context, jobId: String): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, null) ?: return false
            val array = try { JSONArray(raw) } catch (_: Exception) { return false }
            var found = false

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("jobId") == jobId) {
                    val status = item.optString("status")
                    if (status == "sending" || status == "sent" || status == "delivered") {
                        return false // already claimed or finished
                    }
                    item.put("status", "sending")
                    item.put("submittedAt", System.currentTimeMillis())
                    found = true
                    break
                }
            }

            if (found) {
                prefs.edit().putString(KEY_SCHEDULED_SMS_JOBS_JSON, array.toString()).apply()
            }
            return found
        }

        fun updateJobStatus(context: Context, jobId: String, status: String, detail: String? = null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, null) ?: return
            val array = try { JSONArray(raw) } catch (_: Exception) { return }

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("jobId") == jobId) {
                    item.put("status", status)
                    item.put("updatedAt", System.currentTimeMillis())
                    if (detail != null) item.put("statusDetail", detail)
                    break
                }
            }
            prefs.edit().putString(KEY_SCHEDULED_SMS_JOBS_JSON, array.toString()).apply()
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
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_SCHEDULED_SMS_JOBS_JSON, null) ?: return
                val array = try { JSONArray(raw) } catch (_: Exception) { return }

                var targetJob: JSONObject? = null
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    if (item.optString("jobId") == jobId) {
                        targetJob = item
                        break
                    }
                }

                if (targetJob != null) {
                    val scheduledMs = targetJob.optLong("timestampMs", 0L)
                    val now = System.currentTimeMillis()
                    if (now - scheduledMs > MAX_STALE_WINDOW_MS && scheduledMs > 0) {
                        Log.w(TAG, "Alarm fired >12h past scheduled time. Expiring job $jobId.")
                        updateJobStatus(context, jobId, "expired", "Alarm triggered too late (>12h)")
                        recordAttempt(context, jobId, "expired", "Stale alarm trigger")
                        return
                    }
                    executeSmsSend(context, targetJob)
                }
            }

            ACTION_SMS_SENT -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
                val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
                val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1)

                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Log.i(TAG, "SMS part $partIndex/$totalParts sent successfully for job $jobId.")
                        if (partIndex == totalParts - 1) {
                            updateJobStatus(context, jobId, "acceptedByCarrier", "Message accepted by cellular network")
                            recordAttempt(context, jobId, "acceptedByCarrier", "All parts sent to cellular tower")
                        }
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        Log.e(TAG, "SMS generic failure for job $jobId.")
                        updateJobStatus(context, jobId, "failed", "Generic carrier failure")
                        recordAttempt(context, jobId, "failed", "RESULT_ERROR_GENERIC_FAILURE")
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        Log.e(TAG, "SMS no service for job $jobId.")
                        updateJobStatus(context, jobId, "failed", "No cellular service")
                        recordAttempt(context, jobId, "failed", "RESULT_ERROR_NO_SERVICE")
                    }
                    SmsManager.RESULT_ERROR_NULL_PDU -> {
                        Log.e(TAG, "SMS null PDU for job $jobId.")
                        updateJobStatus(context, jobId, "failed", "Null PDU")
                        recordAttempt(context, jobId, "failed", "RESULT_ERROR_NULL_PDU")
                    }
                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        Log.e(TAG, "SMS radio off for job $jobId.")
                        updateJobStatus(context, jobId, "failed", "Airplane mode / Radio off")
                        recordAttempt(context, jobId, "failed", "RESULT_ERROR_RADIO_OFF")
                    }
                    else -> {
                        Log.e(TAG, "SMS send failed with code $resultCode for job $jobId.")
                        updateJobStatus(context, jobId, "failed", "Carrier error code $resultCode")
                        recordAttempt(context, jobId, "failed", "Error code $resultCode")
                    }
                }
            }

            ACTION_SMS_DELIVERED -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
                val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
                val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1)

                if (resultCode == Activity.RESULT_OK) {
                    Log.i(TAG, "SMS delivery receipt confirmed for job $jobId part $partIndex/$totalParts.")
                    if (partIndex == totalParts - 1) {
                        updateJobStatus(context, jobId, "delivered", "Carrier delivery report confirmed")
                        recordAttempt(context, jobId, "delivered", "Delivery receipt verified")
                    }
                } else {
                    Log.w(TAG, "SMS delivery receipt indicated failure or unknown for job $jobId: $resultCode")
                    recordAttempt(context, jobId, "deliveryUnconfirmed", "Delivery receipt status code: $resultCode")
                }
            }
        }
    }
}
