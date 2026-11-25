# Backend Architecture

## Current reality (`IR`)

Firebase Functions exports purchase verification plus registration, lease, claim, arm, retry, transfer, reset/release, deletion and scheduled coordination sweepers. Firestore rules isolate server control state and owner backup. Hosting is static/Vite with account deletion/public routes. Source is present; deployment is not verified.

## Required target (`ER`)

Logical services: identity/session, store entitlement, owner backup/restore, privacy/deletion, optional public support, and separate Autopilot control plane. Separate deployment permissions/configuration by capability. Commands must derive UID from validated auth, validate bounded versioned input, use idempotency/operation IDs, make side effects checkpointed/fenced, and return stable typed errors. Schedulers must be idempotent, bounded and identifier-free in routine logs.

No raw recipient, birthday, phone, prompt or message should enter coordination. Autopilot is approved in product scope, but callable integration, environment deployment, identity, and operational ownership remain gated; do not deploy its callable surface or sweepers until those approvals and security evidence are complete (DEC-044).
