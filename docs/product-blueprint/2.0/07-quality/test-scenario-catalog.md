# Test Scenario Catalog

| TC | Scenario | Required expected outcome | Maps |
|---|---|---|---|
| TC-001 | valid Person/import save and crash between writes | one complete aggregate or zero; no orphan occurrence | FR-002, BR, DEC-041 |
| TC-002 | duplicate import, malformed timezone/phone, unresolved Feb 29 | preview/repair/reject without silent coercion | FR-002 |
| TC-003 | same Person across recipient/device zones and DST | same resolved local calendar date/occurrence | DOM-03, RSK-006 |
| TC-004 | AI free/expired/invalid entitlement with BYOK/Nano available | zero provider calls, manual draft remains | FR-005 |
| TC-005 | AI timeout/quota + user edit while in flight | typed failure/retry; stale result cannot replace edit | FR-004/005 |
| TC-006 | double tap/return/not send/wrong channel during handoff | at most one handoff; truthful event; no inferred send | FR-006 |
| TC-007 | corrupt/partial/stale backup and concurrent deletion/restore | no partial publication/restoration/resurrection; receipt truthful | FR-008, RSK-001/002 |
| TC-008 | prompt field closes delimiter/injects instruction/oversize | treated as data, bounded; no fabricated facts | RSK-007 |
| TC-009 | account switch/shared device | explicit ownership/export/wipe choice; no silent cross-account upload | DEC-043 |
| TC-010 | process death/reboot/retry at each side effect | durable recovery, no duplicate external action | FR-010 |
| TC-011 | 320dp, 200% text, TalkBack focus/dialog/error | core journey reachable and understandable | FR-009 |
| TC-012 | production release with blocked flag/capability mismatch | build/deploy fails; no hidden auto-SMS/control surface | DEC-044/045 |
