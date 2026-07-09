package com.example.ui.viewmodel

import android.content.Context
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
    @ApplicationContext appContext: Context,
    private val preferencesRepository: PreferencesRepository,
) {
    private val deviceCapabilityReader = DeviceCapabilityReader(appContext)

    open fun snapshot(): WishPreviewDeviceReadinessSnapshot {
        return WishPreviewDeviceReadinessSnapshot(
            smsAllowed = runCatching { deviceCapabilityReader.hasSmsPermission() }.getOrDefault(false),
            whatsAppConsentGranted = runCatching {
                preferencesRepository.isWhatsAppAutomationConsentGranted()
            }.getOrDefault(false),
            whatsAppAccessibilityEnabled = runCatching {
                deviceCapabilityReader.isWhatsAppAutomationServiceEnabled()
            }.getOrDefault(false),
            whatsAppInstalled = runCatching { deviceCapabilityReader.isWhatsAppInstalled() }.getOrDefault(false),
        )
    }
}
