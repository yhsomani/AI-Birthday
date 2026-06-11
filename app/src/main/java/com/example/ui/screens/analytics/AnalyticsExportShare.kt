package com.example.ui.screens.analytics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.R
import com.example.domain.service.AnalyticsReport
import java.io.File

internal object AnalyticsExportShare {
    private const val EXPORT_DIR = "analytics_exports"
    private const val DEFAULT_FILE_NAME = "relateai-relationship-report.csv"

    fun createSendIntent(context: Context, report: AnalyticsReport): Intent {
        val exportFile = writeReportToCache(context, report)
        val exportUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile,
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = report.mimeType
            putExtra(Intent.EXTRA_TITLE, exportFile.name)
            putExtra(Intent.EXTRA_SUBJECT, exportFile.name)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.analytics_export_share_body))
            putExtra(Intent.EXTRA_STREAM, exportUri)
            clipData = ClipData.newUri(context.contentResolver, exportFile.name, exportUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writeReportToCache(context: Context, report: AnalyticsReport): File {
        val exportDir = File(context.cacheDir, EXPORT_DIR).apply {
            mkdirs()
        }
        val fileName = report.fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { DEFAULT_FILE_NAME }
        return File(exportDir, fileName).apply {
            writeText(report.content)
        }
    }
}
