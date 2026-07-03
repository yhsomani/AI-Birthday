package com.example.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth

internal class AutomationSetupCapabilityProbe(
    private val context: Context,
) {
    private companion object {
        const val GOOGLE_CONTACTS_SCOPE_URI = "https://www.googleapis.com/auth/contacts.readonly"
        val GOOGLE_CONTACTS_SCOPE = Scope(GOOGLE_CONTACTS_SCOPE_URI)
    }

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun isWhatsAppAutomationServiceEnabled(): Boolean {
        val expectedService = "${context.packageName}/com.example.core.accessibility.WhatsAppAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabledServices.split(':').any {
            it.equals(expectedService, ignoreCase = true)
        }
    }

    fun isWhatsAppInstalled(): Boolean {
        return isPackageInstalled("com.whatsapp") || isPackageInstalled("com.whatsapp.w4b")
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

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
    }
}
