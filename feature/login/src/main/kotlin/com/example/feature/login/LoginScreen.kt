package com.example.feature.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ui.theme.DarkSlate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import androidx.compose.material3.TextButton

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var inProgress by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                viewModel.signInWithGoogle(account) { success ->
                    inProgress = false
                    if (success) {
                        onLoginSuccess()
                    } else {
                        errorMsg = "Firebase Authentication failed"
                    }
                }
            } else {
                inProgress = false
                errorMsg = "Google Sign-In returned null account"
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            inProgress = false
            errorMsg = "Sign In failed (code ${e.statusCode}): ${e.message ?: e.localizedMessage}. Please register SHA-1."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        NeonViolet.copy(alpha = 0.03f),
                        ObsidianBlack
                    ),
                    radius = 800f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Logo
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSlate.copy(alpha = 0.7f))
                    .border(1.dp, GlassEdge, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(32.dp)) {
                    val w = size.width; val h = size.height
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.5f, h * 0.85f)
                        cubicTo(w * 0.15f, h * 0.55f, 0f, h * 0.3f, w * 0.25f, h * 0.15f)
                        cubicTo(w * 0.35f, h * 0.08f, w * 0.45f, h * 0.12f, w * 0.5f, h * 0.25f)
                        cubicTo(w * 0.55f, h * 0.12f, w * 0.65f, h * 0.08f, w * 0.75f, h * 0.15f)
                        cubicTo(w * 1f, h * 0.3f, w * 0.85f, h * 0.55f, w * 0.5f, h * 0.85f)
                        close()
                    }
                    // Outer glow
                    drawPath(
                        path = path,
                        color = NeonViolet.copy(alpha = 0.2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    // Inner stroke
                    drawPath(
                        path = path,
                        color = NeonViolet,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "RelateAI",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Welcome headline
            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sign in to continue managing your relationships",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Google Sign-In button
            if (inProgress) {
                CircularProgressIndicator(color = NeonViolet)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSlate)
                        .border(1.dp, GlassEdge, RoundedCornerShape(12.dp))
                        .clickable {
                            inProgress = true
                            errorMsg = null
                            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                            )
                                .requestEmail()
                                .requestIdToken("339889410493-g5klr4838kfibddoqvk1rbbt39dblffp.apps.googleusercontent.com")
                                .build()
                            val mGoogleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                            launcher.launch(mGoogleSignInClient.signInIntent)
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Google "G" icon placeholder
                    Text(
                        text = "G",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = Color(0xFF4285F4)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary
                    )
                }
            }

            errorMsg?.let { msg ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Terms notice
            Text(
                text = buildAnnotatedString {
                    append("By continuing, you agree to our ")
                    withStyle(SpanStyle(color = NeonViolet)) { append("Terms of Service") }
                    append(" and ")
                    withStyle(SpanStyle(color = NeonViolet)) { append("Privacy Policy") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Bypass button for testing
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { onLoginSuccess() }
            ) {
                Text(
                    text = "Bypass for Testing (Guest Mode)",
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricCyan
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Data assurance
            Row(
                modifier = Modifier.padding(bottom = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Your data stays on your device",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
