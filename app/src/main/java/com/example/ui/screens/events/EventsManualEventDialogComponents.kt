package com.example.ui.screens.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.example.R
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.relateTextFieldColors
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.domain.model.contact.ContactPickerItem
import com.example.domain.model.occasion.OccasionType
import com.example.ui.viewmodel.ManualEventDuplicateWarning
import com.example.ui.viewmodel.ManualEventWarningKind

private val eventTypeOptions = listOf(
    OccasionType.BIRTHDAY.raw,
    OccasionType.ANNIVERSARY.raw,
    OccasionType.WORK_ANNIVERSARY.raw,
    OccasionType.GRADUATION.raw,
    OccasionType.HOLIDAY.raw,
    OccasionType.REVIVAL.raw,
    OccasionType.FOLLOW_UP.raw,
    OccasionType.CUSTOM.raw,
)

@Composable
internal fun ManualEventDialog(
    contacts: List<ContactPickerItem>,
    isSaving: Boolean,
    duplicateWarning: ManualEventDuplicateWarning?,
    onDismiss: () -> Unit,
    onInputChanged: () -> Unit,
    onSave: (
        existingContactId: String?,
        newContactName: String?,
        eventType: String,
        label: String?,
        month: Int,
        day: Int,
        year: Int?,
        allowDuplicate: Boolean,
    ) -> Unit,
) {
    var useExistingContact by remember { mutableStateOf(contacts.isNotEmpty()) }
    var selectedContactId by remember { mutableStateOf(contacts.firstOrNull()?.id?.value) }
    var newContactName by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf(OccasionType.BIRTHDAY.raw) }
    var label by remember { mutableStateOf("") }
    var monthText by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val errorMonthDay = stringResource(R.string.events_error_month_day)
    val errorChooseContact = stringResource(R.string.events_error_choose_contact)
    val errorContactName = stringResource(R.string.events_error_contact_name)

    AlertDialog(
        modifier = Modifier.testTag(EventsTestTags.MANUAL_DIALOG),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.events_add_event), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            ManualEventDialogBody(
                contacts = contacts,
                useExistingContact = useExistingContact,
                onUseExistingContactChange = { useExistingContact = it },
                selectedContactId = selectedContactId,
                onSelectedContactIdChange = { selectedContactId = it },
                newContactName = newContactName,
                onNewContactNameChange = { newContactName = it },
                eventType = eventType,
                onEventTypeChange = { eventType = it },
                label = label,
                onLabelChange = { label = it },
                monthText = monthText,
                onMonthTextChange = { monthText = it.filter(Char::isDigit).take(2) },
                dayText = dayText,
                onDayTextChange = { dayText = it.filter(Char::isDigit).take(2) },
                yearText = yearText,
                onYearTextChange = { yearText = it.filter(Char::isDigit).take(4) },
                localError = localError,
                duplicateWarning = duplicateWarning,
                onInputChanged = onInputChanged,
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                modifier = Modifier.testTag(EventsTestTags.MANUAL_SAVE),
                onClick = {
                    val month = monthText.toIntOrNull()
                    val day = dayText.toIntOrNull()
                    val year = yearText.toIntOrNull()
                    val existingId = selectedContactId.takeIf { useExistingContact }
                    val newName = newContactName.takeIf { !useExistingContact }
                    if (month == null || day == null) {
                        localError = errorMonthDay
                    } else if (useExistingContact && existingId == null) {
                        localError = errorChooseContact
                    } else if (!useExistingContact && newName.isNullOrBlank()) {
                        localError = errorContactName
                    } else {
                        onSave(
                            existingId,
                            newName,
                            eventType,
                            label,
                            month,
                            day,
                            year,
                            duplicateWarning != null,
                        )
                    }
                },
            ) {
                Text(
                    when {
                        isSaving -> stringResource(R.string.saving)
                        duplicateWarning != null -> stringResource(R.string.events_duplicate_save_anyway)
                        else -> stringResource(R.string.save)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(EventsTestTags.MANUAL_CANCEL),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManualEventDialogBody(
    contacts: List<ContactPickerItem>,
    useExistingContact: Boolean,
    onUseExistingContactChange: (Boolean) -> Unit,
    selectedContactId: String?,
    onSelectedContactIdChange: (String?) -> Unit,
    newContactName: String,
    onNewContactNameChange: (String) -> Unit,
    eventType: String,
    onEventTypeChange: (String) -> Unit,
    label: String,
    onLabelChange: (String) -> Unit,
    monthText: String,
    onMonthTextChange: (String) -> Unit,
    dayText: String,
    onDayTextChange: (String) -> Unit,
    yearText: String,
    onYearTextChange: (String) -> Unit,
    localError: String?,
    duplicateWarning: ManualEventDuplicateWarning?,
    onInputChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var contactMenuExpanded by remember { mutableStateOf(false) }
    val selectedContactName = contacts.firstOrNull { it.id.value == selectedContactId }?.displayName

    Column(
        modifier = modifier
            .height(RelateSize.dialogContentMaxHeight)
            .verticalScroll(rememberScrollState())
            .testTag(EventsTestTags.MANUAL_FORM_BODY),
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            FilterChip(
                label = stringResource(R.string.events_existing_contact),
                isSelected = useExistingContact,
                onClick = {
                    onInputChanged()
                    onUseExistingContactChange(true)
                    if (selectedContactId == null) {
                        onSelectedContactIdChange(contacts.firstOrNull()?.id?.value)
                    }
                },
            )
            FilterChip(
                label = stringResource(R.string.events_new_contact),
                isSelected = !useExistingContact,
                onClick = {
                    onInputChanged()
                    onUseExistingContactChange(false)
                },
            )
        }

        if (useExistingContact) {
            Box {
                OutlinedTextField(
                    value = selectedContactName
                        ?: stringResource(R.string.events_choose_contact),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = contacts.isNotEmpty()) {
                            contactMenuExpanded = true
                        },
                    label = { Text(stringResource(R.string.events_contact_label)) },
                    enabled = contacts.isNotEmpty(),
                    colors = relateTextFieldColors(),
                )
                DropdownMenu(
                    expanded = contactMenuExpanded,
                    onDismissRequest = { contactMenuExpanded = false },
                ) {
                    contacts.forEach { contact ->
                        DropdownMenuItem(
                            text = { Text(contact.displayName) },
                            onClick = {
                                onInputChanged()
                                onSelectedContactIdChange(contact.id.value)
                                contactMenuExpanded = false
                            },
                        )
                    }
                }
            }
            if (contacts.isEmpty()) {
                Text(
                    text = stringResource(R.string.events_no_contacts_for_manual),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            OutlinedTextField(
                value = newContactName,
                onValueChange = {
                    onInputChanged()
                    onNewContactNameChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.events_new_contact_name)) },
                singleLine = true,
                colors = relateTextFieldColors(),
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            eventTypeOptions.forEach { value ->
                FilterChip(
                    label = eventTypeLabel(value),
                    isSelected = eventType == value,
                    onClick = {
                        onInputChanged()
                        onEventTypeChange(value)
                    },
                )
            }
        }

        OutlinedTextField(
            value = label,
            onValueChange = {
                onInputChanged()
                onLabelChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.events_label)) },
            placeholder = { Text(stringResource(R.string.optional)) },
            singleLine = true,
            colors = relateTextFieldColors(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
            OutlinedTextField(
                value = monthText,
                onValueChange = {
                    onInputChanged()
                    onMonthTextChange(it)
                },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.events_month_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = relateTextFieldColors(),
            )
            OutlinedTextField(
                value = dayText,
                onValueChange = {
                    onInputChanged()
                    onDayTextChange(it)
                },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.events_day_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = relateTextFieldColors(),
            )
            OutlinedTextField(
                value = yearText,
                onValueChange = {
                    onInputChanged()
                    onYearTextChange(it)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(EventsTestTags.MANUAL_YEAR_FIELD),
                label = { Text(stringResource(R.string.events_year_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = relateTextFieldColors(),
            )
        }

        localError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        duplicateWarning?.let { warning ->
            val warningMessage = when (warning.kind) {
                ManualEventWarningKind.DUPLICATE -> stringResource(
                    R.string.events_duplicate_message,
                    warning.contactName,
                    eventTypeLabel(warning.eventType),
                    warning.month,
                    warning.dayOfMonth,
                )
                ManualEventWarningKind.DATE_CONFLICT -> stringResource(
                    R.string.events_conflict_message,
                    warning.contactName,
                    eventTypeLabel(warning.eventType),
                    warning.month,
                    warning.dayOfMonth,
                    warning.requestedMonth ?: warning.month,
                    warning.requestedDayOfMonth ?: warning.dayOfMonth,
                )
            }
            Column(
                modifier = Modifier.testTag(EventsTestTags.MANUAL_WARNING),
                verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
            ) {
                Text(
                    text = stringResource(
                        when (warning.kind) {
                            ManualEventWarningKind.DUPLICATE -> R.string.events_duplicate_title
                            ManualEventWarningKind.DATE_CONFLICT -> R.string.events_conflict_title
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.relateSemanticColors.warning,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = warningMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
