# RelateAI Implementation Progress

Version: 1.0.0
Date: 2026-06-26
Source backlog: [IMPLEMENTATION_TASKS.md](IMPLEMENTATION_TASKS.md)

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
- Regenerated text is evaluated through the AI auto-send quality gate, updating approval mode and `qualityScore`.
- Channel readiness is recomputed with `AutoSendChannelSelector.selectRoute()`, updating the saved channel and forcing no-route drafts to `ALWAYS_ASK`.
- Stale `editedByUser`, `userEditedText`, and user-approved `APPROVED` status are cleared by default.
- Explicit parameters allow preserving user-edited text or approved status for future workflows that intentionally need that behavior.
- Regenerated auto-schedulable drafts are re-scheduled through `SchedulerService`; no-route drafts are not scheduled.

Why this improves user experience:

- A regenerated generic or fallback draft no longer keeps stale fully automatic eligibility.
- If contact details or channel setup changed since the original draft, regeneration reflects the current delivery reality.
- Users see safer review states after regeneration instead of hidden stale approval/send state.

How user effort is reduced:

- Users do not need to discover later that a regenerated draft failed because route readiness was outdated.
- Lower-quality regenerated drafts are automatically placed back into review, reducing manual audit work.

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
