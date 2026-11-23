# 07 — Security, privacy and threat model

## 1. Security objectives

- Protect birthday, contact, relationship and message data.
- Prevent unauthorized account or cloud-data access.
- Prevent subscription entitlement fraud.
- Prevent credential leakage.
- Preserve user consent and prevent unintended message delivery.
- Make backup, deletion and retention claims accurate.
- Minimize data collection and external disclosure.

## 2. Data classification

| Class | Examples | Handling |
|---|---|---|
| Public | App name, approved public legal/help content | May be public after review |
| Internal operational | App version, operation category, non-sensitive timing | Access-controlled, retention-limited |
| Personal | Person name, birthday, relationship, phone/email, personal notes | Local by default; explicit purpose and user control |
| Sensitive content | Message body, private relationship facts, imported contacts | Never log; transmit only for user-initiated feature with clear disclosure |
| Secret/auth material | Gemini API key, OAuth/Firebase tokens, signing keys, HMAC peppers | OS secret storage or managed secret store; never log, export or sync |
| Security/audit evidence | Entitlement verification state, deletion receipt, control-plane fences | Minimize, scope, integrity protect and apply documented retention |

## 3. Trust boundaries

1. Flutter UI ↔ domain/application services.
2. Flutter ↔ local SQLite.
3. Flutter ↔ OS secure storage.
4. Flutter ↔ Android Kotlin platform bridge.
5. App ↔ Gemini API.
6. App ↔ Google identity and Play Billing.
7. App ↔ Firebase backup and purchase backend.
8. Public Hosting ↔ Auth/App Check/Functions.
9. Autopilot control plane ↔ Firestore/Admin SDK.
10. App ↔ external messaging applications.

Validate all inputs crossing these boundaries. Never trust client-supplied entitlement, user identity, message status or permission claims.

## 4. Threat register

| ID | Threat | Impact | Required controls | Verification |
|---|---|---|---|---|
| SEC-001 | API key or session token leaked through logs, crash reports or backup | Account/API compromise | Secure storage, structured redaction, secret scanning, serialization denylist | Automated log/redaction tests and repository secret scan |
| SEC-002 | Another user reads or modifies cloud records | Personal data exposure | UID-scoped server rules, server-side authorization, emulator tests | Cross-user read/write denial |
| SEC-003 | Client forges Pro entitlement | Unauthorized AI use | Server-side Play verification; no local grant path | Tampered token and offline entitlement tests |
| SEC-004 | BYOK path bypasses entitlement | Product policy violation | Entitlement gate before provider selection and call | Assert provider not invoked while locked |
| SEC-005 | Prompt injection or fabricated personal facts | Misleading/private content | Treat recipient facts as data, bounded prompt, no external retrieval by default, user review | Adversarial prompt suite |
| SEC-006 | Wrong recipient or malformed destination | Privacy/message harm | Explicit recipient display, E.164 validation, final review | Boundary and misdirection tests |
| SEC-007 | App treats WhatsApp launch as sent | False records | Separate handoff and user confirmation states | State-machine tests |
| SEC-008 | Duplicate automated SMS | Unwanted communication | Consent, idempotency, per-recipient approval, stale expiry, bounded retries | Race and retry property tests |
| SEC-009 | Restore overwrites newer local data | Data loss | Manifest validation, version comparison, snapshot generation, rollback | Conflict and corruption tests |
| SEC-010 | Delete/undo leaves orphan or resurrects data | Privacy and integrity failure | Aggregate transaction, tombstones, deletion propagation, undo window | Lifecycle integration tests |
| SEC-011 | Notification permission or alarm state changes silently | Missed reminders | Permission state checks and reconciliation | Device tests across permission revocation/regrant |
| SEC-012 | Control-plane callable replay or race | Unauthorized sender operation | Auth, App Check, replay protection, leases, fences, strict schemas | Emulator adversarial concurrency tests |
| SEC-013 | Hosting deletion page misreports request acceptance as completion | Data-rights failure | Explicit receipt state and independent completion verification | Browser + backend deletion saga tests |
| SEC-014 | Sensitive data retained in backups/logs after deletion | Privacy breach | Retention inventory, tombstones, recursive deletion and evidence | Deletion reconciliation audit |

## 5. Privacy requirements

- Local-first storage must be explained in onboarding and settings.
- Device contacts permission is requested only when user initiates import.
- AI prompt disclosure must identify what recipient facts and draft text are transmitted to the selected provider.
- BYOK key storage and provider billing/quota responsibility must be clear.
- Cloud backup must list exact data categories, account ownership and security limitations.
- Do not claim end-to-end encryption, zero-knowledge storage or encrypted envelope semantics without verified implementation.
- No analytics or crash reporting may collect names, birthdays, phone numbers, private notes, message bodies or raw prompts by default.
- Provide export and deletion paths for local and remote copies.
- Define retention periods for backups, logs, deletion receipts and control-plane records before production.
- Separate sign-out, deleting cloud account, deleting local data and deleting a purchase entitlement.

## 6. Security architecture requirements

- Store secrets using platform-backed secure storage; do not persist credentials in SQLite, SharedPreferences or Firestore.
- Use HTTPS/TLS for network integrations.
- Enforce cloud authorization server-side; client-side visibility is not authorization.
- Validate schemas and size limits at API boundaries.
- Apply rate limits and abuse controls to purchase, AI and control-plane operations.
- Use least-privilege Firebase service accounts and separate dev/staging/prod projects.
- Protect signing keys and peppers through managed secrets; rotate using a documented procedure.
- Redact structured logs by field classification, not fragile string matching alone.
- Maintain dependency and vulnerability scanning with remediation ownership.
- Review Android manifest permissions by distribution flavor and actual feature need.

## 7. Consent matrix

| Capability | Consent/permission | Revocation behavior |
|---|---|---|
| Manual person entry | User action | User can edit/delete |
| Device contacts import | Android contacts permission + user action | Stop future reads; imported copies remain until user deletes them |
| Notifications | OS notification permission + in-app preference | Cancel/stop schedules and explain status |
| Exact alarms | OS special access where required | Reconcile and show reminders may be delayed |
| AI generation | Active subscription + user request; BYOK credential where selected | Stop new requests; preserve drafts; allow credential removal |
| Cloud backup | Explicit opt-in + authenticated account | Stop uploads; explain remote-copy deletion choice |
| WhatsApp/SMS/share | Explicit user handoff | No background send after revocation |
| Personal automatic SMS | Separate explicit feature and recipient consent | Cancel pending alarms and revoke approval |
| Account deletion | Recent authentication and explicit request | Fail closed and report exact progress |

## 8. Security release gates

No production release until:
- threat model reviewed;
- permission manifest reviewed per flavor;
- Firebase rules and callable authorization tests pass;
- secrets scan and dependency audit pass;
- purchase verification is exercised in the supported store environment;
- backup/restore and deletion are tested end to end;
- logging redaction is tested with realistic payloads;
- privacy policy matches actual data flows;
- external AI and messaging disclosures are approved;
- control-plane and Hosting deployment gates are separately satisfied.
