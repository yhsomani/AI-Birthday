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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.BuildConfig
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.ui.viewmodel.AuthViewModel

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "RelateAI",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
            ),
            color = RelatePrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Nurture your connections\nwith AI-powered wishes",
            style = MaterialTheme.typography.bodyLarge,
            color = RelateOnBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(64.dp))
        if (state.isLoading) {
            CircularProgressIndicator(color = RelatePrimary)
        } else {
            Button(
                onClick = { viewModel.startGoogleSignIn { launcher.launch(it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RelateSurfaceVariant,
                ),
            ) {
                Text(
                    text = "Sign in with Google",
                    style = MaterialTheme.typography.labelLarge,
                    color = RelateOnBackground,
                )
            }
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.bypassSignIn() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RelatePrimary,
                    ),
                ) {
                    Text(
                        text = "Bypass Sign-In (Dev)",
                        style = MaterialTheme.typography.labelLarge,
                        color = RelateDarkBackground,
                    )
                }
            }
        }
        state.error?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "By signing in, you agree to our Terms and Privacy Policy",
            style = MaterialTheme.typography.bodySmall,
            color = RelateOnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
