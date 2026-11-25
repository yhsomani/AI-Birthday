# Testing Strategy

## Layers

Static analysis/formatting; domain/application unit; component/widget and semantics; repository/database migration/invariants; provider/Callable contract; API/Firebase emulator; Android platform bridge; end-to-end opaque journeys; security/privacy/abuse; performance/load/stress; accessibility; compatibility/device/OS; reboot/process death; backup/restore; deployment/smoke; rollback.

Current repository evidence contains all major source-level families, but it does not prove exact-commit/device/deployment runtime. Each critical UJ includes normal, boundary, negative, permission, dependency-failure, timeout, concurrency, recovery, security and accessibility cases. Test doubles must not become product fallback behavior.

## Focused gaps

- Cross-timezone/date and schema-vN→vN migration fixtures using observed exception classes.
- Split-write/import bypass, duplicate/orphan/FK/uniqueness and aggregate transaction tests.
- Backup partial/corrupt/oversized/collision/prune/delete/restore race tests.
- Adversarial prompt delimiters, fabricated facts, long inputs and provider output validation.
- TalkBack/manual focus and public-site keyboard/zoom/language review.
- Real AlarmManager/reboot/App Check/Nano/Contacts/WhatsApp and signed Play purchase/restore.
- Load/capacity with approved volume; stuck sweeper/queue/job; backup/deletion concurrency.

Detailed test catalog: [definitive/07](../../definitive/07-quality-testing.md).
