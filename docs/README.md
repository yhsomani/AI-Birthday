# RelateAI Documentation

`SSOT.md` at the repository root records historical Android project scope and risks. `docs/feature-fssot.md` is the source of truth for ideal feature behavior and expected user experience. `docs/feature-roadmap-analysis.md` records product-prioritization recommendations. The active replacement app entrypoint is the React Native app at `src/App.tsx`.

Keep supporting docs narrow:

- `architecture/` records ADRs for accepted architecture decisions.
- `feature-fssot.md` records ideal feature behavior without implementation details.
- `feature-roadmap-analysis.md` records product-prioritization recommendations based on the feature FSSOT.
- `react-native-migration-status.md` records current RN replacement progress and remaining parity gaps.
- `design/` records design-system details that are too detailed for the SSOT.
- `operations/` records release checklist requirements.
- `security/` records privacy and permission release evidence.
- The repository root `README.md` explains how to run the React Native app.
- Test and screenshot release procedures live in `operations/release-checklist.md`; detailed behavior belongs in source tests and CI.

Historical progress logs and older audit reports should stay outside the active repository unless they are needed for release evidence.
