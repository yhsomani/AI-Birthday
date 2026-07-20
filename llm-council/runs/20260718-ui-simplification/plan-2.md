# Overview

Simplify production `src/features/live/*` in safety-first slices; keep all native contracts, projections, review handles, revisions, routes, and platform capabilities intact.

# Prioritized plan

1. First slice: update `LiveSettingsScreen.tsx`, `LiveHelpLegalScreen.tsx`, `LiveAppShell.tsx`, `liveResources.ts`, `LiveApp.test.tsx`, and production-smoke navigation. Remove Settings links/queries for Activity, Attention, Diagnostics, readiness, and duplicate inventory; retain account, Privacy, Help, and Android device controls. Move Diagnostics under Help. Acceptance: three tabs only; Home to Activity/Fix issues; Settings to Help to Diagnostics; Settings to Privacy; Android transfer still reaches Automation.
2. Add compact loading and accessible disclosure primitives in `LiveProjectionState.tsx`/`Primitives.tsx`; blocking facts stay expanded, healthy technical metadata may collapse.
3. Add platform-result guards around Automation and policy mutations; reject wrong-platform envelopes before success UI. Add a sequenced `useCompanionStatus.ts` so stale iOS reminder/composer responses cannot overwrite newer verified state.
4. Simplify `LiveHomeScreen.tsx`: one contextual primary action (`Fix issues` > today review > enable/finish setup > manage plan), compact required BA-15 counts, Activity/Pause secondary, healthy service data under “Safety details,” and no permanent Message button.
5. Split `LiveAutomationScreen.tsx` into stable wrapper plus Android/iOS components; place message editing inside this task flow while preserving Attention recovery access.
6. Simplify setup, then People and Privacy: one current setup step; stack-safe Back behavior; progressive disclosure for advanced person actions; categorized privacy operations with unchanged native prepare/confirm review.

# Platform and safety invariants

- Never infer readiness from JS time or missing data; loading/error/unknown disables consequential actions.
- Preserve revisions, exact recipient IDs, explicit confirmations, duplicate fences, blocklists, deletion recovery, external-copy disclosures, and PII-free diagnostics.
- iOS never implies unattended sending or calls Android APIs; MessageUI receives only native proposal/revision/nonce.
- Android never claims carrier delivery and retains test, SIM, scheduler, notification, sender-transfer, and pause gates.
- Fixture UI and immutable production-smoke projections remain non-production evidence; no mutable action may be “validated” through their fail-closed stubs.

# Validation and risks

Run focused Jest/navigation/accessibility/localization contracts after every slice, then `npm run check`, Android native tests, production-smoke rendering, and physical-device release matrices; iOS simulator/physical validation remains required when Xcode is available.

- Self-critique: collapsing healthy Home details may reduce discoverability; keep unsafe/stale facts visible and test at 200% text with older adults.
- Self-critique: splitting the 1,578-line Automation screen adds churn; land response guards and characterization tests first, and revert the extraction independently if behavior changes.
