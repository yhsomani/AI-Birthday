/// Widget previews for the AI-Birthday design system.
library;

import 'package:flutter/material.dart';
import 'package:flutter/widget_previews.dart';

import 'package:ai_birthday/app/theme/app_theme.dart';
import 'package:ai_birthday/l10n/app_localizations.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';

@Preview(name: 'Countdown Chip — Today (Light)')
Widget previewCountdownChipToday() {
  return MaterialApp(
    theme: AppTheme.light,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: const Scaffold(
      body: Center(
        child: CountdownChip(daysUntil: 0, isToday: true, customLabel: 'Today'),
      ),
    ),
  );
}

@Preview(name: 'Countdown Chip — Soon (Dark)')
Widget previewCountdownChipSoonDark() {
  return MaterialApp(
    theme: AppTheme.dark,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: const Scaffold(
      body: Center(
        child: CountdownChip(daysUntil: 3, customLabel: 'In 3 days'),
      ),
    ),
  );
}

@Preview(name: 'Empty State — Dashboard')
Widget previewEmptyState() {
  return MaterialApp(
    theme: AppTheme.light,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Scaffold(
      body: Center(
        child: EditorialEmptyState(
          icon: Icons.cake_outlined,
          title: 'No upcoming birthdays',
          message:
              'Add someone special to get timely reminders and draft wishes.',
          action: FilledButton(
            onPressed: () {},
            child: const Text('Add Person'),
          ),
        ),
      ),
    ),
  );
}
