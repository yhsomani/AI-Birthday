# ADR 0001: Domain Purity and Module Boundaries

Date: 2026-06-27

Status: Accepted

## Context

The current Gradle graph has four modules: `:app`, `:core:domain`, `:core:data`, and `:core:ui`. `PLAN.md` documents that `core:domain` currently depends on Room and Paging and owns Room entity classes under `core/domain/src/main/kotlin/com/example/core/db/entities`.

Repository evidence:

- `core/domain/build.gradle.kts` declares Android/Room/Paging-facing dependencies.
- `core/domain/src/main/kotlin/com/example/core/db/entities/*Entity.kt` contains Room entities.
- Several domain use cases still accept or return Room entities while extraction is in progress. Completed 2026-06-27: `DispatchEligibilityPolicy` now evaluates the pure `MessageDraft` model instead of `PendingMessageEntity`, event identity/conflict policies now evaluate the pure `Occasion` model instead of `EventEntity`, and `RevivalCadencePolicy` now evaluates pure contact/message models instead of Room contact/pending-message entities.
- `PLAN.md` Sections 3.1, 8.2, and 9 require a pure domain layer and a separate target model layer.

This makes core business rules harder to test without Android persistence concerns and allows storage details to leak across feature, worker, and UI boundaries.

## Decision

The rebuild will introduce pure Kotlin domain boundaries:

- `:core:model` owns pure value objects and aggregate data classes.
- `:core:domain` owns use cases, policies, ports, and domain services.
- `:core:domain` may depend only on `:core:model` and small Kotlin-only common utilities.
- Room entities, DAOs, migrations, and SQLCipher setup move to `:core:database`.
- Provider SDKs, Android framework types, network clients, and AI adapters stay outside domain.
- Repository implementations map between database/network DTOs and pure domain models at the data boundary.

Forbidden dependencies for `:core:domain`:

- Android `Context`.
- Room annotations, Room runtime, SQLCipher, SQLite, or Paging runtime.
- Compose/UI strings/resources.
- Firebase, Google People API, Gemini, JavaMail, SMS, WhatsApp, or WorkManager SDK types.

## Consequences

Positive:

- Domain behavior becomes JVM-testable without Android database setup.
- Storage migrations can evolve without changing public domain APIs.
- Workers and ViewModels must depend on use cases instead of DAOs.
- The rebuild gets a clearer path for feature module extraction.

Costs:

- Existing call sites must be migrated through adapters.
- Tests using `*Entity` types as domain fixtures must be updated or given mapper helpers.
- The transition should be staged to avoid broad behavior changes in automation and backup flows.

## Implementation Rules

1. Add `:core:model` before moving production behavior.
2. Introduce pure models beside existing entities first.
3. Add mapper tests at repository/database boundaries.
4. Move one aggregate at a time: contact, occasion, message, dispatch, audit.
5. Add a build or test guard that fails when `:core:domain` depends on Room, Android framework APIs, or provider SDKs.

## Verification

The decision is implemented when:

- `:core:domain` builds without Room, Paging, Android framework, or provider SDK dependencies.
- Domain unit tests run as pure JVM tests.
- Repository tests prove mapper parity for migrated aggregates.
- `PLAN.md` Phase 1 exit criteria are satisfied.
