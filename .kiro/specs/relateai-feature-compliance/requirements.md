# RelateAI Feature Compliance Requirements

## Goal

Bring the Android app into alignment with `features.md`, with each documented feature fully implemented, accurately classified, tested, documented, and validated on device where possible.

## Requirements

1. The repository must contain a root `features.md` that is treated as the feature source of truth.
2. The app must keep `features.md`, `SSOT_CONSOLIDATED.md`, Kiro steering docs, Jules agent notes, `AUDIT_REPORT.md`, `CHANGELOG.md`, and UI validation docs synchronized after each feature pass.
3. The implementation must avoid local workspace assumptions such as `/workspace` paths and stale module names.
4. Biometric lock must block app access on protected cold start/resume when enabled and must use localized UI text.
5. Message automation must respect:
   - contact custom send time,
   - contact skip-auto-wish,
   - global quiet hours,
   - blackout dates,
   - channel blackout,
   - reminder toggles,
   - event `notifyDaysBefore`.
6. Background contact sync must use the same Google + device merge and event-discovery behavior as foreground sync.
7. Gmail SMTP subjects must describe the actual event type instead of always using a birthday subject.
8. Event reminders must be scheduled, canceled, and rescheduled from active events and user preferences.
9. Unit, Robolectric, Compose/instrumented, lint, build, coverage, and manual device validation must be recorded.
10. Deprecated features F-045 and F-046 must remain documented as deprecated unless explicitly re-scoped.
11. Experimental feature F-047 must remain documented as experimental unless it enters the active Gemini call path.

## Acceptance Criteria

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache` passes.
- `docs/UI_VALIDATION.md` records every primary screen and interactive workflow with pass/fail or blocked evidence.
- New or changed behavior has focused tests.
- Feature status in `features.md` and `SSOT_CONSOLIDATED.md` matches implementation.
- Feature-scoped commits exist for implemented changes.
