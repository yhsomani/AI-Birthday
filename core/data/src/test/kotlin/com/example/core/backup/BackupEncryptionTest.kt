package com.example.core.backup

import android.util.Base64
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [34])
class BackupEncryptionTest {

    @Test
    fun encryptDecrypt_roundTripRestoresPlainText() {
        val encrypted = BackupEncryption.encrypt("relationship data", "Abc12345!")

        val decrypted = BackupEncryption.decrypt(encrypted, "Abc12345!")

        assertEquals("relationship data", decrypted)
    }

    @Test
    fun decrypt_wrongPassphraseFailsAuthentication() {
        val encrypted = BackupEncryption.encrypt("relationship data", "Abc12345!")

        assertThrows(AEADBadTagException::class.java) {
            BackupEncryption.decrypt(encrypted, "Wrong123!")
        }
    }

    @Test
    fun decrypt_malformedBase64FailsValidation() {
        assertThrows(BackupEncryptionException::class.java) {
            BackupEncryption.decrypt("not-valid-base64", "Abc12345!")
        }
    }

    @Test
    fun decrypt_tooShortPayloadFailsValidation() {
        val tooShort = Base64.encodeToString(ByteArray(10), Base64.NO_WRAP)

        assertThrows(BackupEncryptionException::class.java) {
            BackupEncryption.decrypt(tooShort, "Abc12345!")
        }
    }
}
