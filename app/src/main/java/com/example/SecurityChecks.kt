package com.example

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SecurityChecks {
    fun checkCertificatePinExpiry() {
        try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val expiryDate = format.parse("2027-06-01")
            val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000

            if (expiryDate != null) {
                val now = Date().time
                if (expiryDate.time - now < thirtyDaysInMillis) {
                    Log.w("SecurityChecks", "WARNING: Certificate pins in network_security_config.xml will expire soon! Update before 2027-06-01.")
                }
            }
        } catch (e: Exception) {
            Log.e("SecurityChecks", "Error checking certificate pin expiry", e)
        }
    }
}
