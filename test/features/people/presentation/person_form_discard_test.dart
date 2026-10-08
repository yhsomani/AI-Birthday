/// Person form navigation safety: dirty edits are confirmed before the form
/// can be abandoned, and a pristine form exits without friction.
library;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/features/people/presentation/person_form_screen.dart';

class _HomeScreen extends StatelessWidget {
  const _HomeScreen();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: ElevatedButton(
          onPressed: () => context.push('/people/add'),
          child: const Text('Open form'),
        ),
      ),
    );
  }
}

void main() {
  late GoRouter router;

  setUp(() {
    router = GoRouter(
      initialLocation: '/home',
      routes: [
        GoRoute(path: '/home', builder: (_, _) => const _HomeScreen()),
        GoRoute(
          path: '/people/add',
          builder: (_, _) => const PersonFormScreen(),
        ),
      ],
    );
  });

  Widget buildTestApp() {
    return MaterialApp.router(routerConfig: router);
  }

  Future<void> openForm(WidgetTester tester) async {
    await tester.pumpWidget(buildTestApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Open form'));
    await tester.pumpAndSettle();
    expect(find.byType(PersonFormScreen), findsOneWidget);
  }

  testWidgets('edited form asks before discard; Keep Editing stays', (
    tester,
  ) async {
    await openForm(tester);

    await tester.enterText(find.byType(TextField).first, 'Alice');
    // Let the setState from onChanged rebuild PopScope.canPop before the back
    // gesture (mirrors the frame gap a real user always has).
    await tester.pump();
    await tester.tap(find.widgetWithText(TextButton, 'Cancel'));
    await tester.pumpAndSettle();

    expect(find.text('Discard changes?'), findsOneWidget);

    await tester.tap(find.text('Keep Editing'));
    await tester.pumpAndSettle();

    expect(find.text('Discard changes?'), findsNothing);
    expect(find.byType(PersonFormScreen), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('edited form Discard leaves for the previous screen', (
    tester,
  ) async {
    await openForm(tester);

    await tester.enterText(find.byType(TextField).first, 'Alice');
    await tester.pump();
    await tester.tap(find.widgetWithText(TextButton, 'Cancel'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Discard'));
    await tester.pumpAndSettle();

    expect(find.byType(PersonFormScreen), findsNothing);
    expect(find.text('Open form'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('pristine form backs out without a confirmation', (tester) async {
    await openForm(tester);

    await tester.tap(find.widgetWithText(TextButton, 'Cancel'));
    await tester.pumpAndSettle();

    expect(find.text('Discard changes?'), findsNothing);
    expect(find.byType(PersonFormScreen), findsNothing);
    expect(find.text('Open form'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
