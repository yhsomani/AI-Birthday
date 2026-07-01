# RelateAI Implementation Progress

Version: 1.0.0
Date: 2026-06-26
Source backlog: [IMPLEMENTATION_TASKS.md](IMPLEMENTATION_TASKS.md)

## 2026-07-01 - Chat History Search Filter

Completed tasks:

- P2 Chat History UX slice: help users find prior sent messages without manually scanning the full contact history.

Changed files:

- [app/src/main/java/com/example/ui/screens/chat/ChatHistoryViewModel.kt](app/src/main/java/com/example/ui/screens/chat/ChatHistoryViewModel.kt)
- [app/src/main/java/com/example/ui/screens/chat/ChatHistoryScreen.kt](app/src/main/java/com/example/ui/screens/chat/ChatHistoryScreen.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [app/src/test/java/com/example/ui/screens/chat/ChatHistoryViewModelTest.kt](app/src/test/java/com/example/ui/screens/chat/ChatHistoryViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/chat/ChatHistoryScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/chat/ChatHistoryScreenInteractionTest.kt)
- [app/src/test/java/com/example/ui/screenshots/ChatHistoryScreenshotTest.kt](app/src/test/java/com/example/ui/screenshots/ChatHistoryScreenshotTest.kt)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `ChatHistoryUiState` now tracks `searchQuery` and the total loaded message count separately from visible filtered messages.
- `ChatHistoryViewModel` filters loaded history by message text or channel and reapplies the active query when repository updates arrive.
- Chat History UI now includes a localized search field, clear action, and distinct no-results empty state.

Why this improves user experience:

- Users can quickly find a prior message or channel-specific send instead of scanning every sent item.
- Empty history and empty search results now communicate different states.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.screens.chat.ChatHistoryViewModelTest \
  --tests com.example.ui.screens.chat.ChatHistoryScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --no-configuration-cache
```

Result: passed.

## 2026-07-01 - Style Coach Message Style Preview

Completed tasks:

- P3 Style Coach UX slice: show concrete examples of how the learned style profile affects future generated messages.

Changed files:

- [app/src/main/java/com/example/ui/screens/stylecoach/StyleCoachScreen.kt](app/src/main/java/com/example/ui/screens/stylecoach/StyleCoachScreen.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [app/src/test/java/com/example/ui/screens/stylecoach/StyleCoachScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/stylecoach/StyleCoachScreenInteractionTest.kt)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Style Coach now shows a separate message-style preview below the learned profile.
- The preview derives an opening example, tone, target length, and emoji guidance from the saved profile.
- English and Hindi resources were added, and interaction coverage verifies the preview values render.

Why this improves user experience:

- Users can see what the style profile will do instead of only reading raw metrics.
- The preview makes style training feedback more actionable before a larger before/after generation comparison is built.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.screens.stylecoach.StyleCoachScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --no-configuration-cache
```

Result: passed.

## 2026-07-01 - Gift Advisor Suggestion Dismiss Action

Completed tasks:

- P2 Gift Advisor UX slice: let users clear irrelevant AI gift suggestions from the current review list.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/GiftAdvisorViewModel.kt](app/src/main/java/com/example/ui/viewmodel/GiftAdvisorViewModel.kt)
- [app/src/main/java/com/example/ui/screens/giftadvisor/GiftAdvisorScreen.kt](app/src/main/java/com/example/ui/screens/giftadvisor/GiftAdvisorScreen.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [app/src/test/java/com/example/ui/viewmodel/GiftAdvisorViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/GiftAdvisorViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/giftadvisor/GiftAdvisorScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/giftadvisor/GiftAdvisorScreenInteractionTest.kt)
- [app/src/test/java/com/example/ui/screenshots/GiftAdvisorScreenshotTest.kt](app/src/test/java/com/example/ui/screenshots/GiftAdvisorScreenshotTest.kt)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `GiftAdvisorViewModel` now exposes `dismissGiftSuggestion(index)` and removes the selected suggestion without affecting the remaining ideas.
- Gift suggestion cards now show a localized dismiss icon action alongside `Record`.
- Interaction and screenshot test helpers now wire the dismiss callback, and view-model coverage verifies invalid indexes are ignored.

Why this improves user experience:

- Users can remove irrelevant AI ideas instead of repeatedly scanning past them.
- Suggestion review now supports both positive action (`Record`) and negative action (`Dismiss`) in the same place.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.GiftAdvisorViewModelTest \
  --tests com.example.ui.screens.giftadvisor.GiftAdvisorScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --no-configuration-cache
```

Result: passed.

## 2026-07-01 - Chat History Domain UI State

Completed tasks:

- P2 architecture cleanup slice: stop Chat History UI state from exposing the Room-backed `SentMessageEntity`.

Changed files:

- [core/model/src/main/kotlin/com/example/domain/model/message/MessageListItems.kt](core/model/src/main/kotlin/com/example/domain/model/message/MessageListItems.kt)
- [core/domain/src/main/kotlin/com/example/domain/message/SentMessageMappers.kt](core/domain/src/main/kotlin/com/example/domain/message/SentMessageMappers.kt)
- [app/src/main/java/com/example/ui/screens/chat/ChatHistoryViewModel.kt](app/src/main/java/com/example/ui/screens/chat/ChatHistoryViewModel.kt)
- [app/src/main/java/com/example/ui/screens/chat/ChatHistoryScreen.kt](app/src/main/java/com/example/ui/screens/chat/ChatHistoryScreen.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [app/src/test/java/com/example/ui/screens/chat/ChatHistoryViewModelTest.kt](app/src/test/java/com/example/ui/screens/chat/ChatHistoryViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/chat/ChatHistoryScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/chat/ChatHistoryScreenInteractionTest.kt)
- [app/src/test/java/com/example/ui/screenshots/ChatHistoryScreenshotTest.kt](app/src/test/java/com/example/ui/screenshots/ChatHistoryScreenshotTest.kt)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added `ChatHistoryMessageItem` as the domain-facing model for chat history rows.
- Added sent-message mapper functions that convert repository entities into chat-history items.
- `ChatHistoryUiState.messages` now exposes `List<ChatHistoryMessageItem>` instead of `List<SentMessageEntity>`.
- Chat History UI and screenshot fixtures now use typed `MessageChannel` values and a localized unknown-channel label.

Why this improves user experience and architecture:

- Chat History rendering is insulated from Room schema details.
- Future search, resend, regenerate, and paging work can build from a screen-specific domain item instead of a persistence entity.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.screens.chat.ChatHistoryViewModelTest \
  --tests com.example.ui.screens.chat.ChatHistoryScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --no-configuration-cache
```

Result: passed.

## 2026-07-01 - Manual Event Type Validation

Completed tasks:

- P2 Events correctness slice: prevent unsupported manual event type input from being persisted as `UNKNOWN`.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/usecase/SaveManualEventUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/SaveManualEventUseCase.kt)
- [app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [app/src/test/java/com/example/domain/usecase/SaveManualEventUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/SaveManualEventUseCaseTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/EventsViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/EventsViewModelTest.kt)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `SaveManualEventUseCase` now returns `InvalidInputReason.UNSUPPORTED_EVENT_TYPE` for unsupported nonblank event type input.
- Unsupported event types are rejected before contact lookup, contact persistence, event persistence, or reminder scheduling.
- Events UI now maps that reason to localized English and Hindi copy.

Why this improves user experience:

- Users get a clear validation error instead of creating an event that later appears as an ambiguous custom/unknown occasion.
- The event store stays cleaner because manual saves can only persist supported occasion types.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.SaveManualEventUseCaseTest \
  --tests com.example.ui.viewmodel.EventsViewModelTest \
  --tests com.example.ui.LocalizationParityTest \
  --no-configuration-cache
```

Result: passed.

## 2026-07-01 - Review-First Automation Defaults

Completed tasks:

- P1 Settings/automation safety slice: prevent missing, unsupported, or not-yet-loaded automation state from displaying or resolving as fully automatic.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt)
- [core/data/src/main/kotlin/com/example/core/prefs/GlobalAutomationModePrefsMapper.kt](core/data/src/main/kotlin/com/example/core/prefs/GlobalAutomationModePrefsMapper.kt)
- [core/data/src/main/kotlin/com/example/core/prefs/SecurePrefs.kt](core/data/src/main/kotlin/com/example/core/prefs/SecurePrefs.kt)
- [core/domain/src/main/kotlin/com/example/domain/automation/ApprovalModeResolver.kt](core/domain/src/main/kotlin/com/example/domain/automation/ApprovalModeResolver.kt)
- [app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt)
- [app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt)
- [core/data/src/test/kotlin/com/example/core/prefs/GlobalAutomationModePrefsMapperTest.kt](core/data/src/test/kotlin/com/example/core/prefs/GlobalAutomationModePrefsMapperTest.kt)
- [core/domain/src/test/kotlin/com/example/domain/automation/ApprovalModeResolverTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/ApprovalModeResolverTest.kt)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `SettingsUiState` now defaults to `ALWAYS_ASK`, so Settings does not transiently expose `FULLY_AUTO` before persisted preferences load.
- Missing or unsupported stored global automation values now map to `ALWAYS_ASK`.
- `ApprovalModeResolver` now treats unknown global automation modes as `ALWAYS_ASK`, so corrupt or legacy state cannot silently schedule automatic sends.
- Message generation now covers the unknown-mode path with a reviewable draft and approval notification instead of exact-send scheduling.

Why this improves user experience:

- Users only get fully automatic behavior after an explicit supported setting has been persisted.
- Automation trust improves because missing or malformed settings fail closed into manual review.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :core:data:testDebugUnitTest \
  :core:domain:testDebugUnitTest \
  :app:testDebugUnitTest \
  --tests com.example.core.prefs.GlobalAutomationModePrefsMapperTest \
  --tests com.example.domain.automation.ApprovalModeResolverTest \
  --tests com.example.domain.usecase.GenerateMessageUseCaseTest \
  --tests com.example.ui.viewmodel.SettingsViewModelTest \
  --no-configuration-cache
```

Result: passed.

## 2026-07-01 - Gift Advisor Suggestion Trust and Record Shortcut

Completed tasks:

- P2 Gift Advisor UX slice: make AI suggestions explainable and actionable before a larger screen redesign.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/GiftAdvisorViewModel.kt](app/src/main/java/com/example/ui/viewmodel/GiftAdvisorViewModel.kt)
- [app/src/main/java/com/example/ui/screens/giftadvisor/GiftAdvisorScreen.kt](app/src/main/java/com/example/ui/screens/giftadvisor/GiftAdvisorScreen.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [app/src/test/java/com/example/ui/viewmodel/GiftAdvisorViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/GiftAdvisorViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/giftadvisor/GiftAdvisorScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/giftadvisor/GiftAdvisorScreenInteractionTest.kt)
- [app/src/test/java/com/example/ui/screenshots/GiftAdvisorScreenshotTest.kt](app/src/test/java/com/example/ui/screenshots/GiftAdvisorScreenshotTest.kt)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `GiftAdvisorViewModel` now enriches raw AI gift suggestions into UI models with confidence percent, budget-fit status, budget overage, duplicate-history warning, and history-check evidence.
- Gift suggestion cards now show that evidence and expose a visible `Record` action.
- Tapping `Record` pre-fills the existing gift-history form with the suggestion name, cost, and reason notes.
- Dismissing the gift form now discards the draft so a later manual record starts clean.

Why this improves user experience:

- Users can see why a suggestion is trustworthy, whether it fits the remaining budget, and whether it risks repeating a previous gift.
- The record shortcut reduces the effort of turning a useful suggestion into persistent history.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.GiftAdvisorViewModelTest \
  --tests com.example.ui.screens.giftadvisor.GiftAdvisorScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --no-configuration-cache
```

Result: passed.

## 2026-07-01 - Style Coach Confidence Metric

Completed tasks:

- P3 Style Coach UX slice: show a user-facing style confidence level and learned sample count on the learned profile card.

Changed files:

- [app/src/main/java/com/example/ui/screens/stylecoach/StyleCoachScreen.kt](app/src/main/java/com/example/ui/screens/stylecoach/StyleCoachScreen.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [app/src/test/java/com/example/ui/screens/stylecoach/StyleCoachScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/stylecoach/StyleCoachScreenInteractionTest.kt)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Learned profile cards now show `Style Confidence` and `Samples Learned`.
- Confidence is derived from cumulative `sampleCount`: untrained, starting, growing, or strong.
- English and Hindi resources were added with interaction and localization coverage.

Why this improves user experience:

- Users can now understand whether the app has enough writing evidence to personalize reliably.
- The confidence signal turns the cumulative sample count into an actionable training state.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.screens.stylecoach.StyleCoachScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --no-configuration-cache
```

Result: passed.

## 2026-07-01 - Style Coach Cumulative Sample Count

Completed tasks:

- P3 Style Coach UX/data-quality slice: make style profile `sampleCount` accumulate analyzed message evidence instead of resetting to the current batch size.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/usecase/StyleAnalysisUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/StyleAnalysisUseCase.kt)
- [app/src/test/java/com/example/domain/usecase/StyleAnalysisUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/StyleAnalysisUseCaseTest.kt)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `StyleAnalysisUseCase` now stores `currentProfile.sampleCount + texts.size` for each analysis run.
- Existing sample text augmentation still deduplicates retained examples, while `sampleCount` now better represents the amount of training evidence seen over time.

Why this improves user experience:

- Style Coach can present a more accurate confidence/training signal after repeated manual or automatic analysis runs.
- Users are less likely to see the style profile appear to lose evidence after a smaller later analysis batch.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.StyleAnalysisUseCaseTest \
  --no-configuration-cache
```

Result: passed.

## 2026-07-01 - Style Analysis Dead Code Cleanup

Completed tasks:

- P3 dead-code cleanup slice: removed the unused private `StyleAnalysisUseCase.findCommonPhrases(...)` helper after verifying it had no callers.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/usecase/StyleAnalysisUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/StyleAnalysisUseCase.kt)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Removed a stale word-frequency phrase extractor that was not wired into style profile generation.
- Kept the active bigram-based `topPhrases` path unchanged, so generated `commonPhrasesJson` behavior stays covered by existing style analysis tests.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.StyleAnalysisUseCaseTest \
  --no-configuration-cache
```

Result: passed.

## 2026-07-01 - Repository Hygiene Cleanup

Completed tasks:

- P1 repository hygiene slice: untracked verified local/generated artifacts while keeping local copies on disk.
- Updated `.gitignore` and `RepositoryHygieneTest` so generated output folders stay ignored without hiding real app media assets.
- Left Google/Firebase config files tracked because the audit marks them as a repository policy decision rather than a safe generated-artifact cleanup.

Changed files:

- [.gitignore](.gitignore)
- [app/src/test/java/com/example/RepositoryHygieneTest.kt](app/src/test/java/com/example/RepositoryHygieneTest.kt)
- [SSOT.md](SSOT.md)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

Removed from git tracking:

- `.codepulse/workflow/events.json`
- `.gradle-user-home/**`
- `.intelligence/enterprise-diagnostics/*.json`
- `app_logs*.txt`
- `logcat_*.txt`
- `lint_baseline_pre_fixes.txt`
- `app/src/test/screenshots/greeting.png`
- `app/schemas/**`

What changed:

- These files remain available locally but are no longer versioned.
- `app/schemas` is now treated as a legacy/local generated path; active Room schemas remain under `core/data/schemas`.
- `git ls-files -i -c --exclude-standard` now reports only `app/google-services.json` and `app/src/debug/google-services.json`, which need an explicit public/private repository policy decision.

Validation:

```bash
git ls-files -i -c --exclude-standard

JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.RepositoryHygieneTest \
  --no-configuration-cache
```

Result: passed. The ignored-tracked scan now reports only the tracked Google/Firebase service config files that require a separate repository policy decision.

## 2026-07-01 - Messages Readiness Sender Validation

Completed tasks:

- P1 readiness consistency slice: Messages now uses the shared domain email sender validation rule instead of a screen-local blank-only credential check.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt](app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt)
- [app/src/test/java/com/example/ui/viewmodel/MessagesViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/MessagesViewModelTest.kt)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Email pending-message readiness now calls `EmailAddressSyntaxPolicy.isConfiguredSender(...)`, the same domain rule used by route selection and generation paths.
- The Messages ViewModel test now proves an invalid configured sender email keeps an email draft in the blocked queue until a syntactically valid sender is configured.

Why this improves user experience:

- Users no longer see email drafts as ready for review/sending when SMTP credentials are present but the sender address is not usable.
- Messages, generation, and route selection now agree on one more setup-readiness rule.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.MessagesViewModelTest \
  --no-configuration-cache
```

Result: passed.

## 2026-07-01 - Automation Quality Gate, Dispatch Windows, and Docs Alignment

Completed tasks:

- P0 quality gate slice: downgrade fallback, generic, blank, or otherwise low-quality automatic AI messages to manual review instead of preserving fully automatic dispatch.
- T113 follow-up: pass global quiet hours and blackout dates into the foreground/manual `DispatchMessageUseCase` policy path so it matches worker dispatch behavior.
- P0/P1 docs alignment: update the canonical module/schema facts and record current audit status.
- Repository hygiene slice: narrow `.gitignore` media rules so valid Android assets are no longer hidden by global image-extension ignores.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/automation/AiAutoSendQualityGate.kt](core/domain/src/main/kotlin/com/example/domain/automation/AiAutoSendQualityGate.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt)
- [core/domain/src/test/kotlin/com/example/domain/automation/AiAutoSendQualityGateTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/AiAutoSendQualityGateTest.kt)
- [app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt)
- [.gitignore](.gitignore)
- [SSOT.md](SSOT.md)
- [CODEBASE_AUDIT_REPORT_2026-07-01.md](CODEBASE_AUDIT_REPORT_2026-07-01.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `AiAutoSendQualityGate` now lets the computed quality score control automatic-send eligibility for `FULLY_AUTO` and `SMART_APPROVE`; fallback/generic content below the threshold becomes `ALWAYS_ASK`.
- `DispatchMessageUseCase` now supplies `PreferencesRepository` quiet-hours and blackout-date values to `DispatchEligibilityPolicy`, adding manual/foreground deferral coverage for blocked send windows.
- `.gitignore` no longer ignores all `*.png`, `*.jpg`, `*.svg`, or `*.webp` files globally, so launcher icons and future app assets remain visible to git.
- `SSOT.md` now lists `:core:model` as an active module and Room schema version 16.

Why this improves user experience:

- Fully automatic sends no longer allow generic fallback copy through without review.
- Manual or foreground dispatch attempts now respect the same quiet-hours and blackout-date constraints as background workers.
- Contributors can see legitimate asset changes and current architecture facts instead of relying on stale documentation.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest :app:testDebugUnitTest \
  --tests com.example.domain.automation.AiAutoSendQualityGateTest \
  --tests com.example.domain.automation.DispatchEligibilityPolicyTest \
  --tests com.example.domain.usecase.DispatchMessageUseCaseTest \
  --no-configuration-cache
```

Result: passed.

Remaining work:

- Decide repository policy for tracked Google/Firebase service config files.
- Continue consolidating readiness state across Home, Messages, Wish Preview, AI Doctor, notifications, and dispatch.

## 2026-06-26 - ActivityLogType Producers and Filters

Completed tasks:

- T604 slice: Introduce `ActivityLogType` and move activity-log producer, Activity History filter, and icon mapping type labels away from duplicated raw `"MESSAGE"`/`"EVENT"`/`"AI"`/`"ANALYTICS"`/`"BACKUP"`/`"SYNC"`/`"SETTINGS"`/`"DISPATCH"` literals.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/model/ActivityLogType.kt](core/domain/src/main/kotlin/com/example/domain/model/ActivityLogType.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt)
- [core/data/src/main/kotlin/com/example/core/analytics/AnalyticsReportServiceImpl.kt](core/data/src/main/kotlin/com/example/core/analytics/AnalyticsReportServiceImpl.kt)
- [app/src/main/java/com/example/ui/viewmodel/ActivityHistoryViewModel.kt](app/src/main/java/com/example/ui/viewmodel/ActivityHistoryViewModel.kt)
- [app/src/main/java/com/example/ui/screens/activity/ActivityHistoryScreen.kt](app/src/main/java/com/example/ui/screens/activity/ActivityHistoryScreen.kt)
- [app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt)
- [app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt](app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt)
- [app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt](app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt)
- [core/domain/src/test/kotlin/com/example/domain/model/ActivityLogTypeTest.kt](core/domain/src/test/kotlin/com/example/domain/model/ActivityLogTypeTest.kt)
- [core/data/src/test/kotlin/com/example/core/analytics/AnalyticsReportServiceImplTest.kt](core/data/src/test/kotlin/com/example/core/analytics/AnalyticsReportServiceImplTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/ActivityHistoryViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/ActivityHistoryViewModelTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added `ActivityLogType` with normalization for persisted activity-log type labels.
- Dispatch, analytics export, event, messages, and Wish Preview activity producers now write type labels through `ActivityLogType`.
- Activity History type filters and row icons now parse raw log values through `ActivityLogType.fromRaw()`.
- Activity History status filtering now uses `ActivityLogStatus.fromRaw()` instead of direct raw string comparison.

Why this improves user experience:

- Activity History filters and icons now share one log-type vocabulary with the producers that create those records.
- Dispatch audit records remain discoverable through the Dispatch filter while still preserving the existing Message log type plus decision metadata.

How user effort is reduced:

- Users get more reliable Activity History filtering and scanning when reviewing message, event, AI, analytics, backup, sync, settings, or dispatch-related work.
- Maintainers can add or rename activity-log categories in `ActivityLogType` instead of chasing producer, filter, and icon strings separately.

How user control is preserved:

- Room still stores raw activity-log type strings for query, backup/import, and existing-log compatibility.
- Activity History search/filter semantics, dispatch detection, logging, analytics export, event save/resolution, feedback, and message activity behavior are unchanged.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest \
  --tests com.example.domain.model.ActivityLogTypeTest \
  --no-configuration-cache

JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest \
  --tests com.example.core.analytics.AnalyticsReportServiceImplTest \
  --no-configuration-cache

JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.ActivityHistoryViewModelTest \
  --tests com.example.domain.usecase.DispatchMessageUseCaseTest \
  --tests com.example.ui.viewmodel.WishPreviewViewModelTest \
  --tests com.example.ui.viewmodel.MessagesViewModelTest \
  --tests com.example.ui.viewmodel.EventsViewModelTest \
  --no-configuration-cache
```

Result: passed.

Targeted activity-log type scan passed; raw activity-log type writes are removed from the touched production producers and Activity History reader paths.

`git diff --check` passed.

Remaining T604 work: Room SQL status predicates/defaults, serialized fixtures, pending-message fixture literals, event-type taxonomy, and broader approval/status/relationship cleanup remain open.

## 2026-06-30 - Audit Stabilization: Auth, Setup Readiness, Events, and Docs

Completed tasks:

- Removed the debug-only application id suffix and aligned the checked-in debug Firebase JSON so local debug builds use one Firebase-compatible application id.
- Removed hard-coded launcher shortcut package names so shortcuts use their manifest component plus explicit deep-link data.
- Made onboarding's setup checklist action preserve the intended post-auth destination and open AI Doctor after sign-in.
- Made AI Doctor Google Contacts readiness depend on the Google Contacts scope or a cached People API token instead of any signed-in Google account.
- Added Android contacts permission gating before AI Doctor contact sync while preserving Google Contacts sync when the permission is denied.
- Made AI Doctor email readiness distinguish missing setup, malformed sender email, configured-but-unverified Gmail sender details, and a recent successful self-test.
- Added sender email syntax validation before Settings persists Gmail sender details.
- Surfaced graduation, holiday, revival, and follow-up event types in Events filters, manual event creation, Messages badges, Wish Preview labels, Home labels, fallback copy, and email subjects.
- Removed unused direct Google API client/People dependencies from the app and data modules.
- Moved widget layout placeholder text into string resources and added Hindi localization coverage for the new visible strings.
- Updated user, product, SSOT, roadmap, and progress documentation to match the corrected setup and build behavior.

Changed files:

- [app/build.gradle.kts](app/build.gradle.kts)
- [app/src/debug/google-services.json](app/src/debug/google-services.json)
- [app/src/main/java/com/example/ui/navigation/NavGraph.kt](app/src/main/java/com/example/ui/navigation/NavGraph.kt)
- [app/src/main/java/com/example/ui/screens/setup/AutomationSetupScreen.kt](app/src/main/java/com/example/ui/screens/setup/AutomationSetupScreen.kt)
- [app/src/main/java/com/example/ui/viewmodel/AutomationSetupViewModel.kt](app/src/main/java/com/example/ui/viewmodel/AutomationSetupViewModel.kt)
- [app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt)
- [app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt)
- [app/src/main/java/com/example/ui/screens/events/EventsScreen.kt](app/src/main/java/com/example/ui/screens/events/EventsScreen.kt)
- [app/src/main/java/com/example/ui/screens/messages/MessagesQueueComponents.kt](app/src/main/java/com/example/ui/screens/messages/MessagesQueueComponents.kt)
- [app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt](app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt)
- [app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt](app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt)
- [core/data/src/main/kotlin/com/example/core/gemini/ResponseParser.kt](core/data/src/main/kotlin/com/example/core/gemini/ResponseParser.kt)
- [core/data/src/main/kotlin/com/example/core/automation/sender/EmailSubjectBuilder.kt](core/data/src/main/kotlin/com/example/core/automation/sender/EmailSubjectBuilder.kt)
- [app/src/main/res/xml/shortcuts.xml](app/src/main/res/xml/shortcuts.xml)
- [app/src/main/res/layout/widget_birthday.xml](app/src/main/res/layout/widget_birthday.xml)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [gradle/libs.versions.toml](gradle/libs.versions.toml)
- [docs/user/complete-user-guide.md](docs/user/complete-user-guide.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [PRODUCT_UX_WORKFLOW_TECHNICAL_ANALYSIS.md](PRODUCT_UX_WORKFLOW_TECHNICAL_ANALYSIS.md)

Validation:

```bash
JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/private/tmp/relateai-zscaler-cacerts -Djavax.net.ssl.trustStorePassword=changeit" \
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest \
  --tests com.example.core.automation.sender.EmailSubjectBuilderTest \
  :app:testDebugUnitTest \
  --tests com.example.core.gemini.ResponseParserTest \
  --tests com.example.ui.viewmodel.EventsViewModelTest \
  --tests com.example.ui.viewmodel.AutomationSetupViewModelTest \
  --tests com.example.ui.viewmodel.SettingsViewModelTest \
  --tests com.example.ui.screens.events.EventsScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --no-configuration-cache
```

Result: passed.

```bash
JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/private/tmp/relateai-zscaler-cacerts -Djavax.net.ssl.trustStorePassword=changeit" \
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:model:test testDebugUnitTest lintDebug assembleDebug --no-configuration-cache
```

Result: passed.

`git diff --check` passed. Static stale-reference scan passed for the removed debug suffix, hard-coded shortcut package target, removed Google API client aliases, and retired email-ready string id. Gradle needed the temporary `/private/tmp/relateai-zscaler-cacerts` trust store because Maven Central traffic is intercepted by a locally trusted Zscaler certificate that the Homebrew JDK trust store does not include.

## 2026-06-26 - ActivityLogSeverity Logging and Display

Completed tasks:

- T604 slice: Introduce `ActivityLogSeverity` and move activity-log severity writes/display mapping away from duplicated raw `"INFO"`/`"WARNING"`/`"ERROR"` literals.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/model/ActivityLogSeverity.kt](core/domain/src/main/kotlin/com/example/domain/model/ActivityLogSeverity.kt)
- [core/domain/src/main/kotlin/com/example/core/db/entities/ActivityLogEntity.kt](core/domain/src/main/kotlin/com/example/core/db/entities/ActivityLogEntity.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt)
- [app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt](app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt)
- [app/src/main/java/com/example/ui/screens/activity/ActivityHistoryScreen.kt](app/src/main/java/com/example/ui/screens/activity/ActivityHistoryScreen.kt)
- [core/domain/src/test/kotlin/com/example/domain/model/ActivityLogSeverityTest.kt](core/domain/src/test/kotlin/com/example/domain/model/ActivityLogSeverityTest.kt)
- [app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added `ActivityLogSeverity` with normalization for persisted activity-log severities.
- Dispatch activity recording now accepts typed severity values and persists `.raw` only at the `ActivityLogEntity` boundary.
- Wish Preview AI-feedback logs and `ActivityLogEntity` defaults now derive severity from `ActivityLogSeverity.INFO.raw`.
- Activity History severity color mapping now parses raw log values through `ActivityLogSeverity.fromRaw()`.

Why this improves user experience:

- Activity History severity colors now use one shared severity vocabulary for informational, warning, and error records.
- Future severity-label changes are less likely to make audit colors, dispatch blockers, and feedback logs drift apart.

How user effort is reduced:

- Users get clearer and more consistent visual priority cues while scanning Activity History.
- Maintainers can update activity severity semantics in `ActivityLogSeverity` instead of chasing duplicated strings across logging and UI code.

How user control is preserved:

- Room still stores raw activity-log severity strings for schema, backup/import, and existing-log compatibility.
- Dispatch, approval, scheduling, feedback, retry, filtering, and navigation behavior are unchanged.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest \
  --tests com.example.domain.model.ActivityLogSeverityTest \
  --no-configuration-cache

JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.DispatchMessageUseCaseTest \
  --tests com.example.ui.viewmodel.WishPreviewViewModelTest \
  --no-configuration-cache
```

Result: passed.

Targeted activity-log severity scan passed; raw severity writes are removed from the touched dispatch, feedback, entity-default, and Activity History display paths.

`git diff --check` passed.

Remaining T604 work: Room SQL status predicates/defaults, serialized fixtures, pending-message fixture literals, event-type taxonomy, and broader approval/status/relationship cleanup remain open.

## 2026-06-26 - DispatchActivityDecision Metadata Contract

Completed tasks:

- T604 slice: Introduce `DispatchActivityDecision` and move dispatch activity metadata decisions away from duplicated raw `"DEFERRED"`/`"NEEDS_APPROVAL"`/`"EXPIRED"`/`"BLOCKED"`/`"SENT"` literals.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/model/DispatchActivityDecision.kt](core/domain/src/main/kotlin/com/example/domain/model/DispatchActivityDecision.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt)
- [core/domain/src/test/kotlin/com/example/domain/model/DispatchActivityDecisionTest.kt](core/domain/src/test/kotlin/com/example/domain/model/DispatchActivityDecisionTest.kt)
- [app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added `DispatchActivityDecision` with normalization for persisted/serialized dispatch audit decision labels.
- Dispatch activity recording now accepts typed decision values and serializes `.raw` only when constructing activity metadata JSON.
- Dispatch use-case tests now assert metadata decision values through the shared decision model.

Why this improves user experience:

- Activity History dispatch audit cards now share one decision vocabulary for deferred, approval-gated, expired, blocked, and sent outcomes.
- Future decision-label changes are less likely to make audit logs, dispatch blockers, and troubleshooting cues drift apart.

How user effort is reduced:

- Users get more reliable dispatch explanations when reviewing why a message was delayed, blocked, expired, or sent.
- Maintainers can update dispatch audit decision semantics in `DispatchActivityDecision` instead of chasing duplicated metadata strings.

How user control is preserved:

- Activity metadata JSON still stores the same raw decision labels for backup/import and existing log compatibility.
- Dispatch eligibility, approval, scheduling, sending, contact-not-found handling, and retry behavior are unchanged.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest \
  --tests com.example.domain.model.DispatchActivityDecisionTest \
  --no-configuration-cache

JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.DispatchMessageUseCaseTest \
  --no-configuration-cache
```

Result: passed.

Targeted dispatch decision scan passed; raw dispatch decision labels are limited to `DispatchActivityDecision` definitions in the touched dispatch path.

`git diff --check` passed.

Remaining T604 work: Room SQL status predicates/defaults, serialized fixtures, pending-message fixture literals, event-type taxonomy, and broader approval/status/relationship cleanup remain open.

## 2026-06-26 - ActivityLogStatus Dispatch and Feedback Logs

Completed tasks:

- T604 slice: Introduce `ActivityLogStatus` and move dispatch activity records, Wish Preview feedback activity, and ActivityLogEntity defaults away from duplicated raw `"OPEN"`/`"RESOLVED"` status literals.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/model/ActivityLogStatus.kt](core/domain/src/main/kotlin/com/example/domain/model/ActivityLogStatus.kt)
- [core/domain/src/main/kotlin/com/example/core/db/entities/ActivityLogEntity.kt](core/domain/src/main/kotlin/com/example/core/db/entities/ActivityLogEntity.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt)
- [app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt](app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt)
- [core/domain/src/test/kotlin/com/example/domain/model/ActivityLogStatusTest.kt](core/domain/src/test/kotlin/com/example/domain/model/ActivityLogStatusTest.kt)
- [app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added `ActivityLogStatus` with normalization for persisted activity-log statuses.
- Dispatch activity records now accept typed `ActivityLogStatus` values and persist `.raw` only at the `ActivityLogEntity` boundary.
- Wish Preview AI-feedback activity now writes `ActivityLogStatus.OPEN.raw`.
- `ActivityLogEntity` Kotlin default now derives from `ActivityLogStatus.OPEN.raw`; the Room SQL default remains raw for schema compatibility.

Why this improves user experience:

- Activity History filters and dispatch audit records now share a single status model for open and resolved work.
- Future changes to activity status labels are less likely to make dispatch blockers, feedback follow-ups, or Activity History filters drift apart.

How user effort is reduced:

- Users get more trustworthy audit states when troubleshooting dispatch, feedback, and recovery workflows.
- Maintainers can update activity-log status semantics in `ActivityLogStatus` instead of chasing open/resolved literals across workflow code.

How user control is preserved:

- Room still stores raw activity-log status strings for backup/import and query compatibility.
- No dispatch, approval, scheduling, feedback, retry, or navigation behavior changed; only status construction was centralized.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest \
  --tests com.example.domain.model.ActivityLogStatusTest \
  --no-configuration-cache

JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.DispatchMessageUseCaseTest \
  --tests com.example.ui.viewmodel.WishPreviewViewModelTest \
  --no-configuration-cache
```

Result: passed.

Targeted activity-log status scan passed; raw open/resolved statuses are limited to `ActivityLogStatus` definitions in the touched production surface.

`git diff --check` passed.

Remaining T604 work: Room SQL status predicates/defaults, serialized fixtures, pending-message fixture literals, event-type taxonomy, and broader approval/status/relationship cleanup remain open.

## 2026-06-26 - MessageDeliveryStatus Routing and Analytics

Completed tasks:

- T604 slice: Introduce `MessageDeliveryStatus` for sent-message delivery state and move routing history, SMS dispatch writes, SMS callbacks, and Analytics reliability filtering off duplicated raw delivery-status strings.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/model/MessageDeliveryStatus.kt](core/domain/src/main/kotlin/com/example/domain/model/MessageDeliveryStatus.kt)
- [core/domain/src/main/kotlin/com/example/domain/automation/AutoSendChannelSelector.kt](core/domain/src/main/kotlin/com/example/domain/automation/AutoSendChannelSelector.kt)
- [core/data/src/main/kotlin/com/example/core/automation/sender/MessageDispatcher.kt](core/data/src/main/kotlin/com/example/core/automation/sender/MessageDispatcher.kt)
- [core/data/src/main/kotlin/com/example/core/automation/sender/SmsStatusReceiver.kt](core/data/src/main/kotlin/com/example/core/automation/sender/SmsStatusReceiver.kt)
- [app/src/main/java/com/example/ui/viewmodel/AnalyticsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/AnalyticsViewModel.kt)
- [core/domain/src/main/kotlin/com/example/core/db/entities/SentMessageEntity.kt](core/domain/src/main/kotlin/com/example/core/db/entities/SentMessageEntity.kt)
- [core/domain/src/test/kotlin/com/example/domain/model/MessageDeliveryStatusTest.kt](core/domain/src/test/kotlin/com/example/domain/model/MessageDeliveryStatusTest.kt)
- [core/domain/src/test/kotlin/com/example/domain/automation/AutoSendChannelSelectorTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/AutoSendChannelSelectorTest.kt)
- [app/src/test/java/com/example/core/automation/sender/SmsStatusReceiverTest.kt](app/src/test/java/com/example/core/automation/sender/SmsStatusReceiverTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/AnalyticsViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/AnalyticsViewModelTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added `MessageDeliveryStatus` with normalization for persisted sent-message delivery states.
- `AutoSendChannelSelector` now uses `MessageDeliveryStatus.fromRaw()` to decide which historical channels count as successful.
- `MessageDispatcher` and `SmsStatusReceiver` now write delivery statuses through `MessageDeliveryStatus.raw`.
- Analytics delivery reliability now parses failed delivery statuses through the typed model, including legacy casing/spacing.

Why this improves user experience:

- Route selection and delivery reliability now interpret sent-message delivery state consistently across SMS callbacks, dispatch, analytics, and future route suggestions.
- Analytics no longer overstates delivery reliability when a legacy stored value such as `" failed "` appears.

How user effort is reduced:

- Users get more trustworthy reliability metrics and fewer route suggestions based on stale or misread delivery history.
- Maintainers can update sent-message delivery semantics in `MessageDeliveryStatus` instead of chasing callback, routing, and analytics literals.

How user control is preserved:

- Room still stores raw delivery-status strings for backup/import compatibility.
- This does not change approval, scheduling, retry, send, or route override controls; it only centralizes delivery-state parsing and writes.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest \
  --tests com.example.domain.model.MessageDeliveryStatusTest \
  --tests com.example.domain.automation.AutoSendChannelSelectorTest \
  --no-configuration-cache

JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.core.automation.sender.SmsStatusReceiverTest \
  --tests com.example.ui.viewmodel.AnalyticsViewModelTest \
  --no-configuration-cache
```

Result: passed.

Targeted production delivery-status scan passed; raw delivery statuses are limited to `MessageDeliveryStatus` definitions in the touched production surface.

`git diff --check` passed.

Remaining T604 work: Room SQL status predicates/defaults, serialized fixtures, pending-message fixture literals, event-type taxonomy, and broader approval/status/relationship cleanup remain open.

## 2026-06-26 - MessageChannel Supported Literal Sweep

Completed tasks:

- T603 closing sweep: Verify supported raw channel literals are cleared from production and test fixtures outside the `MessageChannel` enum and explicit parser/legacy coverage.

Changed files:

- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Recorded the final supported-channel literal sweep after the remaining test scan showed only:
  - explicit legacy fixtures such as `LEGACY_CHANNEL` and `telegram`
  - lower-case parser coverage built from `MessageChannel.raw.lowercase()`
  - the `MessageChannel` enum raw definitions themselves
- Production source scan now shows supported `"SMS"`, `"WHATSAPP"`, and `"EMAIL"` literals only inside `MessageChannel`.

Why this improves user experience:

- Channel routing, setup diagnostics, review surfaces, blackout settings, reports, backups, automation, and message history now share one supported channel taxonomy in code and tests.
- Future channel changes are less likely to leave stale route labels, blocked states, or dispatch assumptions scattered across user-facing flows.

How user effort is reduced:

- Users are less likely to need manual cleanup, repeated setup checks, or retry investigation caused by duplicated route tokens drifting apart.
- Maintainers can make supported-channel changes through `MessageChannel` and targeted edge parsers instead of broad literal hunts.

How user control is preserved:

- This is validation/documentation cleanup; no runtime behavior changes were introduced.
- Legacy/casing parser coverage remains explicit, and raw storage payload shapes remain compatible at persistence boundaries.

Validation:

```bash
rg -n '"(SMS|EMAIL|WHATSAPP)"|channel\s*=\s*"|preferredChannel\s*=\s*"|title\s*=\s*"(SMS|EMAIL|WHATSAPP)"|assertEquals\("(SMS|EMAIL|WHATSAPP)"' \
  app/src/test core -g '*.kt'

rg -n '"(SMS|EMAIL|WHATSAPP)"|channel\s*=\s*"|preferredChannel\s*=\s*"' \
  app/src/main core -g '*.kt'
```

Result: passed. Test-scan matches are limited to intentional legacy/lower-case parser coverage and enum raw definitions; production-scan matches are limited to `MessageChannel`.

`git diff --check` passed.

Remaining taxonomy cleanup work: approval-mode, status, relationship, and explicit legacy parser cases continue under their own cleanup paths; no supported raw channel fixture sweep remains.

## 2026-06-26 - MessageChannel Automation Setup Display Fixtures

Completed tasks:

- T603 slice: Move Automation Setup SMS readiness-title fixture and assertion from raw `"SMS"` strings to `MessageChannel.SMS.raw`.

Changed files:

- [app/src/test/java/com/example/ui/viewmodel/AutomationSetupViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/AutomationSetupViewModelTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Automation Setup recommended-fix test data now derives the SMS readiness title from `MessageChannel.SMS.raw`.
- Existing lower-case Email and unsupported `telegram` parser coverage remains explicit.

Why this improves user experience:

- Tests protecting AI Doctor recommendations now track the same channel model as setup diagnostics, Settings blackout checks, routing, and dispatch.
- Future channel changes are less likely to leave setup recommendations with stale channel labels.

How user effort is reduced:

- Users benefit through safer setup guidance: fewer stale labels when diagnosing SMS permission blockers.
- Maintainers can update supported SMS values in `MessageChannel` instead of chasing setup display fixtures.

How user control is preserved:

- This is fixture-only cleanup; readiness grouping, recommendation ranking, actions, permissions, settings navigation, generation, approval, and dispatch behavior are unchanged.
- Explicit unsupported-channel coverage remains raw.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.AutomationSetupViewModelTest \
  --no-configuration-cache
```

Result: passed.

Targeted Automation Setup display fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: explicit legacy prompt/preferences coverage and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Settings Blackout Fixtures

Completed tasks:

- T603 slice: Move Settings channel blackout supported JSON tokens from raw `"SMS"`, `"EMAIL"`, and `"WHATSAPP"` strings to `MessageChannel.*.raw`.

Changed files:

- [app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Settings blackout load fixtures now build supported SMS/Email JSON tokens from `MessageChannel.raw`, while retaining explicit `LEGACY_CHANNEL` coverage.
- Blackout save assertions now derive Email/SMS/WhatsApp JSON output from `MessageChannel.raw`.

Why this improves user experience:

- Tests protecting blackout settings now track the same channel model as Settings UI state, readiness checks, routing, and dispatch.
- Future channel changes are less likely to leave blackout toggles or saved JSON assertions with stale route tokens.

How user effort is reduced:

- Users benefit through safer blackout settings: fewer confusing blocked-route states after changing channel availability.
- Maintainers can update supported channel values in `MessageChannel` instead of chasing Settings JSON fixtures.

How user control is preserved:

- This is fixture-only cleanup; blackout toggles, legacy filtering, save order, unknown-channel guards, scheduling, approval, and dispatch behavior are unchanged.
- Raw JSON storage remains the Settings persistence boundary for compatibility.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.SettingsViewModelTest \
  --no-configuration-cache
```

Result: passed.

Targeted Settings blackout fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining Automation Setup title copy assertions, explicit legacy prompt/preferences coverage, and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Messages Interaction Fixtures

Completed tasks:

- T603 slice: Move Messages screen interaction supported SMS, WhatsApp, and Email fixtures from raw strings to `MessageChannel.*.raw`.

Changed files:

- [app/src/test/java/com/example/ui/screens/messages/MessagesScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/messages/MessagesScreenInteractionTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Messages task-bucket pending fixtures now derive SMS, WhatsApp, and Email route values from `MessageChannel.raw`.
- Sent-message default fixtures now derive Email route values from `MessageChannel.EMAIL.raw`.
- Existing lower-case channel parser coverage remains explicit through `MessageChannel.raw.lowercase()`.

Why this improves user experience:

- Tests protecting Needs review, Scheduled, Blocked, Sent, and Failed interaction surfaces now track the same channel model as review read models, routing, readiness, and dispatch.
- Future channel changes are less likely to leave Messages tabs with stale route display or readiness assumptions.

How user effort is reduced:

- Users benefit through safer queue scanning: fewer misleading route labels, blocked states, or recovery cues in Messages.
- Maintainers can update supported channel values in `MessageChannel` instead of chasing Messages interaction fixtures.

How user control is preserved:

- This is fixture-only cleanup; tab navigation, filters, bulk actions, edit, approve, revoke, reject, retry, and setup navigation behavior are unchanged.
- Legacy/casing parser coverage remains visible.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.screens.messages.MessagesScreenInteractionTest \
  --no-configuration-cache
```

Result: passed.

Targeted Messages interaction fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining Settings blackout JSON, Automation Setup title copy assertions, explicit legacy prompt/preferences coverage, and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Wish Preview Interaction Fixtures

Completed tasks:

- T603 slice: Move Wish Preview screen interaction supported SMS fixtures from raw `"SMS"` strings to `MessageChannel.SMS.raw`.

Changed files:

- [app/src/test/java/com/example/ui/screens/wish/WishPreviewScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/wish/WishPreviewScreenInteractionTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Wish Preview interaction send-summary and pending-message fixtures now derive SMS route values from `MessageChannel.SMS.raw`.
- Existing lower-case Email normalization coverage remains explicit through `MessageChannel.EMAIL.raw.lowercase()`.

Why this improves user experience:

- Tests protecting approval-plan rendering, draft readiness, and review actions now track the same channel model as review read models, routing, and dispatch.
- Future channel changes are less likely to leave review interactions with stale route display assumptions.

How user effort is reduced:

- Users benefit through safer preview/review behavior: fewer misleading route summaries before approval.
- Maintainers can update supported SMS values in `MessageChannel` instead of chasing Wish Preview interaction fixtures.

How user control is preserved:

- This is fixture-only cleanup; editing, feedback, regeneration, test-send, rejection, approval, draft readiness, and navigation behavior are unchanged.
- Legacy/casing normalization coverage remains visible.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.screens.wish.WishPreviewScreenInteractionTest \
  --no-configuration-cache
```

Result: passed.

Targeted Wish Preview interaction fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining Messages, Settings blackout JSON, Automation Setup title copy assertions, explicit legacy prompt/preferences coverage, and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Chat History Fixtures

Completed tasks:

- T603 slice: Move Chat History ViewModel and screen interaction supported channel fixtures from raw `"WHATSAPP"` strings to `MessageChannel.WHATSAPP.raw`.

Changed files:

- [app/src/test/java/com/example/ui/screens/chat/ChatHistoryViewModelTest.kt](app/src/test/java/com/example/ui/screens/chat/ChatHistoryViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/chat/ChatHistoryScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/chat/ChatHistoryScreenInteractionTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Chat History sent-message fixtures now derive WhatsApp route values from `MessageChannel.WHATSAPP.raw`.

Why this improves user experience:

- Tests protecting sent-message history now track the same channel model as review labels, routing, dispatch, and chat navigation.
- Future channel changes are less likely to leave chat history with stale route display assumptions.

How user effort is reduced:

- Users benefit through safer message-history presentation, reducing confusion when tracing past WhatsApp wishes.
- Maintainers can update supported WhatsApp values in `MessageChannel` instead of chasing chat history fixtures.

How user control is preserved:

- This is fixture-only cleanup; chat history loading, back navigation, message content, sent timestamps, and delivery-status behavior are unchanged.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.screens.chat.ChatHistoryViewModelTest \
  --tests com.example.ui.screens.chat.ChatHistoryScreenInteractionTest \
  --no-configuration-cache
```

Result: passed.

Targeted chat history fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining Wish Preview, Messages, Settings blackout JSON, Automation Setup title copy assertions, explicit legacy prompt/preferences coverage, and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Contact UI Fixtures

Completed tasks:

- T603 slice: Move Contact Detail quality/body-section and Contact List supported channel fixtures from raw `"SMS"`/`"EMAIL"` strings to `MessageChannel.*.raw`.

Changed files:

- [app/src/test/java/com/example/ui/screens/contacts/ContactDetailPersonalizationQualityCardTest.kt](app/src/test/java/com/example/ui/screens/contacts/ContactDetailPersonalizationQualityCardTest.kt)
- [app/src/test/java/com/example/ui/screens/contacts/ContactDetailBodySectionsTest.kt](app/src/test/java/com/example/ui/screens/contacts/ContactDetailBodySectionsTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/ContactListViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/ContactListViewModelTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Contact Detail personalization-quality and body-section fixtures now derive supported Email/SMS preferences from `MessageChannel.raw`.
- Contact List action-filter and quality-state fixtures now use the same typed Email source for missing-channel scenarios.

Why this improves user experience:

- Tests protecting contact cleanup, personalization readiness, and contact-detail action surfaces now track the same channel model as preferences, setup diagnostics, review, and dispatch.
- Future channel changes are less likely to leave contact quality or missing-channel UI with stale route assumptions.

How user effort is reduced:

- Users benefit through safer contact cleanup cues: fewer misleading missing-channel states and less repeated profile editing after channel-model changes.
- Maintainers can update supported SMS/Email values in `MessageChannel` instead of chasing contact UI fixtures.

How user control is preserved:

- This is fixture-only cleanup; contact filters, quality labels, detail actions, preference saves, generation, approval, and dispatch behavior are unchanged.
- Explicit legacy-channel tests remain raw so fallback behavior stays visible.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.screens.contacts.ContactDetailPersonalizationQualityCardTest \
  --tests com.example.ui.screens.contacts.ContactDetailBodySectionsTest \
  --tests com.example.ui.viewmodel.ContactListViewModelTest \
  --no-configuration-cache
```

Result: passed.

Targeted contact UI fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining Wish Preview, Messages, Chat, Settings blackout JSON, Automation Setup title copy assertions, explicit legacy prompt/preferences coverage, and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Dashboard Style Fixtures

Completed tasks:

- T603 slice: Move dashboard metrics and style-analysis supported channel fixtures from raw `"SMS"` strings to `MessageChannel.SMS.raw`.

Changed files:

- [app/src/test/java/com/example/domain/usecase/GetDashboardMetricsUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/GetDashboardMetricsUseCaseTest.kt)
- [app/src/test/java/com/example/domain/usecase/StyleAnalysisUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/StyleAnalysisUseCaseTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Dashboard pending-message fixtures now derive SMS route values from `MessageChannel.SMS.raw`.
- Style-analysis sent-message fixtures now use the same typed SMS source.

Why this improves user experience:

- Tests protecting dashboard counts and style learning now track the same channel model as routing, reporting, review, and dispatch.
- Future channel changes are less likely to leave dashboard or style-analysis test data with stale route tokens.

How user effort is reduced:

- Users benefit through safer operational summaries and style learning inputs, reducing confusing metrics or stale route assumptions during diagnosis.
- Maintainers can update supported SMS values in `MessageChannel` instead of chasing dashboard/style fixtures.

How user control is preserved:

- This is fixture-only cleanup; dashboard metrics, style analysis, profile saves, scheduling, approval, and dispatch behavior are unchanged.
- Status and approval-mode literals remain scoped to their existing cleanup paths.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.GetDashboardMetricsUseCaseTest \
  --tests com.example.domain.usecase.StyleAnalysisUseCaseTest \
  --no-configuration-cache
```

Result: passed.

Targeted dashboard/style fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining UI interaction, chat, contact, settings blackout JSON, analytics UI, and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Automation Policy Fixtures

Completed tasks:

- T603 slice: Move automation policy, notification-action, SMS receiver, and end-to-end automation pipeline supported channel fixtures from raw `"SMS"` strings to `MessageChannel.SMS.raw`.

Changed files:

- [core/domain/src/test/kotlin/com/example/domain/automation/DispatchEligibilityPolicyTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/DispatchEligibilityPolicyTest.kt)
- [core/domain/src/test/kotlin/com/example/domain/automation/RevivalCadencePolicyTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/RevivalCadencePolicyTest.kt)
- [core/data/src/test/kotlin/com/example/core/automation/notifications/ApprovalNotificationActionPolicyTest.kt](core/data/src/test/kotlin/com/example/core/automation/notifications/ApprovalNotificationActionPolicyTest.kt)
- [app/src/test/java/com/example/core/automation/sender/SmsStatusReceiverTest.kt](app/src/test/java/com/example/core/automation/sender/SmsStatusReceiverTest.kt)
- [app/src/test/java/com/example/core/automation/AutomationPipelineTest.kt](app/src/test/java/com/example/core/automation/AutomationPipelineTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Dispatch eligibility, revival cadence, and notification approval policy fixtures now derive pending-message SMS routes from `MessageChannel.SMS.raw`.
- SMS delivery-status receiver rows and the end-to-end automation pipeline contact route fixture now use the same typed SMS source.

Why this improves user experience:

- Tests protecting dispatch gates, revival duplicate prevention, notification approval, SMS delivery tracking, and the automation pipeline now track the same channel model as routing and dispatch.
- Future channel changes are less likely to create stale automation route assumptions that surface as failed sends or confusing approval states.

How user effort is reduced:

- Users benefit through safer automation: fewer manual retries, duplicate draft investigations, and delivery-status troubleshooting caused by route-token drift.
- Maintainers can update supported SMS values in `MessageChannel` instead of chasing automation-policy fixtures.

How user control is preserved:

- This is fixture-only cleanup; approval gates, quiet hours, revival cadence, notification actions, SMS status transitions, generation, scheduling, and dispatch behavior are unchanged.
- Status and approval-mode literals remain scoped to their existing cleanup paths.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest \
  --tests com.example.domain.automation.DispatchEligibilityPolicyTest \
  --tests com.example.domain.automation.RevivalCadencePolicyTest \
  --no-configuration-cache

JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest \
  --tests com.example.core.automation.notifications.ApprovalNotificationActionPolicyTest \
  --no-configuration-cache

JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.core.automation.sender.SmsStatusReceiverTest \
  --tests com.example.core.automation.AutomationPipelineTest \
  --no-configuration-cache
```

Result: passed.

Targeted automation-policy fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining UI interaction, dashboard/style, chat, contact, settings blackout JSON, analytics UI, and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Persistence Reporting Fixtures

Completed tasks:

- T603 slice: Move analytics, backup, DAO, and pending-entity supported channel fixtures from raw `"SMS"` strings to `MessageChannel.SMS.raw`.

Changed files:

- [core/data/src/test/kotlin/com/example/core/analytics/AnalyticsReportServiceImplTest.kt](core/data/src/test/kotlin/com/example/core/analytics/AnalyticsReportServiceImplTest.kt)
- [core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt](core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt)
- [app/src/test/java/com/example/core/db/DaoTest.kt](app/src/test/java/com/example/core/db/DaoTest.kt)
- [core/domain/src/test/kotlin/com/example/core/db/entities/PendingMessageEntityTest.kt](core/domain/src/test/kotlin/com/example/core/db/entities/PendingMessageEntityTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Analytics report and backup seed rows now derive sent/pending channel payloads from `MessageChannel.SMS.raw`.
- DAO helper rows and the pending-entity default-status fixture now use the same typed SMS source before writing Room-shaped strings.

Why this improves user experience:

- Tests protecting relationship reports, backup/export restoreability, and DAO persistence now track the same channel model as routing, review, readiness, and dispatch.
- Future channel changes are less likely to leave reports or restored drafts with stale route tokens.

How user effort is reduced:

- Users benefit through safer backup/reporting behavior: fewer restored-route surprises and less troubleshooting when audit/report data is inspected.
- Maintainers can update supported SMS storage values in `MessageChannel` instead of chasing persistence-shaped fixtures.

How user control is preserved:

- This is fixture-only cleanup; report generation, backup export/import, DAO behavior, pending defaults, scheduling, approval, and dispatch are unchanged.
- Backup payloads and Room rows still store raw strings at persistence boundaries for compatibility.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest \
  --tests com.example.core.analytics.AnalyticsReportServiceImplTest \
  --tests com.example.core.backup.BackupServiceImplTest \
  --no-configuration-cache

JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.core.db.DaoTest \
  --no-configuration-cache

JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest \
  --tests com.example.core.db.entities.PendingMessageEntityTest \
  --no-configuration-cache
```

Result: passed.

Targeted persistence/reporting fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining UI interaction, sender/notification, dashboard/style/domain-policy, chat, contact, settings, analytics UI, and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Review Read-Model Fixtures

Completed tasks:

- T603 slice: Move Wish Preview and Messages ViewModel supported channel fixtures from raw `"SMS"`, `"EMAIL"`, and `"WHATSAPP"` strings to `MessageChannel.*.raw`.

Changed files:

- [app/src/test/java/com/example/ui/viewmodel/WishPreviewViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/WishPreviewViewModelTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/MessagesViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/MessagesViewModelTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Wish Preview pending-summary fixtures now use `MessageChannel.SMS.raw` and `MessageChannel.EMAIL.raw`.
- Messages ViewModel failed, filter, readiness, and task-bucket fixtures now use `MessageChannel.*.raw`.

Why this improves user experience:

- Tests protecting review summaries, channel filters, readiness buckets, and task-state queues now track the same channel model as setup diagnostics, generation, routing, review labels, and dispatch.
- Future channel changes are less likely to create stale review/readiness state shown to users.

How user effort is reduced:

- Users benefit through safer review queues: fewer manual corrections, filter mismatches, and no-route surprises caused by stale route tokens.
- Maintainers can adjust supported channel values through `MessageChannel` instead of chasing read-model fixtures.

How user control is preserved:

- This is fixture-only cleanup; review summary, filtering, readiness bucketing, approval, rejection, regeneration, scheduling, and send behavior are unchanged.
- Raw persisted channel strings remain isolated at storage/read boundaries and are still parsed before display logic.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.WishPreviewViewModelTest \
  --tests com.example.ui.viewmodel.MessagesViewModelTest \
  --no-configuration-cache
```

Result: passed.

Targeted read-model fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining DB/backup/UI interaction/analytics/DAO fixture groups and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Review Action Fixtures

Completed tasks:

- T603 slice: Move approve, reject, and revoke use-case supported channel fixtures from raw `"SMS"` strings to `MessageChannel.SMS.raw`.

Changed files:

- [app/src/test/java/com/example/domain/usecase/ApprovePendingMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/ApprovePendingMessageUseCaseTest.kt)
- [app/src/test/java/com/example/domain/usecase/RejectPendingMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/RejectPendingMessageUseCaseTest.kt)
- [app/src/test/java/com/example/domain/usecase/RevokeApprovalUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/RevokeApprovalUseCaseTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Approve, reject, and revoke pending-message fixtures now set `channel = MessageChannel.SMS.raw`.
- Review-action tests no longer duplicate supported SMS route literals.

Why this improves user experience:

- Tests protecting explicit review controls now track the same route model as generation, regeneration, routing, review labels, and dispatch.
- Future channel changes are less likely to leave approve/reject/revoke tests green while review actions use stale route tokens.

How user effort is reduced:

- Users benefit through safer review actions: fewer route-related surprises after approving, rejecting, or revoking a draft.
- Maintainers can adjust supported channel values through `MessageChannel` instead of chasing review-action fixtures.

How user control is preserved:

- This is fixture-only cleanup; approve, reject, revoke, schedule, cancel, and status behavior are unchanged.
- Status and approval-mode literals remain scoped to their existing T602/T604 cleanup paths.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.ApprovePendingMessageUseCaseTest \
  --tests com.example.domain.usecase.RejectPendingMessageUseCaseTest \
  --tests com.example.domain.usecase.RevokeApprovalUseCaseTest \
  --no-configuration-cache
```

Result: passed.

Targeted review-action fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining DB/backup/UI/view-model/analytics fixture groups and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Regenerate Use-Case Fixtures

Completed tasks:

- T603 slice: Move `RegeneratePendingMessageUseCaseTest` supported channel fixtures and assertions from raw `"SMS"` strings to `MessageChannel.SMS.raw`.

Changed files:

- [app/src/test/java/com/example/domain/usecase/RegeneratePendingMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/RegeneratePendingMessageUseCaseTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Regeneration pending-message helper defaults now use `MessageChannel.SMS.raw`.
- No-route regeneration contact fixtures and saved-channel assertions now compare against `MessageChannel.SMS.raw`.

Why this improves user experience:

- Tests protecting the user-visible regenerate workflow now track the same channel model as generation, routing, review, dispatch, and setup diagnostics.
- Future channel changes are less likely to create regenerated drafts with stale route values that users must correct before approval or retry.

How user effort is reduced:

- Users benefit through safer regeneration behavior: fewer mismatched routes, no-route surprises, and repeated manual edits after asking AI for a better draft.
- Maintainers can update supported channel values in `MessageChannel` instead of chasing duplicated regeneration fixtures.

How user control is preserved:

- This is fixture-only cleanup; regeneration behavior, feedback forwarding, edited-text clearing, approval downgrade, no-route review forcing, scheduling, and dispatch are unchanged.
- Status and approval-mode literals remain scoped to their existing T602/T604 cleanup paths.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.RegeneratePendingMessageUseCaseTest \
  --no-configuration-cache
```

Result: passed.

Targeted regenerate fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining approval/reject/revoke/DB/backup/UI/view-model fixture groups and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Dispatch Worker Fixtures

Completed tasks:

- T603 slice: Move `MessageDispatchWorkerTest` supported channel fixtures from raw `"SMS"` strings to `MessageChannel.SMS.raw`.

Changed files:

- [app/src/test/java/com/example/core/automation/workers/MessageDispatchWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/MessageDispatchWorkerTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Dispatch worker pending-message fixtures now set `channel = MessageChannel.SMS.raw`.
- Quiet-hours, smart-approve, future-send, failure, and double-send guard scenarios now share the same typed SMS fixture source.

Why this improves user experience:

- Tests protecting the background handoff from approved drafts to actual sending now track the same route model as generation, routing, review, and dispatch use cases.
- Future channel changes are less likely to create stale worker fixtures that miss failed sends, duplicate sends, or deferred-send route drift.

How user effort is reduced:

- Users benefit through safer scheduled dispatch behavior: fewer manual retries, fewer duplicate-send investigations, and fewer setup checks caused by route-token drift.
- Maintainers can update supported channel values through `MessageChannel` instead of fixing worker-specific route literals.

How user control is preserved:

- This is fixture-only cleanup; dispatch worker behavior, approval gates, quiet-hours deferral, double-send guard, failure handling, and scheduling are unchanged.
- Status and approval-mode literals remain scoped to their existing T602/T604 cleanup paths.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.core.automation.workers.MessageDispatchWorkerTest \
  --no-configuration-cache
```

Result: passed.

Targeted dispatch-worker fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining DB/backup/UI/use-case fixture groups and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Background Worker Fixtures

Completed tasks:

- T603 slice: Move holiday, post-event follow-up, and revival worker supported channel fixtures from raw `"SMS"` strings to `MessageChannel.SMS.raw`.

Changed files:

- [app/src/test/java/com/example/core/automation/workers/HolidayWishWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/HolidayWishWorkerTest.kt)
- [app/src/test/java/com/example/core/automation/workers/PostEventFollowUpWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/PostEventFollowUpWorkerTest.kt)
- [app/src/test/java/com/example/core/automation/workers/RevivalWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/RevivalWorkerTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Background-worker contact, pending-message, and sent-message channel fixtures now use `MessageChannel.SMS.raw`.
- No-route assertions for automatically generated holiday, follow-up, and revival drafts now compare against `MessageChannel.SMS.raw`.

Why this improves user experience:

- Tests protecting background-generated drafts now track the same channel model as generation, routing, setup diagnostics, review, and dispatch.
- Future channel changes are less likely to create automated drafts with stale route values that users discover only during review or failed send recovery.

How user effort is reduced:

- Users benefit through safer background automation: fewer manual corrections, retries, and setup investigations after worker-created drafts.
- Maintainers can update supported channel values through `MessageChannel` without chasing worker-specific fixture literals.

How user control is preserved:

- This is fixture-only cleanup; holiday, follow-up, revival, approval downgrade, no-route review forcing, scheduling, notifications, and dispatch behavior are unchanged.
- Status and approval-mode literals remain scoped to their existing T602/T604 cleanup paths.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.core.automation.workers.HolidayWishWorkerTest \
  --tests com.example.core.automation.workers.PostEventFollowUpWorkerTest \
  --tests com.example.core.automation.workers.RevivalWorkerTest \
  --no-configuration-cache
```

Result: passed.

Targeted worker fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining DB/backup/UI fixture groups and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Generation Use-Case Fixtures

Completed tasks:

- T603 slice: Move `GenerateMessageUseCaseTest` supported channel fixtures and assertions from raw `"SMS"`/`"EMAIL"` strings to `MessageChannel.*.raw`.

Changed files:

- [app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Generation contact fixtures now set preferred channels through `MessageChannel.SMS.raw` and `MessageChannel.EMAIL.raw`.
- Pending-message channel assertions and helper defaults now use `MessageChannel.SMS.raw`.

Why this improves user experience:

- Tests protecting AI draft creation now track the same channel model used by routing, review, setup diagnostics, and dispatch.
- Future channel changes are less likely to create drafts with stale route tokens that users must fix during review or retry.

How user effort is reduced:

- Users benefit through safer generation behavior: fewer mismatched routes, no-route surprises, and manual corrections before approval.
- Maintainers can adjust supported channel values in the domain model instead of chasing duplicated generation fixtures.

How user control is preserved:

- This is fixture-only cleanup; generation behavior, approval downgrades, no-route review forcing, scheduling, notifications, and dispatch are unchanged.
- Status and approval-mode literals remain scoped to their existing T602/T604 cleanup paths.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.GenerateMessageUseCaseTest \
  --no-configuration-cache
```

Result: passed.

Targeted generation fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining worker/DB/backup fixture groups and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Dispatch Use-Case Fixtures

Completed tasks:

- T603 slice: Move `DispatchMessageUseCaseTest` supported channel fixtures and assertions from raw `"SMS"` strings to `MessageChannel.SMS.raw`.

Changed files:

- [app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Dispatch pending-message fixtures now set `channel = MessageChannel.SMS.raw`.
- Dispatch outcome assertions now compare against `MessageChannel.SMS.raw`.

Why this improves user experience:

- The tests protecting the final send path now track the same channel model as route selection, review labels, setup diagnostics, and dispatch.
- Future channel model changes are less likely to leave dispatch tests passing while user-visible sends use stale route tokens.

How user effort is reduced:

- Users benefit through safer delivery changes: fewer regressions that would require manual retries, setup investigation, or re-approval.
- Maintainers can adjust supported channel values through `MessageChannel` instead of fixing duplicated dispatch fixtures.

How user control is preserved:

- This is fixture-only cleanup; approval checks, deferred sends, expiration handling, activity logging, and dispatch behavior are unchanged.
- Status and approval-mode literals remain scoped to their existing T602/T604 cleanup paths.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.DispatchMessageUseCaseTest \
  --no-configuration-cache
```

Result: passed.

Targeted dispatch fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining fixture groups and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Prompt Builder Fixtures

Completed tasks:

- T603 slice: Move `PromptBuilderTest` supported channel fixtures and prompt assertions from raw channel strings to `MessageChannel.*.raw`.

Changed files:

- [app/src/test/java/com/example/core/gemini/PromptBuilderTest.kt](app/src/test/java/com/example/core/gemini/PromptBuilderTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Previous-wish channel fixtures now use `MessageChannel.SMS.raw`.
- Prompt channel assertions now use `MessageChannel.WHATSAPP.raw`.
- The email normalization fixture derives its lower-case legacy value from `MessageChannel.EMAIL.raw`; the unsupported legacy token remains as the explicit fallback case.

Why this improves user experience:

- The tests protecting Gemini prompt context now track the active SMS, WhatsApp, and Email model instead of duplicated literals.
- Future channel model changes are less likely to create mismatched prompt context that could generate route-inappropriate wishes.

How user effort is reduced:

- Users benefit indirectly because maintainers can evolve prompt context and route behavior with less fixture drift.
- Regression coverage remains focused on old stored values, reducing the chance users need to repair legacy contact data before AI generation works.

How user control is preserved:

- This is fixture-only cleanup; generated prompt text, fallback behavior, approval gates, routing, scheduling, and dispatch are unchanged.
- Unsupported legacy channel fallback remains explicitly tested.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.core.gemini.PromptBuilderTest \
  --no-configuration-cache
```

Result: passed.

Targeted prompt-builder fixture raw-channel scan passed except the deliberate unsupported legacy fallback token.

`git diff --check` passed.

Remaining T603 work: remaining fixture groups and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Route Selector Fixtures

Completed tasks:

- T603 slice: Move `AutoSendChannelSelectorTest` supported channel fixtures from raw `"SMS"`, `"WHATSAPP"`, and `"EMAIL"` strings to `MessageChannel.*.raw`.

Changed files:

- [core/domain/src/test/kotlin/com/example/domain/automation/AutoSendChannelSelectorTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/AutoSendChannelSelectorTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Route-selector test contacts and sent-message history now derive supported channel values from `MessageChannel`.
- Blackout JSON fixtures are built from `MessageChannel.raw` values.
- Lower-case channel fixtures remain only in tests that explicitly verify legacy preferred-channel normalization.

Why this improves user experience:

- The tests that protect automatic route choice now track the same channel model used by generation, dispatch, setup diagnostics, and review screens.
- Future channel model changes are less likely to leave auto-send routing tests green while the product uses stale route tokens.

How user effort is reduced:

- Users benefit indirectly through safer route-selection changes: maintainers can update route behavior without hunting duplicated test literals.
- Regression tests now better catch drift before it becomes failed sends or incorrect no-route warnings.

How user control is preserved:

- This is fixture-only cleanup; route selection, blackout behavior, approval gates, no-route review handling, and dispatch remain unchanged.
- Legacy lower-case parsing coverage remains explicit, so stored old values are still verified instead of silently dropped from tests.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest \
  --tests com.example.domain.automation.AutoSendChannelSelectorTest \
  --no-configuration-cache
```

Result: passed.

Targeted route-selector fixture raw-channel scan passed.

`git diff --check` passed.

Remaining T603 work: remaining fixture groups and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Review Screen Labels

Completed tasks:

- T603 slice: Move Wish Preview and Messages screen channel labels/icons from raw channel branches to `MessageChannel` parsing.

Changed files:

- [app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt](app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt)
- [app/src/main/java/com/example/ui/screens/messages/MessagesScreen.kt](app/src/main/java/com/example/ui/screens/messages/MessagesScreen.kt)
- [app/src/test/java/com/example/ui/screens/wish/WishPreviewScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/wish/WishPreviewScreenInteractionTest.kt)
- [app/src/test/java/com/example/ui/screens/messages/MessagesScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/messages/MessagesScreenInteractionTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Wish Preview approval-plan channel labels now render through `MessageChannel.fromRaw()`.
- Messages pending/scheduled/blocked/sent cards now parse stored channel strings before choosing icons and localized labels.
- Compose tests now cover legacy-cased/padded Email and WhatsApp channel values rendering as user-facing labels.

Why this improves user experience:

- Review screens now show stable, localized channel labels even when persisted rows contain legacy casing or whitespace.
- Channel icons on message cards stay aligned with the same SMS, WhatsApp, and Email model used by routing and setup diagnostics.

How user effort is reduced:

- Users do not need to re-save drafts or contacts to clean up visible channel casing in review surfaces.
- Maintainers can adjust channel parsing in `MessageChannel` without updating separate UI string branches.

How user control is preserved:

- Unknown legacy channel values are still displayed as stored instead of being silently coerced into a supported route.
- This only changes read/display behavior; approval, rejection, retry, scheduling, and dispatch decisions remain unchanged.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.screens.wish.WishPreviewScreenInteractionTest \
  --tests com.example.ui.screens.messages.MessagesScreenInteractionTest \
  --no-configuration-cache
```

Result: passed.

Targeted review-screen raw channel display scan passed.

`git diff --check` passed.

Remaining T603 work: remaining fixtures and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Setup Email Readiness

Completed tasks:

- T603 slice: Move Automation Setup email-preferred contact diagnostics from a raw `"EMAIL"` comparison to `MessageChannel`.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/AutomationSetupViewModel.kt](app/src/main/java/com/example/ui/viewmodel/AutomationSetupViewModel.kt)
- [app/src/test/java/com/example/ui/viewmodel/AutomationSetupViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/AutomationSetupViewModelTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Automation Setup now counts email-preferred contacts with `MessageChannel.fromRaw(contact.preferredChannel) == MessageChannel.EMAIL`.
- The ViewModel test now covers legacy-cased and padded stored email channel values so setup diagnostics stay tied to the active parser.

Why this improves user experience:

- The AI Doctor email readiness card now uses the same channel model as contact preferences, routing, and prompt context.
- Users with legacy-cased stored channel values still get the right Gmail setup warning before email wishes fail.

How user effort is reduced:

- Users do not need to re-save contacts just to make Automation Setup recognize Email preferences.
- Maintainers can adjust channel parsing in `MessageChannel` without tracking separate setup-screen string checks.

How user control is preserved:

- The diagnostic only reports setup readiness; it does not change contact preferences, Gmail settings, approval mode, scheduling, or dispatch.
- Unsupported legacy channel values remain ignored by the email-preferred count instead of being coerced into Email.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.AutomationSetupViewModelTest \
  --no-configuration-cache
```

Result: passed.

Targeted setup raw-email comparison scan passed.

`git diff --check` passed.

Remaining T603 work: remaining UI labels/fixtures and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Contact Storage Defaults

Completed tasks:

- T603 slice: Move new-contact preferred-channel storage defaults from raw `"SMS"` literals to `MessageChannel.SMS.raw`.

Changed files:

- [core/domain/src/main/kotlin/com/example/core/db/entities/ContactEntity.kt](core/domain/src/main/kotlin/com/example/core/db/entities/ContactEntity.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/SaveManualEventUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/SaveManualEventUseCase.kt)
- [core/domain/src/test/kotlin/com/example/core/db/entities/ContactEntityTest.kt](core/domain/src/test/kotlin/com/example/core/db/entities/ContactEntityTest.kt)
- [app/src/test/java/com/example/domain/usecase/SaveManualEventUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/SaveManualEventUseCaseTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `ContactEntity.preferredChannel` now defaults to `MessageChannel.SMS.raw`.
- `SaveManualEventUseCase` now creates new manual contacts with `MessageChannel.SMS.raw`.
- Entity and manual-event tests now assert that new contact defaults stay tied to the active channel model.

Why this improves user experience:

- Manual contacts and imported/default contacts now start from the same channel model used by Settings, Contact Detail, routing, and AI prompt context.
- Future channel changes are less likely to leave newly created manual contacts with stale route defaults.

How user effort is reduced:

- Users do not need to fix divergent default channel behavior after adding a manual event contact.
- Maintainers can adjust the default channel through `MessageChannel` instead of tracking separate raw literals.

How user control is preserved:

- The persisted default value is still `SMS`, so existing Room rows, backup payloads, and visible channel choices remain compatible.
- This only sets a default for new contacts; users still explicitly change preferred channel in Contact Detail and Settings.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest :app:testDebugUnitTest \
  --tests com.example.core.db.entities.ContactEntityTest \
  --tests com.example.domain.usecase.SaveManualEventUseCaseTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T603 work: remaining UI labels/fixtures and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Channel Blackout Policy Boundary

Completed tasks:

- T603 slice: Move `AutomationSchedulePolicy.isChannelBlocked()` from raw channel strings to `MessageChannel`.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/automation/AutomationSchedulePolicy.kt](core/domain/src/main/kotlin/com/example/domain/automation/AutomationSchedulePolicy.kt)
- [app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt](app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt)
- [core/domain/src/test/kotlin/com/example/domain/automation/AutomationSchedulePolicyTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/AutomationSchedulePolicyTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `AutomationSchedulePolicy.isChannelBlocked()` now accepts `MessageChannel`.
- The policy parses blackout JSON into typed channels and filters unsupported legacy values.
- Messages readiness now parses each pending-message channel once with `MessageChannel.fromRaw()` before checking blackout and route prerequisites.
- `MessageChannel.UNKNOWN` is never treated as a configured blackout token; Messages still marks unknown routes as disabled.

Why this improves user experience:

- Blocked-message readiness is less likely to drift because blackout checks and route prerequisite checks now use the same channel model.
- Legacy or malformed blackout entries no longer influence whether users see a message as blocked.

How user effort is reduced:

- Users do not need to repair old blackout payloads or channel casing before Messages can explain why a draft is blocked.
- Future channel blackout behavior changes can be made in one typed policy instead of call-site string comparisons.

How user control is preserved:

- The same SMS, WhatsApp, and Email blackout settings remain available.
- No message is approved, rejected, scheduled, retried, or dispatched by this change.
- Unknown pending-message routes still remain blocked for explicit user/setup recovery.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest :app:testDebugUnitTest \
  --tests com.example.domain.automation.AutomationSchedulePolicyTest \
  --tests com.example.ui.viewmodel.MessagesViewModelTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T603 work: remaining UI labels/fixtures and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel AI Prompt Context Boundary

Completed tasks:

- T603 slice: Move AI prompt preferred-channel context from raw strings to `MessageChannel`.

Changed files:

- [core/data/src/main/kotlin/com/example/core/gemini/PromptBuilder.kt](core/data/src/main/kotlin/com/example/core/gemini/PromptBuilder.kt)
- [app/src/test/java/com/example/core/gemini/PromptBuilderTest.kt](app/src/test/java/com/example/core/gemini/PromptBuilderTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `ContactContextObject.preferredChannel` now stores `MessageChannel` instead of a raw string.
- `PromptBuilder.buildContactContext()` maps stored contact channel strings through `MessageChannel.fromRaw()` at the prompt-builder edge.
- Unsupported legacy prompt-context channels fall back to SMS before the AI prompt is built.
- The generated prompt still emits the compatible raw channel label with `preferredChannel.raw`.

Why this improves user experience:

- AI generation receives a normalized channel context, reducing confusing or unsupported channel names in prompts.
- Unsupported legacy contact data no longer leaks into the message-writing prompt as an invented route.

How user effort is reduced:

- Users do not need to repair old contact channel values before generating AI wishes.
- Future channel prompt behavior can be updated through `MessageChannel` rather than prompt-specific string defaults.

How user control is preserved:

- The prompt still describes only the same explicit SMS, WhatsApp, and Email channels.
- This does not change approval, route selection, dispatch, scheduling, contact preferences, or stored channel values.
- Unsupported prompt channel data is normalized for prompt clarity only; delivery controls remain governed by route readiness and user approval gates.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.core.gemini.PromptBuilderTest \
  --tests com.example.core.gemini.AiServiceImplTest \
  --tests com.example.core.gemini.ResponseParserTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T603 work: remaining UI labels/fixtures and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Delivery Routing Boundary

Completed tasks:

- T603 slice: Move generation-time route selection and runtime delivery route resolution from raw channel strings to `MessageChannel`.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/automation/AutoSendChannelSelector.kt](core/domain/src/main/kotlin/com/example/domain/automation/AutoSendChannelSelector.kt)
- [core/data/src/main/kotlin/com/example/core/automation/sender/DeliveryChannelResolver.kt](core/data/src/main/kotlin/com/example/core/automation/sender/DeliveryChannelResolver.kt)
- [core/data/src/main/kotlin/com/example/core/automation/sender/MessageDispatcher.kt](core/data/src/main/kotlin/com/example/core/automation/sender/MessageDispatcher.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/RegeneratePendingMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/RegeneratePendingMessageUseCase.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/HolidayWishWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/HolidayWishWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/PostEventFollowUpWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/PostEventFollowUpWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt)
- [core/domain/src/test/kotlin/com/example/domain/automation/AutoSendChannelSelectorTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/AutoSendChannelSelectorTest.kt)
- [core/data/src/test/kotlin/com/example/core/automation/sender/DeliveryChannelResolverTest.kt](core/data/src/test/kotlin/com/example/core/automation/sender/DeliveryChannelResolverTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `AutoSendChannelSelector` now returns `MessageChannel` selections and typed available-route sets.
- `DeliveryChannelResolver.resolveRoutes()` now accepts a typed preferred channel and typed blocked-channel set, and returns `List<MessageChannel>`.
- `MessageDispatcher` parses pending-message and blackout storage strings at the dispatch edge, then routes with typed channels and writes `.raw` only to sent-message persistence/log payloads.
- Foreground generation, regeneration, holiday, follow-up, and revival paths now persist `channelSelection.channel.raw` after receiving typed route decisions.

Why this improves user experience:

- Draft generation and actual dispatch now share typed route decisions, reducing route drift between "ready to send" and runtime delivery.
- Unsupported legacy route names are made explicit as `MessageChannel.UNKNOWN` and fall back to phone-first automatic routing where appropriate.

How user effort is reduced:

- Users are less likely to approve or wait on a draft whose generated route and dispatch route disagree.
- Future channel behavior changes can be made in one active channel model instead of duplicated selector/resolver string branches.

How user control is preserved:

- Persisted pending/sent message channel strings remain compatible.
- No approval, scheduling, fallback, retry, or dispatch gate is bypassed; typed route policies only choose among the same SMS, WhatsApp, and Email options.
- No-route generation still downgrades to explicit review instead of automatic delivery.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest :core:data:testDebugUnitTest :app:testDebugUnitTest \
  --tests com.example.domain.automation.AutoSendChannelSelectorTest \
  --tests com.example.core.automation.sender.DeliveryChannelResolverTest \
  --tests com.example.domain.usecase.GenerateMessageUseCaseTest \
  --tests com.example.domain.usecase.RegeneratePendingMessageUseCaseTest \
  --tests com.example.core.automation.workers.HolidayWishWorkerTest \
  --tests com.example.core.automation.workers.PostEventFollowUpWorkerTest \
  --tests com.example.core.automation.workers.RevivalWorkerTest \
  --tests com.example.core.automation.workers.MessageDispatchWorkerTest \
  --tests com.example.domain.usecase.DispatchMessageUseCaseTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T603 work: remaining UI labels/fixtures and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Settings Channel Blackout Boundary

Completed tasks:

- T603 slice: Move Settings channel blackout controls from raw channel strings to `MessageChannel`.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt)
- [app/src/main/java/com/example/ui/screens/settings/SettingsScreen.kt](app/src/main/java/com/example/ui/screens/settings/SettingsScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `SettingsViewModel.toggleChannelBlackout()` now accepts `MessageChannel` instead of raw strings.
- Settings screen blackout switches now pass `MessageChannel.SMS`, `MessageChannel.WHATSAPP`, and `MessageChannel.EMAIL`.
- Settings parses persisted blackout JSON into typed channels at the `SecurePrefs` edge and filters unknown legacy values.
- Blackout saves still write compact raw JSON arrays for compatibility, but only from typed channel values.
- `MessageChannel.UNKNOWN` is ignored and cannot be persisted as a disabled channel.

Why this improves user experience:

- Settings blackout switches cannot drift from the active channel model or save unsupported route names.
- Legacy or malformed blackout entries no longer surface as broken Settings state.

How user effort is reduced:

- Users do not need to repair old channel-blackout preference payloads before changing Settings.
- Future channel changes now have one enum boundary to update instead of scattered Settings literals.

How user control is preserved:

- The same explicit SMS, WhatsApp, and Email blackout switches remain available.
- Existing `SecurePrefs` storage and backup payload shape remain compatible.
- This only changes Settings preference mapping; approval, scheduling, dispatch, contact preferences, and backup/restore behavior are unchanged.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.SettingsViewModelTest \
  --tests com.example.ui.screens.settings.SettingsScreenInteractionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T603 work: remaining UI labels/fixtures and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageChannel Contact Preference Boundary

Completed tasks:

- T603 slice: Move Contact Detail and contact preference channel saves from raw strings to `MessageChannel`.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/usecase/UpdateContactPreferencesUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/UpdateContactPreferencesUseCase.kt)
- [app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt](app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt)
- [app/src/test/java/com/example/domain/usecase/UpdateContactPreferencesUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/UpdateContactPreferencesUseCaseTest.kt)
- [app/src/test/java/com/example/ui/screens/contacts/ContactPreferencesDialogTest.kt](app/src/test/java/com/example/ui/screens/contacts/ContactPreferencesDialogTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `UpdateContactPreferencesUseCase.Request.preferredChannel` now accepts `MessageChannel` instead of raw channel strings.
- Contact preference persistence writes `preferredChannel.raw` only at the Room entity boundary.
- Contact Detail quick actions and the personalization channel picker use typed SMS, WhatsApp, and Email options.
- Unsupported legacy stored channel values are mapped at the UI edge and fall back to SMS before saving preferences.
- Contact Detail's personalization quality channel check now parses stored channel values through `MessageChannel.fromRaw()`.

Why this improves user experience:

- Users only see supported channel choices in Contact Detail and are less likely to save an unusable channel state.
- Quick actions and the full preferences editor now share the same typed channel boundary, reducing inconsistent route-readiness behavior.

How user effort is reduced:

- Users do not need to manually repair legacy or unsupported stored channel values before saving contact preferences.
- Future channel changes can be made in the active `MessageChannel` model instead of several UI and domain string checks.

How user control is preserved:

- The same explicit SMS, WhatsApp, and Email choices remain available.
- Persisted Room strings remain compatible, and only explicit contact preference saves write the selected channel.
- Approval, scheduling, dispatch, global settings, and backup/restore behavior are unchanged.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.UpdateContactPreferencesUseCaseTest \
  --tests com.example.ui.screens.contacts.ContactPreferencesDialogTest \
  --tests com.example.ui.viewmodel.ContactDetailViewModelTest \
  --tests com.example.ui.screens.contacts.ContactDetailBodySectionsTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T603 work: remaining UI labels/fixtures and persistence mapping paths still hold channel-like strings.

## 2026-06-26 - MessageStatus Reject Defaults and Dispatch Completion

Completed tasks:

- T604 slice: Move reject, notification action, pending-message defaults, review filters, regeneration scheduling checks, and dispatch completion status writes to `MessageStatus`.

Changed files:

- [core/domain/src/main/kotlin/com/example/core/db/entities/PendingMessageEntity.kt](core/domain/src/main/kotlin/com/example/core/db/entities/PendingMessageEntity.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/RejectPendingMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/RejectPendingMessageUseCase.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/RegeneratePendingMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/RegeneratePendingMessageUseCase.kt)
- [core/data/src/main/kotlin/com/example/core/automation/notifications/ApprovalReceiver.kt](core/data/src/main/kotlin/com/example/core/automation/notifications/ApprovalReceiver.kt)
- [core/data/src/main/kotlin/com/example/core/automation/sender/MessageDispatcher.kt](core/data/src/main/kotlin/com/example/core/automation/sender/MessageDispatcher.kt)
- [app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt](app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt)
- [app/src/main/java/com/example/widget/BirthdayWidgetProvider.kt](app/src/main/java/com/example/widget/BirthdayWidgetProvider.kt)
- [core/domain/src/test/kotlin/com/example/core/db/entities/PendingMessageEntityTest.kt](core/domain/src/test/kotlin/com/example/core/db/entities/PendingMessageEntityTest.kt)
- [app/src/test/java/com/example/domain/usecase/RejectPendingMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/RejectPendingMessageUseCaseTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/WishPreviewViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/WishPreviewViewModelTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `PendingMessageEntity.status` now defaults to `MessageStatus.PENDING.raw`.
- Reject use case and notification reject/approve/retry actions now write `MessageStatus` raw values instead of duplicated status literals.
- Reject and regeneration paths parse stored status through `MessageStatus.fromRaw()` before comparing approval state.
- Wish Preview's review-next queue and the home-screen widget pending count now use `MessageStatus.fromRaw()` so legacy casing/spacing still counts as pending.
- `MessageDispatcher` now writes sent/failed pending-message completion states through `MessageStatus`.

Why this improves user experience:

- Users can reject or revoke messages even when old stored status casing is imperfect.
- Pending review counts and the review-next queue are less likely to omit messages because of legacy formatting differences.
- Dispatch completion states now share the same source of truth as review, retry, revoke, and message buckets.

How user effort is reduced:

- Users do not need to manually find missing review items or recover messages whose status strings drifted from the expected casing.
- Future status changes can be made in one model rather than across notification actions, dispatch completion, widgets, and review queues.

How user control is preserved:

- Persisted Room status values remain the same strings for compatibility.
- Reject, approve, retry, review-next, and dispatch behavior still follow the existing explicit user actions and automation gates.
- Delivery-status strings such as `DELIVERED` and `PENDING_DELIVERY` remain separate from pending-message state.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest :app:testDebugUnitTest \
  --tests com.example.core.db.entities.PendingMessageEntityTest \
  --tests com.example.domain.usecase.RejectPendingMessageUseCaseTest \
  --tests com.example.domain.usecase.RegeneratePendingMessageUseCaseTest \
  --tests com.example.domain.usecase.DispatchMessageUseCaseTest \
  --tests com.example.core.automation.workers.MessageDispatchWorkerTest \
  --tests com.example.ui.viewmodel.WishPreviewViewModelTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T604 work: Room SQL status predicates, serialized fixtures, pending-message fixture literals, event-type taxonomy, and remaining persistence mapping paths still hold raw status-like strings; broader channel/relationship typing remains T603/T604 follow-up work.

## 2026-06-26 - MessageStatus Approval and Background Generation Writes

Completed tasks:

- T604 slice: Replace direct approved/pending status writes in background generation, explicit approval/revoke, and retry paths with `MessageStatus`.

Changed files:

- [core/data/src/main/kotlin/com/example/core/automation/workers/HolidayWishWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/HolidayWishWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/PostEventFollowUpWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/PostEventFollowUpWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/ApprovePendingMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/ApprovePendingMessageUseCase.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/RevokeApprovalUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/RevokeApprovalUseCase.kt)
- [app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt](app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt)
- [app/src/test/java/com/example/core/automation/workers/HolidayWishWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/HolidayWishWorkerTest.kt)
- [app/src/test/java/com/example/core/automation/workers/PostEventFollowUpWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/PostEventFollowUpWorkerTest.kt)
- [app/src/test/java/com/example/core/automation/workers/RevivalWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/RevivalWorkerTest.kt)
- [app/src/test/java/com/example/domain/usecase/ApprovePendingMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/ApprovePendingMessageUseCaseTest.kt)
- [app/src/test/java/com/example/domain/usecase/RevokeApprovalUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/RevokeApprovalUseCaseTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/MessagesViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/MessagesViewModelTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Holiday, post-event follow-up, and revival workers now choose `MessageStatus.APPROVED` or `MessageStatus.PENDING` before writing `.raw` into `PendingMessageEntity`.
- `ApprovePendingMessageUseCase`, `RevokeApprovalUseCase`, and Messages retry/bulk-retry now use `MessageStatus` for status writes.
- Revoke now parses stored status with `MessageStatus.fromRaw()`, so legacy casing/spacing variations of approved status can still be revoked.
- Focused tests assert typed status constants for background generation, approval, revoke, and retry behavior.

Why this improves user experience:

- Review, retry, and revoke actions now share the same status source of truth as dispatch policy and message bucketing.
- Legacy status formatting is less likely to trap a user in a scheduled/approved state that cannot be revoked.

How user effort is reduced:

- Users do not need to manually recover messages affected by inconsistent status casing or duplicated status literals.
- Future status changes can be made in `MessageStatus` instead of hunting separate approval, retry, and worker constants.

How user control is preserved:

- Persisted status strings remain unchanged for Room compatibility.
- Approve, revoke, retry, review notifications, and automatic scheduling still require the same explicit user setting or user action gates.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.core.automation.workers.HolidayWishWorkerTest \
  --tests com.example.core.automation.workers.PostEventFollowUpWorkerTest \
  --tests com.example.core.automation.workers.RevivalWorkerTest \
  --tests com.example.domain.usecase.ApprovePendingMessageUseCaseTest \
  --tests com.example.domain.usecase.RevokeApprovalUseCaseTest \
  --tests com.example.ui.viewmodel.MessagesViewModelTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T604 work: Room SQL status predicates, serialized fixtures, pending-message fixture literals, event-type taxonomy, and remaining persistence mapping paths still hold raw status-like strings; broader channel/relationship typing remains T603/T604 follow-up work.

## 2026-06-26 - ApprovalMode Storage Defaults and Dead Taxonomy Cleanup

Completed tasks:

- T602 slice: Move approval-mode storage defaults from raw literals to `ApprovalMode` raw values and remove the unused duplicate taxonomy file.

Changed files:

- [core/domain/src/main/kotlin/com/example/core/db/entities/ContactEntity.kt](core/domain/src/main/kotlin/com/example/core/db/entities/ContactEntity.kt)
- [core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt](core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt)
- [core/data/src/main/kotlin/com/example/core/prefs/SecurePrefs.kt](core/data/src/main/kotlin/com/example/core/prefs/SecurePrefs.kt)
- [core/domain/src/test/kotlin/com/example/core/db/entities/ContactEntityTest.kt](core/domain/src/test/kotlin/com/example/core/db/entities/ContactEntityTest.kt)
- [core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt](core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt)
- Deleted `core/domain/src/main/kotlin/com/example/domain/model/AutomationMode.kt`
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `ContactEntity.automationMode` now defaults to `ApprovalMode.DEFAULT.raw` instead of the raw `"DEFAULT"` literal.
- `SecurePrefs.getGlobalAutomationMode()` and backup preference defaults now derive Smart Approve from `ApprovalMode.SMART_APPROVE.raw`.
- The unused duplicate `AutomationMode`/`CommunicationChannel`/`RelationshipType` sealed-class taxonomy file was deleted after scans confirmed no production references.
- Focused tests now lock the ContactEntity default and backup preference export default to the active `ApprovalMode` model.

Why this improves user experience:

- New contacts and backup preference exports now share the same approval-mode source of truth used by Settings, workers, and approval policies.
- Removing the dead taxonomy reduces the chance that future mode changes are made in an unused model and silently missed by the active app.

How user effort is reduced:

- Users do not need to repair newly created contact defaults or backup preference defaults caused by divergent raw constants.
- Future approval-mode maintenance happens through the active model instead of a duplicate sealed-class file.

How user control is preserved:

- Stored string values and backup schema fields remain unchanged for compatibility.
- Default, Smart Approve, VIP Approve, Fully Auto, and Always Ask behavior remains controlled by the same explicit user settings and contact overrides.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:domain:testDebugUnitTest :core:data:testDebugUnitTest \
  --tests com.example.core.db.entities.ContactEntityTest \
  --tests com.example.core.backup.BackupServiceImplTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T602 work: Room SQL schema/query literals where Room requires raw text, serialized backup/import payload fields, legacy fixture JSON, and remaining persistence mapping paths still hold raw approval strings; broader channel/status/relationship typing remains T603/T604 follow-up work.

## 2026-06-26 - Contact Automation Picker ApprovalMode State

Completed tasks:

- T602 slice: Move the Contact Detail personalization automation picker from raw strings to `ApprovalMode`.

Changed files:

- [app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt](app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [app/src/test/java/com/example/ui/screens/contacts/ContactPreferencesDialogTest.kt](app/src/test/java/com/example/ui/screens/contacts/ContactPreferencesDialogTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Contact Detail's automation preference picker now stores `ApprovalMode` in local dialog state.
- The picker options are typed `ApprovalMode` values, and save passes the selected typed value directly into `UpdateContactPreferencesUseCase.Request`.
- Unsupported legacy contact automation values fall back to `ApprovalMode.DEFAULT` in the dialog instead of producing an unselected/unsavable picker state.
- The picker title now says "Automation mode" instead of listing raw storage enum values.

Why this improves user experience:

- Users see a clean field label and localized option labels, not storage constants such as `SMART_APPROVE` or `FULLY_AUTO`.
- Legacy/unknown contact override values no longer create a confusing picker state when the user opens personalization controls.

How user effort is reduced:

- Users can save contact personalization without first understanding or fixing raw legacy automation strings.
- Future approval-mode changes can update typed option mapping instead of string literals in the dialog.

How user control is preserved:

- The same explicit choices remain available: Default, Smart Approve, VIP Approve, Fully Auto, and Always Ask.
- The change only affects the contact personalization picker and request construction; global settings, generation, review notifications, scheduling, dispatch, and backup/restore are unchanged.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.screens.contacts.ContactPreferencesDialogTest \
  --tests com.example.ui.screens.contacts.ContactDetailBodySectionsTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T602 work: Room SQL schema/query literals where Room requires raw text, serialized backup/import payload fields, legacy fixture JSON, and remaining persistence mapping paths still hold raw approval strings; broader channel/status/relationship typing remains T603/T604 follow-up work.

## 2026-06-26 - SecurePrefs Global ApprovalMode Boundary

Completed tasks:

- T602 slice: Replace direct worker and Settings raw global automation preference reads with a typed `SecurePrefs` approval-mode helper.

Changed files:

- [core/data/src/main/kotlin/com/example/core/prefs/GlobalAutomationModePrefsMapper.kt](core/data/src/main/kotlin/com/example/core/prefs/GlobalAutomationModePrefsMapper.kt)
- [core/data/src/main/kotlin/com/example/core/prefs/SecurePrefs.kt](core/data/src/main/kotlin/com/example/core/prefs/SecurePrefs.kt)
- [core/data/src/main/kotlin/com/example/core/prefs/PreferencesRepositoryImpl.kt](core/data/src/main/kotlin/com/example/core/prefs/PreferencesRepositoryImpl.kt)
- [app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/HolidayWishWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/HolidayWishWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/PostEventFollowUpWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/PostEventFollowUpWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt)
- [core/data/src/test/kotlin/com/example/core/prefs/GlobalAutomationModePrefsMapperTest.kt](core/data/src/test/kotlin/com/example/core/prefs/GlobalAutomationModePrefsMapperTest.kt)
- [core/data/src/test/kotlin/com/example/core/prefs/PreferencesRepositoryImplTest.kt](core/data/src/test/kotlin/com/example/core/prefs/PreferencesRepositoryImplTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt)
- [app/src/test/java/com/example/core/automation/workers/HolidayWishWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/HolidayWishWorkerTest.kt)
- [app/src/test/java/com/example/core/automation/workers/PostEventFollowUpWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/PostEventFollowUpWorkerTest.kt)
- [app/src/test/java/com/example/core/automation/workers/RevivalWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/RevivalWorkerTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `SecurePrefs` now has `getGlobalApprovalMode()` and `setGlobalApprovalMode()` typed helpers backed by `GlobalAutomationModePrefsMapper`.
- Settings, `PreferencesRepositoryImpl`, holiday wishes, post-event follow-ups, and revival workers now consume typed global approval mode values.
- Backup/import still uses raw `getGlobalAutomationMode()`/`setGlobalAutomationMode(String)` so serialized preference payloads preserve the storage contract.

Why this improves user experience:

- Background holiday, follow-up, and revival automation now share the same legacy-value fallback as Settings and foreground generation.
- Unsupported global automation storage values fall back to Smart Approve before any worker decides whether to auto-approve, request review, or schedule dispatch.

How user effort is reduced:

- Users do not need to manually repair global automation preferences before background workers make safe review/scheduling decisions.
- Future approval-mode changes have one mapper to update instead of repeated raw parsing across workers, Settings, and the repository adapter.

How user control is preserved:

- Visible mode choices and existing review gates are unchanged.
- Fully Auto, Smart Approve, VIP Approve, and Always Ask remain explicit user-controlled settings; only unsupported stored values are normalized.
- Backup/restore continues to round-trip raw stored values through the backup schema.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest \
  --tests com.example.core.prefs.GlobalAutomationModePrefsMapperTest \
  --tests com.example.core.prefs.PreferencesRepositoryImplTest \
  --tests com.example.ui.viewmodel.SettingsViewModelTest \
  --tests com.example.ui.screens.settings.SettingsScreenInteractionTest \
  --tests com.example.core.automation.workers.HolidayWishWorkerTest \
  --tests com.example.core.automation.workers.PostEventFollowUpWorkerTest \
  --tests com.example.core.automation.workers.RevivalWorkerTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T602 work: Room SQL schema/query literals where Room requires raw text, serialized backup/import payload fields, legacy fixture JSON, and remaining persistence mapping paths still hold raw approval strings; broader channel/status/relationship typing remains T603/T604 follow-up work.

## 2026-06-26 - PreferencesRepository ApprovalMode Boundary

Completed tasks:

- T602 slice: Move the domain-facing global automation preference API from raw strings to `ApprovalMode`.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/service/PreferencesRepository.kt](core/domain/src/main/kotlin/com/example/domain/service/PreferencesRepository.kt)
- [core/data/src/main/kotlin/com/example/core/prefs/PreferencesRepositoryImpl.kt](core/data/src/main/kotlin/com/example/core/prefs/PreferencesRepositoryImpl.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/RegeneratePendingMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/RegeneratePendingMessageUseCase.kt)
- [core/data/src/test/kotlin/com/example/core/prefs/PreferencesRepositoryImplTest.kt](core/data/src/test/kotlin/com/example/core/prefs/PreferencesRepositoryImplTest.kt)
- [app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt)
- [app/src/test/java/com/example/domain/usecase/RegeneratePendingMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/RegeneratePendingMessageUseCaseTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `PreferencesRepository.getGlobalAutomationMode()` now returns `ApprovalMode` instead of a raw string.
- `PreferencesRepositoryImpl` maps raw `SecurePrefs` storage values through `ApprovalMode.fromRaw()`, defaults unsupported legacy values to Smart Approve, and writes `.raw` only at the `SecurePrefs` boundary.
- Generation and regeneration use cases now pass the typed global mode directly into `ApprovalModeResolver`.

Why this improves user experience:

- Global automation defaults now follow one typed path before message generation or regeneration chooses review, scheduling, or auto-send behavior.
- Unsupported legacy storage values are normalized before policy code runs, reducing confusing approval outcomes.

How user effort is reduced:

- Users do not need to diagnose or reselect settings when older raw values exist in storage; the adapter falls back to Smart Approve.
- Future approval-mode changes can be made in one preference adapter instead of repeated parsing in generation flows.

How user control is preserved:

- The change does not alter visible Settings choices, contact overrides, review notifications, scheduling, dispatch, or approval actions.
- Fully Auto, Smart Approve, VIP Approve, and Always Ask remain explicit user-controlled modes; only unsupported values fall back to Smart Approve.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest \
  --tests com.example.core.prefs.PreferencesRepositoryImplTest \
  --tests com.example.domain.usecase.GenerateMessageUseCaseTest \
  --tests com.example.domain.usecase.RegeneratePendingMessageUseCaseTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T602 work: Room SQL schema/query literals where Room requires raw text, serialized backup/import payload fields, legacy fixture JSON, and remaining persistence mapping paths still hold raw approval strings; broader channel/status/relationship typing remains T603/T604 follow-up work.

## 2026-06-26 - Settings ApprovalMode Preference State

Completed tasks:

- T602 slice: Move Settings global automation-mode UI state from raw strings to `ApprovalMode`.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt)
- [app/src/main/java/com/example/ui/screens/settings/SettingsScreen.kt](app/src/main/java/com/example/ui/screens/settings/SettingsScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `SettingsUiState.automationMode` now stores `ApprovalMode` instead of a raw string.
- `SettingsViewModel` maps persisted global automation mode strings through `ApprovalMode.fromRaw()` on load, defaults unknown legacy values to Smart Approve, and writes `.raw` only when saving to `SecurePrefs`.
- `SettingsScreen` dropdown options and label rendering now use `ApprovalMode` values.

Why this improves user experience:

- Settings now displays automation mode through the same typed approval model used by generation, dispatch, and contact preferences.
- Legacy/unknown persisted global modes fall back to Smart Approve in UI state instead of becoming confusing or unsafe settings display.

How user effort is reduced:

- Users get a consistent global automation setting label and safer default behavior if older storage values exist.
- Future approval-mode changes can update typed settings state and label mapping instead of string literals across ViewModel and screen code.

How user control is preserved:

- The change only affects Settings state representation and preference saving.
- It does not change existing user-triggered selection behavior, contact overrides, message generation, approval, scheduling, dispatch, or review gates.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.SettingsViewModelTest \
  --tests com.example.ui.screens.settings.SettingsScreenInteractionTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T602 work: Room SQL schema/query literals where Room requires raw text, serialized backup/import payload fields, legacy fixture JSON, and remaining persistence mapping paths still hold raw approval strings; broader channel/status/relationship typing remains T603/T604 follow-up work.

## 2026-06-26 - ApprovalMode UI Read-Model Mapping

Completed tasks:

- T602 slice: Replace selected UI/read-model approval-mode string comparisons with `ApprovalMode` mapping.

Changed files:

- [app/src/main/java/com/example/ui/screens/messages/MessagesScreen.kt](app/src/main/java/com/example/ui/screens/messages/MessagesScreen.kt)
- [app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt](app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt)
- [app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt](app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt)
- [app/src/main/java/com/example/ui/viewmodel/ContactListViewModel.kt](app/src/main/java/com/example/ui/viewmodel/ContactListViewModel.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Messages rows now parse `message.approvalMode` through `ApprovalMode.fromRaw()` before choosing mode color, localized mode text, and Smart Approve countdown visibility.
- Wish Preview approval-plan labels now map raw summary values through `ApprovalMode.fromRaw()` before resolving localized copy.
- Contact Detail's VIP quick-action disabled state and Contact List's VIP filter now use `ApprovalMode.fromRaw()` instead of direct string comparison.

Why this improves user experience:

- Users see localized approval-mode labels in Messages instead of raw enum storage values.
- Unknown or legacy approval modes now fall back to the default label/color path instead of surfacing as confusing raw text.

How user effort is reduced:

- Review queues and preview summaries become easier to scan because approval mode presentation is consistent across Messages, Wish Preview, Contacts, and Contact Detail.
- Future approval-mode additions can update typed display mapping instead of scattered UI string comparisons.

How user control is preserved:

- The change is read-only presentation/filtering logic.
- It does not change stored contact preferences, pending-message approval modes, generation, approval, scheduling, dispatch, or review gates.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.ContactListViewModelTest \
  --tests com.example.ui.screens.messages.MessagesScreenInteractionTest \
  --tests com.example.ui.screens.wish.WishPreviewScreenInteractionTest \
  --tests com.example.ui.screens.contacts.ContactDetailBodySectionsTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T602 work: Room SQL schema/query literals where Room requires raw text, serialized backup/import payload fields, legacy fixture JSON, and remaining persistence mapping paths still hold raw approval strings; broader channel/status/relationship typing remains T603/T604 follow-up work.

## 2026-06-26 - ApprovalMode Use-Case Outcome Boundary

Completed tasks:

- T602 slice: Move approval-mode values returned by generation and approval use cases from raw strings to typed `ApprovalMode`.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/ApprovePendingMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/ApprovePendingMessageUseCase.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/MessageGenerationWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/MessageGenerationWorker.kt)
- [app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt)
- [app/src/test/java/com/example/domain/usecase/ApprovePendingMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/ApprovePendingMessageUseCaseTest.kt)
- [app/src/test/java/com/example/core/automation/workers/MessageGenerationWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/MessageGenerationWorkerTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/ContactDetailViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/ContactDetailViewModelTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/WishPreviewViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/WishPreviewViewModelTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `GenerateMessageUseCase.GenerationOutcome.Generated.approvalMode` now carries `ApprovalMode`.
- `ApprovePendingMessageUseCase.ApprovalOutcome.Approved.approvalMode` now carries `ApprovalMode`, mapping unknown legacy persisted values to `ApprovalMode.UNKNOWN`.
- `MessageGenerationWorker` converts the typed outcome back to `.raw` only for structured logging.

Why this improves user experience:

- ViewModels and workers now receive typed approval outcomes, reducing the chance that unsupported or misspelled modes produce inconsistent review or scheduling state.
- Legacy approval values are explicitly represented as unknown instead of being treated as valid user policy.

How user effort is reduced:

- Fewer downstream surfaces need to repeat string parsing or string comparison when reacting to generation or approval success.
- Future approval-mode migrations can update one typed model instead of hunting through outcome consumers.

How user control is preserved:

- The change does not alter pending-message persistence, approval scheduling, dispatch, review notifications, draft editing, or fallback behavior.
- Unknown legacy values remain safe typed values and do not become automatic-send authorization.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.GenerateMessageUseCaseTest \
  --tests com.example.domain.usecase.ApprovePendingMessageUseCaseTest \
  --tests com.example.core.automation.workers.MessageGenerationWorkerTest \
  --tests com.example.ui.viewmodel.ContactDetailViewModelTest \
  --tests com.example.ui.viewmodel.WishPreviewViewModelTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T602 work: Room SQL schema/query literals where Room requires raw text, serialized backup/import payload fields, legacy fixture JSON, and remaining persistence mapping paths still hold raw approval strings; broader channel/status/relationship typing remains T603/T604 follow-up work.

## 2026-06-26 - Dispatch Eligibility ApprovalMode Boundary

Completed tasks:

- T602 slice: Move dispatch eligibility approval-mode input from raw pending-message strings to typed `ApprovalMode`.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/automation/DispatchEligibilityPolicy.kt](core/domain/src/main/kotlin/com/example/domain/automation/DispatchEligibilityPolicy.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/notifications/ApprovalNotificationActionPolicy.kt](core/data/src/main/kotlin/com/example/core/automation/notifications/ApprovalNotificationActionPolicy.kt)
- [core/domain/src/test/kotlin/com/example/domain/automation/DispatchEligibilityPolicyTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/DispatchEligibilityPolicyTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `DispatchEligibilityPolicy.evaluate()` now requires a typed `ApprovalMode` instead of parsing `pending.approvalMode` internally.
- Dispatch use case, dispatch worker, and approval-notification action policy now map persisted pending-message approval strings through `ApprovalMode.fromRaw()` before invoking the domain policy.
- Dispatch eligibility tests now pass `ApprovalMode` values directly and only use `.raw` when creating Room-shaped pending-message fixtures.

Why this improves user experience:

- Dispatch, notification approval, and worker deferral decisions now interpret approval modes consistently before deciding send, defer, approval, expiry, or blocked states.
- Unknown or legacy persisted approval modes still fail into review-oriented behavior instead of leaking arbitrary strings into policy decisions.

How user effort is reduced:

- Users are less likely to encounter mismatched "ready", "needs approval", or "expired" states between Messages, notification approval, and background dispatch.
- Future approval-mode migrations become cheaper because parsing stays at persistence edges rather than inside every policy branch.

How user control is preserved:

- The change preserves existing send/defer/expire/block outcomes and still respects schedule, quiet-hour, blackout, approval, VIP, Always Ask, failed, rejected, and already-handled gates.
- It does not approve, schedule, dispatch, retry, expire, or revoke any message without the existing explicit trigger or worker path.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :core:domain:testDebugUnitTest --tests com.example.domain.automation.DispatchEligibilityPolicyTest \
  :core:data:testDebugUnitTest --tests com.example.core.automation.notifications.ApprovalNotificationActionPolicyTest \
  :app:testDebugUnitTest \
  --tests com.example.domain.usecase.DispatchMessageUseCaseTest \
  --tests com.example.core.automation.workers.MessageDispatchWorkerTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T602 work: Room SQL schema/query literals where Room requires raw text, serialized backup/import payload fields, legacy fixture JSON, and remaining persistence mapping paths still hold raw approval strings; broader channel/status/relationship typing remains T603/T604 follow-up work.

## 2026-06-26 - ApprovalMode Resolver Boundary

Completed tasks:

- T602 slice: Move `ApprovalModeResolver` from raw approval-mode strings to typed `ApprovalMode` inputs.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/automation/ApprovalModeResolver.kt](core/domain/src/main/kotlin/com/example/domain/automation/ApprovalModeResolver.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/RegeneratePendingMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/RegeneratePendingMessageUseCase.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/HolidayWishWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/HolidayWishWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/PostEventFollowUpWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/PostEventFollowUpWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt)
- [core/domain/src/test/kotlin/com/example/domain/automation/ApprovalModeResolverTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/ApprovalModeResolverTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `ApprovalModeResolver.resolve()` now accepts typed `ApprovalMode` values for contact override and global mode instead of raw strings.
- Generation, regeneration, holiday, follow-up, and revival paths now parse persisted/preference strings through `ApprovalMode.fromRaw()` before calling the domain policy.
- Holiday, follow-up, and revival workers now compare `ApprovalMode.FULLY_AUTO` directly when deciding the initial pending-message status.

Why this improves user experience:

- Approval-mode decisions now fail consistently through one typed model before quality gating and scheduling decide whether a draft can be automatic or must be reviewed.
- Legacy/unknown persisted modes still resolve to the safe Smart Approve fallback rather than silently flowing through the resolver as arbitrary strings.

How user effort is reduced:

- Users are less likely to see inconsistent review/schedule behavior between foreground generation, regeneration, and older background generator workers.
- Future approval-mode additions or migrations can be handled through the typed model and edge mapping instead of patching string comparisons inside policy code.

How user control is preserved:

- The change preserves existing approval outcomes, fallback behavior, review notifications, and scheduling gates.
- It does not bypass VIP, Always Ask, missing-route, quality downgrade, quiet-hour, blackout, approval, or manual review safeguards.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :core:domain:testDebugUnitTest --tests com.example.domain.automation.ApprovalModeResolverTest \
  :app:testDebugUnitTest \
  --tests com.example.domain.usecase.GenerateMessageUseCaseTest \
  --tests com.example.domain.usecase.RegeneratePendingMessageUseCaseTest \
  --tests com.example.core.automation.workers.HolidayWishWorkerTest \
  --tests com.example.core.automation.workers.PostEventFollowUpWorkerTest \
  --tests com.example.core.automation.workers.RevivalWorkerTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

Remaining T602 work: Room SQL schema/query literals where Room requires raw text, serialized backup/import payload fields, legacy fixture JSON, and remaining persistence mapping paths still hold raw approval strings; broader channel/status/relationship typing remains T603/T604 follow-up work.

## 2026-06-26 - Contact Preference ApprovalMode Boundary

Completed tasks:

- T602 slice: Replace raw contact-preference automation mode input with `ApprovalMode` at the domain boundary.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/usecase/UpdateContactPreferencesUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/UpdateContactPreferencesUseCase.kt)
- [app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt](app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt)
- [app/src/test/java/com/example/domain/usecase/UpdateContactPreferencesUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/UpdateContactPreferencesUseCaseTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `UpdateContactPreferencesUseCase.Request.automationMode` now accepts `ApprovalMode` instead of a raw `String`.
- Contact Detail maps UI chip state and persisted contact values through `ApprovalMode.fromRaw()` at the UI edge before calling the use case.
- The use case persists only `request.automationMode.raw` into the Room-backed contact entity and rejects `ApprovalMode.UNKNOWN` before persistence.

Why this improves user experience:

- Contact automation preferences now fail closed for unknown values before they can silently become stored policy.
- The VIP shortcut and preferences dialog share the same typed approval-mode boundary, reducing divergent behavior between quick actions and full editing.

How user effort is reduced:

- Users get fewer confusing preference states caused by unsupported automation values being saved and later interpreted differently by generation or dispatch paths.
- Future approval-mode changes can be added once to the typed domain model and surfaced through the UI mapping layer.

How user control is preserved:

- The change only validates and stores an explicit user-selected contact preference.
- It does not auto-approve, auto-send, change global automation defaults, bypass VIP/Always Ask review, or alter message scheduling.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.UpdateContactPreferencesUseCaseTest \
  --tests com.example.ui.viewmodel.ContactDetailViewModelTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

Remaining T602 work: Room SQL schema/query literals where Room requires raw text, serialized backup/import payload fields, legacy fixture JSON, and remaining persistence mapping paths still hold raw approval strings; broader channel/status/relationship typing remains T603/T604 follow-up work.

## 2026-06-26 - Dispatch Worker Policy Timing

Completed tasks:

- T601: Move dispatch worker decision logic behind domain policy.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/automation/DispatchEligibilityPolicy.kt](core/domain/src/main/kotlin/com/example/domain/automation/DispatchEligibilityPolicy.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorker.kt)
- [core/domain/src/test/kotlin/com/example/domain/automation/DispatchEligibilityPolicyTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/DispatchEligibilityPolicyTest.kt)
- [app/src/test/java/com/example/core/automation/workers/MessageDispatchWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/MessageDispatchWorkerTest.kt)
- [app/src/test/java/com/example/core/automation/AutomationPipelineTest.kt](app/src/test/java/com/example/core/automation/AutomationPipelineTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `DispatchEligibilityPolicy.evaluate()` now accepts quiet-hour and blackout-date inputs and returns `DeferUntil(QUIET_HOURS_OR_BLACKOUT_DATE)` when an otherwise sendable message must wait.
- `MessageDispatchWorker` injects `PreferencesRepository` and passes timing preferences into the domain policy instead of computing `AutomationSchedulePolicy.nextAllowedSendMs()` locally.
- The worker now remains responsible for orchestration side effects only: loading the pending message/contact, scheduling deferred work, expiring stale VIP approvals, updating dispatch status, invoking the sender, and marking unexpected failures.

Why this improves user experience:

- Foreground dispatch checks, notification approval handling, and background dispatch have a single place to reason about whether a message can send now or must wait.
- Quiet-hour and blackout-date deferrals are less likely to diverge between automatic sends and user-triggered send paths.

How user effort is reduced:

- Users get fewer surprise failed sends or inconsistent "ready" states because the dispatch timing rule is centralized.
- Developers can extend dispatch timing once instead of patching worker-only logic.

How user control is preserved:

- The change only moves policy calculation; it does not weaken approval gates, bypass quiet hours, skip blackout dates, send messages directly from policy, or alter user-configured timing.
- The worker still schedules deferred dispatch rather than sending during a blocked time window.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :core:domain:testDebugUnitTest --tests com.example.domain.automation.DispatchEligibilityPolicyTest \
  :app:testDebugUnitTest \
  --tests com.example.core.automation.workers.MessageDispatchWorkerTest \
  --tests com.example.domain.usecase.DispatchMessageUseCaseTest \
  --tests com.example.core.automation.AutomationPipelineTest \
  --no-configuration-cache
```

Result: passed.

Hardcoded-string regression and `git diff --check` passed.

## 2026-06-26 - Message Generation Worker Delegation

Completed tasks:

- T600: Move message generation worker logic behind domain use case/service.

Changed files:

- [core/data/src/main/kotlin/com/example/core/automation/workers/MessageGenerationWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/MessageGenerationWorker.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt)
- [core/domain/src/main/kotlin/com/example/domain/service/NotificationService.kt](core/domain/src/main/kotlin/com/example/domain/service/NotificationService.kt)
- [core/data/src/main/kotlin/com/example/core/automation/notifications/NotificationServiceImpl.kt](core/data/src/main/kotlin/com/example/core/automation/notifications/NotificationServiceImpl.kt)
- [app/src/test/java/com/example/core/automation/workers/MessageGenerationWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/MessageGenerationWorkerTest.kt)
- [app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt)
- [app/src/test/java/com/example/core/automation/AutomationPipelineTest.kt](app/src/test/java/com/example/core/automation/AutomationPipelineTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `MessageGenerationWorker` now owns only worker cadence/setup orchestration: AI-enabled check, missing-provider setup notification, 7-day event lookahead, and per-event delegation.
- Prompt building, AI calls, anti-repetition retries, fallback alerting, approval resolution, quality gating, channel selection, pending-message persistence, scheduling, and approval notifications now run through `GenerateMessageUseCase`.
- `GenerateMessageUseCase.Request` allows the worker to regenerate a previously failed event occurrence while preserving the same pending-message ID; normal foreground generation still treats any existing occurrence as `AlreadyExists`.
- AI fallback system alerts moved behind `NotificationService` so the domain use case can preserve fallback visibility without importing notification resources.

Why this improves user experience:

- Weekly background generation and foreground generation now follow the same approval, quality, route-readiness, skip-auto-wish, and scheduling rules.
- Failed generated wishes can recover through the worker without creating duplicate occurrence rows or changing message identity.

How user effort is reduced:

- Users see fewer inconsistent draft states between background-created messages and manually generated messages.
- Recovery from transient AI failures is automatic on the next generation pass for failed occurrences.

How user control is preserved:

- The worker still does not approve VIP/Always Ask drafts, bypass review requirements, send messages directly, or mutate contacts.
- Failed occurrence replacement only runs from the worker request path; user-triggered generation continues to block duplicates.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.GenerateMessageUseCaseTest \
  --tests com.example.core.automation.workers.MessageGenerationWorkerTest \
  --tests com.example.core.automation.AutomationPipelineTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

## 2026-06-26 - Accessibility Labels Regression

Completed tasks:

- T519: Add labels to icon-only actions in new/refactored UI.

Changed files:

- [app/src/test/java/com/example/ui/AccessibilityLabelsRegressionTest.kt](app/src/test/java/com/example/ui/AccessibilityLabelsRegressionTest.kt)
- [SSOT.md](SSOT.md)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Audited refactored/new action surfaces for `IconButton` and `FloatingActionButton` labels.
- Confirmed the cleaned icon-only actions already expose non-null `contentDescription` values.
- Added a regression test that scans the cleaned screens/components and fails if an `IconButton` or `FloatingActionButton` action lacks a non-null screen reader label.

Why this improves user experience:

- Screen reader users can rely on named icon-only actions such as back, export, add, clear, delete, pin, and visibility toggles.
- Future UI refactors are less likely to silently remove accessibility labels.

How user effort is reduced:

- Users who navigate by assistive technology do not have to guess what an icon-only control does.
- Developers get a fast unit-test failure instead of discovering missing labels through manual audit.

How user control is preserved:

- This change only validates labels for existing explicit actions.
- It does not trigger navigation, export, delete, approval, sync, generation, scheduling, restore, or sending automatically.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.AccessibilityLabelsRegressionTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

## 2026-06-26 - ViewModel Localization Cleanup

Completed tasks:

- T518: Convert selected remaining hardcoded ViewModel/use case user strings to resources and typed reasons.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt)
- [app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt](app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt)
- [app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt](app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt)
- [app/src/main/java/com/example/ui/viewmodel/ContactListViewModel.kt](app/src/main/java/com/example/ui/viewmodel/ContactListViewModel.kt)
- [app/src/main/java/com/example/ui/viewmodel/ContactDetailViewModel.kt](app/src/main/java/com/example/ui/viewmodel/ContactDetailViewModel.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/SaveManualEventUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/SaveManualEventUseCase.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/UpdateContactPreferencesUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/UpdateContactPreferencesUseCase.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- Focused ViewModel/use case tests and hardcoded-string regression coverage.

What changed:

- Events, Messages, Home, Contact List, and Contact Detail now resolve cleaned user-facing errors, feedback, planner copy, and activity-log copy through string resources.
- Manual-event and contact-preference validation outcomes now return typed domain reasons instead of English messages.
- Contact Detail preference errors now use resource IDs, matching its existing generation-error model.
- `NoHardcodedStringsRegressionTest` now guards the cleaned ViewModel/use case files against reintroducing the removed literals.

Why this improves user experience:

- Error and feedback copy can be localized consistently instead of leaking English from ViewModels and domain validation.
- Users get the same operational messages in Home, Events, Messages, Contacts, and Contact Detail regardless of locale.

How user effort is reduced:

- Localized validation messages reduce ambiguity during event creation, preference editing, message recovery, and contact sync troubleshooting.
- Typed validation reasons make future UI copy changes cheaper and safer.

How user control is preserved:

- The change only affects presentation text and validation outcome shape.
- It does not approve, reject, sync, generate, schedule, dispatch, restore, or mutate relationship data without the existing explicit user action.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.EventsViewModelTest \
  --tests com.example.ui.viewmodel.ContactListViewModelTest \
  --tests com.example.ui.viewmodel.ContactDetailViewModelTest \
  --tests com.example.ui.viewmodel.HomeViewModelTest \
  --tests com.example.ui.viewmodel.MessagesViewModelTest \
  --tests com.example.domain.usecase.SaveManualEventUseCaseTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed. Gradle emitted a non-fatal KSP/AWT exception after compilation, but the build completed successfully.

`git diff --check` passed.

## 2026-06-26 - Activity History Task Filters

Completed tasks:

- T517: Add filters for dispatch, AI, sync, backup, and settings.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/ActivityHistoryViewModel.kt](app/src/main/java/com/example/ui/viewmodel/ActivityHistoryViewModel.kt)
- [app/src/main/java/com/example/ui/screens/activity/ActivityHistoryScreen.kt](app/src/main/java/com/example/ui/screens/activity/ActivityHistoryScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/ActivityHistoryViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/ActivityHistoryViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/activity/ActivityHistoryScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/activity/ActivityHistoryScreenInteractionTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Activity History now exposes explicit Dispatch and Backup filters alongside AI, Sync, Settings, Messages, Events, and Analytics.
- Dispatch matching recognizes current dispatch logs stored as `MESSAGE` with dispatch decision metadata.
- Backup matching recognizes `BACKUP` entries and backup/restore activity text without misclassifying generic analytics exports.
- The Activity History screen renders localized chips for the new filters and uses a backup icon for backup entries.

Why this improves user experience:

- Users can inspect operational audit trails by the task they are troubleshooting instead of searching raw log text.
- Dispatch and backup work are no longer hidden inside broader message or generic activity buckets.

How user effort is reduced:

- Failed send investigation can start from the Dispatch filter.
- Backup/restore verification can start from the Backup filter without scanning analytics, events, or message rows.

How user control is preserved:

- Filters only change the visible audit list.
- They do not retry sends, restore data, export backups, mark logs resolved, change settings, approve, schedule, or send anything.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.ActivityHistoryViewModelTest \
  --tests com.example.ui.screens.activity.ActivityHistoryScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

## 2026-06-26 - AI Doctor Generic Message Diagnostic

Completed tasks:

- T515: Add generic-message diagnostic from personalization quality.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/AutomationSetupViewModel.kt](app/src/main/java/com/example/ui/viewmodel/AutomationSetupViewModel.kt)
- [app/src/test/java/com/example/ui/viewmodel/AutomationSetupViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/AutomationSetupViewModelTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added an AI Doctor `Generic Message Risk` check in the Quality group.
- The check counts contacts missing AI personalization context by using nicknames, interests, shared history, notes, or strong classification confidence.
- Low-context contacts show a warning with the affected count and a Review Contacts action.
- Empty contact lists route to Sync Contacts before diagnosis.

Why this improves user experience:

- Users can see why AI messages may sound generic from the setup diagnostics screen.
- AI Doctor now separates raw personalization coverage from the more actionable generic-message risk.

How user effort is reduced:

- Users no longer need to infer generic output causes from draft feedback or per-contact detail screens alone.
- The diagnostic sends them directly to Contacts cleanup, where quality labels and filters already identify missing context.

How user control is preserved:

- The diagnostic is advisory and warning-level only.
- It does not edit contacts, infer private memories, regenerate drafts, change approval modes, schedule, approve, or send messages.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.AutomationSetupViewModelTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed. Gradle emitted a non-fatal KSP/AWT background exception, but the build completed successfully.

`git diff --check` passed.

## 2026-06-26 - AI Doctor Recommended Fix

Completed tasks:

- T514: Rank setup blockers and show a single recommended fix.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/AutomationSetupViewModel.kt](app/src/main/java/com/example/ui/viewmodel/AutomationSetupViewModel.kt)
- [app/src/main/java/com/example/ui/screens/setup/AutomationSetupScreen.kt](app/src/main/java/com/example/ui/screens/setup/AutomationSetupScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/AutomationSetupViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/AutomationSetupViewModelTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added a typed `AiDoctorRecommendedFix` state derived from readiness checks.
- AI Doctor now ranks actionable problems by blocker status, setup group priority, and original check order for deterministic tie-breaking.
- The diagnostics dashboard shows one recommended fix before the grouped checklist.
- Dry run uses the same ranked blocker when reporting a setup problem.

Why this improves user experience:

- Users see where to start before scanning the full diagnostic list.
- Required setup and reliability blockers are promoted ahead of quality warnings.

How user effort is reduced:

- The first repair action is visible as a single button, while the detailed checklist remains available for context.
- Deterministic ranking makes repeated refreshes stable unless the underlying setup state changes.

How user control is preserved:

- The recommendation only routes to the same explicit action already available on the matching diagnostic row.
- It does not auto-enable permissions, credentials, sync, AI tests, email tests, scheduling, approval, or sending.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.AutomationSetupViewModelTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

## 2026-06-26 - Wish Preview Edit Readiness

Completed tasks:

- T513: Recalculate Wish Preview readiness after edit.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt](app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt)
- [app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt](app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/WishPreviewViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/WishPreviewViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/wish/WishPreviewScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/wish/WishPreviewScreenInteractionTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added a typed `WishDraftReadiness` state for ready, edited-ready, very short, and blank drafts.
- Wish Preview recalculates draft readiness on load, tone/variant changes, text edits, regeneration, and approval attempts.
- The screen shows a visible readiness message below the editable draft.
- Blank edited drafts disable Approve & Schedule and are blocked in the ViewModel before the approval use case is invoked.
- Very short drafts show a warning but remain user-approvable after review.

Why this improves user experience:

- Users get immediate feedback when an edit changes whether the draft is safe to approve.
- The approval button state now matches the actual edited draft instead of the original generated text.

How user effort is reduced:

- Users no longer discover blank draft problems only after trying approval or send.
- The edit loop explains whether the current text will be saved on approval without requiring another screen.

How user control is preserved:

- Only blank content is blocked because it cannot produce a meaningful send.
- Very short and edited drafts remain under explicit user control; approve, regenerate, test-send, and reject still require user action.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.WishPreviewViewModelTest \
  --tests com.example.ui.screens.wish.WishPreviewScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

## 2026-06-26 - Wish Preview Approval Plan

Completed tasks:

- T512: Show event type, route, schedule, approval mode, and quality/fallback in Wish Preview.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt](app/src/main/java/com/example/ui/viewmodel/WishPreviewViewModel.kt)
- [app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt](app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/WishPreviewViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/WishPreviewViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/wish/WishPreviewScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/wish/WishPreviewScreenInteractionTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Wish Preview now derives an approval-plan summary while loading the pending message.
- The summary shows event type, delivery route, scheduled time, approval mode, and whether the draft is an AI draft or template fallback.
- Event type is resolved from the event stream when available and falls back safely for legacy pending-message routes.
- The summary appears before the editable draft and action buttons so users see send context before approving.

Why this improves user experience:

- Users can understand what approving the draft means without leaving the review screen.
- The preview connects draft quality/fallback state to the same approval decision that schedules delivery.

How user effort is reduced:

- Users no longer need to cross-check Messages, event details, or settings just to confirm the route, timing, and approval mode.
- The key send-risk facts are grouped in one scannable card above the draft.

How user control is preserved:

- The approval plan is informational only.
- It does not edit the message, change route, change schedule, approve, reject, regenerate, test-send, or dispatch anything automatically.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.WishPreviewViewModelTest \
  --tests com.example.ui.screens.wish.WishPreviewScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Messages Task-State Tabs

Completed tasks:

- T510: Split Messages tabs into Needs review, Scheduled, Blocked, Sent, and Failed.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt](app/src/main/java/com/example/ui/viewmodel/MessagesViewModel.kt)
- [app/src/main/java/com/example/ui/screens/messages/MessagesScreen.kt](app/src/main/java/com/example/ui/screens/messages/MessagesScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/MessagesViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/MessagesViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/messages/MessagesScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/messages/MessagesScreenInteractionTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Messages state now derives task buckets for Needs review, Scheduled, Blocked, Sent, and Failed from status plus contact/channel/setup readiness.
- Needs review shows pending/unknown drafts that can be reviewed now.
- Scheduled shows approved/dispatching drafts whose prerequisites are currently satisfied.
- Blocked collects pending or approved drafts with missing contact, disabled channel, missing phone/email, or missing email setup.
- Blocked rows still allow edit and reject, but hide approve so users cannot treat an unsendable draft as ready.
- The tab row is scrollable to keep five task labels usable on narrow screens.

Why this improves user experience:

- The inbox now starts from the user's task: review, monitor scheduled sends, fix blockers, inspect sent history, or recover failures.
- Users no longer need to understand date/lifecycle buckets before finding messages that need attention.

How user effort is reduced:

- Reviewable drafts and blocked drafts no longer mix in the same pending queue.
- The Blocked tab makes setup/contact issues discoverable without scanning every pending row.

How user control is preserved:

- No approval, rejection, retry, revoke, edit, route change, contact edit, or setup change is automatic.
- Blocked rows remove the approve affordance while still leaving explicit edit and reject choices.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.MessagesViewModelTest \
  --tests com.example.ui.screens.messages.MessagesScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

## 2026-06-26 - Event Conflict Resolution Actions

Completed tasks:

- T509: Add duplicate/conflict resolution action.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/event/EventResolutionPolicy.kt](core/domain/src/main/kotlin/com/example/domain/event/EventResolutionPolicy.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/ResolveEventConflictUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/ResolveEventConflictUseCase.kt)
- [core/domain/src/main/kotlin/com/example/core/db/entities/EventEntity.kt](core/domain/src/main/kotlin/com/example/core/db/entities/EventEntity.kt)
- [app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt)
- [app/src/main/java/com/example/ui/screens/events/EventsScreen.kt](app/src/main/java/com/example/ui/screens/events/EventsScreen.kt)
- [app/src/test/java/com/example/domain/usecase/ResolveEventConflictUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/ResolveEventConflictUseCaseTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/EventsViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/EventsViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/events/EventsScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/events/EventsScreenInteractionTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added a shared event resolution policy for conflict grouping, source normalization, and user-reviewed keep-separate markers.
- Added a `ResolveEventConflictUseCase` with two explicit actions: merge into the selected event or keep the active conflict group separate.
- Merge keeps the selected event active, verifies it, reschedules its reminder, deactivates sibling events, and cancels sibling reminders without physically deleting history.
- Keep separate verifies and marks the active conflict group as reviewed while preserving all reminders and original source labels in the UI.
- Events rows now show Merge here and Keep separate controls only when a duplicate/date-conflict trust state exists.

Why this improves user experience:

- Users can resolve duplicate or conflicting event families from the list instead of remembering which event form created them.
- The row action matches the visible trust warning, so review work is local to the problem the user is already seeing.

How user effort is reduced:

- Merge is one tap on the event the user wants to keep.
- Keep separate clears the repeated conflict warning for intentionally separate reminders without forcing data edits.

How user control is preserved:

- No merge, deactivation, verification, reminder cancellation, or keep-separate marker is applied automatically.
- The selected row determines which event survives a merge, and keep-separate preserves every active event.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.domain.usecase.ResolveEventConflictUseCaseTest \
  --tests com.example.ui.viewmodel.EventsViewModelTest \
  --tests com.example.ui.screens.events.EventsScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache \
  --rerun-tasks
```

Result: passed. `--rerun-tasks` was required after the first run used a stale transformed domain artifact.

`git diff --check` passed, and new T509 files were checked for trailing whitespace.

## 2026-06-26 - Event Trust Conflict Labels

Completed tasks:

- T508: Show source, verification, and conflict state.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt)
- [app/src/main/java/com/example/ui/screens/events/EventsScreen.kt](app/src/main/java/com/example/ui/screens/events/EventsScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/EventsViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/EventsViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/events/EventsScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/events/EventsScreenInteractionTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Events state now derives a trust map from each event's source, verification flag, confidence score, and active same-contact/type event groups.
- Event rows still show source and verification, and now add explicit duplicate-reminder or conflicting-date chips when active events collide.
- Manual "Save anyway" conflict overrides remain visible in the list because the trust state is derived from the saved event family, not only from `source == "CONFLICT"`.
- Tests cover ViewModel trust derivation plus Compose rendering for manual/imported/merged/conflict, duplicate, and date-conflict states.

Why this improves user experience:

- Users can see whether an upcoming moment is trustworthy, manually entered, imported, low confidence, duplicated, or date-conflicted directly in the Events list.
- Conflict state stays visible after the user intentionally keeps a separate reminder, reducing surprise when two reminders exist for the same relationship moment.

How user effort is reduced:

- Users no longer need to infer list trust from raw source strings or reopen the add-event flow to understand why multiple records exist.
- The list now identifies which event families should be reviewed before relying on reminders or AI-generated wishes.

How user control is preserved:

- The trust labels are informational only.
- They do not merge, delete, edit, verify, schedule, generate, approve, or send anything automatically; merge/keep resolution remains a future explicit action.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.EventsViewModelTest \
  --tests com.example.ui.screens.events.EventsScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

`git diff --check` passed.

## 2026-06-26 - Contact Detail Quality Impact

Completed tasks:

- T507: Show personalization quality impact.

Changed files:

- [app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt](app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt)
- [app/src/test/java/com/example/ui/screens/contacts/ContactDetailPersonalizationQualityCardTest.kt](app/src/test/java/com/example/ui/screens/contacts/ContactDetailPersonalizationQualityCardTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- The Contact Detail personalization quality card now shows an AI-impact line for low, partial, and ready quality states.
- Low-quality contacts warn that the next AI wish may sound generic.
- Partial-quality contacts explain that the next detail makes drafts more relationship-specific.
- Ready contacts clarify that richer wishes are possible while review and send remain user-controlled.

Why this improves user experience:

- Users no longer just see a missing-field checklist; they understand why the next detail matters.
- The card connects relationship context directly to AI output quality before generation.

How user effort is reduced:

- Users can prioritize the next context edit without guessing which fields change message quality.
- The impact line reduces trial-and-error regeneration caused by generic inputs.

How user control is preserved:

- The impact message is advisory.
- It does not infer private context, edit preferences, generate drafts, approve, or send.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.screens.contacts.ContactDetailBodySectionsTest \
  --tests com.example.ui.screens.contacts.ContactDetailPersonalizationQualityCardTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Contact Detail Grouped Sections

Completed tasks:

- T506: Group fields into essentials, personalization, automation, and history.

Changed files:

- [app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt](app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt)
- [app/src/test/java/com/example/ui/screens/contacts/ContactDetailBodySectionsTest.kt](app/src/test/java/com/example/ui/screens/contacts/ContactDetailBodySectionsTest.kt)
- [app/src/test/java/com/example/ui/screens/contacts/ContactDetailPersonalizationQualityCardTest.kt](app/src/test/java/com/example/ui/screens/contacts/ContactDetailPersonalizationQualityCardTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Contact Detail now groups the body into Essentials, Personalization, Automation, and History sections.
- Essentials contains contact info and upcoming wish generation.
- Personalization contains the quality card plus memory/gift/edit context actions.
- Automation contains VIP/channel shortcuts.
- History contains Memory Vault, Gift Advisor, and Chat History navigation.
- Tests cover section labels and verify actions still dispatch explicit callbacks.

Why this improves user experience:

- Users can scan the dense Contact Detail screen by intent instead of parsing one long mixed list.
- Related tasks now sit together: context improvement, automation setup, and relationship history are visually separated.

How user effort is reduced:

- Users can jump to the section that matches the task they came for.
- The screen exposes the same controls with less cognitive sorting.

How user control is preserved:

- The grouping is presentation-only.
- Editing preferences, setting VIP, changing channel, opening history surfaces, and generating wishes still require explicit user taps.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.screens.contacts.ContactDetailBodySectionsTest \
  --tests com.example.ui.screens.contacts.ContactDetailPersonalizationQualityCardTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Contact Action Filters

Completed tasks:

- T505: Add filters for missing relationship, missing channel, low health, and VIP.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/ContactListViewModel.kt](app/src/main/java/com/example/ui/viewmodel/ContactListViewModel.kt)
- [app/src/main/java/com/example/ui/screens/contacts/ContactListScreen.kt](app/src/main/java/com/example/ui/screens/contacts/ContactListScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/ContactListViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/ContactListViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/contacts/ContactListScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/contacts/ContactListScreenInteractionTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added Contacts filters for missing relationship, missing preferred-channel route, low relationship health, and VIP approval mode.
- The filters reuse existing contact semantics: `UNKNOWN`/blank relationship, preferred-channel reachability, Home's low-health threshold, and `VIP_APPROVE`.
- Contacts screen chip labels are resource-backed in English and Hindi.
- Tests cover filter predicates and chip label/callback behavior.

Why this improves user experience:

- Users can now jump directly to the contacts that need cleanup or special review instead of scanning the full list.
- The filters turn the T504 quality labels into actionable list segmentation.

How user effort is reduced:

- Missing relationship, missing channel, low-health, and VIP contacts are one tap away.
- Users no longer need to search manually or open each profile to find these groups.

How user control is preserved:

- Filters only change the visible list.
- They do not classify relationships, edit channel data, mark VIPs, change automation mode, generate messages, approve, or send.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.ContactListViewModelTest \
  --tests com.example.ui.screens.contacts.ContactListScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Contact Quality State

Completed tasks:

- T504: Add contact quality state model.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/ContactListViewModel.kt](app/src/main/java/com/example/ui/viewmodel/ContactListViewModel.kt)
- [app/src/main/java/com/example/ui/screens/contacts/ContactListScreen.kt](app/src/main/java/com/example/ui/screens/contacts/ContactListScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/ContactListViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/ContactListViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/contacts/ContactListScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/contacts/ContactListScreenInteractionTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added a typed `ContactQualityState` with `READY`, `MISSING_EVENT`, `MISSING_CHANNEL`, and `MISSING_CONTEXT` statuses.
- Contacts list state now computes quality for each contact from known event dates, preferred-channel reachability, and personalization context.
- Contact rows show a compact resource-backed quality label so users can see the next missing prerequisite without opening every profile.
- Tests cover the derived quality states and row badge rendering.

Why this improves user experience:

- The contact list now explains whether a person is ready for automation or which first detail blocks useful wishes.
- Missing event/channel/context states are visible before users enter a dense contact detail form.

How user effort is reduced:

- Users can scan the list for the next cleanup target instead of opening contacts one by one.
- Future missing-field filters can reuse the same computed model.

How user control is preserved:

- The quality label is advisory only.
- It does not infer private notes, change contact data, enable automation, generate messages, or send anything.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.ContactListViewModelTest \
  --tests com.example.ui.screens.contacts.ContactListScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Home Low-Health Relationship Action

Completed tasks:

- T503: Add low-health relationship action.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt](app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt)
- [app/src/main/java/com/example/ui/screens/home/HomeScreen.kt](app/src/main/java/com/example/ui/screens/home/HomeScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/HomeViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/HomeViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/home/HomeScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/home/HomeScreenInteractionTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added `HomeNextActionKind.RECONNECT_CONTACT`.
- The lowest-health contact under the Home at-risk threshold can now become the primary ranked action when setup, approvals, and backup work are not more urgent.
- When backup risk is more urgent, the reconnect action appears as a supporting action.
- The relationship planner skips the contact already promoted to the ranked action, avoiding duplicate dashboard prompts.

Why this improves user experience:

- Home can recommend an actual relationship-care task, not only operational setup/recovery work.
- The action names the contact and relationship health score before navigation.

How user effort is reduced:

- Users can open the exact low-health contact in one tap.
- Users no longer need to scan planner cards to find the most neglected relationship.

How user control is preserved:

- The action only opens Contact Detail.
- Adding memories, generating a check-in, approving drafts, and sending remain explicit choices.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.HomeViewModelTest \
  --tests com.example.ui.screens.home.HomeScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Home Setup Blocker Summary

Completed tasks:

- T502: Surface setup blocker summary from AI Doctor.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt](app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt)
- [app/src/main/java/com/example/ui/screens/home/HomeScreen.kt](app/src/main/java/com/example/ui/screens/home/HomeScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/HomeViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/HomeViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/home/HomeScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/home/HomeScreenInteractionTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Split Home setup next actions into concrete blocker kinds: contact sync issue, missing AI access, and disabled AI generation.
- Home ranked actions now show blocker-specific titles/details instead of a generic setup count.
- The action still routes to AI Doctor for the full diagnostic and fix path.
- Tests cover contact sync, missing AI access, disabled AI generation, and Home screen copy/routing.

Why this improves user experience:

- Users understand the setup problem before leaving Home.
- The dashboard no longer asks users to open AI Doctor just to learn which first blocker matters.

How user effort is reduced:

- Reduces scanning inside AI Doctor by naming the first concrete issue at the dashboard level.
- Keeps Home as the command center while preserving AI Doctor as the detailed diagnostic surface.

How user control is preserved:

- Home only navigates to AI Doctor.
- Permissions, credentials, AI generation settings, and sync actions still require explicit user action.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.HomeViewModelTest \
  --tests com.example.ui.screens.home.HomeScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Home Ranked Next Action

Completed tasks:

- T500: Add ranked next-action model.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt](app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt)
- [app/src/main/java/com/example/ui/screens/home/HomeScreen.kt](app/src/main/java/com/example/ui/screens/home/HomeScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/HomeViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/HomeViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/home/HomeScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/home/HomeScreenInteractionTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added typed `HomeNextAction` and `HomeNextActionKind` models.
- Home now ranks setup, pending approval, and backup work into one primary action and up to three supporting actions.
- The screen renders the primary action before setup progress, stats, quick actions, planner, and birthdays.
- Supporting actions route through the same typed navigation target model already used by dashboard cards.
- Tests verify pending review outranks stale backup, backup can become the primary action, supporting actions route correctly, and new text remains resource-backed/localized.

Why this improves user experience:

- Users see one clear next step instead of several equal-weight dashboard prompts.
- When multiple operational tasks exist, Home guides the first decision rather than making users scan every card.

How user effort is reduced:

- Fewer dashboard taps and less visual comparison are needed to decide what to do first.
- Approvals, setup fixes, and backup work each route directly to their task screen.

How user control is preserved:

- Ranked actions only navigate.
- Sync, setup changes, message approval, backup export, and restore still require explicit user action in the destination workflow.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.HomeViewModelTest \
  --tests com.example.ui.screens.home.HomeScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Home Backup Freshness Prompt

Completed tasks:

- T501: Surface stale/never backup status on Home.

Changed files:

- [app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt](app/src/main/java/com/example/ui/viewmodel/HomeViewModel.kt)
- [app/src/main/java/com/example/ui/screens/home/HomeScreen.kt](app/src/main/java/com/example/ui/screens/home/HomeScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/HomeViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/HomeViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/home/HomeScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/home/HomeScreenInteractionTest.kt)
- [core/domain/src/main/kotlin/com/example/domain/service/PreferencesRepository.kt](core/domain/src/main/kotlin/com/example/domain/service/PreferencesRepository.kt)
- [core/data/src/main/kotlin/com/example/core/prefs/PreferencesRepositoryImpl.kt](core/data/src/main/kotlin/com/example/core/prefs/PreferencesRepositoryImpl.kt)
- [core/data/src/main/kotlin/com/example/core/prefs/SecurePrefs.kt](core/data/src/main/kotlin/com/example/core/prefs/SecurePrefs.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/DailyTriggerWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/DailyTriggerWorker.kt)
- [app/src/test/java/com/example/core/automation/workers/DailyTriggerWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/DailyTriggerWorkerTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [SSOT.md](SSOT.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Home now computes a backup freshness prompt when local relationship data has never been backed up or the last real export is at least 30 days old.
- The Home screen shows a resource-backed prompt that routes to Backup & Restore.
- `PreferencesRepository` exposes the real last-backup timestamp for Home.
- `DailyTriggerWorker` now uses a separate backup-reminder timestamp, so notification throttling no longer writes a fake successful backup time.
- Tests cover never-backed-up, stale, and recent backup Home states, the Home prompt route, and worker reminder semantics.

Why this improves user experience:

- Data-loss risk is visible in the daily command center, not only in Settings.
- The app can honestly distinguish "never backed up" from "recently reminded about backup."

How user effort is reduced:

- Users do not need to open Settings or Backup & Restore just to discover backup risk.
- One tap from Home opens the exact recovery workflow.

How user control is preserved:

- The Home prompt only navigates.
- Backup export still requires the user to choose a destination and passphrase.
- Restore still uses preview and explicit replace confirmation.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.HomeViewModelTest \
  --tests com.example.ui.screens.home.HomeScreenInteractionTest \
  --tests com.example.core.automation.workers.DailyTriggerWorkerTest \
  --tests com.example.ui.LocalizationParityTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Backup Replace Restore Mode

Completed tasks:

- T407: Add restore mode decision: replace first, merge later.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/service/BackupService.kt](core/domain/src/main/kotlin/com/example/domain/service/BackupService.kt)
- [core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt](core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt)
- [core/data/src/main/kotlin/com/example/core/db/dao/ContactDao.kt](core/data/src/main/kotlin/com/example/core/db/dao/ContactDao.kt)
- [core/data/src/main/kotlin/com/example/core/db/dao/EventDao.kt](core/data/src/main/kotlin/com/example/core/db/dao/EventDao.kt)
- [core/data/src/main/kotlin/com/example/core/db/dao/PendingMessageDao.kt](core/data/src/main/kotlin/com/example/core/db/dao/PendingMessageDao.kt)
- [core/data/src/main/kotlin/com/example/core/db/dao/SentMessageDao.kt](core/data/src/main/kotlin/com/example/core/db/dao/SentMessageDao.kt)
- [core/data/src/main/kotlin/com/example/core/db/dao/MemoryNoteDao.kt](core/data/src/main/kotlin/com/example/core/db/dao/MemoryNoteDao.kt)
- [core/data/src/main/kotlin/com/example/core/db/dao/GiftHistoryDao.kt](core/data/src/main/kotlin/com/example/core/db/dao/GiftHistoryDao.kt)
- [core/data/src/main/kotlin/com/example/core/db/dao/ActivityLogDao.kt](core/data/src/main/kotlin/com/example/core/db/dao/ActivityLogDao.kt)
- [core/data/src/main/kotlin/com/example/core/db/dao/MessageFeedbackDao.kt](core/data/src/main/kotlin/com/example/core/db/dao/MessageFeedbackDao.kt)
- [core/data/src/main/kotlin/com/example/core/db/dao/StyleProfileDao.kt](core/data/src/main/kotlin/com/example/core/db/dao/StyleProfileDao.kt)
- [app/src/main/java/com/example/ui/viewmodel/BackupRestoreViewModel.kt](app/src/main/java/com/example/ui/viewmodel/BackupRestoreViewModel.kt)
- [app/src/main/java/com/example/ui/screens/backup/BackupRestoreScreen.kt](app/src/main/java/com/example/ui/screens/backup/BackupRestoreScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/BackupRestoreViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/BackupRestoreViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/backup/BackupRestoreScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/backup/BackupRestoreScreenInteractionTest.kt)
- [core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt](core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added a typed `BackupRestoreMode.REPLACE` contract to backup preview and import results.
- Restore preview now carries the restore mode through the ViewModel, and the Backup & Restore screen warns that restoring replaces existing local relationship data while merge restore is deferred.
- Confirmed restore now clears existing restorable tables inside the same Room transaction before inserting backup rows.
- Delete ordering removes dependent rows before contacts, then restores from the backup payload.
- Tests verify replace mode is exposed, current local relationship data is replaced by the backup, and failures roll back the pre-insert deletes.

Why this improves user experience:

- Users no longer have ambiguous restore semantics: the app says replace, and the service performs replace.
- The warning appears before the second restore confirmation, after the manifest/count preview.

How user effort is reduced:

- Users do not need to discover merge-vs-replace behavior by inspecting restored records after the fact.
- Support/debugging can treat restore behavior as one explicit mode until merge is built.

How user control is preserved:

- File selection still previews without mutation.
- Restore still requires explicit confirmation.
- Invalid payloads, wrong passphrases, unsupported versions, and insert failures do not leave the database half-cleared.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :core:data:testDebugUnitTest --tests com.example.core.backup.BackupServiceImplTest \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.BackupRestoreViewModelTest \
  --tests com.example.ui.screens.backup.BackupRestoreScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Backup Import Preview Before Restore

Completed tasks:

- T404: Add import preview before DB mutation.
- T516: Add import manifest preview in the backup UI.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/service/BackupService.kt](core/domain/src/main/kotlin/com/example/domain/service/BackupService.kt)
- [core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt](core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt)
- [core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt](core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt)
- [app/src/main/java/com/example/ui/viewmodel/BackupRestoreViewModel.kt](app/src/main/java/com/example/ui/viewmodel/BackupRestoreViewModel.kt)
- [app/src/main/java/com/example/ui/screens/backup/BackupRestoreScreen.kt](app/src/main/java/com/example/ui/screens/backup/BackupRestoreScreen.kt)
- [app/src/test/java/com/example/ui/viewmodel/BackupRestoreViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/BackupRestoreViewModelTest.kt)
- [app/src/test/java/com/example/ui/screens/backup/BackupRestoreScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/backup/BackupRestoreScreenInteractionTest.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added `BackupService.previewBackup(...)` with typed `BackupPreviewResult` and `BackupRecordCounts`.
- `BackupServiceImpl` now shares decrypt/version/checksum validation between preview and import.
- Selecting an import file previews backup version, app version, export timestamp, and total restorable records without mutating the database.
- `BackupRestoreViewModel` stores the selected import URI internally and restores only after explicit confirmation.
- The Backup & Restore screen now shows a review card and separate `Restore now` action before database restore.

Why this improves user experience:

- Users can verify they selected a plausible backup before replacing or merging local relationship data.
- Wrong passphrase, invalid file, unsupported version, and checksum failures are shown before any database write.

How user effort is reduced:

- Users avoid accidental restores from the wrong file and get immediate count/version feedback.
- Support/debug flows can inspect preview metadata without triggering mutation.

How user control is preserved:

- File selection no longer restores automatically.
- Restore requires a second explicit confirmation after preview.
- The preview path does not import credentials, enable sending, or change database rows.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :core:data:testDebugUnitTest --tests com.example.core.backup.BackupServiceImplTest \
  :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.BackupRestoreViewModelTest \
  --tests com.example.ui.screens.backup.BackupRestoreScreenInteractionTest \
  --tests com.example.ui.LocalizationParityTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Backup Export Temp File Cleanup

Completed tasks:

- T406: Remove the internal encrypted `.enc` copy after successful URI export.

Changed files:

- [core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt](core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt)
- [core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt](core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Selected-document exports now copy the encrypted backup to the user-selected URI and delete the internal `relateai_backup_*.enc` file afterward.
- Export result metadata captures the generated file name and size before cleanup, so UI success behavior remains stable.
- The backup test suite now asserts no generated internal backup file remains after selected-URI export.

Why this improves user experience:

- Backup export no longer leaves hidden encrypted copies in app storage after the user saves a backup elsewhere.
- Storage behavior better matches user expectation: the selected file is the durable backup artifact.

How user effort is reduced:

- Users do not need to manually clear app storage to remove redundant encrypted export files.
- Future support/debugging has less ambiguity about which backup copy is authoritative.

How user control is preserved:

- Exports without a selected URI still return the internal backup artifact as before.
- The cleanup does not change backup contents, passphrase requirements, restore behavior, or credential exclusions.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest \
  --tests com.example.core.backup.BackupServiceImplTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Backup V2 Manifest and Relationship Data Scope

Completed tasks:

- T400: Define a backup v2 manifest with version, app version, record counts, and checksum.
- T401: Include activity logs and message feedback in backup export/import.
- T402: Add a non-secret preference subset to backup payloads.
- T403: Add secret-exclusion coverage for credential preference keys.

Changed files:

- [core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt](core/data/src/main/kotlin/com/example/core/backup/BackupServiceImpl.kt)
- [core/data/src/main/kotlin/com/example/core/db/dao/ActivityLogDao.kt](core/data/src/main/kotlin/com/example/core/db/dao/ActivityLogDao.kt)
- [core/data/src/main/kotlin/com/example/core/db/dao/MessageFeedbackDao.kt](core/data/src/main/kotlin/com/example/core/db/dao/MessageFeedbackDao.kt)
- [core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt](core/data/src/test/kotlin/com/example/core/backup/BackupServiceImplTest.kt)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Backup exports now write version 2 payloads with a `manifest` containing backup version, app version, export timestamp, record counts, and a SHA-256 checksum over the exported data snapshot.
- Imports remain compatible with version 1 payloads and reject version 2 payloads whose manifest checksum does not match the data.
- Activity logs and message feedback are now exported and restored with the rest of the relationship data.
- Backup payloads include a non-secret settings subset: automation mode, theme, blackout dates, quiet hours, channel blackout, biometric lock flag, birthday reminders flag, and AI generation flag.
- Backup preference capture and restore are best-effort; if encrypted preferences are temporarily unavailable, database backup/restore still proceeds instead of blocking relationship data recovery.
- Tests verify manifest serialization, count fields, checksum presence, activity/feedback restore, preference schema inclusion, and absence of known credential preference keys.

Why this improves user experience:

- Backups now preserve more of the user-visible relationship history, including activity explanations and regeneration feedback context.
- Restore failures caused by tampered or mismatched v2 payloads are detected before database mutation.
- Backup behavior is more transparent because the payload carries machine-readable counts for future preview UI.

How user effort is reduced:

- Users do not have to rebuild audit history or feedback context manually after restore.
- Future import preview can use the manifest directly instead of scanning the full payload in the UI layer.

How user control is preserved:

- Device-bound credentials, OAuth tokens, Gemini API key, Gmail app password, Firebase UID, and sync token keys stay out of the backup schema.
- This slice does not silently import credentials or enable new sending capability.
- Restore still requires the user-provided passphrase and keeps unsupported/tampered payloads out of the database.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest \
  --tests com.example.core.backup.BackupServiceImplTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - AI Parse Metadata and Redacted Fallback Logging

Completed tasks:

- T204: Return structured parse metadata for malformed message-variant JSON.
- T205: Log malformed/error AI responses without message body or secrets.
- T211: Update the AI contract section in `PLAN.md` after the fix.

Changed files:

- [core/data/src/main/kotlin/com/example/core/gemini/ResponseParser.kt](core/data/src/main/kotlin/com/example/core/gemini/ResponseParser.kt)
- [core/data/src/main/kotlin/com/example/core/gemini/AiServiceImpl.kt](core/data/src/main/kotlin/com/example/core/gemini/AiServiceImpl.kt)
- [app/src/test/java/com/example/core/gemini/ResponseParserTest.kt](app/src/test/java/com/example/core/gemini/ResponseParserTest.kt)
- [app/src/test/java/com/example/core/gemini/AiServiceImplTest.kt](app/src/test/java/com/example/core/gemini/AiServiceImplTest.kt)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- `MessageVariants` now carries `MessageVariantParseMetadata` with `SUCCESS` or `FALLBACK` status and a structured fallback reason.
- Message parsing distinguishes malformed JSON from explicit AI error payloads.
- `AiServiceImpl` logs fallback usage with operation, event ID, event type, and fallback reason only.
- The fallback log intentionally omits raw model output, prompt text, generated message text, phone numbers, email addresses, and contact names.

Why this improves user experience:

- AI fallback states are now diagnosable without exposing sensitive relationship content.
- Support/debugging can distinguish malformed model output from explicit error payloads.

How user effort is reduced:

- Users and maintainers no longer need to infer why a fallback message appeared from generic `isUsingFallback` state alone.
- Future UI or activity-history work can surface clearer non-technical fallback reasons.

How user control is preserved:

- This change does not auto-send, approve, regenerate, or alter generated copy.
- It only records operational metadata about fallback decisions.
- Raw AI responses and private contact context remain out of structured logs.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.core.gemini.ResponseParserTest \
  --tests com.example.core.gemini.AiServiceImplTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - AI Classification Contract Hardening

Completed tasks:

- T207: Parse `communication_style` with enum normalization.
- T208: Handle old field names and missing/unsupported style safely.
- T209: Ensure `ClassifyContactUseCase` persists normalized parsed style.
- T210: Add a central contract for classification field names and accepted values.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/service/ContactClassificationContract.kt](core/domain/src/main/kotlin/com/example/domain/service/ContactClassificationContract.kt)
- [core/data/src/main/kotlin/com/example/core/gemini/PromptBuilder.kt](core/data/src/main/kotlin/com/example/core/gemini/PromptBuilder.kt)
- [core/data/src/main/kotlin/com/example/core/gemini/ResponseParser.kt](core/data/src/main/kotlin/com/example/core/gemini/ResponseParser.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/ClassifyContactUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/ClassifyContactUseCase.kt)
- [app/src/test/java/com/example/core/gemini/PromptBuilderTest.kt](app/src/test/java/com/example/core/gemini/PromptBuilderTest.kt)
- [app/src/test/java/com/example/core/gemini/ResponseParserTest.kt](app/src/test/java/com/example/core/gemini/ResponseParserTest.kt)
- [app/src/test/java/com/example/domain/usecase/ClassifyContactUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/ClassifyContactUseCaseTest.kt)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Added `ContactClassificationContract` as the single source for classification JSON field names, accepted relationship types, languages, formality levels, and communication styles.
- The classification prompt now emits canonical `relationship_type`, `relationship_subtype`, `confidence`, `language`, `formality`, and `communication_style` fields.
- The parser accepts canonical fields plus legacy `type`, `subtype`, `communicationStyle`, and `style` aliases.
- Relationship type, language, formality, and communication style are normalized before they leave parsing and again before contact classification is persisted.
- Missing or unsupported communication style falls back to `WARM` and records structured telemetry without storing the AI response body.

Why this improves user experience:

- AI classification can no longer silently save unsupported communication styles that are not available in the contact preferences UI.
- Existing users are protected from older AI response shapes while the app moves to a clearer canonical schema.

How user effort is reduced:

- Users should see fewer contacts with mismatched or confusing style/profile values after sync classification.
- Legacy AI responses do not require manual correction when they can be safely mapped.

How user control is preserved:

- The classification still only runs for unknown relationship types.
- Defaults are conservative, visible through contact preferences, and can be overridden by the user.
- Telemetry records schema/default events, not private contact notes or AI response content.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.core.gemini.PromptBuilderTest \
  --tests com.example.core.gemini.ResponseParserTest \
  --tests com.example.domain.usecase.ClassifyContactUseCaseTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Dispatch Activity Audit Trail

Completed tasks:

- T115: Log defer, expire, blocked, and send decisions with redacted details.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt)
- [app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Domain dispatch now records best-effort `ActivityLogEntity` rows for deferred, needs-approval, expired, blocked, contact-missing, and sent outcomes.
- Dispatch activity metadata includes only operational IDs and policy state: decision, message ID, event ID, contact ID, channel, approval mode, status, reason, and scheduled time when relevant.
- Activity details intentionally avoid message body text, phone numbers, emails, or contact names.
- The dispatch use-case tests verify the audit entries and confirm sent logs do not contain draft text.

Why this improves user experience:

- Activity History can explain why a message was not sent yet, why it expired, or when it was dispatched.
- Users get a clearer audit trail for automation behavior without exposing sensitive message content.

How user effort is reduced:

- Users and support/debug flows no longer need to infer dispatch outcomes from raw message status alone.
- Deferred and blocked outcomes become visible recovery signals instead of silent no-ops.

How user control is preserved:

- Logging is observational only; it does not approve, send, retry, or change channel setup.
- Sensitive delivery details and message text stay out of the audit metadata.
- Policy gates still decide whether dispatch can proceed.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.DispatchMessageUseCaseTest \
  --no-configuration-cache
```

Result: passed.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest \
  --tests com.example.core.automation.notifications.ApprovalNotificationActionPolicyTest \
  --no-configuration-cache
```

Result: passed.

```bash
git diff --check
```

Result: passed.

## 2026-06-26 - Notification Approval Dispatch Safety

Completed tasks:

- T114: Ensure approval notification actions use the same eligibility rules.

Changed files:

- [core/data/src/main/kotlin/com/example/core/automation/notifications/ApprovalNotificationActionPolicy.kt](core/data/src/main/kotlin/com/example/core/automation/notifications/ApprovalNotificationActionPolicy.kt)
- [core/data/src/main/kotlin/com/example/core/automation/notifications/ApprovalReceiver.kt](core/data/src/main/kotlin/com/example/core/automation/notifications/ApprovalReceiver.kt)
- [core/data/src/test/kotlin/com/example/core/automation/notifications/ApprovalNotificationActionPolicyTest.kt](core/data/src/test/kotlin/com/example/core/automation/notifications/ApprovalNotificationActionPolicyTest.kt)
- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- Notification approve actions now flow through `ApprovalNotificationActionPolicy`, which delegates to the shared `DispatchEligibilityPolicy`.
- Approving a future review-gated message now approves and schedules it instead of relying on receiver-local time checks.
- Approving a due review-gated message approves and dispatches it immediately through WorkManager.
- Expired VIP approvals are marked `EXPIRED` instead of being approved/sent from a stale notification.
- Already-handled messages are blocked and any exact-send alarm for that message is cancelled.

Why this improves user experience:

- Users can trust that approving from a notification behaves like approving from the in-app flow.
- Stale VIP approval notifications no longer create surprising sends after the approval window has elapsed.

How user effort is reduced:

- Users do not need to open the app just to avoid notification/action behavior drift.
- Future approvals are automatically scheduled after the tap, instead of requiring manual recovery later.

How user control is preserved:

- The user still explicitly taps approve before a review-gated message can move to approved state.
- Schedule, expiry, and handled-state gates remain enforced by the same domain policy used elsewhere.
- No permission, credential, or channel setup is enabled by this change.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :core:data:testDebugUnitTest \
  --tests com.example.core.automation.notifications.ApprovalNotificationActionPolicyTest \
  --tests com.example.core.automation.workers.MessageDispatchWorkRequestsTest \
  --no-configuration-cache
```

Result: passed.

```bash
git diff --check
```

Result: passed.

## 2026-06-26 - PLAN Stabilization Status Alignment

Completed tasks:

- T118: Update `PLAN.md` debt D-001/D-002 status after automation dispatch safety landed.
- T211: Update `PLAN.md` AI contract status after event-aware fallback and classification schema fixes landed.
- T316: Update `PLAN.md` data integrity status for D-005, D-006, D-008, and D-009.

Changed files:

- [PLAN.md](PLAN.md)
- [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

What changed:

- The executive summary, feature audits, roadmap, debt registry, and testing sections now distinguish resolved stabilization items from open follow-ups.
- D-001 through D-006, D-008, and D-009 are marked resolved with links to current implementation and tests.
- Product-doc conflict, richer UI readiness badges, remaining worker reuse, and full P7 validation remain open instead of being implied complete; later entries resolve parse metadata, central AI schema constants, dispatch activity-log detail, weekly generation-worker reuse, the backup v2 foundation, selected-URI backup cleanup, import preview, and replace restore mode.
- The testing section now records that targeted Gradle suites pass with explicit JDK 21 while full unit, lint, assemble, emulator, and device validation remain pending.

Why this improves user experience:

- Product and engineering decisions now reflect the safer automation, AI, event, channel, regeneration, and sync behavior already implemented.
- Remaining UX gaps are clearer, so future work can target visible user problems instead of re-solving fixed defects.

How user effort is reduced:

- Maintainers do not need to cross-check stale PLAN risks against the progress log before choosing the next task.
- The roadmap now points directly to the next user-facing improvements: backup recovery, clearer readiness badges, and merge/keep controls.

How user control is preserved:

- The documentation keeps explicit follow-ups for permission-gated actions, notification actions, merge/keep decisions, and full release validation.
- Resolved automation items are documented as conservative gates, not silent enablement of sends, permissions, or credentials.

Validation:

```bash
rg -n "no Java runtime|Unable to locate a Java Runtime|failed before Gradle|manual birthday can duplicate|permission failure can look|manual save creates|does not recompute channel|can store an unavailable channel|service path does not|schema mismatch defaults" PLAN.md
```

Result: no stale unresolved wording found.

```bash
git diff --check
```

Result: passed.

## 2026-06-26 - Event Date Conflict Visibility

Completed tasks:

- T304: Add conflict outcome for same event type with different date.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/event/EventIdentityPolicy.kt](core/domain/src/main/kotlin/com/example/domain/event/EventIdentityPolicy.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/SaveManualEventUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/SaveManualEventUseCase.kt)
- [app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/EventsViewModel.kt)
- [app/src/main/java/com/example/ui/screens/events/EventsScreen.kt](app/src/main/java/com/example/ui/screens/events/EventsScreen.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [app/src/test/java/com/example/domain/usecase/SaveManualEventUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/SaveManualEventUseCaseTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/EventsViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/EventsViewModelTest.kt)

What changed:

- Manual event saving now checks for active same-contact, same-type events with a different date before persisting.
- Exact same-date matches still surface as duplicate warnings; different-date matches now surface as date conflicts with both the existing and requested dates.
- Explicit save-anyway on a conflicting standard event creates a separate manual event ID instead of replacing the canonical event, and it preserves the contact's existing primary date fields.
- The Add Event dialog now shows conflict-specific copy instead of presenting all warnings as possible duplicates.

Why this improves user experience:

- Users see when a birthday or anniversary date disagrees with an existing record before the app changes reminders.
- The warning explains the specific mismatch, making it easier to decide whether the new date is a correction or a separate reminder.

How user effort is reduced:

- Users no longer need to clean up silently overwritten canonical reminders after entering a conflicting date.
- The dialog shows both dates in place, so users do not need to leave the flow to compare records.

How user control is preserved:

- The default path blocks mutation when a conflict is detected.
- Users can still intentionally save a separate reminder with the existing save-anyway action.
- Existing imported/manual canonical date data remains intact unless a future explicit correction workflow changes it.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.SaveManualEventUseCaseTest \
  --tests com.example.ui.viewmodel.EventsViewModelTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --tests com.example.ui.LocalizationParityTest \
  --no-configuration-cache
```

Result: passed.

```bash
git diff --check
```

Result: passed.

## 2026-06-26 - Event Source and Verification Visibility

Completed tasks:

- T307: Show event source and verification status in the event list row.

Changed files:

- [app/src/main/java/com/example/ui/screens/events/EventsScreen.kt](app/src/main/java/com/example/ui/screens/events/EventsScreen.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [app/src/test/java/com/example/ui/screens/events/EventsScreenInteractionTest.kt](app/src/test/java/com/example/ui/screens/events/EventsScreenInteractionTest.kt)

What changed:

- Event cards now show localized source chips for imported, manual, calendar, AI-inferred, merged, and conflict sources.
- Event cards now show verification chips: verified, needs review with confidence score, or conflict to resolve.
- The previous raw `event.source` subtitle value was replaced with user-readable metadata.
- Event list rendering is covered by a Compose interaction test for manual, imported, merged, and conflict states.

Why this improves user experience:

- Users can see whether an event came from contacts, manual entry, merging, or a conflict without opening another workflow.
- Low-confidence and conflict events are visible before users rely on them for reminders or AI messages.

How user effort is reduced:

- Users no longer need to infer trust from raw source strings or inspect data indirectly.
- The list row points attention directly to events that need review.

How user control is preserved:

- Manual events remain clearly labeled as manual.
- Imported and merged events are transparent instead of silently replacing user-entered data.
- Conflict and low-confidence states stay visible for user review.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.screens.events.EventsScreenInteractionTest \
  --tests com.example.ui.NoHardcodedStringsRegressionTest \
  --tests com.example.ui.LocalizationParityTest \
  --no-configuration-cache
```

Result: passed.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.EventsViewModelTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Contact Sync Integrity

Completed tasks:

- T314: Encode People API page and sync tokens.
- T315: Return a permission-specific outcome when device contacts permission is denied.

Changed files:

- [core/data/src/main/kotlin/com/example/core/contacts/PeopleConnectionsRequestUrl.kt](core/data/src/main/kotlin/com/example/core/contacts/PeopleConnectionsRequestUrl.kt)
- [core/data/src/main/kotlin/com/example/core/contacts/GoogleContactsSync.kt](core/data/src/main/kotlin/com/example/core/contacts/GoogleContactsSync.kt)
- [core/data/src/main/kotlin/com/example/core/contacts/DeviceContactsReader.kt](core/data/src/main/kotlin/com/example/core/contacts/DeviceContactsReader.kt)
- [core/domain/src/main/kotlin/com/example/domain/service/ContactSyncService.kt](core/domain/src/main/kotlin/com/example/domain/service/ContactSyncService.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/SyncContactsUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/SyncContactsUseCase.kt)
- [app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt](app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)
- [app/src/main/res/values-hi/strings.xml](app/src/main/res/values-hi/strings.xml)
- [core/data/src/test/kotlin/com/example/core/contacts/PeopleConnectionsRequestUrlTest.kt](core/data/src/test/kotlin/com/example/core/contacts/PeopleConnectionsRequestUrlTest.kt)
- [app/src/test/java/com/example/domain/usecase/SyncContactsUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/SyncContactsUseCaseTest.kt)
- [app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt](app/src/test/java/com/example/ui/viewmodel/SettingsViewModelTest.kt)

What changed:

- People API connection URLs are now built with OkHttp's structured `HttpUrl` builder instead of string concatenation.
- Sync and page tokens are encoded as query parameter values, preventing special characters from corrupting the URL.
- Device contact permission denial now throws `DeviceContactsPermissionDeniedException` instead of returning an empty contact list.
- `SyncContactsUseCase.SyncOutcome` now includes `deviceContactsPermissionDenied`.
- Settings sync surfaces a permission-specific message when phone-contact import is blocked by missing `READ_CONTACTS`.

Why this improves user experience:

- Google Contacts incremental sync is less likely to fail for valid tokens containing reserved URL characters.
- Users can distinguish "no phone contacts exist" from "phone contacts could not be imported because permission is missing."

How user effort is reduced:

- Fewer sync retries fail because of malformed People API URLs.
- Permission recovery is clearer and does not require users to infer the problem from an empty contacts list.

How user control is preserved:

- Device contacts are imported only after explicit Android permission.
- Missing permission does not block Google contacts from syncing when Google access is available.
- The app reports the permission gap instead of silently broadening access.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :core:data:testDebugUnitTest --tests com.example.core.contacts.PeopleConnectionsRequestUrlTest \
  :app:testDebugUnitTest \
  --tests com.example.domain.usecase.SyncContactsUseCaseTest \
  --tests com.example.ui.viewmodel.SettingsViewModelTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Regeneration Safety

Completed tasks:

- T311: Re-run the AI quality gate after regeneration.
- T312: Recompute route readiness after regeneration.
- T313: Clear stale edited/approved state by default, preserving it only when explicitly requested.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/usecase/RegeneratePendingMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/RegeneratePendingMessageUseCase.kt)
- [app/src/test/java/com/example/domain/usecase/RegeneratePendingMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/RegeneratePendingMessageUseCaseTest.kt)

What changed:

- Regeneration now re-resolves the intended approval mode from current contact and global automation preferences.
- Regenerated text is evaluated through the AI auto-send quality gate, updating approval mode where needed, `qualityScore`, and fallback metadata.
- Channel readiness is recomputed with `AutoSendChannelSelector.selectRoute()`, updating the saved channel and forcing no-route drafts to `ALWAYS_ASK`.
- Stale `editedByUser`, `userEditedText`, and user-approved `APPROVED` status are cleared by default.
- Explicit parameters allow preserving user-edited text or approved status for future workflows that intentionally need that behavior.
- Regenerated auto-schedulable drafts are re-scheduled through `SchedulerService`; no-route drafts are not scheduled.

Why this improves user experience:

- A regenerated generic or fallback draft no longer hides stale quality metadata; blank/invalid or no-route regenerated drafts are kept review-first.
- If contact details or channel setup changed since the original draft, regeneration reflects the current delivery reality.
- Users see safer review states after regeneration instead of hidden stale approval/send state.

How user effort is reduced:

- Users do not need to discover later that a regenerated draft failed because route readiness was outdated.
- Blank/invalid or no-route regenerated drafts are automatically placed back into review, reducing manual audit work.

How user control is preserved:

- Regeneration replaces automation readiness conservatively and clears stale edits unless explicitly preserved.
- No-route regeneration cannot auto-send.
- Explicit preservation knobs exist for workflows that intentionally keep user edits or prior approval.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.RegeneratePendingMessageUseCaseTest \
  --no-configuration-cache
```

Result: passed.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.ui.viewmodel.WishPreviewViewModelTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Channel Route Readiness

Completed tasks:

- T308: Change `AutoSendChannelSelector` to return a typed route result.
- T309: Update generation paths so no-route drafts are review-only instead of auto-ready fallback SMS.
- T310: Align generation route readiness with runtime dispatch route availability rules.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/automation/AutoSendChannelSelector.kt](core/domain/src/main/kotlin/com/example/domain/automation/AutoSendChannelSelector.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/GenerateMessageUseCase.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/MessageGenerationWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/MessageGenerationWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/HolidayWishWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/HolidayWishWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/RevivalWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/PostEventFollowUpWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/PostEventFollowUpWorker.kt)
- [core/domain/src/test/kotlin/com/example/domain/automation/AutoSendChannelSelectorTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/AutoSendChannelSelectorTest.kt)
- [app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/GenerateMessageUseCaseTest.kt)
- [app/src/test/java/com/example/core/automation/workers/MessageGenerationWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/MessageGenerationWorkerTest.kt)
- [app/src/test/java/com/example/core/automation/workers/HolidayWishWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/HolidayWishWorkerTest.kt)
- [app/src/test/java/com/example/core/automation/workers/RevivalWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/RevivalWorkerTest.kt)
- [app/src/test/java/com/example/core/automation/workers/PostEventFollowUpWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/PostEventFollowUpWorkerTest.kt)

What changed:

- `AutoSendChannelSelector` now exposes `selectRoute()`, returning either a selected channel with available routes or a no-route result with explicit reasons.
- Existing `select()` behavior remains as a compatibility wrapper for channel-only callers.
- Birthday/event, holiday, revival, and post-event follow-up generators now force no-route drafts to `ALWAYS_ASK` and `PENDING`.
- No-route drafts keep a display fallback channel for editing, but are not scheduled for automatic dispatch.

Why this improves user experience:

- Users no longer see messages silently marked ready for automation when the app has no phone number, email route, or unblocked channel to use.
- Background-generated drafts become visible review items instead of failing later in the dispatch worker.

How user effort is reduced:

- Users spend less time recovering failed or misleading automation attempts.
- The app identifies route readiness at draft creation time, before the user expects delivery.

How user control is preserved:

- No-route automation is downgraded to explicit review.
- Users can still edit the draft, add contact/channel details, and approve when ready.
- Existing fully automatic and Smart Approve scheduling remains available when a real route exists.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :core:domain:testDebugUnitTest --tests com.example.domain.automation.AutoSendChannelSelectorTest \
  :app:testDebugUnitTest \
  --tests com.example.domain.usecase.GenerateMessageUseCaseTest \
  --tests com.example.core.automation.workers.MessageGenerationWorkerTest \
  --tests com.example.core.automation.workers.HolidayWishWorkerTest \
  --tests com.example.core.automation.workers.RevivalWorkerTest \
  --tests com.example.core.automation.workers.PostEventFollowUpWorkerTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - AI Contract Correctness

Completed tasks:

- T201: Pass event type to `ResponseParser.parseMessageVariants` in the generate path.
- T202: Pass event type to `ResponseParser.parseMessageVariants` in the regeneration path.
- T203: Add parser coverage for anniversary and work-anniversary fallback copy.
- T206: Add `communication_style` to the classification prompt JSON schema.

Changed files:

- [core/data/src/main/kotlin/com/example/core/gemini/AiServiceImpl.kt](core/data/src/main/kotlin/com/example/core/gemini/AiServiceImpl.kt)
- [core/data/src/main/kotlin/com/example/core/gemini/PromptBuilder.kt](core/data/src/main/kotlin/com/example/core/gemini/PromptBuilder.kt)
- [app/src/test/java/com/example/core/gemini/AiServiceImplTest.kt](app/src/test/java/com/example/core/gemini/AiServiceImplTest.kt)
- [app/src/test/java/com/example/core/gemini/PromptBuilderTest.kt](app/src/test/java/com/example/core/gemini/PromptBuilderTest.kt)
- [app/src/test/java/com/example/core/gemini/ResponseParserTest.kt](app/src/test/java/com/example/core/gemini/ResponseParserTest.kt)

What changed:

- AI message generation and regeneration now pass `event.type` into the response parser, so malformed or error AI responses use event-specific fallback copy.
- The classification prompt now asks for `communication_style`, matching the parser field that updates contact writing style.
- Focused regression tests cover prompt/schema alignment and event-specific fallback behavior through parser and service paths.

Why this improves user experience:

- Anniversary and work-anniversary drafts no longer fall back to birthday text when AI fails or returns malformed JSON.
- AI classification can populate the communication style field more reliably, making future drafts less generic.

How user effort is reduced:

- Users should spend less time manually correcting wrong-event fallback messages.
- Users should need fewer manual edits to contact style defaults when classification succeeds.

How user control is preserved:

- Generated drafts remain editable and reviewable.
- Classification results still update ordinary contact fields that users can override.
- Fallback use remains explicit through `isUsingFallback`.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.core.gemini.PromptBuilderTest \
  --tests com.example.core.gemini.ResponseParserTest \
  --tests com.example.core.gemini.AiServiceImplTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Event Identity and Date Integrity

Completed tasks:

- T300: Add manual birthday then discovery duplicate regression coverage.
- T301: Define canonical identity rules for standard contact events.
- T302: Save standard manual contact events with canonical IDs unless the user explicitly allows a duplicate.
- T303: Preserve existing manual/verified events by skipping matching contact-derived discovery events.
- T305: Replace lenient discovery date handling with shared non-lenient date validation.
- T306: Preserve leap-day next-occurrence behavior through the shared event date policy.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/event/EventDatePolicy.kt](core/domain/src/main/kotlin/com/example/domain/event/EventDatePolicy.kt)
- [core/domain/src/main/kotlin/com/example/domain/event/EventIdentityPolicy.kt](core/domain/src/main/kotlin/com/example/domain/event/EventIdentityPolicy.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/SaveManualEventUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/SaveManualEventUseCase.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/DiscoverEventsUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/DiscoverEventsUseCase.kt)
- [app/src/test/java/com/example/domain/usecase/SaveManualEventUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/SaveManualEventUseCaseTest.kt)
- [app/src/test/java/com/example/domain/usecase/DiscoverEventsUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/DiscoverEventsUseCaseTest.kt)

What changed:

- Manual birthday, anniversary, and work-anniversary events attached to a contact now use the same canonical IDs as discovered contact events unless the user explicitly chooses to save a duplicate.
- Event discovery now loads existing active events and skips contact-derived events when a matching manual/imported event already exists.
- Existing contact-derived events are still refreshed during discovery so next occurrence and reminders stay current.
- Event discovery validates imported contact dates with non-lenient date rules before computing next occurrence.

Why this improves user experience:

- Users avoid duplicate birthday or anniversary reminders after adding a manual date and later syncing/discovering contact events.
- Invalid imported dates are ignored instead of silently rolling into the wrong month.

How user effort is reduced:

- Users do not need to manually clean up duplicate reminders or duplicate AI drafts for the same relationship moment.
- Fewer confusing event records appear in Events, Home, Messages, and reminders.

How user control is preserved:

- Explicit duplicate creation is still supported with `allowDuplicate`.
- Existing manual events are preserved instead of overwritten by contact discovery.
- Invalid imported data is skipped rather than guessed.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest \
  --tests com.example.domain.usecase.SaveManualEventUseCaseTest \
  --tests com.example.domain.usecase.DiscoverEventsUseCaseTest \
  --no-configuration-cache
```

Result: passed.

## 2026-06-26 - Automation Dispatch Safety

Completed tasks:

- T100: Add coverage proving future approved messages do not dispatch before `scheduledForMs`.
- T101: Add shared `DispatchEligibilityPolicy`.
- T102: Defer approved messages before schedule.
- T103: Allow approved messages at or after schedule.
- T104: Keep Smart Approve pending messages in review before schedule.
- T105: Allow Smart Approve pending messages at scheduled time.
- T106/T107: Keep VIP approval pending before deadline and expire it after the approval window.
- T108: Keep Always Ask pending until explicit approval.
- T109: Block already-handled dispatch states.
- T110: Update `MessageDispatchWorker` to use the shared policy before sending.
- T111: Add delayed WorkManager request support for exact-alarm fallback.
- T113: Update `DispatchMessageUseCase` to use the shared policy.
- T116/T117: Add regression coverage for no-early-send and due Smart Approve behavior.

Changed files:

- [core/domain/src/main/kotlin/com/example/domain/automation/DispatchEligibilityPolicy.kt](core/domain/src/main/kotlin/com/example/domain/automation/DispatchEligibilityPolicy.kt)
- [core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt](core/domain/src/main/kotlin/com/example/domain/usecase/DispatchMessageUseCase.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorker.kt](core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorker.kt)
- [core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorkRequests.kt](core/data/src/main/kotlin/com/example/core/automation/workers/MessageDispatchWorkRequests.kt)
- [core/data/src/main/kotlin/com/example/core/automation/scheduler/DailyScheduler.kt](core/data/src/main/kotlin/com/example/core/automation/scheduler/DailyScheduler.kt)
- [core/domain/src/test/kotlin/com/example/domain/automation/DispatchEligibilityPolicyTest.kt](core/domain/src/test/kotlin/com/example/domain/automation/DispatchEligibilityPolicyTest.kt)
- [app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt](app/src/test/java/com/example/domain/usecase/DispatchMessageUseCaseTest.kt)
- [app/src/test/java/com/example/core/automation/workers/MessageDispatchWorkerTest.kt](app/src/test/java/com/example/core/automation/workers/MessageDispatchWorkerTest.kt)
- [core/data/src/test/kotlin/com/example/core/automation/workers/MessageDispatchWorkRequestsTest.kt](core/data/src/test/kotlin/com/example/core/automation/workers/MessageDispatchWorkRequestsTest.kt)

What changed:

- Dispatch eligibility is now evaluated by one domain policy before any worker or use-case send.
- `APPROVED` means authorized, not "send immediately"; future approved messages are deferred until `scheduledForMs`.
- Smart Approve pending messages remain reviewable before schedule and can send at scheduled time.
- VIP messages expire after the approval window instead of sending automatically.
- Exact-alarm fallback can now enqueue delayed WorkManager dispatch work instead of immediate work for future sends.

Why this improves user experience:

- Users can trust scheduled messages will not send early when exact alarm permission is missing.
- Messages behave consistently between background automation and explicit dispatch paths.
- Review modes have clearer, safer behavior.

How user effort is reduced:

- Fewer failed or surprising automation outcomes require manual recovery.
- Smart Approve can still reduce babysitting after the review window while preserving review before scheduled time.

How user control is preserved:

- VIP and Always Ask never auto-send.
- Pending Smart Approve messages remain editable/rejectable before schedule.
- Blocked, deferred, expired, and send-now decisions are explicit policy outcomes.

Validation:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew \
  :core:domain:testDebugUnitTest --tests com.example.domain.automation.DispatchEligibilityPolicyTest \
  :core:data:testDebugUnitTest --tests com.example.core.automation.workers.MessageDispatchWorkRequestsTest \
  :app:testDebugUnitTest \
  --tests com.example.domain.usecase.DispatchMessageUseCaseTest \
  --tests com.example.core.automation.workers.MessageDispatchWorkerTest \
  --no-configuration-cache
```

Result: passed.
