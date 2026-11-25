# Accessibility Requirements

**ER/DA:** Target WCAG 2.2 AA for approved user journeys and platform matrix, but do not claim conformance until audited.

- Semantic name, role, value, state and error for every actionable element; meaningful reading/focus order.
- Operate core journey with screen reader, keyboard/external input where supported, and no gesture-only action.
- 320dp/narrow reflow and 200% text without clipped required actions; IME and sticky surfaces preserve reachability.
- Minimum target sizing, visible focus, text/icon contrast, no color-only meaning, useful haptics without dependence.
- Accessible loading, progress, empty, no-result, error, stale, permission denial and recovery states.
- Dialogs/ bottom sheets trap/restore focus correctly; destructive and send actions are unambiguous.
- Alt/semantic descriptions for product meaning, not decorative duplication.
- Public site: keyboard, zoom, landmarks, language metadata, forms/errors and screen-reader labels.
- Release evidence requires automated checks plus manual TalkBack and, if supported, public-site assistive technology/device review.

Current source includes contrast/responsive/selected semantics tests; comprehensive conformance evidence is not present (`UA`).
