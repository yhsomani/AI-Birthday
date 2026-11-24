# Phase 2.4.2 — Decision approval matrix

**Approval rule:** A recommendation is not an approval. Record approver role, decision date, approved wording and any conditions before changing a status to Approved.

| Decision | Recommended resolution | Required approval | Current status |
|---|---|---|---|
| DEC-019 | One local UnitOfWork transaction for Person + affected Birthday occurrence rows; durable post-commit reconciliation for OS side effects | Architecture + Data | Awaiting technical sign-off |
| DEC-020 / DEC-025 | Merge as one decision: one PersonRepository port, one Drift adapter and one application write owner | Architecture + Data | Awaiting technical sign-off |
| DEC-022 | Retain prior-cycle drafts and delivery evidence as read-only history linked to exact cycle; define separate retention/purge policy | Product + Privacy | Awaiting product/privacy approval |
| DEC-023 | Preserve legacy no-birthday rows as Needs birthday; no occurrence/reminder; keep visible/exportable; require correction before successful edit | Product + Data | Awaiting product/data approval and record count |
| DEC-024 | Prompt per legacy Feb 29 Person for Feb 28 or Mar 1; do not infer past preference from old computed date | Product | Awaiting product approval and record count |
| DEC-026 | Durable per-Person/per-year Birthday record, unique on (personId, cycleYear) | Product + Data | Awaiting product/data approval |
| DEC-027 | Soft-delete Person as aggregate visibility boundary; hide linked children during Undo window; define permanent purge separately | Product + Privacy + Data | Awaiting product/privacy approval |
| DEC-028 | Explicit country selection for local number; validate and store E.164; never infer country | Product + UX + Security | Awaiting product and dependency review |
| DEC-029 | Invalid non-empty timezone is preserved and flagged; do not silently convert to device-local; pause precise reminders until repaired | Product + Android | Awaiting product/platform approval |
| DEC-030 | Show invalid import row with field error, unselected/disabled until corrected | Product + UX | Awaiting product/UX approval |
| DEC-031 | Preserve existing stored autoPrepare true/false; false applies to new records only; no mass reset | Product + Data | Awaiting product/data approval and provenance assessment |
| DEC-032 | After repair: UNIQUE(personId, cycleYear), Birthday.personId FK to Persons.id ON DELETE RESTRICT; add birthday NOT NULL only after clean preflight | Architecture + Data | Awaiting technical sign-off and inventory |
| DEC-033 | Reconcile on app resume/timezone change and next relevant midnight across distinct recipient zones; reminders use canonical occurrence | Android + Architecture | Awaiting technical sign-off and device validation |
| DEC-034 | Durable local workflow record; do not independently sync Birthday rows until DEC-015 defines backup/sync behavior | Data + Sync owner | Awaiting data/sync approval |
| DEC-035 | Legacy no-birthday rows remain visible/exportable with warning; import marks invalid; successful edits require correction | Product + UX + Data | Awaiting product approval and alignment with DEC-023 |

## 1. Decisions requiring explicit user/product approval

These decisions change user experience, retention or interpretation of personal data:
- DEC-022 prior-cycle draft and delivery-history retention;
- DEC-023 treatment of old Persons without birthday;
- DEC-024 legacy Feb 29 choice prompt;
- DEC-027 deletion/Undo and eventual purge;
- DEC-028 country selection and phone normalization UX;
- DEC-029 invalid timezone behavior;
- DEC-030 invalid import candidate UX;
- DEC-031 existing autoPrepare preservation;
- DEC-035 legacy edit/export behavior.

**Recommended product package:** approve these recommendations as a coherent bundle, or identify the DEC IDs that need a different direction. DEC-023/024/029/031 still need actual-record evidence before final migration detail.

## 2. Decisions requiring technical sign-off

- DEC-019, DEC-020/025, DEC-026, DEC-032, DEC-033 and DEC-034.

Approval is for target architecture only. It does not authorize application changes; implementation requires a separate scope/authorization.

## 3. Approval record template

For each approved decision, record:

| Field | Value |
|---|---|
| Decision ID | DEC-xxx |
| Approved wording | Exact direction approved |
| Approver role | Product / Data / Architecture / Android / Privacy |
| Approver | Name or accountable role |
| Date | YYYY-MM-DD |
| Conditions | Preconditions, exceptions or deferred details |
| Evidence reference | Inventory report, design, security/privacy review or test plan |

## 4. Current approval statement

At Phase 2.4.2 creation, no new recommendation has been marked Approved. The four Phase 2.3.1 product decisions remain approved. This matrix is awaiting explicit owner sign-off.
