import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_handoff.dart';
import 'package:ai_birthday/features/history/presentation/history_screen.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';

void main() {
  group('HistoryScreen Status Truthfulness (P0)', () {
    testWidgets(
      'shows "Opened in WhatsApp" only from a persisted handoff, "Sent" for confirmedSent',
      (tester) async {
        // b1 was genuinely handed off to WhatsApp (persisted evidence) —
        // even so it must NOT read "Sent".
        final handedOffDraft = MessageDraft(
          id: 'd1',
          birthdayId: 'b1',
          personId: 'p1',
          body: 'Happy birthday friend!',
          status: DraftStatus.ready,
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

        // A handed-off draft WITHOUT any persisted record must never render
        // "Opened in WhatsApp" — no record, no claim (audit 03 AC1).
        final noRecordDraft = MessageDraft(
          id: 'd3',
          birthdayId: 'b3',
          personId: 'p3',
          body: 'No launch evidence.',
          status: DraftStatus.ready,
          createdAt: DateTime(2026, 1, 3),
          updatedAt: DateTime(2026, 1, 3),
        );

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              draftsStreamProvider.overrideWith(
                (_) => Stream.value([
                  handedOffDraft,
                  confirmedSentDraft,
                  noRecordDraft,
                ]),
              ),
              peopleStreamProvider.overrideWith((_) => Stream.value([])),
              deliveryEventsStreamProvider.overrideWith(
                (_) => Stream.value([
                  DeliveryHandoff(
                    birthdayId: 'b1',
                    channel: DeliveryChannel.whatsapp,
                    at: DateTime(2026, 1, 1, 9),
                  ),
                ]),
              ),
            ],
            child: const MaterialApp(home: HistoryScreen()),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Opened in WhatsApp'), findsOneWidget);
        expect(find.text('Sent'), findsOneWidget);
        // No evidence → honest neutral label, never an "Opened" claim.
        expect(find.text('Ready to Send'), findsOneWidget);
      },
    );
  });
}