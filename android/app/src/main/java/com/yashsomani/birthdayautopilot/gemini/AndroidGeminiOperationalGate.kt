package com.yashsomani.birthdayautopilot.gemini

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.yashsomani.birthdayautopilot.BuildConfig
import com.yashsomani.birthdayautopilot.auth.AndroidIdentityConfigurationResolver
import com.yashsomani.birthdayautopilot.auth.IdentityConfigurationResult
import java.util.concurrent.Executor

/** Pure policy for the one native-only Gemini operational switch. */
internal object AndroidGeminiOperationalPolicy {
  const val PARAMETER_KEY = "gemini_suggestions_enabled"
  const val IN_APP_DEFAULT = false
  const val MINIMUM_FETCH_INTERVAL_SECONDS = 60L * 60L
  const val FIREBASE_FETCH_TIMEOUT_SECONDS = 8L
  const val LOCAL_COMPLETION_TIMEOUT_MILLIS = 10_000L

  fun acceptsActivatedValue(
    sourceIsRemote: Boolean,
    canonicalString: String,
    boolValue: Boolean,
  ): Boolean = sourceIsRemote && canonicalString == "true" && boolValue
}

internal interface GeminiOperationalGate {
  fun configureAfterFirebaseLaunch()

  fun refreshInBackground()

  fun foregroundSuggestionsEnabled(): Boolean
}

internal data class GeminiRemoteConfigActivatedValue(
  val sourceIsRemote: Boolean,
  val canonicalString: String,
  val boolValue: Boolean,
)

internal interface GeminiRemoteConfigClient {
  fun prepare(completion: (Boolean) -> Unit)

  fun activatedValue(): GeminiRemoteConfigActivatedValue

  fun fetchAndActivate(completion: (Boolean) -> Unit)
}

internal fun interface GeminiRemoteConfigClientFactory {
  fun create(): GeminiRemoteConfigClient?
}

internal fun interface GeminiOperationalTimeoutScheduler {
  fun schedule(delayMillis: Long, action: () -> Unit)
}

/**
 * Process-local, fail-closed Remote Config gate for Firebase AI Logic.
 *
 * Foreground authoring reads only a cached activated value. Configuration and refresh callbacks
 * are generation-fenced and bounded; no parameter, fetch result, installation identifier, or
 * provider detail crosses React Native or a log.
 */
internal class AndroidGeminiOperationalGate internal constructor(
  private val clientFactory: GeminiRemoteConfigClientFactory,
  private val timeoutScheduler: GeminiOperationalTimeoutScheduler,
) : GeminiOperationalGate {
  private val lock = Any()
  private var client: GeminiRemoteConfigClient? = null
  private var configured = false
  private var preparing = false
  private var fetchInFlight = false
  private var cachedActivatedEnabled = AndroidGeminiOperationalPolicy.IN_APP_DEFAULT
  private var preparationGeneration = 0L
  private var fetchGeneration = 0L

  constructor(context: Context) : this(
    clientFactory = AndroidFirebaseRemoteConfigClientFactory(context),
    timeoutScheduler = GeminiOperationalTimeoutScheduler { delayMillis, action ->
      Handler(Looper.getMainLooper()).postDelayed({ action() }, delayMillis)
    },
  )

  override fun configureAfterFirebaseLaunch() {
    val generation = synchronized(lock) {
      if (configured || preparing) return
      preparing = true
      preparationGeneration = incrementOrMax(preparationGeneration)
      preparationGeneration
    }
    val candidate = try {
      clientFactory.create()
    } catch (_: Exception) {
      null
    } catch (_: LinkageError) {
      null
    }
    if (candidate == null) {
      finishPreparation(generation, null, succeeded = false)
      return
    }
    try {
      candidate.prepare { succeeded ->
        finishPreparation(generation, candidate, succeeded)
      }
      timeoutScheduler.schedule(AndroidGeminiOperationalPolicy.LOCAL_COMPLETION_TIMEOUT_MILLIS) {
        finishPreparation(generation, candidate, succeeded = false)
      }
    } catch (_: Exception) {
      finishPreparation(generation, candidate, succeeded = false)
    } catch (_: LinkageError) {
      finishPreparation(generation, candidate, succeeded = false)
    }
  }

  override fun refreshInBackground() {
    val startConfiguration = synchronized(lock) { !configured && !preparing }
    if (startConfiguration) {
      configureAfterFirebaseLaunch()
      return
    }
    val state = synchronized(lock) {
      val current = client
      if (!configured || preparing || fetchInFlight || current == null) return
      fetchInFlight = true
      fetchGeneration = incrementOrMax(fetchGeneration)
      RefreshState(fetchGeneration, current)
    }
    try {
      state.client.fetchAndActivate { succeeded ->
        finishRefresh(state.generation, state.client, succeeded)
      }
      timeoutScheduler.schedule(AndroidGeminiOperationalPolicy.LOCAL_COMPLETION_TIMEOUT_MILLIS) {
        finishRefresh(state.generation, state.client, succeeded = false)
      }
    } catch (_: Exception) {
      finishRefresh(state.generation, state.client, succeeded = false)
    } catch (_: LinkageError) {
      finishRefresh(state.generation, state.client, succeeded = false)
    }
  }

  override fun foregroundSuggestionsEnabled(): Boolean = synchronized(lock) {
    configured && cachedActivatedEnabled
  }

  private fun finishPreparation(
    generation: Long,
    candidate: GeminiRemoteConfigClient?,
    succeeded: Boolean,
  ) {
    val shouldRefresh = synchronized(lock) {
      if (!preparing || preparationGeneration != generation) return
      preparing = false
      if (!succeeded || candidate == null) {
        client = null
        configured = false
        cachedActivatedEnabled = AndroidGeminiOperationalPolicy.IN_APP_DEFAULT
        false
      } else {
        client = candidate
        configured = true
        cachedActivatedEnabled = readEnabled(candidate)
        true
      }
    }
    if (shouldRefresh) refreshInBackground()
  }

  private fun finishRefresh(
    generation: Long,
    candidate: GeminiRemoteConfigClient,
    succeeded: Boolean,
  ) {
    val refreshedEnabled = if (succeeded) readEnabled(candidate) else null
    synchronized(lock) {
      if (
        !fetchInFlight ||
        fetchGeneration != generation ||
        client !== candidate
      ) return
      if (refreshedEnabled != null) cachedActivatedEnabled = refreshedEnabled
      fetchInFlight = false
    }
  }

  private fun readEnabled(candidate: GeminiRemoteConfigClient): Boolean {
    val value = try {
      candidate.activatedValue()
    } catch (_: Exception) {
      return false
    } catch (_: LinkageError) {
      return false
    }
    return AndroidGeminiOperationalPolicy.acceptsActivatedValue(
      sourceIsRemote = value.sourceIsRemote,
      canonicalString = value.canonicalString,
      boolValue = value.boolValue,
    )
  }

  private fun incrementOrMax(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1

  private data class RefreshState(
    val generation: Long,
    val client: GeminiRemoteConfigClient,
  )
}

private class AndroidFirebaseRemoteConfigClientFactory(context: Context) :
  GeminiRemoteConfigClientFactory {
  private val appContext = context.applicationContext
  private val callbackExecutor = ContextCompat.getMainExecutor(appContext)

  override fun create(): GeminiRemoteConfigClient? {
    val configuration = when (
      val resolved = AndroidIdentityConfigurationResolver(appContext, BuildConfig.APP_ENV).resolve()
    ) {
      is IdentityConfigurationResult.Ready -> resolved.configuration
      IdentityConfigurationResult.Missing -> return null
    }
    val remoteConfig = try {
      FirebaseRemoteConfig.getInstance(configuration.firebaseApp)
    } catch (_: Exception) {
      return null
    } catch (_: LinkageError) {
      return null
    }
    return AndroidFirebaseRemoteConfigClient(remoteConfig, callbackExecutor)
  }
}

private class AndroidFirebaseRemoteConfigClient(
  private val remoteConfig: FirebaseRemoteConfig,
  private val callbackExecutor: Executor,
) : GeminiRemoteConfigClient {
  override fun prepare(completion: (Boolean) -> Unit) {
    val settings = FirebaseRemoteConfigSettings.Builder()
      .setMinimumFetchIntervalInSeconds(
        AndroidGeminiOperationalPolicy.MINIMUM_FETCH_INTERVAL_SECONDS,
      )
      .setFetchTimeoutInSeconds(AndroidGeminiOperationalPolicy.FIREBASE_FETCH_TIMEOUT_SECONDS)
      .build()
    val settingsTask = remoteConfig.setConfigSettingsAsync(settings)
    val defaultsTask = remoteConfig.setDefaultsAsync(
      mapOf<String, Any>(
        AndroidGeminiOperationalPolicy.PARAMETER_KEY to
          AndroidGeminiOperationalPolicy.IN_APP_DEFAULT,
      ),
    )
    Tasks.whenAll(settingsTask, defaultsTask)
      .addOnCompleteListener(callbackExecutor) { task -> completion(task.isSuccessful) }
  }

  override fun activatedValue(): GeminiRemoteConfigActivatedValue {
    val value = remoteConfig.getValue(AndroidGeminiOperationalPolicy.PARAMETER_KEY)
    return GeminiRemoteConfigActivatedValue(
      sourceIsRemote = value.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
      canonicalString = value.asString(),
      boolValue = value.asBoolean(),
    )
  }

  override fun fetchAndActivate(completion: (Boolean) -> Unit) {
    remoteConfig.fetchAndActivate()
      .addOnCompleteListener(callbackExecutor) { task -> completion(task.isSuccessful) }
  }
}
