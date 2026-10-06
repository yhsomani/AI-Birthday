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
    // NavigationBar's height is fixed by design (it never scales with text
    // size), so at large text scales its labels wrap past the bar's bottom
    // edge. Grow the bar linearly with the user's scale instead: at the 200%
    // ceiling this accommodates even 3-4 line wrapped labels on narrow
    // phones, and at 1.0x it is exactly the M3 80dp. (M3 itself clamps label
    // scale to 1.3 but still wraps at narrow widths.)
    final textScale = MediaQuery.textScalerOf(context).scale(1.0);

    return Scaffold(
      body: widget.navigationShell,
      // RepaintBoundary isolates the bar's layer from body repaints (and
      // makes shell goldens capture the bar alone, not the whole screen).
      bottomNavigationBar: RepaintBoundary(
        child: NavigationBar(
          height: 80 * textScale,
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
      ),
    );
  }
}
