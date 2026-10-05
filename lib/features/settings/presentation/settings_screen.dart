/// Authoritative Settings screen for Account, Reminders, AI Credentials,
/// Subscription Entitlement, and Appearance (SSOT §5, §11, §17, §20).
library;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/auth/application/auth_controller.dart';
import 'package:ai_birthday/features/auth/domain/auth_state.dart';
import 'package:ai_birthday/features/auth/presentation/auth_bottom_sheet.dart';
import 'package:ai_birthday/features/reminders/application/reminder_providers.dart';
import 'package:ai_birthday/features/reminders/application/reminder_settings_controller.dart';
import 'package:ai_birthday/features/reminders/domain/quiet_hours.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_kind.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  final TextEditingController _apiKeyController = TextEditingController();
  bool _hasKey = false;
  bool _obscureKey = true;
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
      final syncService = ref.read(cloudSyncServiceProvider);
      final time = await syncService.getLastSyncTime();
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
              ? 'Cloud sync complete! ${result.uploadedCount} records backed up to Firestore.'
              : 'Sync failed: ${result.error ?? 'Unknown error'}',
        ),
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
        setState(() => _hasKey = false);
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
              content: Text('Gemini API key saved in secure hardware storage.'),
            ),
          );
        }
      }
    } catch (e, st) {
      if (!mounted) return;
      // 🛡️ SECURITY: Prevent internal exception strings from leaking into the UI.
      ref
          .read(loggerProvider)
          .error('Settings', 'Error saving API key', error: e, stackTrace: st);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'An unexpected error occurred while saving the API key.',
          ),
        ),
      );
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
              ? 'Pro Subscription Activated! AI message drafting is now unlocked. ✨'
              : 'Purchase could not be completed.',
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
              ? 'Existing Pro subscription restored!'
              : 'No active purchases found to restore.',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final reminderSettings = ref.watch(reminderSettingsProvider);
    final themeMode = ref.watch(themeModeProvider);
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

          // Section 1.5: Cloud Backup & Sync (SSOT §13)
          _SectionHeader(title: 'Cloud Backup & Sync'),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(
                        Icons.cloud_sync_outlined,
                        color: Color(0xFF2D5A46),
                      ),
                      const SizedBox(width: 8),
                      const Text(
                        'Cloud Sync (Firestore)',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      const Spacer(),
                      if (_lastSyncTime != null)
                        Chip(
                          label: const Text(
                            'SYNCED',
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.bold,
                              color: Color(0xFF2D5A46),
                            ),
                          ),
                          backgroundColor: const Color(
                            0xFF2D5A46,
                          ).withValues(alpha: 0.12),
                          visualDensity: VisualDensity.compact,
                        ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _lastSyncTime != null
                        ? 'Last backed up to cloud: ${DateFormat.yMMMd().add_jm().format(_lastSyncTime!)}'
                        : 'Securely sync your birthdays and greetings to Google Cloud Firestore so you never lose them.',
                    style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
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
                            : const Icon(Icons.sync, size: 18),
                        label: Text(_isSyncing ? 'Syncing...' : 'Sync Now'),
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
                            ? const Color(0xFFD9822B)
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
                                ? const Color(0xFF2D5A46)
                                : Colors.grey[700],
                          ),
                        ),
                        backgroundColor: entitlement.canUseAi
                            ? const Color(0xFF2D5A46).withValues(alpha: 0.12)
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
                  if (kDebugMode) ...[
                    const Divider(height: 24),
                    // Developer Sandbox Testing Section
                    Row(
                      children: [
                        const Icon(
                          Icons.bug_report_outlined,
                          size: 16,
                          color: Colors.grey,
                        ),
                        const SizedBox(width: 6),
                        Text(
                          '[Dev Sandbox Testing]',
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.grey[600],
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const Spacer(),
                        Text(
                          'Simulate Pro:',
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.grey[600],
                          ),
                        ),
                        const SizedBox(width: 8),
                        Switch(
                          value: entitlement.canUseAi,
                          onChanged: (val) {
                            HapticFeedback.lightImpact();
                            ref
                                .read(subscriptionNotifierProvider.notifier)
                                .setDevSandboxEntitlement(
                                  val
                                      ? UserEntitlement.proActive
                                      : UserEntitlement.free,
                                );
                          },
                        ),
                      ],
                    ),
                  ],
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
                      const Icon(Icons.key, color: Color(0xFFA64B2A)),
                      const SizedBox(width: 8),
                      const Text(
                        'Personal Gemini API Key',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      const Spacer(),
                      if (_hasKey)
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(
                              0xFF2D5A46,
                            ).withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Text(
                            'Configured',
                            style: TextStyle(
                              color: Color(0xFF2D5A46),
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Stored strictly on your device using hardware-backed secure storage. Never logged or sent to external servers.',
                    style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                  ),
                  TextField(
                    controller: _apiKeyController,
                    obscureText: _obscureKey,
                    decoration: InputDecoration(
                      labelText: 'Gemini API Key (AIzaSy...)',
                      suffixIcon: IconButton(
                        icon: Icon(
                          _obscureKey ? Icons.visibility : Icons.visibility_off,
                        ),
                        onPressed: () =>
                            setState(() => _obscureKey = !_obscureKey),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      if (_hasKey) ...[
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
                        const SizedBox(width: 8),
                      ],
                      FilledButton(
                        onPressed: _saveKey,
                        child: const Text('Save Key'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Section 4: On-Device AI / Gemini Nano (SSOT §5, §25)
          _SectionHeader(title: 'On-Device Intelligence'),
          Card(
            child: ListTile(
              leading: const Icon(Icons.memory, color: Color(0xFF2D5A46)),
              title: const Text('Gemini Nano (AICore)'),
              subtitle: Text(switch (_nanoState) {
                NanoState.available =>
                  'Ready on device for offline generation.',
                NanoState.downloadable =>
                  'Model available for download on this device.',
                NanoState.downloading => 'Downloading on-device model...',
                _ => 'Secondary fallback on supported Android devices.',
              }),
              trailing: Chip(
                label: Text(
                  _nanoState == NanoState.available ? 'AVAILABLE' : 'STANDBY',
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: _nanoState == NanoState.available
                        ? const Color(0xFF2D5A46)
                        : Colors.grey[700],
                  ),
                ),
                backgroundColor: _nanoState == NanoState.available
                    ? const Color(0xFF2D5A46).withValues(alpha: 0.12)
                    : Colors.grey.withValues(alpha: 0.12),
                visualDensity: VisualDensity.compact,
              ),
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
                  onChanged: (on) {
                    HapticFeedback.lightImpact();
                    if (on) {
                      ref
                          .read(notificationSchedulerGatewayProvider)
                          .requestPermission();
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

          // Section 6: Display & Appearance
          _SectionHeader(title: 'Appearance'),
          Card(
            child: SwitchListTile(
              secondary: const Icon(Icons.dark_mode_outlined),
              title: const Text('Dark Mode'),
              value: themeMode == ThemeMode.dark,
              onChanged: (val) {
                HapticFeedback.lightImpact();
                ref.read(themeModeProvider.notifier).state = val
                    ? ThemeMode.dark
                    : ThemeMode.light;
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
          color: Color(0xFFA64B2A),
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
          leading: const Icon(Icons.login, color: Color(0xFFA64B2A)),
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
            backgroundColor: const Color(0xFF2D5A46).withValues(alpha: 0.15),
            foregroundColor: const Color(0xFF2D5A46),
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
