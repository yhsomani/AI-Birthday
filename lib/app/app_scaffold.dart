import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/shared/design_system/design_system.dart';

/// Material 3 responsive navigation scaffold hosting the 4-branch app shell.
///
/// Switches between a bottom [NavigationBar] (`< 840 dp`, clamped height for
/// large text scaling), a compact [NavigationRail] with labels (`840–1023 dp`),
/// and an extended [NavigationRail] (`>= 1024 dp`).
class AppScaffold extends StatelessWidget {
  const AppScaffold({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  void _onDestinationSelected(int index) {
    HapticFeedback.lightImpact();
    navigationShell.goBranch(
      index,
      initialLocation: index == navigationShell.currentIndex,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final textScale = MediaQuery.textScalerOf(context).scale(1.0);
    final barHeight = (80.0 * textScale.clamp(1.0, 2.0)).clamp(80.0, 160.0);

    final destinations = [
      (
        icon: const Icon(Icons.cake_outlined),
        selectedIcon: const Icon(Icons.cake),
        label: l10n.navToday,
      ),
      (
        icon: const Icon(Icons.people_outline),
        selectedIcon: const Icon(Icons.people),
        label: l10n.navPeople,
      ),
      (
        icon: const Icon(Icons.calendar_month_outlined),
        selectedIcon: const Icon(Icons.calendar_month),
        label: l10n.navCalendar,
      ),
      (
        icon: const Icon(Icons.history_outlined),
        selectedIcon: const Icon(Icons.history),
        label: l10n.navActivity,
      ),
    ];

    return LayoutBuilder(
      builder: (context, constraints) {
        final isExpanded = constraints.maxWidth >= AppSpace.expandedBreakpoint;
        if (isExpanded) {
          final isExtendedRail =
              constraints.maxWidth >= AppSpace.extendedRailBreakpoint;
          return Scaffold(
            body: Row(
              children: [
                RepaintBoundary(
                  child: NavigationRail(
                    extended: isExtendedRail,
                    labelType: isExtendedRail
                        ? NavigationRailLabelType.none
                        : NavigationRailLabelType.all,
                    selectedIndex: navigationShell.currentIndex,
                    onDestinationSelected: _onDestinationSelected,
                    destinations: [
                      for (final d in destinations)
                        NavigationRailDestination(
                          icon: d.icon,
                          selectedIcon: d.selectedIcon,
                          label: Text(d.label),
                        ),
                    ],
                  ),
                ),
                const VerticalDivider(width: 1, thickness: 1),
                Expanded(child: navigationShell),
              ],
            ),
          );
        }

        return Scaffold(
          body: navigationShell,
          // RepaintBoundary isolates the bar's layer from body repaints (and
          // makes shell goldens capture the bar alone, not the whole screen).
          bottomNavigationBar: RepaintBoundary(
            child: NavigationBar(
              height: barHeight,
              selectedIndex: navigationShell.currentIndex,
              onDestinationSelected: _onDestinationSelected,
              destinations: [
                for (final d in destinations)
                  NavigationDestination(
                    icon: d.icon,
                    selectedIcon: d.selectedIcon,
                    label: d.label,
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}
