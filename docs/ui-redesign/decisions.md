# Architectural & Design Decisions Register

> Historical design decisions are not current implementation or verification claims. The current shell IA and automatic-SMS state are documented in `screen-inventory.md` and the product-blueprint governance registers.

## Decision 1: Adaptation of Modern Analytics Dashboard Paradigm
- **Context**: The master prompt requested transforming the visual sophistication to match a modern analytics dashboard, adapted specifically for a personal AI birthday assistant.
- **Decision**: Adopted a clean, high-density executive layout with top KPI metric strips (e.g. *Today's Celebrations*, *Action Needed*, *Total Network Tracked*), urgent alert callouts with immediate actions (e.g. *Send via WhatsApp* for birthdays within 7 days), and structured cards bounded by subtle 1px borders (`#E2E8F0`).
- **Consequences**: Eliminates childish balloon/confetti clipart, random gradients, and unstructured card lists while keeping celebratory warmth through terracotta (`#A64B2A`) and emerald green (`#0F7B54`) accents.

## Decision 2: Stitch Design System & Color Tokens
- **Context**: Screen designs generated in Google Stitch MCP must map 1:1 to Flutter theme tokens.
- **Decision**: Established `Modern Executive Analytics` in Stitch with uploaded base64 `DESIGN.md`. Mapped directly to Flutter's `AppPalette` theme extension, using `#A64B2A` as primary brand terracotta, `#FFF9F5` warm paper background, `#0F172A` deep slate text, and explicit paired status colors (`onSuccess`, `onWarning`, `onDanger`).
- **Consequences**: `test/ui/contrast_test.dart` passes with >5.7:1 contrast on all surfaces, enforcing strict WCAG 2.1 AA accessibility.

## Decision 3: Preservation of Real Riverpod Streams & SQLite Persistence
- **Context**: UI redesigns frequently risk introducing mock data or breaking database watchers.
- **Decision**: Retained all existing Riverpod reactive stream providers (`birthdaysStreamProvider`, `peopleStreamProvider`, `settingsProvider`) and Drift SQLite queries. No mocks were introduced into production screens.
- **Consequences**: Application remains 100% reactive to midnight refreshes, contact additions, and background sync.

## Decision 4: Android Carrier Compliance & Consent Boundary
- **Context**: Automatic SMS vs WhatsApp delivery channels have distinct permission and Google Play compliance implications.
- **Decision**: Manual SMS/WhatsApp handoff remains user initiated. Automatic SMS is in Android product scope only with explicit per-person opt-in and approval of the exact message. It is currently disabled by Dart/native gates; Play retains manual SMS if policy does not allow automatic sending.
