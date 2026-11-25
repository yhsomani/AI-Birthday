# Frontend Architecture

Retain Flutter/Material 3, Riverpod, go_router, Drift, secure storage and platform interfaces (`ER`, current fit is verified). Presentation renders view state only: `Loading/Content/Empty/Failure/Stale/Unavailable/Locked`. Domain/date/entitlement/delivery invariants belong below presentation. Use a single use-case boundary for People/import/birthday and one draft/revision service.

- Routing: entity ownership, not-found, unknown capability, unsaved-edit and back-stack contracts.
- State: immutable state; no authoritative durable state solely in widgets.
- Data: repository streams/queries with explicit stale/cached/error and cancellation.
- Forms: shared validators, domain commands, field errors, dirty tracking and recovery.
- Accessibility: Semantics/focus/large-text/IME/reduced motion; tested per journey.
- Performance: avoid N+1, measure startup/query/render and large-list patterns; no invented thresholds.
- Observability: operation ID and typed failure, never prompt/fact/name/number/message/token.
- Testing: domain/application unit, widget, route, repository, platform contract and opaque E2E boundaries.
