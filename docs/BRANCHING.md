# RelateAI Branching Strategy

## Branches
- `main` — always production-ready (never push directly)
- `phase/N-*` — one branch per phase; merged to main after Phase checkpoint passes
- `fix/ID-*` — one branch per specific bug fix (e.g., fix/SEC-CRIT-01)
- `feature/*` — one branch per UI screen or feature

## Rules
1. Never commit secrets (.env, local.properties, keystore files)
2. Always run ./gradlew test before merging to main
3. All phase branches must pass their checkpoint task before merge
4. Create PR with description of changes for each merge to main
