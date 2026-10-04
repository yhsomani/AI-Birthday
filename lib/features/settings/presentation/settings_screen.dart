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
class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
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

  @override
  Widget build(BuildContext context) {
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
