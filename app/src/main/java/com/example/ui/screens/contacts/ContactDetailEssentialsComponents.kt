package com.example.ui.screens.contacts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelatePrimaryButton
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.contact.ContactDetailProfile

@Composable
internal fun ContactInfoCard(contact: ContactDetailProfile) {
    RelateGlassCard {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            contact.primaryPhone?.let {
                InfoRow(Icons.Filled.Phone, it)
                Spacer(modifier = Modifier.height(RelateSpacing.md))
            }
            contact.primaryEmail?.let {
                InfoRow(Icons.Filled.Email, it)
                Spacer(modifier = Modifier.height(RelateSpacing.md))
            }
            val birthdayMonth = contact.birthdayMonth
            val birthdayDay = contact.birthdayDay
            val birthday = if (birthdayMonth != null && birthdayDay != null) {
                stringResource(R.string.contact_detail_birthday_date_format, birthdayMonth, birthdayDay)
            } else {
                stringResource(R.string.contact_detail_unknown)
            }
            InfoRow(
                Icons.Filled.CalendarMonth,
                stringResource(R.string.contact_detail_birthday_format, birthday),
            )
        }
    }
}

@Composable
internal fun UpcomingWishCard(
    upcomingEventDaysLeft: Int?,
    isGenerating: Boolean,
    generationErrorRes: Int?,
    onGenerateWish: () -> Unit,
) {
    RelateGlassCard {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(RelateSpacing.sm))
                val daysText = upcomingEventDaysLeft?.let {
                    stringResource(R.string.contact_detail_days_left_format, it)
                } ?: stringResource(R.string.contact_detail_no_upcoming_event)
                Text(
                    text = daysText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(RelateSpacing.lg))
            if (isGenerating) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                RelatePrimaryButton(
                    text = stringResource(R.string.contact_detail_generate_ai_wish),
                    onClick = onGenerateWish,
                )
            }
            generationErrorRes?.let { errorRes ->
                Spacer(modifier = Modifier.height(RelateSpacing.sm))
                Text(
                    text = stringResource(errorRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(RelateSize.iconMd),
        )
        Spacer(modifier = Modifier.width(RelateSpacing.md))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
