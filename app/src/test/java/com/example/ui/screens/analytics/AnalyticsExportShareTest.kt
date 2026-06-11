package com.example.ui.screens.analytics

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.domain.service.AnalyticsReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AnalyticsExportShareTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun createSendIntent_attachesReadableCsvFile() {
        val report = AnalyticsReport(
            fileName = "../relationship.csv",
            mimeType = "text/csv",
            content = "section,metric,value\nsummary,total_contacts,3\n",
        )

        val intent = AnalyticsExportShare.createSendIntent(context, report)
        val streamUri = intent.streamUri()

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/csv", intent.type)
        assertEquals("relationship.csv", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals("relationship.csv", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(
            context.getString(R.string.analytics_export_share_body),
            intent.getStringExtra(Intent.EXTRA_TEXT),
        )
        assertNotNull(streamUri)
        assertEquals("${context.packageName}.fileprovider", streamUri?.authority)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(intent.clipData)
        assertEquals(report.content, context.readText(streamUri))
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM)

    private fun Context.readText(uri: Uri?): String {
        requireNotNull(uri)
        return contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input).bufferedReader().readText()
        }
    }
}
