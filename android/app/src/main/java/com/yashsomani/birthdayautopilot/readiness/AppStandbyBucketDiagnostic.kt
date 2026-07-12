package com.yashsomani.birthdayautopilot.readiness

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

/**
 * Content-free support evidence for the app's current App Standby bucket.
 *
 * This signal is deliberately not part of [AndroidReadinessSnapshot], [DistributionSignals], or
 * [ReadinessInputs]. Merely opening the app can promote its bucket, and the OS may change it again,
 * so a sampled value cannot prove that unattended automation is ready.
 */
internal enum class AppStandbyBucketDiagnostic(val wireCode: String) {
  EXEMPTED("app-standby-bucket-exempted"),
  ACTIVE("app-standby-bucket-active"),
  WORKING_SET("app-standby-bucket-working-set"),
  FREQUENT("app-standby-bucket-frequent"),
  RARE("app-standby-bucket-rare"),
  RESTRICTED("app-standby-bucket-restricted"),
  NEVER("app-standby-bucket-never"),
  UNKNOWN("app-standby-bucket-unknown"),
  API_UNSUPPORTED("app-standby-bucket-api-unsupported"),
  SERVICE_UNAVAILABLE("app-standby-bucket-service-unavailable"),
  ACCESS_DENIED("app-standby-bucket-access-denied"),
  RUNTIME_UNAVAILABLE("app-standby-bucket-runtime-unavailable"),
  PLATFORM_UNAVAILABLE("app-standby-bucket-platform-unavailable"),
  READ_FAILED("app-standby-bucket-read-failed"),
}

/** Reads only the calling app's bucket and never requests usage-access privileges. */
internal class AndroidAppStandbyBucketDiagnosticReader(context: Context) {
  private val appContext = context.applicationContext

  fun read(): AppStandbyBucketDiagnostic = AppStandbyBucketDiagnosticPolicy.evaluate(
    apiLevel = Build.VERSION.SDK_INT,
    readBucket = {
      appContext.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket
    },
  )
}

/** Pure mapping and failure containment kept independently testable on the host JVM. */
internal object AppStandbyBucketDiagnosticPolicy {
  fun evaluate(
    apiLevel: Int,
    readBucket: () -> Int?,
  ): AppStandbyBucketDiagnostic {
    if (apiLevel < MINIMUM_SUPPORTED_API) {
      return AppStandbyBucketDiagnostic.API_UNSUPPORTED
    }
    val bucket = try {
      readBucket()
    } catch (_: SecurityException) {
      return AppStandbyBucketDiagnostic.ACCESS_DENIED
    } catch (_: RuntimeException) {
      return AppStandbyBucketDiagnostic.RUNTIME_UNAVAILABLE
    } catch (_: Exception) {
      return AppStandbyBucketDiagnostic.READ_FAILED
    } catch (_: LinkageError) {
      return AppStandbyBucketDiagnostic.PLATFORM_UNAVAILABLE
    } ?: return AppStandbyBucketDiagnostic.SERVICE_UNAVAILABLE

    return when (bucket) {
      BUCKET_EXEMPTED -> AppStandbyBucketDiagnostic.EXEMPTED
      BUCKET_ACTIVE -> AppStandbyBucketDiagnostic.ACTIVE
      BUCKET_WORKING_SET -> AppStandbyBucketDiagnostic.WORKING_SET
      BUCKET_FREQUENT -> AppStandbyBucketDiagnostic.FREQUENT
      BUCKET_RARE -> AppStandbyBucketDiagnostic.RARE
      BUCKET_RESTRICTED -> AppStandbyBucketDiagnostic.RESTRICTED
      BUCKET_NEVER -> AppStandbyBucketDiagnostic.NEVER
      else -> AppStandbyBucketDiagnostic.UNKNOWN
    }
  }

  private const val MINIMUM_SUPPORTED_API = Build.VERSION_CODES.P

  // EXEMPTED and NEVER are platform bucket values but are hidden from the public SDK stub. Keep
  // the complete stable value set here so those legitimate observations do not become Unknown.
  private const val BUCKET_EXEMPTED = 5
  private const val BUCKET_ACTIVE = 10
  private const val BUCKET_WORKING_SET = 20
  private const val BUCKET_FREQUENT = 30
  private const val BUCKET_RARE = 40
  private const val BUCKET_RESTRICTED = 45
  private const val BUCKET_NEVER = 50
}
