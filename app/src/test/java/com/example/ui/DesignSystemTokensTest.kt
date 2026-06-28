package com.example.ui

import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateElevation
import com.example.core.ui.theme.RelateFraction
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemTokensTest {

    @Test
    fun spacingTokens_followFourDpGridForReusableLayout() {
        assertEquals(4f, RelateSpacing.xs.value)
        assertEquals(8f, RelateSpacing.sm.value)
        assertEquals(12f, RelateSpacing.md.value)
        assertEquals(16f, RelateSpacing.lg.value)
        assertEquals(48f, RelateSpacing.xxxl.value)
        assertEquals(RelateSpacing.lg, RelateSpacing.screenHorizontal)
        assertEquals(RelateSpacing.lg, RelateSpacing.cardContent)
    }

    @Test
    fun shapeTokens_keepCardsAndControlsCompact() {
        assertTrue("Cards should stay at 8 dp radius or below.", RelateRadius.card.value <= 8f)
        assertEquals(8f, RelateRadius.control.value)
        assertEquals(20f, RelateRadius.pill.value)
    }

    @Test
    fun sizeTokens_keepPrimaryActionsAccessible() {
        assertTrue(
            "Primary buttons should meet or exceed the target touch height.",
            RelateSize.primaryButtonHeight >= RelateSize.minTouchTarget,
        )
        assertEquals(6f, RelateSize.statusDot.value)
        assertEquals(24f, RelateSize.chipMinHeight.value)
        assertEquals(1f, RelateSize.outlineStroke.value)
        assertEquals(24f, RelateSize.iconLg.value)
        assertEquals(32f, RelateSize.progressIndicator.value)
        assertEquals(34f, RelateSize.setupStepIndex.value)
        assertEquals(20f, RelateSize.chartBarHeight.value)
        assertEquals(64f, RelateSize.heroIcon.value)
        assertTrue(
            "Profile avatars should be larger than list avatars.",
            RelateSize.profileAvatar > RelateSize.avatar,
        )
        assertTrue(
            "Loading panels should reserve enough height to avoid first-paint layout jump.",
            RelateSize.loadingPanelHeight >= RelateSize.primaryButtonHeight,
        )
        assertTrue(
            "Action cards need enough height for icon, title, and wrapped supporting copy.",
            RelateSize.actionCardMinHeight >= RelateSize.minTouchTarget,
        )
        assertTrue(
            "Responsive action-card breakpoint must fit two compact action cards.",
            RelateSize.actionGridBreakpoint.value >= RelateSize.actionCardMinHeight.value * 2,
        )
        assertTrue(
            "Progress strokes should stay visible without becoming heavy.",
            RelateSize.progressStroke.value in 1f..4f,
        )
        assertTrue(
            "Large indicator dots should be visually distinct from default dots.",
            RelateSize.indicatorDotLarge > RelateSize.indicatorDot,
        )
        assertTrue(
            "Dialog content should provide enough height for dense preference forms.",
            RelateSize.dialogContentMaxHeight >= RelateSize.loadingPanelHeight,
        )
    }

    @Test
    fun alphaTokens_remainValidFractions() {
        listOf(
            RelateAlpha.disabled,
            RelateAlpha.divider,
            RelateAlpha.muted,
            RelateAlpha.outline,
            RelateAlpha.shimmerHigh,
            RelateAlpha.shimmerLow,
            RelateAlpha.subtle,
            RelateAlpha.feedbackContainer,
            RelateAlpha.fieldContainer,
        ).forEach { alpha ->
            assertTrue("Alpha tokens must be between 0 and 1.", alpha in 0f..1f)
        }
    }

    @Test
    fun layoutFractionTokens_remainValidFractions() {
        listOf(
            RelateFraction.strengthWeak,
            RelateFraction.strengthFair,
            RelateFraction.strengthStrong,
            RelateFraction.strengthFull,
            RelateFraction.healthStrongThreshold,
            RelateFraction.healthAttentionThreshold,
            RelateFraction.metadataLabel,
            RelateFraction.metadataValue,
            RelateFraction.skeletonTitle,
            RelateFraction.skeletonSubtitle,
        ).forEach { fraction ->
            assertTrue("Layout fraction tokens must be between 0 and 1.", fraction in 0f..1f)
        }
        assertTrue(
            "Password strength fractions should increase with quality.",
            RelateFraction.strengthWeak < RelateFraction.strengthFair &&
                RelateFraction.strengthFair < RelateFraction.strengthStrong &&
                RelateFraction.strengthStrong < RelateFraction.strengthFull,
        )
        assertTrue(
            "Health thresholds should keep strong health above attention health.",
            RelateFraction.healthStrongThreshold > RelateFraction.healthAttentionThreshold,
        )
        assertEquals(
            "Metadata label/value weights should fill the row.",
            1f,
            RelateFraction.metadataLabel + RelateFraction.metadataValue,
            0.001f,
        )
        assertTrue(
            "Skeleton title placeholders should be wider than subtitles.",
            RelateFraction.skeletonTitle > RelateFraction.skeletonSubtitle,
        )
    }

    @Test
    fun elevationTokens_keepSurfacesSubtle() {
        assertTrue("Card elevation should stay subtle.", RelateElevation.card.value in 0f..3f)
        assertTrue("App bar elevation should stay subtle.", RelateElevation.appBar.value in 0f..4f)
    }

    @Test
    fun syncErrorCard_usesDesignTokensAndSemanticWarningColors() {
        val source = sourceFile("app/src/main/java/com/example/ui/components/SyncErrorCard.kt").readText()

        assertTrue(
            "SyncErrorCard should use theme-provided semantic warning colors.",
            source.contains("MaterialTheme.relateSemanticColors"),
        )
        assertTrue(
            "SyncErrorCard should use RelateSpacing instead of local dp values.",
            source.contains("RelateSpacing.") && !Regex("""\d+\.dp""").containsMatchIn(source),
        )
        assertTrue(
            "SyncErrorCard should not define raw color literals.",
            !source.contains("Color(0x"),
        )
    }

    @Test
    fun feedbackComponents_useSemanticStatusColors() {
        val source = sourceFile("core/ui/src/main/kotlin/com/example/core/ui/components/FeedbackComponents.kt")
            .readText()

        assertTrue(
            "Feedback components should resolve status colors through MaterialTheme.relateSemanticColors.",
            source.contains("MaterialTheme.relateSemanticColors"),
        )
        listOf("RelateSuccess", "RelateWarning", "RelateError", "RelatePrimary").forEach { rawColor ->
            assertTrue(
                "Feedback components should not import or reference $rawColor directly.",
                !source.contains(rawColor),
            )
        }
    }

    @Test
    fun relateComponents_useThemeBackedColorRoles() {
        val source = sourceFile("core/ui/src/main/kotlin/com/example/core/ui/components/RelateComponents.kt")
            .readText()

        assertTrue(
            "Shared Relate components should use MaterialTheme.relateSemanticColors for non-Material card/status roles.",
            source.contains("MaterialTheme.relateSemanticColors"),
        )
        assertTrue(
            "Shared Relate components should use MaterialTheme.colorScheme for Material color roles.",
            source.contains("MaterialTheme.colorScheme"),
        )
        listOf(
            "RelateDarkBackground",
            "RelateCard",
            "RelateCardBorder",
            "RelateOnBackground",
            "RelateOnSurfaceVariant",
            "RelatePrimary",
            "RelateSurfaceVariant",
            "RelateSuccess",
            "RelateWarning",
            "RelateError",
        ).forEach { rawColor ->
            assertTrue(
                "RelateComponents should not import or reference $rawColor directly.",
                !Regex("""\b$rawColor\b""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun shimmerLoading_usesThemeBackedSurfaceColor() {
        val source = sourceFile("core/ui/src/main/kotlin/com/example/core/ui/components/ShimmerLoading.kt")
            .readText()

        assertTrue(
            "ShimmerItem should derive its base color from MaterialTheme.colorScheme.",
            source.contains("MaterialTheme.colorScheme.surfaceVariant"),
        )
        assertTrue(
            "ShimmerItem should not reference dark-specific surface colors directly.",
            !source.contains("RelateSurfaceVariant") && !source.contains("Color(0x"),
        )
    }

    @Test
    fun eventsScreen_usesThemeBackedColorRoles() {
        val source = sourceFile("app/src/main/java/com/example/ui/screens/events/EventsScreen.kt").readText()

        assertTrue(
            "Events screen should resolve status accents through MaterialTheme.relateSemanticColors.",
            source.contains("MaterialTheme.relateSemanticColors"),
        )
        assertTrue(
            "Events screen should resolve Material colors through MaterialTheme.colorScheme.",
            source.contains("MaterialTheme.colorScheme"),
        )
        listOf(
            "RelateDarkBackground",
            "RelateOnSurfaceVariant",
            "RelatePrimary",
            "RelateSurfaceVariant",
            "RelateWarning",
        ).forEach { rawColor ->
            assertTrue(
                "EventsScreen should not import or reference $rawColor directly.",
                !Regex("""\b$rawColor\b""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun chatHistoryScreen_usesThemeBackedColorRoles() {
        val source = sourceFile("app/src/main/java/com/example/ui/screens/chat/ChatHistoryScreen.kt").readText()

        assertTrue(
            "ChatHistoryScreen should resolve Material colors through MaterialTheme.colorScheme.",
            source.contains("MaterialTheme.colorScheme"),
        )
        listOf(
            "RelateOnSurfaceVariant",
            "RelatePrimary",
        ).forEach { rawColor ->
            assertTrue(
                "ChatHistoryScreen should not import or reference $rawColor directly.",
                !Regex("""\b$rawColor\b""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun splashScreen_usesThemeBackedColorRoles() {
        val source = sourceFile("app/src/main/java/com/example/ui/screens/splash/SplashScreen.kt").readText()

        assertTrue(
            "SplashScreen should resolve Material colors through MaterialTheme.colorScheme.",
            source.contains("MaterialTheme.colorScheme"),
        )
        listOf(
            "RelateDarkBackground",
            "RelateOnBackground",
            "RelatePrimary",
        ).forEach { rawColor ->
            assertTrue(
                "SplashScreen should not import or reference $rawColor directly.",
                !Regex("""\b$rawColor\b""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun authScreen_usesThemeBackedColorRoles() {
        val source = sourceFile("app/src/main/java/com/example/ui/screens/auth/AuthScreen.kt").readText()

        assertTrue(
            "AuthScreen should resolve Material colors through MaterialTheme.colorScheme.",
            source.contains("MaterialTheme.colorScheme"),
        )
        listOf(
            "RelateDarkBackground",
            "RelateOnBackground",
            "RelateOnSurfaceVariant",
            "RelatePrimary",
            "RelateSurfaceVariant",
        ).forEach { rawColor ->
            assertTrue(
                "AuthScreen should not import or reference $rawColor directly.",
                !Regex("""\b$rawColor\b""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun onboardingScreen_usesThemeBackedColorRoles() {
        val source = sourceFile("app/src/main/java/com/example/ui/screens/onboarding/OnboardingScreen.kt").readText()

        assertTrue(
            "OnboardingScreen should resolve Material colors through MaterialTheme.colorScheme.",
            source.contains("MaterialTheme.colorScheme"),
        )
        listOf(
            "RelateDarkBackground",
            "RelateOnBackground",
            "RelateOnSurfaceVariant",
            "RelatePrimary",
            "RelateSurfaceVariant",
        ).forEach { rawColor ->
            assertTrue(
                "OnboardingScreen should not import or reference $rawColor directly.",
                !Regex("""\b$rawColor\b""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun styleCoachScreen_usesThemeBackedColorRoles() {
        val source = sourceFile("app/src/main/java/com/example/ui/screens/stylecoach/StyleCoachScreen.kt").readText()

        assertTrue(
            "StyleCoachScreen should resolve Material colors through MaterialTheme.colorScheme.",
            source.contains("MaterialTheme.colorScheme"),
        )
        assertTrue(
            "StyleCoachScreen should resolve success status colors through MaterialTheme.relateSemanticColors.",
            source.contains("MaterialTheme.relateSemanticColors"),
        )
        listOf(
            "RelateDarkBackground",
            "RelateSuccess",
        ).forEach { rawColor ->
            assertTrue(
                "StyleCoachScreen should not import or reference $rawColor directly.",
                !Regex("""\b$rawColor\b""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun backupRestoreScreen_usesThemeBackedColorRoles() {
        val source = sourceFile("app/src/main/java/com/example/ui/screens/backup/BackupRestoreScreen.kt").readText()

        assertTrue(
            "BackupRestoreScreen should resolve Material colors through MaterialTheme.colorScheme.",
            source.contains("MaterialTheme.colorScheme"),
        )
        assertTrue(
            "BackupRestoreScreen should resolve status and card colors through MaterialTheme.relateSemanticColors.",
            source.contains("MaterialTheme.relateSemanticColors"),
        )
        listOf(
            "RelateDarkBackground",
            "RelateCard",
            "RelateError",
            "RelateOnSurfaceVariant",
            "RelatePrimary",
            "RelateSecondary",
            "RelateSurfaceVariant",
            "RelateSuccess",
            "RelateWarning",
        ).forEach { rawColor ->
            assertTrue(
                "BackupRestoreScreen should not import or reference $rawColor directly.",
                !Regex("""\b$rawColor\b""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun activityHistoryScreen_usesThemeBackedColorRoles() {
        val source = sourceFile("app/src/main/java/com/example/ui/screens/activity/ActivityHistoryScreen.kt")
            .readText()

        assertTrue(
            "ActivityHistoryScreen should resolve Material colors through MaterialTheme.colorScheme.",
            source.contains("MaterialTheme.colorScheme"),
        )
        assertTrue(
            "ActivityHistoryScreen should resolve warning severity colors through MaterialTheme.relateSemanticColors.",
            source.contains("MaterialTheme.relateSemanticColors"),
        )
        listOf(
            "RelateOnSurfaceVariant",
            "RelatePrimary",
            "RelateWarning",
        ).forEach { rawColor ->
            assertTrue(
                "ActivityHistoryScreen should not import or reference $rawColor directly.",
                !Regex("""\b$rawColor\b""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun memoryVaultScreen_usesThemeBackedColorRoles() {
        val source = sourceFile("app/src/main/java/com/example/ui/screens/memoryvault/MemoryVaultScreen.kt")
            .readText()

        assertTrue(
            "MemoryVaultScreen should resolve Material colors through MaterialTheme.colorScheme.",
            source.contains("MaterialTheme.colorScheme"),
        )
        assertTrue(
            "MemoryVaultScreen should resolve note card colors through MaterialTheme.relateSemanticColors.",
            source.contains("MaterialTheme.relateSemanticColors"),
        )
        listOf(
            "RelateCard",
            "RelateDarkBackground",
            "RelateOnSurfaceVariant",
            "RelatePrimary",
            "RelateSurfaceVariant",
        ).forEach { rawColor ->
            assertTrue(
                "MemoryVaultScreen should not import or reference $rawColor directly.",
                !Regex("""\b$rawColor\b""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun contactListScreen_usesThemeBackedColorRoles() {
        val source = sourceFile("app/src/main/java/com/example/ui/screens/contacts/ContactListScreen.kt")
            .readText()

        assertTrue(
            "ContactListScreen should resolve Material colors through MaterialTheme.colorScheme.",
            source.contains("MaterialTheme.colorScheme"),
        )
        listOf(
            "RelateDarkBackground",
            "RelateOnBackground",
            "RelateOnSurfaceVariant",
            "RelatePrimary",
            "RelateSurfaceVariant",
        ).forEach { rawColor ->
            assertTrue(
                "ContactListScreen should not import or reference $rawColor directly.",
                !Regex("""\b$rawColor\b""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun homeScreen_usesThemeBackedColorRoles() {
        val source = sourceFile("app/src/main/java/com/example/ui/screens/home/HomeScreen.kt")
            .readText()

        assertTrue(
            "HomeScreen should resolve Material colors through MaterialTheme.colorScheme.",
            source.contains("MaterialTheme.colorScheme"),
        )
        assertTrue(
            "HomeScreen should resolve success and warning status colors through MaterialTheme.relateSemanticColors.",
            source.contains("MaterialTheme.relateSemanticColors"),
        )
        listOf(
            "RelateDarkBackground",
            "RelateOnSurfaceVariant",
            "RelatePrimary",
            "RelateSuccess",
            "RelateWarning",
        ).forEach { rawColor ->
            assertTrue(
                "HomeScreen should not import or reference $rawColor directly.",
                !Regex("""\b$rawColor\b""").containsMatchIn(source),
            )
        }
    }

    private fun sourceFile(rootRelativePath: String): File {
        return listOf(
            File(rootRelativePath),
            File("../$rootRelativePath"),
            File(rootRelativePath.removePrefix("app/")),
        ).firstOrNull { it.exists() }
            ?: error("Could not locate source file $rootRelativePath from ${File(".").absolutePath}")
    }
}
