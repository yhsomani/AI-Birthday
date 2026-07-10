# RelateAI Design System

Last reviewed: 2026-07-10

RelateAI is an operational relationship assistant. The UI should feel calm, direct, and efficient: dense enough for repeated work, clear enough for new users, and careful around automation, permissions, and personal data.

## Code Source

- Theme tokens: `src/ui/theme.ts`
- App shell navigation and screen composition: `src/App.tsx`
- Localization contracts: `src/i18n/i18n.ts` and `src/ui/localizationContract.test.ts`
- Accessibility and primary interaction contracts: `src/ui/accessibilityContract.test.ts` and `src/ui/primaryInteractionContract.test.ts`

## Theme Mode Policy

- Supported production mode: light neutral theme with semantic status colors.
- `src/ui/theme.ts` owns the current RN color and spacing tokens. New RN UI should reuse those tokens before adding screen-local values.
- App-shell, card, input, status, warning, danger, success, and muted text colors should come from the RN token layer so future theme modes can replace values centrally.
- Future dark/dynamic support must first implement alternate RN token schemes, run contrast review, and add compact-phone, typical-phone, large-font, Hindi, and Hinglish release-device evidence before release.

## Tokens

Spacing uses a 4 dp grid:

| Token | Value | Use |
| --- | ---: | --- |
| `RelateSpacing.xxs` | 2 dp | Micro alignment and small status dots. |
| `RelateSpacing.xs` | 4 dp | Icon/text gaps and compact vertical rhythm. |
| `RelateSpacing.sm` | 8 dp | Standard inline spacing and compact list gaps. |
| `RelateSpacing.md` | 12 dp | Compact card padding and banner rows. |
| `RelateSpacing.lg` | 16 dp | Screen horizontal padding and standard content padding. |
| `RelateSpacing.xl` | 24 dp | Section separation. |
| `RelateSpacing.xxl` | 32 dp | Large screen separation. |
| `RelateSpacing.xxxl` | 48 dp | Hero-to-progress spacing and sparse startup/setup rhythm. |

Radius:

| Token | Value | Use |
| --- | ---: | --- |
| `RelateRadius.xs` | 2 dp | Progress tracks and tiny markers. |
| `RelateRadius.sm` | 4 dp | Badges and compact status surfaces. |
| `RelateRadius.md` | 6 dp | Small labels and mid-density controls. |
| `RelateRadius.control` | 8 dp | Buttons, text fields, skeletons, banners. |
| `RelateRadius.card` | 8 dp | Cards and repeated list items. |
| `RelateRadius.pill` | 20 dp | Filter chips and segmented choice pills. |

Sizing:

| Token | Value | Use |
| --- | ---: | --- |
| `RelateSize.minTouchTarget` | 48 dp | Minimum tappable area target. |
| `RelateSize.primaryButtonHeight` | 52 dp | Primary full-width commands. |
| `RelateSize.compactButtonHeight` | 36 dp | Inline list actions. |
| `RelateSize.chipMinHeight` | 24 dp | Metadata chips and compact labels. |
| `RelateSize.outlineStroke` | 1 dp | Outlined button and chip borders. |
| `RelateSize.statusDot` | 6 dp | Compact active/inactive markers. |
| `RelateSize.iconSm` | 18 dp | Feedback/status icons. |
| `RelateSize.iconMd` | 22 dp | Banner icons. |
| `RelateSize.iconLg` | 24 dp | Navigation/stat icons. |
| `RelateSize.progressIndicator` | 32 dp | Standard blocking progress indicator size. |
| `RelateSize.setupStepIndex` | 34 dp | Number badges in onboarding/setup checklist rows. |
| `RelateSize.chartBarHeight` | 20 dp | Compact bar-chart tracks in analytics/reporting surfaces. |
| `RelateSize.avatar` | 44 dp | Contact avatars. |
| `RelateSize.heroIcon` | 64 dp | Single symbolic hero icons on setup/auth-style screens. |
| `RelateSize.profileAvatar` | 96 dp | Large profile avatars on detail screens. |
| `RelateSize.loadingPanelHeight` | 200 dp | Reserved loading space for dashboard panels that would otherwise shift content. |
| `RelateSize.actionCardMinHeight` | 148 dp | Responsive command cards with icon, title, and supporting copy. |
| `RelateSize.actionGridBreakpoint` | 520 dp | Breakpoint for switching paired action cards from stacked to side-by-side. |
| `RelateSize.progressTrack` | 4 dp | Health/progress bars. |
| `RelateSize.progressStroke` | 3 dp | Circular progress indicators inside compact action surfaces. |
| `RelateSize.indicatorDot` | 10 dp | Default health/status indicator dot. |
| `RelateSize.indicatorDotLarge` | 14 dp | Larger profile/header health indicator dot. |
| `RelateSize.dialogContentMaxHeight` | 460 dp | Scrollable dense forms inside dialogs. |

Elevation:

| Token | Value | Use |
| --- | ---: | --- |
| `RelateElevation.flat` | 0 dp | Flat app shell surfaces such as the bottom navigation bar. |
| `RelateElevation.card` | 2 dp | Subtle separation for focused cards that need lift from the surrounding surface. |
| `RelateElevation.appBar` | 3 dp | Subtle top app bar surface separation. |

Alpha:

| Token | Value | Use |
| --- | ---: | --- |
| `RelateAlpha.disabled` | 0.4 | Disabled icons and supporting UI. |
| `RelateAlpha.divider` | 0.12 | Low-emphasis dividers inside grouped settings, lists, and dense panels. |
| `RelateAlpha.muted` | 0.7 | Low-priority startup or explanatory text that should stay legible. |
| `RelateAlpha.outline` | 0.5 | Subdued action borders and outlines. |
| `RelateAlpha.shimmerHigh` | 0.6 | Leading/trailing shimmer band opacity. |
| `RelateAlpha.shimmerLow` | 0.2 | Center shimmer band opacity. |
| `RelateAlpha.subtle` | 0.82 | Secondary text or supporting UI that should remain readable. |
| `RelateAlpha.feedbackContainer` | 0.15 | Status, warning, and feedback containers. |
| `RelateAlpha.fieldContainer` | 0.22 | Input or badge containers. |

Layout fractions:

| Token | Value | Use |
| --- | ---: | --- |
| `RelateFraction.strengthWeak` | 0.25 | Weak password-strength progress. |
| `RelateFraction.strengthFair` | 0.5 | Fair password-strength progress. |
| `RelateFraction.strengthStrong` | 0.75 | Strong password-strength progress. |
| `RelateFraction.strengthFull` | 1.0 | Complete progress for very strong password strength. |
| `RelateFraction.healthStrongThreshold` | 0.7 | Health indicator threshold for healthy/strong state. |
| `RelateFraction.healthAttentionThreshold` | 0.4 | Health indicator threshold for attention/warning state. |
| `RelateFraction.metadataLabel` | 0.4 | Compact label column in summary/metadata rows. |
| `RelateFraction.metadataValue` | 0.6 | Compact value column in summary/metadata rows. |
| `RelateFraction.skeletonTitle` | 0.5 | Width for title-like list skeleton placeholders. |
| `RelateFraction.skeletonSubtitle` | 0.3 | Width for subtitle-like list skeleton placeholders. |

Color:

- Neutral surfaces should carry most of the UI. Accent color should identify status, route, or priority, not decorate every section.
- Purple remains the current primary accent, but redesign work should reduce one-note purple/slate dominance by using semantic green, amber, red, cyan, and rose only where they clarify state.
- Use RN token colors for reusable card primitives that need the RelateAI card treatment.
- Use semantic success, warning, danger, info, muted, and border colors only where they clarify state. Error colors must not be decorative.
- Provider, channel, and status colors must be tokenized before broad reuse.
- Error/success/warning colors must not be used as decorative accents.

Typography:

- Use the RN app typography styles from `src/App.tsx` until reusable text tokens are extracted.
- Do not scale font size with viewport width.
- Letter spacing remains 0 unless Material defaults require otherwise.
- Use hero-scale type only for true top-level screens; cards and panels use `titleMedium`, `titleSmall`, `bodyMedium`, and `bodySmall`.

## Components

App shell:

- Owns routed content, primary bottom navigation, the core permission rationale dialog, and the biometric lock gate.
- Use the RN background/surface tokens for shell surfaces.
- Bottom navigation remains limited to Home, Events, Messages, Contacts, and More; secondary tools such as Analytics, Backup/Restore, Setup Check, Style Coach, Activity History, and Settings stay under More or contextual actions.
- Biometric-lock loading uses stable reserved space and tokenized spacing. Authentication prompt behavior remains owned by the RN native bridge and app shell.

`RelateScreen`:

- Use for standard full-screen pages with a title, optional subtitle, optional navigation icon, and a content column.
- Do not put a `RelateGlassCard` around an entire screen.
- Content should own its loading/empty/error state below the top bar.

`RelateTopBar`:

- Use one heading per screen.
- Navigation icons need content descriptions.
- Keep action slots short; move multi-action menus into screen content or a menu.

`RelateGlassCard`:

- Use for repeated items, grouped settings, status panels, and compact summaries.
- Do not nest cards.
- Keep card radius at `RelateRadius.card`.
- Card container and border colors come from RN theme tokens so future theme modes can replace those values centrally.

`RelatePrimaryButton`:

- Use for the one main command in a section.
- Disable when the action cannot safely run; pair disabled states with visible reason text.
- Keep the tokenized minimum height, but allow labels to wrap and grow vertically at large font scale or in longer localizations.
- Use icon+text buttons for specific tools when the icon improves recognition.

`RelateStatusBanner` and `AdaptiveFeedbackBanner`:

- Use for setup blockers, warnings, success, and transient feedback.
- Messages must be redacted and actionable.
- Live region feedback should stay concise.
- Shared warning/error/success/info surfaces use RN semantic tokens so future theme modes can replace palette values centrally.

`StatCard`:

- Use for dashboard/analytics summaries.
- Keep labels short and values scan-friendly.
- Do not use stat cards as navigation tiles unless the whole card is explicitly tappable and labeled.

`FilterChip`:

- Use for low-risk filtering and segmented modes.
- Filter chips must not own business logic; they call existing reducer or screen-state filter handlers.

`ShimmerItem`:

- Use where skeleton shape is known.
- Prefer skeletons for list/card loading and progress indicators for one-off blocking work.
- Shimmer colors and alpha values must use theme colors and `RelateAlpha` tokens, not raw grays.

Pager-backed queue/list content:

- Populated list pages should fill the pager page and top-align rows directly below their filters.
- Centered content is reserved for intentional loading or empty states.
- Keep pager alignment presentation-only; tabs, filters, selection, and reducer/screen state remain owned by the screen.
- Repeated queue/list presentation such as pager shells, list shells, list/card composition, tab rows, avatars, selection controls, search/filter/sort controls, selectable card headers, sent-card headers/bodies, pending-card metadata/bodies, review-card bodies/status icons, metadata badges, card text/date labels, action clusters and card action rows, bulk-selection action bars, reject-dialog presentation, recovery/support panels, readiness badges, and approval-status copy should be extracted into screen-local helpers first.
- Repeated detail-screen presentation such as blocking loading states, profile headers, relationship metadata, health indicators, section shells, contextual action clusters, and preference-form controls should also start as screen-local helpers. Promote to shared `src/ui` components only after multiple screens need the same API and behavior.

`RelateAvatar` and `HealthIndicatorDot`:

- Use tokenized `Dp` sizes from `RelateSize`.
- Health color thresholds are presentation thresholds in `RelateFraction`; domain health scoring remains outside shared UI.

## Navigation Patterns

- Bottom navigation: Home, Events, Messages, Contacts, More.
- Secondary screens are launched from contextual actions and should provide an obvious back path.
- Dashboard summaries link to feature owners; they do not duplicate full feature controls.
- Settings links to AI Doctor, Backup/Restore, and Activity History instead of embedding those workflows.
- Messages owns queue management; Wish Preview owns single-draft review.

## Interaction Patterns

- Checkboxes are for consent and binary acknowledgement.
- Switches/toggles are for persistent on/off preferences.
- Chips/tabs are for filters and modes.
- Menus are for compact option sets.
- Buttons execute commands.
- Use scaffold-reserved bottom action bars for persistent screen commands that must remain available while content scrolls; avoid overlay FABs when they hide list or card content.
- Destructive actions require confirmation and route through the existing business orchestrator.
- Automation actions must expose setup blockers before dispatch.

## Accessibility Rules

- Keep touch targets at 48 dp where practical.
- Icons with no visible text need content descriptions.
- Purely decorative icons use null content descriptions.
- Avoid truncating essential action text. If a label cannot fit, wrap or move the action.
- Validate at large font scale before closing a screen redesign.
- Avoid communicating state by color alone.

## Implementation Rules

- UI state comes from the React reducer/domain selectors; shared components must not own business decisions.
- New reusable RN UI belongs in `src/ui` only after at least two screens need it or it removes meaningful duplication.
- Screen-local helper components are acceptable while behavior is still changing.
- Convert hard-coded screen dimensions to tokens incrementally with focused compile/test validation.
- Do not change route names, reducer action contracts, dispatch policies, or persistence behavior during visual-only redesign work.
