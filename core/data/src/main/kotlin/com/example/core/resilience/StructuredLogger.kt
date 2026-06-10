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
        val entry = redactedEntry(LogEntry(tag, message, LogLevel.DEBUG, extras = extras))
        log(entry)
        Log.d(tag, formatMessage(entry.message, entry.extras))
    }

    fun i(tag: String, message: String, extras: Map<String, String> = emptyMap()) {
        val entry = redactedEntry(LogEntry(tag, message, LogLevel.INFO, extras = extras))
        log(entry)
        Log.i(tag, formatMessage(entry.message, entry.extras))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null, extras: Map<String, String> = emptyMap()) {
        val entry = redactedEntry(LogEntry(tag, message, LogLevel.WARN, throwable, extras = extras))
        log(entry)
        Log.w(tag, formatMessage(entry.message, entry.extras))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null, extras: Map<String, String> = emptyMap()) {
        val entry = redactedEntry(LogEntry(tag, message, LogLevel.ERROR, throwable, extras = extras))
        log(entry)
        Log.e(tag, formatMessage(entry.message, entry.extras))
    }

    private fun redactedEntry(entry: LogEntry): LogEntry {
        val throwableExtras = entry.throwable?.let { throwable ->
            buildMap {
                put("exception", throwable.javaClass.simpleName)
                throwable.message
                    ?.takeIf { it.isNotBlank() }
                    ?.let { put("exceptionMessage", SensitiveLogRedactor.redact(it)) }
            }
        } ?: emptyMap()

        return entry.copy(
            message = SensitiveLogRedactor.redact(entry.message),
            throwable = null,
            extras = entry.extras.mapValues { (_, value) -> SensitiveLogRedactor.redact(value) } + throwableExtras,
        )
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

    fun clearForTests() {
        synchronized(history) {
            history.clear()
        }
    }

    private fun formatMessage(message: String, extras: Map<String, String>): String {
        if (extras.isEmpty()) return message
        return "$message ${extras.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
    }
}
