/// Authoritative Settings screen for Account, Reminders, AI Credentials,
/// Subscription Entitlement, and Help & Guide (SSOT §5, §11, §17, §20).
library;

import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/auth/application/auth_controller.dart';
import 'package:ai_birthday/features/auth/domain/auth_state.dart';
import 'package:ai_birthday/features/auth/presentation/auth_bottom_sheet.dart';
import 'package:ai_birthday/features/jobs/application/cloud_job_handler.dart';
import 'package:ai_birthday/features/jobs/domain/job.dart';
import 'package:ai_birthday/features/reminders/application/reminder_providers.dart';
import 'package:ai_birthday/features/subscription/application/subscription_service.dart';
import 'package:ai_birthday/features/reminders/application/reminder_settings_controller.dart';
import 'package:ai_birthday/features/reminders/domain/quiet_hours.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_kind.dart';
import 'package:ai_birthday/features/settings/presentation/widgets/cloud_backup_card.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';
import 'package:ai_birthday/ui/design_system/app_tokens.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  static const String _geminiApiKeyUrl =
      'https://aistudio.google.com/app/apikey';
  static const String _geminiBillingUrl = 'https://ai.google.dev/pricing';

  final TextEditingController _apiKeyController = TextEditingController();
  bool _hasKey = false;
  bool _obscureKey = true;
  bool _isTestingKey = false;
  GeminiConnectionResult? _connectionResult;
  DateTime? _lastVerifiedAt;
  bool _isPurchasing = false;
  // Cloud backup/restore now run as durable background jobs: `_isSyncing`
  // follows the account's active cloud job (truthful even after re-entry),
  // and failures stay visible inline with a retry affordance.
  bool _isSyncing = false;
  String? _cloudError;
  DateTime? _lastSyncTime;
  NanoState _nanoState = NanoState.unavailable;

  // Durable job bookkeeping for the signed-in account's cloud_sync job.
  StreamSubscription<JobRecord?>? _cloudJobSub;
  String? _lastHandledCloudJobId;
  JobStatus? _lastHandledCloudJobStatus;
  // Set when THIS screen visit enqueues a job: gates the success/failure
  // snackbar and picks backup- vs restore-specific copy.
  String? _lastEnqueuedCloudJobId;
  String? _pendingCloudOperation;

  // Bumped after a notification re-enable attempt so the revoked-state
  // FutureBuilder below re-queries OS permission (audit Scenario T).
  int _notificationPermissionRevision = 0;

  @override
  void initState() {
    super.initState();
    _checkStoredKey();
    _checkNanoState();
    _loadLastSyncTime();
    _initCloudJobWatch();
  }

  @override
  void dispose() {
    _cloudJobSub?.cancel();
    _apiKeyController.dispose();
    super.dispose();
  }

  /// Follows the signed-in account's durable cloud job so backup/restore
  /// status is truthful even when the job outlives this screen visit. Seat
  /// the state with a one-shot read, then ride the worker's in-process
  /// transition stream (a plain broadcast, so no drift query timers).
  Future<void> _initCloudJobWatch() async {
    final auth = ref.read(authControllerProvider).valueOrNull;
    final identity = auth?.identity;
    final accountId = identity?.firebaseUid ?? identity?.googleSubject;
    if (accountId == null || accountId.isEmpty) return;
    _cloudJobSub?.cancel();
    final latest = await ref
        .read(jobsRepositoryProvider)
        .latestJob(JobTypes.cloudSync, accountId);
    if (latest != null && mounted) _onCloudJobEvent(latest);
    _cloudJobSub = ref
        .read(jobWorkerProvider)
        .events
        .where(
          (job) => job.type == JobTypes.cloudSync && job.subjectId == accountId,
        )
        .listen(_onCloudJobEvent);
  }

  void _onCloudJobEvent(JobRecord? job) {
    if (job == null || !mounted) return;
    if (job.id == _lastHandledCloudJobId &&
        job.status == _lastHandledCloudJobStatus) {
      return;
    }
    _lastHandledCloudJobId = job.id;
    _lastHandledCloudJobStatus = job.status;

    final ours = job.id == _lastEnqueuedCloudJobId;
    final operation = _pendingCloudOperation ?? CloudJobPayload.backup;

    switch (job.status) {
      case JobStatus.queued || JobStatus.running || JobStatus.retrying:
        setState(() {
          _isSyncing = true;
          _cloudError = null;
        });
      case JobStatus.succeeded:
        setState(() {
          _isSyncing = false;
          _cloudError = null;
        });
        _loadLastSyncTime(); // Refresh the LAST SUCCESSFUL chip.
        if (ours) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                operation == CloudJobPayload.restore
                    ? 'Cloud restore complete!'
                    : 'Cloud backup complete.',
              ),
            ),
          );
        }
      case JobStatus.failed:
        final message = job.errorMessage ?? 'Cloud operation failed.';
        setState(() {
          _isSyncing = false;
          _cloudError = message;
        });
        if (ours) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                operation == CloudJobPayload.restore
                    ? 'Restore failed: $message'
                    : 'Backup failed: $message',
              ),
            ),
          );
        }
      case JobStatus.canceled:
        setState(() {
          _isSyncing = false;
          _cloudError = null;
        });
    }
  }

  Future<void> _checkStoredKey() async {
    try {
      final storage = ref.read(credentialStorageProvider);
      final key = await storage.getGeminiApiKey();
      if (!mounted) return;
      if (key != null && key.isNotEmpty) {
        // Durable validation (audit 02 P1-2): never claim readiness from key
        // presence alone; badge derives from the last recorded verification.
        final verifiedAt = await storage.geminiKeyVerifiedAt();
        if (!mounted) return;
        setState(() {
          _hasKey = true;
          _apiKeyController.text = key;
          _lastVerifiedAt = verifiedAt;
        });
      }
    } catch (_) {
      // Gracefully ignored in tests and unsupported environments
    }
  }

  Future<void> _checkNanoState() async {
    try {
      final state = await ref.read(geminiNanoPlatformProvider).currentState();
      if (mounted) {
        setState(() => _nanoState = state);
      }
    } catch (_) {}
  }

  Future<void> _loadLastSyncTime() async {
    try {
      final auth = ref.read(authControllerProvider).valueOrNull;
      final identity = auth?.identity;
      final accountId = identity?.firebaseUid ?? identity?.googleSubject;
      if (accountId == null || accountId.isEmpty) return;

      final syncService = ref.read(cloudSyncServiceProvider);
      final time = await syncService.getLastSyncTime(accountId);
      if (mounted) {
        setState(() => _lastSyncTime = time);
      }
    } catch (_) {}
  }

  Future<void> _handleCloudSync() async {
    HapticFeedback.lightImpact();
    final auth = ref.read(authControllerProvider).valueOrNull;
    if (auth == null || !auth.isSignedIn || auth.identity == null) {
      AuthBottomSheet.show(context);
      return;
    }
    final accountId =
        auth.identity!.firebaseUid ?? auth.identity!.googleSubject;
    if (accountId.isEmpty) {
      AuthBottomSheet.show(context);
      return;
    }

    // Durable backup job: the tap only enqueues; the worker uploads even if
    // the user leaves Settings, and retries transient failures once.
    final job = await ref
        .read(jobWorkerProvider)
        .enqueue(
          type: JobTypes.cloudSync,
          subjectId: accountId,
          payload: jsonEncode(
            const CloudJobPayload(operation: CloudJobPayload.backup).toJson(),
          ),
          maxAttempts: 2,
        );
    _initCloudJobWatch();
    if (!mounted) return;
    setState(() {
      _isSyncing = true;
      _cloudError = null;
      _lastEnqueuedCloudJobId = job.id;
      _pendingCloudOperation = CloudJobPayload.backup;
    });
  }

  Future<void> _handleCloudRestore() async {
    HapticFeedback.lightImpact();
    final auth = ref.read(authControllerProvider).valueOrNull;
    if (auth == null || !auth.isSignedIn || auth.identity == null) {
      AuthBottomSheet.show(context);
      return;
    }
    final accountId =
        auth.identity!.firebaseUid ?? auth.identity!.googleSubject;
    if (accountId.isEmpty) {
      AuthBottomSheet.show(context);
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Restore from Cloud Backup?'),
        content: const Text(
          'This will merge your cloud data into this device. New records will be restored and newer matching records may update local data. Existing reminder preferences on this device are preserved.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Restore'),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    // Durable restore job. Restore is NOT auto-retried (maxAttempts 1): a
    // destructive-looking merge should only re-run when the user asks.
    final job = await ref
        .read(jobWorkerProvider)
        .enqueue(
          type: JobTypes.cloudSync,
          subjectId: accountId,
          payload: jsonEncode(
            const CloudJobPayload(operation: CloudJobPayload.restore).toJson(),
          ),
          maxAttempts: 1,
        );
    _initCloudJobWatch();
    if (!mounted) return;
    setState(() {
      _isSyncing = true;
      _cloudError = null;
      _lastEnqueuedCloudJobId = job.id;
      _pendingCloudOperation = CloudJobPayload.restore;
    });
  }

  Future<void> _launchExternalUrl(String url) async {
    final uri = Uri.parse(url);
    try {
      final launched = await launchUrl(
        uri,
        mode: LaunchMode.externalApplication,
      );
      if (!launched && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Could not open that link. Please try again.'),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Could not open that link. Please try again.'),
          ),
        );
      }
    }
  }

  Future<void> _manageExactAlarmAccess() async {
    HapticFeedback.lightImpact();
    final gateway = ref.read(notificationSchedulerGatewayProvider);
    final granted = await gateway.hasExactAlarmPermission();
    if (granted && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Precise reminder access is already enabled.'),
        ),
      );
      return;
    }
    final opened = await gateway.requestExactAlarmPermission();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          opened
              ? 'Precise reminder access is enabled.'
              : 'Allow precise reminders in Android settings, then return to AI-Birthday.',
        ),
      ),
    );
    await _checkReminderAccess();
  }

  Future<void> _checkReminderAccess() async {
    // Refreshing this screen after the system-settings handoff prevents stale permission UI.
    if (!mounted) return;
    setState(() {});
  }

  /// Recovery for reminders that were enabled but are now blocked at the OS
  /// level (audit Scenario T): re-request access and reschedule.
  Future<void> _reenableNotifications() async {
    HapticFeedback.lightImpact();
    final gateway = ref.read(notificationSchedulerGatewayProvider);
    final granted = await gateway.requestPermission();
    if (!mounted) return;
    setState(() => _notificationPermissionRevision++);
    if (granted) {
      // Re-run the schedule now that delivery can work again.
      ref.read(reminderSettingsProvider.notifier).setEnabled(true);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Notifications re-enabled. Reminders are being rescheduled.',
          ),
        ),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Notifications are still blocked in system settings.'),
        ),
      );
    }
  }

  Future<void> _testConnection() async {
    HapticFeedback.lightImpact();
    final text = _apiKeyController.text.trim();
    if (text.isEmpty) {
      setState(() {
        _connectionResult = const GeminiConnectionResult.invalidKey(
          'Please enter an API key to test.',
        );
      });
      return;
    }

    setState(() {
      _isTestingKey = true;
      _connectionResult = null;
    });

    try {
      final provider = ref.read(userGeminiApiProvider);
      final result = await provider.testApiKey(text);
      if (!mounted) return;

      if (result.status == GeminiConnectionStatus.connected) {
        await ref.read(credentialStorageProvider).saveGeminiApiKey(text);
        // Persist the successful verification so the badge stays honest
        // across restarts (audit 02 P1-2).
        await ref
            .read(credentialStorageProvider)
            .recordGeminiKeyVerifiedAt(DateTime.now());
        setState(() {
          _hasKey = true;
          _connectionResult = result;
          _lastVerifiedAt = DateTime.now();
          _isTestingKey = false;
        });
      } else {
        setState(() {
          _connectionResult = result;
          _isTestingKey = false;
        });
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _connectionResult = GeminiConnectionResult.error(
          'Unable to verify API key. Please check your connection and try again.',
        );
        _isTestingKey = false;
      });
    }
  }

  void _showGeminiSetupGuide(BuildContext context) {
    HapticFeedback.lightImpact();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => _GeminiSetupGuideSheet(
        onOpenAiStudio: () => _launchExternalUrl(_geminiApiKeyUrl),
        onOpenBilling: () => _launchExternalUrl(_geminiBillingUrl),
      ),
    );
  }

  Future<void> _saveKey() async {
    HapticFeedback.lightImpact();
    final text = _apiKeyController.text.trim();
    if (text.isEmpty) {
      // The empty-field Save Key button previously wiped the stored key
      // silently — a redundant, unlabeled destructive path next to the
      // explicit 'Remove Key' button. Save now refuses to delete; removal is
      // the labelled 'Remove Key' action only.
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              _hasKey
                  ? 'Enter a key to save, or use Remove Key to delete the stored key.'
                  : 'Enter a key to save first.',
            ),
          ),
        );
      }
      return;
    }
    final storage = ref.read(credentialStorageProvider);
    try {
      await storage.saveGeminiApiKey(text);
      // Raw save is UNVERIFIED: drop any prior verification so the badge
      // can never claim readiness for a key that was not tested
      // (audit 02 P2-1).
      await storage.clearGeminiKeyVerifiedAt();
      setState(() {
        _hasKey = true;
        _lastVerifiedAt = null;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Gemini API key saved in secure device storage.'),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Unable to save API key to secure storage. Please try again.',
            ),
          ),
        );
      }
    }
  }

  Future<void> _removeKey() async {
    HapticFeedback.lightImpact();
    final storage = ref.read(credentialStorageProvider);
    try {
      await storage.deleteGeminiApiKey();
      await storage.clearGeminiKeyVerifiedAt();
      setState(() {
        _hasKey = false;
        _connectionResult = null;
        _lastVerifiedAt = null;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Gemini API key removed.')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Unable to remove API key from secure storage. Please try again.',
            ),
          ),
        );
      }
    }
  }

  Future<void> _editQuietHours(QuietHours quiet) async {
    final start = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(
        hour: quiet.start.inHours % 24,
        minute: quiet.start.inMinutes % 60,
      ),
    );
    if (start == null || !mounted) return;
    final end = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(
        hour: quiet.end.inHours % 24,
        minute: quiet.end.inMinutes % 60,
      ),
    );
    if (end == null || !mounted) return;
    ref
        .read(reminderSettingsProvider.notifier)
        .setQuietHours(
          QuietHours(
            start: Duration(hours: start.hour, minutes: start.minute),
            end: Duration(hours: end.hour, minutes: end.minute),
          ),
        );
  }

  String _formatTime(Duration time) {
    return MaterialLocalizations.of(context).formatTimeOfDay(
      TimeOfDay(hour: time.inHours % 24, minute: time.inMinutes % 60),
    );
  }

  Future<void> _handlePurchasePro() async {
    HapticFeedback.lightImpact();
    setState(() => _isPurchasing = true);
    final success = await ref
        .read(subscriptionNotifierProvider.notifier)
        .purchaseProMonthly();
    if (!mounted) return;
    setState(() => _isPurchasing = false);

    final reason = ref
        .read(subscriptionNotifierProvider.notifier)
        .lastPurchaseFailure;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          success
              ? 'Purchase flow opened. Google Play will verify the subscription before AI is unlocked.'
              : _purchaseFailureMessage(reason),
        ),
      ),
    );
  }

  String _purchaseFailureMessage(PurchaseFailure reason) => switch (reason) {
    PurchaseFailure.signInRequired =>
      'Sign in with Google to subscribe to Pro.',
    PurchaseFailure.storeUnavailable =>
      'Google Play Billing is not available on this device.',
    PurchaseFailure.productUnavailable =>
      'Pro is not available on this install. Install AI-Birthday from Google Play to subscribe.',
    PurchaseFailure.verificationUnavailable =>
      'Pro purchases are temporarily unavailable. You have not been charged. Try again later.',
    _ => 'Purchase could not be started. Try again.',
  };

  Future<void> _handleRestorePurchases() async {
    HapticFeedback.lightImpact();
    final restored = await ref
        .read(subscriptionNotifierProvider.notifier)
        .restorePurchases();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          restored
              ? 'Verified Pro subscription restored.'
              : 'No verified active subscription was found.',
        ),
      ),
    );
  }

  /// On-device section is only shown when Gemini Nano can run or be set up here.
  bool get _nanoSupported =>
      _nanoState == NanoState.available ||
      _nanoState == NanoState.downloadable ||
      _nanoState == NanoState.downloading;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final reminderSettings = ref.watch(reminderSettingsProvider);
    final entitlement = ref.watch(entitlementProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 88),
        children: [
          // Section 1: Account
          AppSectionHeader(title: 'Account', isAccent: true),
          Card(child: const _AuthTile()),
          const SizedBox(height: 20),

          // Section 5: Reminders & Quiet Hours (SSOT §17)
          AppSectionHeader(title: 'Reminders', isAccent: true),
          Card(
            child: Column(
              children: [
                SwitchListTile(
                  secondary: const Icon(Icons.notifications_outlined),
                  title: const Text('Birthday reminders'),
                  subtitle: const Text('Reminders before birthdays'),
                  value: reminderSettings.enabled,
                  onChanged: (on) async {
                    HapticFeedback.lightImpact();
                    if (on) {
                      final granted = await ref
                          .read(notificationSchedulerGatewayProvider)
                          .requestPermission();
                      if (!granted) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text(
                                'Notification permission was not granted. Please enable notifications in device settings.',
                              ),
                            ),
                          );
                        }
                        ref
                            .read(reminderSettingsProvider.notifier)
                            .setEnabled(false);
                        return;
                      }
                    }
                    ref.read(reminderSettingsProvider.notifier).setEnabled(on);
                  },
                ),
                if (reminderSettings.syncError != null) ...[
                  Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Theme.of(
                        context,
                      ).colorScheme.errorContainer.withValues(alpha: 0.55),
                      borderRadius: AppSpacing.roundedMd,
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          Icons.warning_amber_rounded,
                          color: Theme.of(context).colorScheme.error,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            reminderSettings.syncError!,
                            style: TextStyle(
                              color: Theme.of(
                                context,
                              ).colorScheme.onErrorContainer,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
                if (reminderSettings.enabled)
                  FutureBuilder<bool>(
                    key: ValueKey(
                      'notification-permission-$_notificationPermissionRevision',
                    ),
                    future: ref
                        .read(notificationSchedulerGatewayProvider)
                        .hasPermission(),
                    builder: (context, snapshot) {
                      if (snapshot.data != false) {
                        return const SizedBox.shrink();
                      }
                      return Container(
                        margin: const EdgeInsets.only(bottom: 8),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Theme.of(
                            context,
                          ).colorScheme.errorContainer.withValues(alpha: 0.55),
                          borderRadius: AppSpacing.roundedMd,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Icon(
                                  Icons.notifications_off_outlined,
                                  color: Theme.of(context).colorScheme.error,
                                ),
                                const SizedBox(width: 10),
                                Expanded(
                                  child: Text(
                                    'Notifications are blocked in system settings, so birthday reminders cannot be delivered right now.',
                                    style: TextStyle(
                                      color: Theme.of(
                                        context,
                                      ).colorScheme.onErrorContainer,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            Align(
                              alignment: Alignment.centerRight,
                              child: TextButton.icon(
                                onPressed: _reenableNotifications,
                                icon: const Icon(
                                  Icons.notifications_active_outlined,
                                  size: 16,
                                ),
                                label: const Text('Re-enable notifications'),
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                FutureBuilder<bool>(
                  future: ref
                      .read(notificationSchedulerGatewayProvider)
                      .hasExactAlarmPermission(),
                  builder: (context, snapshot) {
                    final exactReady = snapshot.data != false;
                    // Only surface the row when reminders are on and Android is
                    // blocking precise alarms; a healthy state needs no row.
                    if (exactReady || !reminderSettings.enabled) {
                      return const SizedBox.shrink();
                    }
                    return ListTile(
                      leading: Icon(
                        exactReady
                            ? Icons.schedule_outlined
                            : Icons.warning_amber_rounded,
                        color: exactReady
                            ? context.colors.success
                            : Theme.of(context).colorScheme.error,
                      ),
                      title: const Text('Precise reminder access'),
                      subtitle: Text(
                        exactReady
                            ? 'Android can schedule reminders at their selected time.'
                            : 'Android is blocking precise reminders. Enable access so scheduled birthdays are not left unscheduled.',
                      ),
                      trailing: exactReady
                          ? const Icon(Icons.check_circle_outline)
                          : TextButton(
                              onPressed: _manageExactAlarmAccess,
                              child: const Text('Fix'),
                            ),
                    );
                  },
                ),
                if (reminderSettings.enabled) ...[
                  const Divider(height: 1),
                  for (final kind in ReminderKind.values)
                    SwitchListTile(
                      title: Text(kind.title),
                      subtitle: Text(kind.when),
                      value: reminderSettings.kinds.contains(kind),
                      onChanged: (on) {
                        HapticFeedback.lightImpact();
                        ref
                            .read(reminderSettingsProvider.notifier)
                            .setKind(kind, on);
                      },
                    ),
                  const Divider(height: 1),
                  ListTile(
                    leading: const Icon(Icons.bedtime_outlined),
                    title: const Text('Quiet hours'),
                    subtitle: Text(
                      'No delivery between '
                      '${_formatTime(reminderSettings.quietHours.start)} and '
                      '${_formatTime(reminderSettings.quietHours.end)}',
                    ),
                    onTap: () => _editQuietHours(reminderSettings.quietHours),
                  ),
                  const Divider(height: 1),
                  ListTile(
                    leading: const Icon(Icons.notifications_active_outlined),
                    title: const Text('Send test notification'),
                    subtitle: const Text(
                      'Trigger immediate alert on this device',
                    ),
                    onTap: () async {
                      HapticFeedback.lightImpact();
                      final sent = await ref
                          .read(notificationSchedulerGatewayProvider)
                          .sendTestNotification(
                            title: '🎉 Birthday Reminder Test',
                            body:
                                'Notifications are working properly on your device!',
                          );
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(
                              sent
                                  ? 'Test notification sent to this device.'
                                  : 'The test notification could not be delivered. Check notification access and try again.',
                            ),
                            duration: const Duration(seconds: 3),
                          ),
                        );
                      }
                    },
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 20),

          // Section 1.5: Cloud Backup (SSOT §13)
          AppSectionHeader(title: 'Cloud Backup', isAccent: true),
          CloudBackupCard(
            lastSyncTime: _lastSyncTime,
            cloudError: _cloudError,
            isSyncing: _isSyncing,
            onBackup: _handleCloudSync,
            onRestore: _handleCloudRestore,
          ),
          const SizedBox(height: 20),

          // Section 2: Subscription & Entitlement (SSOT §11)
          AppSectionHeader(title: 'Subscription & Entitlement', isAccent: true),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(
                        entitlement.canUseAi
                            ? Icons.verified
                            : Icons.lock_outline,
                        color: entitlement.canUseAi
                            ? context.colors.warning
                            : context.colors.textSecondary,
                      ),
                      const SizedBox(width: 10),
                      Text(
                        entitlement.status.displayName,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    entitlement.canUseAi
                        ? 'Full AI draft generation, rewrite variations, and personalized message studio are active.'
                        : 'Application AI features require an active subscription entitlement. Birthday tracking and manual drafting remain free forever.',
                    style: TextStyle(
                      fontSize: 13,
                      color: context.colors.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 16),
                  if (!entitlement.canUseAi) ...[
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: _isPurchasing ? null : _handlePurchasePro,
                        icon: _isPurchasing
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Icon(Icons.star_outline),
                        label: Text(
                          _isPurchasing
                              ? 'Verifying Purchase...'
                              : 'Upgrade to Pro (\$2.99/mo)',
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                  ],
                  if (!entitlement.canUseAi &&
                      ref
                          .read(subscriptionNotifierProvider.notifier)
                          .hasPendingVerification) ...[
                    Row(
                      children: [
                        Icon(
                          Icons.error_outline,
                          size: 16,
                          color: Theme.of(context).colorScheme.error,
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            'Purchase verification failed. Tap “Restore Purchases” to try again.',
                            style: TextStyle(
                              fontSize: 12,
                              color: context.colors.textSecondary,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                  ],
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      TextButton(
                        onPressed: _handleRestorePurchases,
                        child: const Text('Restore Purchases'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Section 3: AI Provider & Personal Gemini API Key (SSOT §5, §20)
          AppSectionHeader(title: 'AI provider', isAccent: true),
          Card(
            clipBehavior: Clip.antiAlias,
            child: ExpansionTile(
              initiallyExpanded: !_hasKey,
              leading: Icon(
                Icons.key,
                color: Theme.of(context).colorScheme.primary,
              ),
              title: const Text('Gemini API key'),
              subtitle: Text(
                _hasKey
                    ? 'Saved on this device'
                    : 'Add a key to unlock personalized AI drafts',
              ),
              childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              expandedCrossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.key,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                    const SizedBox(width: 8),
                    const Text(
                      'Personal Gemini API Key',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                    const Spacer(),
                    _buildKeyStatusBadge(),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  'Don\'t have an API key? Get one from Google AI Studio to unlock personalized AI message drafting.',
                  style: TextStyle(
                    fontSize: 13,
                    color: context.colors.textSecondary,
                  ),
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    FilledButton.tonalIcon(
                      onPressed: () => _launchExternalUrl(_geminiApiKeyUrl),
                      icon: const Icon(Icons.open_in_new, size: 16),
                      label: const Text('Get Gemini API key ↗'),
                    ),
                    OutlinedButton.icon(
                      onPressed: () => _showGeminiSetupGuide(context),
                      icon: const Icon(Icons.help_outline, size: 16),
                      label: const Text('How to connect'),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _apiKeyController,
                  obscureText: _obscureKey,
                  onChanged: (_) {
                    if (_connectionResult != null) {
                      setState(() => _connectionResult = null);
                    }
                  },
                  decoration: InputDecoration(
                    labelText: 'Paste your Gemini API key',
                    hintText: 'AIzaSy...',
                    border: const OutlineInputBorder(),
                    suffixIcon: IconButton(
                      icon: Icon(
                        _obscureKey ? Icons.visibility : Icons.visibility_off,
                      ),
                      tooltip: _obscureKey ? 'Show API key' : 'Hide API key',
                      onPressed: () {
                        HapticFeedback.lightImpact();
                        setState(() => _obscureKey = !_obscureKey);
                      },
                    ),
                  ),
                ),
                if (_connectionResult != null) ...[
                  const SizedBox(height: 12),
                  _buildConnectionResultBanner(_connectionResult!),
                ],
                const SizedBox(height: 14),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  alignment: WrapAlignment.end,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    if (_hasKey)
                      TextButton(
                        onPressed: () {
                          _apiKeyController.clear();
                          _removeKey();
                        },
                        child: Text(
                          'Remove Key',
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.error,
                          ),
                        ),
                      ),
                    OutlinedButton(
                      onPressed: _saveKey,
                      child: const Text('Save Key'),
                    ),
                    FilledButton.icon(
                      onPressed: _isTestingKey ? null : _testConnection,
                      icon: _isTestingKey
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Icon(Icons.bolt, size: 18),
                      label: Text(
                        _isTestingKey ? 'Testing...' : 'Test Connection',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                const Divider(height: 16),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('🔒 ', style: TextStyle(fontSize: 13)),
                    Expanded(
                      child: Text(
                        'Stored securely on this device in hardware-backed encrypted storage. Sent directly to Google Gemini API only when generating messages, and never to AI-Birthday servers. Never included in sync or application logs.',
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.grey[600],
                          height: 1.3,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Semantics(
                  link: true,
                  child: InkWell(
                    onTap: () => _launchExternalUrl(_geminiBillingUrl),
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(minHeight: 48),
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: Row(
                          children: [
                            Icon(
                              Icons.open_in_new,
                              size: 14,
                              color: theme.colorScheme.primary,
                            ),
                            const SizedBox(width: 6),
                            Text(
                              'Gemini API billing, quotas & free limits ↗',
                              style: TextStyle(
                                fontSize: 12,
                                color: theme.colorScheme.primary,
                                decoration: TextDecoration.underline,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),

          // Section 4: On-Device AI / Gemini Nano (SSOT §5, §25)
          if (_nanoSupported) ...[
            AppSectionHeader(title: 'On-Device Intelligence', isAccent: true),
            Card(
              child: Column(
                children: [
                  ListTile(
                    leading: Icon(Icons.memory, color: context.colors.success),
                    title: const Text('Gemini Nano (AICore)'),
                    subtitle: Text(switch (_nanoState) {
                      NanoState.available =>
                        'Ready on device for offline generation.',
                      NanoState.downloadable =>
                        'Model available for download on this device.',
                      NanoState.downloading => 'Downloading on-device model...',
                      _ =>
                        'Not available on this device hardware (requires Google AICore on Android 14+).',
                    }),
                    trailing: Chip(
                      label: Text(
                        _nanoState == NanoState.available
                            ? 'AVAILABLE'
                            : 'NOT AVAILABLE',
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          color: _nanoState == NanoState.available
                              ? context.colors.success
                              : context.colors.textSecondary,
                        ),
                      ),
                      backgroundColor: _nanoState == NanoState.available
                          ? context.colors.success.withValues(alpha: 0.12)
                          : Colors.grey.withValues(alpha: 0.12),
                      visualDensity: VisualDensity.compact,
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
                    child: Text(
                      'AI provider: your active AI-Birthday Pro access is required. When available, AI-Birthday uses your saved Gemini key; otherwise supported Android devices can use Gemini Nano on-device.',
                      style: TextStyle(
                        fontSize: 12,
                        color: context.colors.textSecondary,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
          ],

          // Section 6: Help & Guide
          AppSectionHeader(title: 'Help & Guide', isAccent: true),
          Card(
            child: ListTile(
              leading: const Icon(Icons.explore_outlined),
              title: const Text('Replay App Onboarding'),
              subtitle: const Text('View the 5-step loop and feature guide'),
              trailing: const Icon(Icons.arrow_forward_ios, size: 14),
              onTap: () {
                HapticFeedback.lightImpact();
                context.push('/onboarding');
              },
            ),
          ),
          const SizedBox(height: 24),

          Center(
            child: Text(
              'AI-Birthday • v1.0.0',
              style: theme.textTheme.labelMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          const SizedBox(height: 16),
        ],
      ),
    );
  }

  Widget _buildKeyStatusBadge() {
    final colors = context.colors;
    if (_connectionResult?.status == GeminiConnectionStatus.connected) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: AppTone.success.fill(colors),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.check_circle,
              size: 12,
              color: AppTone.success.label(colors),
            ),
            const SizedBox(width: 4),
            Text(
              'Connected',
              style: TextStyle(
                color: AppTone.success.label(colors),
                fontSize: 11,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
      );
    } else if (_connectionResult?.status == GeminiConnectionStatus.invalidKey) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: AppTone.danger.fill(colors),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          'Invalid Key',
          style: TextStyle(
            color: AppTone.danger.label(colors),
            fontSize: 11,
            fontWeight: FontWeight.bold,
          ),
        ),
      );
    } else if (_connectionResult?.status ==
        GeminiConnectionStatus.quotaExceeded) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: AppTone.warning.fill(colors),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          'Quota Exceeded',
          style: TextStyle(
            color: AppTone.warning.label(colors),
            fontSize: 11,
            fontWeight: FontWeight.bold,
          ),
        ),
      );
    } else if (_hasKey && _lastVerifiedAt != null) {
      // Durable state: key was verified by a live ping at some point, but not
      // this session. Label the timestamp rather than claiming live
      // "Connected" (audit 02 P1-2).
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: AppTone.warning.fill(colors),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.verified_outlined,
              size: 12,
              color: AppTone.warning.label(colors),
            ),
            const SizedBox(width: 4),
            Text(
              'Verified · '
              '${MaterialLocalizations.of(context).formatMediumDate(_lastVerifiedAt!.toLocal())}',
              style: TextStyle(
                color: AppTone.warning.label(colors),
                fontSize: 11,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
      );
    } else if (_hasKey) {
      // A stored key that was never verified must not look ready (audit 02
      // P2-1).
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: AppTone.neutral.fill(colors),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          'Unverified',
          style: TextStyle(
            color: AppTone.neutral.label(colors),
            fontSize: 11,
            fontWeight: FontWeight.bold,
          ),
        ),
      );
    } else {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: AppTone.neutral.fill(colors),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          'Not Configured',
          style: TextStyle(
            color: AppTone.neutral.label(colors),
            fontSize: 11,
            fontWeight: FontWeight.bold,
          ),
        ),
      );
    }
  }

  Widget _buildConnectionResultBanner(GeminiConnectionResult result) {
    final colors = context.colors;
    final (AppTone tone, IconData icon) = switch (result.status) {
      GeminiConnectionStatus.connected => (
        AppTone.success,
        Icons.check_circle_outline,
      ),
      GeminiConnectionStatus.invalidKey => (
        AppTone.danger,
        Icons.error_outline,
      ),
      GeminiConnectionStatus.quotaExceeded => (
        AppTone.warning,
        Icons.warning_amber_rounded,
      ),
      GeminiConnectionStatus.networkUnavailable => (
        AppTone.info,
        Icons.wifi_off_outlined,
      ),
      GeminiConnectionStatus.error => (AppTone.danger, Icons.error_outline),
    };

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: tone.fill(colors),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: tone.label(colors).withValues(alpha: 0.4)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 20, color: tone.label(colors)),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              result.message,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: tone.label(colors),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _AuthTile extends ConsumerWidget {
  const _AuthTile();

  Future<void> _confirmSignOut(BuildContext context, WidgetRef ref) async {
    HapticFeedback.lightImpact();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Sign out?'),
        content: const Text(
          'Your birthdays and drafts stay on this device. Signing out only '
          'disconnects your Google account and cloud backup.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Sign out'),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;
    ref.read(authControllerProvider.notifier).signOut();
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authControllerProvider);

    return auth.when(
      loading: () => const ListTile(
        leading: Icon(Icons.login),
        title: Text('Sign in with Google'),
        subtitle: Text('Checking\u2026'),
        enabled: false,
      ),
      error: (error, stackTrace) => const ListTile(
        leading: Icon(Icons.login),
        title: Text('Sign in with Google'),
        subtitle: Text('Account unavailable'),
        enabled: false,
      ),
      data: (state) => switch (state.status) {
        AuthStatus.unknown => const ListTile(
          leading: Icon(Icons.login),
          title: Text('Sign in with Google'),
          subtitle: Text('Account status unavailable'),
          enabled: false,
        ),
        AuthStatus.signedOut => ListTile(
          leading: Icon(
            Icons.login,
            color: Theme.of(context).colorScheme.primary,
          ),
          title: const Text('Sign in with Google'),
          subtitle: const Text('Sync, backups and delivery'),
          trailing: FilledButton.tonal(
            onPressed: () {
              HapticFeedback.lightImpact();
              AuthBottomSheet.show(context);
            },
            child: const Text('Sign in'),
          ),
          // Single entry point: the row and the button open the same sheet,
          // so the sign-in flow always explains itself before starting.
          onTap: () {
            HapticFeedback.lightImpact();
            AuthBottomSheet.show(context);
          },
        ),
        AuthStatus.signedIn => ListTile(
          leading: CircleAvatar(
            backgroundColor: context.colors.success.withValues(alpha: 0.15),
            foregroundColor: context.colors.success,
            backgroundImage: state.identity?.photoUrl != null
                ? NetworkImage(state.identity!.photoUrl!)
                : null,
            child: state.identity?.photoUrl == null
                ? Text(
                    (state.identity?.displayName.isNotEmpty == true
                            ? state.identity!.displayName[0]
                            : state.identity?.email.isNotEmpty == true
                            ? state.identity!.email[0]
                            : 'U')
                        .toUpperCase(),
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  )
                : null,
          ),
          title: Text(
            state.identity?.displayName.isNotEmpty == true
                ? state.identity!.displayName
                : 'Signed in',
            style: const TextStyle(fontWeight: FontWeight.bold),
          ),
          subtitle: Text(state.identity?.email ?? ''),
          trailing: OutlinedButton(
            onPressed: () => _confirmSignOut(context, ref),
            child: const Text('Sign out'),
          ),
        ),
        AuthStatus.unavailable => const ListTile(
          leading: Icon(Icons.login),
          title: Text('Sign in with Google'),
          subtitle: Text('Not configured for this build'),
          enabled: false,
        ),
      },
    );
  }
}

class _GeminiSetupGuideSheet extends StatelessWidget {
  const _GeminiSetupGuideSheet({
    required this.onOpenAiStudio,
    required this.onOpenBilling,
  });

  final VoidCallback onOpenAiStudio;
  final VoidCallback onOpenBilling;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return DraggableScrollableSheet(
      initialChildSize: 0.85,
      minChildSize: 0.5,
      maxChildSize: 0.95,
      expand: false,
      builder: (context, scrollController) {
        return SingleChildScrollView(
          controller: scrollController,
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey[400],
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: Theme.of(
                        context,
                      ).colorScheme.primary.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      Icons.auto_awesome,
                      color: Theme.of(context).colorScheme.primary,
                      size: 24,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'How to connect Gemini',
                          style: theme.textTheme.titleLarge?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Text(
                          'Step-by-step setup in Google AI Studio',
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: Colors.grey[600],
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Step 1
              _GuideStepTile(
                stepNumber: '1',
                title: 'Open Google AI Studio',
                description:
                    'Visit Google AI Studio in your browser and sign in with your Google Account.',
                action: Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: FilledButton.tonalIcon(
                    onPressed: onOpenAiStudio,
                    icon: const Icon(Icons.open_in_new, size: 16),
                    label: const Text('Open Google AI Studio ↗'),
                  ),
                ),
              ),
              const SizedBox(height: 18),

              // Step 2
              const _GuideStepTile(
                stepNumber: '2',
                title: 'Create an API Key',
                description:
                    'Select "Create API key" in Google AI Studio. Follow the project and access options Google shows for your account.',
              ),
              const SizedBox(height: 18),

              // Step 3
              const _GuideStepTile(
                stepNumber: '3',
                title: 'Copy your API Key',
                description:
                    'Copy the new key, then return here and paste it into the field below. Google may use different key formats as its key system changes.',
              ),
              const SizedBox(height: 18),

              // Step 4
              const _GuideStepTile(
                stepNumber: '4',
                title: 'Paste and Test in Settings',
                description:
                    'Return to AI-Birthday, paste the key into the Gemini API Key field, then tap "Test Connection" to verify access before saving. "Save Key" also works, but the key is stored unverified until you test it.',
              ),
              const SizedBox(height: 24),

              // Explanatory note container
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest.withValues(
                    alpha: 0.5,
                  ),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: theme.colorScheme.outlineVariant.withValues(
                      alpha: 0.5,
                    ),
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(
                          Icons.info_outline,
                          size: 18,
                          color: theme.colorScheme.primary,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Important Notes on Usage & Billing',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 14,
                            color: theme.colorScheme.primary,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Text(
                      '• Your key belongs exclusively to your personal Google Cloud project.\n'
                      '• Google AI Studio includes a generous free tier for Gemini models.\n'
                      '• Quotas, rate limits, and billing (if enabled) are controlled directly by Google in your account.\n'
                      '• AI-Birthday connects directly to Google\'s API and never stores or forwards your key to any external server.',
                      style: TextStyle(
                        fontSize: 13,
                        height: 1.4,
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Align(
                      alignment: Alignment.centerLeft,
                      child: TextButton.icon(
                        onPressed: onOpenBilling,
                        icon: const Icon(Icons.help_outline, size: 16),
                        label: const Text(
                          'Learn about Gemini API billing & quotas ↗',
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Got it'),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _GuideStepTile extends StatelessWidget {
  const _GuideStepTile({
    required this.stepNumber,
    required this.title,
    required this.description,
    this.action,
  });

  final String stepNumber;
  final String title;
  final String description;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        CircleAvatar(
          radius: 14,
          backgroundColor: Theme.of(context).colorScheme.primary,
          foregroundColor: Theme.of(context).colorScheme.onPrimary,
          child: Text(
            stepNumber,
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 15,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                description,
                style: TextStyle(
                  fontSize: 13,
                  color: context.colors.textSecondary,
                ),
              ),
              ?action,
            ],
          ),
        ),
      ],
    );
  }
}
