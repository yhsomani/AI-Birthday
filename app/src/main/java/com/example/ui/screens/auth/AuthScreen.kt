package com.example.ui.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.BuildConfig
import com.example.R
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel

internal object AuthScreenTestTags {
    const val SIGN_IN_BUTTON = "auth_sign_in_button"
    const val DEV_BYPASS_BUTTON = "auth_dev_bypass_button"
    const val LOADING = "auth_loading"
    const val ERROR = "auth_error"
}

@Composable
fun AuthScreen(
    onAuthComplete: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleResult(result.resultCode, result.data)
    }

    LaunchedEffect(state.isSignedIn) {
        if (state.isSignedIn) {
            onAuthComplete()
        }
    }

    AuthContent(
        state = state,
        onSignIn = { viewModel.startGoogleSignIn { launcher.launch(it) } },
        onDevBypass = viewModel::bypassSignIn,
        showDevBypass = BuildConfig.DEBUG,
    )
}

@Composable
internal fun AuthContent(
    state: AuthUiState,
    onSignIn: () -> Unit,
    onDevBypass: () -> Unit,
    showDevBypass: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(RelateSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        Text(
            text = stringResource(R.string.auth_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(RelateSize.heroIcon))
        if (state.isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(AuthScreenTestTags.LOADING),
            )
        } else {
            Button(
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RelateSize.primaryButtonHeight)
                    .testTag(AuthScreenTestTags.SIGN_IN_BUTTON),
                shape = RoundedCornerShape(RelateRadius.control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    text = stringResource(R.string.auth_sign_in_google),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            if (showDevBypass) {
                Spacer(modifier = Modifier.height(RelateSpacing.lg))
                Button(
                    onClick = onDevBypass,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RelateSize.primaryButtonHeight)
                        .testTag(AuthScreenTestTags.DEV_BYPASS_BUTTON),
                    shape = RoundedCornerShape(RelateRadius.control),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.auth_dev_bypass),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.background,
                    )
                }
            }
        }
        state.error?.let { error ->
            Spacer(modifier = Modifier.height(RelateSpacing.md))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(AuthScreenTestTags.ERROR),
            )
        }
        Spacer(modifier = Modifier.height(RelateSize.minTouchTarget))
        Text(
            text = stringResource(R.string.auth_legal_agreement),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
