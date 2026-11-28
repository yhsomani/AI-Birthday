import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/theme/app_theme.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/l10n/app_localizations.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';

void main() {
  Widget host(
    Widget child, {
    bool disableAnimations = false,
    ThemeData? theme,
  }) {
    return MaterialApp(
      theme: theme ?? AppTheme.light,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: MediaQuery(
        data: MediaQueryData(disableAnimations: disableAnimations),
        child: Scaffold(body: child),
      ),
    );
  }

  testWidgets('AppResponsiveContainer constrains content to 720dp on desktop', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1280, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      host(
        const AppResponsiveContainer(
          child: SizedBox(
            key: ValueKey('probe'),
            width: double.infinity,
            height: 100,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final rect = tester.getRect(find.byKey(const ValueKey('probe')));
    expect(rect.width, 720.0);
  });

  testWidgets('EditorialCard enforces >= 48dp touch height and fires onTap', (
    tester,
  ) async {
    var tapped = false;
    await tester.pumpWidget(
      host(
        EditorialCard(
          onTap: () => tapped = true,
          padding: EdgeInsets.zero,
          child: const Text('Tap me'),
        ),
      ),
    );

    final size = tester.getSize(find.byType(EditorialCard));
    expect(size.height, greaterThanOrEqualTo(AppSpace.touchTargetMin));

    await tester.tap(find.text('Tap me'));
    expect(tapped, isTrue);
  });

  testWidgets('EditorialEmptyState renders cleanly at 320x568', (tester) async {
    tester.view.physicalSize = const Size(320, 568);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      host(
        EditorialEmptyState(
          icon: Icons.cake_outlined,
          title: 'Nothing here yet',
          message: 'Add a birthday contact to begin.',
          action: FilledButton(onPressed: () {}, child: const Text('Add')),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Nothing here yet'), findsOneWidget);
    expect(find.text('Add a birthday contact to begin.'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'EditorialErrorState announces liveRegion and invokes retry callback',
    (tester) async {
      final handle = tester.ensureSemantics();
      var retried = false;
      const failure = AppFailure(
        AppFailureCode.networkUnavailable,
        message: 'Network unavailable',
        detail: 'Could not reach server.',
        action: 'Check your connection.',
      );

      await tester.pumpWidget(
        host(
          EditorialErrorState(failure: failure, onRetry: () => retried = true),
        ),
      );
      await tester.pumpAndSettle();

      final node = tester.getSemantics(find.byType(EditorialErrorState));
      expect(node.flagsCollection.isLiveRegion, isTrue);
      expect(find.text(failure.message!), findsOneWidget);
      expect(find.textContaining('Could not reach server.'), findsOneWidget);

      await tester.tap(find.text('Try again'));
      expect(retried, isTrue);
      handle.dispose();
    },
  );

  testWidgets(
    'EditorialSkeletonList respects disableAnimations and renders without overflow at 320x568',
    (tester) async {
      tester.view.physicalSize = const Size(320, 568);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      // Static mode: pumpAndSettle completes immediately (no AnimationController).
      await tester.pumpWidget(
        host(
          const EditorialSkeletonList(itemCount: 3),
          disableAnimations: true,
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.byType(EditorialSkeletonCard), findsNWidgets(3));

      // Animated mode: pump advances shimmer frame cleanly without overflow.
      await tester.pumpWidget(
        host(
          const EditorialSkeletonList(itemCount: 3),
          disableAnimations: false,
          theme: AppTheme.dark,
        ),
      );
      await tester.pump(const Duration(milliseconds: 300));
      expect(tester.takeException(), isNull);
    },
  );
}
