package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.core.auth.BiometricAuthManager
import com.example.core.ui.theme.RelateAITheme
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.service.PreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    private lateinit var biometricAuthManager: BiometricAuthManager
    private var biometricSessionUnlocked = false
    private var biometricPromptInFlight = false
    private var biometricGateState by mutableStateOf<BiometricGateState>(BiometricGateState.Unlocked)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        biometricAuthManager = BiometricAuthManager(this)
        refreshBiometricGate(autoPrompt = false)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {}

        setContent {
            RelateAITheme {
                when (val gateState = biometricGateState) {
                    BiometricGateState.Unlocked -> {
                        RelateApp(
                            onRequestCorePermissions = {
                                val permissionsToRequest = getCorePermissionsToRequest()
                                if (permissionsToRequest.isNotEmpty()) {
                                    requestPermissionLauncher.launch(permissionsToRequest)
                                }
                            },
                            isSignedIn = {
                                preferencesRepository.isLocalOnlyModeEnabled() ||
                                    FirebaseAuth.getInstance().currentUser != null
                            },
                        )
                    }
                    else -> {
                        BiometricLockGate(
                            state = gateState,
                            onUnlock = ::authenticateWithBiometric,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBiometricGate(autoPrompt = true)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && preferencesRepository.isBiometricLockEnabled()) {
            biometricSessionUnlocked = false
        }
    }

    private fun refreshBiometricGate(autoPrompt: Boolean) {
        val isEnabled = runCatching { preferencesRepository.isBiometricLockEnabled() }.getOrDefault(false)
        val isAvailable = if (isEnabled) biometricAuthManager.isAvailable() else true
        biometricGateState = when (
            BiometricLockPolicy.resolve(
                isEnabled = isEnabled,
                isAuthenticatorAvailable = isAvailable,
                isSessionUnlocked = biometricSessionUnlocked,
            )
        ) {
            BiometricLockRequirement.UNLOCKED -> BiometricGateState.Unlocked
            BiometricLockRequirement.UNAVAILABLE -> BiometricGateState.Unavailable
            BiometricLockRequirement.LOCKED -> BiometricGateState.Locked
        }
        if (autoPrompt && biometricGateState == BiometricGateState.Locked) {
            authenticateWithBiometric()
        }
    }

    private fun authenticateWithBiometric() {
        if (biometricPromptInFlight) return
        if (!preferencesRepository.isBiometricLockEnabled()) {
            biometricSessionUnlocked = true
            biometricGateState = BiometricGateState.Unlocked
            return
        }
        if (!biometricAuthManager.isAvailable()) {
            biometricGateState = BiometricGateState.Unavailable
            return
        }

        biometricPromptInFlight = true
        biometricGateState = BiometricGateState.Authenticating
        biometricAuthManager.authenticate(
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            onSuccess = {
                biometricPromptInFlight = false
                biometricSessionUnlocked = true
                biometricGateState = BiometricGateState.Unlocked
            },
            onError = { _, error ->
                biometricPromptInFlight = false
                biometricGateState = BiometricGateState.Error(error)
            },
            onFailed = {
                biometricGateState = BiometricGateState.Authenticating
            },
        )
    }

    private fun getCorePermissionsToRequest(): Array<String> {
        return buildList {
            if (
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.SEND_SMS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.SEND_SMS)
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
}

sealed interface BiometricGateState {
    data object Unlocked : BiometricGateState
    data object Locked : BiometricGateState
    data object Authenticating : BiometricGateState
    data object Unavailable : BiometricGateState
    data class Error(val message: String) : BiometricGateState
}

@Composable
private fun BiometricLockGate(
    state: BiometricGateState,
    onUnlock: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(RelateSpacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RelateSpacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.biometric_lock_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when (state) {
                        BiometricGateState.Authenticating -> stringResource(R.string.biometric_lock_authenticating)
                        BiometricGateState.Unavailable -> stringResource(R.string.biometric_lock_unavailable)
                        is BiometricGateState.Error -> stringResource(R.string.biometric_lock_error, state.message)
                        else -> stringResource(R.string.biometric_lock_message)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state == BiometricGateState.Authenticating) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(RelateSize.progressIndicator),
                    )
                } else {
                    Button(
                        onClick = onUnlock,
                        enabled = state != BiometricGateState.Unavailable,
                    ) {
                        Text(
                            text = if (state is BiometricGateState.Error) {
                                stringResource(R.string.biometric_lock_retry)
                            } else {
                                stringResource(R.string.biometric_lock_unlock)
                            },
                        )
                    }
                }
            }
        }
    }
}
