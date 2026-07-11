# RelateAI Product Reset Assessment

Date: 2026-07-11

Status: complete assessment set; validated against the repository and the deliverable contract

## Executive Finding

RelateAI has strong technical safety work but no evidence that its current breadth is a viable product. The implementation exposes 141 strict commands across 28 previously documented feature areas, yet ordinary users currently receive only a JSON test console. Production provider sessions, cloud identity/sync, monetization, product telemetry, public policy/support operations, signed-device evidence, and a finished mobile experience are absent.

The project should not continue feature-by-feature toward the previous scope. It should reset around a narrow, testable outcome: **remember an important relationship moment and prepare a genuine, reviewed action quickly**. The end-user shell, information architecture, onboarding, data vocabulary, and use-case layer should be rebuilt. Proven safety policies may be reused selectively.

No current feature receives credit for business value merely because it is implemented or tested.

## Evidence Baseline

The assessment used:

- the active React Native source, manifests, adapters, tests, and release gates;
- the 141-command catalog in `src/application/commandCatalog.ts`;
- the previous 28-area `docs/feature-fssot.md`;
- the previous prioritization in `docs/feature-roadmap-analysis.md`;
- current implementation/release status and ADRs;
- repository searches for payments, product analytics, support, privacy policy, and production provider composition;
- current official competitor/product pages and platform policies.

Evidence proves implementation behavior, not customer demand. The repository contains no validated persona research, market sizing, pricing evidence, activation/retention cohorts, production usage, support tickets, store reviews, or willingness-to-pay data. Business scores are therefore explicit hypotheses and should be revised after discovery.

Relevant external reality checks:

- [Dex](https://getdex.com/docs/dex-core) and [Monica](https://www.monicahq.com/features) already cover broad personal-CRM reminders, contact context, timelines, and follow-ups.
- [Apple Calendar](https://support.apple.com/en-lamr/guide/iphone/iph3d1110d4/ios) and [Google Contacts](https://support.google.com/contacts/answer/12732221?hl=en) already cover contact dates and birthday reminders.
- Google Play's announced 2026 Contacts policy favors a minimum-scope Contact Picker when broad access is not essential ([official policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)).
- Both [Google Play](https://support.google.com/googleplay/android-developer/answer/10144311) and [Apple](https://developer.apple.com/app-store/review/guidelines/) treat contact data as sensitive and constrain collection, use, and disclosure.

## Scoring Method

Each current capability is scored 0–10 on:

- **Business value** — likely contribution to activation, retention, revenue, cost reduction, trust, or compliance;
- **User value** — strength and frequency of the user problem solved;
- **Usability** — current end-user discoverability and effort, not internal command reachability;
- **Completeness** — complete real-world journey, including external and device dependencies;
- **Strategic importance** — importance to the proposed narrow product thesis.

Interpretation:

- 0–2: absent, harmful, or unjustified;
- 3–4: weak/niche or materially incomplete;
- 5–6: plausible but unvalidated;
- 7–8: strong fit with remaining evidence gaps;
- 9–10: essential to the proposed thesis or a non-negotiable trust constraint; still unvalidated as customer value unless explicitly labeled otherwise.

No current business or user-value score is validated. A 10 is a prioritization judgment under the proposed thesis, not proof of demand.

## Deliverable Index

| #   | Requested deliverable                             | Primary deliverable                                                                                                       |
| --- | ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| 1   | Complete feature inventory                        | `current-product-assessment.md` — feature decision matrix                                                                 |
| 2   | Business value assessment                         | `current-product-assessment.md` — outcomes, cost/value, scores                                                            |
| 3   | End-user value assessment                         | `current-product-assessment.md` and `user-experience-and-ideal-product.md`                                                |
| 4   | User journey analysis                             | `current-product-assessment.md` and `user-experience-and-ideal-product.md` — core, secondary/operator, and ideal journeys |
| 5   | Business workflow analysis                        | `current-product-assessment.md` and `../../SSOT.md` §8                                                                    |
| 6   | Business logic validation report                  | `current-product-assessment.md` — logic findings                                                                          |
| 7   | Broken functionality report                       | `current-product-assessment.md` — incomplete/broken reality                                                               |
| 8   | UX and usability report                           | `user-experience-and-ideal-product.md`                                                                                    |
| 9   | Technical debt report                             | `technical-rebuild-assessment.md`                                                                                         |
| 10  | Feature usefulness scoring                        | `current-product-assessment.md` — 0–10 matrix                                                                             |
| 11  | Keep/Improve/Merge/Rebuild/Remove recommendations | `current-product-assessment.md`                                                                                           |
| 12  | Product redesign recommendations                  | `user-experience-and-ideal-product.md` and `../../SSOT.md`                                                                |
| 13  | Rebuild feasibility analysis                      | `technical-rebuild-assessment.md`                                                                                         |
| 14  | Ideal information architecture                    | `user-experience-and-ideal-product.md` and `../../SSOT.md` §7                                                             |
| 15  | Ideal user journey flows                          | `user-experience-and-ideal-product.md` and `../../SSOT.md` §11                                                            |
| 16  | Ideal business workflows                          | `../../SSOT.md` §8                                                                                                        |
| 17  | New SSOT                                          | `../../SSOT.md`                                                                                                           |
| 18  | Product vision document                           | `product-vision-and-roadmap.md` §§1–3                                                                                     |
| 19  | Prioritized roadmap                               | `product-vision-and-roadmap.md` §§4–5                                                                                     |
| 20  | Complete Build From Scratch strategy              | `product-vision-and-roadmap.md` §6 and `technical-rebuild-assessment.md`                                                  |

## Document Set

- `../../SSOT.md` — the only normative business/end-user product scope.
- `current-product-assessment.md` — current capability, value, business logic, and feature decisions.
- `user-experience-and-ideal-product.md` — current journey/UX failures and zero-based ideal experience.
- `technical-rebuild-assessment.md` — architecture, technical debt, documentation reset, and rebuild feasibility.
- `product-vision-and-roadmap.md` — strategic choice, evidence-gated roadmap, and construction sequence.

## Decision Summary

### Rebuild

- customer UI and navigation;
- onboarding and permission progression;
- Today/Moments/People information architecture;
- task-oriented application use cases;
- reduced domain/data model;
- provider product path, measurement, support, and monetization operations.

### Reuse only after review

- recurrence/time-zone policies;
- duplicate and stale-confirmation guards;
- manual handoff and privacy-safe notification policies;
- bounded provider/session contracts;
- backup, cryptographic erasure, lifecycle journals, and recovery invariants;
- redacted issue model and release evidence gates.

### Merge/simplify

- Memory Vault, Gift Advisor, Chat History, and user-meaningful activity into one person timeline;
- Events, reminder plans, and preparation into Moments;
- AI generation, templates, manual composer, Wish Preview, tone, and review into Compose/Review;
- setup, permissions, and diagnostics into contextual guidance and Help.

### Remove/postpone

- dashboards and CSV reports;
- relationship health scoring;
- bulk operations and unattended automation;
- direct email/SMS/WhatsApp automation;
- standalone style/gift/memory/chat/diagnostic destinations;
- complex group and schedule policy matrices;
- broad sync, widgets, and shortcuts until core demand is proven.

## Documentation Reset

Until the product owner completes a history-retention review, old documents are retained as evidence but are not product authority:

- `docs/feature-fssot.md` — previous scope hypothesis; superseded by `SSOT.md`.
- `docs/feature-roadmap-analysis.md` — previous prioritization; superseded by the evidence-gated roadmap.
- implementation ADRs — technical history only; they cannot define product scope.
- `docs/react-native-migration-status.md` — current implementation status only.
- release/security docs — operational controls, not product strategy.

After that review, archive or delete superseded feature documents rather than maintaining duplicate truths. Keep only the SSOT, research evidence, current ADRs, operational runbooks, privacy/security policy, and generated release evidence references.

## Immediate Next Decision

Do not authorize more product implementation yet. Authorize customer discovery and Figma prototype testing. If the narrow thesis fails against native reminders plus general AI, stop or reposition before investing in another rebuild.
