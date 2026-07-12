package com.yashsomani.birthdayautopilot.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DatabaseKeyManager(
  private val context: Context,
  private val secureRandom: SecureRandom = SecureRandom(),
) {
  private val keyStore: KeyStore
    get() = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

  private val wrappedKeyFile: AtomicFile
    get() = AtomicFile(context.noBackupFilesDir.resolve(WRAPPED_KEY_FILE))

  @Synchronized
  @Throws(StorageKeyUnavailableException::class)
  fun getOrCreatePassphrase(databaseAlreadyExists: Boolean): ByteArray {
    val file = wrappedKeyFile.baseFile
    if (file.exists()) {
      val wrappingKey = existingWrappingKey()
        ?: throw StorageKeyUnavailableException("keystore-key-missing")
      return unwrap(readWrappedBlob(), wrappingKey)
    }

    if (databaseAlreadyExists) {
      throw StorageKeyUnavailableException("wrapped-key-missing")
    }

    val wrappingKey = existingWrappingKey() ?: createWrappingKey()
    val passphrase = ByteArray(PASSPHRASE_BYTES).also(secureRandom::nextBytes)
    try {
      writeWrappedBlob(wrap(passphrase, wrappingKey))
      return passphrase
    } catch (error: Exception) {
      passphrase.fill(0)
      throw StorageKeyUnavailableException("wrapped-key-create-failed", error)
    }
  }

  @Synchronized
  @Throws(StorageKeyUnavailableException::class)
  fun clear() {
    try {
      wrappedKeyFile.delete()
      check(!wrappedKeyFile.baseFile.exists())
      keyStore.deleteEntry(KEY_ALIAS)
      check(!keyStore.containsAlias(KEY_ALIAS))
    } catch (error: Exception) {
      throw StorageKeyUnavailableException("storage-key-clear-failed", error)
    }
  }

  private fun existingWrappingKey(): SecretKey? = try {
    keyStore.getKey(KEY_ALIAS, null) as? SecretKey
  } catch (error: Exception) {
    throw StorageKeyUnavailableException("keystore-read-failed", error)
  }

  private fun createWrappingKey(): SecretKey = try {
    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
      init(
        KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .setRandomizedEncryptionRequired(true)
          .setUserAuthenticationRequired(false)
          .build(),
      )
      generateKey()
    }
  } catch (error: Exception) {
    throw StorageKeyUnavailableException("keystore-key-create-failed", error)
  }

  private fun wrap(passphrase: ByteArray, key: SecretKey): WrappedBlob = try {
    val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    WrappedBlob(iv = cipher.iv.copyOf(), ciphertext = cipher.doFinal(passphrase))
  } catch (error: Exception) {
    throw StorageKeyUnavailableException("wrapped-key-encrypt-failed", error)
  }

  private fun unwrap(blob: WrappedBlob, key: SecretKey): ByteArray = try {
    val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, blob.iv))
    cipher.doFinal(blob.ciphertext).also { passphrase ->
      if (passphrase.size != PASSPHRASE_BYTES) {
        passphrase.fill(0)
        throw StorageKeyUnavailableException("wrapped-key-size-invalid")
      }
    }
  } catch (error: StorageKeyUnavailableException) {
    throw error
  } catch (error: Exception) {
    throw StorageKeyUnavailableException("wrapped-key-decrypt-failed", error)
  }

  private fun readWrappedBlob(): WrappedBlob = try {
    wrappedKeyFile.openRead().use { input ->
      DataInputStream(input).use { data ->
        val magic = ByteArray(MAGIC.size)
        data.readFully(magic)
        check(magic.contentEquals(MAGIC))
        check(data.readUnsignedByte() == FORMAT_VERSION)
        val ivLength = data.readUnsignedByte()
        check(ivLength in MIN_IV_BYTES..MAX_IV_BYTES)
        val ciphertextLength = data.readUnsignedShort()
        check(ciphertextLength in MIN_CIPHERTEXT_BYTES..MAX_CIPHERTEXT_BYTES)
        val iv = ByteArray(ivLength).also(data::readFully)
        val ciphertext = ByteArray(ciphertextLength).also(data::readFully)
        check(data.read() == -1)
        WrappedBlob(iv, ciphertext)
      }
    }
  } catch (error: Exception) {
    throw StorageKeyUnavailableException("wrapped-key-file-invalid", error)
  }

  private fun writeWrappedBlob(blob: WrappedBlob) {
    val bytes = ByteArrayOutputStream().use { buffer ->
      DataOutputStream(buffer).use { data ->
        data.write(MAGIC)
        data.writeByte(FORMAT_VERSION)
        data.writeByte(blob.iv.size)
        data.writeShort(blob.ciphertext.size)
        data.write(blob.iv)
        data.write(blob.ciphertext)
      }
      buffer.toByteArray()
    }

    // Parse before persisting so malformed bounds can never become durable.
    DataInputStream(ByteArrayInputStream(bytes)).use { data ->
      val magic = ByteArray(MAGIC.size).also(data::readFully)
      check(magic.contentEquals(MAGIC))
    }

    val output = wrappedKeyFile.startWrite()
    try {
      output.write(bytes)
      output.flush()
      output.fd.sync()
      wrappedKeyFile.finishWrite(output)
    } catch (error: Exception) {
      wrappedKeyFile.failWrite(output)
      throw error
    } finally {
      bytes.fill(0)
    }
  }

  private data class WrappedBlob(val iv: ByteArray, val ciphertext: ByteArray)

  private companion object {
    const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    const val KEY_ALIAS = "birthday-autopilot-db-wrap-v1"
    const val WRAPPED_KEY_FILE = "birthday-db-key-v1.bin"
    const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_BITS = 128
    const val FORMAT_VERSION = 1
    const val PASSPHRASE_BYTES = 32
    const val MIN_IV_BYTES = 12
    const val MAX_IV_BYTES = 32
    const val MIN_CIPHERTEXT_BYTES = PASSPHRASE_BYTES + (GCM_TAG_BITS / 8)
    const val MAX_CIPHERTEXT_BYTES = 256
    val MAGIC = byteArrayOf(0x42, 0x41, 0x4B, 0x31)
  }
}
