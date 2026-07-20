# Overview

Simplify presentation without changing native state machines, safety gates, permissions, deletion semantics, or Android/iOS truth. Keep exactly Home, People, and Settings tabs; move advanced detail behind task-specific routes and disclosures.

# Prioritized plan

1. **First slice: navigation, Home, and Settings hierarchy**
   - Update `src/features/live/LiveAppShell.tsx`, `LiveHomeScreen.tsx`, and `LiveSettingsScreen.tsx`.
   - Home shows readiness, next greeting, a two-line plan summary, blockers, and one state-derived primary action.
   - Put healthy heartbeat, coordination, sender epoch, and companion details behind “Status details”; blockers remain expanded.
   - Settings becomes grouped rows: Birthday message, Schedule/reminders, conditional Android phone/SIM, Account & contacts, Notifications, Privacy & data, Help/About.
   - Activity remains under Home; Attention becomes “Fix issues”; Diagnostics moves to Help/Troubleshooting.
   - Acceptance: every existing destination remains reachable, each state has at most one filled primary action, raw reason codes never appear outside Diagnostics, and platform-specific copy remains truthful.
2. **Design-system and focus foundation**
   - Extend `src/design-system/components/Primitives.tsx`, `RouteAccessibilityFocus.tsx`, `AppText.tsx`, and `src/design-system/tokens/theme.ts`.
   - Add shared EmptyState, InlineIssue, review summary, destructive confirmation, filter, loading, and status-detail patterns.
   - Focus the visible screen heading instead of a hidden duplicate; add visible keyboard focus and distinct pressed/disabled states.
3. **Unify setup**
   - Consolidate `LiveSetupScreen.tsx` and `LiveProductSetupJourney.tsx` into four progressive stages while retaining all native sub-states.
   - Show only current requirements, one primary action, and collapsed completed steps; keep permissions immediately contextual.
   - Preserve Android test/activation and iOS reminder/composer boundaries exactly.
4. **Reduce dense task screens**
   - Make `LivePeopleScreen.tsx` a virtualized/searchable list with accessible filters; move bulk approval to its own review.
   - Make `LivePersonDetailScreen.tsx` state-driven; place exclude/block/pause controls under clearly named secondary choices.
   - Progressively disclose built-in, custom, and AI message paths in `LiveMessageScreen.tsx`.
   - Split `LiveAutomationScreen.tsx` into overview, schedule, Android test/activation, and iOS reminder/composer tasks.
5. **Simplify recovery and privacy**
   - Use compact Activity rows with one recovery action.
   - Replace gates/codes in Attention with plain issue and next-step copy.
   - Group Privacy operations; retain every `PrivacyActionKind` and exact confirmation consequence.
6. **Localization and evidence**
   - Update `src/localization/liveResources.ts`, `productionResources.ts`, `formatLive.ts`, and `bidi.ts` in the same commits as UI changes.
   - Feed production live components from the isolated fixture port; retire duplicate fixture screens only after parity.
   - Update `tools/live-navigation-contract.test.mjs`, accessibility tests, Maestro flows, and store evidence last.

# Accessibility and localization acceptance criteria

- All controls are at least 48 by 48 dp; content remains usable at 200% text without clipping, overlap, hidden actions, or horizontal consent scrolling.
- TalkBack and VoiceOver announce visible headings, role, state, errors, and status changes once, in logical focus order.
- Body text meets 4.5:1 contrast; focus and non-text states meet 3:1 in light, dark, and high-contrast modes.
- Status never relies on color; reduced motion removes nonessential animation.
- English/Hindi keys and plurals remain exactly paired and human-reviewed; unsupported locales safely fall back to English.
- Names are never translated or reordered; interpolated names, phone numbers, dates, and times receive bidi isolation.
- Render Android and iOS matrices at 100%/200%, light/dark/high contrast, EN/HI, pseudo-RTL, and reduced motion using synthetic data.

# Top risks

- Collapsing operational detail could hide a safety blocker; keep blockers expanded and status detail one action away.
- Route splitting could break deep links or stale-review handling; retain aliases and characterize native call sequences before refactoring.
- Fixture consolidation could accidentally invoke privileged native behavior; keep the fixture port isolated and assert production-boundary separation.
- Self-critique: Density findings are source-based; conditional branches may not coexist in real projections.
- Self-critique: No live render was available during this independent review, so visual hypotheses require device validation.
