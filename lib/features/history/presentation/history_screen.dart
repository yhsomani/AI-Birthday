import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../app/providers.dart';
import '../../../shared/design_system/design_system.dart';
import '../../delivery/domain/models/delivery_channel.dart';
import '../../delivery/domain/models/delivery_handoff.dart';
import '../../message_studio/domain/models/message_draft.dart';

/// History — message and delivery activity timeline (SSOT §3, §16, §28).
class HistoryScreen extends ConsumerWidget {
  const HistoryScreen({super.key});

  /// "Opened in" is only honest for channels that actually opened (WhatsApp /
  /// SMS); clipboard and share sheet handoffs are labelled by what they did.
  String _handoffLabel(DeliveryHandoff handoff) => switch (handoff.outcome) {
    DeliveryOutcome.copied => 'Copied to Clipboard',
    DeliveryOutcome.launchFailed =>
      '${handoff.channel.displayName} could not be opened',
    DeliveryOutcome.notSent => 'Not Sent Yet',
    DeliveryOutcome.confirmed => 'User Confirmed Sent',
    DeliveryOutcome.launched => switch (handoff.channel) {
      DeliveryChannel.whatsapp ||
      DeliveryChannel.sms => 'Opened in ${handoff.channel.displayName}',
      DeliveryChannel.clipboard => 'Copied to Clipboard',
      DeliveryChannel.share => 'Share Sheet Opened',
    },
  };

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final draftsAsync = ref.watch(draftsStreamProvider);
    final peopleAsync = ref.watch(peopleStreamProvider);
    // Batched: only the latest handoff per drafted birthday is loaded, never
    // the full (unbounded) event log (query audit).
    final handoffsAsync = ref.watch(latestHandoffsForDraftsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('History & Activity')),
      body: draftsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => EmptyState(
          icon: Icons.cloud_off_outlined,
          title: 'Could not load activity',
          message: 'Your saved messages have not been deleted.',
          action: TextButton.icon(
            onPressed: () => ref.invalidate(draftsStreamProvider),
            icon: const Icon(Icons.refresh),
            label: const Text('Try again'),
          ),
        ),
        data: (drafts) {
          if (drafts.isEmpty) {
            return EmptyState(
              icon: Icons.history_outlined,
              title: 'No activity yet',
              message: 'Prepared and sent messages will appear here.',
              action: FilledButton.icon(
                onPressed: () {
                  HapticFeedback.lightImpact();
                  context.push('/people/add');
                },
                icon: const Icon(Icons.add),
                label: const Text('Add Birthday Contact'),
              ),
            );
          }

          if (peopleAsync.isLoading) {
            return const Center(child: CircularProgressIndicator());
          }
          if (peopleAsync.hasError) {
            return EmptyState(
              icon: Icons.people_outline,
              title: 'Could not load contacts',
              message: 'History is waiting for contact details.',
              action: TextButton.icon(
                onPressed: () => ref.invalidate(peopleStreamProvider),
                icon: const Icon(Icons.refresh),
                label: const Text('Try again'),
              ),
            );
          }
          if (handoffsAsync.isLoading) {
            return const Center(child: CircularProgressIndicator());
          }
          if (handoffsAsync.hasError) {
            return EmptyState(
              icon: Icons.cloud_off_outlined,
              title: 'Could not load delivery activity',
              message: 'Your saved messages are still available.',
              action: TextButton.icon(
                onPressed: () =>
                    ref.invalidate(latestHandoffsForDraftsProvider),
                icon: const Icon(Icons.refresh),
                label: const Text('Try again'),
              ),
            );
          }

          final people = peopleAsync.requireValue;
          final peopleMap = {for (final p in people) p.id: p};

          // Resolve the latest persisted handoff per birthday (audit 03 P1-1):
          // "Opened in <channel>" is evidence-driven, never inferred from
          // draft status.
          final handoffs = handoffsAsync.requireValue;
          final draftCountsByBirthday = <String, int>{};
          for (final draft in drafts) {
            draftCountsByBirthday.update(
              draft.birthdayId,
              (count) => count + 1,
              ifAbsent: () => 1,
            );
          }
          final latestHandoffByDraft = <String, DeliveryHandoff>{};
          for (final h in handoffs) {
            final key = h.draftId ?? h.birthdayId;
            final existing = latestHandoffByDraft[key];
            if (existing == null || h.at.isAfter(existing.at)) {
              latestHandoffByDraft[key] = h;
            }
          }

          return ListView.separated(
            padding: const EdgeInsets.fromLTRB(
              16,
              16,
              16,
              AppSpacing.bottomClearance,
            ),
            itemCount: drafts.length,
            separatorBuilder: (_, _) => const SizedBox(height: 12),
            itemBuilder: (context, index) {
              final draft = drafts[index];
              final person = peopleMap[draft.personId];
              // The contact may have been deleted; say so instead of showing
              // a placeholder name that reads like a real recipient.
              final recipientName = person?.name ?? 'Removed contact';
              final handoff =
                  latestHandoffByDraft[draft.id] ??
                  (draftCountsByBirthday[draft.birthdayId] == 1
                      ? latestHandoffByDraft[draft.birthdayId]
                      : null);
              final sent = draft.status == DraftStatus.confirmedSent;
              // Status chips use the palette's contrast-tested tone pairs so
              // they stay legible in dark mode (audit 05 P1-3).
              final colors = context.colors;
              final statusTone =
                  sent || handoff?.outcome == DeliveryOutcome.confirmed
                  ? AppTone.success
                  : handoff != null
                  ? AppTone.warning
                  : switch (draft.status) {
                      DraftStatus.approvedForAutoSms => AppTone.info,
                      DraftStatus.ready => AppTone.primary,
                      DraftStatus.draft => AppTone.neutral,
                      // confirmedSent is handled above via [sent].
                      _ => AppTone.neutral,
                    };

              final (statusIcon, statusLabel) = sent
                  ? (Icons.check_circle_outline, 'User Confirmed Sent')
                  : handoff != null
                  ? (Icons.open_in_new, _handoffLabel(handoff))
                  : switch (draft.status) {
                      DraftStatus.approvedForAutoSms => (
                        Icons.alarm_on_outlined,
                        'Auto-SMS Scheduled',
                      ),
                      DraftStatus.ready => (
                        Icons.send_outlined,
                        'Ready to Send',
                      ),
                      DraftStatus.draft => (Icons.edit_note_outlined, 'Draft'),
                      _ => (Icons.edit_note_outlined, 'Draft'),
                    };

              return Card(
                elevation: 0,
                shape: RoundedRectangleBorder(
                  side: BorderSide(
                    color: Theme.of(context).colorScheme.outlineVariant,
                  ),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          CircleAvatar(
                            radius: 18,
                            child: Text(
                              recipientName.isNotEmpty
                                  ? recipientName[0].toUpperCase()
                                  : '?',
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  recipientName,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.bold,
                                    fontSize: 16,
                                  ),
                                ),
                                Text(
                                  DateFormat.yMMMd().add_jm().format(
                                    draft.updatedAt,
                                  ),
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: statusTone.fill(colors),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Wrap(
                          crossAxisAlignment: WrapCrossAlignment.center,
                          spacing: 4,
                          runSpacing: 2,
                          children: [
                            Icon(
                              statusIcon,
                              size: 14,
                              color: statusTone.label(colors),
                            ),
                            Text(
                              statusLabel,
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.bold,
                                color: statusTone.label(colors),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 12),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Theme.of(context)
                              .colorScheme
                              .surfaceContainerHighest
                              .withValues(alpha: 0.35),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(
                          draft.body,
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                      ),

                      const SizedBox(height: 12),
                      Wrap(
                        alignment: WrapAlignment.end,
                        spacing: 8,
                        runSpacing: 4,
                        children: [
                          TextButton.icon(
                            onPressed: () async {
                              try {
                                await Clipboard.setData(
                                  ClipboardData(text: draft.body),
                                );
                                await ref
                                    .read(deliveryEventsRepositoryProvider)
                                    .recordOutcome(
                                      birthdayId: draft.birthdayId,
                                      draftId: draft.id,
                                      channel: DeliveryChannel.clipboard,
                                      outcome: DeliveryOutcome.copied,
                                      at: DateTime.now(),
                                    );
                                if (!context.mounted) return;
                                HapticFeedback.lightImpact();
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(
                                    content: Text(
                                      'Message copied to clipboard!',
                                    ),
                                    duration: Duration(seconds: 2),
                                  ),
                                );
                              } catch (_) {
                                if (!context.mounted) return;
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(
                                    content: Text(
                                      'The message could not be recorded as copied.',
                                    ),
                                  ),
                                );
                              }
                            },
                            icon: const Icon(Icons.copy, size: 16),
                            label: const Text('Copy'),
                          ),
                          if (person != null)
                            FilledButton.tonalIcon(
                              onPressed: () {
                                context.push(
                                  '/message-studio/${draft.birthdayId}',
                                );
                              },
                              icon: const Icon(Icons.open_in_new, size: 16),
                              label: const Text('Studio'),
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
