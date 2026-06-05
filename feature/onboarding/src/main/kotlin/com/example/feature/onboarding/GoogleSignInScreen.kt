package com.example.feature.onboarding

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.core.prefs.SecurePrefs
import com.example.ui.components.PrimaryButton
import com.example.ui.components.StandardCard
import com.example.ui.theme.DarkSlate
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GoogleSignInScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { SecurePrefs(context) }
    var inProgress by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        inProgress = false
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account?.account != null) {
                inProgress = true
                scope.launch(Dispatchers.IO) {
                        try {
                            val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                                context,
                                account.account!!,
                                "oauth2:https://www.googleapis.com/auth/contacts.readonly"
                            )
                            prefs.setGoogleOAuthToken(token)
                            withContext(Dispatchers.Main) {
                                inProgress = false
                                navController.navigate("contacts_perm")
                            }
                        } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            inProgress = false
                            errorMsg = "Auth token fetch failed: ${e.localizedMessage}"
                        }
                    }
                }
            } else {
                errorMsg = "Logged-in account is empty"
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            errorMsg = "Sign In failed with status: ${e.statusCode}"
        }
    }

    OnboardingWrapper(
        title = "Establish Google Link",
        subtitle = "Connect Google to download missing birthdays, emails, and address logs automatically to save tedious typing. No credentials are sent to external backends.",
        currentStep = 2,
        onNext = {
            navController.navigate("contacts_perm")
        },
        nextText = "Proceed manually (Skip)",
        onBack = { navController.popBackStack() },
        onSkip = { navController.navigate("contacts_perm") },
        skipText = "Skip Sync"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSlate.copy(alpha = 0.7f))
                    .border(1.dp, GlassEdge, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = "Google Contacts",
                        tint = NeonViolet,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Secure Address Syncer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Downloads phone book nodes & dates perfectly",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            AnimatedVisibility(visible = errorMsg != null) {
                if (errorMsg != null) {
                    StandardCard(
                        modifier = Modifier.fillMaxWidth(),
                        padding = 12.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, "Error", tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                errorMsg!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (inProgress) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    "Requesting secure token...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                PrimaryButton(
                    text = "Authenticate Google Account",
                    icon = Icons.Default.AccountCircle,
                    onClick = {
                        inProgress = true
                        errorMsg = null
                        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                        )
                            .requestEmail()
                            .requestIdToken("339889410493-g5klr4838kfibddoqvk1rbbt39dblffp.apps.googleusercontent.com")
                            .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/contacts.readonly"))
                            .build()
                        val mGoogleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                        launcher.launch(mGoogleSignInClient.signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
