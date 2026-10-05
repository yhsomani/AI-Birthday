import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../app/providers.dart';
import '../../../shared/design_system/design_system.dart';
import '../../message_studio/domain/models/message_draft.dart';

/// History — message and delivery activity timeline (SSOT §3, §16, §28).
class HistoryScreen extends ConsumerWidget {
  const HistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final draftsAsync = ref.watch(draftsStreamProvider);
    final peopleAsync = ref.watch(peopleStreamProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('History & Activity')),
      body: draftsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => Center(child: Text('Error loading history: $err')),
        data: (drafts) {
          if (drafts.isEmpty) {
            return const EmptyState(
              icon: Icons.history_outlined,
              title: 'No activity yet',
              message: 'Prepared and sent messages will appear here.',
            );
          }

          final people = peopleAsync.asData?.value ?? [];
          final peopleMap = {for (final p in people) p.id: p};

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
              final recipientName = person?.name ?? 'Birthday Recipient';
              final statusColor = switch (draft.status) {
                DraftStatus.confirmedSent => Colors.green[800] ?? Colors.green,
                DraftStatus.handedOff => Colors.orange[800] ?? Colors.orange,
                DraftStatus.ready => Theme.of(context).colorScheme.primary,
                DraftStatus.reviewed => Colors.teal[800] ?? Colors.teal,
                DraftStatus.draft => Colors.grey[700] ?? Colors.grey,
              };

              final (statusIcon, statusLabel) = switch (draft.status) {
                DraftStatus.confirmedSent => (Icons.check_circle_outline, 'Sent'),
                DraftStatus.handedOff => (Icons.open_in_new, 'Opened in WhatsApp'),
                DraftStatus.ready => (Icons.send_outlined, 'Ready to Send'),
                DraftStatus.reviewed => (Icons.rate_review_outlined, 'Reviewed'),
                DraftStatus.draft => (Icons.edit_note_outlined, 'Draft'),
              };

              return Card(
                elevation: 0,
                shape: RoundedRectangleBorder(
                  side: BorderSide(
                    color: Theme.of(context).colorScheme.outlineVariant,
                  ),
                  borderRadius: BorderRadius.circular(16),
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
                              color: statusColor.withValues(alpha: 0.12),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  statusIcon,
                                  size: 14,
                                  color: statusColor,
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  statusLabel,
                                  style: TextStyle(
                                    fontSize: 12,
                                    fontWeight: FontWeight.bold,
                                    color: statusColor,
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
