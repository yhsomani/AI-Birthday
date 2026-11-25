#!/usr/bin/env python3
"""Generate PROJECT_MINDMAP.md from docs/graph/master-project-graph.json.

Counts, diagrams and tables are derived from the graph so the document cannot drift
from the inventory; narrative sections are fixed text in this script.

Usage: python3 docs/graph/tools/build_mindmap.py [--check]
"""
from __future__ import annotations

import argparse
import collections
import csv
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
GRAPH = ROOT / 'docs' / 'graph'
OUT = ROOT / 'PROJECT_MINDMAP.md'

CLASS = {
    'IMPLEMENTED': 'impl', 'PARTIALLY_IMPLEMENTED': 'part', 'NOT_IMPLEMENTED': 'miss', 'BROKEN': 'miss',
    'MOCKED': 'miss', 'UNREACHABLE': 'miss', 'UNKNOWN': 'unk', 'DEPRECATED': 'unk',
    'FIXED': 'impl', 'PARTIAL': 'part', 'OPEN': 'miss', 'UNCONFIRMED': 'unk',
}
CLASSDEFS = [
    'classDef impl fill:#e7f6ec,stroke:#318553,color:#153c27;',
    'classDef part fill:#fff3cd,stroke:#be8b00,color:#4d3b00;',
    'classDef miss fill:#fbe4e6,stroke:#b3261e,color:#5a1511;',
    'classDef unk fill:#eceff1,stroke:#66727a,color:#263238;',
    'classDef br fill:#e8f4ff,stroke:#2476b9,color:#12324a;',
]


def mid(node_id: str) -> str:
    return 'n_' + ''.join(c if c.isalnum() else '_' for c in node_id)


def lab(text: str, n: int = 46) -> str:
    text = text.replace('"', "'").replace('\n', ' ')
    return text if len(text) <= n else text[: n - 1] + '…'


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    g = json.loads((GRAPH / 'master-project-graph.json').read_text(encoding='utf-8'))
    nodes = {n['id']: n for n in g['nodes']}
    edges = g['edges']
    kids: dict[str, list[str]] = collections.defaultdict(list)
    for n in g['nodes']:
        if n.get('parent'):
            kids[n['parent']].append(n['id'])
    branch_count = collections.Counter(n.get('branch') for n in g['nodes'] if n.get('branch'))
    for b in kids:
        if b.startswith('BRANCH-'):
            branch_count[b] += 0
    membership = collections.defaultdict(set)
    for e in edges:
        if e['type'] == 'CONTAINS' and e['from'].startswith('BRANCH-'):
            membership[e['from']].add(e['to'])
    for b in kids:
        if b.startswith('BRANCH-'):
            membership[b].update(kids[b])

    L: list[str] = []
    w = L.append
    w('# AI-Birthday — Project mind map and navigation graph')
    w('')
    w('> **Generated document.** Do not edit by hand. Edit `docs/graph/feature-registry.json`, `docs/graph/graph-overlay.json` or `docs/graph/issue-register.json`, then run `python3 docs/graph/tools/build_graph.py && python3 docs/graph/tools/build_mindmap.py`. Validate with `python3 docs/graph/tools/validate_docs.py`.')
    w('')
    w(f"**Snapshot:** `{g['snapshot_sha'][:8]}` plus uncommitted fixes ({g['review_date']}). **Graph size:** {len(g['nodes'])} nodes, {len(g['edges'])} typed edges, 25 branches. Status values describe repository source, not deployment or runtime proof (see [IMPLEMENTATION.md](IMPLEMENTATION.md) for verification evidence).")
    w('')
    w('## Contents')
    w('')
    for t in ['How to use the graph', 'Master ecosystem map', 'Branch index', 'Module and feature hierarchy', 'Journey to data chains',
              'Application flow and navigation', 'API dependency map', 'Cross-module dependencies', 'Implementation status summary',
              'Journey traceability matrix', 'Legacy blueprint maps (preserved)', 'Relationship and identity conventions']:
        w(f"- [{t}](#{t.lower().replace(' ', '-').replace('(', '').replace(')', '')})")
    w('')
    w('## How to use the graph')
    w('')
    w('| Artifact | Purpose |')
    w('|---|---|')
    w('| [docs/graph/master-project-graph.json](docs/graph/master-project-graph.json) | Complete machine-readable graph: every node (stable ID, parent, kind, status, confidence, evidence) and every typed edge. Nothing is omitted because of visualisation limits. |')
    w('| [docs/graph/master-project-graph.dot](docs/graph/master-project-graph.dot) | Graphviz DOT export of the full graph for external layout tools (`dot -Tsvg`). |')
    w('| [docs/graph/feature-registry.json](docs/graph/feature-registry.json) | Source of the feature hierarchy (module → feature → sub-feature → micro-feature) with source and test evidence. |')
    w('| [docs/graph/graph-overlay.json](docs/graph/graph-overlay.json) | Curated nodes and edges: journeys, screens, actions, APIs, services, data, integrations, security, infrastructure, routes. |')
    w('| [docs/graph/issue-register.json](docs/graph/issue-register.json) | Audit issue register (machine copy of IMPLEMENTATION.md §4). |')
    w('| [docs/graph/traceability-matrix.csv](docs/graph/traceability-matrix.csv) | Journey → feature → screen → action → rule → API → service → data → test. |')
    w('| [docs/graph/nano-units.json](docs/graph/nano-units.json) | Nano-level functional units (`<registry-ID>/n<k>`), mirrored in PRODUCT.md §5.5. |')
    w('| [docs/graph/spec-traceability.csv](docs/graph/spec-traceability.csv) | Legacy requirement keys (SSOT sections) → feature → source → tests. |')
    w('| [docs/graph/migration-matrix.csv](docs/graph/migration-matrix.csv), [document-disposition.csv](docs/graph/document-disposition.csv) | Record of where every extracted item of the consolidated legacy documents went, and the fate of every legacy document. |')
    w('')
    w('The Mermaid views below draw the hierarchy, routes, API map, journey chains and module dependencies. Security controls, test suites, services, issues and the remaining nodes are in the complete JSON graph and DOT export, and are summarised in the tables.')
    w('')
    w('The graph is edited by changing its source files and regenerating the JSON and DOT outputs. The feature registry ends at micro-features; nano-level units (validation rules, parser steps, state transitions) are kept in [docs/graph/nano-units.json](docs/graph/nano-units.json) with `<registry-ID>/n<k>` handles and are documented in [PRODUCT.md §5.5](PRODUCT.md). Atomic business rules (BR-) and interaction patterns (ACT-) are separate branches.')
    w('')

    # master map
    w('## Master ecosystem map')
    w('')
    w('```mermaid')
    w('flowchart LR')
    w('  ROOT(("COMPLETE PROJECT ECOSYSTEM<br/>AI-Birthday"))')
    branches = [n for n in g['nodes'] if n['kind'] == 'branch']
    for b in branches:
        cnt = len(membership[b['id']])
        w(f"  ROOT --> {mid(b['id'])}[\"{b['id'][-2:]} {lab(b['label'])}<br/>{cnt} nodes\"]")
    w('  classDef br fill:#e8f4ff,stroke:#2476b9,color:#12324a;')
    w('  class ' + ','.join(mid(b['id']) for b in branches) + ' br;')
    w('```')
    w('')

    # branch index
    w('## Branch index')
    w('')
    w('| Branch | Nodes | Implemented | Partial | Missing | Detail |')
    w('|---|---:|---:|---:|---:|---|')
    detail = {
        'BRANCH-01': '[README.md](README.md)', 'BRANCH-02': '[PRODUCT.md §2](PRODUCT.md)', 'BRANCH-03': '[PRODUCT.md §4](PRODUCT.md)',
        'BRANCH-04': '[PRODUCT.md §5](PRODUCT.md)', 'BRANCH-05': '[PRODUCT.md §5](PRODUCT.md)', 'BRANCH-06': '[PRODUCT.md §5](PRODUCT.md)',
        'BRANCH-07': '[PRODUCT.md §5](PRODUCT.md)', 'BRANCH-08': '[PRODUCT.md §9](PRODUCT.md)', 'BRANCH-09': '[PRODUCT.md §7](PRODUCT.md)',
        'BRANCH-10': '[PRODUCT.md §7](PRODUCT.md)', 'BRANCH-11': 'this document', 'BRANCH-12': '[PRODUCT.md §9](PRODUCT.md)',
        'BRANCH-13': '[ARCHITECTURE.md §4](ARCHITECTURE.md)', 'BRANCH-14': '[ARCHITECTURE.md §8](ARCHITECTURE.md)',
        'BRANCH-15': '[ARCHITECTURE.md §8](ARCHITECTURE.md)', 'BRANCH-16': '[ARCHITECTURE.md §7](ARCHITECTURE.md)',
        'BRANCH-17': '[ARCHITECTURE.md §9](ARCHITECTURE.md)', 'BRANCH-18': '[ARCHITECTURE.md §9](ARCHITECTURE.md)',
        'BRANCH-19': '[ARCHITECTURE.md §10](ARCHITECTURE.md)', 'BRANCH-20': '[SECURITY.md](SECURITY.md)',
        'BRANCH-21': '[IMPLEMENTATION.md](IMPLEMENTATION.md)', 'BRANCH-22': '[IMPLEMENTATION.md §6](IMPLEMENTATION.md)',
        'BRANCH-23': '[OPERATIONS.md](OPERATIONS.md)', 'BRANCH-24': '[IMPLEMENTATION.md §4](IMPLEMENTATION.md)',
        'BRANCH-25': 'this document',
    }
    for b in branches:
        members = [nodes[x] for x in membership[b['id']]]
        c = collections.Counter(CLASS.get(m.get('status'), 'unk') for m in members)
        w(f"| {b['id']} {b['label']} | {len(members)} | {c['impl']} | {c['part']} | {c['miss']} | {detail[b['id']]} |")
    w('')

    # hierarchy
    w('## Module and feature hierarchy')
    w('')
    top = [n for n in g['nodes'] if n['kind'] == 'module']
    level_counts = collections.Counter(n['kind'] for n in g['nodes'] if n['kind'] in ('application', 'module', 'feature', 'sub-feature', 'micro-feature', 'nano-feature'))
    w(f"The registry holds {level_counts['application']} application, {level_counts['module']} modules, {level_counts['feature']} features, {level_counts['sub-feature']} sub-features, {level_counts['micro-feature']} micro-features and {level_counts['nano-feature']} nano-level units. Green = implemented, yellow = partial, red = not implemented/broken/unreachable, grey = unknown. Per-node evidence and tests are in the registry.")
    w('')
    for m in sorted(top, key=lambda n: n['id']):
        sub = []
        stack = [m['id']]
        while stack:
            cur = stack.pop()
            for c in sorted(kids.get(cur, [])):
                if nodes[c]['kind'] in ('feature', 'sub-feature', 'micro-feature', 'nano-feature'):
                    sub.append(c)
                    stack.append(c)
        w(f"### {m['id']} — {m['label']}")
        w('')
        w(f"Status **{m['status']}** ({m['confidence']}); {len(sub)} descendant nodes.")
        w('')
        w('```mermaid')
        w('flowchart LR')
        w(f"  {mid(m['id'])}[\"{m['id']}<br/>{lab(m['label'], 40)}\"]")
        w(f"  class {mid(m['id'])} {CLASS.get(m['status'], 'unk')};")
        for c in sub:
            n = nodes[c]
            w(f"  {mid(c)}[\"{c}<br/>{lab(n['label'], 40)}\"]")
            w(f"  {mid(n['parent'])} --> {mid(c)}")
            w(f"  class {mid(c)} {CLASS.get(n['status'], 'unk')};")
        for d in CLASSDEFS[:4]:
            w('  ' + d)
        w('```')
        w('')

    # journeys
    w('## Journey to data chains')
    w('')
    w('Source-level chains from journeys through screens, actions, rules, services and data. They show call and data dependencies, not proof of successful external calls.')
    w('')
    w((ROOT / 'docs' / 'graph' / 'tools' / 'mindmap-parts' / 'journey-chains.md').read_text(encoding='utf-8').rstrip())
    w('')

    # navigation
    w('## Application flow and navigation')
    w('')
    routes = [n for n in g['nodes'] if n['kind'] == 'route']
    w('```mermaid')
    w('flowchart TD')
    w('  START(["App launch"]) --> PRE["Database preflight (bounded) — recovery screen with retry on failure"]')
    w('  PRE --> GUARD{"Onboarding complete? (BR-025)"}')
    w('  GUARD -- "no" --> R1["/onboarding"]')
    w('  GUARD -- "yes" --> SHELL["Shell: four destinations"]')
    w('  R1 --> SHELL')
    w('  SHELL --> D["/dashboard Today"]')
    w('  SHELL --> P["/people"]')
    w('  SHELL --> C["/calendar"]')
    w('  SHELL --> H["/history Activity"]')
    w('  D --> ST["/message-studio/:birthdayId"]')
    w('  P --> F["/people/add, /people/edit/:id"]')
    w('  P --> SP["/message-studio/person/:personId"]')
    w('  C --> ST')
    w('  H --> ST')
    w('  NOTIF(["Notification tap or cold start"]) --> SP')
    w('  D --> SET["/settings (pushed above shell)"]')
    w('  SET --> R1')
    w('  ANY["Unknown path"] --> NF["Not-found recovery screen"] --> D')
    w('```')
    w('')
    w('| Route node | Pattern and purpose | Screen | Evidence |')
    w('|---|---|---|---|')
    for r in sorted(routes, key=lambda n: n['id']):
        tgt = [e['to'] for e in edges if e['from'] == r['id'] and e['type'] in ('NAVIGATES_TO', 'VALIDATES')]
        w(f"| {r['id']} | {r['label'].replace('|', '/')} | {', '.join(tgt)} | `{r['evidence'][0]}` |")
    w('')
    w('Public Hosting routes (SCR-010 … SCR-014): `/`, `/privacy/`, `/terms/`, `/support/`, `/delete/` — a separate Vite site, not a Flutter web target. Route-level behaviour, guards and deep links are specified in [PRODUCT.md §7](PRODUCT.md).')
    w('')

    # api
    w('## API dependency map')
    w('')
    w((ROOT / 'docs' / 'graph' / 'tools' / 'mindmap-parts' / 'api-map.md').read_text(encoding='utf-8').rstrip())
    w('')

    # module deps
    w('## Cross-module dependencies')
    w('')
    deps = [n for n in g['nodes'] if n['kind'] == 'cross-module-dependency']
    w(f"{len(deps)} directed dependencies between feature modules, computed from Dart `import` statements under `lib/features/` (a module depends on another when any file imports from it). Evidence: the first importing file is recorded on each edge.")
    w('')
    w('```mermaid')
    w('flowchart LR')
    used = set()
    for e in sorted(edges, key=lambda e: (e['from'], e['to'])):
        if e['type'] == 'DEPENDS_ON' and nodes[e['from']]['kind'] == 'module' and nodes[e['to']]['kind'] == 'module':
            used.update([e['from'], e['to']])
            w(f"  {mid(e['from'])} --> {mid(e['to'])}")
    for u in sorted(used):
        w(f"  {mid(u)}[\"{u}\"]")
    w('```')
    w('')
    w('| From | To | Importing files | Evidence |')
    w('|---|---|---:|---|')
    for d in sorted(deps, key=lambda n: n['label']):
        w(f"| {d['id']} | {d['label'].split(' (')[0]} | {d['label'].split('(')[-1].split(' ')[0]} | `{d['evidence'][0]}` |")
    w('')

    # status summary
    w('## Implementation status summary')
    w('')
    reg_nodes = [n for n in g['nodes'] if n['kind'] in ('module', 'feature', 'sub-feature', 'micro-feature', 'nano-feature')]
    sc = collections.Counter(n['status'] for n in reg_nodes)
    w('| Feature and nano-unit status | Count |')
    w('|---|---:|')
    for k in sorted(sc):
        w(f'| {k} | {sc[k]} |')
    w('')
    issues = [n for n in g['nodes'] if n['kind'] == 'issue']
    ic = collections.Counter((n['severity'], n['status']) for n in issues)
    w('| Audit issues | FIXED | PARTIAL | OPEN | UNCONFIRMED |')
    w('|---|---:|---:|---:|---:|')
    for sev in ['P0', 'P1', 'P2', 'P3', 'P4']:
        w(f"| {sev} | {ic[(sev, 'FIXED')]} | {ic[(sev, 'PARTIAL')]} | {ic[(sev, 'OPEN')]} | {ic[(sev, 'UNCONFIRMED')]} |")
    w('')
    w('Open or partial P1/P2 issues:')
    w('')
    for n in sorted(issues, key=lambda n: n['id']):
        if n['severity'] in ('P1', 'P2') and n['status'] in ('OPEN', 'PARTIAL', 'UNCONFIRMED'):
            w(f"- **{n['id']}** ({n['severity']}, {n['status']}) — {lab(n['label'], 140)}")
    w('')
    w('Statuses are source classifications; deployment, device behaviour and production data were not verified. See [IMPLEMENTATION.md](IMPLEMENTATION.md) §1 for what was executed.')
    w('')

    # traceability
    w('## Journey traceability matrix')
    w('')
    rows = list(csv.DictReader((GRAPH / 'traceability-matrix.csv').open(newline='', encoding='utf-8')))
    w('Full detail (feature, screen, action, rule, API, service, data and test per journey) is in [docs/graph/traceability-matrix.csv](docs/graph/traceability-matrix.csv).')
    w('')
    w('| Journey | Name | Screens | Actions | Rules | APIs | Services | Status |')
    w('|---|---|---|---|---|---|---|---|')
    for r in rows:
        w(f"| {r['journey_id']} | {r['journey_name']} | {r['screen_ids'].replace(';', ', ')} | {r['action_ids'].replace(';', ', ')} | {r['rule_ids'].replace(';', ', ')} | {r['api_ids'].replace(';', ', ') or '—'} | {r['service_ids'].replace(';', ', ') or '—'} | {r['implementation_status']} |")
    w('')

    w((ROOT / 'docs' / 'graph' / 'tools' / 'mindmap-parts' / 'legacy-maps.md').read_text(encoding='utf-8').rstrip())
    w('')
    w('## Relationship and identity conventions')
    w('')
    w('| Convention | Rule |')
    w('|---|---|')
    w('| Node IDs | Stable and unique: `ROLE-`, `J-`, `SCR-`, `ROUTE-`, `ACT-`, `BR-`, `API-`, `SVC-`, `DB-`, `FLOW-`, `STATE-`, `ERR-`, `EXT-`, `SEC-`, `INF-`, `TEST-`, `ISSUE-`/`A#-###`, `DEP-`, `PROJ-`; registry IDs such as `PEOPLE-VALIDATE` keep their original names. |')
    w('| Relationship types | `CONTAINS`, `DEPENDS_ON`, `TRIGGERS`, `CALLS`, `READS`, `WRITES`, `UPDATES`, `NAVIGATES_TO`, `VALIDATES`, `AUTHORIZES`, `REQUIRES`, `PRODUCES`, `CONSUMES`, `TRANSFORMS`, `TESTED_BY`, `FALLBACK_TO`, `BLOCKED_BY`. Every edge carries evidence. |')
    w('| Status | `IMPLEMENTED`, `PARTIALLY_IMPLEMENTED`, `NOT_IMPLEMENTED`, `BROKEN`, `MOCKED`, `UNREACHABLE`, `DEPRECATED`, `UNKNOWN`; issue nodes use `OPEN`, `PARTIAL`, `FIXED`, `UNCONFIRMED`. |')
    w('| Confidence | `HIGH` directly observed in source or execution; `MEDIUM` source-mapped, not fully exercised; `LOW` indirect. |')
    w('| Authority | Source code and execution evidence decide implementation status; PRODUCT.md decides intended behaviour; ARCHITECTURE.md and SECURITY.md decide intended technical design. Discrepancies are listed in IMPLEMENTATION.md. |')
    text = '\n'.join(L) + '\n'
    if args.check:
        if not OUT.exists() or OUT.read_text(encoding='utf-8') != text:
            print('PROJECT_MINDMAP.md is stale')
            return 1
        print('PROJECT_MINDMAP.md up to date')
        return 0
    OUT.write_text(text, encoding='utf-8')
    print(f'wrote PROJECT_MINDMAP.md ({len(L)} lines)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
