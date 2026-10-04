/// Message Studio for AI generation, editing, and delivery handoff (SSOT §16).
library;

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
  const MessageStudioScreen({super.key, required this.birthdayId});

  final String birthdayId;

  @override
  ConsumerState<MessageStudioScreen> createState() =>
      _MessageStudioScreenState();
}

class _MessageStudioScreenState extends ConsumerState<MessageStudioScreen> {
  final TextEditingController _messageController = TextEditingController();
  final TextEditingController _customInstructionController =
      TextEditingController();

  Birthday? _birthday;
  Person? _person;
  MessageDraft? _draft;
  bool _isLoading = true;
  bool _isGenerating = false;
  String? _errorMessage;

  MessageTone _selectedTone = MessageTone.warm;
  MessageLength _selectedLength = MessageLength.standard;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  @override
  void dispose() {
    _messageController.dispose();
    _customInstructionController.dispose();
    super.dispose();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);

    final bRepo = ref.read(birthdaysRepositoryProvider);
    final pRepo = ref.read(peopleRepositoryProvider);
    final dRepo = ref.read(draftsRepositoryProvider);

    final birthday = await bRepo.getBirthday(widget.birthdayId);
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
        birthdayId: widget.birthdayId,
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
            widget.birthdayId,
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
      birthdayId: widget.birthdayId,
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
            .warning('MessageStudio', 'Could not launch WhatsApp directly', error: e);
      }

      // Track handoff status only when launch succeeds; otherwise action required (SSOT §9)
      if (launched) {
        await ref
            .read(birthdaysRepositoryProvider)
            .updateBirthdayStatus(widget.birthdayId, BirthdayStatus.handedOff);
      } else {
        await ref
            .read(birthdaysRepositoryProvider)
            .updateBirthdayStatus(widget.birthdayId, BirthdayStatus.failed);
      }

      if (mounted) {
        _showHandoffConfirmationDialog(
          handoff.uri.toString(),
          wasLaunched: launched,
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

  void _showHandoffConfirmationDialog(
    String waUrl, {
    required bool wasLaunched,
  }) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Row(
            children: [
              Icon(
                wasLaunched ? Icons.mark_chat_read_outlined : Icons.open_in_new,
                color: const Color(0xFF2D5A46),
              ),
              const SizedBox(width: 8),
              const Text('WhatsApp Handoff'),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                wasLaunched
                    ? 'WhatsApp opened with your pre-filled message. Once you have tapped Send in WhatsApp, confirm below to update your celebration tracker.'
                    : 'WhatsApp could not be opened automatically. You can use the link below or copy the message to your clipboard:',
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
                  waUrl,
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
                      widget.birthdayId,
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

          // Error banner if any
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
              child: Row(
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
        const Text(
          'Message Body (User review required):',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _messageController,
          maxLines: 5,
          decoration: const InputDecoration(
            hintText: 'Write a birthday greeting or tap Generate with AI...',
          ),
        ),
      ],
    );
  }
}
