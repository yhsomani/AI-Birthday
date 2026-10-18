import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/delivery/application/birthday_sms_service.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';

/// Settings card for Automatic Birthday SMS (Personal distribution feature).
class AutomaticSmsCard extends ConsumerStatefulWidget {
  const AutomaticSmsCard({super.key});

  @override
  ConsumerState<AutomaticSmsCard> createState() => _AutomaticSmsCardState();
}

class _AutomaticSmsCardState extends ConsumerState<AutomaticSmsCard> {
  bool _isReconciling = false;
  List<Map<String, dynamic>>? _logs;
  bool _isLoadingLogs = false;

  Future<void> _loadLogs() async {
    setState(() => _isLoadingLogs = true);
    try {
      final gateway = ref.read(nativeSmsGatewayProvider);
      final raw = await gateway.getSmsAttemptsLog();
      final list =
          (jsonDecode(raw) as List<dynamic>?)
              ?.map((e) => Map<String, dynamic>.from(e as Map))
              .toList() ??
          [];
      if (mounted) {
        setState(() {
          _logs = list.reversed.toList();
          _isLoadingLogs = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _logs = [];
          _isLoadingLogs = false;
        });
      }
    }
  }

  Future<void> _reconcileAll() async {
    setState(() => _isReconciling = true);
    HapticFeedback.lightImpact();
    try {
      final smsService = ref.read(birthdaySmsServiceProvider);
      final people = await ref.read(peopleRepositoryProvider).getPeople();
      final birthdaysRepo = ref.read(birthdaysRepositoryProvider);
      final draftsRepo = ref.read(draftsRepositoryProvider);

      int scheduledCount = 0;
      for (final person in people) {
        final birthday = await birthdaysRepo.getBirthdayForPerson(person.id);
        final draft = birthday?.draftId != null
            ? await draftsRepo.getDraft(birthday!.draftId!)
            : null;
        final res = await smsService.reconcileRecipientSms(
          person: person,
          birthday: birthday,
          draft: draft,
        );
        if (res) scheduledCount++;
      }

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              scheduledCount > 0
                  ? 'Reconciled: $scheduledCount automated SMS job(s) scheduled.'
                  : 'Reconciled: No pending approved SMS jobs.',
            ),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Failed to reconcile automated SMS jobs.'),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isReconciling = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final settings = ref.watch(automaticSmsSettingsProvider);
    final theme = Theme.of(context);

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  Icons.sms_outlined,
                  color: settings.isSupported
                      ? theme.colorScheme.primary
                      : context.colors.textSecondary,
                ),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: Text(
                    'Automatic SMS at Midnight (00:00)',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              'Sends pre-approved birthday wishes at 00:00 in the recipient\'s local time using native background alarms.',
              style: theme.textTheme.bodySmall?.copyWith(
                color: context.colors.textSecondary,
              ),
            ),
            const SizedBox(height: AppSpacing.md),
            if (!settings.isSupported) ...[
              Container(
                padding: const EdgeInsets.all(AppSpacing.sm),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest.withValues(
                    alpha: 0.5,
                  ),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.info_outline,
                      size: 20,
                      color: context.colors.textSecondary,
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    Expanded(
                      child: Text(
                        'This build is the Play Store distribution, which does not declare direct SMS sending. To enable automatic midnight SMS, install the personal sideload APK.',
                        style: theme.textTheme.bodySmall,
                      ),
                    ),
                  ],
                ),
              ),
            ] else ...[
              SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                title: const Text('Enable Automatic SMS'),
                subtitle: Text(
                  settings.hasSmsPermission
                      ? 'SMS permission granted. Only sends messages that you explicitly review and approve.'
                      : 'Requires SEND_SMS permission on your device.',
                ),
                value: settings.enabled,
                onChanged: (val) async {
                  HapticFeedback.lightImpact();
                  await ref
                      .read(automaticSmsSettingsProvider.notifier)
                      .setEnabled(val);
                },
              ),
              if (settings.enabled && !settings.hasSmsPermission) ...[
                const SizedBox(height: AppSpacing.xs),
                FilledButton.tonalIcon(
                  onPressed: () async {
                    HapticFeedback.lightImpact();
                    await ref
                        .read(automaticSmsSettingsProvider.notifier)
                        .refreshPermissions();
                  },
                  icon: const Icon(Icons.security),
                  label: const Text('Grant SMS Permission'),
                ),
              ],
              if (settings.enabled) ...[
                const Divider(height: 24),
                Row(
                  children: [
                    OutlinedButton.icon(
                      onPressed: _isReconciling ? null : _reconcileAll,
                      icon: _isReconciling
                          ? const SizedBox(
                              width: 14,
                              height: 14,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.sync, size: 18),
                      label: const Text('Reconcile All Alarms'),
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    TextButton.icon(
                      onPressed: _isLoadingLogs ? null : _loadLogs,
                      icon: const Icon(Icons.receipt_long, size: 18),
                      label: const Text('Delivery Logs'),
                    ),
                  ],
                ),
                if (_logs != null) ...[
                  const SizedBox(height: AppSpacing.sm),
                  Text(
                    'Recent Delivery Attempts (${_logs!.length})',
                    style: theme.textTheme.labelMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: AppSpacing.xs),
                  if (_logs!.isEmpty)
                    Text(
                      'No SMS execution attempts recorded yet.',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: context.colors.textSecondary,
                      ),
                    )
                  else
                    Container(
                      constraints: const BoxConstraints(maxHeight: 200),
                      child: ListView.builder(
                        shrinkWrap: true,
                        itemCount: _logs!.length,
                        itemBuilder: (context, idx) {
                          final log = _logs![idx];
                          final status = log['status'] as String? ?? 'unknown';
                          final phone = log['phoneNumber'] as String? ?? '';
                          final ts = log['timestampMs'] as num? ?? 0;
                          final dateStr = ts > 0
                              ? DateTime.fromMillisecondsSinceEpoch(
                                  ts.toInt(),
                                ).toLocal().toString().split('.')[0]
                              : '';
                          return ListTile(
                            dense: true,
                            contentPadding: EdgeInsets.zero,
                            leading: Icon(
                              status == 'delivered'
                                  ? Icons.done_all
                                  : (status == 'submitted' ||
                                        status == 'acceptedByCarrier')
                                  ? Icons.check
                                  : Icons.error_outline,
                              color: status == 'delivered'
                                  ? Colors.green
                                  : (status == 'submitted' ||
                                        status == 'acceptedByCarrier')
                                  ? Colors.blue
                                  : Colors.red,
                              size: 18,
                            ),
                            title: Text('$phone • $status'),
                            subtitle: Text(dateStr),
                          );
                        },
                      ),
                    ),
                ],
              ],
            ],
          ],
        ),
      ),
    );
  }
}
