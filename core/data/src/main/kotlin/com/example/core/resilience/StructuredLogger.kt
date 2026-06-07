package com.example.core.resilience

import android.util.Log

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val tag: String,
    val message: String,
    val level: LogLevel,
    val throwable: Throwable? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val extras: Map<String, String> = emptyMap(),
)

object StructuredLogger {
    private const val MAX_ENTRIES = 200
    private val history = mutableListOf<LogEntry>()

    fun d(tag: String, message: String, extras: Map<String, String> = emptyMap()) {
        log(LogEntry(tag, message, LogLevel.DEBUG, extras = extras))
        Log.d(tag, formatMessage(message, extras))
    }

    fun i(tag: String, message: String, extras: Map<String, String> = emptyMap()) {
        log(LogEntry(tag, message, LogLevel.INFO, extras = extras))
        Log.i(tag, formatMessage(message, extras))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null, extras: Map<String, String> = emptyMap()) {
        log(LogEntry(tag, message, LogLevel.WARN, throwable, extras = extras))
        if (throwable != null) Log.w(tag, formatMessage(message, extras), throwable)
        else Log.w(tag, formatMessage(message, extras))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null, extras: Map<String, String> = emptyMap()) {
        log(LogEntry(tag, message, LogLevel.ERROR, throwable, extras = extras))
        if (throwable != null) Log.e(tag, formatMessage(message, extras), throwable)
        else Log.e(tag, formatMessage(message, extras))
    }

    private fun log(entry: LogEntry) {
        synchronized(history) {
            history.add(entry)
            if (history.size > MAX_ENTRIES) history.removeAt(0)
        }
    }

    fun getRecent(count: Int = 50): List<LogEntry> {
        synchronized(history) {
            return history.takeLast(count)
        }
    }

    fun getErrors(): List<LogEntry> {
        synchronized(history) {
            return history.filter { it.level == LogLevel.ERROR }
        }
    }

    private fun formatMessage(message: String, extras: Map<String, String>): String {
        if (extras.isEmpty()) return message
        return "$message ${extras.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
    }
}
