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
import '../../../ui/design_system/app_tokens.dart';

/// History — message and delivery activity timeline (SSOT §3, §16, §28).
class HistoryScreen extends ConsumerWidget {
  const HistoryScreen({super.key});

  /// "Opened in" is only honest for channels that actually opened (WhatsApp /
  /// SMS); clipboard and share sheet handoffs are labelled by what they did.
  String _handoffLabel(DeliveryHandoff handoff) => switch (handoff.channel) {
    DeliveryChannel.whatsapp ||
    DeliveryChannel.sms => 'Opened in ${handoff.channel.displayName}',
    DeliveryChannel.clipboard => 'Copied to Clipboard',
    DeliveryChannel.share => 'Shared',
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

          final people = peopleAsync.asData?.value ?? [];
          final peopleMap = {for (final p in people) p.id: p};

          // Resolve the latest persisted handoff per birthday (audit 03 P1-1):
          // "Opened in <channel>" is evidence-driven, never inferred from
          // draft status.
          final handoffs =
              handoffsAsync.asData?.value ?? const <DeliveryHandoff>[];
          final latestHandoffByBirthday = <String, DeliveryHandoff>{};
          for (final h in handoffs) {
            final existing = latestHandoffByBirthday[h.birthdayId];
            if (existing == null || h.at.isAfter(existing.at)) {
              latestHandoffByBirthday[h.birthdayId] = h;
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
              final handoff = latestHandoffByBirthday[draft.birthdayId];
              final sent = draft.status == DraftStatus.confirmedSent;
              // Status chips use the palette's contrast-tested tone pairs so
              // they stay legible in dark mode (audit 05 P1-3).
              final colors = context.colors;
              final statusTone = sent
                  ? AppTone.success
                  : handoff != null
                  ? AppTone.warning
                  : switch (draft.status) {
                      DraftStatus.ready => AppTone.primary,
                      DraftStatus.draft => AppTone.neutral,
                      // confirmedSent is handled above via [sent].
                      _ => AppTone.neutral,
                    };

              final (statusIcon, statusLabel) = sent
                  ? (Icons.check_circle_outline, 'Sent')
                  : handoff != null
                  ? (Icons.open_in_new, _handoffLabel(handoff))
                  : switch (draft.status) {
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
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 10,
                              vertical: 4,
                            ),
                            decoration: BoxDecoration(
                              color: statusTone.fill(colors),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  statusIcon,
                                  size: 14,
                                  color: statusTone.label(colors),
                                ),
                                const SizedBox(width: 4),
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
                        ],
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
                      Row(
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          TextButton.icon(
                            onPressed: () {
                              Clipboard.setData(
                                ClipboardData(text: draft.body),
                              );
                              HapticFeedback.lightImpact();
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(
                                  content: Text('Message copied to clipboard!'),
                                  duration: Duration(seconds: 2),
                                ),
                              );
                            },
                            icon: const Icon(Icons.copy, size: 16),
                            label: const Text('Copy'),
                          ),
                          const SizedBox(width: 8),
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
