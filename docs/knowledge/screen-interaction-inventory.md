# Screen and interaction inventory

[Knowledge home](README.md) · [Journeys](journeys/README.md) · [Traceability](journeys/traceability.md) · [Graph](master-project-graph.json)

**Snapshot:** 2026-11-25 checkout `20a598e9789a19e2a35a2b693e63c098473cc68d`. The inventory models **14 distinct rendered screen/page surfaces**: nine Flutter widget surfaces (including the not-found recovery screen) and five public Hosting routes. The Flutter router declares ten route patterns; two route patterns reuse Message Studio and two reuse the Person Form. Action IDs group a user goal/interaction family, not every repeated tap on a control.

## Route and screen inventory

| Screen ID | Surface / route | Visible purpose and state sources | Actions and destinations | Data/API dependencies | Status, edge cases and evidence |
|---|---|---|---|---|---|
| SCR-001 | Onboarding `/onboarding` | Three-page introduction; secure completion flag | ACT-001 Continue/Skip/Get Started; add/import onramps | Secure storage; contacts path after explicit selection | Implemented; write/permission failure should be surfaced; [screen](../../lib/features/onboarding/presentation/onboarding_screen.dart), [tests](../../test/features/onboarding/presentation/onboarding_screen_test.dart) |
| SCR-002 | Today `/dashboard` | Today, action-needed and upcoming birthday summaries from people/birthday streams | Add person; open birthday/person Studio; open Settings; select shell tab | Drift streams; lifecycle state | Implemented; loading/error/empty states are conditional; [screen](../../lib/features/dashboard/presentation/dashboard_screen.dart) |
| SCR-003 | People `/people` | Live people list, search and filters; import/export entry points | ACT-003 search/filter; ACT-004 import; ACT-005 delete/undo; add/edit; ACT-024 export | Persons repository; contacts permission; CSV parser/share | Implemented in source; permission refusal and invalid rows need explicit branches; [screen](../../lib/features/people/presentation/people_screen.dart) |
| SCR-004 | Person Form `/people/add`, `/people/edit/:id` | New/edit recipient fields, validation, save/discard state | ACT-002 save; ACT-026 resolve unsaved discard; ACT-028 set auto-prepare preference | PersonService, validator, repositories, birthday lifecycle | Implemented; model type differs from canonical model used by other modules; preference does not trigger an automatic preparation job; [screen](../../lib/features/people/presentation/person_form_screen.dart) |
| SCR-005 | Calendar `/calendar` | Month grid and selected-day detail | ACT-006 change month/open date; open person-linked Studio | Birthday and Person streams | Implemented; leap/timezone policy and device-local date changes are edge cases; [screen](../../lib/features/calendar/presentation/calendar_screen.dart) |
| SCR-006 | Activity `/history` | Drafts and delivery-event history | ACT-027 open history entry / return to Studio | MessageDrafts, DeliveryEvents | Implemented; handoff and confirmed-sent states are distinct; [screen](../../lib/features/history/presentation/history_screen.dart) |
| SCR-007 | Message Studio `/message-studio/:birthdayId`, `/message-studio/person/:personId` | Recipient/birthday context, editable draft, AI and channel controls | ACT-007 edit/autosave; ACT-008 AI/retry; ACT-009 rewrite; ACT-010 handoff; ACT-011 explicit confirmation | Drafts/Birthdays/People/Jobs; Gemini or Nano; external intent/share | Implemented with typed error states; missing target, stale cycle, missing phone, locked AI and external launch failures; [screen](../../lib/features/message_studio/presentation/message_studio_screen.dart) |
| SCR-008 | Settings `/settings` above the shell | Reminders, auth, AI key, entitlement, backup, SMS settings and onboarding replay | ACT-012 reminders; ACT-013 auth; ACT-014 purchase; ACT-015 backup; ACT-016 restore; ACT-017 SMS gate; ACT-022 open; ACT-023 replay | Secure storage, local settings, Firebase Auth/Firestore, Play verifier | Implemented source, integrations vary; automatic SMS defaults off; [screen](../../lib/features/settings/presentation/settings_screen.dart) |
| SCR-009 | Not found (router errorBuilder) | Requested path and route back to dashboard | Return to dashboard | Router only | Implemented; [router](../../lib/app/router.dart), [test](../../test/app/router_not_found_test.dart) |
| SCR-010 | Public site `/` | Product/resource links and release-config notice | ACT-020 language; ACT-030 navigate public resource routes | Local runtime-config fetch; no account API on home | Implemented; local preview showed missing-config warning and a local 404 for runtime-config as designed; [source](../../backend/hosting/src/app.ts), [browser evidence](project-coverage-report.md) |
| SCR-011 | Public deletion `/delete/` | Deletion explanation, reauthentication, private receipt controls | ACT-018 request; ACT-019 check/clear receipt; ACT-020 language | Runtime config, Firebase Auth, App Check, two deletion callables | Partially available; preview has missing config and disabled controls; no request was submitted; [source](../../backend/hosting/src/app.ts) |
| SCR-012 | Privacy `/privacy/` | Data, sharing, retention and deletion notice | ACT-030 public navigation; language | Runtime config/effective date values | Page renders in local preview; content approval/deployment not evidenced; [source](../../backend/hosting/src/app.ts) |
| SCR-013 | Terms `/terms/` | Product terms and platform-specific statements | ACT-030 public navigation; language | Runtime config, approved identity/effective dates | Page renders in local preview; public automation/iOS wording conflicts with app gates and absent deployment/platform target; [source](../../backend/hosting/src/app.ts) |
| SCR-014 | Support `/support/` | Help, data/account distinction, verified-support handoff | ACT-030 public navigation; language | Runtime config and external verified-support URL | Preview warns verified support is not configured; no private request sent; [source](../../backend/hosting/src/app.ts) |

## Interaction catalog

The following **30 interaction patterns** are the counted inventory represented by ACT-001 through ACT-030. A status describes the actual source path, not whether an external service is deployed.

| Action ID | Trigger → precondition → behavior → result | Status | Evidence / failure branch |
|---|---|---|---|
| ACT-001 | Onboarding next/skip/finish → write completion flag → route to shell | IMPLEMENTED | Onboarding screen and widget tests |
| ACT-002 | Save form → validate fields → PersonService/repositories → updated People | IMPLEMENTED | Invalid field errors and persistence failures |
| ACT-003 | Enter search/filter → filter live people view → matching/empty state | IMPLEMENTED | Search test and People visibility tests |
| ACT-004 | Choose contacts/CSV → permission/parse/normalize/review → persist selected rows | IMPLEMENTED | Permission denial and invalid-row diagnostics |
| ACT-005 | Delete person → confirm/soft-delete → offer undo → refresh related data | IMPLEMENTED | Person service/delete tests |
| ACT-006 | Change calendar month/select date → render day detail → open Studio | IMPLEMENTED | Calendar tests |
| ACT-007 | Edit draft → mark dirty → debounce autosave/lifecycle flush → durable draft | IMPLEMENTED | Durable and optimistic Studio tests |
| ACT-008 | Generate/retry → entitlement/provider check → durable job → draft or typed failure | IMPLEMENTED | Job/AI/Studio tests; manual writing remains fallback |
| ACT-009 | Change tone/length, shorten, translate or vary → AI request → editable result | IMPLEMENTED | Studio and prompt-builder tests |
| ACT-010 | Choose WhatsApp/SMS/share/copy → validate/launch or copy → record handoff where applicable | IMPLEMENTED | Launch is not delivery confirmation |
| ACT-011 | Confirm user sent → persist explicit outcome/status → Activity updates | IMPLEMENTED | User confirmation only; not inferred from external return |
| ACT-012 | Change reminders/quiet hours → persist preference → reconcile scheduler | IMPLEMENTED | Permission/OS delivery still platform-dependent |
| ACT-013 | Sign in/out → Google/Firebase gateway → refresh auth state | IMPLEMENTED | Live provider config and token revocation not tested here |
| ACT-014 | Buy/restore → Play Billing → verify purchase/account binding → entitlement update | IMPLEMENTED | Real store purchase not observed |
| ACT-015 | Start backup → durable cloud job → generation/manifest/pointer → result feedback | IMPLEMENTED | Requires signed-in account and deployed Firebase |
| ACT-016 | Confirm restore → validate generation → transactionally apply local records → replan reminders | IMPLEMENTED | Malformed/partial failure behavior needs live integration evidence |
| ACT-017 | Toggle automatic SMS → permission and feature gate → default-disabled/cancel jobs | PARTIALLY_IMPLEMENTED | `ENABLE_AUTOMATIC_SMS=false` by default; personal variant permission differs from Play |
| ACT-018 | Start public deletion → runtime config + Google reauth + App Check → request receipt | PARTIALLY_IMPLEMENTED | Disabled in local preview because release config absent |
| ACT-019 | Enter/check/clear private receipt → signed-out App Check request → known status or unknown | PARTIALLY_IMPLEMENTED | No production callable invoked |
| ACT-020 | Toggle language → update query/state and localized public copy | IMPLEMENTED | Browser navigation observed; localization quality approval unknown |
| ACT-021 | Select/re-tap shell destination → switch indexed branch or return branch to initial route | IMPLEMENTED | Four destinations; shell widget tests |
| ACT-022 | Tap Settings action → push `/settings` above current shell destination | IMPLEMENTED | Navigation test; Settings is not a fifth tab |
| ACT-023 | Settings onboarding replay → navigate to `/onboarding` → preserve return path as implemented | IMPLEMENTED | Source route and shell navigation evidence |
| ACT-024 | Export contacts → serialize CSV → invoke user share/save path | IMPLEMENTED | CSV service source; device file/share result not independently exercised |
| ACT-025 | Tap notification or cold-start notification → resolve person ID → open person-linked Studio | IMPLEMENTED | Native gateway + AppScaffold; cold-start device validation remains |
| ACT-026 | Abandon edited person form → confirm discard or stay → pop only on confirmation | IMPLEMENTED | Form discard test |
| ACT-027 | Select Activity/history item → open matching Studio/history detail | IMPLEMENTED | History screen source; stale/missing cycle needs recovery validation |
| ACT-028 | Toggle per-person auto-prepare preference → persist preference → no automatic AI generation in current default product gate | PARTIALLY_IMPLEMENTED | Preference exists; end-to-end trigger is not implemented/verified |
| ACT-029 | Startup database retry → invalidate/reopen database preflight → ready or recovery state | IMPLEMENTED | App startup recovery screen and provider |
| ACT-030 | Follow public page links → open privacy/terms/support/delete route | IMPLEMENTED | Static routes render; config-dependent actions remain gated |

## System states and inaccessible actions

| State family | Observable states | Recovery / limitation |
|---|---|---|
| Startup | checking database → ready or local recovery | Retry is explicit; no cloud fallback is asserted |
| Form | clean/editing/invalid/saving/saved/discard prompt | Avoid claiming save on failed persistence |
| AI/job | queued/running/retry/succeeded/failed/cancelled | Worker is app-process backed, not a guaranteed OS job |
| Delivery | draft → handoff → explicit user-confirmed sent | Handoff does not prove carrier/recipient delivery |
| Reminder | persisted preference and separate OS schedule | Reconcile after permission/timezone/reboot changes |
| SMS automation | unsupported/disabled/permission-gated/scheduled states | Feature gate defaults false; Play lacks permission |
| Public deletion | unconfigured/ready/authenticated/requested/receipt status | Missing release config disables request/check; unknown receipt never means complete |

## Evidence boundary

Source controls, route declarations, tests and the local browser preview are linked above. Runtime evidence for the Flutter app is separately bounded in the [coverage report](project-coverage-report.md). The user-requested full browser exploration of the Flutter app cannot be performed from this checkout because there is no Flutter `web/` target; the browser inspection covered the separate Hosting product surface.
