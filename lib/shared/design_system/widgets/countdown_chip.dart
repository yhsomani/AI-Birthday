import 'package:flutter/material.dart';

import 'package:ai_birthday/l10n/app_localizations.dart';
import 'package:ai_birthday/l10n/l10n_extension.dart';

import '../tokens/app_palette.dart';
import '../tokens/app_radius.dart';
import '../tokens/app_space.dart';

/// Contextual countdown badge indicating proximity to a birthday.
class CountdownChip extends StatelessWidget {
  const CountdownChip({
    super.key,
    required this.daysUntil,
    this.isToday = false,
    this.customLabel,
  });

  final int daysUntil;
  final bool isToday;
  final String? customLabel;

  /// Single source of truth for countdown labels, shared with People list tiles.
  static String labelFor({
    required int daysUntil,
    bool isToday = false,
    String? customLabel,
    AppLocalizations? l10n,
  }) {
    if (customLabel != null) return customLabel;
    if (isToday || daysUntil == 0) return l10n?.countdownToday ?? 'Today';
    if (daysUntil == 1) return l10n?.countdownTomorrow ?? 'Tomorrow';
    if (daysUntil <= 90) {
      return l10n?.countdownInDays(daysUntil) ?? 'In $daysUntil days';
    }
    final months = (daysUntil / 30).ceil();
    return l10n?.countdownInMonths(months) ?? 'In $months months';
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.colors;
    final label = labelFor(
      daysUntil: daysUntil,
      isToday: isToday,
      customLabel: customLabel,
      l10n: context.l10n,
    );

    final Color bg;
    final Color fg;

    if (isToday || daysUntil == 0) {
      bg = palette.primaryContainer;
      fg = palette.onPrimaryContainer;
    } else if (daysUntil == 1 || daysUntil <= 7) {
      bg = palette.warningContainer;
      fg = palette.onWarningContainer;
    } else {
      bg = palette.surfaceAlt;
      fg = palette.textSecondary;
    }

    return Container(
      constraints: const BoxConstraints(minHeight: 28),
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpace.sm + 2,
        vertical: AppSpace.xs,
      ),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: AppRadius.roundedSm,
        border: Border.all(color: fg.withValues(alpha: 0.2), width: 1),
      ),
      child: Center(
        widthFactor: 1.0,
        heightFactor: 1.0,
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w700,
            color: fg,
            letterSpacing: 0.2,
          ),
        ),
      ),
    );
  }
}
