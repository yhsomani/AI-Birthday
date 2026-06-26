package com.example.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NoHardcodedStringsRegressionTest {

    @Test
    fun cleanedScreens_doNotIntroduceVisibleStringLiterals() {
        val offenders = CLEANED_SCREEN_SOURCES.flatMap { path ->
            val file = sourceFile(path)
            visibleStringPattern.findAll(file.readText()).map { match ->
                "${file.path}:${lineNumber(file.readText(), match.range.first)}"
            }
        }

        assertTrue(
            "Visible strings in cleaned screens should use string resources:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun notificationAndSetupSurfaces_doNotIntroduceRawUserVisibleStrings() {
        val offenders = NOTIFICATION_AND_SETUP_SOURCES.flatMap { path ->
            val file = sourceFile(path)
            val text = file.readText()
            val directNotificationOffenders = notificationStringPattern.findAll(text).map { match ->
                "${file.path}:${lineNumber(text, match.range.first)}"
            }
            val setupCallOffenders = notificationCallPattern.findAll(text)
                .filter { call -> rawLiteralArgumentPattern.containsMatchIn(call.value) }
                .map { match -> "${file.path}:${lineNumber(text, match.range.first)}" }
            directNotificationOffenders + setupCallOffenders
        }

        assertTrue(
            "Notification/setup user-visible strings should use resources:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun cleanedViewModelsAndUseCases_doNotReintroduceKnownUserVisibleLiterals() {
        val offenders = CLEANED_VIEWMODEL_AND_USECASE_SOURCES.flatMap { path ->
            val file = sourceFile(path)
            val text = file.readText()
            KNOWN_CLEANED_USER_VISIBLE_LITERALS
                .filter { literal -> text.contains(literal) }
                .map { literal -> "${file.path}: contains \"$literal\"" }
        }

        assertTrue(
            "Cleaned ViewModel/use case user-visible strings should use resources or typed reasons:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private fun sourceFile(rootRelativePath: String): File {
        return listOf(
            File(rootRelativePath),
            File("../$rootRelativePath"),
            File(rootRelativePath.removePrefix("app/")),
        ).firstOrNull { it.exists() }
            ?: error("Could not locate source file $rootRelativePath from ${File(".").absolutePath}")
    }

    private fun lineNumber(text: String, offset: Int): Int {
        return text.substring(0, offset).count { it == '\n' } + 1
    }

    private companion object {
        val CLEANED_SCREEN_SOURCES = listOf(
            "app/src/main/java/com/example/ui/screens/splash/SplashScreen.kt",
            "app/src/main/java/com/example/ui/screens/activity/ActivityHistoryScreen.kt",
            "app/src/main/java/com/example/ui/screens/analytics/AnalyticsScreen.kt",
            "app/src/main/java/com/example/ui/screens/auth/AuthScreen.kt",
            "app/src/main/java/com/example/ui/screens/backup/BackupRestoreScreen.kt",
            "app/src/main/java/com/example/ui/screens/contacts/ContactListScreen.kt",
            "app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt",
            "app/src/main/java/com/example/ui/screens/events/EventsScreen.kt",
            "app/src/main/java/com/example/ui/screens/home/HomeScreen.kt",
            "app/src/main/java/com/example/ui/screens/messages/MessagesScreen.kt",
            "app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt",
            "app/src/main/java/com/example/ui/screens/chat/ChatHistoryScreen.kt",
            "app/src/main/java/com/example/ui/screens/settings/SettingsScreen.kt",
            "app/src/main/java/com/example/ui/screens/onboarding/OnboardingScreen.kt",
            "app/src/main/java/com/example/ui/screens/stylecoach/StyleCoachScreen.kt",
            "app/src/main/java/com/example/ui/screens/setup/AutomationSetupScreen.kt",
            "app/src/main/java/com/example/ui/screens/memoryvault/MemoryVaultScreen.kt",
            "app/src/main/java/com/example/ui/screens/giftadvisor/GiftAdvisorScreen.kt",
        )

        val visibleStringPattern = Regex(
            pattern = "(Text|SectionHeader)\\(\\s*\"|contentDescription\\s*=\\s*\"|EmptyState\\(message\\s*=\\s*\"",
        )

        val NOTIFICATION_AND_SETUP_SOURCES = listOf(
            "core/data/src/main/kotlin/com/example/core/automation/notifications/NotificationHelper.kt",
            "core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorker.kt",
            "core/data/src/main/kotlin/com/example/core/automation/workers/MessageGenerationWorker.kt",
            "core/data/src/main/kotlin/com/example/core/automation/workers/DailyTriggerWorker.kt",
            "core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt",
            "core/data/src/main/kotlin/com/example/core/automation/scheduler/DailyScheduler.kt",
            "core/data/src/main/kotlin/com/example/core/automation/sender/MessageDispatcher.kt",
        )

        val notificationStringPattern = Regex(
            pattern = "setContent(Title|Text)\\(\\s*\"|addAction\\([^\\n]*,\\s*\"|NotificationChannel\\([^\\n]*,\\s*\"",
        )
        val notificationCallPattern = Regex(
            pattern = "show(?:SetupNotification|SystemAlert)\\([\\s\\S]*?\\)",
        )
        val rawLiteralArgumentPattern = Regex(
            pattern = ",\\s*\"",
        )

        val CLEANED_VIEWMODEL_AND_USECASE_SOURCES = listOf(
            "app/src/main/java/com/example/ui/viewmodel/ContactDetailViewModel.kt",
            "app/src/main/java/com/example/ui/viewmodel/ContactListViewModel.kt",
            "app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt",
            "app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt",
            "app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt",
            "core/domain/src/main/kotlin/com/example/domain/usecase/SaveManualEventUseCase.kt",
            "core/domain/src/main/kotlin/com/example/domain/usecase/UpdateContactPreferencesUseCase.kt",
        )

        val KNOWN_CLEANED_USER_VISIBLE_LITERALS = listOf(
            "Unable to load events. Please try again.",
            "Unable to refresh events. Please try again.",
            "Unable to update this event conflict. Please try again.",
            "Selected contact was not found.",
            "Event was not found.",
            "No event conflict remains.",
            "Choose a contact or enter a new contact name.",
            "Enter a valid date.",
            " related event(s).",
            "Unable to sync contacts. Please try again.",
            "Contact not found.",
            "Relationship type is required.",
            "Preferred language is required.",
            "Choose SMS, WhatsApp, or Email as the preferred channel.",
            "Choose a supported automation mode.",
            "Send hour must be 0-23.",
            "Send minute must be 0-59.",
            "Set both hour and minute for a custom send time.",
            "Budgets cannot be negative.",
            "Setup needs attention",
            "Approvals waiting",
            "Relationship health is ",
            "Upcoming in ",
            "Deleted Contact",
            "Unable to load messages. Please try again.",
            "Unable to approve the message. Please try again.",
            "Unable to reject the message. Please try again.",
            "Unable to retry the message. Please try again.",
            "Unable to revoke approval. Please try again.",
            "Unable to approve the selected messages. Please try again.",
            "Unable to reject the selected messages. Please try again.",
            "Unable to retry the selected messages. Please try again.",
            "A pending message was approved.",
            "A pending message was rejected.",
            "A failed message was queued for retry.",
            " pending messages were approved.",
            " pending messages were rejected.",
            " failed messages were queued for retry.",
            "A message approval was revoked.",
        )
    }
}
