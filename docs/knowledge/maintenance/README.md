# Maintenance and traceability rules

[Knowledge portal](../README.md) · [Feature registry](../feature-registry.json)

The feature registry is a **living map**, not a substitute for the actual code. This documentation is non-authoritative for runtime claims; the linked current source code and tested runtime behavior govern those claims.

## For each feature change

1. Locate affected stable ID(s) in [feature-registry.json](../feature-registry.json); prefer retaining IDs through renames.
2. Identify changed UI routes, feature contracts, state transitions, data writers/readers, external API integration and tests.
3. Update the registry's `source`, `tests`, `note`, implementation/verification classifications and (if needed) child IDs. Avoid creating overlapping sibling entries.
4. Update only affected Markdown sections in [functional tree](../features/README.md), [journeys](../journeys/README.md) and [architecture](../architecture/README.md).
5. For newly discovered defects, add an evidence-linked entry in [gaps](../gaps/README.md); mark proposal vs confirmed issue explicitly.
6. If implementation behavior diverges from [SSOT.md](../../../SSOT.md), do not quietly rewrite intent: describe mismatch, request decision, update separately if approved.
7. Re-check all relative links, Mermaid diagrams, source references and parent IDs.
8. Verify impacted tests, lint/typecheck and device/emulator scenarios where appropriate. Record exact CI run/commit and environment.
9. Never copy secrets, recipient data, generated binaries, tokens, keys or actual private user content into documentation.
10. Keep archived design/audit plans archival; never automatically merge their historical completion assertions with current source truth.

## Recommended validations

```sh
# From repository root, for Flutter changes:
dart format --output=none --set-exit-if-changed .
flutter analyze
flutter test --exclude-tags golden

# Backend:
(cd backend/functions && npm ci && npm run check)

# Hosting:
(cd backend/hosting && npm ci && npm run check)
```

For the registry: check JSON parsing, unique `id`, existing `parent`, acyclic hierarchy and actual source paths. Optionally add lightweight CI validation without unnecessary runtime dependencies.

## Coverage, evidence and documentation debt

- A feature is **implemented** if the code is located. It becomes **runtime-verified** only when exercised under named conditions; CI success is narrower than production verification.
- Test files referenced in the registry are **locations**, not necessarily passed tests during this documentation session.
- Never force arbitrary feature/micro/nano depth. Split only when the sub-unit makes behavior, dependencies or testing clearer.
- Distinguish backend-only capabilities from Flutter-user-facing behavior; record the integration edge only when source and/or runtime confirms it.
- If a source path or diagram is stale, open a targeted maintenance finding. Do not silently fabricate replacements.

## Current evidence snapshot

Review: 2026-10-14 · Base commit: `75b9d91aadcc1c4136fdcb2c06340d3744bc50a4`. [Observed main CI run](https://github.com/yhsomani/AI-Birthday/actions/runs/38461085640) completed successfully. Source and tests were inspected remotely; local compilation, emulators and physical-device tests were **not** rerun during generation of this knowledge set.
