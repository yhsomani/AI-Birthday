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
            val setupCallOffenders = setupNotificationCallPattern.findAll(text)
                .filter { call -> rawLiteralArgumentPattern.containsMatchIn(call.value) }
                .map { match -> "${file.path}:${lineNumber(text, match.range.first)}" }
            directNotificationOffenders + setupCallOffenders
        }

        assertTrue(
            "Notification/setup user-visible strings should use resources:\n${offenders.joinToString("\n")}",
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
            "core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt",
            "core/data/src/main/kotlin/com/example/core/automation/scheduler/DailyScheduler.kt",
            "core/data/src/main/kotlin/com/example/core/automation/sender/MessageDispatcher.kt",
        )

        val notificationStringPattern = Regex(
            pattern = "setContent(Title|Text)\\(\\s*\"|addAction\\([^\\n]*,\\s*\"|NotificationChannel\\([^\\n]*,\\s*\"",
        )
        val setupNotificationCallPattern = Regex(
            pattern = "showSetupNotification\\([\\s\\S]*?\\)",
        )
        val rawLiteralArgumentPattern = Regex(
            pattern = ",\\s*\"",
        )
    }
}
