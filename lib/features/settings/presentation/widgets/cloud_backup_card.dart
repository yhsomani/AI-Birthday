import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'package:ai_birthday/shared/design_system/design_system.dart';

/// Cloud backup card on the Settings screen: the last successful backup, the
/// backup and restore actions, and the durable failure status (audit F22).
class CloudBackupCard extends StatelessWidget {
  const CloudBackupCard({
    super.key,
    required this.lastSyncTime,
    required this.cloudError,
    this.restoreReport,
    this.restoreDetails = const [],
    required this.isSyncing,
    this.isRestoring = false,
    required this.onBackup,
    required this.onRestore,
  });

  final DateTime? lastSyncTime;
  final String? cloudError;
  final String? restoreReport;
  final List<String> restoreDetails;
  final bool isSyncing;
  final bool isRestoring;
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
            Wrap(
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 8,
              runSpacing: 4,
              children: [
                Icon(
                  Icons.cloud_upload_outlined,
                  color: context.colors.success,
                ),
                const Text(
                  'Cloud Backup (Firestore)',
                  softWrap: true,
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                if (lastSyncTime != null) ...[
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
              'Privacy note: Backup uploads saved people, birthdays, drafts and reminder settings to this app\'s Firestore project under your signed-in account. It is not end-to-end encrypted and should only be enabled when you accept that cloud-service boundary. Gemini keys and auth tokens are never included.',
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
                    label: Text(
                      isSyncing
                          ? (isRestoring ? 'Restoring...' : 'Backing up...')
                          : 'Back Up Now',
                    ),
                  ),
                ],
              ),
            ),
            if (restoreReport != null) ...[
              const SizedBox(height: 12),
              Semantics(
                liveRegion: true,
                child: Text(
                  restoreReport!,
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: context.colors.success,
                  ),
                ),
              ),
              if (restoreDetails.isNotEmpty)
                ExpansionTile(
                  tilePadding: EdgeInsets.zero,
                  title: Text(
                    'Restore details (${restoreDetails.length})',
                    style: const TextStyle(fontSize: 12),
                  ),
                  children: [
                    for (final detail in restoreDetails)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 6),
                        child: Align(
                          alignment: Alignment.centerLeft,
                          child: Text(
                            detail,
                            style: TextStyle(
                              fontSize: 11,
                              color: context.colors.textSecondary,
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
            ],
            // Durable failure status: the job row remembers that the
            // last backup/restore failed, even after this screen was
            // closed and reopened. Tapping the button again retries.
            if (cloudError != null) ...[
              const SizedBox(height: 10),
              Semantics(
                liveRegion: true,
                child: Row(
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
              ),
            ],
          ],
        ),
      ),
    );
  }
}
