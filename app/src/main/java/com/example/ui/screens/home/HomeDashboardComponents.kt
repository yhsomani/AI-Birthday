package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.StatCard
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.HomeActionTarget
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.RelationshipPlannerItem
import com.example.ui.viewmodel.UpcomingBirthday

@Composable
internal fun HomeStatsGrid(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            StatCard(
                label = stringResource(R.string.home_stat_wishes_sent),
                value = "${state.sentCount}",
                icon = Icons.Filled.Star,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.home_stat_upcoming),
                value = "${state.upcomingEventsCount}",
                icon = Icons.Filled.CalendarMonth,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            StatCard(
                label = stringResource(R.string.dashboard_contacts),
                value = "${state.contactCount}",
                icon = Icons.Filled.People,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.messages_pending),
                value = "${state.pendingCount}",
                icon = Icons.Filled.MailOutline,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.home_stat_score),
                value = "${state.healthScore}",
                icon = Icons.Filled.Favorite,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun HomePlannerSection(
    plannerItems: List<RelationshipPlannerItem>,
    onActionClick: (HomeActionTarget) -> Unit,
) {
    Spacer(modifier = Modifier.height(RelateSpacing.xl))
    SectionHeader(title = stringResource(R.string.relationship_planner_title))
    Spacer(modifier = Modifier.height(RelateSpacing.sm))
    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
        plannerItems.forEach { item ->
            PlannerItemCard(
                item = item,
                onClick = { onActionClick(item.actionTarget) },
                modifier = Modifier.testTag(
                    HomeScreenTestTags.PLANNER_ITEM_PREFIX + item.actionTarget.testKey(),
                ),
            )
        }
    }
}

@Composable
internal fun HomeBirthdaysSection(upcomingBirthdays: List<UpcomingBirthday>) {
    Spacer(modifier = Modifier.height(RelateSpacing.xl))
    SectionHeader(title = stringResource(R.string.home_upcoming_birthdays))
    RelateGlassCard {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            if (upcomingBirthdays.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_no_upcoming_birthdays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                upcomingBirthdays.forEachIndexed { index, birthday ->
                    BirthdayRow(name = birthday.name, date = birthday.date)
                    if (index < upcomingBirthdays.lastIndex) {
                        Spacer(modifier = Modifier.height(RelateSpacing.md))
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(RelateSpacing.xl))
}

private fun HomeActionTarget.testKey(): String {
    return when (this) {
        HomeActionTarget.AutomationSetup -> "automation_setup"
        HomeActionTarget.BackupRestore -> "backup_restore"
        is HomeActionTarget.ContactDetail -> contactId
        HomeActionTarget.Messages -> "messages"
    }
}

@Composable
private fun PlannerItemCard(
    item: RelationshipPlannerItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(RelateSpacing.compactCardContent),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(RelateSize.iconMd),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BirthdayRow(
    name: String,
    date: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(RelateSize.avatar)
                .clip(RoundedCornerShape(RelateRadius.control))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = RelateAlpha.feedbackContainer)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.take(3),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(RelateSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_birthday_on_date, date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
