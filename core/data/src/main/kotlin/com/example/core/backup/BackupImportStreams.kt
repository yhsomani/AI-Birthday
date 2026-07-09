package com.example.core.backup

import java.io.InputStream

private const val MAX_IMPORT_BYTES = 25 * 1024 * 1024

internal fun readUtf8TextWithLimit(
    inputStream: InputStream,
    maxBytes: Int = MAX_IMPORT_BYTES,
): String {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = java.io.ByteArrayOutputStream()
    var totalBytes = 0
    while (true) {
        val bytesRead = inputStream.read(buffer)
        if (bytesRead == -1) break
        totalBytes += bytesRead
        if (totalBytes > maxBytes) {
            throw BackupFileTooLargeException()
        }
        output.write(buffer, 0, bytesRead)
    }
    return output.toString(Charsets.UTF_8.name())
}

internal class BackupFileTooLargeException : Exception()
