package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════
// RelateAI Neon Glassmorphic Color Palette
// Matches Stitch Design System: assets/6577782804216846461
// ═══════════════════════════════════════════════

// Primary — Neon Violet
val NeonViolet = Color(0xFF8B5CF6)
val NeonVioletLight = Color(0xFFD0BCFF)
val NeonVioletDark = Color(0xFF6D3BD7)
val NeonVioletContainer = Color(0xFFA078FF)
val OnNeonViolet = Color(0xFF3C0091)

// Secondary — Electric Cyan
val ElectricCyan = Color(0xFF06B6D4)
val ElectricCyanLight = Color(0xFF4CD7F6)
val ElectricCyanContainer = Color(0xFF03B5D3)
val OnElectricCyan = Color(0xFF003640)

// Tertiary — Cyber Rose
val CyberRose = Color(0xFFF43F5E)
val CyberRoseLight = Color(0xFFFFB2B7)
val CyberRoseContainer = Color(0xFFFF516A)
val OnCyberRose = Color(0xFF67001B)

// Success
val Emerald = Color(0xFF10B981)
val EmeraldLight = Color(0xFF6EE7B7)

// Error
val ErrorRed = Color(0xFFFFB4AB)
val ErrorRedContainer = Color(0xFF93000A)
val OnError = Color(0xFF690005)

// Backgrounds
val ObsidianBlack = Color(0xFF05070F)
val DarkSlate = Color(0xFF0F111E)
val SurfaceElevated = Color(0xFF1A1D2E)
val SurfaceContainerHigh = Color(0xFF2C2832)
val SurfaceContainerHighest = Color(0xFF37333D)

// Borders & Glass
val BorderStructural = Color(0xFF1F2937)
val GlassEdge = Color(0x0FFFFFFF) // rgba(255, 255, 255, 0.06)
val GlassEdgeStrong = Color(0x14FFFFFF) // rgba(255, 255, 255, 0.08)

// Text
val TextPrimary = Color(0xFFE7E0ED)
val TextSecondary = Color(0xFF9CA3AF)
val TextTertiary = Color(0xFF6B7280)
val TextOnPrimary = Color.White

// Outline
val Outline = Color(0xFF958EA0)
val OutlineVariant = Color(0xFF494454)

// Inverse
val InverseSurface = Color(0xFFE7E0ED)
val InverseOnSurface = Color(0xFF322F39)
val InversePrimary = Color(0xFF6D3BD7)

object RelateAIColors {
    val Primary = NeonViolet
    val PrimaryLight = NeonVioletLight
    val PrimaryDark = NeonVioletDark

    val Secondary = ElectricCyan
    val SecondaryLight = ElectricCyanLight
    val SecondaryDark = ElectricCyanContainer

    val Tertiary = CyberRose
    val TertiaryLight = CyberRoseLight
    val TertiaryDark = CyberRoseContainer

    val Thriving = Emerald
    val Stable = ElectricCyan
    val NeedsAttention = Color(0xFFFBBF24) // Yellow/Amber
    val AtRisk = CyberRose

    val Family = NeonViolet
    val BestFriend = ElectricCyan
    val CloseFriend = NeonVioletLight
    val Friend = Emerald
    val Colleague = TextSecondary
    val Client = NeonVioletDark
    val Mentor = Color(0xFFFBBF24)

    val Surface = SurfaceElevated
    val SurfaceVariant = DarkSlate
    val Background = ObsidianBlack

    val SurfaceDark = DarkSlate
    val SurfaceVariantDark = SurfaceElevated
    val BackgroundDark = ObsidianBlack

    val OnSurface = TextPrimary
    val OnSurfaceVariant = TextSecondary
    val Outline = com.example.ui.theme.Outline

    val OnSurfaceDark = TextPrimary
    val OnSurfaceVariantDark = TextSecondary
    val OutlineDark = com.example.ui.theme.Outline
}

