package com.example.ui.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.ElevatedCard
import com.example.ui.theme.RelateAIColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleCoachScreen(
    onNavigateBack: () -> Unit,
    onSaveTrainingText: ((String) -> Unit)?
) {
    var trainingText by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Style Coach", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Pulse graphic card representing AI analysis
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Voice DNA Analysis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Draw a custom canvas showing relationship/voice wave pulse
                    val infiniteTransition = rememberInfiniteTransition(label = "wave_pulse")
                    val pulseAnim by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(4000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "wave_rotation"
                    )

                    Canvas(modifier = Modifier.size(120.dp).padding(8.dp)) {
                        val center = this.center
                        val radius = 45.dp.toPx()
                        
                        // Draw outer glow ring
                        drawCircle(
                            color = RelateAIColors.Primary.copy(alpha = 0.15f),
                            radius = radius + 10.dp.toPx(),
                            style = Stroke(width = 4.dp.toPx())
                        )
                        
                        // Draw wave connections
                        for (i in 0 until 12) {
                            val angle = (i * 30 + pulseAnim).toDouble()
                            val rad = Math.toRadians(angle)
                            val offsetFactor = Math.sin(Math.toRadians(pulseAnim.toDouble() * 3 + i * 40)) * 6.dp.toPx()
                            val nodeRadius = radius + offsetFactor.toFloat()
                            
                            val startX = (center.x + Math.cos(rad) * radius).toFloat()
                            val startY = (center.y + Math.sin(rad) * radius).toFloat()
                            val endX = (center.x + Math.cos(rad) * nodeRadius).toFloat()
                            val endY = (center.y + Math.sin(rad) * nodeRadius).toFloat()
                            
                            drawLine(
                                color = RelateAIColors.Primary,
                                start = center,
                                end = androidx.compose.ui.geometry.Offset(endX, endY),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            
                            drawCircle(
                                color = if (i % 2 == 0) RelateAIColors.Secondary else RelateAIColors.Tertiary,
                                radius = 4.dp.toPx(),
                                center = androidx.compose.ui.geometry.Offset(endX, endY)
                            )
                        }
                        
                        // Center core
                        drawCircle(
                            color = RelateAIColors.Primary,
                            radius = 12.dp.toPx(),
                            center = center
                        )
                    }

                    Text(
                        text = "Train RelateAI to match your punctuation patterns, vocabulary choices, and emoji density automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Training prompt card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = RelateAIColors.Secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add Writing Sample",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = trainingText,
                        onValueChange = { trainingText = it },
                        label = { Text("Paste message or email samples") },
                        placeholder = { Text("e.g., 'Hey everyone! Hope you have an awesome week ahead. Talk soon!'") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Text(
                        text = "For best results, paste at least 2-3 paragraphs of text you wrote yourself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    PrimaryButton(
                        text = "Save & Analyze Voice DNA",
                        icon = Icons.Default.Save,
                        onClick = {
                            if (trainingText.isNotBlank()) {
                                onSaveTrainingText?.invoke(trainingText)
                                showSuccess = true
                                scope.launch {
                                    kotlinx.coroutines.delay(1500)
                                    onNavigateBack()
                                }
                            }
                        },
                        enabled = trainingText.isNotBlank() && !showSuccess,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (showSuccess) {
                        Text(
                            text = "Voice blueprint updated successfully!",
                            style = MaterialTheme.typography.bodySmall,
                            color = RelateAIColors.Secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
