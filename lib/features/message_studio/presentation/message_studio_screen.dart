/// Message Studio for AI generation, editing, and delivery handoff (SSOT §16).
library;

import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';

class MessageStudioScreen extends ConsumerStatefulWidget {
  const MessageStudioScreen({super.key, this.birthdayId, this.personId})
    : assert(
        (birthdayId != null && birthdayId.length > 0) ||
            (personId != null && personId.length > 0),
        'Either birthdayId or personId must be provided',
      );

  final String? birthdayId;
  final String? personId;

  @override
  ConsumerState<MessageStudioScreen> createState() =>
      _MessageStudioScreenState();
}

class _MessageStudioScreenState extends ConsumerState<MessageStudioScreen>
    with WidgetsBindingObserver {
  final TextEditingController _messageController = TextEditingController();
  final TextEditingController _customInstructionController =
      TextEditingController();

  Birthday? _birthday;
  Person? _person;
  MessageDraft? _draft;
  bool _isLoading = true;
  bool _isGenerating = false;
  String? _errorMessage;

  Timer? _autosaveTimer;
  bool _isSaving = false;
  DateTime? _lastSavedTime;

  MessageTone _selectedTone = MessageTone.warm;
  MessageLength _selectedLength = MessageLength.standard;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _loadData();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive) {
      _autosaveTimer?.cancel();
      _performAutosave();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _autosaveTimer?.cancel();
    if (_messageController.text.isNotEmpty && _person != null) {
      _saveDraft(status: _draft?.status ?? DraftStatus.draft);
    }
    _messageController.dispose();
    _customInstructionController.dispose();
    super.dispose();
  }

  void _onMessageChanged(String text) {
    setState(() {});
    _autosaveTimer?.cancel();
    _autosaveTimer = Timer(const Duration(milliseconds: 750), () {
      _performAutosave();
    });
  }

  Future<void> _performAutosave() async {
    if (_person == null || !mounted) return;
    setState(() => _isSaving = true);
    await _saveDraft(status: _draft?.status ?? DraftStatus.draft);
    if (mounted) {
      setState(() {
        _isSaving = false;
        _lastSavedTime = DateTime.now();
      });
    }
  }

  String get _activeBirthdayId => _birthday?.id ?? widget.birthdayId ?? '';

  Future<void> _loadData() async {
    setState(() => _isLoading = true);

    final bRepo = ref.read(birthdaysRepositoryProvider);
    final pRepo = ref.read(peopleRepositoryProvider);
    final dRepo = ref.read(draftsRepositoryProvider);

    Birthday? birthday;
    if (widget.birthdayId != null && widget.birthdayId!.isNotEmpty) {
      birthday = await bRepo.getBirthday(widget.birthdayId!);
      birthday ??= await bRepo.getBirthdayForPerson(widget.birthdayId!);
    }
    if (birthday == null &&
        widget.personId != null &&
        widget.personId!.isNotEmpty) {
      birthday = await bRepo.getBirthdayForPerson(widget.personId!);
    }

    if (birthday != null) {
      final person = await pRepo.getPerson(birthday.personId);
      final draft = await dRepo.getDraftForBirthday(birthday.id);

      setState(() {
        _birthday = birthday;
        _person = person;
        _draft = draft;
        if (draft != null) {
          _messageController.text = draft.body;
          _selectedTone = draft.tone;
          _selectedLength = draft.length;
        } else if (person != null) {
          _selectedTone = person.preferredTone;
        }
        _isLoading = false;
      });
    } else {
      setState(() {
        _errorMessage = 'Birthday event not found.';
        _isLoading = false;
      });
    }
  }

  Future<void> _generateWithAi() async {
    if (_person == null) return;

    setState(() {
      _isGenerating = true;
      _errorMessage = null;
    });

    try {
      final aiRouter = ref.read(aiRouterProvider);
      final entitlement = ref.read(entitlementProvider);

      final request = AiGenerationRequest(
        person: _person!,
        tone: _selectedTone,
        length: _selectedLength,
        customInstruction: _customInstructionController.text.trim().isNotEmpty
            ? _customInstructionController.text.trim()
            : null,
        existingMessage: _messageController.text.trim().isNotEmpty
            ? _messageController.text.trim()
            : null,
      );

      final result = await aiRouter.generate(
        request: request,
        entitlement: entitlement,
      );

      _messageController.text = result.message;

      // Update or create draft
      final draftId =
          _draft?.id ?? 'draft-${DateTime.now().millisecondsSinceEpoch}';
      final updatedDraft = MessageDraft(
        id: draftId,
        birthdayId: _activeBirthdayId,
        personId: _person!.id,
        body: result.message,
        tone: _selectedTone,
        length: _selectedLength,
        status: DraftStatus.draft,
        providerType: result.providerType,
        createdAt: _draft?.createdAt ?? DateTime.now(),
        updatedAt: DateTime.now(),
      );

      await ref.read(draftsRepositoryProvider).saveDraft(updatedDraft);
      await ref
          .read(birthdaysRepositoryProvider)
          .updateBirthdayStatus(
            _activeBirthdayId,
            BirthdayStatus.messageDrafted,
            draftId: draftId,
          );

      setState(() {
        _draft = updatedDraft;
        _isGenerating = false;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Message drafted with AI ✨')),
        );
      }
    } on AppFailure catch (e) {
      setState(() {
        _errorMessage = '${e.message}: ${e.detail ?? ''} ${e.action ?? ''}';
        _isGenerating = false;
      });
    } catch (e, st) {
      // 🛡️ SECURITY: Prevent internal exception strings from leaking into the UI.
      ref
          .read(loggerProvider)
          .error(
            'MessageStudio',
            'Unexpected error drafting message',
            error: e,
            stackTrace: st,
          );
      setState(() {
        _errorMessage =
            'An unexpected error occurred while drafting the message.';
        _isGenerating = false;
      });
    }
  }

  Future<void> _saveDraft({DraftStatus status = DraftStatus.draft}) async {
    if (_person == null) return;
    final draftId =
        _draft?.id ?? 'draft-${DateTime.now().millisecondsSinceEpoch}';
    final updatedDraft = MessageDraft(
      id: draftId,
      birthdayId: _activeBirthdayId,
      personId: _person!.id,
      body: _messageController.text,
      tone: _selectedTone,
      length: _selectedLength,
      status: status,
      providerType: _draft?.providerType ?? 'manual',
      createdAt: _draft?.createdAt ?? DateTime.now(),
      updatedAt: DateTime.now(),
    );

    await ref.read(draftsRepositoryProvider).saveDraft(updatedDraft);
    if (mounted) {
      setState(() {
        _draft = updatedDraft;
      });
    }
  }

  Future<void> _handleWhatsAppSend() async {
    final message = _messageController.text.trim();
    if (message.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please enter or generate a message first.'),
        ),
      );
      return;
    }

    final phone = _person?.phoneNumber;
    if (phone == null || phone.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Recipient does not have a phone number.'),
        ),
      );
      return;
    }

    try {
      final builder = ref.read(whatsappHandoffBuilderProvider);
      final handoff = builder.buildHandoff(
        rawPhoneNumber: phone,
        message: message,
      );

      // Save draft as ready for delivery
      await _saveDraft(status: DraftStatus.ready);

      // Attempt to launch WhatsApp with official Click-to-Chat URL
      bool launched = false;
      try {
        launched = await launchUrl(
          handoff.uri,
          mode: LaunchMode.externalApplication,
        );
      } catch (e) {
        ref
            .read(loggerProvider)
            .warning(
              'MessageStudio',
              'Could not launch WhatsApp directly',
              error: e,
            );
      }

      // Track handoff status only when launch succeeds; otherwise action required (SSOT §9)
      if (launched) {
        await ref
            .read(birthdaysRepositoryProvider)
            .updateBirthdayStatus(_activeBirthdayId, BirthdayStatus.handedOff);
      } else {
        await ref
            .read(birthdaysRepositoryProvider)
            .updateBirthdayStatus(_activeBirthdayId, BirthdayStatus.failed);
      }

      if (mounted) {
        _showHandoffConfirmationDialog(
          handoff.uri.toString(),
          wasLaunched: launched,
          channelName: 'WhatsApp',
        );
      }
    } on AppFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.detail ?? e.message ?? 'Handoff failed')),
        );
      }
    }
  }

  Future<void> _handleSmsSend() async {
    final message = _messageController.text.trim();
    if (message.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please enter or generate a message first.'),
        ),
      );
      return;
    }

    final phone = _person?.phoneNumber;
    await _saveDraft(status: DraftStatus.ready);
    final smsService = ref.read(smsDeliveryServiceProvider);
    final launched = await smsService.sendSms(
      phoneNumber: phone,
      message: message,
    );

    if (launched) {
      await ref
          .read(birthdaysRepositoryProvider)
          .updateBirthdayStatus(_activeBirthdayId, BirthdayStatus.handedOff);
    }

    if (mounted) {
      _showHandoffConfirmationDialog(
        phone != null ? 'Recipient: $phone' : 'Prepared message in SMS app',
        wasLaunched: launched,
        channelName: 'SMS',
      );
    }
  }

  Future<void> _handleShareSend() async {
    final message = _messageController.text.trim();
    if (message.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please enter or generate a message first.'),
        ),
      );
      return;
    }

    await _saveDraft(status: DraftStatus.ready);
    final shareService = ref.read(nativeShareServiceProvider);
    final launched = await shareService.shareText(
      text: message,
      title: 'Birthday greeting for ${_person?.name}',
    );

    if (launched) {
      await ref
          .read(birthdaysRepositoryProvider)
          .updateBirthdayStatus(_activeBirthdayId, BirthdayStatus.handedOff);
    }

    if (mounted) {
      _showHandoffConfirmationDialog(
        'Shared via Android chooser',
        wasLaunched: launched,
        channelName: 'Share Sheet',
      );
    }
  }

  Future<void> _rewriteMessage({
    MessageLength? length,
    String? customInstruction,
    String? targetLanguage,
    String? languageName,
  }) async {
    if (_person == null) return;
    final currentText = _messageController.text.trim();
    if (currentText.isEmpty) {
      await _generateWithAi();
      return;
    }

    setState(() {
      _isGenerating = true;
      _errorMessage = null;
    });

    try {
      final aiRouter = ref.read(aiRouterProvider);
      final entitlement = ref.read(entitlementProvider);

      final request = AiGenerationRequest(
        person: _person!,
        tone: _selectedTone,
        length: length ?? _selectedLength,
        customInstruction: customInstruction,
        existingMessage: currentText,
        targetLanguage: targetLanguage,
      );

      final result = await aiRouter.generate(
        request: request,
        entitlement: entitlement,
      );

      _messageController.text = result.message;
      if (length != null) {
        _selectedLength = length;
      }

      await _saveDraft();

      if (mounted) {
        final label = languageName != null
            ? 'Translated to $languageName ✨'
            : (length == MessageLength.short
                  ? 'Message shortened ✂️'
                  : length == MessageLength.expanded
                  ? 'Message expanded 📝'
                  : 'Message refined ✨');
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(label)));
      }
    } on AppFailure catch (e) {
      setState(() {
        _errorMessage = '${e.message}: ${e.detail ?? ''} ${e.action ?? ''}';
      });
    } catch (e, st) {
      ref
          .read(loggerProvider)
          .error(
            'MessageStudio',
            'Unexpected error rewriting message',
            error: e,
            stackTrace: st,
          );
      setState(() {
        _errorMessage = 'An error occurred while rewriting the message.';
      });
    } finally {
      if (mounted) {
        setState(() => _isGenerating = false);
      }
    }
  }

  Future<void> _generateVariations() async {
    if (_person == null) return;
    setState(() {
      _isGenerating = true;
      _errorMessage = null;
    });

    try {
      final aiRouter = ref.read(aiRouterProvider);
      final entitlement = ref.read(entitlementProvider);

      final tones = [
        MessageTone.warm,
        MessageTone.funny,
        MessageTone.emotional,
      ];
      final variations = await Future.wait(
        tones.map((t) async {
          final req = AiGenerationRequest(
            person: _person!,
            tone: t,
            length: _selectedLength,
          );
          final res = await aiRouter.generate(
            request: req,
            entitlement: entitlement,
          );
          return MapEntry(t, res.message);
        }),
      );

      if (!mounted) return;
      setState(() => _isGenerating = false);

      final selected = await showModalBottomSheet<String>(
        context: context,
        isScrollControlled: true,
        builder: (ctx) => SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(Icons.auto_awesome, color: AppColors.accentAmber),
                    SizedBox(width: 8),
                    Text(
                      'Select a Variation',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 18,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                for (final v in variations) ...[
                  Card(
                    child: InkWell(
                      borderRadius: BorderRadius.circular(12),
                      onTap: () => Navigator.of(ctx).pop(v.value),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Chip(
                                  label: Text(v.key.displayName),
                                  visualDensity: VisualDensity.compact,
                                ),
                                const Spacer(),
                                const Text(
                                  'Tap to choose',
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: Colors.grey,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Text(v.value, style: const TextStyle(fontSize: 14)),
                          ],
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                ],
              ],
            ),
          ),
        ),
      );

      if (selected != null && mounted) {
        _messageController.text = selected;
        await _saveDraft();
        if (mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(const SnackBar(content: Text('Variation applied! ✨')));
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e is AppFailure
              ? '${e.message}: ${e.detail ?? ''} ${e.action ?? ''}'.trim()
              : 'Could not generate variations. You can compose your message manually or retry.';
          _isGenerating = false;
        });
      }
    }
  }

  void _showTranslateDialog() {
    final languages = [
      {'code': 'es', 'name': 'Spanish (Español)'},
      {'code': 'fr', 'name': 'French (Français)'},
      {'code': 'de', 'name': 'German (Deutsch)'},
      {'code': 'hi', 'name': 'Hindi (हिन्दी)'},
      {'code': 'it', 'name': 'Italian (Italiano)'},
      {'code': 'ja', 'name': 'Japanese (日本語)'},
    ];

    showModalBottomSheet(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text(
                'Translate Greeting To',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
            ),
            for (final lang in languages)
              ListTile(
                title: Text(lang['name']!),
                onTap: () {
                  Navigator.of(ctx).pop();
                  _rewriteMessage(
                    targetLanguage: lang['code'],
                    languageName: lang['name'],
                  );
                },
              ),
          ],
        ),
      ),
    );
  }

  void _showHandoffConfirmationDialog(
    String detailInfo, {
    required bool wasLaunched,
    String channelName = 'WhatsApp',
  }) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Row(
            children: [
              Icon(
                wasLaunched ? Icons.mark_chat_read_outlined : Icons.open_in_new,
                color: AppColors.accentForest,
              ),
              const SizedBox(width: 8),
              Text('$channelName Handoff'),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                wasLaunched
                    ? '$channelName opened with your message. Once you have dispatched it in $channelName, confirm below to update your celebration tracker.'
                    : '$channelName could not be opened automatically. You can copy the message to your clipboard:',
              ),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.grey.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.grey.withValues(alpha: 0.2)),
                ),
                child: SelectableText(
                  detailInfo,
                  style: const TextStyle(fontSize: 11, fontFamily: 'monospace'),
                ),
              ),
              const SizedBox(height: 16),
              const Text(
                'Did you send the message?',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Not Sent Yet'),
            ),
            FilledButton(
              onPressed: () async {
                HapticFeedback.lightImpact();
                await ref
                    .read(birthdaysRepositoryProvider)
                    .updateBirthdayStatus(
                      _activeBirthdayId,
                      BirthdayStatus.completed,
                    );
                await _saveDraft(status: DraftStatus.confirmedSent);
                if (context.mounted) {
                  Navigator.of(context).pop();
                  context.pop();
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Celebration confirmed as sent! 🎉'),
                    ),
                  );
                }
              },
              child: const Text('Yes, Message Sent!'),
            ),
          ],
        );
      },
    );
  }

  void _copyToClipboard() {
    final text = _messageController.text.trim();
    if (text.isNotEmpty) {
      Clipboard.setData(ClipboardData(text: text));
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Copied to clipboard 📋')));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    if (_person == null || _birthday == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Message Studio')),
        body: Center(child: Text(_errorMessage ?? 'Birthday not found')),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text('Message for ${_person!.name}'),
        actions: [
          IconButton(
            icon: const Icon(Icons.copy_outlined),
            tooltip: 'Copy',
            onPressed: _copyToClipboard,
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(
          16,
          16,
          16,
          AppSpacing.bottomClearance,
        ),
        children: [
          // Recipient Context Card
          _buildRecipientCard(),
          const SizedBox(height: AppSpacing.md),

          // Tone & Length Controls
          _buildControlsCard(),
          const SizedBox(height: AppSpacing.md),

          // Error banner with recovery action buttons
          if (_errorMessage != null) ...[
            Container(
              padding: const EdgeInsets.all(AppSpacing.sm),
              decoration: BoxDecoration(
                color: Theme.of(
                  context,
                ).colorScheme.errorContainer.withValues(alpha: 0.5),
                borderRadius: AppSpacing.roundedMd,
                border: Border.all(
                  color: Theme.of(
                    context,
                  ).colorScheme.error.withValues(alpha: 0.3),
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        Icons.error_outline,
                        color: Theme.of(context).colorScheme.error,
                      ),
                      const SizedBox(width: AppSpacing.xs),
                      Expanded(
                        child: Text(
                          _errorMessage!,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.error,
                            fontSize: 13,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 4,
                    children: [
                      TextButton.icon(
                        icon: const Icon(Icons.refresh, size: 16),
                        label: const Text('Retry'),
                        onPressed: _generateWithAi,
                        style: TextButton.styleFrom(
                          visualDensity: VisualDensity.compact,
                        ),
                      ),
                      TextButton.icon(
                        icon: const Icon(Icons.edit_note, size: 16),
                        label: const Text('Write manually'),
                        onPressed: () {
                          setState(() => _errorMessage = null);
                        },
                        style: TextButton.styleFrom(
                          visualDensity: VisualDensity.compact,
                        ),
                      ),
                      TextButton.icon(
                        icon: const Icon(Icons.tune, size: 16),
                        label: const Text('AI Settings'),
                        onPressed: () => context.push('/settings'),
                        style: TextButton.styleFrom(
                          visualDensity: VisualDensity.compact,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: AppSpacing.md),
          ],

          // Message Editor Box
          _buildMessageEditor(),
          const SizedBox(height: AppSpacing.lg),

          // Action Buttons
          ResponsiveActionBar(
            primary: FilledButton.icon(
              onPressed: _handleWhatsAppSend,
              icon: const Icon(Icons.chat),
              label: const Text('Send on WhatsApp'),
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.whatsappGreen,
                foregroundColor: Colors.white,
              ),
            ),
            secondary: FilledButton.tonalIcon(
              onPressed: _isGenerating ? null : _generateWithAi,
              icon: _isGenerating
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.auto_awesome),
              label: Text(_isGenerating ? 'Drafting...' : 'Generate with AI'),
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          // Alternate delivery channels (SSOT §10)
          Wrap(
            spacing: 8,
            runSpacing: 8,
            alignment: WrapAlignment.center,
            children: [
              OutlinedButton.icon(
                onPressed: _handleSmsSend,
                icon: const Icon(Icons.sms_outlined, size: 18),
                label: const Text('Send via SMS'),
              ),
              OutlinedButton.icon(
                onPressed: _handleShareSend,
                icon: const Icon(Icons.share_outlined, size: 18),
                label: const Text('Share Sheet'),
              ),
              OutlinedButton.icon(
                onPressed: _copyToClipboard,
                icon: const Icon(Icons.copy_outlined, size: 18),
                label: const Text('Copy Text'),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildRecipientCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(child: Text(_person!.name[0])),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _person!.name,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        '${_person!.relationship.displayName} • ${_person!.phoneNumber ?? 'No phone'}',
                        style: TextStyle(fontSize: 13, color: Colors.grey[600]),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            if (_person!.importantFacts.isNotEmpty) ...[
              const Divider(height: 24),
              const Text(
                'Known Facts (User-verified):',
                style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 4),
              Text(
                _person!.importantFacts.join(' • '),
                style: TextStyle(fontSize: 12, color: Colors.grey[700]),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildControlsCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Tone:',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: MessageTone.values.map((t) {
                final isSelected = t == _selectedTone;
                return ChoiceChip(
                  label: Text(t.displayName),
                  selected: isSelected,
                  onSelected: (val) {
                    if (val) setState(() => _selectedTone = t);
                  },
                );
              }).toList(),
            ),
            const SizedBox(height: 12),
            const Text(
              'Length:',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: MessageLength.values.map((l) {
                final isSelected = l == _selectedLength;
                return ChoiceChip(
                  label: Text(l.name.toUpperCase()),
                  selected: isSelected,
                  onSelected: (val) {
                    if (val) setState(() => _selectedLength = l);
                  },
                );
              }).toList(),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _customInstructionController,
              decoration: const InputDecoration(
                hintText:
                    'Optional tweak (e.g. "rhyme", "mention weekend party")',
                isDense: true,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMessageEditor() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text(
              'Your message',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (_isSaving)
                  const Padding(
                    padding: EdgeInsets.only(right: 8),
                    child: Text(
                      'Saving...',
                      style: TextStyle(fontSize: 11, color: Colors.grey),
                    ),
                  )
                else if (_lastSavedTime != null)
                  const Padding(
                    padding: EdgeInsets.only(right: 8),
                    child: Text(
                      'Saved',
                      style: TextStyle(fontSize: 11, color: Colors.green),
                    ),
                  ),
                Text(
                  '${_messageController.text.length} chars',
                  style: TextStyle(fontSize: 11, color: Colors.grey[600]),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 2),
        Text(
          'Review and edit this before sending.',
          style: TextStyle(fontSize: 12, color: Colors.grey[600]),
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _messageController,
          maxLines: 5,
          onChanged: _onMessageChanged,
          decoration: const InputDecoration(
            hintText: 'Write a birthday greeting or tap Create message...',
          ),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 4,
          children: [
            ActionChip(
              avatar: const Icon(Icons.compress, size: 16),
              label: const Text('Shorten'),
              onPressed: _isGenerating
                  ? null
                  : () => _rewriteMessage(length: MessageLength.short),
            ),
            ActionChip(
              avatar: const Icon(Icons.expand, size: 16),
              label: const Text('Expand'),
              onPressed: _isGenerating
                  ? null
                  : () => _rewriteMessage(length: MessageLength.expanded),
            ),
            ActionChip(
              avatar: const Icon(Icons.translate, size: 16),
              label: const Text('Translate'),
              onPressed: _isGenerating ? null : _showTranslateDialog,
            ),
            ActionChip(
              avatar: const Icon(Icons.casino_outlined, size: 16),
              label: const Text('Variations'),
              onPressed: _isGenerating ? null : _generateVariations,
            ),
          ],
        ),
      ],
    );
  }
}
