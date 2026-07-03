package com.example.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.domain.service.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class WishPreviewDeviceReadinessSnapshot(
    val smsAllowed: Boolean = false,
    val whatsAppConsentGranted: Boolean = false,
    val whatsAppAccessibilityEnabled: Boolean = false,
    val whatsAppInstalled: Boolean = false,
)

open class WishPreviewDeviceReadinessReader @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val preferencesRepository: PreferencesRepository,
) {
    open fun snapshot(): WishPreviewDeviceReadinessSnapshot {
        return WishPreviewDeviceReadinessSnapshot(
            smsAllowed = runCatching { hasSmsPermission() }.getOrDefault(false),
            whatsAppConsentGranted = runCatching {
                preferencesRepository.isWhatsAppAutomationConsentGranted()
            }.getOrDefault(false),
            whatsAppAccessibilityEnabled = runCatching {
                isWhatsAppAutomationServiceEnabled()
            }.getOrDefault(false),
            whatsAppInstalled = runCatching { isWhatsAppInstalled() }.getOrDefault(false),
        )
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun isWhatsAppAutomationServiceEnabled(): Boolean {
        val expectedService = "${appContext.packageName}/com.example.core.accessibility.WhatsAppAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabledServices.split(':').any {
            it.equals(expectedService, ignoreCase = true)
        }
    }

    private fun isWhatsAppInstalled(): Boolean {
        return isPackageInstalled("com.whatsapp") || isPackageInstalled("com.whatsapp.w4b")
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            appContext.packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
    }
}
