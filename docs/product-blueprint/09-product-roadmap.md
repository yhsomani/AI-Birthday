# 09 — Dependency-aware product roadmap

No calendar deadlines, staffing assumptions, budget or delivery estimates are set. Phases are ordered by dependency and risk, not by guessed duration.

| Phase | Objective | Main deliverables | Dependencies | Exit criteria |
|---|---|---|---|---|
| P0 — Discovery validation | Confirm product problem, target users, platform and commercial direction | Research plan, interview findings, approved vision, scope and boundaries | Stakeholder access | Product brief approved; assumptions and unknowns owned |
| P1 — Product contract | Resolve contradictions and define core behavior | Approved requirements, feature contracts, user journeys, business rules, glossary | P0 | Every P0 requirement testable and traceable |
| P2 — Domain/data design | Establish canonical ownership and lifecycle | Entity model, state machines, migration strategy, backup/restore contract | P1 | No unresolved critical data-integrity design |
| P3 — UX/design system | Validate end-to-end user workflows | IA, screen specs, component/state inventory, accessibility specification, prototype validation | P1 | Core journeys pass usability review |
| P4 — Architecture/security | Finalize boundaries and external contracts | C4/logical diagrams, API schemas, threat model, privacy inventory, deployment model | P2, P3 | Architecture/security review approved |
| P5 — Core local experience | Deliver reliable remember/plan/prepare manually loop | People, occurrence lifecycle, dashboard, calendar, reminders, manual drafts | P2, P3, P4 | Offline core journey and data integrity gates pass |
| P6 — User-controlled delivery | Deliver truthful handoff and history | WhatsApp/SMS composer/share, confirmation model, history event model | P5 | Handoff cannot be mistaken for send; E2E passes |
| P7 — Subscription-gated AI | Deliver safe AI preparation | Purchase verification, entitlement gate, BYOK/Nano provider routing, prompt safety | P4, P5, P6 | No provider call without entitlement; all AI failure paths preserve draft |
| P8 — Continuity and account | Deliver optional account/backup/data lifecycle | Auth, opt-in backup, restore, export, deletion | P4, P5 | Cross-account security and restore/deletion tests pass |
| P9 — Conditional automation | Decide and validate automatic preparation/SMS/control plane | Consent model, job orchestration, flavor policies, separate control-plane approval | P5–P8 | Separate product/security/legal approval; safe disable/rollback |
| P10 — Production readiness | Prepare controlled release | CI, signing, environment provisioning, legal, monitoring, support, rollback | P5–P9 as applicable | Release gates all pass with evidence |
| P11 — Operate and improve | Sustain reliability and product value | Monitoring review, support, incident response, research, roadmap refresh | P10 | Operational owner and review cadence active |

## Critical path

```text
Product validation
  → approved requirements and business rules
    → canonical data/state model
      → UX and architecture/security approval
        → reliable local core
          → truthful delivery/history
            → subscription-gated AI
              → optional cloud/account continuity
                → production readiness
                  → monitored operation
```

Some streams can run in parallel after requirements stabilize: UX research, threat modeling, CI hardening and provider/device capability validation.

## Release slicing recommendation

**Slice 1 — Trustworthy core:** local people, recurring birthday, dashboard/calendar, manual drafts, reminder controls, export and basic delivery handoff.

**Slice 2 — Paid AI assistance:** server-authoritative subscription, entitlement-first routing, BYOK/Nano provider handling, grounded prompts and robust failure behavior.

**Slice 3 — Continuity:** account, opt-in backup/restore, deletion and recovery.

**Slice 4 — Conditional automation:** only after separate product, consent, platform-policy and operational approval.

This sequencing is a recommendation. It does not imply current implementation is absent or that work must be rebuilt. Existing source must be mapped to approved contracts before deciding whether to retain, repair or replace it.
