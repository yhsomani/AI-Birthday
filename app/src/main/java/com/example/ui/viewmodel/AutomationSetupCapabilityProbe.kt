package com.example.ui.viewmodel

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth

internal class AutomationSetupCapabilityProbe(
    private val context: Context,
) {
    private val deviceCapabilityReader = DeviceCapabilityReader(context)

    private companion object {
        const val GOOGLE_CONTACTS_SCOPE_URI = "https://www.googleapis.com/auth/contacts.readonly"
        val GOOGLE_CONTACTS_SCOPE = Scope(GOOGLE_CONTACTS_SCOPE_URI)
    }

    fun hasSmsPermission(): Boolean {
        return deviceCapabilityReader.hasSmsPermission()
    }

    fun hasNotificationPermission(): Boolean {
        return deviceCapabilityReader.hasNotificationPermission()
    }

    fun isWhatsAppAutomationServiceEnabled(): Boolean {
        return deviceCapabilityReader.isWhatsAppAutomationServiceEnabled()
    }

    fun isWhatsAppInstalled(): Boolean {
        return deviceCapabilityReader.isWhatsAppInstalled()
    }

    fun currentFirebaseUserOrNull() = runCatching {
        FirebaseAuth.getInstance().currentUser
    }.getOrNull()

    fun hasGoogleContactsAccess(hasCachedGoogleOAuthToken: Boolean): Boolean {
        if (hasCachedGoogleOAuthToken) return true
        val account = runCatching { GoogleSignIn.getLastSignedInAccount(context) }.getOrNull()
            ?: return false
        return runCatching { GoogleSignIn.hasPermissions(account, GOOGLE_CONTACTS_SCOPE) }
            .getOrDefault(false)
    }

}
