/// Message Studio for AI generation, editing, and delivery handoff (SSOT §16).
library;

import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/delivery/data/whatsapp_handoff_builder.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/jobs/application/ai_job_handler.dart';
import 'package:ai_birthday/features/jobs/domain/job.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/message_studio/domain/repositories/drafts_repository.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';
import 'package:ai_birthday/ui/design_system/app_tokens.dart';

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
  // AI work is a durable background job now: this flag tracks whether the
  // active job (or a synchronous variations run) is in flight. The job row is
  // the source of truth — the widget follows it even across re-entry.
  bool _isGenerating = false;
  String? _retryingLabel;
  String? _errorMessage;
  AppFailureCode? _lastFailureCode;

  // Durable-job bookkeeping for the birthday's latest ai_generate job.
  StreamSubscription<JobRecord?>? _aiJobSub;
  String? _lastHandledJobId;
  JobStatus? _lastHandledJobStatus;
  // Set when THIS screen visit enqueues a job: gates "ours" snackbars and
  // the stale-result copy ("you edited while drafting").
  String? _lastEnqueuedJobId;
  int _enqueuedRevision = 0;
  String? _pendingJobLabel;
  String? _pendingStaleLabel;

  // Captured at load: dispose() runs after ref becomes unusable, but the
  // final draft save still needs the repository.
  // ponytail: captured repo instead of ProviderContainer access; revisit if more providers are needed in dispose
  DraftsRepository? _draftsRepo;

  Timer? _autosaveTimer;
  bool _isSaving = false;
  DateTime? _lastSavedTime;
  // Single-flight guard: stops a rapid double-tap from launching the external
  // delivery app twice (two SMS composers, two handoff records).
  bool _isSending = false;
  // Bumped on every user edit. AI results only apply if no edit happened
  // while the request was in flight (stale-response guard).
  int _editRevision = 0;

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
    _aiJobSub?.cancel();
    _autosaveTimer?.cancel();
    if (_messageController.text.isNotEmpty && _person != null) {
      _saveDraft(status: _draft?.status ?? DraftStatus.draft);
    }
    _messageController.dispose();
    _customInstructionController.dispose();
    super.dispose();
  }

  void _onMessageChanged(String text) {
    _editRevision++;
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
    _draftsRepo = dRepo;

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
      final targetId = birthday.id;
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

      // Follow the birthday's durable generation job: in-flight states
      // ("Drafting…", "Retrying…"), applied results, and failures all come
      // from the job row so the studio stays truthful across re-entry. Seat
      // the state with a one-shot read, then ride the worker's in-process
      // transition stream (a plain broadcast, so no drift query timers).
      _aiJobSub?.cancel();
      final latest = await ref
          .read(jobsRepositoryProvider)
          .latestJob(JobTypes.aiGenerate, targetId);
      if (latest != null && mounted) _handleJobEvent(latest);
      _aiJobSub = ref
          .read(jobWorkerProvider)
          .events
          .where(
            (job) =>
                job.type == JobTypes.aiGenerate && job.subjectId == targetId,
          )
          .listen(_handleJobEvent);
    } else {
      setState(() {
        _errorMessage = 'Birthday event not found.';
        _isLoading = false;
      });
    }
  }

  /// Primary "Generate with AI" action and the error-banner Retry path.
  ///
  /// Generation now runs as a durable background job: the tap only enqueues
  /// (fast ack), the worker generates and persists the drafted message even
  /// if the user leaves the screen, and [._handleJobEvent] applies the result
  /// when it succeeds. [forceNano] retries with the on-device provider.
  Future<void> _generateWithAi({bool forceNano = false}) async {
    await _enqueueGenerate(
      customInstruction: _customInstructionController.text.trim().isNotEmpty
          ? _customInstructionController.text.trim()
          : null,
      existingMessage: _messageController.text.trim().isNotEmpty
          ? _messageController.text.trim()
          : null,
      forceNano: forceNano,
      successLabel: 'Message drafted with AI ✨',
    );
  }

  /// Enqueues a durable `ai_generate` job. The job payload captures the
  /// non-secret inputs plus the draft's `updatedAt` at enqueue time; the
  /// worker discards the result if the draft changed in the meantime (the
  /// stale-response guard that used to live in this widget now lives in the
  /// job handler, so it also protects drafts saved from other screens).
  Future<void> _enqueueGenerate({
    MessageTone? tone,
    MessageLength? length,
    String? customInstruction,
    String? existingMessage,
    String? targetLanguage,
    bool forceNano = false,
    String? successLabel,
    String? staleLabel,
  }) async {
    if (_person == null || _birthday == null) return;
    final birthdayId = _activeBirthdayId;

    // Snapshot the persisted draft so the worker can tell "user edited while
    // the job was in flight" from "draft unchanged".
    final revisedDraft = await ref
        .read(draftsRepositoryProvider)
        .getDraftForBirthday(birthdayId);

    final job = await ref
        .read(jobWorkerProvider)
        .enqueue(
          type: JobTypes.aiGenerate,
          subjectId: birthdayId,
          payload: jsonEncode(
            AiJobPayload(
              birthdayId: birthdayId,
              personId: _person!.id,
              tone: tone ?? _selectedTone,
              length: length ?? _selectedLength,
              customInstruction: customInstruction,
              existingMessage: existingMessage,
              targetLanguage: targetLanguage,
              targetCycleYear: _birthday!.cycleYear,
              forceNano: forceNano,
              draftUpdatedAt: revisedDraft?.updatedAt.toIso8601String(),
            ).toJson(),
          ),
          maxAttempts: 3,
        );
    if (!mounted) return;
    setState(() {
      _lastEnqueuedJobId = job.id;
      _enqueuedRevision = _editRevision;
      _pendingJobLabel = successLabel ?? 'Message drafted with AI ✨';
      _pendingStaleLabel =
          staleLabel ??
          'You edited the message while drafting. Your edits were kept — review before sending.';
      _errorMessage = null;
      _lastFailureCode = null;
      _isGenerating = true;
      _retryingLabel = null;
    });
  }

  /// Follows the birthday's durable generation job.
  void _handleJobEvent(JobRecord? job) {
    if (job == null || !mounted) return;
    // Only react to actual transitions, not re-emissions of the same state.
    if (job.id == _lastHandledJobId && job.status == _lastHandledJobStatus) {
      return;
    }
    _lastHandledJobId = job.id;
    _lastHandledJobStatus = job.status;

    switch (job.status) {
      case JobStatus.queued || JobStatus.running:
        setState(() {
          _isGenerating = true;
          _retryingLabel = null;
          _errorMessage = null;
        });
      case JobStatus.retrying:
        setState(() {
          _isGenerating = true;
          _retryingLabel = 'Retrying (${job.attempts + 1}/${job.maxAttempts})';
          _errorMessage = null;
        });
      case JobStatus.succeeded:
        setState(() {
          _isGenerating = false;
          _retryingLabel = null;
        });
        _onAiJobSucceeded(job);
      case JobStatus.failed:
        setState(() {
          _isGenerating = false;
          _retryingLabel = null;
          _errorMessage =
              job.errorMessage ?? 'AI drafting failed. Please retry.';
          _lastFailureCode = AppFailureCode.values
              .cast<AppFailureCode?>()
              .firstWhere(
                (code) => code?.name == job.errorCode,
                orElse: () => null,
              );
        });
      case JobStatus.canceled:
        setState(() {
          _isGenerating = false;
          _retryingLabel = null;
        });
    }
  }

  /// A generation job succeeded. The worker already persisted the draft, so
  /// this only updates the visible editor and reports honestly:
  /// - result applies if the user has not edited since enqueue;
  /// - otherwise the newest text wins and the user is told their edits were
  ///   kept (same stale-response guarantee as before, now durable).
  Future<void> _onAiJobSucceeded(JobRecord job) async {
    final ours = job.id == _lastEnqueuedJobId;
    final draft = await ref
        .read(draftsRepositoryProvider)
        .getDraftForBirthday(_activeBirthdayId);
    if (!mounted) return;

    final userEdited = _enqueuedRevision != _editRevision;
    if (draft != null && !userEdited) {
      setState(() {
        _messageController.text = draft.body;
        _draft = draft;
        _selectedTone = draft.tone;
        _selectedLength = draft.length;
      });
      if (ours) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(_pendingJobLabel ?? 'Message drafted with AI ✨'),
          ),
        );
      }
    } else if (ours && userEdited) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_pendingStaleLabel ?? 'Your edits were kept.')),
      );
    }
  }

  Future<void> _saveDraft({DraftStatus status = DraftStatus.draft}) async {
    if (_person == null) return;
    final repo = _draftsRepo;
    if (repo == null) return;
    final draftId = _draft?.id ?? _activeBirthdayId;
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

    await repo.saveDraft(updatedDraft);
    if (mounted) {
      setState(() {
        _draft = updatedDraft;
      });
    }
  }

  Future<void> _handleWhatsAppSend() async {
    if (_isSending) return;
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

    setState(() => _isSending = true);
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
        // Persist the launch as evidence (audit 03 P1-1): History renders
        // "Opened in WhatsApp" from this record, never from status alone.
        await ref
            .read(deliveryEventsRepositoryProvider)
            .recordHandoff(
              birthdayId: _activeBirthdayId,
              channel: DeliveryChannel.whatsapp,
              at: DateTime.now(),
            );
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
    } catch (e, st) {
      ref
          .read(loggerProvider)
          .error(
            'MessageStudio',
            'Unexpected error launching WhatsApp handoff',
            error: e,
            stackTrace: st,
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not open the delivery app.')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSending = false);
      }
    }
  }

  Future<void> _handleSmsSend() async {
    if (_isSending) return;
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

    setState(() => _isSending = true);
    try {
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
        await ref
            .read(deliveryEventsRepositoryProvider)
            .recordHandoff(
              birthdayId: _activeBirthdayId,
              channel: DeliveryChannel.sms,
              at: DateTime.now(),
            );
      } else {
        // Any channel that fails to open must surface as action-required, same
        // as WhatsApp (audit 03 AC4/P2-2).
        await ref
            .read(birthdaysRepositoryProvider)
            .updateBirthdayStatus(_activeBirthdayId, BirthdayStatus.failed);
      }

      if (mounted) {
        _showHandoffConfirmationDialog(
          'Recipient: $phone',
          wasLaunched: launched,
          channelName: 'SMS',
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSending = false);
      }
    }
  }

  Future<void> _handleShareSend() async {
    if (_isSending) return;
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

    setState(() => _isSending = true);
    try {
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
        await ref
            .read(deliveryEventsRepositoryProvider)
            .recordHandoff(
              birthdayId: _activeBirthdayId,
              channel: DeliveryChannel.share,
              at: DateTime.now(),
            );
      } else {
        await ref
            .read(birthdaysRepositoryProvider)
            .updateBirthdayStatus(_activeBirthdayId, BirthdayStatus.failed);
      }

      if (mounted) {
        _showHandoffConfirmationDialog(
          'Shared via Android chooser',
          wasLaunched: launched,
          channelName: 'Share Sheet',
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSending = false);
      }
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

    final label = languageName != null
        ? 'Translated to $languageName ✨'
        : (length == MessageLength.short
              ? 'Message shortened ✂️'
              : length == MessageLength.expanded
              ? 'Message expanded 📝'
              : 'Message refined ✨');
    await _enqueueGenerate(
      length: length,
      customInstruction: customInstruction,
      existingMessage: currentText,
      targetLanguage: targetLanguage,
      successLabel: label,
      staleLabel:
          'You edited the message while rewriting. Your edits were kept — review before sending.',
    );
  }

  Future<void> _generateVariations() async {
    if (_person == null) return;
    setState(() {
      _isGenerating = true;
      _errorMessage = null;
    });

    final revision = _editRevision;
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
            targetCycleYear: _birthday?.cycleYear,
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
        backgroundColor: Theme.of(context).colorScheme.surface,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
        builder: (ctx) => AppBottomSheet(
          children: [
            const Row(
              children: [
                Icon(Icons.auto_awesome, color: AppColors.accentAmber),
                SizedBox(width: 8),
                Text(
                  'Select a Variation',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
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
                            Text(
                              'Tap to choose',
                              style: TextStyle(
                                fontSize: 12,
                                color: context.colors.textSecondary,
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
      );

      if (selected != null && mounted) {
        // Don't let a picked variation clobber edits made while the three
        // variations were generating or the sheet was open.
        if (revision == _editRevision) {
          _messageController.text = selected;
          await _saveDraft();
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Variation applied! ✨')),
            );
          }
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'You edited the message while preparing variations. Your edits were kept.',
              ),
            ),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e is AppFailure
              ? '${e.message}: ${e.detail ?? ''} ${e.action ?? ''}'.trim()
              : 'Could not generate variations. You can compose your message manually or retry.';
          _lastFailureCode = e is AppFailure ? e.code : null;
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

    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => AppBottomSheet(
        children: [
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 8),
            child: Text(
              'Translate Greeting To',
              textAlign: TextAlign.center,
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
                  style: Theme.of(context).textTheme.bodySmall,
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
            if (!wasLaunched)
              TextButton(
                onPressed: () {
                  Clipboard.setData(
                    ClipboardData(text: _messageController.text.trim()),
                  );
                  Navigator.of(context).pop();
                },
                child: const Text('Copy message'),
              ),
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

  /// Confirm-only entry (audit 03 AC3/P1-3): a handed-off birthday's primary
  /// action asks "Did you send it?" WITHOUT re-launching the external app.
  Future<void> _confirmFromHandedOff() async {
    HapticFeedback.lightImpact();
    final repo = ref.read(deliveryEventsRepositoryProvider);
    final handoff = await repo.latestHandoffForBirthday(_activeBirthdayId);
    final channel = handoff?.channel.displayName ?? 'delivery app';
    if (!mounted) return;
    _showHandoffConfirmationDialog(
      'Opened via $channel — ${_messageController.text.trim()}',
      wasLaunched: true,
      channelName: channel,
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

  /// Opens the contact editor so the recipient can get a usable phone number.
  void _openPhoneSetup() {
    HapticFeedback.lightImpact();
    context.push('/people/edit/${_person!.id}');
  }

  /// Replaces the AI controls when the account is not entitled (audit H).
  Widget _buildAiLockedNotice() {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.star_outline, color: theme.colorScheme.tertiary),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                'AI drafting (tones, lengths, rewrite and translate) is a Pro feature. '
                'Compose your message below — writing and delivery stay free.',
                style: TextStyle(
                  fontSize: 13,
                  height: 1.35,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    if (_person == null || _birthday == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Message Studio')),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
                child: Text(
                  _errorMessage ?? 'Birthday not found',
                  textAlign: TextAlign.center,
                ),
              ),
              const SizedBox(height: AppSpacing.md),
              // Recovery action: the birthday was deleted (or the deep link
              // went stale), so hand the user back to where birthdays live.
              FilledButton.icon(
                onPressed: () => context.go('/people'),
                icon: const Icon(Icons.people_outline),
                label: const Text('Go to People'),
              ),
            ],
          ),
        ),
      );
    }

    final person = _person!;
    final birthday = _birthday!;
    final entitlement = ref.watch(entitlementProvider);
    // A saved personal Gemini key unlocks drafting without Pro (BYOK product
    // decision), so only lock when there is neither a subscription nor a key.
    // The router enforces the same rule, so the UI never offers a dead button.
    final ownKey = ref.watch(hasOwnGeminiKeyProvider).valueOrNull ?? false;
    final aiLocked = !entitlement.canUseAi && !ownKey;
    final isCompleted = birthday.status == BirthdayStatus.completed;
    final isHandedOff = birthday.status == BirthdayStatus.handedOff;
    final rawPhone = person.phoneNumber?.trim() ?? '';
    final hasUsablePhone =
        WhatsAppHandoffBuilder.sanitizePhoneNumber(rawPhone) != null;
    final phoneMissing = rawPhone.isEmpty;
    final phoneInvalid = !phoneMissing && !hasUsablePhone;

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
        padding: EdgeInsets.fromLTRB(
          16,
          16,
          16,
          AppSpacing.bottomClearance + MediaQuery.viewInsetsOf(context).bottom,
        ),
        children: [
          // Recipient Context Card
          _buildRecipientCard(
            phoneMissing: phoneMissing,
            phoneInvalid: phoneInvalid,
            isCompleted: isCompleted,
          ),
          const SizedBox(height: AppSpacing.md),

          // Tone & Length Controls (AI personalization; hidden when AI is locked)
          if (!aiLocked) ...[
            _buildControlsCard(),
            const SizedBox(height: AppSpacing.md),
          ] else ...[
            _buildAiLockedNotice(),
            const SizedBox(height: AppSpacing.md),
          ],

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
                        onPressed: () => _generateWithAi(),
                        style: TextButton.styleFrom(
                          visualDensity: VisualDensity.compact,
                        ),
                      ),
                      // On a credential failure, offer the on-device fallback
                      // (audit 02 AC P1-1): retry with Gemini Nano forced.
                      if (_lastFailureCode ==
                          AppFailureCode.aiCredentialInvalid)
                        TextButton.icon(
                          icon: const Icon(Icons.phone_android, size: 16),
                          label: const Text('Use on-device AI (Gemini Nano)'),
                          onPressed: () => _generateWithAi(forceNano: true),
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
          _buildMessageEditor(showAiTools: !aiLocked),
          const SizedBox(height: AppSpacing.lg),

          // Action Buttons
          ResponsiveActionBar(
            primary: isCompleted
                ? FilledButton.icon(
                    onPressed: () {
                      HapticFeedback.lightImpact();
                      context.go('/history');
                    },
                    icon: const Icon(Icons.history),
                    label: const Text('View in History'),
                  )
                : isHandedOff
                ? // Already handed off: ask to confirm, never re-launch
                  // (audit 03 AC3/C2 — works for any channel, not just WhatsApp).
                  FilledButton.icon(
                    onPressed: _confirmFromHandedOff,
                    icon: const Icon(Icons.check_circle_outline),
                    label: const Text('Confirm Sent'),
                  )
                : !hasUsablePhone
                ? FilledButton.icon(
                    onPressed: _openPhoneSetup,
                    icon: const Icon(Icons.person_add_alt_1),
                    label: Text(
                      phoneMissing ? 'Add Phone Number' : 'Fix Phone Number',
                    ),
                  )
                : FilledButton.icon(
                    onPressed: _isSending ? null : _handleWhatsAppSend,
                    icon: const Icon(Icons.chat),
                    label: const Text('Send on WhatsApp'),
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.whatsappGreen,
                      foregroundColor: Colors.white,
                    ),
                  ),
            secondary: aiLocked
                ? FilledButton.tonalIcon(
                    onPressed: () => context.push('/settings'),
                    icon: const Icon(Icons.star_outline),
                    label: const Text('Unlock with Pro'),
                  )
                : FilledButton.tonalIcon(
                    onPressed: _isGenerating ? null : _generateWithAi,
                    icon: _isGenerating
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.auto_awesome),
                    label: Text(
                      _isGenerating
                          ? (_retryingLabel ?? 'Drafting...')
                          : 'Generate with AI',
                    ),
                  ),
          ),
          const SizedBox(height: AppSpacing.sm),
          // Alternate delivery channels (SSOT §10)
          Wrap(
            spacing: 8,
            runSpacing: 8,
            alignment: WrapAlignment.center,
            children: [
              if (hasUsablePhone)
                OutlinedButton.icon(
                  onPressed: _isSending ? null : _handleSmsSend,
                  icon: const Icon(Icons.sms_outlined, size: 18),
                  label: const Text('Send via SMS'),
                ),
              OutlinedButton.icon(
                onPressed: _isSending ? null : _handleShareSend,
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

  Widget _buildRecipientCard({
    required bool phoneMissing,
    required bool phoneInvalid,
    required bool isCompleted,
  }) {
    final theme = Theme.of(context);
    final person = _person!;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(child: Text(person.name[0])),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        person.name,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        '${person.relationship.displayName} • ${person.phoneNumber ?? 'No phone'}',
                        style: TextStyle(
                          fontSize: 13,
                          color: context.colors.textSecondary,
                        ),
                      ),
                      if (phoneMissing)
                        Text(
                          'No phone number yet — add one for WhatsApp delivery.',
                          style: TextStyle(
                            fontSize: 12,
                            color: theme.colorScheme.tertiary,
                            fontWeight: FontWeight.w600,
                          ),
                        )
                      else if (phoneInvalid)
                        Text(
                          'This number needs a country code (e.g. +1 555 123 4567) for WhatsApp.',
                          style: TextStyle(
                            fontSize: 12,
                            color: theme.colorScheme.tertiary,
                            fontWeight: FontWeight.w600,
                          ),
                        )
                      else if (isCompleted)
                        Text(
                          // WhatsApp is replaced by 'View in History' once the
                          // celebration is marked sent, so don't promise a
                          // resend path that no longer exists on this screen.
                          '✓ Celebration marked as sent.',
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.green[800],
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
            if (person.importantFacts.isNotEmpty) ...[
              const Divider(height: 24),
              const Text(
                'Known Facts (User-verified):',
                style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 4),
              Text(
                person.importantFacts.join(' • '),
                style: TextStyle(
                  fontSize: 12,
                  color: context.colors.textSecondary,
                ),
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

  Widget _buildMessageEditor({required bool showAiTools}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          alignment: WrapAlignment.spaceBetween,
          crossAxisAlignment: WrapCrossAlignment.center,
          spacing: 8,
          runSpacing: 4,
          children: [
            const Text(
              'Your message',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (_isSaving)
                  Padding(
                    padding: EdgeInsets.only(right: 8),
                    child: Text(
                      'Saving...',
                      style: TextStyle(
                        fontSize: 11,
                        color: context.colors.textSecondary,
                      ),
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
                  style: TextStyle(
                    fontSize: 11,
                    color: context.colors.textSecondary,
                  ),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 2),
        Text(
          'Review and edit this before sending.',
          style: TextStyle(fontSize: 12, color: context.colors.textSecondary),
        ),
        const SizedBox(height: 8),
        Semantics(
          label: 'Your message',
          child: TextField(
            controller: _messageController,
            maxLines: 5,
            onChanged: _onMessageChanged,
            decoration: const InputDecoration(
              hintText: 'Write a birthday greeting or tap Generate with AI...',
            ),
          ),
        ),
        if (showAiTools) ...[
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
      ],
    );
  }
}
