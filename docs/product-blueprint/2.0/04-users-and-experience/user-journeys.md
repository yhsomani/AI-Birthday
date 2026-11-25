# User Journeys

Detailed journey contracts: [definitive UX](../../definitive/06-user-experience.md).

| UJ | Objective/role | Core path | Fail/recovery/evidence |
|---|---|---|---|
| UJ-001 | First value / organizer | initialize → optional onboarding → dashboard → add Person | DB/migration failure is blocked/recoverable, no fallback memory silently replacing data |
| UJ-002 | Create/correct Person | entry/import → validate/preview → transaction → occurrence | invalid leap/timezone/duplicate partial candidates preserved; no partial aggregate |
| UJ-003 | Plan upcoming | dashboard/calendar/search → Studio | same canonical occurrence; stale/deleted/orphan is explicit |
| UJ-004 | Reminder | configure milestones/quiet hours → permission → schedule → notification | denial/revocation/reboot/timezone/change/device failure with manual action |
| UJ-005 | Prepare message | manual editor or AI request → revise → autosave | entitlement/provider/timeout/edit-race failure preserves latest manual draft |
| UJ-006 | Deliver/confirm | review revision → handoff → external app → return/confirm | duplicate handoff, no return, not sent, wrong channel, stale revision; completion only by explicit rule |
| UJ-007 | Identity/subscription | sign in → purchase/restore → verify server entitlement → use AI | token/expiry/network/revoke/account-switch handled without client grant |
| UJ-008 | Backup/restore | consent/disclosure → generation → verify → restore staged | corrupt/stale/duplicate/deleting/concurrent writers fail safely |
| UJ-009 | Export/delete | choose scope → preview → create/confirm → receipt | must distinguish local, cloud and provider scopes; current implementation incomplete |
| UJ-010 | Interrupted work | app/job handoff pause/resume | recover persisted jobs/drafts/occurrence and avoid duplicate external effect |

Every UJ requires normal, negative, permission, dependency failure, timeout, concurrency, recovery, security and accessibility scenarios.
