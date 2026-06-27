package com.example

import android.util.Log
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

object SecurityChecks {
    const val PIN_EXPIRY_DATE = "2027-06-01"
    const val PIN_EXPIRY_RELEASE_GATE_DAYS = 60L
    private const val TAG = "SecurityChecks"

    fun checkCertificatePinExpiry() {
        try {
            val daysUntilExpiry = daysUntilCertificatePinExpiry()
            if (daysUntilExpiry <= PIN_EXPIRY_RELEASE_GATE_DAYS) {
                Log.w(
                    TAG,
                    certificatePinExpiryMessage(daysUntilExpiry),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking certificate pin expiry", e)
        }
    }

    fun certificatePinExpiryDate(): LocalDate = LocalDate.parse(PIN_EXPIRY_DATE)

    fun daysUntilCertificatePinExpiry(
        asOfDate: LocalDate = LocalDate.now(ZoneOffset.UTC),
        expiryDate: LocalDate = certificatePinExpiryDate(),
    ): Long = ChronoUnit.DAYS.between(asOfDate, expiryDate)

    fun certificatePinsPassReleaseGate(
        asOfDate: LocalDate = LocalDate.now(ZoneOffset.UTC),
        expiryDate: LocalDate = certificatePinExpiryDate(),
    ): Boolean {
        return daysUntilCertificatePinExpiry(asOfDate, expiryDate) > PIN_EXPIRY_RELEASE_GATE_DAYS
    }

    fun certificatePinExpiryMessage(
        daysUntilExpiry: Long,
        expiryDate: LocalDate = certificatePinExpiryDate(),
    ): String {
        return "Certificate pins in network_security_config.xml expire in $daysUntilExpiry days " +
            "on $expiryDate. Update pins at least $PIN_EXPIRY_RELEASE_GATE_DAYS days before expiry."
    }
}
