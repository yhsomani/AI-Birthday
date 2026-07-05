package com.example.ui.screens.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.relateTextFieldColors
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.contact.ContactPickerItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManualEventContactSection(
    contacts: List<ContactPickerItem>,
    useExistingContact: Boolean,
    onUseExistingContactChange: (Boolean) -> Unit,
    selectedContactId: String?,
    onSelectedContactIdChange: (String?) -> Unit,
    newContactName: String,
    onNewContactNameChange: (String) -> Unit,
    onInputChanged: () -> Unit,
) {
    var contactMenuExpanded by remember { mutableStateOf(false) }
    val selectedContactName = contacts.firstOrNull { it.id.value == selectedContactId }?.displayName

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
        ExistingContactPicker(
            contacts = contacts,
            selectedContactName = selectedContactName,
            contactMenuExpanded = contactMenuExpanded,
            onContactMenuExpandedChange = { contactMenuExpanded = it },
            onSelectedContactIdChange = onSelectedContactIdChange,
            onInputChanged = onInputChanged,
        )
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
}

@Composable
private fun ExistingContactPicker(
    contacts: List<ContactPickerItem>,
    selectedContactName: String?,
    contactMenuExpanded: Boolean,
    onContactMenuExpandedChange: (Boolean) -> Unit,
    onSelectedContactIdChange: (String?) -> Unit,
    onInputChanged: () -> Unit,
) {
    Box {
        OutlinedTextField(
            value = selectedContactName
                ?: stringResource(R.string.events_choose_contact),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EventsTestTags.MANUAL_CONTACT_FIELD)
                .clickable(enabled = contacts.isNotEmpty()) {
                    onContactMenuExpandedChange(true)
                },
            label = { Text(stringResource(R.string.events_contact_label)) },
            enabled = contacts.isNotEmpty(),
            colors = relateTextFieldColors(),
        )
        DropdownMenu(
            expanded = contactMenuExpanded,
            onDismissRequest = { onContactMenuExpandedChange(false) },
        ) {
            contacts.forEach { contact ->
                DropdownMenuItem(
                    text = { Text(contact.displayName) },
                    onClick = {
                        onInputChanged()
                        onSelectedContactIdChange(contact.id.value)
                        onContactMenuExpandedChange(false)
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
}
