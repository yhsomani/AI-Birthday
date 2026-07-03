# ADR 0001: Domain Purity and Module Boundaries

Date: 2026-06-27

Status: Accepted

Implementation update 2026-07-03: the Room/Paging/entity portion of this ADR is now implemented in the working tree. Room entities, DAO projections, and persistence-backed mappers live in `:core:data`; `:core:domain` no longer depends on Room/Paging and its main source imports no Android, AndroidX, Room entity, or DAO types. The remaining follow-up is build-level conversion of `:core:domain` from Android library to JVM module.

## Context

The Gradle graph now has five modules: `:app`, `:core:model`, `:core:domain`, `:core:data`, and `:core:ui`. Earlier versions of `core:domain` depended on Room/Paging and owned Room entity classes under `core/domain/src/main/kotlin/com/example/core/db/entities`; those persistence types now live in `:core:data`.

Repository evidence:

- `core/domain/build.gradle.kts` still applies the Android library plugin, but no longer declares Room/Paging dependencies.
- `core/data/src/main/kotlin/com/example/core/db/entities/*Entity.kt` contains Room entities.
- Domain repository/service contracts now avoid Room entities on the audited contact, event, message, dispatch, backup, and pure-record paths.
- `PLAN.md` Sections 3.1, 8.2, and 9 require a pure domain layer and a separate target model layer.

This ADR remains relevant because `:core:domain` should become an explicit JVM module. The source/API persistence leakage described above has been removed, but the build module still carries Android-library configuration.

## Decision

The rebuild will introduce pure Kotlin domain boundaries:

- `:core:model` owns pure value objects and aggregate data classes.
- `:core:domain` owns use cases, policies, ports, and domain services.
- `:core:domain` may depend only on `:core:model` and small Kotlin-only common utilities.
- Room entities, DAOs, migrations, and SQLCipher setup stay outside domain in the data/database layer.
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
- Domain unit tests run as pure JVM tests after `:core:domain` is converted from Android library to JVM module.
- Repository tests prove mapper parity for migrated aggregates.
- `PLAN.md` Phase 1 exit criteria are satisfied.
