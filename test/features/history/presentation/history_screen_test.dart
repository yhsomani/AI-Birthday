import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/history/presentation/history_screen.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';

void main() {
  group('HistoryScreen Status Truthfulness (P0)', () {
    testWidgets(
      'shows "Opened in WhatsApp" for handedOff and "Sent" for confirmedSent',
      (tester) async {
        final handedOffDraft = MessageDraft(
          id: 'd1',
          birthdayId: 'b1',
          personId: 'p1',
          body: 'Happy birthday friend!',
          status: DraftStatus.handedOff,
          createdAt: DateTime(2026, 1, 1),
          updatedAt: DateTime(2026, 1, 1),
        );

        final confirmedSentDraft = MessageDraft(
          id: 'd2',
          birthdayId: 'b2',
          personId: 'p2',
          body: 'Happy birthday family!',
          status: DraftStatus.confirmedSent,
          createdAt: DateTime(2026, 1, 2),
          updatedAt: DateTime(2026, 1, 2),
        );

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              draftsStreamProvider.overrideWith(
                (ref) => Stream.value([handedOffDraft, confirmedSentDraft]),
              ),
              peopleStreamProvider.overrideWith((ref) => Stream.value([])),
            ],
            child: const MaterialApp(home: Scaffold(body: HistoryScreen())),
          ),
        );

        await tester.pumpAndSettle();

        expect(find.text('Opened in WhatsApp'), findsOneWidget);
        expect(find.text('Sent'), findsOneWidget);
      },
    );
  });
}
