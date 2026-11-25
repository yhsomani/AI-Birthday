# Interaction Specifications

- Forms are grouped by required identity/date and optional context/delivery. Preserve input on validation/storage failure.
- Validation happens on blur/submit with field-level accessible messages; server/platform errors use typed, recoverable states.
- Destructive actions explain exact scope, linked data and recoverability. Undo is shown only when durable for the stated scope/duration.
- Handoffs require a final content/recipient/channel review and no false send state.
- Loading: indicate operation and progress bounds. Empty/no-match/error/stale/offline/partial states are distinct.
- AI: separate entitlement locked, capability unavailable, credential invalid/missing, timeout, quota and provider error.
- Search/filter/sort/pagination: canonical local indexes, explicit filters/clear/no-match state; no private search telemetry.
- Notifications: milestone-based, permission-aware, quiet-hours aware and tappable to exact entity.
- Bulk import: local parse, bounded file/row count, formula-safe, preview, per-row status, duplicate review and reconciliation counts.
