<<<<<<< HEAD
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/application/auth_controller.dart';
import '../../auth/domain/auth_state.dart';
import '../../reminders/application/reminder_settings_controller.dart';
import '../../reminders/domain/quiet_hours.dart';
import '../../reminders/domain/reminder_kind.dart';

/// Settings.
///
/// Sections grow as their owning features land: Account (device Firebase),
/// Notifications/Reminders (Phase 1), Subscription (Phase 2), AI and Gemini
/// API credential (Phase 3).
=======
/// Settings screen for API credentials, subscription entitlement, and preferences (SSOT §5, §11, §20).
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

>>>>>>> e8906b8fe21fa6f7bcb6466936b85c0d59161f88
class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
<<<<<<< HEAD
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
=======
  final TextEditingController _apiKeyController = TextEditingController();
  bool _hasKey = false;
  bool _isLoading = true;
  bool _obscureKey = true;

  @override
  void initState() {
    super.initState();
    _checkStoredKey();
  }

  @override
  void dispose() {
    _apiKeyController.dispose();
    super.dispose();
  }

  Future<void> _checkStoredKey() async {
    final storage = ref.read(credentialStorageProvider);
    final key = await storage.getGeminiApiKey();
    setState(() {
      _hasKey = key != null && key.isNotEmpty;
      if (_hasKey) {
        _apiKeyController.text = key!;
      }
      _isLoading = false;
    });
  }

  Future<void> _saveKey() async {
    final text = _apiKeyController.text.trim();
    final storage = ref.read(credentialStorageProvider);
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
          const SnackBar(content: Text('Gemini API key saved in secure storage.')),
        );
      }
    }
>>>>>>> e8906b8fe21fa6f7bcb6466936b85c0d59161f88
  }

  @override
  Widget build(BuildContext context) {
<<<<<<< HEAD
    final theme = Theme.of(context);
    final settings = ref.watch(reminderSettingsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          _Section(title: 'Account', children: const [_AuthTile()]),
          _Section(
            title: 'AI',
            children: const [
              ListTile(
                leading: Icon(Icons.auto_awesome_outlined),
                title: Text('AI provider'),
                subtitle: Text('Available soon'),
                enabled: false,
              ),
            ],
          ),
          _Section(
            title: 'Notifications',
            children: [
              SwitchListTile(
                secondary: const Icon(Icons.notifications_outlined),
                title: const Text('Reminders'),
                subtitle: const Text('Reminders before birthdays'),
                value: settings.enabled,
                onChanged: (on) =>
                    ref.read(reminderSettingsProvider.notifier).setEnabled(on),
              ),
              if (settings.enabled) ...[
                for (final kind in ReminderKind.values)
                  SwitchListTile(
                    title: Text(kind.title),
                    subtitle: Text(kind.when),
                    value: settings.kinds.contains(kind),
                    onChanged: (on) => ref
                        .read(reminderSettingsProvider.notifier)
                        .setKind(kind, on),
                  ),
                ListTile(
                  leading: const Icon(Icons.bedtime_outlined),
                  title: const Text('Quiet hours'),
                  subtitle: Text(
                    'No delivery between '
                    '${_formatTime(settings.quietHours.start)} and '
                    '${_formatTime(settings.quietHours.end)}',
                  ),
                  onTap: () => _editQuietHours(settings.quietHours),
                ),
              ],
            ],
          ),
          const SizedBox(height: 16),
          Center(
            child: Text(
              'AI-Birthday',
              style: theme.textTheme.labelMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
=======
    final themeMode = ref.watch(themeModeProvider);
    final entitlement = ref.watch(entitlementProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                // Section 1: AI Provider & Gemini Credentials (SSOT §5)
                _buildSectionHeader('AI Provider & Credentials'),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Icon(Icons.key, color: Color(0xFFE03E5D)),
                            const SizedBox(width: 8),
                            const Text(
                              'Personal Gemini API Key',
                              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                            ),
                            const Spacer(),
                            if (_hasKey)
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(
                                  color: Colors.green.withOpacity(0.12),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: const Text(
                                  'Configured',
                                  style: TextStyle(color: Colors.green, fontSize: 11, fontWeight: FontWeight.bold),
                                ),
                              ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Your Gemini API key is stored strictly on your device using hardware-backed secure storage. It is never logged or sent to any developer cloud server.',
                          style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                        ),
                        const SizedBox(height: 16),
                        TextField(
                          controller: _apiKeyController,
                          obscureText: _obscureKey,
                          decoration: InputDecoration(
                            labelText: 'Gemini API Key (AIzaSy...)',
                            suffixIcon: IconButton(
                              icon: Icon(_obscureKey ? Icons.visibility : Icons.visibility_off),
                              onPressed: () => setState(() => _obscureKey = !_obscureKey),
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
                                child: const Text('Remove Key', style: TextStyle(color: Colors.red)),
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
                const SizedBox(height: 24),

                // Section 2: Application Subscription / Entitlement (SSOT §11)
                _buildSectionHeader('Subscription & Entitlement'),
                Card(
                  child: ListTile(
                    leading: Icon(
                      entitlement.canUseAi ? Icons.verified : Icons.lock_outline,
                      color: entitlement.canUseAi ? Colors.amber[700] : Colors.grey,
                    ),
                    title: Text(entitlement.status.displayName),
                    subtitle: Text(
                      entitlement.canUseAi
                          ? 'AI draft generation is unlocked.'
                          : 'Subscribe to unlock AI message drafting.',
                    ),
                    trailing: Switch(
                      value: entitlement.canUseAi,
                      onChanged: (val) {
                        ref.read(entitlementProvider.notifier).state =
                            val ? UserEntitlement.proActive : UserEntitlement.free;
                      },
                    ),
                  ),
                ),
                const SizedBox(height: 24),

                // Section 3: On-Device AI (Gemini Nano)
                _buildSectionHeader('On-Device Intelligence'),
                Card(
                  child: ListTile(
                    leading: const Icon(Icons.memory, color: Colors.blueGrey),
                    title: const Text('Gemini Nano (AICore)'),
                    subtitle: const Text('Secondary fallback on supported Android devices.'),
                    trailing: const Chip(
                      label: Text('Ready on Android', style: TextStyle(fontSize: 10)),
                      visualDensity: VisualDensity.compact,
                    ),
                  ),
                ),
                const SizedBox(height: 24),

                // Section 4: Display & Appearance
                _buildSectionHeader('Appearance'),
                Card(
                  child: SwitchListTile(
                    secondary: const Icon(Icons.dark_mode_outlined),
                    title: const Text('Dark Mode'),
                    value: themeMode == ThemeMode.dark,
                    onChanged: (val) {
                      ref.read(themeModeProvider.notifier).state =
                          val ? ThemeMode.dark : ThemeMode.light;
                    },
                  ),
                ),
              ],
            ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 8),
      child: Text(
        title,
        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
>>>>>>> e8906b8fe21fa6f7bcb6466936b85c0d59161f88
      ),
    );
  }
}
<<<<<<< HEAD

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
          leading: const Icon(Icons.login),
          title: const Text('Sign in with Google'),
          subtitle: const Text('Sync, backups and delivery'),
          onTap: () => ref.read(authControllerProvider.notifier).signIn(),
        ),
        AuthStatus.signedIn => ListTile(
          leading: const Icon(Icons.account_circle_outlined),
          title: Text(state.identity?.displayName ?? 'Signed in'),
          subtitle: Text(state.identity?.email ?? ''),
          trailing: TextButton(
            onPressed: () =>
                ref.read(authControllerProvider.notifier).signOut(),
            child: const Text('Sign out'),
          ),
        ),
        AuthStatus.unavailable => const ListTile(
          leading: Icon(Icons.login),
          title: Text('Sign in with Google'),
          subtitle: Text('Available on device'),
          enabled: false,
        ),
      },
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Text(
            title,
            style: theme.textTheme.titleSmall?.copyWith(
              color: theme.colorScheme.primary,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        ...children,
      ],
    );
  }
}
=======
>>>>>>> e8906b8fe21fa6f7bcb6466936b85c0d59161f88
