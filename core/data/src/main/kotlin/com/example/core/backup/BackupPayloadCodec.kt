package com.example.core.backup

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.security.MessageDigest

internal class BackupPayloadCodec {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi
        .adapter(BackupPayloadDto::class.java)
        .indent("  ")

    private val recordSnapshotAdapter = moshi
        .adapter(BackupRecordSnapshotDto::class.java)
        .indent("  ")

    fun toJson(payload: BackupPayloadDto): String = payloadAdapter.toJson(payload)

    fun fromJson(json: String): BackupPayloadDto? = payloadAdapter.fromJson(json)

    fun checksumFor(snapshot: BackupRecordSnapshotDto): String {
        val bytes = recordSnapshotAdapter.toJson(snapshot).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hasValidManifestChecksum(payload: BackupPayloadDto): Boolean {
        val manifest = payload.manifest ?: return true
        val expected = checksumFor(payload.toRecordSnapshot())
        return manifest.dataChecksumSha256.equals(expected, ignoreCase = true)
    }
}
