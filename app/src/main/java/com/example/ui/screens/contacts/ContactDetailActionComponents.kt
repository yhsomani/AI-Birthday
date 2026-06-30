package com.example.ui.screens.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactDetailProfile

@Composable
internal fun PersonalizationActionsCard(
    onAddMemory: () -> Unit,
    onAddGift: () -> Unit,
    onEditPreferences: () -> Unit,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.contact_detail_personalization_actions),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onAddMemory,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RelateSize.compactButtonHeight)
                        .testTag(ContactDetailTestTags.ACTION_ADD_MEMORY),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(RelateRadius.control),
                ) {
                    Text(stringResource(R.string.contact_detail_add_memory), color = MaterialTheme.colorScheme.onSurface)
                }
                Button(
                    onClick = onAddGift,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RelateSize.compactButtonHeight)
                        .testTag(ContactDetailTestTags.ACTION_ADD_GIFT),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(RelateRadius.control),
                ) {
                    Text(stringResource(R.string.contact_detail_add_gift), color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Button(
                onClick = onEditPreferences,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = RelateSize.compactButtonHeight)
                    .testTag(ContactDetailTestTags.ACTION_EDIT_PREFERENCES),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(RelateRadius.control),
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(RelateSize.iconSm),
                )
                Spacer(modifier = Modifier.width(RelateSpacing.sm))
                Text(
                    text = stringResource(R.string.contact_detail_edit_preferences),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun AutomationActionsCard(
    contact: ContactDetailProfile,
    onMarkVip: () -> Unit,
    onSetWhatsApp: () -> Unit,
    onSetSms: () -> Unit,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.contact_detail_automation_actions),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
            ) {
                Button(
                    onClick = onMarkVip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = RelateSize.compactButtonHeight),
                    enabled = contact.automationMode != ApprovalMode.VIP_APPROVE,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(RelateRadius.control),
                ) {
                    Text(stringResource(R.string.contact_detail_mark_vip), color = MaterialTheme.colorScheme.onSurface)
                }
                Button(
                    onClick = onSetWhatsApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = RelateSize.compactButtonHeight),
                    enabled = contact.preferredChannel != MessageChannel.WHATSAPP,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(RelateRadius.control),
                ) {
                    Text(stringResource(R.string.contact_detail_set_whatsapp), color = MaterialTheme.colorScheme.onSurface)
                }
                Button(
                    onClick = onSetSms,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = RelateSize.compactButtonHeight),
                    enabled = contact.preferredChannel != MessageChannel.SMS,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(RelateRadius.control),
                ) {
                    Text(stringResource(R.string.contact_detail_set_sms), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
internal fun HistoryActionsCard(
    contactId: String,
    onNavigateToMemoryVault: (String) -> Unit,
    onNavigateToGiftAdvisor: (String) -> Unit,
    onNavigateToChatHistory: (String) -> Unit,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.lg),
            ) {
                Button(
                    onClick = { onNavigateToMemoryVault(contactId) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RelateSize.compactButtonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(RelateRadius.control),
                ) {
                    Text(
                        text = stringResource(R.string.contact_detail_memory_vault),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Button(
                    onClick = { onNavigateToGiftAdvisor(contactId) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RelateSize.compactButtonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(RelateRadius.control),
                ) {
                    Text(
                        text = stringResource(R.string.contact_detail_gift_advisor),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Button(
                onClick = { onNavigateToChatHistory(contactId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = RelateSize.compactButtonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(RelateRadius.control),
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(RelateSize.iconSm),
                )
                Spacer(modifier = Modifier.width(RelateSpacing.sm))
                Text(
                    text = stringResource(R.string.contact_detail_chat_history),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
