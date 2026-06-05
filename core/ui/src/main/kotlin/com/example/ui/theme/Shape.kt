package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════
// RelateAI Shapes — Neon Glassmorphic Radii
// ═══════════════════════════════════════════════

val RelateAIShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // Chips, tags
    small = RoundedCornerShape(8.dp),        // Small chips, badges
    medium = RoundedCornerShape(12.dp),      // Buttons, input fields
    large = RoundedCornerShape(16.dp),       // Cards, containers
    extraLarge = RoundedCornerShape(24.dp)   // Bottom sheets, modals
)

// Additional custom shapes
val PillShape = RoundedCornerShape(9999.dp)  // FABs, pill buttons
val BottomSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
