# Developer Workbench — Project About and Sole Source of Truth

Date: 2026-07-11

Status: **pre-implementation, clean-slate product baseline**

Authority: **This is the only project document and the only source of truth for future implementation.**

## 1. Reset state and binding decision

The previous RelateAI React Native relationship application has been rejected as the future product and removed from the working tree. It solved a different consumer problem, exposed 143 raw JSON inputs instead of a usable product interface, had no VS Code extension surface, and carried a large centralized runtime, global reducer, custom mobile persistence, native integrations, and release machinery that do not serve the target users.

The new product will be built from scratch as a local-first, evidence-grounded VS Code Developer Workbench.

No product implementation exists after this reset. That is intentional. Phase 1 product implementation must not begin until the product owner accepts the feature decisions and Phase 0 gates in this file. Phase 0 may use disposable, isolated prototypes and technical spikes solely to validate those gates; they are not production architecture, release code, or permission to retain a scaffold.

The post-reset repository may contain only:

- PROJECT_ABOUT.md;
- one preserved historical Firebase Android client configuration at app/google-services.json;
- Git history as the rollback/audit mechanism.

The Firebase file is dormant configuration, not proof of a working connection and not authorization for a Firebase-dependent feature.

### Product promise

**Open an unfamiliar repository, get a trustworthy system map, ask cited questions, and understand the likely consequences of a change.**

### Clean-slate rules

- Do not reuse the mobile product domain, UI, runtime, commands, state, storage, tests, build system, native adapters, or release pipeline.
- Carry forward only principles: local-first processing, bounded inputs, explicit consent, cancellation, provenance, fail-closed provider behavior, and evidence-bound release claims.
- Build the evidence system first, daily decision workflows second, and breadth last.
- “Technology-agnostic” means adapter-based with a visible support matrix; it does not mean equal semantic depth for every language.
- AI explains verified evidence. It does not create authoritative architecture facts.
- The extension never silently edits, executes, installs, commits, pushes, publishes, deploys, or writes repository artifacts.

## 2. Product scope

### 2.1 The production wedge

The first credible product contains five connected user capabilities:

1. **Safe discovery and control** — identify workspace boundaries, technologies, exclusions, trust state, and available analysis without executing project code.
2. **Local evidence/dependency graph** — store versioned, source-linked facts with provenance, confidence, freshness, and known gaps.
3. **Architecture Explorer** — show high-level architecture, modules, dependencies, and a structure mode with drill-down to evidence and source.
4. **Cited onboarding and Q&A** — guide new developers and answer bounded questions; deterministic graph/search results remain available when AI is off.
5. **Change Impact** — analyze a file, symbol, package, or working-tree diff and show direct/likely effects, relevant discovered tests, and unknowns.

Circular dependency detection is the first dependency finding. Folder structure is an Explorer mode. Guided onboarding is a Q&A/Explorer mode. These are not separate products.

### 2.2 Explicit non-goals

The initial product is not:

- an autonomous coding agent;
- a generic chatbot;
- a replacement compiler, linter, SAST/SCA tool, profiler, test runner, Git host, CI platform, or observability platform;
- a cloud-mandatory source-code graph;
- a universal health score or employee productivity monitor;
- a diagram generator disconnected from source evidence;
- a public analyzer marketplace;
- a root-cause, modernization, or refactoring automation system;
- a system that creates hidden files or temporary artifacts in repositories.

## 3. Users and jobs

| Persona | Core job | Required outcome |
| --- | --- | --- |
| Beginner, intern, fresher, new joiner | Understand vocabulary, architecture, entry points, workflows, and where a change belongs | Guided, cited path to a first safe change with fewer senior interruptions |
| Experienced developer, lead, architect | Trace dependencies, assess impact, review coupling, and plan safe change | Fast, evidence-backed paths, impact, cycles, tests, and limitations |
| Engineering manager | Understand explainable engineering risks and investment needs | Dimensional evidence and trends, never an opaque score or individual ranking |
| Solution architect | Visualize boundaries, dependencies, services, APIs, data, and drift | Source-linked projections and enforceable rules only after graph accuracy is proven |
| Security/platform/QA | Correlate existing scanner, topology, CI, test, and release evidence | One finding/evidence model without replacing specialist tools |
| Enterprise administrator | Govern data, AI, providers, policy, retention, and distribution | Local-only mode, explicit controls, audit, proxy, and offline options when enterprise scope is approved |

Primary jobs:

- “Explain this repository without guessing.”
- “Show where this behavior starts and what it touches.”
- “What can break if I change this?”
- “Which tests, owners, APIs, data, or services are affected?”
- “Why does the workbench believe this, how fresh is it, and what is unknown?”

## 4. Product principles and success

### Principles

- Evidence before explanation.
- One graph, many projections.
- Native VS Code UI before webviews.
- Local by default; network use visible and controllable.
- Read-only by default; preview before any write or execution.
- Project content is hostile input and never trusted model instruction.
- Progressive useful results instead of a blocking all-or-nothing scan.
- Integrate mature tools through open formats instead of recreating them.
- Unknown and unsupported are valid outcomes.
- No developer surveillance or individual scoring.

### North-star outcome

Weekly evidence-backed engineering decisions completed per active workspace: a cited answer used, an impact path reviewed, a finding resolved, or a reviewed artifact explicitly exported. Opening a diagram or sending a chat message alone is not value.

### Guardrails

- Zero unapproved repository writes, command execution, network egress, or external publication.
- Every material fact and AI claim has valid evidence or an explicit hypothesis/unknown label.
- Analyzer-specific accuracy and limitation reports.
- Bounded activation, indexing, query, memory, and cancellation behavior.
- No source, paths, prompts, findings, secrets, or personal identity in telemetry.
- Full keyboard, screen-reader, high-contrast, zoom, and reduced-motion support.

## 5. Legacy product disposition

Every previous relationship/mobile capability was evaluated against the target developer personas, business value, developer value, VS Code fit, scalability, privacy, maintainability, and workflow value. All feature-domain implementations fail target fit and are removed. Rebuild mappings below refer only to a transferable idea, never code reuse.

### 5.1 Legacy capability inventory

The 68 rows below cover all 141 catalogued mobile commands as customer-meaningful capability groups; L01 also covers the two shell-only recovery commands. “Rebuild” means reuse the idea only; it never authorizes code reuse.

| ID | Existing capability | Decision | Future mapping or specific reason |
| --- | --- | --- | --- |
| L01 | JSON command console, catalog, and raw dispatch hook | Remove | A developer harness must not become product architecture |
| L02 | Navigation state and deep links | Rebuild | VS Code commands, views, editors, URIs, and source navigation |
| L03 | Home planner / daily plan | Rebuild | One evidence-backed workspace Overview and finding queue |
| L04 | Goal-based onboarding | Rebuild | DW-15 and the first-value workflow |
| L05 | Setup Wizard | Remove | Use contextual capability guidance, not a second onboarding system |
| L06 | Setup Doctor / readiness checks | Rebuild | Capability diagnostics, logs, and privacy-safe support export |
| L07 | Settings | Rebuild | VS Code configuration scopes, policies, and a Data Control Center |
| L08 | Local account mode | Remove | Local-first is the default, not a pretend account |
| L09 | Google account/sync concept | Remove | No relevance to codebase analysis |
| L10 | Contact browse, search, filter, sort, and detail | Remove | Personal CRM domain |
| L11 | Contact create/edit/archive/restore/delete | Remove | Personal CRM domain |
| L12 | Contact identity and route normalization | Remove | Personal CRM domain |
| L13 | Device-contact import | Remove | Mobile personal-data integration |
| L14 | Contact merge and conflict resolution | Remove | Personal CRM domain |
| L15 | Relationship groups | Remove | Personal CRM taxonomy |
| L16 | Per-contact channel, tone, cadence, DND, timing, and automation overrides | Remove | Personal communication policy |
| L17 | Contact enrichment prompts | Remove | Personal CRM workflow |
| L18 | Relationship-health score and group suggestion | Remove | Unrelated and intrinsically misleading |
| L19 | Event browse and lifecycle | Remove | Personal occasion domain |
| L20 | Yearly recurrence and leap-day policy | Remove | Correct code for the wrong product |
| L21 | Event text/file import | Remove | Personal occasion data |
| L22 | Calendar import | Remove | Mobile calendar integration |
| L23 | Calendar export | Remove | Mobile calendar integration |
| L24 | Event preparation checklist | Remove | Personal occasion workflow |
| L25 | Reminder plan generation and reconcile | Remove | Mobile reminder domain |
| L26 | Notification permission/readiness | Remove | Mobile notification domain |
| L27 | Check-in cadence, snooze, and mark-contacted | Remove | Personal relationship workflow |
| L28 | Follow-up scheduling | Remove | Personal relationship workflow |
| L29 | Manual composer | Remove | Personal message composition |
| L30 | Local message templates | Remove | Personal message composition |
| L31 | Authenticated AI drafting | Remove | Provider seam is too domain-specific; rebuild AI at DW-16 |
| L32 | AI context preview and privacy policy | Rebuild | DW-32 and DW-38; preserve minimization intent only |
| L33 | Tone, language, and recipient controls | Remove | Personal message composition |
| L34 | Draft variants and regeneration | Remove | Personal message composition |
| L35 | Style Coach | Remove | Personal writing-style feature |
| L36 | Message testing / route preflight | Remove | Personal delivery domain |
| L37 | Message inbox/query | Remove | Personal delivery domain |
| L38 | Preview/edit/channel/variant selection | Remove | Personal delivery domain |
| L39 | Approval, expiry, revoke, reject, and duplicate guard | Remove | Domain workflow; stale-confirmation principle is reimplemented |
| L40 | Scheduling, quiet hours, send time, blackouts, and time-zone guard | Remove | Personal delivery policy |
| L41 | Smart approve and VIP approve modes | Remove | Unimplemented and irrelevant automation |
| L42 | Bulk message actions | Remove | Personal delivery workflow |
| L43 | SMS handoff | Remove | Mobile communication integration |
| L44 | WhatsApp handoff | Remove | Mobile communication integration |
| L45 | Manual mail handoff | Remove | Mobile communication integration |
| L46 | Provider email delivery | Remove | Personal communication backend |
| L47 | Handoff sent confirmation | Remove | Personal delivery workflow |
| L48 | Retry, revoke, recovery, and delivery reconcile | Remove | Personal delivery workflow |
| L49 | Memory vault | Remove | Personal CRM data |
| L50 | Contact timeline | Remove | Personal CRM data |
| L51 | Chat history | Remove | Personal communication history |
| L52 | Gift records, feedback, budget, and suggestions | Remove | Personal relationship domain |
| L53 | Relationship analytics dashboard | Remove | Unrelated metrics; do not rename it into engineering health |
| L54 | Redacted analytics summary sharing | Remove | Unrelated report |
| L55 | Analytics CSV export | Remove | Unrelated report |
| L56 | Activity history and issue resolution | Rebuild | Current evidence-backed finding lifecycle; suppression/audit only in later DW-36 |
| L57 | Privacy center and permission history | Rebuild | DW-38 Data and Privacy Control Center |
| L58 | Biometric lock and recovery | Remove | Use VS Code authentication and SecretStorage; do not add an app lock |
| L59 | Encrypted entity persistence and migration | Rebuild | Extension-owned rebuildable index and VS Code SecretStorage |
| L60 | Encrypted backup export and restore | Remove | Caches rebuild; explicit reports/baselines use normal save dialogs |
| L61 | Local data clear and recovery | Rebuild | Clear workspace index, clear all data, and safe reindex commands |
| L62 | Operation serialization and cancellation | Rebuild | Cancellable worker scheduler with resource budgets |
| L63 | Redacted errors and logging | Rebuild | Structured privacy-safe logs, diagnostics, and telemetry |
| L64 | Launcher shortcuts | Remove | Use Command Palette, menus, keybindings, and editor context |
| L65 | Home widget | Remove | No workbench value |
| L66 | Localization | Rebuild | Externalize all user text and support locale-safe formatting |
| L67 | Accessibility | Rebuild | WCAG-aligned keyboard, screen-reader, zoom, and reduced-motion behavior |
| L68 | Release evidence and production gating | Rebuild | Signed VSIX, SBOM, provenance, compatibility, and staged release gates |

### 5.2 Required 16-point assessment for every legacy feature

The preceding inventory plus this rule is the complete 16-point evaluation for L01–L68:

1. **Feature name:** the row name.
2. **Problem it solves:** the pre-reset mobile/relationship problem represented by the row; for rebuilt infrastructure rows, the analogous reliability or control problem.
3. **Target users:** pre-reset mobile consumers, not any target Developer Workbench persona; rebuilt rows map to the target feature named in the row.
4. **Business value:** zero in the future product unless a target mapping is named.
5. **Developer value:** zero in the future product unless a target mapping is named.
6. **Why needed:** not needed for a codebase workbench unless mapped.
7. **Real-world use cases:** none in the target for removed rows; mapped rows use the corresponding DW specification.
8. **Proposed workflow:** none for removed rows; mapped rows use the corresponding target workflow.
9. **UI requirements:** no target UI for removed rows.
10. **Backend requirements:** no target backend for removed rows.
11. **Technical considerations:** React Native, Expo, native-mobile, and personal-domain contracts are incompatible with the extension host and source-analysis model.
12. **Risks:** carrying a row forward adds false compatibility, privacy surface, bundle weight, cognitive load, and maintenance cost.
13. **Limitations:** no row analyzes code, models software architecture, or integrates with VS Code.
14. **Recommended priority:** P0 removal from the new build; mapped concepts are prioritized in the target catalogue.
15. **Should it be implemented?:** No for Remove; Yes only as a clean implementation under the named target feature for Rebuild.
16. **Reasoning:** the row-specific final column, together with the incompatibility and value findings above.

This explicit rule avoids repeating identical “no target fit” prose 68 times while evaluating every pre-reset capability. The detailed target specifications below replace, rather than extend, the rebuilt concepts.

### 5.3 Pre-reset implementation defects, duplication, and missing abstractions

These findings are a historical snapshot of the removed implementation. They explain why reuse of that architecture was rejected even apart from the product mismatch; none of the referenced paths remains in the working tree.

| Finding | Concrete evidence | What is wrong and the problem it creates | Ideal target treatment |
| --- | --- | --- | --- |
| Command surface is repeated | Command union, 141-item catalog, 1,833-line parser, 5,951-line runtime dispatcher, reducer action union/switch, tests, and docs | One feature requires coordinated edits across central files; exhaustive typing detects drift but does not create ownership | Feature-owned use cases and one declarative command/contribution registration contract |
| Universal command runtime | src/application/commandRuntime.ts had 46 internal dependencies and nearly all feature orchestration/transient sessions | High change radius, merge conflicts, unrelated security/operation concerns, and no independent feature lifecycle | Thin VS Code handlers invoking application use cases and ports |
| God reducer/global state | src/state/relateReducer.ts was 4,144 lines; AppState mixed entities, settings, navigation, providers, reminders, setup, and persistence | Business invariants were split among runtime, domain helpers, reducer, and persistence; all data was hydrated into arrays | Workspace sessions, feature repositories, versioned evidence graph, immutable generations, and small view state |
| Full-state persistence work | Persistence serialized/compared broad AppState; dirty calculation stable-serialized collections | Cost grew with total data and could not scale to a code graph/monorepo | Transactional indexed incremental store with dependency-directed invalidation |
| Overbuilt custom database | Approximately 1,600 lines of custom encrypted record/manifest/checkpoint/index logic plus migrations | Large security-critical maintenance surface without independent review; pre-reset production paths did not use much of its query/retention API | Maintained embedded database behind a storage port; source index is rebuildable |
| Repository capabilities were unused | query, archive-state, and retention capabilities were not consumed by normal production journeys | Complexity existed without product value and created false scalability confidence | Add capabilities only behind a proven use case |
| Duplicate contact browsing | buildContactBrowserRows was tested but production used a separate contacts.query implementation | Two health/filter semantics could drift | Removed with the mobile domain; target projections have one query owner |
| Duplicate event browsing | Event browser filtering/month-grid helpers were tested while runtime implemented another event query path | Redundant rules and unreachable product behavior | Removed; target diagram/query projections share graph queries |
| AI preview disagreement | buildAiContextPreview had no production caller; the active message preview used different eligibility/count logic than the provider context builder | The privacy preview could disagree with data actually sent, undermining consent | One policy engine creates both the exact preview and transport payload |
| Duplicate biometric/account/tone helpers | resolveBiometricLock, buildAccountExitPlan, tone and wish-feedback builders were test-only or reimplemented elsewhere | Test coverage gave the appearance of product reachability while code was dead | Target feature contracts require an executable caller or deletion |
| Setup systems overlapped | Onboarding, Setup Wizard, Setup Doctor, settings, capability flags, and Home warnings modeled overlapping readiness | Users faced conflicting states and maintenance duplicated policy | One capability registry, contextual fixes, and support diagnostics |
| Health definitions conflicted | Persisted contact health and derived health were used by different reports | The same label could mean different data, making analytics untrustworthy | Removed; future health dimensions use one published formula and evidence contract |
| Permission responsibility was duplicated | Command preflight/coordinators and native adapters both inspected/requested certain mobile permissions | Side-effect ownership and failure truth became hard to reason about | One trust/consent/policy boundary; infrastructure never prompts unexpectedly |
| AI capability truth conflicted with defaults | Fresh state enabled AI while authenticated provider capability was unavailable by default | UI/readiness could imply a product capability that could not run | Capability registry is authoritative; unavailable providers never appear ready |
| Private console result conflicted with documentation | Unlocked inspect/query commands could return private names/bodies/notes that App serialized, while some docs implied only redacted output | Privacy claims did not match the actual presentation boundary | Target UI receives minimum view models; diagnostics remain content-free |
| Shell vocabulary escaped the catalog | runtime.retry and runtime.clear-corrupt-storage were parsed in App.tsx but absent from the 141-command catalog | “Exhaustive” public-surface claims were inaccurate | One typed registration source with runtime tests |
| Android code generation was premature | Widget and launcher-shortcut plugins generated native Java while the removed app had no feature UI | Optional platform maintenance preceded validated core value and lacked parity | Target extension adds only VS Code-native contributions tied to proven jobs |
| Release machinery was disproportionate | Extensive mobile release-evidence logic existed before a usable product or signed-device proof | Engineering effort optimized release evidence for a product users could not operate | Keep evidence discipline but bind it to VSIX, extension-host, security, performance, and Marketplace proof |
| Legacy utility was stale | scripts/extract_strings.sh scanned Kotlin UI calls and was not part of the pre-reset TypeScript scripts | Dead migration residue confused ownership and maintenance | Target localization extraction is extension/webview-specific |
| Critical target abstractions were absent | No workspace trust, file discovery/index, capability registry, evidence graph, analyzers, workers, diagram projection, typed webview protocol, extension storage, or provider governance | The removed runtime could not incrementally acquire the target architecture without carrying unrelated assumptions | Build the target packages in Sections 11–12 from scratch |

## 6. Future feature decision matrix

Decision meanings:

- **MVP keep:** approved for the first product.
- **Merge:** useful only as a mode, projection, enrichment, finding, or small internal contract inside an approved feature; no standalone engine, screen, or roadmap pillar.
- **Later:** not approved backlog. It requires new user evidence and an explicit decision gate.
- **Remove:** not part of the product unless this file is formally changed.

| ID | Feature | Decision | Production judgment |
| --- | --- | --- | --- |
| DW-01 | Interactive Architecture Explorer | MVP keep | Primary user surface and fastest route to codebase understanding |
| DW-02 | Dependency Analysis Engine | MVP keep | Supplies the facts for architecture, cycles, Q&A, and impact |
| DW-03 | Project Knowledge Graph | MVP keep | Internal evidence model; never a separate marketed screen |
| DW-04 | Security Vulnerability Scanner | Merge | Later import and correlate mature SARIF/SBOM/SAST/SCA output |
| DW-05 | Technical Debt Analyzer | Merge | Later explainable hotspot signals; no subjective debt oracle |
| DW-06 | Performance Bottleneck Analyzer | Later | Only with imported measured profiles/traces; no static speculation |
| DW-07 | API Relationship Mapper | Merge | Later Explorer projection over the shared graph |
| DW-08 | Database Relationship Visualizer | Merge | Later Explorer projection for qualified schema/ORM adapters |
| DW-09 | Event Flow Analyzer | Merge | Later Explorer projection after framework demand is proven |
| DW-10 | Routing Diagram Generator | Merge | Later Explorer projection, not a separate generator |
| DW-11 | Folder Structure Visualization | Merge | MVP Structure mode inside Explorer |
| DW-12 | Dead Code Detection | Later | Requires mature semantic reachability and strict confidence gates |
| DW-13 | Unused Dependency Detection | Merge | Later dependency-hygiene finding inside DW-02 |
| DW-14 | Circular Dependency Detection | Merge | First deterministic MVP finding inside DW-02 |
| DW-15 | AI Project Onboarding Assistant | Merge | MVP guided mode across Explorer and Q&A; no second assistant |
| DW-16 | AI Codebase Q&A Assistant | MVP keep | Compelling only with citations and deterministic fallback |
| DW-17 | Change Impact Analyzer | MVP keep | Recurring daily value beyond one-time diagrams |
| DW-18 | Refactoring Recommendation Engine | Later | Plans before edits, only after impact/test accuracy is proven |
| DW-19 | Architecture Rule Validator | Later | Team feature after graph accuracy and baselines mature |
| DW-20 | Documentation Generator | Merge | Later explicit reviewed report/export, never silent generation |
| DW-21 | Test Coverage Analyzer | Merge | Later coverage-report evidence inside Impact |
| DW-22 | Design Pattern Detection | Remove | Subjective labels create noise and rarely change a decision |
| DW-23 | Code Ownership Mapping | Merge | Later team-first metadata in Explorer/Impact; no surveillance |
| DW-24 | Microservice Dependency Mapping | Merge | Later service projection; no early cloud control plane |
| DW-25 | Release Readiness Assessment | Later | Integration-dependent and unsafe as an early “ready” label |
| DW-26 | Technical Health Score Dashboard | Remove | Universal score invites gaming, surveillance, and false precision |
| DW-27 | Risk Assessment Dashboard | Merge | Later risk register inside Findings/Health |
| DW-28 | Migration and Modernization Recommendations | Later | Needs customer demand, current compatibility data, and mature impact |
| DW-29 | Repository Health Insights | Later | Dimensional view only after enough reliable analyzers exist |
| DW-30 | AI-Powered Root Cause Analysis | Remove | Outside the wedge, duplicates observability, high privacy/hallucination risk |
| DW-31 | Project Discovery and Capability Profile | MVP keep | Required for safe scope and honest support claims |
| DW-32 | Evidence, Provenance, Confidence, and Freshness | MVP keep | Central trust boundary for every output |
| DW-33 | Architecture Snapshot and Drift Diff | Merge | Later Change Impact mode |
| DW-34 | Branch and Pull-Request Intelligence | Merge | Later Change Impact scope, not another product |
| DW-35 | Test Selection and Validation Planner | Merge | Later validation section inside Impact |
| DW-36 | Policy Packs, Baselines, and Exceptions | Later | Enterprise governance after trusted local findings |
| DW-37 | Analyzer Integration SDK and Interchange Hub | Merge | Small internal adapter contract only; public SDK removed |
| DW-38 | Data and Privacy Control Center | MVP keep | Separate controls for scan, storage, AI, egress, execution, and write |

Totals: **8 MVP keep, 19 merge, 8 later, 3 remove.**

Merged capabilities do not add top-level navigation. Later capabilities do not enter implementation tickets until their gate is approved.

## 7. Complete 16-point feature evaluations

### DW-01 — Interactive Architecture Explorer

1. **Feature Name:** Interactive Architecture Explorer.
2. **Problem It Solves:** Architecture knowledge is fragmented across source, configuration, diagrams, and people; static diagrams drift and file trees do not explain system relationships.
3. **Target Users:** Beginners, experienced developers, technical leads, architects, platform engineers, and managers using curated views.
4. **Business Value:** Shortens onboarding and design-review time, reduces key-person dependency, and makes architecture evidence reusable.
5. **Developer Value:** Lets a developer move from system to component to file/symbol and back without manually reconstructing dependencies.
6. **Why It Is Needed:** It is the primary workbench surface and the common interaction layer for graph-backed analysis; without it, the product is a list of disconnected findings.
7. **Real-world Use Cases:** Trace a web request to a datastore; locate service boundaries; inspect a monorepo package; explain a legacy subsystem; inspect dependency boundaries before a local change.
8. **Proposed Workflow:** Select a workspace or bounded scope → choose a diagram projection → filter/cluster → inspect evidence and confidence → jump to source → save a private view or explicitly export.
9. **UI Requirements:** Searchable diagram panel, native outline tree, breadcrumbs, semantic zoom, minimap, legend, filters, confidence/freshness indicators, keyboard navigation, source peek, and path highlighting.
10. **Backend Requirements:** None for local exploration; an optional enterprise service may share approved snapshots and views without requiring raw source upload.
11. **Technical Considerations:** Render paged graph projections rather than the complete graph; compute layout off the extension-host thread; sanitize labels; use level-of-detail clustering and stable node identities.
12. **Risks:** Visual overload, false relationships presented as fact, inaccessible canvas interaction, high memory, and diagrams becoming decorative.
13. **Limitations:** Accuracy follows adapter coverage; static evidence cannot always resolve reflection, runtime injection, generated code, or production topology.
14. **Recommended Priority:** MVP.
15. **Should It Be Implemented?:** Yes — MVP core.
16. **Reasoning:** It is the primary user surface for trustworthy codebase understanding.


### DW-02 — Dependency Analysis Engine

1. **Feature Name:** Dependency Analysis Engine.
2. **Problem It Solves:** Developers cannot reliably see direct, transitive, cyclic, runtime, build-time, package, module, or symbol dependencies across heterogeneous projects.
3. **Target Users:** All developers, architects, security engineers, and platform teams.
4. **Business Value:** Reduces change risk, upgrade effort, architectural coupling, supply-chain exposure, and incident investigation time.
5. **Developer Value:** Answers “what depends on this?”, “why is this included?”, “where is this cycle?”, and “which boundary does this cross?” with traceable paths.
6. **Why It Is Needed:** Dependencies are the backbone of architecture, impact, dead-code, security reachability, test selection, and modernization.
7. **Real-world Use Cases:** Find consumers of a shared library; expose a service cycle; understand a transitive vulnerable package; plan a module split; inspect dependency direction violations.
8. **Proposed Workflow:** Discover manifests and semantic sources → extract typed edges → normalize identities/scopes → compute paths/cycles → show evidence → refine scope/type filters or explicitly export.
9. **UI Requirements:** Dependency tree and graph, inbound/outbound toggle, shortest-path explanation, direct/transitive distinction, scope/type filters, cycle groups, source links, and export preview.
10. **Backend Requirements:** None for source dependency analysis; optional signed vulnerability/license metadata feeds are separate inputs.
11. **Technical Considerations:** Parse lockfiles and build metadata without installing dependencies or running lifecycle scripts; model conditional/dynamic edges and per-ecosystem resolution confidence explicitly.
12. **Risks:** Resolver mismatch, path-alias errors, generated dependencies, huge fan-out, and treating package presence as runtime reachability.
13. **Limitations:** Dynamic loading, reflection, macros, code generation, and environment-specific resolution may require runtime or tool-produced evidence.
14. **Recommended Priority:** MVP.
15. **Should It Be Implemented?:** Yes — MVP core.
16. **Reasoning:** Architecture, cycles, Q&A, and impact all require one dependable dependency engine.


### DW-03 — Project Knowledge Graph

1. **Feature Name:** Project Knowledge Graph.
2. **Problem It Solves:** File-oriented tools cannot correlate components, symbols, APIs, data, events, tests, owners, findings, changes, deployments, and evidence.
3. **Target Users:** Indirectly every persona; directly analyzer authors, architects, and power users.
4. **Business Value:** Creates one reusable analysis foundation, reduces duplicated indexing, and enables differentiated cross-domain insights.
5. **Developer Value:** Produces consistent answers and paths across features instead of conflicting feature-specific models.
6. **Why It Is Needed:** It is the canonical system model behind MVP exploration, Q&A, impact, and findings, and behind any later approved projection.
7. **Real-world Use Cases:** MVP connects components, files, symbols, dependencies, discovered tests, changes, and evidence; later adapters may add APIs, data, events, ownership, deployments, and cross-repository facts.
8. **Proposed Workflow:** Ingest immutable evidence → normalize entities/edges → validate contracts → commit a transactional current generation → update incrementally → expose bounded queries/projections. Historical comparison is deferred to DW-33.
9. **UI Requirements:** No mandatory standalone screen; provide graph inspection/debug views, evidence details, query diagnostics, coverage, and schema version for advanced users/support.
10. **Backend Requirements:** None for MVP. A later team graph service is optional for explicitly approved cross-repository metadata and shared baselines.
11. **Technical Considerations:** Use typed node/edge schemas, provenance per fact, transactional updates, analyzer/schema versioning, content hashes, tombstones, namespace isolation, and indexed traversals.
12. **Risks:** A “god graph,” schema churn, stale facts, identity collisions, unbounded storage, expensive traversals, and accidental source-content replication.
13. **Limitations:** It represents observed evidence, not an omniscient digital twin; unsupported ecosystems and runtime behavior remain partial.
14. **Recommended Priority:** MVP internal foundation.
15. **Should It Be Implemented?:** Yes — internal foundation, not a separate marketed screen.
16. **Reasoning:** One evidence graph prevents feature-specific models from contradicting each other.


### DW-04 — Security Vulnerability Scanner

1. **Feature Name:** Security Vulnerability Scanner.
2. **Problem It Solves:** Security findings are spread across SAST, SCA, secrets, IaC, container, and configuration tools, often without reachability, ownership, or architectural context.
3. **Target Users:** Developers, security engineers, technical leads, and release owners.
4. **Business Value:** Speeds triage and remediation, lowers duplicated effort, improves audit evidence, and reduces exposure windows.
5. **Developer Value:** Shows a deduplicated finding with affected path, evidence, dependency reachability, owner, fix context, and suppression history.
6. **Why It Is Needed:** Security is a high-value SDLC workflow, but correlation is more defensible than attempting to replace mature scanners.
7. **Real-world Use Cases:** Trace a vulnerable transitive package to reachable code; correlate a hard-coded secret finding with ownership; detect risky IaC defaults; validate remediation impact.
8. **Proposed Workflow:** Import SARIF/SBOM/tool output and run approved local rules → normalize/dedupe → enrich with graph paths and ownership → triage → suppress with reason/expiry or preview remediation.
9. **UI Requirements:** Problems integration, security view with severity/confidence/reachability filters, evidence pane, dependency path, remediation preview, suppression audit, and policy status.
10. **Backend Requirements:** Optional signed advisory feeds, team policy, and centralized audit; raw source remains local unless an enterprise policy explicitly authorizes processing.
11. **Technical Considerations:** Use open formats, stable fingerprints, advisory freshness, ecosystem-specific severity, secret-safe logging, no dependency install, and no automatic fix application.
12. **Risks:** False positives, stale CVEs, duplicate alerts, secret leakage, scanner licensing conflicts, severity inflation, and misleading reachability.
13. **Limitations:** Static correlation does not prove exploitability; the workbench is not a penetration test or compliance certification.
14. **Recommended Priority:** Post-MVP merged integration.
15. **Should It Be Implemented?:** No as a standalone scanner; later import and correlate established SARIF, SBOM, SAST, and SCA outputs.
16. **Reasoning:** Rebuilding mature scanners adds cost and weaker coverage; correlation is the differentiated value.


### DW-05 — Technical Debt Analyzer

1. **Feature Name:** Technical Debt Analyzer.
2. **Problem It Solves:** Debt is discussed through anecdotes or generic complexity scores without showing cost, change frequency, coupling, ownership, or remediation leverage.
3. **Target Users:** Senior developers, leads, architects, and engineering managers.
4. **Business Value:** Makes investment choices defensible, focuses modernization work, and reduces long-term change cost.
5. **Developer Value:** Identifies concrete hotspots and explains why they matter, who changes them, what they affect, and how debt trends.
6. **Why It Is Needed:** A workbench spanning the SDLC must help prioritize improvement, not merely describe structure.
7. **Real-world Use Cases:** Find high-churn/high-coupling modules; locate boundary erosion; identify stale dependencies; compare debt before and after a refactor; build a remediation backlog.
8. **Proposed Workflow:** Combine structural findings, history, tests, ownership, and policy → group by cause → estimate evidence-backed impact → let teams accept, defer, suppress, or plan.
9. **UI Requirements:** Hotspot map, dimensional filters, evidence timeline, affected paths, trend/baseline diff, owner, effort band, and links to a refactoring plan.
10. **Backend Requirements:** None locally; team trends and portfolio comparison require approved snapshot metadata.
11. **Technical Considerations:** Keep rules transparent and configurable; separate facts from judgment; avoid invented monetary estimates; retain historical baselines without source bodies.
12. **Risks:** Gamified metrics, blame, false precision, noisy rules, culturally biased “best practices,” and teams optimizing scores instead of systems.
13. **Limitations:** Static metrics cannot measure organizational constraints or actual business priority; human review is required.
14. **Recommended Priority:** Post-MVP merged signal.
15. **Should It Be Implemented?:** No as a standalone debt engine; later add explainable hotspot signals inside Findings and Health.
16. **Reasoning:** Generic debt scoring is subjective; evidence-backed, actionable factors are useful only after the graph matures.


### DW-06 — Performance Bottleneck Analyzer

1. **Feature Name:** Performance Bottleneck Analyzer.
2. **Problem It Solves:** Developers struggle to connect profiles, traces, build timings, query plans, and suspicious code paths to architecture and recent changes.
3. **Target Users:** Experienced developers, performance engineers, SREs, and architects.
4. **Business Value:** Reduces investigation time and infrastructure waste and helps prevent regressions.
5. **Developer Value:** Correlates measured hotspots to call/dependency paths, owners, changes, tests, and optimization candidates.
6. **Why It Is Needed:** Performance has material value, but the feature must be grounded in measurements rather than static speculation.
7. **Real-world Use Cases:** Import a CPU profile; trace a slow endpoint; compare build-task duration; correlate an N+1 query warning; identify a heavy bundle dependency.
8. **Proposed Workflow:** Explicitly import or connect approved measurement output → normalize samples/spans → map to graph entities → rank measured hotspots → show hypotheses and validation steps.
9. **UI Requirements:** Flame/profile links, hotspot table, architecture overlay, before/after comparison, confidence/source badge, and a “measurement required” state for static hints.
10. **Backend Requirements:** None for imported local artifacts; optional observability integrations require user/admin authorization and scoped read-only tokens.
11. **Technical Considerations:** Support open profile/trace formats, symbol mapping, sampling bias, source-map handling, data-size limits, retention controls, and strict separation from automatic execution.
12. **Risks:** Sensitive production data, misleading samples, huge traces, unsupported symbols, vendor lock-in, and treating correlation as causation.
13. **Limitations:** The extension does not benchmark or profile a project automatically; static-only “bottlenecks” are labeled hypotheses.
14. **Recommended Priority:** Later, runtime-evidence gate.
15. **Should It Be Implemented?:** No for the initial product; reconsider only with imported profiles, traces, or benchmarks.
16. **Reasoning:** Static performance guesses would create noise and false confidence.


### DW-07 — API Relationship Mapper

1. **Feature Name:** API Relationship Mapper.
2. **Problem It Solves:** API definitions, handlers, middleware, clients, schemas, tests, and downstream data paths are difficult to connect across services and languages.
3. **Target Users:** Backend, frontend, full-stack, integration, QA, security, and architecture users.
4. **Business Value:** Reduces integration defects, accelerates API change reviews, and exposes undocumented or orphaned contracts.
5. **Developer Value:** Traces an endpoint or operation from declaration through implementation and consumers with version and evidence.
6. **Why It Is Needed:** APIs are common system boundaries and a natural high-value projection of the knowledge graph.
7. **Real-world Use Cases:** Find all consumers of a REST operation; compare OpenAPI to handlers; map GraphQL resolvers; trace gRPC calls; assess a breaking schema change.
8. **Proposed Workflow:** Detect specifications/framework routes/clients → normalize operations and schemas → link implementations and consumers → flag gaps → explore or run impact.
9. **UI Requirements:** API catalogue, operation filters, request/response schema view, producer-consumer graph, version/breaking-change badges, source navigation, and sequence projection.
10. **Backend Requirements:** None; optional API-catalog synchronization must be explicit and metadata-scoped.
11. **Technical Considerations:** Support OpenAPI, AsyncAPI, GraphQL, gRPC/protobuf, framework adapters, generated clients, versioning, and ambiguous route matching with confidence.
12. **Risks:** Framework churn, generated code noise, endpoint exposure misclassification, stale specs, and accidental display of example secrets.
13. **Limitations:** Runtime gateways, service meshes, dynamic routes, and external consumers require imported deployment/runtime evidence.
14. **Recommended Priority:** Post-MVP Explorer projection.
15. **Should It Be Implemented?:** No as a standalone feature; later merge into Explorer.
16. **Reasoning:** API relationships are valuable as a graph projection, not another indexing platform.


### DW-08 — Database Relationship Visualizer

1. **Feature Name:** Database Relationship Visualizer.
2. **Problem It Solves:** DDL, migrations, ORM models, queries, repositories, and service ownership provide conflicting or incomplete views of data structure and use.
3. **Target Users:** Backend and data developers, architects, DBAs, security engineers, and modernization teams.
4. **Business Value:** Lowers schema-change risk, improves data governance, and speeds legacy understanding.
5. **Developer Value:** Shows tables/entities, keys, relationships, migrations, readers/writers, and API/service paths linked to source.
6. **Why It Is Needed:** Data dependencies are critical to impact and architecture but should not be inferred from filenames alone.
7. **Real-world Use Cases:** Plan a column rename; find all writers to a table; visualize a legacy schema; compare ORM to migrations; trace PII flow.
8. **Proposed Workflow:** Parse approved schema/migration/ORM/query evidence → reconcile identities → expose conflicts → generate ER and access projections → inspect impact.
9. **UI Requirements:** ER diagram, table/column search, key and cardinality legend, service access overlay, migration timeline, conflict markers, PII tags, and source links.
10. **Backend Requirements:** None by default; live database introspection is a separate, explicit read-only integration with secret-scoped credentials.
11. **Technical Considerations:** Model dialects, migration order, schema namespaces, ORM conventions, generated models, dynamic SQL, and confidence per relationship.
12. **Risks:** Connecting to production accidentally, leaking schemas/data, incorrect inferred cardinality, and oversized ER diagrams.
13. **Limitations:** Static sources may not reflect deployed schema or dynamic queries; the extension does not browse or copy production row data.
14. **Recommended Priority:** Later Explorer projection.
15. **Should It Be Implemented?:** No as a standalone feature; later merge after qualified schema and ORM adapters exist.
16. **Reasoning:** The capability is adapter-heavy and should not enlarge the MVP.


### DW-09 — Event Flow Analyzer

1. **Feature Name:** Event Flow Analyzer.
2. **Problem It Solves:** Producers, brokers, topics, schemas, consumers, retries, dead-letter paths, and eventual side effects are scattered across configuration and code.
3. **Target Users:** Backend, platform, SRE, architecture, and incident-response teams.
4. **Business Value:** Reduces event-contract failures, hidden coupling, and incident diagnosis time.
5. **Developer Value:** Traces an event from publication through consumers and downstream effects with delivery semantics and evidence.
6. **Why It Is Needed:** Event-driven systems cannot be understood accurately through synchronous call graphs alone.
7. **Real-world Use Cases:** Find consumers before schema change; locate dead-letter handling; trace order-created effects; detect topic fan-out; compare intended and observed flow.
8. **Proposed Workflow:** Parse broker/IaC/config/framework evidence → normalize events/topics/handlers → link schemas and side effects → validate contracts → visualize and assess change.
9. **UI Requirements:** Event catalogue, producer-consumer graph, sequence view, retry/DLQ annotations, schema versions, delivery-semantics badges, filters, and source links.
10. **Backend Requirements:** None for static mapping; runtime broker metadata is an explicit read-only enterprise integration.
11. **Technical Considerations:** Support AsyncAPI and ecosystem adapters, aliases, environment-specific topic names, schema registries, generated consumers, and at-least/at-most/exactly-once claims.
12. **Risks:** Incorrect delivery-semantics claims, configuration interpolation, huge fan-out, sensitive payload examples, and environment drift.
13. **Limitations:** Static analysis cannot prove ordering, retries, or runtime subscribers; those require imported operational evidence.
14. **Recommended Priority:** Later Explorer projection.
15. **Should It Be Implemented?:** No as a standalone feature; later merge after event-framework demand is proven.
16. **Reasoning:** Event flow is valuable for some systems but not universal enough for the first release.


### DW-10 — Routing Diagram Generator

1. **Feature Name:** Routing Diagram Generator.
2. **Problem It Solves:** Frontend routes, backend routes, middleware, guards, controllers, redirects, and navigation transitions are hard to discover and keep documented.
3. **Target Users:** Frontend, mobile-web, backend, full-stack, QA, security, and onboarding users.
4. **Business Value:** Speeds feature discovery, reduces broken navigation/API changes, and improves test planning.
5. **Developer Value:** Maps route patterns to screens/handlers, middleware, permissions, loaders, tests, and outgoing calls.
6. **Why It Is Needed:** Routing is a frequent, understandable entry point and a high-value specialized projection.
7. **Real-world Use Cases:** Find the implementation for a URL; inspect auth guards; trace nested client routes; map gateway to service handler; identify untested routes.
8. **Proposed Workflow:** Detect router frameworks/config → normalize patterns and nesting → link handlers/guards/tests → surface conflicts/gaps → render route tree or flow.
9. **UI Requirements:** Route tree, flow diagram, path search, parameter/guard annotations, frontend/backend toggle, conflict badges, test coverage overlay, and source links.
10. **Backend Requirements:** None.
11. **Technical Considerations:** Use framework adapters, model route precedence and nesting, distinguish static from generated routes, and preserve environment/config confidence.
12. **Risks:** Dynamic route construction, false conflicts, framework version drift, and diagrams that confuse client navigation with server APIs.
13. **Limitations:** Runtime registration and remote gateway configuration remain partial without imported runtime/deployment evidence.
14. **Recommended Priority:** Post-MVP Explorer projection.
15. **Should It Be Implemented?:** No as a standalone generator; later merge into Explorer.
16. **Reasoning:** Routing should reuse the evidence graph and diagram engine.


### DW-11 — Folder Structure Visualization

1. **Feature Name:** Folder Structure Visualization.
2. **Problem It Solves:** Large repositories and monorepos hide package boundaries, generated/vendor areas, and architectural intent inside a flat file explorer.
3. **Target Users:** Beginners, new joiners, leads, architects, and monorepo maintainers.
4. **Business Value:** Improves orientation and reveals organization problems with little additional analysis cost.
5. **Developer Value:** Shows logical components and evidence overlays without requiring users to interpret every directory.
6. **Why It Is Needed:** A structural view is useful, but VS Code already has a file explorer; duplicating it as a product would add little value.
7. **Real-world Use Cases:** Identify workspace packages; distinguish source/test/generated/vendor areas; find entry points; compare directory structure to detected module boundaries.
8. **Proposed Workflow:** Open the Architecture Explorer → choose Structure projection → cluster by detected component/package → overlay classification and dependency/cycle evidence → navigate to source.
9. **UI Requirements:** Collapsible tree/treemap projection, exclude/generated controls, logical-versus-physical toggle, badges, search, and accessible list alternative.
10. **Backend Requirements:** None.
11. **Technical Considerations:** Reuse discovery classification, respect excludes and virtual URIs, avoid scanning binary/vendor trees, and never claim folders equal architecture.
12. **Risks:** Redundant Explorer UI, large-node rendering, and encouraging directory-based assumptions.
13. **Limitations:** Folder shape reveals organization, not call/data/runtime relationships.
14. **Recommended Priority:** MVP merged mode.
15. **Should It Be Implemented?:** No as a standalone feature; include a Structure mode in Explorer.
16. **Reasoning:** A context-rich structure view helps onboarding, while another file browser has no independent value.


### DW-12 — Dead Code Detection

1. **Feature Name:** Dead Code Detection.
2. **Problem It Solves:** Unreachable or unreferenced files, exports, symbols, routes, handlers, flags, and configuration accumulate risk and maintenance cost.
3. **Target Users:** Experienced developers, leads, maintainers, and modernization teams.
4. **Business Value:** Reduces attack surface, bundle/build cost, cognitive load, and migration effort.
5. **Developer Value:** Produces evidence for safe deletion and shows why a candidate may still be dynamically reachable.
6. **Why It Is Needed:** Dead-code cleanup is common and valuable, but language-specific semantics and dynamic entry points require conservative results.
7. **Real-world Use Cases:** Find unused exports; remove an abandoned feature; identify orphaned routes; detect unreachable service code; shrink a frontend bundle.
8. **Proposed Workflow:** Build entry-point/reachability model → apply language/framework rules → classify definite/likely/unknown → show paths and exclusions → preview deletion impact and tests.
9. **UI Requirements:** Problems/list view, confidence and entry-point explanation, reachability path, dynamic-use warning, baseline suppression, and explicit refactor-plan handoff.
10. **Backend Requirements:** None.
11. **Technical Considerations:** Use compiler/language-service outputs where available, model public API surfaces and reflection/configuration, and never equate “no static reference” with definite dead code.
12. **Risks:** Destructive false positives, generated/reflected use, plugin entry points, test-only use, and configuration-dependent reachability.
13. **Limitations:** Dynamic languages and framework magic may support only likely candidates; deletion remains user-controlled.
14. **Recommended Priority:** Later, semantic-accuracy gate.
15. **Should It Be Implemented?:** No for the initial product; add only after reachability accuracy is proven.
16. **Reasoning:** A false dead-code claim can cause destructive user action.


### DW-13 — Unused Dependency Detection

1. **Feature Name:** Unused Dependency Detection.
2. **Problem It Solves:** Declared packages, plugins, modules, and build dependencies remain after code changes, increasing cost and supply-chain exposure.
3. **Target Users:** Developers, maintainers, security engineers, and platform teams.
4. **Business Value:** Lowers vulnerability/license surface, install/build time, image size, and upgrade burden.
5. **Developer Value:** Explains which declaration appears unused, how scopes differ, and what dynamic/configured uses were checked.
6. **Why It Is Needed:** It is a practical extension of dependency evidence with immediate cleanup value.
7. **Real-world Use Cases:** Remove an obsolete npm package; find unused Maven/Gradle dependency; detect an unnecessary Python package; identify unused build plugins.
8. **Proposed Workflow:** Parse manifests/lockfiles/build config → correlate imports, scripts, plugins, generated/tool references → classify candidate → inspect evidence → preview manifest edit only on request.
9. **UI Requirements:** Dependency finding list, scope and evidence, dynamic-use checklist, transitive impact, security/license context, suppression, and edit preview.
10. **Backend Requirements:** None; advisory/license enrichment is optional.
11. **Technical Considerations:** Handle dev/peer/optional/runtime scopes, scripts, framework conventions, monorepo hoisting, workspace packages, code generation, and lockfile-only records.
12. **Risks:** Removing a CLI/plugin used only by CI, missing string-based imports, package-manager semantic drift, and false savings estimates.
13. **Limitations:** Some dependencies are intentionally declared for consumers or external tooling and require user confirmation.
14. **Recommended Priority:** Post-MVP merged finding.
15. **Should It Be Implemented?:** No as a standalone feature; add dependency-hygiene findings inside DW-02.
16. **Reasoning:** The dependency engine already owns the required evidence and workflow.


### DW-14 — Circular Dependency Detection

1. **Feature Name:** Circular Dependency Detection.
2. **Problem It Solves:** Cycles across modules, packages, layers, services, schemas, or build targets create initialization defects and block independent change.
3. **Target Users:** Developers, leads, architects, and build/platform teams.
4. **Business Value:** Reduces fragility, enables modular delivery, and prevents architecture erosion.
5. **Developer Value:** Shows the minimal cycle, edge evidence, and boundary type so a developer can assess it without graph guesswork.
6. **Why It Is Needed:** Strongly connected component analysis is deterministic, broadly applicable, and a fast proof of graph usefulness.
7. **Real-world Use Cases:** Diagnose import initialization; break a package cycle; find service call loops; validate layer direction; locate a cycle that touches a working-tree change.
8. **Proposed Workflow:** Select dependency granularity → compute strongly connected components → collapse noise → inspect minimal cycle paths and edge evidence → jump to source or explicitly export.
9. **UI Requirements:** Cycle list grouped by granularity and size, highlighted minimal path, edge-type filter, evidence/confidence, source links, and an explicit reminder that a cycle is not automatically a defect.
10. **Backend Requirements:** None.
11. **Technical Considerations:** Distinguish type-only/build/test/runtime edges, choose stable cycle representatives, avoid enumerating exponential paths, and compute incrementally.
12. **Risks:** Flooding users with low-value file cycles, treating every cycle equally, and suggesting invalid dependency inversion.
13. **Limitations:** A cycle is a structural fact, not automatically a defect; teams may explicitly allow bounded cycles.
14. **Recommended Priority:** MVP merged finding.
15. **Should It Be Implemented?:** No as a standalone feature; include the first deterministic finding in DW-02.
16. **Reasoning:** Cycle detection is a fast, explainable proof of graph value.


### DW-15 — AI Project Onboarding Assistant

1. **Feature Name:** AI Project Onboarding Assistant.
2. **Problem It Solves:** New contributors do not know the system vocabulary, entry points, architecture, development workflows, or safe first-change path.
3. **Target Users:** Beginners, interns, freshers, new joiners, contractors, and experienced developers entering unfamiliar systems.
4. **Business Value:** Reduces onboarding time and senior-engineer interruption while improving first-change safety.
5. **Developer Value:** Produces a tailored, source-cited tour instead of generic repository summarization.
6. **Why It Is Needed:** Onboarding is the clearest adoption wedge and can combine discovery, diagrams, cited Q&A, detected tests, and repository documentation.
7. **Real-world Use Cases:** “How does a request reach storage?”; learn the detected build/test workflow; find a beginner-sized module; understand terminology; trace a bounded user flow.
8. **Proposed Workflow:** Choose onboarding goal and depth → review scan/AI data scope → generate deterministic project outline → AI composes a cited tour → user follows checkpoints and asks questions.
9. **UI Requirements:** Welcome view or walkthrough contribution, goal picker, progressive tour cards, architecture checkpoints, glossary, source links, completion state, and “show evidence” on every claim.
10. **Backend Requirements:** None for deterministic onboarding; AI requires an approved VS Code model or configured enterprise provider.
11. **Technical Considerations:** Ground every section in graph retrieval, adapt to model availability and token budget, cache only approved metadata, and make tours refreshable when evidence changes.
12. **Risks:** Hallucinated architecture, overly long tours, exposing sensitive code, infantilizing experienced users, and stale onboarding documents.
13. **Limitations:** It cannot replace team-specific business context, access onboarding, or mentorship; unsupported areas must be named.
14. **Recommended Priority:** MVP merged mode.
15. **Should It Be Implemented?:** No as a separate assistant or wizard; include guided onboarding in Explorer and Q&A.
16. **Reasoning:** One guided mode avoids duplicating navigation, retrieval, and AI.


### DW-16 — AI Codebase Q&A Assistant

1. **Feature Name:** AI Codebase Q&A Assistant.
2. **Problem It Solves:** General AI chat lacks a reliable, current, scoped model of the repository and often returns uncited or invented explanations.
3. **Target Users:** All developer personas, architects, security users, and managers asking bounded evidence questions.
4. **Business Value:** Reduces search and interruption time and makes codebase knowledge available without centralizing it in a few people.
5. **Developer Value:** Answers architecture, flow, test, dependency, and finding questions with file/range citations and uncertainty; ownership questions remain unsupported until the later DW-23 enrichment exists.
6. **Why It Is Needed:** Natural-language access is valuable, but only as a view over the evidence graph and source retrieval.
7. **Real-world Use Cases:** Find request validation; explain a module; list consumers of an API; identify tests for a change; compare two components; explain a finding.
8. **Proposed Workflow:** Ask or select context → classify intent → retrieve bounded graph/source evidence → show context preview when data leaves the machine → stream answer → validate citations → collect feedback.
9. **UI Requirements:** VS Code chat participant/tool where available plus workbench fallback, cited answers, source peek, scope/model/privacy indicators, cancel, retry, “insufficient evidence,” and deterministic query links.
10. **Backend Requirements:** Optional; support VS Code Language Model API, enterprise gateway, or approved provider. Local graph query remains available without AI.
11. **Technical Considerations:** Treat repository text as untrusted data, separate instructions from evidence, enforce token/data budgets, validate citation ranges, route by model capability, and evaluate groundedness.
12. **Risks:** Prompt injection, source exfiltration, hallucination, model/provider churn, cost, latency, quota limits, and misplaced trust.
13. **Limitations:** Answers are explanations, not proof; unsupported or dynamic behavior may remain unknown, and model availability is not guaranteed.
14. **Recommended Priority:** MVP.
15. **Should It Be Implemented?:** Yes — MVP core.
16. **Reasoning:** Cited Q&A is a compelling interface when deterministic evidence remains authoritative.


### DW-17 — Change Impact Analyzer

1. **Feature Name:** Change Impact Analyzer.
2. **Problem It Solves:** Developers and reviewers miss downstream callers, contracts, tests, data flows, owners, deployments, and architecture rules affected by a change.
3. **Target Users:** Developers, reviewers, leads, architects, QA, security, and release owners.
4. **Business Value:** Reduces escaped regressions and review time and enables smaller, safer changes.
5. **Developer Value:** Provides a ranked, explainable impact cone for a symbol, file, package, or working-tree diff in MVP; commit, branch, and pull-request scopes are later DW-34 enrichment.
6. **Why It Is Needed:** It is the highest-value daily decision workflow built from the dependency graph and local SCM evidence; later adapters can enrich it without changing the core.
7. **Real-world Use Cases:** Assess an API signature change; find consumers before schema migration; identify tests for a bug fix; review an architectural boundary change; locate affected services.
8. **Proposed Workflow:** Select a file, symbol, package, or working-tree diff → map changed entities → traverse typed edges under budgets → correlate available test evidence → classify direct/likely/unknown → review impacted tests and known gaps → explicitly export if requested.
9. **UI Requirements:** MVP includes an impact tree/graph, direct versus inferred paths, evidence/confidence, relevant discovered tests, filters, “why included,” and known gaps. It does not include branch/PR comparison or an AI-generated validation plan.
10. **Backend Requirements:** None for MVP local workspace and working-tree analysis; later hosted PR metadata is optional and permission-scoped under DW-34.
11. **Technical Considerations:** Combine semantic diff with text diff, handle renames/generated files, cap traversal, avoid path explosion, retain snapshot identity, and expose missing coverage.
12. **Risks:** False negatives create overconfidence; false positives create noise; stale indexes, ambiguous symbols, and massive shared dependencies distort results.
13. **Limitations:** It predicts likely impact from available evidence and must never claim complete runtime impact.
14. **Recommended Priority:** MVP.
15. **Should It Be Implemented?:** Yes — MVP core.
16. **Reasoning:** Impact analysis creates recurring daily value beyond a one-time diagram.


### DW-18 — Refactoring Recommendation Engine

1. **Feature Name:** Refactoring Recommendation Engine.
2. **Problem It Solves:** Teams can see debt or coupling but struggle to convert findings into sequenced, verifiable, low-risk improvement plans.
3. **Target Users:** Experienced developers, leads, architects, and modernization teams.
4. **Business Value:** Makes remediation investment actionable and reduces failed or over-broad refactors.
5. **Developer Value:** Suggests bounded structural moves, explains trade-offs, predicts impact, and defines validation steps.
6. **Why It Is Needed:** Analysis should lead to improvement, but recommendations must remain review-first and evidence-backed.
7. **Real-world Use Cases:** Break a cycle; extract a package; move a misplaced dependency; split an oversized component; retire an API; isolate infrastructure code.
8. **Proposed Workflow:** Select finding/component → generate deterministic candidate transformations → optionally use AI to explain alternatives → run impact/test plan → preview edits or create a plan → user applies explicitly.
9. **UI Requirements:** Alternatives with rationale/trade-offs, affected graph, step sequence, risk/confidence, validation plan, diff preview, undo support, and no one-click bulk mutation by default.
10. **Backend Requirements:** None for local plans; AI explanation follows configured provider policy.
11. **Technical Considerations:** Separate recommendation from transformation, use language-native refactoring APIs where possible, make edits atomic, revalidate stale state, and preserve formatting.
12. **Risks:** Superficial “best practice” advice, behavioral changes, enormous diffs, broken generated code, and model-generated unsafe edits.
13. **Limitations:** Cross-language and architectural refactors often require human design, runtime validation, and staged migration.
14. **Recommended Priority:** Later, impact-quality gate.
15. **Should It Be Implemented?:** No for the initial product; add plans before edits only after impact and validation accuracy are proven.
16. **Reasoning:** Premature refactoring automation can damage repositories.


### DW-19 — Architecture Rule Validator

1. **Feature Name:** Architecture Rule Validator.
2. **Problem It Solves:** Intended layer, module, service, ownership, data-access, and dependency rules are documented informally and drift without timely enforcement.
3. **Target Users:** Architects, leads, platform teams, developers, and engineering managers.
4. **Business Value:** Prevents architectural erosion, standardizes governance, and reduces late review/rework.
5. **Developer Value:** Gives immediate, source-linked feedback and a clear route to compliant alternatives or time-bounded exceptions.
6. **Why It Is Needed:** The knowledge graph can validate relationships that ordinary linters cannot express across technologies.
7. **Real-world Use Cases:** Enforce layer direction; prohibit direct database access; restrict service dependencies; require owner/test coverage; validate public API boundaries.
8. **Proposed Workflow:** Choose built-in/team rule pack → preview scope and baseline → evaluate graph → inspect violation path → fix, suppress, or request owned exception with expiry.
9. **UI Requirements:** Problems integration, rule explorer, violation path, rationale/remediation, baseline/new toggle, severity, exception owner/expiry, and policy source.
10. **Backend Requirements:** None for local policies; enterprise policy distribution and exception approval are optional services.
11. **Technical Considerations:** Use declarative versioned rules, stable entity selectors, deterministic evaluation, local baselines, CI-compatible output, and explicit repo-file generation consent.
12. **Risks:** Governance overload, brittle selectors, central rules ignoring context, exception accumulation, and hidden policy changes.
13. **Limitations:** Rules validate observable graph facts; they do not prove design quality or replace architecture review.
14. **Recommended Priority:** Later, team-adoption gate.
15. **Should It Be Implemented?:** No for the initial product; add after graph accuracy and a baseline lifecycle are proven.
16. **Reasoning:** Rules become useful only when findings are trusted and adoptable.


### DW-20 — Documentation Generator

1. **Feature Name:** Documentation Generator.
2. **Problem It Solves:** Architecture, API, module, onboarding, and change documentation becomes stale because it is manually assembled and detached from source evidence.
3. **Target Users:** Developers, leads, architects, technical writers, managers, and support teams.
4. **Business Value:** Reduces documentation effort, improves handoffs, and lowers knowledge concentration.
5. **Developer Value:** Generates current, scoped drafts with citations, diagrams, limitations, and freshness metadata.
6. **Why It Is Needed:** The graph already contains reusable facts; reviewed documentation is a natural automation outcome.
7. **Real-world Use Cases:** Create a system overview; document an API/module; produce an onboarding guide; summarize a branch; generate an architecture decision evidence appendix.
8. **Proposed Workflow:** Choose template/scope/audience → preview included evidence and optional AI context → generate ephemeral draft → review citations/claims → explicitly save to chosen location or copy.
9. **UI Requirements:** Template picker, outline editor, live preview, citation and freshness panel, diagram embeds, diff against prior export, accessibility, and explicit save dialog.
10. **Backend Requirements:** None for deterministic reports; AI prose requires an approved provider.
11. **Technical Considerations:** Keep fact extraction deterministic, validate links, mark generated sections, preserve user-owned sections on regeneration, and support Markdown/HTML/PDF through explicit exporters.
12. **Risks:** Stale or authoritative-looking generated prose, overwriting hand-written docs, copyright/license issues, and context leakage.
13. **Limitations:** Generated documentation is a reviewable draft; business intent and operational knowledge often live outside the repository.
14. **Recommended Priority:** Post-MVP merged export.
15. **Should It Be Implemented?:** No as a standalone subsystem; later generate explicit reviewed reports from Explorer and Q&A.
16. **Reasoning:** Autonomous documentation generation would create stale file noise.


### DW-21 — Test Coverage Analyzer

1. **Feature Name:** Test Coverage Analyzer.
2. **Problem It Solves:** Raw coverage percentages do not show which architecture paths, APIs, risks, or changed behavior lack evidence.
3. **Target Users:** Developers, QA, reviewers, leads, and release engineers.
4. **Business Value:** Improves test investment and release confidence while avoiding blanket test growth.
5. **Developer Value:** Overlays existing coverage on code and graph entities and highlights change-relevant gaps.
6. **Why It Is Needed:** Coverage is most useful when correlated with impact, criticality, ownership, and test selection.
7. **Real-world Use Cases:** Find uncovered changed lines; inspect API path coverage; identify a high-risk untested module; compare branch coverage; trace which tests cover a symbol.
8. **Proposed Workflow:** Import approved report → map paths/source maps → connect test and code entities → overlay graph/impact → inspect gaps → plan validation.
9. **UI Requirements:** Editor decorations where useful, coverage tree, architecture overlay, changed-code filter, test links, branch/line/function distinction, freshness, and import diagnostics.
10. **Backend Requirements:** None; CI report retrieval is an optional scoped integration.
11. **Technical Considerations:** Ingest LCOV, Cobertura, JaCoCo and ecosystem formats through adapters; normalize paths; bind reports to commit/config; never run tests automatically.
12. **Risks:** Treating coverage as quality, stale or mismatched reports, source-map errors, generated-code noise, and management misuse.
13. **Limitations:** Coverage proves execution, not assertions or correctness; dynamic/integration coverage may not map cleanly.
14. **Recommended Priority:** Post-MVP merged evidence.
15. **Should It Be Implemented?:** No as a standalone analyzer; later ingest coverage reports into Impact.
16. **Reasoning:** The extension should enrich existing coverage, not build another test runner.


### DW-22 — Design Pattern Detection

1. **Feature Name:** Design Pattern Detection.
2. **Problem It Solves:** Developers sometimes need vocabulary for recurring structural approaches and deviations in unfamiliar systems.
3. **Target Users:** Beginners, reviewers, architects, and educators.
4. **Business Value:** Low and unvalidated; possible onboarding and review assistance.
5. **Developer Value:** May explain concrete structures such as adapters, repositories, state machines, or dependency injection when evidence is strong.
6. **Why It Is Needed:** It is not required for the core outcome; pattern names are useful only when they clarify consequences or decisions.
7. **Real-world Use Cases:** Identify an explicit state machine; explain a framework adapter boundary; locate inconsistent repository implementations.
8. **Proposed Workflow:** Detect multiple structural signals → label as candidate → show evidence and counter-evidence → user confirms or dismisses → use only in onboarding/refactoring context.
9. **UI Requirements:** No standalone screen; small evidence card in Explorer or recommendations with confidence and “candidate, not fact” wording.
10. **Backend Requirements:** None.
11. **Technical Considerations:** Prefer framework/compiler facts and explicit interfaces over naming heuristics; make rules ecosystem-specific and independently measurable.
12. **Risks:** Subjectivity, cargo-cult advice, false expertise, naming debates, and noisy generic classifications.
13. **Limitations:** Many patterns are implicit, mixed, or context-dependent and do not imply quality.
14. **Recommended Priority:** Removed.
15. **Should It Be Implemented?:** No.
16. **Reasoning:** Subjective pattern labels rarely change a decision and create noise and false expertise.


### DW-23 — Code Ownership Mapping

1. **Feature Name:** Code Ownership Mapping.
2. **Problem It Solves:** Formal ownership, actual contribution knowledge, service responsibility, and review routing often disagree or are absent.
3. **Target Users:** Developers, reviewers, leads, managers, architects, security, and incident responders.
4. **Business Value:** Reduces review routing and incident escalation time and exposes continuity risk.
5. **Developer Value:** Shows declared team/owner, evidence source, recent maintainers, and orphaned areas without ranking individuals.
6. **Why It Is Needed:** Ownership strengthens impact, findings, onboarding, risk, and release workflows.
7. **Real-world Use Cases:** Find a reviewer; locate unowned critical code; map service responsibility; assess maintainer continuity; route a vulnerability.
8. **Proposed Workflow:** Ingest CODEOWNERS/service catalog/team map and optionally Git history → normalize identities → show declared versus inferred ownership → let teams correct or suppress inference.
9. **UI Requirements:** Ownership overlay, declared/inferred distinction, team-first display, source/time window, orphan filters, contact action through approved VS Code/Git-host integration, and privacy notice.
10. **Backend Requirements:** Optional directory/service-catalog/Git-host integration with least-privilege access; local CODEOWNERS works offline.
11. **Technical Considerations:** Resolve aliases carefully, apply recency windows, avoid storing commit content, support team entities, and make privacy/retention configurable.
12. **Risks:** Surveillance, inaccurate attribution, departed users, sensitive identity data, and managers treating commit activity as productivity.
13. **Limitations:** Contribution does not equal ownership or expertise; inferred ownership must never override an authoritative team source silently.
14. **Recommended Priority:** Post-MVP merged metadata.
15. **Should It Be Implemented?:** No as a standalone feature; later enrich Explorer and Impact with team-first ownership.
16. **Reasoning:** Ownership helps routing but must not become individual surveillance.


### DW-24 — Microservice Dependency Mapping

1. **Feature Name:** Microservice Dependency Mapping.
2. **Problem It Solves:** Service calls, events, shared datastores, gateways, deployments, and ownership cross repository and environment boundaries.
3. **Target Users:** Architects, platform engineers, SREs, backend developers, managers, and incident responders.
4. **Business Value:** Improves change coordination, reliability planning, incident response, and decomposition decisions.
5. **Developer Value:** Shows service-level dependencies with declared, inferred, and observed evidence clearly separated.
6. **Why It Is Needed:** Enterprise/cloud-native systems cannot be understood from a single module graph, but this is not required for the individual wedge.
7. **Real-world Use Cases:** Assess a service API change; map shared database risk; trace an event fan-out; plan service retirement; identify an ownership gap.
8. **Proposed Workflow:** Configure approved repository/workspace/service-catalog scope → ingest API/event/IaC/runtime evidence → resolve service identities → explore paths and impact → compare environments/snapshots.
9. **UI Requirements:** C4-like service view, domain/environment/team filters, observed-versus-declared styling, dependency matrix, critical path, source/catalog links, and unknown external boundary.
10. **Backend Requirements:** Optional enterprise metadata service for multi-repository aggregation; single multi-root workspace remains local.
11. **Technical Considerations:** Use stable service IDs, tenant isolation, metadata minimization, snapshot/commit binding, topology conflict handling, and scalable aggregation.
12. **Risks:** Incomplete cross-repo access, stale service catalog, runtime data sensitivity, environment drift, and expensive control-plane scope.
13. **Limitations:** Static analysis cannot prove production topology; multi-repository value depends on enterprise authorization and integration quality.
14. **Recommended Priority:** Later Explorer projection.
15. **Should It Be Implemented?:** No as a standalone feature; later add a service projection without forcing a cloud control plane.
16. **Reasoning:** Cross-repository topology is costly and depends on validated enterprise demand.


### DW-25 — Release Readiness Assessment

1. **Feature Name:** Release Readiness Assessment.
2. **Problem It Solves:** Release evidence is scattered across CI, tests, coverage, security, dependencies, migrations, ownership, documentation, and rollout systems.
3. **Target Users:** Release engineers, leads, QA, security, managers, and service owners.
4. **Business Value:** Reduces missed gates, release coordination time, and unverifiable sign-off.
5. **Developer Value:** Shows exactly which checks passed, failed, are stale, are unknown, or were explicitly waived for one commit/artifact.
6. **Why It Is Needed:** The workbench can correlate evidence, but it must avoid pretending to replace CI or guarantee safety.
7. **Real-world Use Cases:** Review a release candidate; check migration/test/security evidence; verify ownership and rollback plan; export audit evidence; compare two candidates.
8. **Proposed Workflow:** Select commit/build/release → load configured evidence → validate provenance/freshness → display pass/fail/unknown/waived → assign gaps → explicitly export.
9. **UI Requirements:** Commit-bound checklist, evidence links, unknown state, waiver owner/expiry, artifact identity, trend/diff, blocking-policy explanation, and signed export metadata.
10. **Backend Requirements:** Optional CI, artifact, Git-host, security, and deployment connectors; local source readiness works without them.
11. **Technical Considerations:** Use adapter contracts and signed provenance, keep checks policy-driven, distinguish source evidence from artifact/runtime evidence, and support offline imported evidence.
12. **Risks:** False certification, stale credentials, inconsistent CI semantics, policy sprawl, and pressure to mark unknown as pass.
13. **Limitations:** The assessment reports configured evidence; it cannot guarantee defect-free, secure, or successful production behavior.
14. **Recommended Priority:** Later, enterprise evidence gate.
15. **Should It Be Implemented?:** No for the initial product; reconsider after reliable analyzers and CI integrations exist.
16. **Reasoning:** A premature readiness label can be mistaken for certification.


### DW-26 — Technical Health Score Dashboard

1. **Feature Name:** Technical Health Score Dashboard.
2. **Problem It Solves:** Leaders need to prioritize engineering-system weaknesses and see movement over time.
3. **Target Users:** Leads, architects, engineering managers, platform teams, and developers.
4. **Business Value:** Supports investment and governance decisions when signals are explainable.
5. **Developer Value:** Provides one place to see maintainability, architecture, dependency, security, test, documentation, ownership, and release dimensions.
6. **Why It Is Needed:** The underlying need is valid; the requested singular score is not.
7. **Real-world Use Cases:** Review architecture drift trend; prioritize security/debt work; find deteriorating test evidence; compare a repository to its own baseline.
8. **Proposed Workflow:** Open Health → select time/scope → inspect dimension status and change → drill to findings/evidence → create owned actions → review later.
9. **UI Requirements:** Dimensional cards, ranges and distributions, baseline delta, evidence coverage, confidence, unknowns, filters, and no cross-team leaderboard.
10. **Backend Requirements:** None for local snapshots; approved team trends require metadata-only shared snapshots.
11. **Technical Considerations:** Publish formulas and inputs, separate absence of evidence from poor health, retain versioned baselines, and prohibit individual activity metrics.
12. **Risks:** Gaming, false precision, blame, inappropriate repository comparisons, and management surveillance.
13. **Limitations:** Dimensions are decision aids, not universal quality measurements; business context stays human-owned.
14. **Recommended Priority:** Removed.
15. **Should It Be Implemented?:** No.
16. **Reasoning:** A universal score invites gaming, surveillance, and false precision; later health must remain dimensional.


### DW-27 — Risk Assessment Dashboard

1. **Feature Name:** Risk Assessment Dashboard.
2. **Problem It Solves:** Findings become an unowned backlog without impact context, mitigation, exceptions, or a coherent risk view.
3. **Target Users:** Leads, architects, managers, security, platform, and release owners.
4. **Business Value:** Improves risk ownership and remediation prioritization and makes known unknowns visible.
5. **Developer Value:** Connects each risk to exact findings, affected assets, owners, changes, and mitigation work.
6. **Why It Is Needed:** Risk management is valuable, but a second dashboard would fragment the product.
7. **Real-world Use Cases:** Track a critical dependency risk; own architecture exceptions; plan migration risk; review release blockers; age out suppressions.
8. **Proposed Workflow:** Correlate findings into a risk candidate → human reviews likelihood/impact → assign owner/mitigation/expiry → track evidence and residual risk.
9. **UI Requirements:** Risk register inside Health/Findings, evidence drill-down, confidence, affected assets, owner, due date, mitigation, exception, status history, and filters.
10. **Backend Requirements:** Optional team synchronization and approval; local risk records remain private.
11. **Technical Considerations:** Avoid fake probability math, keep source findings immutable, version human judgments, and support policy-defined risk classes.
12. **Risks:** False numerical precision, duplicate governance, stale ownership, confidential risk exposure, and surveillance.
13. **Limitations:** The extension supplies engineering evidence; business impact and risk acceptance require accountable humans.
14. **Recommended Priority:** Later merged register.
15. **Should It Be Implemented?:** No as a standalone dashboard; later add a risk register inside Findings and Health.
16. **Reasoning:** Risk is a human judgment over evidence, not another dashboard product.


### DW-28 — Migration and Modernization Recommendations

1. **Feature Name:** Migration and Modernization Recommendations.
2. **Problem It Solves:** Legacy upgrades, framework migrations, service decomposition, language transitions, and cloud modernization fail when dependencies and sequencing are poorly understood.
3. **Target Users:** Architects, senior developers, platform teams, managers, and modernization programs.
4. **Business Value:** Improves planning accuracy, reduces migration rework, and makes staged investment and rollback clearer.
5. **Developer Value:** Produces evidence-backed migration waves, compatibility constraints, affected tests/contracts, and alternative strategies.
6. **Why It Is Needed:** Modernization is high-value enterprise work, but it depends on accurate topology and current vendor compatibility evidence.
7. **Real-world Use Cases:** Upgrade a framework; split a monolith; replace an obsolete dependency; migrate a database; move a service to a new runtime.
8. **Proposed Workflow:** Select target/outcome → inventory constraints → load signed/current compatibility knowledge → model options and waves → run impact/test/rollback analysis → export reviewed plan.
9. **UI Requirements:** Current/target comparison, constraint list, dependency waves, blockers, risk/confidence, alternatives, validation/rollback checklist, and evidence links.
10. **Backend Requirements:** Optional curated lifecycle/compatibility feed and team planning; repository facts remain local by default.
11. **Technical Considerations:** Version all external knowledge, distinguish mechanical from semantic changes, model coexistence stages, and never execute migration tools automatically.
12. **Risks:** Stale vendor guidance, oversimplified plans, hidden organizational dependencies, lock-in, and dangerously broad generated patches.
13. **Limitations:** Recommendations are planning input, not a guaranteed or autonomous migration; runtime and business validation remain external.
14. **Recommended Priority:** Later, explicit discovery gate.
15. **Should It Be Implemented?:** No for the initial product; reconsider only after customer demand and mature impact evidence.
16. **Reasoning:** Modernization advice goes stale and cannot safely be generic.


### DW-29 — Repository Health Insights

1. **Feature Name:** Repository Health Insights.
2. **Problem It Solves:** Teams lack one evidence-backed view of architecture, maintainability, dependencies, security, tests, ownership, documentation, and operational gaps.
3. **Target Users:** Developers, leads, architects, managers, platform, and security teams.
4. **Business Value:** Enables prioritization and trend-based governance without requiring users to inspect every analyzer separately.
5. **Developer Value:** Shows the most actionable dimension changes and links directly to evidence and remediation workflows.
6. **Why It Is Needed:** Correlated insights are more useful than independent scanner totals, provided the product avoids one opaque score.
7. **Real-world Use Cases:** Review a repository baseline; see new architecture violations; identify dependency hygiene gaps; track waiver aging; prepare a technical review.
8. **Proposed Workflow:** Select scope/snapshot → view dimensional signals and evidence coverage → drill to findings/risks → assign action or baseline → compare over time.
9. **UI Requirements:** Unified Health view, dimension cards, trend and baseline, evidence/unknown coverage, top changes, finding/risk drill-down, and export preview.
10. **Backend Requirements:** None locally; team/portfolio trends require approved metadata-only synchronization.
11. **Technical Considerations:** Correlate without double-counting, expose formulas, version rules, separate absolute state from change, and prevent individual-level analytics.
12. **Risks:** Dashboard bloat, vanity metrics, duplicated findings, inappropriate comparison, and low-signal alerts.
13. **Limitations:** It summarizes configured evidence and cannot measure every socio-technical or business concern.
14. **Recommended Priority:** Later, analyzer-maturity gate.
15. **Should It Be Implemented?:** No for the initial product; add one dimensional view only after reliable signals exist.
16. **Reasoning:** An early health page would be a vanity dashboard.


### DW-30 — AI-Powered Root Cause Analysis

1. **Feature Name:** AI-Powered Root Cause Analysis.
2. **Problem It Solves:** Failures require manual correlation of logs, traces, tests, topology, dependencies, recent changes, owners, and known findings.
3. **Target Users:** Developers, SREs, platform engineers, QA, incident responders, and service owners.
4. **Business Value:** Can reduce mean time to a verified cause and coordinate faster mitigation.
5. **Developer Value:** Produces ranked hypotheses with supporting and contradicting evidence plus the next cheapest validation steps.
6. **Why It Is Needed:** It is not needed for the chosen product wedge; any incident-analysis value is already served by specialist observability products with better runtime context.
7. **Real-world Use Cases:** Diagnose a failing test after a branch change; explain a production error trace; correlate latency regression; locate a broken dependency or schema contract.
8. **Proposed Workflow:** Select failure or explicitly import redacted data → bind time/commit/environment → correlate graph/change/findings → AI ranks hypotheses → user runs chosen checks → mark verified cause.
9. **UI Requirements:** Incident timeline, redaction/context preview, hypothesis cards, supporting/contradicting evidence, confidence, next checks, source/topology links, and explicit verified/unverified state.
10. **Backend Requirements:** Optional approved model and observability/test/Git connectors with least-privilege, scoped, revocable credentials.
11. **Technical Considerations:** Normalize OpenTelemetry and test/log formats, redact secrets/PII, preserve timestamps/provenance, prevent prompt injection, and separate correlation from causation.
12. **Risks:** Sensitive logs, hallucinated causes, automation bias, connector cost, missing environment context, and incident-data retention.
13. **Limitations:** Results remain hypotheses until a human validates them; incomplete telemetry may make the correct cause unknowable.
14. **Recommended Priority:** Removed.
15. **Should It Be Implemented?:** No.
16. **Reasoning:** It falls outside the core wedge, duplicates observability products, and carries high privacy and hallucination risk.


### DW-31 — Project Discovery and Capability Profile

1. **Feature Name:** Project Discovery and Capability Profile.
2. **Problem It Solves:** The extension must understand repository boundaries, technologies, generated/vendor areas, manifests, workspace topology, and available analyzers without executing untrusted code.
3. **Target Users:** Every user and every downstream engine.
4. **Business Value:** Enables fast first value, broad ecosystem coverage, honest product claims, and lower support cost.
5. **Developer Value:** Explains what was detected, what will be scanned, what is excluded, and which analyses are full, partial, structural-only, or unavailable.
6. **Why It Is Needed:** “Technology-agnostic” is only credible through capability detection and graceful degradation.
7. **Real-world Use Cases:** Detect a JavaScript/Python monorepo; identify Terraform and Kubernetes; scope a legacy polyglot repository; explain why semantic impact is unavailable for one module.
8. **Proposed Workflow:** Read safe workspace metadata → detect roots/components/tooling → apply ignore/sensitivity policy → propose scan plan and budget → user adjusts scope → activate adapters.
9. **UI Requirements:** First-run capability summary, scan-scope tree, exclusions, trust/network/AI status, expected depth per technology, estimated cost, and rescan diagnostics.
10. **Backend Requirements:** None.
11. **Technical Considerations:** Use VS Code URI/filesystem APIs, support multi-root/remote/virtual schemes, never load project modules or executable config, bound file discovery, and fingerprint detectors.
12. **Risks:** Mis-detection, huge workspaces, symlink escape, generated/vendor noise, hidden sensitive folders, and slow activation.
13. **Limitations:** Detection describes evidence, not complete runtime behavior; users may need to correct boundaries.
14. **Recommended Priority:** MVP.
15. **Should It Be Implemented?:** Yes — MVP core.
16. **Reasoning:** Safe discovery enables scope, honest capability claims, and progressive results.


### DW-32 — Evidence, Provenance, Confidence, and Freshness

1. **Feature Name:** Evidence, Provenance, Confidence, and Freshness System.
2. **Problem It Solves:** Users cannot trust analysis or AI outputs if facts, inferences, source locations, analyzer versions, completeness, and staleness are hidden.
3. **Target Users:** Every persona; especially reviewers, architects, security, release, and support.
4. **Business Value:** Differentiates the product through trust, reduces false-action cost, and supports enterprise audit.
5. **Developer Value:** Answers “why do you believe this?”, “how current is it?”, “what contradicts it?”, and “what could be missing?”
6. **Why It Is Needed:** It is the cross-cutting contract that makes every requested feature safe and internally consistent.
7. **Real-world Use Cases:** Inspect an inferred call edge; verify an AI citation; see that coverage belongs to another commit; compare static and runtime topology; diagnose an adapter.
8. **Proposed Workflow:** Analyzer emits evidence envelope → validator checks URI/range/schema → graph stores fact and confidence basis → projections preserve it → user opens Evidence Inspector or reports feedback.
9. **UI Requirements:** Universal evidence drawer, source peek, observed/inferred/declared style, confidence basis, freshness/commit, analyzer/version, contradictory evidence, coverage gaps, and report-issue action.
10. **Backend Requirements:** None; shared reports may include content-free provenance and signed snapshot identity.
11. **Technical Considerations:** Confidence must be rule-derived rather than model self-rating; use immutable evidence IDs, commit/index generation, schema version, source hashes, and privacy classes.
12. **Risks:** UI clutter, arbitrary confidence numbers, stale-range navigation, graph storage growth, and users ignoring caveats.
13. **Limitations:** Provenance explains the basis; it does not guarantee the conclusion is correct or complete.
14. **Recommended Priority:** MVP.
15. **Should It Be Implemented?:** Yes — MVP core.
16. **Reasoning:** Evidence and freshness are the central trust boundary for every output.


### DW-33 — Architecture Snapshot and Drift Diff

1. **Feature Name:** Architecture Snapshot and Drift Diff.
2. **Problem It Solves:** Teams cannot easily see how dependencies, boundaries, services, APIs, data, events, or risks changed between commits and intended baselines.
3. **Target Users:** Developers, reviewers, architects, leads, managers, and platform teams.
4. **Business Value:** Detects architecture erosion early and makes review/modernization progress measurable.
5. **Developer Value:** Shows only material graph changes with evidence instead of forcing a complete diagram comparison.
6. **Why It Is Needed:** A living workbench must explain change over time, not just current state.
7. **Real-world Use Cases:** Find a new cross-layer dependency; compare a refactor; detect an added external service; review API/data topology drift; validate a decomposition milestone.
8. **Proposed Workflow:** Choose current and baseline snapshot/branch → compute normalized graph diff → classify additions/removals/changes → apply rules → inspect paths → accept baseline or remediate.
9. **UI Requirements:** Added/removed/changed styling, scope filters, impact overlay, rule violations, before/after evidence, timeline, and explicit baseline action.
10. **Backend Requirements:** None for local Git snapshots; team baseline sharing is optional.
11. **Technical Considerations:** Preserve stable entity identity across renames, bind snapshots to analyzer/schema version, distinguish reindex noise, and store compact metadata deltas.
12. **Risks:** Identity churn, baseline misuse, expensive historical indexing, noisy diffs after analyzer upgrades, and implicit policy.
13. **Limitations:** A structural diff does not explain intent; users must link approved decisions or policies.
14. **Recommended Priority:** Post-MVP merged mode.
15. **Should It Be Implemented?:** No as a standalone feature; later add snapshot drift as a Change Impact mode.
16. **Reasoning:** Diff is useful when it reuses stable graph identity and the existing impact workflow.


### DW-34 — Branch and Pull-Request Intelligence

1. **Feature Name:** Branch and Pull-Request Intelligence.
2. **Problem It Solves:** Full-repository analysis overwhelms daily review; developers need a concise, current view of what one change affects and violates.
3. **Target Users:** Authors, reviewers, leads, QA, security, and release engineers.
4. **Business Value:** Reduces review time and escaped defects and integrates workbench value into the daily merge workflow.
5. **Developer Value:** Correlates a diff with impact, tests, findings, ownership, contracts, diagrams, and documentation drift.
6. **Why It Is Needed:** Differential intelligence is more actionable and adoptable than periodic dashboards.
7. **Real-world Use Cases:** Review a local branch; summarize PR architecture changes; flag new vulnerability/cycle; suggest reviewers/tests; generate a change note.
8. **Proposed Workflow:** Select working tree/commit range/PR → semantic diff → run incremental impact and policy analyses → inspect only new/resolved/changed evidence → optionally publish reviewed summary.
9. **UI Requirements:** Change-centric overview, file/symbol grouping, impact paths, suggested tests/owners, new findings, architecture diff, comment/export preview, and stale-index warning.
10. **Backend Requirements:** None for local Git; Git-host PR comments/status require explicit authorization and publish confirmation.
11. **Technical Considerations:** Handle renames, rebases, merge bases, generated files, partial checkouts, uncommitted work, and API rate limits; never post automatically.
12. **Risks:** Noisy review comments, leaking local findings, incorrect merge base, duplicate CI output, and social pressure from inferred ownership.
13. **Limitations:** Cross-repository impacts and runtime behavior may be unknown; published results reflect the analyzed commit only.
14. **Recommended Priority:** Post-MVP merged scope.
15. **Should It Be Implemented?:** No as a standalone feature; later add branch and PR scopes to Change Impact.
16. **Reasoning:** A diff scope should not become another product surface.


### DW-35 — Test Selection and Validation Planner

1. **Feature Name:** Test Selection and Validation Planner.
2. **Problem It Solves:** Teams either run too few tests and miss regressions or run everything and waste time because change-to-test relationships are unclear.
3. **Target Users:** Developers, QA, reviewers, build/platform teams, and release engineers.
4. **Business Value:** Shortens feedback cycles while preserving confidence and makes validation expectations explicit.
5. **Developer Value:** Recommends tests and manual checks with dependency paths, coverage/history evidence, and an uncertainty tier.
6. **Why It Is Needed:** Impact without a practical verification plan stops before the user’s real decision.
7. **Real-world Use Cases:** Select unit/integration tests for a change; identify missing tests; plan schema/API validation; reduce monorepo CI scope; verify a refactor.
8. **Proposed Workflow:** Start from change impact → map test references/coverage/historical outcomes/configured suites → rank required/recommended/unknown → user chooses → run only through explicit VS Code task/test action.
9. **UI Requirements:** Validation checklist, test IDs/locations, “why selected,” confidence, estimated scope based on real history only, missing-coverage warning, and explicit run/copy command.
10. **Backend Requirements:** None; CI history is an optional metadata integration.
11. **Technical Considerations:** Integrate VS Code Testing API and report formats, avoid inventing shell commands, model flaky/quarantined tests, bind history to branches/configuration, and keep selection deterministic.
12. **Risks:** False negatives, stale coverage, flaky history, configuration-specific suites, and users mistaking recommendations for guarantees.
13. **Limitations:** Recommended selection cannot prove equivalence to a full suite; policy may still require broader tests.
14. **Recommended Priority:** Post-MVP merged plan.
15. **Should It Be Implemented?:** No as a standalone feature; later include evidence-linked validation inside Impact.
16. **Reasoning:** Test planning is the action layer of impact, not a separate destination.


### DW-36 — Policy Packs, Baselines, and Exceptions

1. **Feature Name:** Policy Packs, Baselines, and Exceptions.
2. **Problem It Solves:** Enterprise adoption fails when rules cannot be introduced gradually, versioned centrally, justified, or waived with accountability.
3. **Target Users:** Architects, platform/security teams, leads, enterprise admins, developers, and auditors.
4. **Business Value:** Enables scalable governance and legacy adoption without blocking all delivery on day one.
5. **Developer Value:** Makes active policy visible, separates new from accepted debt, and gives a transparent exception path.
6. **Why It Is Needed:** Architecture, security, health, and readiness need one governance substrate rather than separate suppression systems.
7. **Real-world Use Cases:** Baseline existing cycles; enforce dependency rules on new code; distribute security policy; approve a temporary exception; expire waivers.
8. **Proposed Workflow:** Select signed local/team pack → preview rules/scope/change → evaluate → baseline existing findings if authorized → request/approve exception with owner/rationale/expiry → audit changes.
9. **UI Requirements:** Policy source/version, rule explorer, effective scope, baseline/new toggle, change preview, exception workflow, expiry queue, and offline pack status.
10. **Backend Requirements:** Optional enterprise policy distribution, RBAC, approvals, and content-free audit; local packs require no service.
11. **Technical Considerations:** Sign/version packs, deterministic selectors, schema compatibility, policy precedence, managed settings, offline bundles, and CI parity.
12. **Risks:** Rule sprawl, central overreach, permanent baselines, hidden updates, conflicting policies, and bureaucratic exception flows.
13. **Limitations:** Policy cannot replace engineering judgment; local-only teams need a simple path without enterprise infrastructure.
14. **Recommended Priority:** Later, enterprise gate.
15. **Should It Be Implemented?:** No for the initial product; add after local findings and real team governance demand are proven.
16. **Reasoning:** Policy distribution before trusted findings would create bureaucracy without value.


### DW-37 — Analyzer Integration SDK and Interchange Hub

1. **Feature Name:** Analyzer Integration SDK and Interchange Hub.
2. **Problem It Solves:** No single team can implement deep, current semantics for every language, framework, scanner, test system, and platform.
3. **Target Users:** Internal feature teams first; later language/tool vendors, enterprise platform teams, and trusted third-party authors.
4. **Business Value:** Expands ecosystem coverage, reduces duplicated integrations, and supports enterprise customization.
5. **Developer Value:** Brings existing tool evidence into one workbench while preserving provenance and familiar workflows.
6. **Why It Is Needed:** Adapter-based extensibility is the only honest path to technology breadth, but a premature public API would freeze weak contracts.
7. **Real-world Use Cases:** MVP uses the contract for the selected bundled language/framework adapters. Later approvals may add SARIF, service-catalog, IaC, diagram, or policy adapters.
8. **Proposed Workflow:** MVP adapter declares capabilities, inputs, trust, and resource needs → runs conformance tests → emits versioned evidence/findings → passes validation and worker limits → exposes support status. External registration/distribution is deferred.
9. **UI Requirements:** No standalone MVP UI; DW-31 shows bundled adapter support and diagnostics. A later enterprise integration manager may add source/version/trust/data-use disclosure, enable/disable, resource use, and conflict resolution.
10. **Backend Requirements:** None for MVP bundled adapters; later signed enterprise/private distribution is optional.
11. **Technical Considerations:** Versioned schemas, capability negotiation, worker isolation, time/memory/output budgets, deterministic IDs, contract tests, signing, and deprecation policy.
12. **Risks:** Malicious analyzers, schema lock-in, quality variance, dependency conflicts, performance degradation, and support explosion.
13. **Limitations:** Public extensibility is deferred until at least several internal adapters prove stable contracts and security boundaries.
14. **Recommended Priority:** MVP internal contract; public SDK removed.
15. **Should It Be Implemented?:** No as a public feature; keep only a small internal adapter contract.
16. **Reasoning:** Internal adapters enable technology breadth, while a public marketplace would freeze immature contracts.


### DW-38 — Data and Privacy Control Center

1. **Feature Name:** Data and Privacy Control Center.
2. **Problem It Solves:** Users and enterprises need to know what is read, indexed, stored, sent, retained, shared, executed, and written—and to control each boundary independently.
3. **Target Users:** Every user, enterprise admins, privacy/security teams, and support.
4. **Business Value:** Enables trust, regulated adoption, lower incident risk, and truthful provider governance.
5. **Developer Value:** Makes exclusions, cache use, provider context, telemetry, permissions, and cleanup visible and reversible.
6. **Why It Is Needed:** Privacy cannot be buried in settings because source code, secrets, logs, AI, and remote workspaces create material boundaries.
7. **Real-world Use Cases:** Enforce local-only mode; exclude sensitive folders; preview AI context; clear one workspace index; revoke provider token; inspect egress history; export a data inventory.
8. **Proposed Workflow:** Open Control Center → inspect current trust/scope/storage/network/AI/telemetry/write/execute status → change one explicit control → preview consequences → confirm → receive auditable local receipt.
9. **UI Requirements:** Native settings plus dedicated trust summary, scope/exclusion tree, storage size/TTL, provider/region/retention, context preview, telemetry state, clear/export/revoke actions, and admin-policy explanation.
10. **Backend Requirements:** None locally; enterprise policy and consent/audit metadata service is optional and must not receive source by default.
11. **Technical Considerations:** Separate Workspace Trust, scan consent, source egress, telemetry, execution, and write consent; use SecretStorage; respect managed settings and VS Code telemetry APIs.
12. **Risks:** Consent fatigue, misleading toggles, policy conflicts, hidden transitive provider behavior, accidental secret indexing, and incomplete deletion.
13. **Limitations:** The control center can govern the extension and declared providers, not other extensions, project tools, or external systems.
14. **Recommended Priority:** MVP.
15. **Should It Be Implemented?:** Yes — MVP core.
16. **Reasoning:** Scanning, storage, AI, egress, execution, and writes require separate visible controls.

## 8. Diagram decisions

All diagrams use one evidence-graph projection, layout, render, and export engine. Every edge distinguishes declared, statically observed, inferred, imported, and runtime-observed evidence. Every canvas has a keyboard-accessible tree/table alternative.

| Diagram | Decision | Users and value | Generation and data | Visualization, interaction, and scale |
| --- | --- | --- | --- | --- |
| High-level architecture | MVP keep | Newcomers, leads, architects; establishes system vocabulary and boundaries | Discovery, manifests, components, dependencies, APIs/data only when reliable | C4-like clustered view; search, filter, drill, evidence; domain clusters for scale |
| Module | MVP keep | Developers/architects; shows logical boundaries and coupling | Packages, namespaces, build targets, imports/exports, folders | Directed graph or matrix; cycles/public interfaces; collapse packages |
| Dependency | MVP keep | Developers/security; traces inbound, outbound, transitive paths and cycles | Manifests, lockfiles, imports, calls, build metadata | Path modes, shortest path, typed edges, SCC grouping, external dependency collapse |
| Folder structure | Merge into Explorer | Newcomers; quick physical orientation | Workspace filesystem, ignores, file classification, package boundaries | Lazy tree/treemap with logical overlay; generated/vendor collapsed |
| Low-level architecture | Later | Developers; bounded internal implementation view | Symbols, types, calls, inheritance/composition | Only for one selected component; lazy symbol expansion |
| Service | Later | Architects/SREs; deployable topology | Service catalog, APIs, events, data, IaC, optional runtime evidence | Domain/environment clusters; observed and declared edges distinct |
| API flow | Later | Backend/frontend/QA/security; request/consumer path | Specs, routes, controllers, middleware, clients, tests | One operation or bounded tag; request/sequence view with source links |
| Database relationship | Later | Backend/data; schema and access impact | DDL, migrations, ORM, queries, constraints | ER plus reader/writer overlay; schema scope and column-on-zoom |
| Event-driven architecture | Later | Backend/SRE; async producer/consumer/failure path | Broker config, schemas, producers/consumers, IaC, optional traces | Event/topic topology; retry/DLQ and version annotations |
| Routing | Later | Frontend/backend; URL-to-handler ownership and conflicts | File routes, router declarations, guards, gateway config, tests | Searchable route tree/flow; group by application/domain |
| State flow | Later | Developers/QA; lifecycle, guards, effects | Explicit state machines, reducers, workflow handlers, tests | One selected machine; state transition graph and evidence |
| Sequence | Later | Developers/QA/incidents; one end-to-end scenario | Selected graph paths, tests, optional runtime trace | Bounded lifelines; observed solid/inferred dashed; loops collapsed |

Diagram export is always explicit, previewed, and user-selected. Internal layouts and snapshots stay in extension-owned storage.

## 9. Canonical user workflow

~~~mermaid
flowchart TD
    A[User opens Workbench] --> B[Safe structural discovery]
    B --> C[Show trust, scope, excludes, capability, cost, and local-only defaults]
    C --> D{User starts analysis?}
    D -- No --> E[Remain idle]
    D -- Yes --> F[Incremental evidence and dependency graph]
    F --> G[Progressive Architecture Explorer]
    G --> H{User goal}
    H --> I[Guided onboarding]
    H --> J[Cited question]
    H --> K[Select file, symbol, package, or working-tree diff]
    I --> L[Evidence and source navigation]
    J --> L
    K --> M[Change impact: direct, likely, unknown, and discovered tests]
    M --> N[Review impacted tests and known gaps]
    N --> O{Explicit export requested?}
    O -- No --> P[No project change]
    O -- Yes --> Q[Preview exact export]
    Q --> R{User confirms?}
    R -- No --> P
    R -- Yes --> S[Save only the approved export]
~~~

The deterministic graph query is authoritative. If AI is unavailable, denied, or off, Explorer, dependency paths, impact, findings, and structured summaries continue to work.

## 10. Minimal user experience

One Developer Workbench Activity Bar container:

- **Overview:** scope, index freshness, capabilities, recent task, high-signal next action.
- **Explorer:** native tree/search entry to structure, architecture, modules, and dependencies.
- **Findings:** unified current findings synchronized with VS Code Problems; baselines, exceptions, and suppression are deferred to DW-36.

Open rich editor panels only on demand:

- Architecture diagram;
- Change Impact;
- Evidence Inspector;
- compact Data and Privacy controls where native settings are insufficient.

Use VS Code Chat for Q&A when available, with a native workbench query fallback. Use native Command Palette, Quick Pick, Progress, Problems, Testing, SCM, diff editor, settings, authentication, SecretStorage, and URI navigation before a webview.

Webviews are limited to graphs/reports that native APIs cannot express. They use restrictive Content Security Policy, packaged resources only, sanitized labels, typed bounded messages, no credentials/filesystem/process/network access, and an accessible tree/table equivalent.

## 11. Target architecture

~~~mermaid
flowchart TB
    UI[VS Code commands, views, Problems, SCM, Testing, Chat]
    Host[Thin workspace extension host]
    Session[Workspace session and use cases]
    Discovery[Safe discovery and capability registry]
    Workers[Cancellable analysis workers]
    Graph[Transactional local evidence graph]
    Diagram[Diagram projection and layout]
    AI[Optional policy-controlled AI gateway]
    Firebase[Dormant historical Firebase config]

    UI <--> Host
    Host --> Session
    Session --> Discovery
    Session <--> Workers
    Workers <--> Graph
    Session <--> Graph
    Graph --> Diagram
    Session -. explicit source-egress consent .-> AI
    Firebase -. no MVP dependency or active wiring .-> Host
~~~

### Architecture rules

- Extension activation registers lightweight contributions and performs no full scan.
- CPU-heavy parsing, graph updates, analysis, and layout run outside the extension-host event loop.
- Workspace files, config, imported reports, logs, and model output are hostile input.
- Default analysis does not run package managers, compilers, project modules, scripts, tasks, tests, generators, containers, or infrastructure tools.
- Every fact has source URI/range where available, extractor/version, observation type, confidence basis, freshness, snapshot, and privacy class.
- Graph generations are transactional; stale jobs cannot overwrite newer state.
- The extension host sends bounded projections to webviews, never the full graph or credentials.
- AI receives only policy-approved, previewed, bounded evidence and cannot authorize actions.
- Errors are structured and content-free. Telemetry contains no source, paths, prompts, findings, secrets, or identity.
- Optional services never become required for local analysis.

### Target folder structure

~~~text
apps/
  extension/
    src/
      activation/
      commands/
      views/
      trust/
      vscode-adapters/
      webview-host/
  webview/
    src/
      architecture/
      impact/
      evidence/
      privacy/
packages/
  core-domain/
  application/
  workspace-discovery/
  indexing/
  analyzer-sdk/
  analyzers/
  analysis-engine/
  knowledge-graph/
  storage/
  diagram-engine/
  ai-gateway/
  integrations/
  ui-protocol/
  observability/
  shared/
tests/
  repositories/
  golden-graphs/
  golden-diagrams/
  extension-host/
  webview/
  performance/
  security/
~~~

Feature use cases own commands, contracts, projections, errors, and tests. Core domain imports no VS Code, Node, UI, parser, storage, or provider module. Analyzers emit evidence contracts and never write storage/UI directly. There is no universal command runtime, global reducer, or “services/utils” dumping ground.

### Storage and state

- Workspace-specific graph/index lives in VS Code extension storage, not the repository.
- Credentials live only in SecretStorage.
- Small UI preferences live in workspaceState/globalState.
- Use a maintained transactional embedded store behind a port; do not build another custom database.
- Corruption falls back to the last verified generation or clear/reindex.
- Quotas, TTL/LRU cleanup, storage inventory, clear-workspace, and clear-all are product controls.
- A web/virtual build may offer reduced structural capability with IndexedDB/URI-safe storage.
- Source remains in the workspace; persist normalized facts and minimal snippets only when policy permits.

### Analyzer model

Each analyzer declares supported ecosystems/versions, inputs, outputs, trust/network/execute needs, incremental invalidation, cost and resource budgets, confidence rules, privacy class, dependencies, fixtures, and degradation behavior.

A small internal adapter contract is MVP. A public SDK, runtime package downloader, and analyzer marketplace are removed from the committed roadmap.

## 12. Privacy, security, and non-invasive rules

The extension must:

- create no cache, database, log, temporary file, report, diagram, config, policy, baseline, documentation, or generated source inside a repository without explicit approval;
- modify no source, manifest, lockfile, workspace setting, task, launch config, CI file, Git state, or documentation automatically;
- execute or install nothing during ordinary analysis;
- make no commit, push, PR comment, issue, release, deployment, or external write automatically;
- separate Workspace Trust, scan scope, AI/source egress, telemetry, execution, write, and publish consent;
- stay useful with AI off and no account;
- show exact provider, purpose, files/ranges, redactions, token estimate, region/retention disclosure, and cost/rate policy before source egress;
- treat repository content as untrusted evidence, never model instruction;
- use extension-owned storage and provide verified clear/delete controls;
- never expose individual productivity metrics.

Threat controls include Workspace Trust limited mode, path/symlink containment, parser isolation, file/time/memory/output caps, no project-module loading, no shell by default, webview CSP and schema validation, prompt-injection separation, secret detection, endpoint/redirect validation, SBOM/provenance, signed releases, and stale-generation rejection.

Current official platform references:

- [Extension Host](https://code.visualstudio.com/api/advanced-topics/extension-host)
- [Workspace Trust](https://code.visualstudio.com/api/extension-guides/workspace-trust)
- [Virtual Workspaces](https://code.visualstudio.com/api/extension-guides/virtual-workspaces)
- [Web Extensions](https://code.visualstudio.com/api/extension-guides/web-extensions)
- [Webview security](https://code.visualstudio.com/api/extension-guides/webview)
- [Language Model API](https://code.visualstudio.com/api/extension-guides/ai/language-model)
- [Extension data storage](https://code.visualstudio.com/api/extension-capabilities/common-capabilities)

## 13. Firebase preservation record

One canonical historical Android configuration is preserved at app/google-services.json because the project owner explicitly requested that Firebase connection identity be retained.

Verified local facts:

- source in Git history: commit a05d95156f747c7e1900e91e4f5b7f960cc583f3;
- expected blob: d159425573ab2d66883f618103858b28d8e14c66;
- expected SHA-256: a890656e5723da63f187757fce398f645ff21d99d080488c56839748dda5473f;
- Firebase project: relateai-birthday-ysomani;
- registered Android package: com.aistudio.relateai.qxtjrk;
- the historical debug copy was byte-identical and is deliberately not retained;
- no iOS config, Firebase backend code, Firestore/Realtime Database rules, Functions, service account, signing key, or current SDK wiring exists.

Production truth:

- The preserved JSON is dormant client metadata, not a working connection.
- It is Android-specific and does not match the removed Expo package com.relateai.app or define a VS Code extension client.
- Firebase is not an MVP dependency and authorizes no login, sync, analytics, telemetry, AI, storage, database, or cloud feature.
- Before any use, verify Firebase Console ownership, project status, Auth/API/billing/App Check/IAM settings, API-key restrictions, OAuth clients, and signing fingerprints.
- Register the actual future client/backend identity and obtain the correct config. Never adapt the product identity merely to make an old config pass.
- Never commit service-account credentials or admin secrets.
- If Firebase is later approved for team identity, policy, or metadata, place it behind an optional backend/service port; local code analysis remains independent.

## 14. Scalability and quality gates

### Candidate budgets

- No scan on activation and no unyielded extension-host task over 50 ms.
- First structural result within 2 seconds for a small reference workspace.
- Progressive architecture/dependency result rather than blocking completion.
- Warm bounded graph query P95 under 200 ms.
- Ordinary single-file incremental result visible within 2 seconds.
- Diagram projection capped and clustered; no “render entire monorepo” default.
- Cancellation acknowledged immediately and worker terminated or at a safe boundary promptly.
- Extension host never holds the full source corpus or graph.
- Remote workspaces send paged projections/deltas, never full indexes.

### Required testing

- domain and graph unit tests;
- adapter conformance fixtures and supported-version matrix;
- golden repositories, graphs, impacts, and diagrams;
- property/fuzz tests for paths, parsers, imports, graph/query and webview protocol;
- storage transaction, corruption, migration, deletion, and stale-job tests;
- real VS Code extension-host tests;
- untrusted, multi-root, remote, virtual, and web-degraded tests;
- webview CSP, sanitization, keyboard, screen-reader, zoom, and E2E tests;
- monorepo performance, cancellation, leak, and soak tests;
- deterministic AI retrieval, redaction, prompt-boundary, citation, fallback, and evaluation suites;
- VSIX content, install/upgrade/uninstall, signing, SBOM, provenance, and release smoke tests.

Hard gates:

- zero unapproved write/execute/network behavior;
- every factual AI claim cited or marked hypothesis;
- no known critical/high unmitigated extension security issue;
- published analyzer accuracy/limitations;
- privacy map and binary behavior match;
- performance/accessibility/remote/trust matrices pass;
- signed artifact, SBOM, provenance, rollback, incident, and vulnerability-response evidence exists.

## 15. Roadmap

### Phase 0 — Validate before product implementation

Phase 0 prototypes and spikes must be disposable, isolated from product/release code, time-boxed, and deleted after their evidence is captured. They validate choices; they do not establish the implementation baseline.

- Interview target personas with real unfamiliar-repository and change-impact tasks.
- Select two materially different semantic ecosystems from evidence, not preference.
- Prototype discovery, evidence schema, module/dependency graph, cited Q&A, and impact.
- Prove local-only zero egress, Workspace Trust behavior, storage, workers, remote/virtual degradation, and performance.
- Decide product name, storage technology, diagram libraries, AI packaging, and whether Firebase has any approved business role.

Exit: users complete the core task materially better than their baseline; every fact is traceable; privacy and performance gates are credible.

### Phase 1 — Foundation

Build only DW-31, DW-32, DW-38, DW-03, DW-02, the small internal DW-37 adapter contract, thin extension UI, worker scheduler, graph storage, and release/test foundation.

Exit: transactional incremental graph, truthful capability coverage, clean install/remote/multi-root/untrusted behavior, and published benchmarks.

### Phase 2 — Private MVP

Build DW-01, DW-16, DW-17, plus merged DW-11 structure, DW-14 cycles, and DW-15 onboarding modes. Support only the selected ecosystems.

Exit: onboarding and impact beat baseline; cited answers meet groundedness gates; users return for change/review work; no material privacy or editor-stability regression.

### Phase 3 — Evidence-gated expansion

Only after new approval, consider merged/later security imports, API/routing/data/event projections, dependency hygiene, dead code, refactoring plans, rules, reports, coverage, ownership, health, PR scope, test planning, and enterprise governance.

Removed DW-22, DW-26, and DW-30 remain out. Any reconsideration requires a formal change to this file with user evidence, risk analysis, and a replacement decision.

## 16. Implementation acceptance and change control

Phase 1 product implementation may start only when Phase 0 owners, target ecosystems, storage spike, privacy threat model, evaluation corpus, and MVP acceptance thresholds are approved.

The implementation is aligned only if:

- no deleted mobile implementation or compatibility layer returns;
- all features consume shared evidence contracts;
- no merged capability becomes a new top-level engine or screen;
- no Later or Remove item appears in an implementation ticket without a formal decision;
- AI remains optional and subordinate to deterministic evidence;
- unsupported and partial analysis is visible;
- all writes, execution, publication, and source egress are explicit and previewed;
- internal data stays outside repositories;
- Firebase remains dormant unless separately approved;
- extension-host responsiveness, privacy, accessibility, and security gates are enforced in CI.

To change a binding decision:

1. provide dated user/business evidence;
2. identify affected personas, workflows, data, architecture, privacy, risks, metrics, and tests;
3. update the feature evaluation and decision;
4. obtain product, architecture, security/privacy, and affected owner approval;
5. update this file in the same change.

No README, ADR, ticket, test, Figma file, comment, generated report, or implementation detail may silently change product scope. This file remains the sole authority.
