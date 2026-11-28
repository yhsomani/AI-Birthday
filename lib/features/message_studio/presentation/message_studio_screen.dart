/// Message Studio for AI generation, editing, and delivery handoff (legacy SSOT §16 (PRODUCT.md §16.7)).
library;

import 'dart:async';
import 'dart:convert';

import 'package:crypto/crypto.dart' as crypto;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/config/product_capabilities.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/delivery/data/whatsapp_handoff_builder.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_handoff.dart';
import 'package:ai_birthday/features/jobs/application/ai_job_handler.dart';
import 'package:ai_birthday/features/jobs/domain/job.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/message_studio/domain/repositories/drafts_repository.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/people/domain/person_enums.dart';
import 'package:ai_birthday/features/delivery/application/birthday_sms_service.dart';
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

/// Thrown by [_MessageStudioScreenState._saveDraft] when the stored draft
/// changed after this screen loaded it (for example an AI result was applied).
/// The editor text is kept and the user has already been told.
class _DraftSaveConflict implements Exception {
  const _DraftSaveConflict();
}

class _MessageStudioScreenState extends ConsumerState<MessageStudioScreen>
    with WidgetsBindingObserver {
  final TextEditingController _messageController = TextEditingController();
  final TextEditingController _customInstructionController =
      TextEditingController();

  Birthday? _birthday;
  Person? _person;
  MessageDraft? _draft;
  // Last draft row known to be persisted (with its repository revision). The
  // optimistic-concurrency token for saves; `_draft` may be edited in memory.
  MessageDraft? _persistedDraft;
  bool _isLoading = true;
  // AI work is a durable background job now: this flag tracks whether the
  // active job (or a synchronous variations run) is in flight. The job row is
  // the source of truth — the widget follows it even across re-entry.
  bool _isGenerating = false;
  // Variations run outside the durable queue; tracked separately so finishing
  // them never clears the flag of a concurrently running job.
  bool _variationsLoading = false;
  bool get _aiBusy => _isGenerating || _variationsLoading;
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

  // Captured at load: dispose() runs after ref becomes unusable.
  DraftsRepository? _draftsRepo;

  Timer? _autosaveTimer;
  bool _isSaving = false;
  Future<void>? _saveFuture;
  bool _hasUnsavedChanges = false;
  bool _saveFailed = false;
  bool _discardingUnsavedChanges = false;
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
      // Nothing to flush: a no-op write would still bump updatedAt/revision and
      // make a queued AI result look stale.
      if (!_hasUnsavedChanges) return;
      _autosaveTimer?.cancel();
      _performAutosave();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _aiJobSub?.cancel();
    _autosaveTimer?.cancel();
    _messageController.dispose();
    _customInstructionController.dispose();
    super.dispose();
  }

  void _onMessageChanged(String text) {
    _editRevision++;
    _hasUnsavedChanges = true;
    if (_draft?.status == DraftStatus.confirmedSent) {
      final now = DateTime.now();
      _draft = _draft?.copyWith(
        id: '$_activeBirthdayId-revision-${now.microsecondsSinceEpoch}',
        status: DraftStatus.draft,
        createdAt: now,
        updatedAt: now,
      );
      _birthday = _birthday?.copyWith(status: BirthdayStatus.messageDrafted);
    } else if (ProductCapabilities.automaticSms &&
        _draft?.status == DraftStatus.approvedForAutoSms) {
      _draft = _draft?.copyWith(status: DraftStatus.draft);
      _reconcileAutoSms();
    }
    setState(() {});
    _scheduleAutosave();
  }

  void _scheduleAutosave() {
    _autosaveTimer?.cancel();
    _autosaveTimer = Timer(const Duration(milliseconds: 750), () {
      _performAutosave();
    });
  }

  void _onDraftPreferencesChanged(VoidCallback update) {
    setState(update);
    _editRevision++;
    _hasUnsavedChanges = true;
    _scheduleAutosave();
  }

  Future<void> _performAutosave() async {
    final inFlight = _saveFuture;
    if (inFlight != null) return inFlight;
    final save = _performAutosaveOnce();
    _saveFuture = save;
    try {
      await save;
    } finally {
      if (identical(_saveFuture, save)) _saveFuture = null;
    }
  }

  Future<void> _performAutosaveOnce() async {
    if (_person == null || !mounted || !_hasUnsavedChanges) return;
    final savingRevision = _editRevision;
    setState(() => _isSaving = true);
    try {
      await _saveDraft(
        status: _draft?.status ?? DraftStatus.draft,
        skipIfUnchanged: true,
      );
      if (mounted) {
        setState(() {
          _isSaving = false;
          _saveFailed = false;
          _hasUnsavedChanges = savingRevision != _editRevision;
          _lastSavedTime = DateTime.now();
          _errorMessage = null;
          _lastFailureCode = null;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isSaving = false;
          _saveFailed = true;
          _hasUnsavedChanges = true;
          _lastSavedTime = null;
          _errorMessage = e is _DraftSaveConflict
              ? _conflictMessage
              : 'Your latest message could not be saved. It is still in the editor; use Save now to retry.';
        });
      }
    }
  }

  static const String _conflictMessage =
      'This draft was changed elsewhere (for example an AI result was applied) '
      'after you started editing. Your text is still in the editor and was not '
      'saved over it. Use Save now to keep your version.';

  Future<bool> _saveBeforeLeaving() async {
    _autosaveTimer?.cancel();
    await _performAutosave();
    return !_saveFailed && !_hasUnsavedChanges;
  }

  Future<void> _discardAndLeave() async {
    final discard = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Discard unsaved changes?'),
        content: const Text(
          'Your latest edits could not be saved. Leaving now will discard them.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Keep editing'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Discard edits'),
          ),
        ],
      ),
    );
    if (discard != true || !mounted) return;
    _autosaveTimer?.cancel();
    setState(() {
      _discardingUnsavedChanges = true;
      _hasUnsavedChanges = false;
      _saveFailed = false;
    });
    await WidgetsBinding.instance.endOfFrame;
    if (mounted) Navigator.of(context).pop();
  }

  String get _activeBirthdayId => _birthday?.id ?? widget.birthdayId ?? '';

  Future<void> _loadData() async {
    if (!mounted) return;
    setState(() => _isLoading = true);
    try {
      final bRepo = ref.read(birthdaysRepositoryProvider);
      final pRepo = ref.read(peopleRepositoryProvider);
      final dRepo = ref.read(draftsRepositoryProvider);
      _draftsRepo = dRepo;

      Birthday? birthday;
      if (widget.birthdayId != null && widget.birthdayId!.isNotEmpty) {
        birthday = await bRepo.getBirthday(widget.birthdayId!);
        if (!mounted) return;
        birthday ??= await bRepo.getBirthdayForPerson(widget.birthdayId!);
      }
      if (!mounted) return;
      if (birthday == null &&
          widget.personId != null &&
          widget.personId!.isNotEmpty) {
        birthday = await bRepo.getBirthdayForPerson(widget.personId!);
      }
      if (!mounted) return;

      if (birthday == null) {
        setState(() {
          _errorMessage = 'Birthday event not found.';
          _isLoading = false;
        });
        return;
      }

      final targetId = birthday.id;
      final person = await pRepo.getPersonById(birthday.personId);
      if (!mounted) return;
      if (person == null || !person.hasResolvableBirthday) {
        setState(() {
          _errorMessage = person == null
              ? 'Recipient not found.'
              : 'Choose a non-leap-year celebration date before opening Studio.';
          _isLoading = false;
        });
        return;
      }
      final draft = await dRepo.getDraftForBirthday(birthday.id);
      if (!mounted) return;

      setState(() {
        _birthday = birthday;
        _person = person;
        _draft = draft;
        _persistedDraft = draft;
        if (draft != null) {
          _messageController.text = draft.body;
          _selectedTone = draft.tone;
          _selectedLength = draft.length;
        } else {
          _selectedTone = person.preferredTone;
        }
        _isLoading = false;
      });

      _aiJobSub?.cancel();
      final jobsRepository = ref.read(jobsRepositoryProvider);
      final worker = ref.read(jobWorkerProvider);
      _aiJobSub = worker.events
          .where(
            (job) =>
                job.type == JobTypes.aiGenerate && job.subjectId == targetId,
          )
          .listen(_handleJobEvent);
      final latest = await jobsRepository.latestJob(
        JobTypes.aiGenerate,
        targetId,
      );
      if (!mounted) return;
      if (latest != null &&
          (latest.status == JobStatus.queued ||
              latest.status == JobStatus.running ||
              latest.status == JobStatus.retrying)) {
        _handleJobEvent(latest);
      }
    } catch (error, stackTrace) {
      if (!mounted) return;
      ref
          .read(loggerProvider)
          .error(
            'MessageStudio',
            'Could not load birthday studio data',
            error: error,
            stackTrace: stackTrace,
          );
      setState(() {
        _errorMessage = 'Could not load this birthday. Try again.';
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
      successLabel: 'Message drafted with AI.',
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
    if (_messageController.text.isNotEmpty || _draft != null) {
      try {
        await _saveDraft(status: DraftStatus.draft, skipIfUnchanged: true);
      } on _DraftSaveConflict {
        return;
      } catch (_) {
        if (mounted) {
          setState(() {
            _errorMessage =
                'Save this draft before asking AI to generate. Your text has been kept in the editor.';
          });
        }
        return;
      }
    }
    final birthdayId = _activeBirthdayId;

    // Snapshot the persisted draft so the worker can tell "user edited while
    // the job was in flight" from "draft unchanged".
    final revisedDraft = await ref
        .read(draftsRepositoryProvider)
        .getDraftForBirthday(birthdayId);

    final JobRecord job;
    try {
      job = await ref
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
                rewriteExisting:
                    existingMessage != null &&
                    existingMessage.trim().isNotEmpty,
                targetLanguage: targetLanguage,
                targetCycleYear: _birthday!.cycleYear,
                forceNano: forceNano,
                draftUpdatedAt: revisedDraft?.updatedAt.toIso8601String(),
                draftRevision: revisedDraft?.revision,
              ).toJson(),
            ),
            maxAttempts: 3,
          );
    } catch (error, stackTrace) {
      if (!mounted) return;
      ref
          .read(loggerProvider)
          .error(
            'MessageStudio',
            'Could not enqueue AI generation',
            error: error,
            stackTrace: stackTrace,
          );
      setState(() {
        _errorMessage =
            'Another AI request is already in progress for this birthday. Wait for it to finish before changing the request.';
      });
      return;
    }
    if (!mounted) return;
    setState(() {
      _lastEnqueuedJobId = job.id;
      _enqueuedRevision = _editRevision;
      _pendingJobLabel = successLabel ?? 'Message drafted with AI.';
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
    // The worker deliberately did not apply its result: report neutrally, never
    // as a success, and never pull stale text over the user's editor.
    if (job.wasDropped) {
      if (draft != null) _persistedDraft = draft;
      if (draft != null && !userEdited && !_hasUnsavedChanges) {
        setState(() {
          _messageController.text = draft.body;
          _draft = draft;
          _selectedTone = draft.tone;
          _selectedLength = draft.length;
        });
      }
      if (ours) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              userEdited
                  ? (_pendingStaleLabel ?? 'Your edits were kept.')
                  : 'AI result not applied because the draft changed. Generate again if you still want it.',
            ),
          ),
        );
      }
      return;
    }

    if (draft != null) _persistedDraft = draft;
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
            content: Text(_pendingJobLabel ?? 'Message drafted with AI.'),
          ),
        );
      }
    } else if (ours && userEdited) {
      if (draft != null) {
        setState(() {
          _draft = draft;
          _birthday = _birthday?.copyWith(
            draftId: draft.id,
            status: BirthdayStatus.messageDrafted,
          );
        });
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_pendingStaleLabel ?? 'Your edits were kept.')),
      );
    }
  }

  /// Persists the editor as the birthday's draft.
  ///
  /// Writes are optimistic: the stored row must still be the one this screen
  /// last loaded/saved ([_persistedDraft] revision), otherwise nothing is
  /// written, the editor text is kept and [_DraftSaveConflict] is thrown.
  /// [skipIfUnchanged] avoids no-op writes (which would bump `updatedAt` and
  /// revision and make a queued AI result look stale). A brand-new draft is
  /// never created from an empty editor.
  Future<void> _saveDraft({
    DraftStatus status = DraftStatus.draft,
    bool skipIfUnchanged = false,
  }) async {
    if (_person == null) return;
    final repo = _draftsRepo;
    if (repo == null) return;
    final birthday = _birthday;
    if (birthday == null) return;
    final savedRevision = _editRevision;
    final persisted = _persistedDraft;
    final body = _messageController.text;

    // Opening Studio and leaving must not create an empty "draft" or flip the
    // birthday to "Message drafted". Clearing an existing draft is still saved.
    if (persisted == null && body.trim().isEmpty) {
      if (_editRevision == savedRevision) _hasUnsavedChanges = false;
      return;
    }
    if (skipIfUnchanged &&
        persisted != null &&
        persisted.status != DraftStatus.confirmedSent &&
        persisted.status == status &&
        persisted.body == body &&
        persisted.tone == _selectedTone &&
        persisted.length == _selectedLength) {
      if (mounted && _editRevision == savedRevision) {
        setState(() {
          _hasUnsavedChanges = false;
          _saveFailed = false;
        });
      }
      return;
    }

    final now = DateTime.now();
    final canonicalDraft = await repo.getDraft(_activeBirthdayId);
    final startsNewRevision =
        _draft?.status == DraftStatus.confirmedSent ||
        (birthday.draftId == null && canonicalDraft != null);
    final draftId = startsNewRevision
        ? '$_activeBirthdayId-revision-${now.microsecondsSinceEpoch}'
        : _draft?.id ?? _activeBirthdayId;
    final updatedDraft = MessageDraft(
      id: draftId,
      birthdayId: _activeBirthdayId,
      personId: _person!.id,
      body: body,
      tone: _selectedTone,
      length: _selectedLength,
      status: status,
      providerType: _draft?.providerType ?? 'manual',
      createdAt: startsNewRevision ? now : _draft?.createdAt ?? now,
      updatedAt: now,
    );

    final nextStatus = status == DraftStatus.confirmedSent
        ? BirthdayStatus.completed
        : birthday.status == BirthdayStatus.handedOff && !startsNewRevision
        ? BirthdayStatus.handedOff
        : BirthdayStatus.messageDrafted;
    final applied = await repo.saveDraftAndUpdateBirthday(
      updatedDraft,
      nextStatus,
      requireUnchanged: true,
      expectedCurrentDraft: persisted,
    );
    if (!applied) {
      await _adoptStoredDraftAfterConflict(repo);
      throw const _DraftSaveConflict();
    }
    final stored = await repo.getDraft(updatedDraft.id);
    _persistedDraft = stored ?? updatedDraft;
    _birthday = birthday.copyWith(draftId: updatedDraft.id, status: nextStatus);
    if (mounted) {
      setState(() {
        _draft = stored ?? updatedDraft;
        if (_editRevision == savedRevision) {
          _hasUnsavedChanges = false;
          _saveFailed = false;
          _lastSavedTime = DateTime.now();
        }
      });
    }
  }

  /// A save lost the race with another writer. Re-seat the concurrency token on
  /// the stored row (so an explicit "Save now" can overwrite knowingly), keep
  /// the editor text untouched, and offer the stored text.
  Future<void> _adoptStoredDraftAfterConflict(DraftsRepository repo) async {
    final stored = await repo.getDraftForBirthday(_activeBirthdayId);
    final refreshedBirthday = await ref
        .read(birthdaysRepositoryProvider)
        .getBirthday(_activeBirthdayId);
    _persistedDraft = stored;
    if (refreshedBirthday != null) _birthday = refreshedBirthday;
    if (!mounted) return;
    setState(() {
      _draft = stored;
      _hasUnsavedChanges = true;
    });
    if (stored != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text(
            'Another change was saved to this draft. Your text was kept and not overwritten.',
          ),
          action: SnackBarAction(
            label: 'Use saved text',
            onPressed: () {
              if (!mounted) return;
              _editRevision++;
              setState(() {
                _messageController.text = stored.body;
                _selectedTone = stored.tone;
                _selectedLength = stored.length;
                _draft = stored;
                _hasUnsavedChanges = false;
                _saveFailed = false;
                _errorMessage = null;
              });
            },
          ),
        ),
      );
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

    final WhatsAppHandoffResult handoff;
    try {
      handoff = ref
          .read(whatsappHandoffBuilderProvider)
          .buildHandoff(rawPhoneNumber: phone, message: message);
    } on AppFailure catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.detail ?? e.message ?? 'Handoff failed')),
      );
      return;
    }

    await _performHandoff(
      channel: DeliveryChannel.whatsapp,
      channelName: 'WhatsApp',
      expectedMessage: message,
      confirmationDetail: handoff.uri.toString(),
      launch: () async {
        // Official Click-to-Chat URL; a launcher exception is a failed launch.
        try {
          return await launchUrl(
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
          return false;
        }
      },
    );
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

    await _performHandoff(
      channel: DeliveryChannel.sms,
      channelName: 'SMS',
      expectedMessage: message,
      confirmationDetail: 'Recipient: $phone',
      launch: () => ref
          .read(smsDeliveryServiceProvider)
          .sendSms(phoneNumber: phone, message: message),
    );
  }

  Future<void> _handlePlatformSend(DeliveryChannel channel) async {
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

    final plan = ref
        .read(platformHandoffBuilderProvider)
        .build(
          channel: channel,
          message: message,
          phoneNumber: _person?.phoneNumber,
        );
    if (!plan.requiresNativeShareFallback || plan.primaryUri != null) {
      ref
          .read(loggerProvider)
          .error(
            'MessageStudio',
            'Social platform did not produce a native share plan',
          );
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('This platform cannot open a direct chat from here.'),
        ),
      );
      return;
    }

    final channelName = channel.displayName;
    await _performHandoff(
      channel: channel,
      channelName: channelName,
      expectedMessage: message,
      confirmationDetail:
          'Message shared to $channelName for review and sending.',
      launch: () => ref
          .read(nativeShareServiceProvider)
          .shareToPlatform(
            channel: channel,
            text: message,
            title: 'Birthday greeting for ${_person?.name}',
          ),
    );
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

    await _performHandoff(
      channel: DeliveryChannel.share,
      channelName: 'Share Sheet',
      expectedMessage: message,
      confirmationDetail: 'Shared via Android chooser',
      launch: () => ref
          .read(nativeShareServiceProvider)
          .shareText(
            text: message,
            title: 'Birthday greeting for ${_person?.name}',
          ),
    );
  }

  /// Shared single-flight handoff: save the draft as ready, launch the external
  /// app, then record evidence. Each phase reports its own failure so a launch
  /// failure, a save failure and an evidence-write failure are never conflated.
  Future<void> _performHandoff({
    required DeliveryChannel channel,
    required String channelName,
    required String expectedMessage,
    required String confirmationDetail,
    required Future<bool> Function() launch,
  }) async {
    setState(() => _isSending = true);
    try {
      try {
        await _saveDraft(status: DraftStatus.ready);
      } on _DraftSaveConflict {
        // Already explained to the user; nothing was opened.
        if (mounted) setState(() => _errorMessage = _conflictMessage);
        return;
      } catch (e, st) {
        ref
            .read(loggerProvider)
            .error(
              'MessageStudio',
              'Could not save draft before $channelName handoff',
              error: e,
              stackTrace: st,
            );
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                'Your message could not be saved, so $channelName was not opened. Please try again.',
              ),
            ),
          );
        }
        return;
      }

      if (_draft?.body.trim() != expectedMessage) {
        if (mounted) {
          setState(() {
            _errorMessage =
                'The message changed while it was being prepared. Review it and try again.';
          });
        }
        return;
      }
      final bodyHash = crypto.sha256
          .convert(utf8.encode(expectedMessage))
          .toString();
      final birthdayId = _activeBirthdayId;
      final draftId = _draft?.id;
      final draftRevision = _draft?.revision;
      if (draftId == null || draftRevision == null) {
        if (mounted) {
          setState(() {
            _errorMessage =
                'The saved draft could not be verified. Please save it and try again.';
          });
        }
        return;
      }

      var launched = false;
      try {
        launched = await launch();
      } catch (e, st) {
        ref
            .read(loggerProvider)
            .error(
              'MessageStudio',
              'Unexpected error launching $channelName handoff',
              error: e,
              stackTrace: st,
            );
      }

      await _recordHandoffEvidence(
        birthdayId: birthdayId,
        draftId: draftId,
        channel: channel,
        channelName: channelName,
        confirmationDetail: confirmationDetail,
        launched: launched,
        bodyHash: bodyHash,
        draftRevision: draftRevision,
      );
    } finally {
      if (mounted) {
        setState(() => _isSending = false);
      }
    }
  }

  /// Records the launch outcome (evidence + birthday status) and then shows the
  /// confirmation dialog. If only this write fails the external app may already
  /// be open, so the message says so and offers a retry of just the record.
  Future<void> _recordHandoffEvidence({
    required String birthdayId,
    required String draftId,
    required DeliveryChannel channel,
    required String channelName,
    required String confirmationDetail,
    required bool launched,
    required String bodyHash,
    required int? draftRevision,
  }) async {
    final at = DateTime.now();
    try {
      final events = ref.read(deliveryEventsRepositoryProvider);
      if (launched) {
        // Evidence and the handed-off status move in one transaction (audit F20).
        await events.recordHandoffAndMarkHandedOff(
          birthdayId: birthdayId,
          draftId: draftId,
          draftRevision: draftRevision,
          bodyHash: bodyHash,
          channel: channel,
          at: at,
        );
      } else {
        await events.recordOutcome(
          birthdayId: birthdayId,
          draftId: draftId,
          draftRevision: draftRevision,
          bodyHash: bodyHash,
          channel: channel,
          outcome: DeliveryOutcome.launchFailed,
          at: at,
        );
        await ref
            .read(birthdaysRepositoryProvider)
            .updateBirthdayStatus(birthdayId, BirthdayStatus.failed);
      }
    } catch (e, st) {
      ref
          .read(loggerProvider)
          .error(
            'MessageStudio',
            'Could not record $channelName handoff evidence',
            error: e,
            stackTrace: st,
          );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          duration: const Duration(seconds: 10),
          content: Text(
            launched
                ? '$channelName was opened, but the handoff could not be recorded. Retry so History stays accurate.'
                : 'Could not open $channelName, and that failure could not be recorded. Retry to record it.',
          ),
          action: SnackBarAction(
            label: 'Retry record',
            onPressed: () => _recordHandoffEvidence(
              channel: channel,
              channelName: channelName,
              confirmationDetail: confirmationDetail,
              launched: launched,
              birthdayId: birthdayId,
              draftId: draftId,
              bodyHash: bodyHash,
              draftRevision: draftRevision,
            ),
          ),
        ),
      );
      return;
    }

    if (mounted) {
      _showHandoffConfirmationDialog(
        confirmationDetail,
        wasLaunched: launched,
        channelName: channelName,
        handoff: DeliveryHandoff(
          birthdayId: birthdayId,
          draftId: draftId,
          draftRevision: draftRevision,
          bodyHash: bodyHash,
          channel: channel,
          at: at.toUtc(),
          outcome: launched
              ? DeliveryOutcome.launched
              : DeliveryOutcome.launchFailed,
        ),
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

    final label = languageName != null
        ? 'Translated to $languageName.'
        : (length == MessageLength.short
              ? 'Message shortened ✂️'
              : length == MessageLength.expanded
              ? 'Message expanded 📝'
              : 'Message refined.');
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
    if (_person == null || _aiBusy) return;
    setState(() {
      _variationsLoading = true;
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
      // Sequential and per-variant tolerant: one failed tone must not discard
      // the others, and a non-retryable failure (locked, no key) stops early.
      final variations = <MapEntry<MessageTone, String>>[];
      Object? firstError;
      for (final t in tones) {
        try {
          final res = await aiRouter.generate(
            request: AiGenerationRequest(
              person: _person!,
              tone: t,
              length: _selectedLength,
              targetCycleYear: _birthday?.cycleYear,
            ),
            entitlement: entitlement,
          );
          variations.add(MapEntry(t, res.message));
        } catch (e) {
          firstError ??= e;
          if (e is AppFailure && !e.isRetryable) break;
        }
        if (!mounted) return;
      }
      if (variations.isEmpty) throw firstError ?? StateError('no variations');

      if (!mounted) return;
      setState(() => _variationsLoading = false);

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
          _editRevision++;
          _hasUnsavedChanges = true;
          var saved = true;
          var conflict = false;
          try {
            await _saveDraft();
          } on _DraftSaveConflict {
            saved = false;
            conflict = true;
          } catch (_) {
            saved = false;
            if (mounted) _scheduleAutosave();
          }
          // A conflict has already been explained (and never auto-overwrites).
          if (mounted && !conflict) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  saved
                      ? 'Variation applied.'
                      : 'Variation applied, but it could not be saved yet. It stays in the editor and will be retried.',
                ),
              ),
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
          _variationsLoading = false;
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

  DeliveryChannel _handoffChannelForName(String name) => switch (name) {
    'WhatsApp' => DeliveryChannel.whatsapp,
    'SMS' => DeliveryChannel.sms,
    'Share Sheet' => DeliveryChannel.share,
    _ => DeliveryChannel.whatsapp,
  };

  bool _isCurrentHandoff(DeliveryHandoff handoff) {
    final draftId = handoff.draftId;
    final revision = handoff.draftRevision;
    final bodyHash = handoff.bodyHash;
    if (handoff.birthdayId != _activeBirthdayId ||
        draftId == null ||
        revision == null ||
        bodyHash == null ||
        _draft?.id != draftId ||
        _draft?.revision != revision ||
        _draft?.birthdayId != handoff.birthdayId) {
      return false;
    }
    return crypto.sha256
            .convert(utf8.encode(_messageController.text.trim()))
            .toString() ==
        bodyHash;
  }

  void _showHandoffConfirmationDialog(
    String detailInfo, {
    required bool wasLaunched,
    String channelName = 'WhatsApp',
    DeliveryHandoff? handoff,
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
                  color: context.colors.surfaceAlt,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: context.colors.border),
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
                onPressed: () async {
                  await _copyToClipboard();
                  if (context.mounted) Navigator.of(context).pop();
                },
                child: const Text('Copy message'),
              ),
            TextButton(
              onPressed: () async {
                try {
                  await ref
                      .read(deliveryEventsRepositoryProvider)
                      .recordOutcome(
                        birthdayId: handoff?.birthdayId ?? _activeBirthdayId,
                        draftId: handoff?.draftId,
                        draftRevision: handoff?.draftRevision,
                        bodyHash: handoff?.bodyHash,
                        channel: _handoffChannelForName(channelName),
                        outcome: DeliveryOutcome.notSent,
                        at: DateTime.now(),
                      );
                  if (context.mounted) Navigator.of(context).pop();
                } catch (_) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Could not save your not-sent choice.'),
                      ),
                    );
                  }
                }
              },
              child: const Text('Not Sent Yet'),
            ),
            FilledButton(
              onPressed:
                  wasLaunched &&
                      handoff?.draftRevision != null &&
                      handoff?.bodyHash != null
                  ? () async {
                      HapticFeedback.lightImpact();
                      try {
                        if (!_isCurrentHandoff(handoff!)) {
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                content: Text(
                                  'This draft changed after the handoff. It was not marked sent; review and hand off the current text.',
                                ),
                              ),
                            );
                          }
                          return;
                        }
                        await ref
                            .read(deliveryEventsRepositoryProvider)
                            .confirmHandoff(
                              birthdayId: handoff.birthdayId,
                              draftId: handoff.draftId!,
                              expectedDraftRevision: handoff.draftRevision!,
                              expectedBodyHash: handoff.bodyHash!,
                              at: DateTime.now(),
                            );
                        await _loadData();
                        if (context.mounted) {
                          Navigator.of(context).pop();
                          context.pop();
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text(
                                'Celebration confirmed as sent by you.',
                              ),
                            ),
                          );
                        }
                      } catch (_) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text(
                                'This handoff no longer matches the saved draft, so it was not marked sent.',
                              ),
                            ),
                          );
                        }
                      }
                    }
                  : null,
              child: const Text('Yes, I Sent It'),
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
    if (!mounted) return;
    if (handoff == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('No handoff evidence is available to confirm.'),
        ),
      );
      return;
    }
    final channel = handoff.channel.displayName;
    final isCurrent = _isCurrentHandoff(handoff);
    _showHandoffConfirmationDialog(
      isCurrent
          ? 'Opened via $channel — ${_messageController.text.trim()}'
          : 'This handoff is tied to an earlier saved message. The current draft must be handed off again before it can be marked sent.',
      wasLaunched: true,
      channelName: channel,
      handoff: handoff,
    );
  }

  Future<void> _copyToClipboard() async {
    final text = _messageController.text.trim();
    if (text.isEmpty) return;
    try {
      if (_hasUnsavedChanges || _draft == null) {
        await _saveDraft(status: DraftStatus.draft);
      }
      await Clipboard.setData(ClipboardData(text: text));
      await ref
          .read(deliveryEventsRepositoryProvider)
          .recordOutcome(
            birthdayId: _activeBirthdayId,
            draftId: _draft?.id,
            channel: DeliveryChannel.clipboard,
            outcome: DeliveryOutcome.copied,
            at: DateTime.now(),
          );
      if (!mounted) return;
      HapticFeedback.lightImpact();
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Copied to clipboard 📋')));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Copy could not be saved to History.')),
      );
    }
  }

  /// Opens the contact editor so the recipient can get a usable phone number.
  void _openPhoneSetup() {
    HapticFeedback.lightImpact();
    context.push('/people/edit/${_person!.id}');
  }

  /// Opens Settings through the app router and returns to the current draft.
  void _openSettings() {
    HapticFeedback.lightImpact();
    context.push('/settings');
  }

  Future<void> _approveForAutoSms() async {
    final text = _messageController.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please enter or generate a message first.'),
        ),
      );
      return;
    }

    final smsSettings = ref.read(automaticSmsSettingsProvider);
    if (!smsSettings.enabled) {
      final proceed = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Enable Automatic SMS?'),
          content: Text(
            !smsSettings.isSupported
                ? 'Automatic SMS requires the personal sideload distribution of AI-Birthday.'
                : 'Automatic SMS is currently disabled in Settings. Would you like to enable it now?',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(false),
              child: const Text('Cancel'),
            ),
            if (smsSettings.isSupported)
              FilledButton(
                onPressed: () => Navigator.of(ctx).pop(true),
                child: const Text('Open Settings'),
              ),
          ],
        ),
      );
      if (proceed == true) {
        _openSettings();
      }
      return;
    }

    HapticFeedback.lightImpact();
    await _saveDraft(status: DraftStatus.approvedForAutoSms);
    final scheduled = await _reconcileAutoSms();

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            scheduled
                ? 'Message approved! Automatic SMS scheduled for midnight 00:00.'
                : 'Message approved, but Automatic SMS could not be scheduled. Check eligibility and permissions.',
          ),
        ),
      );
    }
  }

  Future<void> _revokeAutoSmsApproval() async {
    HapticFeedback.lightImpact();
    await _saveDraft(status: DraftStatus.draft);
    await _reconcileAutoSms();
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Automatic SMS approval revoked.')),
      );
    }
  }

  Future<bool> _reconcileAutoSms() async {
    if (_person == null) return false;
    try {
      final smsService = ref.read(birthdaySmsServiceProvider);
      final birthdaysRepo = ref.read(birthdaysRepositoryProvider);
      final birthday = await birthdaysRepo.getBirthday(_activeBirthdayId);
      return await smsService.reconcileRecipientSms(
        person: _person!,
        birthday: birthday,
        draft: _draft,
      );
    } catch (error, stackTrace) {
      ref
          .read(loggerProvider)
          .error(
            'MessageStudio',
            'Automatic SMS reconciliation failed',
            error: error,
            stackTrace: stackTrace,
          );
      return false;
    }
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
      return Scaffold(
        body: Center(
          child: Semantics(
            label: 'Loading message studio',
            child: CircularProgressIndicator(),
          ),
        ),
      );
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
                child: Semantics(
                  container: true,
                  liveRegion: true,
                  child: Text(
                    _errorMessage ?? 'Birthday not found',
                    textAlign: TextAlign.center,
                  ),
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
              if (_errorMessage?.startsWith('Could not load') ?? false)
                TextButton(
                  onPressed: _loadData,
                  child: const Text('Try Again'),
                ),
            ],
          ),
        ),
      );
    }

    final person = _person!;
    final birthday = _birthday!;
    final entitlement = ref.watch(entitlementProvider);
    // AI drafting features strictly require an active subscription entitlement.
    final aiLocked = !entitlement.canUseAi;
    final isCompleted = birthday.status == BirthdayStatus.completed;
    final isHandedOff = birthday.status == BirthdayStatus.handedOff;
    final rawPhone = person.phoneNumber?.trim() ?? '';
    final hasUsablePhone =
        WhatsAppHandoffBuilder.sanitizePhoneNumber(rawPhone) != null;
    final phoneMissing = rawPhone.isEmpty;
    final phoneInvalid = !phoneMissing && !hasUsablePhone;

    return PopScope<void>(
      canPop:
          _discardingUnsavedChanges ||
          (!_hasUnsavedChanges && !_isSaving && !_saveFailed),
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        final saved = await _saveBeforeLeaving();
        if (saved && context.mounted) Navigator.of(context).pop();
      },
      child: Scaffold(
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
        body: AppResponsiveContainer(
          maxWidth: 720,
          child: ListView(
            padding: EdgeInsets.fromLTRB(
              16,
              16,
              16,
              AppSpacing.bottomClearance +
                  MediaQuery.viewInsetsOf(context).bottom,
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
                            child: Semantics(
                              container: true,
                              liveRegion: true,
                              child: Text(
                                _errorMessage!,
                                style: TextStyle(
                                  color: Theme.of(context).colorScheme.error,
                                  fontSize: 13,
                                ),
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
                          if (_saveFailed)
                            FilledButton.icon(
                              icon: const Icon(Icons.save_outlined, size: 16),
                              label: const Text('Save now'),
                              onPressed: _performAutosave,
                            )
                          else
                            TextButton.icon(
                              icon: const Icon(Icons.refresh, size: 16),
                              label: const Text('Retry'),
                              onPressed: () => _generateWithAi(),
                              style: TextButton.styleFrom(
                                visualDensity: VisualDensity.compact,
                              ),
                            ),
                          if (_saveFailed)
                            TextButton.icon(
                              icon: const Icon(Icons.delete_outline, size: 16),
                              label: const Text('Discard edits'),
                              onPressed: _isSaving ? null : _discardAndLeave,
                            ),
                          // On a credential failure, offer the on-device fallback
                          // (audit 02 AC P1-1): retry with Gemini Nano forced.
                          if (!_saveFailed &&
                              _lastFailureCode ==
                                  AppFailureCode.aiCredentialInvalid)
                            TextButton.icon(
                              icon: const Icon(Icons.phone_android, size: 16),
                              label: const Text(
                                'Use on-device AI (Gemini Nano)',
                              ),
                              onPressed: () => _generateWithAi(forceNano: true),
                              style: TextButton.styleFrom(
                                visualDensity: VisualDensity.compact,
                              ),
                            ),
                          if (!_saveFailed)
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
                          if (!_saveFailed)
                            TextButton.icon(
                              icon: const Icon(Icons.tune, size: 16),
                              label: const Text('AI Settings'),
                              onPressed: _openSettings,
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
                        onPressed: () async {
                          HapticFeedback.lightImpact();
                          if (await _saveBeforeLeaving() && context.mounted) {
                            context.go('/history');
                          }
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
                    : !hasUsablePhone &&
                          (person.preferredDeliveryChannel ==
                                  DeliveryChannel.whatsapp ||
                              person.preferredDeliveryChannel ==
                                  DeliveryChannel.sms)
                    ? FilledButton.icon(
                        onPressed: _openPhoneSetup,
                        icon: const Icon(Icons.person_add_alt_1),
                        label: Text(
                          phoneMissing
                              ? 'Add Phone Number'
                              : 'Fix Phone Number',
                        ),
                      )
                    : switch (person.preferredDeliveryChannel) {
                        DeliveryChannel.sms => FilledButton.icon(
                          onPressed: _isSending ? null : _handleSmsSend,
                          icon: const Icon(Icons.sms_outlined),
                          label: const Text('Send via SMS'),
                        ),
                        DeliveryChannel.share => FilledButton.icon(
                          onPressed: _isSending ? null : _handleShareSend,
                          icon: const Icon(Icons.share_outlined),
                          label: const Text('Open Share Sheet'),
                        ),
                        DeliveryChannel.clipboard => FilledButton.icon(
                          onPressed: _copyToClipboard,
                          icon: const Icon(Icons.copy_outlined),
                          label: const Text('Copy Message'),
                        ),
                        DeliveryChannel.instagram ||
                        DeliveryChannel.snapchat ||
                        DeliveryChannel.linkedin => FilledButton.icon(
                          onPressed: _isSending
                              ? null
                              : () => _handlePlatformSend(
                                  person.preferredDeliveryChannel,
                                ),
                          icon: const Icon(Icons.send_outlined),
                          label: Text(
                            'Open ${person.preferredDeliveryChannel.displayName}',
                          ),
                        ),
                        DeliveryChannel.whatsapp => FilledButton.icon(
                          onPressed: _isSending ? null : _handleWhatsAppSend,
                          icon: const Icon(Icons.chat),
                          label: const Text('Send on WhatsApp'),
                          style: FilledButton.styleFrom(
                            backgroundColor: AppColors.whatsappGreen,
                            foregroundColor: Colors.white,
                          ),
                        ),
                      },
                secondary: aiLocked
                    ? FilledButton.tonalIcon(
                        onPressed: _openSettings,
                        icon: const Icon(Icons.star_outline),
                        label: const Text('Unlock with Pro'),
                      )
                    : FilledButton.tonalIcon(
                        onPressed: _aiBusy ? null : _generateWithAi,
                        icon: _isGenerating
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
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
              // Alternate delivery channels (legacy SSOT §10 (PRODUCT.md §16.7))
              Wrap(
                spacing: 8,
                runSpacing: 8,
                alignment: WrapAlignment.center,
                children: [
                  if (hasUsablePhone &&
                      person.preferredDeliveryChannel != DeliveryChannel.sms)
                    OutlinedButton.icon(
                      onPressed: _isSending ? null : _handleSmsSend,
                      icon: const Icon(Icons.sms_outlined, size: 18),
                      label: const Text('Send via SMS'),
                    ),
                  if (person.preferredDeliveryChannel != DeliveryChannel.share)
                    OutlinedButton.icon(
                      onPressed: _isSending ? null : _handleShareSend,
                      icon: const Icon(Icons.share_outlined, size: 18),
                      label: const Text('Share Sheet'),
                    ),
                  if (person.preferredDeliveryChannel !=
                      DeliveryChannel.clipboard)
                    OutlinedButton.icon(
                      onPressed: _copyToClipboard,
                      icon: const Icon(Icons.copy_outlined, size: 18),
                      label: const Text('Copy Text'),
                    ),
                ],
              ),
              if (ProductCapabilities.automaticSms &&
                  _person?.autoSendPolicy ==
                      AutoSendPolicy.automaticSmsApproved &&
                  hasUsablePhone &&
                  !isCompleted) ...[
                const SizedBox(height: AppSpacing.sm),
                SizedBox(
                  width: double.infinity,
                  child: _draft?.status == DraftStatus.approvedForAutoSms
                      ? FilledButton.tonalIcon(
                          onPressed: _revokeAutoSmsApproval,
                          icon: Icon(
                            Icons.check_circle,
                            color: context.colors.success,
                          ),
                          label: const Text(
                            'Approved for Auto-SMS (Tap to Revoke)',
                          ),
                        )
                      : OutlinedButton.icon(
                          onPressed: _approveForAutoSms,
                          icon: const Icon(Icons.alarm_on),
                          label: const Text('Approve for Midnight Auto-SMS'),
                        ),
                ),
              ],
            ],
          ),
        ),
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
                          'Celebration marked as sent.',
                          style: TextStyle(
                            fontSize: 12,
                            color: context.colors.success,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      if (ProductCapabilities.automaticSms &&
                          person.autoSendPolicy ==
                              AutoSendPolicy.automaticSmsApproved)
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text(
                            _draft?.status == DraftStatus.approvedForAutoSms
                                ? 'Approved for midnight automatic SMS'
                                : 'Auto-SMS opted in: requires message approval below',
                            style: TextStyle(
                              fontSize: 12,
                              color:
                                  _draft?.status ==
                                      DraftStatus.approvedForAutoSms
                                  ? context.colors.success
                                  : theme.colorScheme.tertiary,
                              fontWeight: FontWeight.w600,
                            ),
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
                    if (val) {
                      _onDraftPreferencesChanged(() => _selectedTone = t);
                    }
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
                    if (val) {
                      _onDraftPreferencesChanged(() => _selectedLength = l);
                    }
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
                  Padding(
                    padding: EdgeInsets.only(right: 8),
                    child: Text(
                      'Saved',
                      style: TextStyle(
                        fontSize: 11,
                        color: context.colors.success,
                      ),
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
                onPressed: _aiBusy
                    ? null
                    : () {
                        HapticFeedback.lightImpact();
                        _rewriteMessage(length: MessageLength.short);
                      },
              ),
              ActionChip(
                avatar: const Icon(Icons.expand, size: 16),
                label: const Text('Expand'),
                onPressed: _aiBusy
                    ? null
                    : () {
                        HapticFeedback.lightImpact();
                        _rewriteMessage(length: MessageLength.expanded);
                      },
              ),
              ActionChip(
                avatar: const Icon(Icons.translate, size: 16),
                label: const Text('Translate'),
                onPressed: _aiBusy
                    ? null
                    : () {
                        HapticFeedback.lightImpact();
                        _showTranslateDialog();
                      },
              ),
              ActionChip(
                avatar: const Icon(Icons.casino_outlined, size: 16),
                label: const Text('Variations'),
                onPressed: _aiBusy
                    ? null
                    : () {
                        HapticFeedback.lightImpact();
                        _generateVariations();
                      },
              ),
            ],
          ),
        ],
      ],
    );
  }
}
