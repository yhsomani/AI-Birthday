import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'package:ai_birthday/ui/design_system/app_tokens.dart';

/// Cloud backup card on the Settings screen: the last successful backup, the
/// backup and restore actions, and the durable failure status (audit F22).
class CloudBackupCard extends StatelessWidget {
  const CloudBackupCard({
    super.key,
    required this.lastSyncTime,
    required this.cloudError,
    required this.isSyncing,
    required this.onBackup,
    required this.onRestore,
  });

  final DateTime? lastSyncTime;
  final String? cloudError;
  final bool isSyncing;
  final VoidCallback onBackup;
  final VoidCallback onRestore;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  Icons.cloud_upload_outlined,
                  color: context.colors.success,
                ),
                const SizedBox(width: 8),
                // Expanded so the title wraps instead of overflowing at
                // large text scales (audit 05 P2-3 sibling).
                const Expanded(
                  child: Text(
                    'Cloud Backup (Firestore)',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                ),
                if (lastSyncTime != null) ...[
                  const SizedBox(width: 8),
                  Chip(
                    label: Text(
                      'LAST SUCCESSFUL',
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                        color: context.colors.success,
                      ),
                    ),
                    backgroundColor: context.colors.success.withValues(
                      alpha: 0.12,
                    ),
                    visualDensity: VisualDensity.compact,
                  ),
                ],
              ],
            ),
            const SizedBox(height: 8),
            Text(
              lastSyncTime != null
                  ? 'Last backed up: ${DateFormat.yMMMd().add_jm().format(lastSyncTime!)}'
                  : 'Save a recoverable copy of your birthdays and contacts to your signed-in cloud account.',
              style: TextStyle(
                fontSize: 12,
                color: context.colors.textSecondary,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              'Privacy note: Cloud backup uploads your saved people, birthdays, message drafts, reminder settings, and related contact and personalization fields to this app\'s Firestore project under your signed-in account.',
              style: TextStyle(
                fontSize: 11,
                color: context.colors.textSecondary,
                fontStyle: FontStyle.italic,
              ),
            ),
            const SizedBox(height: 12),
            // Full width, so a wrapped second button right-aligns to the
            // card edge instead of the first button's width (device fix).
            SizedBox(
              width: double.infinity,
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                alignment: WrapAlignment.end,
                children: [
                  OutlinedButton.icon(
                    onPressed: isSyncing ? null : onRestore,
                    icon: const Icon(Icons.cloud_download_outlined, size: 18),
                    label: const Text('Restore from Cloud'),
                  ),
                  FilledButton.tonalIcon(
                    onPressed: isSyncing ? null : onBackup,
                    icon: isSyncing
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.cloud_upload, size: 18),
                    label: Text(isSyncing ? 'Backing up...' : 'Back Up Now'),
                  ),
                ],
              ),
            ),
            // Durable failure status: the job row remembers that the
            // last backup/restore failed, even after this screen was
            // closed and reopened. Tapping the button again retries.
            if (cloudError != null) ...[
              const SizedBox(height: 10),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.error_outline,
                    size: 16,
                    color: context.colors.danger,
                  ),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      '$cloudError Tap "Back Up Now" (or "Restore from Cloud") to retry.',
                      style: TextStyle(
                        fontSize: 12,
                        color: context.colors.danger,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}
