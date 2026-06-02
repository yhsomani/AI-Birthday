package com.example.automation.notifications

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.automation.scheduler.DailyScheduler
import com.example.core.db.dao.PendingMessageDao
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.StandardCard
import com.example.ui.theme.MyApplicationTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MessageEditActivity : ComponentActivity() {

    @Inject
    lateinit var pendingMessageDao: PendingMessageDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val eventId = intent.getStringExtra("event_id") ?: return finish()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MessageEditScreen(
                        eventId = eventId,
                        pendingMessageDao = pendingMessageDao,
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun MessageEditScreen(
    eventId: String,
    pendingMessageDao: PendingMessageDao,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        val pending = pendingMessageDao.getByEventId(eventId)
        if (pending != null) {
            text = when(pending.selectedVariant) {
                "short" -> pending.shortVariant
                "long" -> pending.longVariant
                else -> pending.standardVariant
            }
            loaded = true
        }
    }

    if (!loaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(
        modifier = Modifier.padding(24.dp).fillMaxSize().systemBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Edit Draft",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        StandardCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = { Text("Message Content") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            PrimaryButton(
                text = "Approve & Send",
                icon = Icons.Default.Send,
                onClick = {
                    scope.launch {
                        val pending = pendingMessageDao.getByEventId(eventId)
                        if (pending != null) {
                            pendingMessageDao.insert(pending.copy(
                                standardVariant = text,
                                selectedVariant = "standard",
                                status = "APPROVED"
                            ))
                            DailyScheduler.scheduleExactSend(context, eventId)
                        }
                        onDismiss()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
