# Personas and Roles

Demographic traits are intentionally not invented.

| Role | Purpose | Goals / information needs | Owned data/actions | Restrictions |
|---|---|---|---|---|
| Birthday organizer | primary local user | know what is next; add/correct people; prepare/review/hand off | all local domain records; feature preferences; explicit send confirmation | no server-control or entitlement grant |
| Signed-in account owner | optional cloud user | purchase, backup/restore, account status | Firebase account and owner-scoped cloud tree | UID is server-derived; no cross-user access |
| Recipient | subject of stored context | none in core product | no app account; only external delivery endpoint | cannot see/edit app data |
| Platform/store/provider | external actor | permission/capability/verification/transport | OS alarms, purchase truth, AI response, external app | cannot mutate canonical local state without adapter result |
| Support operator | PC | privacy-safe diagnosis/deletion status | minimum identifiers and operational state | no normal access to names/messages/prompts |
| Release/operator | PC/required for production | deploy, observe, incident response, restore | infrastructure/control with least privilege | no client-side entitlement or content access |

Account lifecycle includes local initialization, optional sign-in, entitlement, sign-out/account switch, export, backup/restore, deactivation/deletion requests and support. Local ownership and account switching are unresolved; see DEC-043.
