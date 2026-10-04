import 'package:flutter/material.dart';

/// Centralized semantic color tokens for AI-Birthday (SSOT §3, §22).
///
/// Clean, warm, and editorial. Strictly avoids generic neon gradients or arbitrary hues.
class AppColors {
  const AppColors._();

  // Primary brand palette (Warm Terracotta)
  static const Color primaryTerracotta = Color(0xFFA64B2A);
  static const Color primaryTerracottaLight = Color(0xFFE28464);
  static const Color primaryTerracottaContainer = Color(0xFFFBECE6);

  // Secondary accents (Vintage Amber & Forest Green)
  static const Color accentAmber = Color(0xFFD9822B);
  static const Color accentAmberLight = Color(0xFFF0A65B);
  static const Color accentAmberContainer = Color(0xFFFEF3E7);

  static const Color accentForest = Color(0xFF2D5A46);
  static const Color accentForestLight = Color(0xFF4E8B6D);
  static const Color accentForestContainer = Color(0xFFE8F2EC);

  // Light surfaces (Linen / Warm paper)
  static const Color lightBackground = Color(0xFFFAF7F2);
  static const Color lightSurface = Color(0xFFFFFFFF);
  static const Color lightSurfaceVariant = Color(0xFFF2ECE4);
  static const Color lightBorder = Color(0xFFE5DDD3);
  static const Color lightTextPrimary = Color(0xFF1C1917);
  static const Color lightTextSecondary = Color(0xFF78716C);

  // Dark surfaces (Warm Charcoal / Espresso)
  static const Color darkBackground = Color(0xFF161413);
  static const Color darkSurface = Color(0xFF201D1B);
  static const Color darkSurfaceVariant = Color(0xFF2B2724);
  static const Color darkBorder = Color(0xFF38332F);
  static const Color darkTextPrimary = Color(0xFFF5F5F4);
  static const Color darkTextSecondary = Color(0xFFA8A29E);

  // Status & semantic colors
  static const Color error = Color(0xFFBA1A1A);
  static const Color errorContainer = Color(0xFFFFDAD6);
  static const Color success = Color(0xFF2D5A46);
  static const Color successContainer = Color(0xFFE8F2EC);
  static const Color whatsappGreen = Color(0xFF25D366);
}
