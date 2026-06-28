package com.example.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color

val RelateDarkBackground = Color(0xFF0F0F1A)
val RelateSurface = Color(0xFF1E1E2E)
val RelateSurfaceVariant = Color(0xFF2A2A3E)
val RelateCard = Color(0xFF1A1A2E)
val RelateCardBorder = Color(0x1AFFFFFF)

val RelatePrimary = Color(0xFF8B5CF6)
val RelatePrimaryVariant = Color(0xFF7C3AED)
val RelatePrimaryContainer = Color(0xFF3B1F8E)
val RelateOnPrimary = Color(0xFFFFFFFF)

val RelateSecondary = Color(0xFF22D3EE)
val RelateSecondaryVariant = Color(0xFF06B6D4)
val RelateOnSecondary = Color(0xFF0F0F1A)

val RelateTertiary = Color(0xFFFB7185)
val RelateOnTertiary = Color(0xFF0F0F1A)

val RelateOnBackground = Color(0xFFF8FAFC)
val RelateOnSurface = Color(0xFFE2E8F0)
val RelateOnSurfaceVariant = Color(0xFF94A3B8)
val RelateOutline = Color(0xFF475569)

val RelateError = Color(0xFFEF4444)
val RelateOnError = Color(0xFFFFFFFF)

val RelateSuccess = Color(0xFF22C55E)
val RelateWarning = Color(0xFFF59E0B)

val HealthGradientStart = Color(0xFF22C55E)
val HealthGradientEnd = Color(0xFFEF4444)

val VioletGlow = Color(0x338B5CF6)

@Immutable
data class RelateSemanticColors(
    val cardContainer: Color,
    val cardOutline: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

internal val RelateDarkSemanticColors = RelateSemanticColors(
    cardContainer = RelateCard,
    cardOutline = RelateCardBorder,
    success = RelateSuccess,
    onSuccess = RelateDarkBackground,
    successContainer = RelateSuccess.copy(alpha = RelateAlpha.feedbackContainer),
    onSuccessContainer = RelateSuccess,
    warning = RelateWarning,
    onWarning = RelateDarkBackground,
    warningContainer = RelateWarning.copy(alpha = RelateAlpha.feedbackContainer),
    onWarningContainer = RelateWarning,
    info = RelatePrimary,
    onInfo = RelateOnPrimary,
    infoContainer = RelatePrimary.copy(alpha = RelateAlpha.feedbackContainer),
    onInfoContainer = RelatePrimary,
)

internal val LocalRelateSemanticColors = staticCompositionLocalOf { RelateDarkSemanticColors }

val MaterialTheme.relateSemanticColors: RelateSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalRelateSemanticColors.current
