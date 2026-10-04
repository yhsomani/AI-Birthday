/// Settings screen for API credentials, subscription entitlement, and preferences (SSOT §5, §11, §20).
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
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
  }

  @override
  Widget build(BuildContext context) {
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
      ),
    );
  }
}
