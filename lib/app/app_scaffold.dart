import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/features/reminders/application/reminder_providers.dart';
import 'package:ai_birthday/l10n/app_localizations.dart';

/// Material 3 bottom-navigation scaffold hosting the application shell (SSOT §15, §24).
class AppScaffold extends ConsumerStatefulWidget {
  const AppScaffold({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  ConsumerState<AppScaffold> createState() => _AppScaffoldState();
}

class _AppScaffoldState extends ConsumerState<AppScaffold> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final gateway = ref.read(notificationSchedulerGatewayProvider);
      final initialPersonId = await gateway.getInitialNotificationPersonId();
      if (initialPersonId != null && mounted) {
        context.push('/message-studio/person/$initialPersonId');
      }
      gateway.setNotificationOpenedHandler((personId) {
        if (mounted) {
          context.push('/message-studio/person/$personId');
        }
      });
    });
  }

  void _onDestinationSelected(int index) {
    HapticFeedback.lightImpact();
    widget.navigationShell.goBranch(
      index,
      initialLocation: index == widget.navigationShell.currentIndex,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      body: widget.navigationShell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: widget.navigationShell.currentIndex,
        onDestinationSelected: _onDestinationSelected,
        destinations: [
          NavigationDestination(
            icon: const Icon(Icons.cake_outlined),
            selectedIcon: const Icon(Icons.cake),
            label: l10n.navDashboard,
          ),
          NavigationDestination(
            icon: const Icon(Icons.people_outline),
            selectedIcon: const Icon(Icons.people),
            label: l10n.navPeople,
          ),
          NavigationDestination(
            icon: const Icon(Icons.calendar_month_outlined),
            selectedIcon: const Icon(Icons.calendar_month),
            label: l10n.navCalendar,
          ),
          NavigationDestination(
            icon: const Icon(Icons.history_outlined),
            selectedIcon: const Icon(Icons.history),
            label: l10n.navHistory,
          ),
          NavigationDestination(
            icon: const Icon(Icons.tune_outlined),
            selectedIcon: const Icon(Icons.tune),
            label: l10n.navSettings,
          ),
        ],
      ),
    );
  }
}
