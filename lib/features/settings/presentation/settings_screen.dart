/// Authoritative Settings screen for Account, Reminders, AI Credentials,
/// Subscription Entitlement, and Help & Guide (SSOT §5, §11, §17, §20).
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/auth/application/auth_controller.dart';
import 'package:ai_birthday/features/auth/domain/auth_state.dart';
import 'package:ai_birthday/features/auth/presentation/auth_bottom_sheet.dart';
import 'package:ai_birthday/features/reminders/application/reminder_providers.dart';
import 'package:ai_birthday/features/reminders/application/reminder_settings_controller.dart';
import 'package:ai_birthday/features/reminders/domain/quiet_hours.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_kind.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';

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
  bool _isPurchasing = false;
  bool _isSyncing = false;
  DateTime? _lastSyncTime;
  NanoState _nanoState = NanoState.unavailable;

  @override
  void initState() {
    super.initState();
    _checkStoredKey();
    _checkNanoState();
    _loadLastSyncTime();
  }

  @override
  void dispose() {
    _apiKeyController.dispose();
    super.dispose();
  }

  Future<void> _checkStoredKey() async {
    try {
      final storage = ref.read(credentialStorageProvider);
      final key = await storage.getGeminiApiKey();
      if (!mounted) return;
      if (key != null && key.isNotEmpty) {
        setState(() {
          _hasKey = true;
          _apiKeyController.text = key;
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
    if (auth == null || !auth.isSignedIn) {
      AuthBottomSheet.show(context);
      return;
    }

    setState(() => _isSyncing = true);
    final syncService = ref.read(cloudSyncServiceProvider);
    final result = await syncService.sync(auth);
    if (!mounted) return;
    setState(() {
      _isSyncing = false;
      if (result.success) {
        _lastSyncTime = result.timestamp;
      }
    });

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          result.success
              ? 'Cloud backup complete. ${result.uploadedCount} records saved.'
              : 'Backup failed: ${result.error ?? 'Unknown error'}',
        ),
      ),
    );
  }

  Future<void> _handleCloudRestore() async {
    HapticFeedback.lightImpact();
    final auth = ref.read(authControllerProvider).valueOrNull;
    if (auth == null || !auth.isSignedIn) {
      AuthBottomSheet.show(context);
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Restore from Cloud Backup?'),
        content: const Text(
          'This will download your saved birthdays and contacts from the cloud and merge them into your device. Existing records with matching IDs will be updated.',
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

    setState(() => _isSyncing = true);
    final syncService = ref.read(cloudSyncServiceProvider);
    final result = await syncService.restore(auth);
    if (!mounted) return;
    setState(() => _isSyncing = false);

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          result.success
              ? 'Cloud restore complete! ${result.downloadedCount} records restored to your device.'
              : 'Restore failed: ${result.error ?? 'Unknown error'}',
        ),
      ),
    );
  }

  Future<void> _launchExternalUrl(String url) async {
    final uri = Uri.parse(url);
    try {
      final launched = await launchUrl(
        uri,
        mode: LaunchMode.externalApplication,
      );
      if (!launched && mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: const Text('Could not open that link. Please try again.')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: const Text('Could not open that link. Please try again.')));
      }
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
        setState(() {
          _hasKey = true;
          _connectionResult = result;
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
    final storage = ref.read(credentialStorageProvider);
    try {
      if (text.isEmpty) {
        await storage.deleteGeminiApiKey();
        setState(() {
          _hasKey = false;
          _connectionResult = null;
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Gemini API key removed.')),
          );
        }
      } else {
        await storage.saveGeminiApiKey(text);
        setState(() => _hasKey = true);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Gemini API key saved in secure device storage.'),
            ),
          );
        }
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

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          success
              ? 'Purchase flow opened. Google Play will verify the subscription before AI is unlocked.'
              : 'Purchase could not be started. Sign in and try again.',
        ),
      ),
    );
  }

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
          _SectionHeader(title: 'Account'),
          Card(child: const _AuthTile()),
          const SizedBox(height: 20),

          // Section 1.5: Cloud Backup (SSOT §13)
          _SectionHeader(title: 'Cloud Backup'),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(
                        Icons.cloud_upload_outlined,
                        color: AppColors.accentForest,
                      ),
                      const SizedBox(width: 8),
                      const Text(
                        'Cloud Backup (Firestore)',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      const Spacer(),
                      if (_lastSyncTime != null)
                        Chip(
                          label: const Text(
                            'LAST SUCCESSFUL',
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.bold,
                              color: AppColors.accentForest,
                            ),
                          ),
                          backgroundColor: AppColors.accentForestContainer,
                          visualDensity: VisualDensity.compact,
                        ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _lastSyncTime != null
                        ? 'Last backed up: ${DateFormat.yMMMd().add_jm().format(_lastSyncTime!)}'
                        : 'Save a recoverable copy of your birthdays and contacts to your signed-in cloud account.',
                    style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Privacy note: Backing up uploads your saved recipient names, birthdays, phone numbers, and notes to this app\'s Firestore project under your signed-in account.',
                    style: TextStyle(
                      fontSize: 11,
                      color: Colors.grey[500],
                      fontStyle: FontStyle.italic,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    alignment: WrapAlignment.end,
                    children: [
                      OutlinedButton.icon(
                        onPressed: _isSyncing ? null : _handleCloudRestore,
                        icon: const Icon(
                          Icons.cloud_download_outlined,
                          size: 18,
                        ),
                        label: const Text('Restore from Cloud'),
                      ),
                      FilledButton.tonalIcon(
                        onPressed: _isSyncing ? null : _handleCloudSync,
                        icon: _isSyncing
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Icon(Icons.cloud_upload, size: 18),
                        label: Text(
                          _isSyncing ? 'Backing up...' : 'Back Up Now',
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Section 2: Subscription & Entitlement (SSOT §11)
          _SectionHeader(title: 'Subscription & Entitlement'),
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
                            ? AppColors.accentAmber
                            : Colors.grey,
                      ),
                      const SizedBox(width: 10),
                      Text(
                        entitlement.status.displayName,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      const Spacer(),
                      Chip(
                        label: Text(
                          entitlement.canUseAi ? 'UNLOCKED' : 'FREE TIER',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                            color: entitlement.canUseAi
                                ? AppColors.accentForest
                                : Colors.grey[700],
                          ),
                        ),
                        backgroundColor: entitlement.canUseAi
                            ? AppColors.accentForestContainer
                            : Colors.grey.withValues(alpha: 0.12),
                        visualDensity: VisualDensity.compact,
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    entitlement.canUseAi
                        ? 'Full AI draft generation, rewrite variations, and personalized message studio are active.'
                        : 'Application AI features require an active subscription entitlement. Birthday tracking and manual drafting remain free forever.',
                    style: TextStyle(fontSize: 13, color: Colors.grey[600]),
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
          _SectionHeader(title: 'AI provider'),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.key, color: AppColors.primaryTerracotta),
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
                    style: TextStyle(fontSize: 13, color: Colors.grey[700]),
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
                        onPressed: () =>
                            setState(() => _obscureKey = !_obscureKey),
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
                            _saveKey();
                          },
                          child: const Text(
                            'Remove Key',
                            style: TextStyle(color: Colors.red),
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
                  InkWell(
                    onTap: () => _launchExternalUrl(_geminiBillingUrl),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
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
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Section 4: On-Device AI / Gemini Nano (SSOT §5, §25)
          _SectionHeader(title: 'On-Device Intelligence'),
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(
                    Icons.memory,
                    color: AppColors.accentForest,
                  ),
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
                            ? AppColors.accentForest
                            : Colors.grey[700],
                      ),
                    ),
                    backgroundColor: _nanoState == NanoState.available
                        ? AppColors.accentForest.withValues(alpha: 0.12)
                        : Colors.grey.withValues(alpha: 0.12),
                    visualDensity: VisualDensity.compact,
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
                  child: Text(
                    'AI provider: your active AI-Birthday Pro access is required. When available, AI-Birthday uses your saved Gemini key; otherwise supported Android devices can use Gemini Nano on-device.',
                    style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),

          // Section 5: Reminders & Quiet Hours (SSOT §17)
          _SectionHeader(title: 'Reminders'),
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
                      '${_formatTime(reminderSettings.quietHours.end)} (shifted to 8:00 AM)',
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
                      await ref
                          .read(notificationSchedulerGatewayProvider)
                          .sendTestNotification(
                            title: '🎉 Birthday Reminder Test',
                            body:
                                'Notifications are working properly on your device!',
                          );
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Test notification dispatched!'),
                            duration: Duration(seconds: 2),
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

          // Section 6: Help & Guide
          _SectionHeader(title: 'Help & Guide'),
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
    if (_connectionResult?.status == GeminiConnectionStatus.connected) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: AppColors.accentForest.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(8),
        ),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.check_circle, size: 12, color: AppColors.accentForest),
            SizedBox(width: 4),
            Text(
              'Connected',
              style: TextStyle(
                color: AppColors.accentForest,
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
          color: Colors.red.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(8),
        ),
        child: const Text(
          'Invalid Key',
          style: TextStyle(
            color: Colors.red,
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
          color: Colors.amber.withValues(alpha: 0.15),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          'Quota Exceeded',
          style: TextStyle(
            color: Colors.amber[900],
            fontSize: 11,
            fontWeight: FontWeight.bold,
          ),
        ),
      );
    } else if (_hasKey) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: AppColors.accentForest.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(8),
        ),
        child: const Text(
          'Configured',
          style: TextStyle(
            color: AppColors.accentForest,
            fontSize: 11,
            fontWeight: FontWeight.bold,
          ),
        ),
      );
    } else {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: Colors.grey.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          'Not Configured',
          style: TextStyle(
            color: Colors.grey[700],
            fontSize: 11,
            fontWeight: FontWeight.bold,
          ),
        ),
      );
    }
  }

  Widget _buildConnectionResultBanner(GeminiConnectionResult result) {
    final (
      Color bgColor,
      Color borderColor,
      Color textColor,
      IconData icon,
    ) = switch (result.status) {
      GeminiConnectionStatus.connected => (
        AppColors.accentForest.withValues(alpha: 0.12),
        AppColors.accentForest,
        AppColors.accentForest,
        Icons.check_circle_outline,
      ),
      GeminiConnectionStatus.invalidKey => (
        Colors.red.withValues(alpha: 0.10),
        Colors.red,
        Colors.red[800] ?? Colors.red,
        Icons.error_outline,
      ),
      GeminiConnectionStatus.quotaExceeded => (
        Colors.amber.withValues(alpha: 0.15),
        Colors.amber[800] ?? Colors.amber,
        Colors.amber[900] ?? Colors.black,
        Icons.warning_amber_rounded,
      ),
      GeminiConnectionStatus.networkUnavailable => (
        Colors.blueGrey.withValues(alpha: 0.12),
        Colors.blueGrey,
        Colors.blueGrey[800] ?? Colors.blueGrey,
        Icons.wifi_off_outlined,
      ),
      GeminiConnectionStatus.error => (
        Colors.red.withValues(alpha: 0.10),
        Colors.red,
        Colors.red[800] ?? Colors.red,
        Icons.error_outline,
      ),
    };

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: borderColor.withValues(alpha: 0.4)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 20, color: borderColor),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              result.message,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: textColor,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 8),
      child: Text(
        title,
        style: const TextStyle(
          fontWeight: FontWeight.bold,
          fontSize: 13,
          letterSpacing: 0.5,
          color: AppColors.primaryTerracotta,
        ),
      ),
    );
  }
}

class _AuthTile extends ConsumerWidget {
  const _AuthTile();

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
          title: Text('Sign in with Google'),
        ),
        AuthStatus.signedOut => ListTile(
          leading: const Icon(Icons.login, color: AppColors.primaryTerracotta),
          title: const Text('Sign in with Google'),
          subtitle: const Text('Sync, backups and delivery'),
          trailing: FilledButton.tonal(
            onPressed: () {
              HapticFeedback.lightImpact();
              AuthBottomSheet.show(context);
            },
            child: const Text('Sign in'),
          ),
          onTap: () {
            HapticFeedback.lightImpact();
            ref.read(authControllerProvider.notifier).signIn();
          },
        ),
        AuthStatus.signedIn => ListTile(
          leading: CircleAvatar(
            backgroundColor: AppColors.accentForest.withValues(alpha: 0.15),
            foregroundColor: AppColors.accentForest,
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
            onPressed: () {
              HapticFeedback.lightImpact();
              ref.read(authControllerProvider.notifier).signOut();
            },
            child: const Text('Sign out'),
          ),
        ),
        AuthStatus.unavailable => ListTile(
          leading: const Icon(Icons.login),
          title: const Text('Sign in with Google'),
          subtitle: const Text('Available on device'),
          enabled: false,
          onTap: () {
            HapticFeedback.lightImpact();
            AuthBottomSheet.show(context);
          },
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
                      color: AppColors.primaryTerracotta.withValues(
                        alpha: 0.12,
                      ),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(
                      Icons.auto_awesome,
                      color: AppColors.primaryTerracotta,
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
                    'Click "Create API key" (or "Get API key"). Select an existing Google Cloud project or create a new one instantly.',
              ),
              const SizedBox(height: 18),

              // Step 3
              const _GuideStepTile(
                stepNumber: '3',
                title: 'Copy your API Key',
                description:
                    'Copy the generated key to your clipboard. It will start with "AIzaSy...".',
              ),
              const SizedBox(height: 18),

              // Step 4
              const _GuideStepTile(
                stepNumber: '4',
                title: 'Paste and Test in Settings',
                description:
                    'Return to AI-Birthday, paste the key into the Gemini API Key field, and tap "Test Connection" to verify it works.',
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
          backgroundColor: AppColors.primaryTerracotta,
          foregroundColor: Colors.white,
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
                style: TextStyle(fontSize: 13, color: Colors.grey[700]),
              ),
              ?action,
            ],
          ),
        ),
      ],
    );
  }
}
