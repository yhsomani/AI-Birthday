package com.example.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.ui.components.ElevatedCard
import com.example.ui.components.PrimaryButton
import com.example.ui.components.StatusBadge
import com.example.ui.theme.RelateAIColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    contacts: List<ContactEntity>,
    events: List<EventEntity>,
    onAddBirthday: (contactId: String, dayOfMonth: Int, month: Int, year: Int?) -> Unit = { _: String, _: Int, _: Int, _: Int? -> }
) {
    var showAddSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Calendar state
    val calendarInstance = remember { Calendar.getInstance() }
    var currentMonth by remember { mutableIntStateOf(calendarInstance.get(Calendar.MONTH)) }
    var currentYear by remember { mutableIntStateOf(calendarInstance.get(Calendar.YEAR)) }

    val monthName = remember(currentMonth) {
        calendarInstance.apply { set(Calendar.MONTH, currentMonth) }
            .getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: ""
    }

    val daysInMonth = remember(currentMonth, currentYear) {
        calendarInstance.apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val firstDayOfWeek = remember(currentMonth, currentYear) {
        calendarInstance.apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }.get(Calendar.DAY_OF_WEEK) - 1
    }

    val birthdaysThisMonth = remember(events, currentMonth) {
        events.filter {
            it.month == currentMonth + 1
        }.groupBy { it.dayOfMonth }
    }

    if (showAddSheet) {
        AddBirthdaySheet(
            contacts = contacts.filter { c ->
                events.none { it.contactId == c.id && it.type == "BIRTHDAY" }
            },
            onDismiss = { showAddSheet = false },
            onSave = { contactId, day, month, year ->
                onAddBirthday(contactId, day, month, year)
                showAddSheet = false
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 80.dp)
        ) {
            Text(
                text = "Events",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp),
                letterSpacing = (-0.5).sp
            )

            // Mini Calendar Card Widget
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 16.dp
            ) {
                Column {
                    // Month Picker Headers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (currentMonth == 0) {
                                currentMonth = 11
                                currentYear--
                            } else {
                                currentMonth--
                            }
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = Color.White)
                        }
                        Text(
                            text = "$monthName $currentYear",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = {
                            if (currentMonth == 11) {
                                currentMonth = 0
                                currentYear++
                            } else {
                                currentMonth++
                            }
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Days of week
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall,
                                color = RelateAIColors.OnSurfaceVariantDark,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar Grid Layout
                    val totalCells = firstDayOfWeek + daysInMonth
                    val rowsCount = (totalCells + 6) / 7
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in 0 until rowsCount) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                for (c in 0..6) {
                                    val cellIndex = r * 7 + c
                                    if (cellIndex < firstDayOfWeek || cellIndex >= totalCells) {
                                        Spacer(modifier = Modifier.size(32.dp).weight(1f))
                                    } else {
                                        val day = cellIndex - firstDayOfWeek + 1
                                        val hasEvent = birthdaysThisMonth.containsKey(day)
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .weight(1f)
                                                .clip(CircleShape)
                                                .background(if (hasEvent) RelateAIColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
                                                .border(
                                                    1.dp,
                                                    if (hasEvent) RelateAIColors.Primary.copy(alpha = 0.3f) else Color.Transparent,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = day.toString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (hasEvent) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (hasEvent) RelateAIColors.PrimaryLight else Color.White
                                                )
                                                if (hasEvent) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(RelateAIColors.Secondary)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Timelines of upcoming reminders
            Text(
                text = "Timeline Feed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val sortedTimelineEvents = remember(events) {
                events.sortedBy { it.nextOccurrenceMs }
            }

            if (sortedTimelineEvents.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = RelateAIColors.OnSurfaceVariantDark.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No upcoming reminders.", style = MaterialTheme.typography.bodyMedium, color = RelateAIColors.OnSurfaceVariantDark)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    sortedTimelineEvents.forEach { event ->
                        val contact = contacts.find { it.id == event.contactId }
                        val name = contact?.name ?: "Unknown"
                        val dateStr = SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(event.nextOccurrenceMs))
                        val isAnniversary = event.type == "ANNIVERSARY" || event.type == "WORK_ANNIVERSARY"

                        ElevatedCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                            ListItem(
                                headlineContent = { Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White) },
                                supportingContent = {
                                    Column(modifier = Modifier.padding(top = 2.dp)) {
                                        Text(
                                            text = "${event.type} • $dateStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = RelateAIColors.OnSurfaceVariantDark
                                        )
                                        if (!event.isVerified) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            StatusBadge(
                                                text = "Verify Event?",
                                                containerColor = RelateAIColors.Tertiary.copy(alpha = 0.15f),
                                                contentColor = RelateAIColors.Tertiary
                                            )
                                        }
                                    }
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isAnniversary) RelateAIColors.Tertiary.copy(alpha = 0.12f)
                                                else RelateAIColors.Primary.copy(alpha = 0.12f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isAnniversary) Icons.Default.Chat else Icons.Default.Event,
                                            contentDescription = null,
                                            tint = if (isAnniversary) RelateAIColors.Tertiary else RelateAIColors.Primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                trailingContent = {
                                    Button(
                                        onClick = { /* TODO: Navigate to Messages screen to generate/send message */ },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAnniversary) RelateAIColors.Tertiary else RelateAIColors.Primary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (isAnniversary) "Send Message" else "Generate Wish",
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
        }

        // FAB styled in vibrant Indigo
        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = RelateAIColors.Primary,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Birthday")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBirthdaySheet(
    contacts: List<ContactEntity>,
    onDismiss: () -> Unit,
    onSave: (contactId: String, dayOfMonth: Int, month: Int, year: Int?) -> Unit
) {
    var selectedContactId by remember { mutableStateOf<String?>(null) }
    var dayText by remember { mutableStateOf("") }
    var monthText by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var hasYear by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RelateAIColors.SurfaceDark,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Add Birthday",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                val selectedName = contacts.find { it.id == selectedContactId }?.name ?: ""
                OutlinedTextField(
                    value = selectedName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Contact", color = RelateAIColors.OnSurfaceVariantDark) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RelateAIColors.Primary,
                        unfocusedBorderColor = RelateAIColors.OutlineDark,
                        focusedLabelColor = RelateAIColors.Primary,
                        unfocusedLabelColor = RelateAIColors.OnSurfaceVariantDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(RelateAIColors.SurfaceDark)
                ) {
                    contacts.forEach { contact ->
                        DropdownMenuItem(
                            text = { Text(contact.name, color = Color.White) },
                            onClick = {
                                selectedContactId = contact.id
                                expanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = dayText,
                    onValueChange = { if (it.length <= 2) dayText = it.filter { c -> c.isDigit() } },
                    label = { Text("Day", color = RelateAIColors.OnSurfaceVariantDark) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RelateAIColors.Primary,
                        unfocusedBorderColor = RelateAIColors.OutlineDark,
                        focusedTextColor = Color.White
                    )
                )
                OutlinedTextField(
                    value = monthText,
                    onValueChange = { if (it.length <= 2) monthText = it.filter { c -> c.isDigit() } },
                    label = { Text("Month", color = RelateAIColors.OnSurfaceVariantDark) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RelateAIColors.Primary,
                        unfocusedBorderColor = RelateAIColors.OutlineDark,
                        focusedTextColor = Color.White
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { if (it.length <= 4) yearText = it.filter { c -> c.isDigit() } },
                    label = { Text("Year", color = RelateAIColors.OnSurfaceVariantDark) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = hasYear,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RelateAIColors.Primary,
                        unfocusedBorderColor = RelateAIColors.OutlineDark,
                        focusedTextColor = Color.White
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasYear,
                        onCheckedChange = {
                            hasYear = it
                            if (!it) yearText = ""
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = RelateAIColors.Primary
                        )
                    )
                    Text(
                        text = "Has year",
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateAIColors.OnSurfaceVariantDark
                    )
                }
            }

            val isValid = selectedContactId != null &&
                    dayText.toIntOrNull()?.let { it in 1..31 } == true &&
                    monthText.toIntOrNull()?.let { it in 1..12 } == true &&
                    (!hasYear || yearText.toIntOrNull()?.let { it in 1900..2100 } == true)

            PrimaryButton(
                text = "Save Birthday",
                onClick = {
                    val day = dayText.toInt()
                    val month = monthText.toInt()
                    val year = if (hasYear) yearText.toIntOrNull() else null
                    selectedContactId?.let { onSave(it, day, month, year) }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isValid
            )
        }
    }
}
