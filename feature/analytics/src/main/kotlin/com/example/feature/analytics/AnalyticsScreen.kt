package com.example.feature.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ElevatedCard
import com.example.ui.theme.RelateAIColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relationship Insights", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Large Central Card - Relationship Health Ring (Average computed dynamically)
            item {
                val averageHealth = uiState.averageHealthScore
                val healthLabel = when {
                    averageHealth >= 80 -> "Excellent"
                    averageHealth >= 60 -> "Good"
                    averageHealth >= 40 -> "Needs Work"
                    averageHealth > 0 -> "At Risk"
                    else -> "No Data"
                }
                
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Overall Network Health",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(140.dp)
                        ) {
                            Canvas(modifier = Modifier.size(120.dp)) {
                                // Background circle
                                drawArc(
                                    color = Color.White.copy(alpha = 0.06f),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                )
                                // Glowing gradient ring (Emerald to Indigo)
                                val sweep = (averageHealth / 100f) * 360f
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(RelateAIColors.Secondary, RelateAIColors.Primary, RelateAIColors.Secondary)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$averageHealth%",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontSize = 32.sp
                                )
                                Text(
                                    text = healthLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RelateAIColors.Secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Glowing line chart card showing "Weekly Engagement" over the last 6 months
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "Health",
                                tint = RelateAIColors.Secondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Weekly Engagement",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Interaction trends over the past 6 months",
                            style = MaterialTheme.typography.bodySmall,
                            color = RelateAIColors.OnSurfaceVariantDark
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        GlowingLineChart(modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                }
            }

            // Strongest Connections List
            if (uiState.topHealthContacts.isNotEmpty()) {
                item {
                    Text(
                        text = "Top Connections",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                uiState.topHealthContacts.take(3).forEach { contact ->
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                            ListItem(
                                headlineContent = { Text(contact.name, fontWeight = FontWeight.Bold, color = Color.White) },
                                supportingContent = { Text("Thriving • Health: ${contact.healthScore}%", style = MaterialTheme.typography.bodySmall, color = RelateAIColors.OnSurfaceVariantDark) },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(RelateAIColors.Secondary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = "Star", tint = RelateAIColors.Secondary, modifier = Modifier.size(20.dp))
                                    }
                                },
                                trailingContent = {
                                    Text(
                                        text = "${contact.healthScore}%",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = RelateAIColors.Secondary
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }

            // Neglected Contacts - with Reconnect Prompt Action Button
            if (uiState.neglectedContacts.isNotEmpty()) {
                item {
                    Text(
                        text = "Needs Attention",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                uiState.neglectedContacts.take(3).forEach { contact ->
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                            ListItem(
                                headlineContent = { Text(contact.name, fontWeight = FontWeight.Bold, color = Color.White) },
                                supportingContent = { Text("Fading • Health: ${contact.healthScore}%", style = MaterialTheme.typography.bodySmall, color = RelateAIColors.OnSurfaceVariantDark) },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(RelateAIColors.Tertiary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = "Info", tint = RelateAIColors.Tertiary, modifier = Modifier.size(20.dp))
                                    }
                                },
                                trailingContent = {
                                    Button(
                                        onClick = { /* Launch write draft */ },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = RelateAIColors.Primary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Reconnect",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun GlowingLineChart(modifier: Modifier = Modifier) {
    val primaryColor = RelateAIColors.Primary
    val secondaryColor = RelateAIColors.Secondary
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        val path = Path().apply {
            moveTo(0f, height * 0.7f)
            cubicTo(width * 0.2f, height * 0.75f, width * 0.35f, height * 0.25f, width * 0.5f, height * 0.35f)
            cubicTo(width * 0.65f, height * 0.45f, width * 0.8f, height * 0.05f, width, height * 0.15f)
        }
        
        // Background glow
        drawPath(
            path = path,
            color = primaryColor.copy(alpha = 0.15f),
            style = Stroke(
                width = 12.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        
        // Foreground line
        val gradient = Brush.linearGradient(
            colors = listOf(primaryColor, secondaryColor)
        )
        drawPath(
            path = path,
            brush = gradient,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
