package com.example.feature.onboarding

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.ui.components.StandardCard
import kotlinx.coroutines.delay

import com.example.core.db.AppDatabase

@Composable
fun ImportProgressScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.getInstance(context) }
    val contactCount by db.contactDao().countAll().collectAsState(initial = 0)
    val eventCount by db.eventDao().countAll().collectAsState(initial = 0)

    var contactSyncId by remember { mutableStateOf<java.util.UUID?>(null) }
    var eventDiscoveryId by remember { mutableStateOf<java.util.UUID?>(null) }

    LaunchedEffect(Unit) {
        val req1 = androidx.work.OneTimeWorkRequestBuilder<com.example.automation.workers.ContactSyncWorker>().build()
        val req2 = androidx.work.OneTimeWorkRequestBuilder<com.example.automation.workers.EventDiscoveryWorker>().build()

        contactSyncId = req1.id
        eventDiscoveryId = req2.id

        androidx.work.WorkManager.getInstance(context)
            .beginWith(req1)
            .then(req2)
            .enqueue()
    }

    val contactSyncInfo by remember(context, contactSyncId) {
        if (contactSyncId != null) {
            androidx.work.WorkManager.getInstance(context).getWorkInfoByIdFlow(contactSyncId!!)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }.collectAsState(initial = null)

    val eventDiscoveryInfo by remember(context, eventDiscoveryId) {
        if (eventDiscoveryId != null) {
            androidx.work.WorkManager.getInstance(context).getWorkInfoByIdFlow(eventDiscoveryId!!)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }.collectAsState(initial = null)

    val isContactSyncFinished = contactSyncInfo?.state == androidx.work.WorkInfo.State.SUCCEEDED
    val isEventDiscoveryFinished = eventDiscoveryInfo?.state == androidx.work.WorkInfo.State.SUCCEEDED

    val targetProgress = when {
        isEventDiscoveryFinished -> 1.0f
        isContactSyncFinished -> 0.75f
        contactSyncInfo?.state == androidx.work.WorkInfo.State.RUNNING -> 0.35f
        else -> 0.05f
    }

    var progresses by remember { mutableStateOf(0.05f) }
    LaunchedEffect(targetProgress) {
        val start = progresses
        val steps = 20
        for (i in 1..steps) {
            delay(30)
            progresses = start + (targetProgress - start) * (i.toFloat() / steps)
        }
        progresses = targetProgress
    }

    OnboardingWrapper(
        title = "Provisioning Graph...",
        subtitle = "Please wait a moment while we map relationship directories, discover historical log milestones, and initiate intelligence vectors locally.",
        currentStep = 10,
        onNext = onFinish,
        nextText = if (progresses >= 1.0f) "Launch Dashboard" else "Assembling Dashboard (Please wait)",
        isNextEnabled = progresses >= 1.0f
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progresses },
                    modifier = Modifier.size(110.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    strokeWidth = 8.dp
                )

                Text(
                    text = "${(progresses * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Real-time fetched data stats
            StandardCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 16.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Real-time Import Stats",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$contactCount",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Contacts Synced",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$eventCount",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Events Discovered",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            StandardCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 16.dp
            ) {
                Text(
                    "Network Initialization Task logs:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))

                ImportTaskRow(
                    label = "Populate SQLite directories schema",
                    active = contactSyncInfo?.state == androidx.work.WorkInfo.State.RUNNING || contactSyncInfo?.state == androidx.work.WorkInfo.State.ENQUEUED,
                    finished = isContactSyncFinished
                )
                ImportTaskRow(
                    label = "Expose and index calendar milestones",
                    active = eventDiscoveryInfo?.state == androidx.work.WorkInfo.State.RUNNING || eventDiscoveryInfo?.state == androidx.work.WorkInfo.State.ENQUEUED,
                    finished = isEventDiscoveryFinished
                )
                ImportTaskRow(
                    label = "Compute initial health metrics logs",
                    active = isContactSyncFinished && !isEventDiscoveryFinished,
                    finished = isEventDiscoveryFinished
                )
                ImportTaskRow(
                    label = "Compile local voice style descriptors",
                    active = isEventDiscoveryFinished,
                    finished = isEventDiscoveryFinished
                )
            }
        }
    }
}

@Composable
fun ImportTaskRow(label: String, active: Boolean, finished: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tone = when {
            finished -> MaterialTheme.colorScheme.primary
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        }

        Icon(
            imageVector = if (finished) Icons.Default.CheckCircle else Icons.Default.Circle,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active || finished) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
