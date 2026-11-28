import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/l10n/l10n_extension.dart';

class SettingsActionButton extends StatelessWidget {
  const SettingsActionButton({super.key});

  @override
  Widget build(BuildContext context) => IconButton(
    tooltip: context.l10n.navSettingsTooltip,
    icon: const Icon(Icons.settings_outlined),
    onPressed: () => context.push('/settings'),
  );
}
