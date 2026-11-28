@Tags(['e2e'])
library;

import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/calendar/presentation/calendar_screen.dart';
import 'package:ai_birthday/features/dashboard/presentation/dashboard_screen.dart';
import 'package:ai_birthday/features/history/presentation/history_screen.dart';
import 'package:ai_birthday/features/onboarding/presentation/onboarding_screen.dart';
import 'package:ai_birthday/features/people/presentation/people_screen.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';

import 'support/e2e_app_harness.dart';
import 'support/responsive_helpers.dart';
import 'support/seed_helpers.dart';

void main() {
  testWidgets(
    'E1 smoke: cold boot onboarding skip, 4-tab shell, Settings round-trip, clean teardown',
    (tester) async {
      final harness = await E2eAppHarness.create(onboardingCompleted: false);
      try {
        await expectNoLayoutOverflows(tester, () async {
          await harness.pumpApp(tester, viewport: E2eViewport.phone412);

          // 1. Verify first-run OnboardingScreen.
          expect(find.byType(OnboardingScreen), findsOneWidget);
          expect(
            find.text('Never miss a birthday that matters.'),
            findsOneWidget,
          );

          // 2. Complete onboarding via Skip -> lands on Dashboard empty shell.
          await completeOnboardingBySkip(tester);
          expect(
            harness.secureStorage.values['ai_birthday_onboarding_completed'],
            'true',
          );
          expect(find.byType(DashboardScreen), findsOneWidget);
          expect(find.text("Let's add your first birthday"), findsOneWidget);

          // 3. Switch across all 4 primary shell branches.
          await switchTab(tester, 'People');
          expect(find.byType(PeopleScreen), findsOneWidget);

          await switchTab(tester, 'Calendar');
          expect(find.byType(CalendarScreen), findsOneWidget);

          await switchTab(tester, 'Activity');
          expect(find.byType(HistoryScreen), findsOneWidget);

          await switchTab(tester, 'Today');
          expect(find.byType(DashboardScreen), findsOneWidget);

          // 4. Push /settings and return cleanly to the shell.
          await tester.tap(find.byTooltip('Settings').first);
          await tester.pumpAndSettle();
          expect(find.byType(SettingsScreen), findsOneWidget);
          expect(find.text('Settings'), findsOneWidget);

          await tester.pageBack();
          await tester.pumpAndSettle();
          expect(find.byType(DashboardScreen), findsOneWidget);

          // 5. Explicit timer-draining unmount before DB close.
          await harness.disposeApp(tester);
        });
      } finally {
        await harness.tearDown(tester);
      }
    },
  );
}
