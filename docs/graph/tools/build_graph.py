#!/usr/bin/env python3
"""Build the master project graph from its sources.

Sources (all under docs/graph/):
  nano-units.json         nano-level functional units (mirrors PRODUCT.md section 5.5)
  feature-registry.json   canonical feature hierarchy (module..nano) with source/test evidence
  graph-overlay.json      curated nodes and non-containment edges (journeys, screens, APIs, ...)
  issue-register.json     audit issue register (ID, severity, feature, resolution)
Derived: containment edges, branch membership, issue links, test links, cross-module
dependencies parsed from Dart imports.

Outputs: master-project-graph.json, master-project-graph.dot

Usage: python3 docs/graph/tools/build_graph.py [--check]
--check rebuilds in memory and fails if the committed outputs differ (CI friendly).
No network access; standard library only.
"""
from __future__ import annotations

import argparse
import collections
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
GRAPH = ROOT / 'docs' / 'graph'

BRANCHES = [
    ('BRANCH-01', 'Project overview'), ('BRANCH-02', 'Personas and roles'),
    ('BRANCH-03', 'User journeys'), ('BRANCH-04', 'Module hierarchy'),
    ('BRANCH-05', 'Feature hierarchy'), ('BRANCH-06', 'Sub-features'),
    ('BRANCH-07', 'Micro-features'), ('BRANCH-08', 'Nano-features'),
    ('BRANCH-09', 'Screens and components'), ('BRANCH-10', 'Actions and interactions'),
    ('BRANCH-11', 'Application navigation'), ('BRANCH-12', 'Business logic'),
    ('BRANCH-13', 'Frontend architecture'), ('BRANCH-14', 'Backend architecture'),
    ('BRANCH-15', 'API ecosystem'), ('BRANCH-16', 'Database architecture'),
    ('BRANCH-17', 'Data flows'), ('BRANCH-18', 'State management'),
    ('BRANCH-19', 'External integrations'), ('BRANCH-20', 'Security and permissions'),
    ('BRANCH-21', 'Error handling'), ('BRANCH-22', 'Testing'),
    ('BRANCH-23', 'Infrastructure'), ('BRANCH-24', 'Implementation gaps'),
    ('BRANCH-25', 'Cross-module dependencies'),
]
LEVEL_BRANCH = {0: 'BRANCH-04', 1: 'BRANCH-04', 2: 'BRANCH-05', 3: 'BRANCH-06', 4: 'BRANCH-07'}
LEVEL_KIND = {0: 'application', 1: 'module', 2: 'feature', 3: 'sub-feature', 4: 'micro-feature'}
KIND_BRANCH = {
    'project-component': 'BRANCH-01', 'persona': 'BRANCH-02', 'service-identity': 'BRANCH-02',
    'journey': 'BRANCH-03', 'mobile-screen': 'BRANCH-09', 'web-page': 'BRANCH-09',
    'interaction': 'BRANCH-10', 'route': 'BRANCH-11', 'business-rule': 'BRANCH-12',
    'callable-api': 'BRANCH-15', 'scheduled-api': 'BRANCH-15', 'database-entity': 'BRANCH-16',
    'data-flow': 'BRANCH-17', 'state': 'BRANCH-18', 'integration': 'BRANCH-19',
    'security-control': 'BRANCH-20', 'security-gap': 'BRANCH-20', 'failure-path': 'BRANCH-21',
    'test-suite': 'BRANCH-22', 'infrastructure': 'BRANCH-23',
    'implementation-gap': 'BRANCH-24', 'issue': 'BRANCH-24',
    'cross-module-dependency': 'BRANCH-25',
}
STATUS_MAP = {
    'implemented': 'IMPLEMENTED', 'partially-implemented': 'PARTIALLY_IMPLEMENTED',
    'not-implemented': 'NOT_IMPLEMENTED', 'broken': 'BROKEN', 'mocked': 'MOCKED',
    'unreachable': 'UNREACHABLE', 'deprecated': 'DEPRECATED', 'unknown': 'UNKNOWN',
}
# lib/features/<dir> -> registry module ID
DIR_MODULE = {
    'ai': 'AI', 'auth': 'AUTH', 'birthdays': 'BIRTH', 'calendar': 'CAL', 'dashboard': 'DASH',
    'delivery': 'SEND', 'history': 'HISTORY', 'jobs': 'JOBS', 'message_studio': 'STUDIO',
    'onboarding': 'ONB', 'people': 'PEOPLE', 'reminders': 'REM', 'settings': 'SETTINGS',
    'subscription': 'BILL', 'sync': 'CLOUD',
}
IMPORT_RE = re.compile(r"""^\s*(?:import|export)\s+['"](?:package:ai_birthday/|\.\./|\./)?([^'"]+)['"]""", re.M)


def head_sha() -> str:
    return subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=ROOT, capture_output=True, text=True).stdout.strip()


def load(name: str):
    return json.loads((GRAPH / name).read_text(encoding='utf-8'))


def module_dependencies() -> dict[tuple[str, str], list[str]]:
    deps: dict[tuple[str, str], list[str]] = collections.defaultdict(list)
    base = ROOT / 'lib' / 'features'
    for path in sorted(base.rglob('*.dart')):
        rel = path.relative_to(base)
        src_dir = rel.parts[0]
        if src_dir not in DIR_MODULE:
            continue
        for target in IMPORT_RE.findall(path.read_text(encoding='utf-8')):
            parts = target.split('/')
            dst_dir = None
            if target.startswith('features/'):
                dst_dir = parts[1]
            elif target.startswith('..') or '../' in target:
                continue
            if dst_dir and dst_dir in DIR_MODULE and dst_dir != src_dir:
                deps[(DIR_MODULE[src_dir], DIR_MODULE[dst_dir])].append(path.relative_to(ROOT).as_posix())
    # relative imports of sibling features (../other_feature/...)
    rel_re = re.compile(r"""^\s*(?:import|export)\s+['"]((?:\.\./)+)([a-z_]+)/[^'"]*['"]""", re.M)
    for path in sorted(base.rglob('*.dart')):
        rel = path.relative_to(base)
        src_dir = rel.parts[0]
        if src_dir not in DIR_MODULE:
            continue
        for ups, dst_dir in rel_re.findall(path.read_text(encoding='utf-8')):
            depth = ups.count('../')
            # only a hop out of the feature directory reaches a sibling feature
            if depth == len(rel.parts) - 1 and dst_dir in DIR_MODULE and dst_dir != src_dir:
                deps[(DIR_MODULE[src_dir], DIR_MODULE[dst_dir])].append(path.relative_to(ROOT).as_posix())
    return deps


def build() -> dict:
    registry = load('feature-registry.json')
    overlay = load('graph-overlay.json')
    issues = load('issue-register.json')['issues']
    feats = registry['features']
    by_feat = {f['id']: f for f in feats}

    def depth(fid: str) -> int:
        d = 0
        while by_feat[fid].get('parent'):
            fid = by_feat[fid]['parent']
            d += 1
        return d

    nodes: dict[str, dict] = {}
    edges: list[dict] = []

    def add_node(node: dict) -> None:
        if node['id'] in nodes:
            raise SystemExit(f"duplicate node id {node['id']}")
        nodes[node['id']] = node

    add_node({'id': 'ECOSYS-ROOT', 'kind': 'project', 'label': 'COMPLETE PROJECT ECOSYSTEM — AI-Birthday',
              'status': 'IMPLEMENTED', 'confidence': 'HIGH', 'parent': None, 'evidence': ['README.md']})
    for bid, label in BRANCHES:
        add_node({'id': bid, 'kind': 'branch', 'label': label, 'parent': 'ECOSYS-ROOT'})
        edges.append({'from': 'ECOSYS-ROOT', 'to': bid, 'type': 'CONTAINS', 'evidence': ['PROJECT_MINDMAP.md']})

    # overlay nodes
    for n in overlay['nodes']:
        if n['id'] == 'ECOSYS-ROOT':
            continue
        node = dict(n)
        if 'parent' not in node:
            if node['kind'] == 'service':
                ev = (node.get('evidence') or [''])[0]
                node['parent'] = 'BRANCH-13' if ev.startswith(('lib/', 'android/')) else 'BRANCH-14'
            else:
                node['parent'] = KIND_BRANCH[node['kind']]
        node['branch'] = node['parent'] if node['parent'].startswith('BRANCH-') else nodes.get(node['parent'], {}).get('branch')
        add_node(node)

    # registry nodes
    for f in feats:
        d = depth(f['id'])
        level = d if d <= 4 else 5
        kind = LEVEL_KIND.get(d, 'nano-feature')
        branch = LEVEL_BRANCH.get(d, 'BRANCH-08')
        evid = list(f.get('source', [])) + list(f.get('tests', []))
        node = {
            'id': f['id'], 'kind': kind, 'level': level, 'label': f['name'],
            'status': STATUS_MAP[f['implementation_status']], 'confidence': f.get('confidence', 'MEDIUM'),
            'parent': f.get('parent') or 'BRANCH-04', 'branch': branch, 'evidence': evid,
            'verification': f.get('verification', ''),
        }
        if f.get('issues'):
            node['issues'] = f['issues']
        if f.get('note'):
            node['note'] = f['note']
        add_node(node)

    # nano-level units (PRODUCT.md section 5.5); registry depth ends at micro
    for u in load('nano-units.json')['units']:
        add_node({'id': u['id'], 'kind': 'nano-feature', 'level': 6, 'label': u['label'], 'status': u['status'],
                  'confidence': u.get('confidence', 'MEDIUM'), 'parent': u['parent'], 'branch': 'BRANCH-08',
                  'evidence': u['evidence'], 'note': u.get('note', '')})

    # issue nodes
    for i in issues:
        add_node({'id': i['id'], 'kind': 'issue', 'label': i['title'], 'severity': i['severity'],
                  'status': i['resolution'], 'confidence': 'HIGH' if i['resolution'] != 'UNCONFIRMED' else 'LOW',
                  'parent': 'BRANCH-24', 'branch': 'BRANCH-24', 'feature': i.get('feature', ''),
                  'evidence': i.get('evidence', [])})

    # cross-module dependency nodes + edges
    deps = module_dependencies()
    for n, ((a, b), files) in enumerate(sorted(deps.items()), 1):
        if a not in nodes or b not in nodes:
            raise SystemExit(f'unknown module in dependency {a}->{b}')
        did = f'DEP-{n:03d}'
        add_node({'id': did, 'kind': 'cross-module-dependency', 'label': f'{a} depends on {b} ({len(files)} importing files)',
                  'status': 'IMPLEMENTED', 'confidence': 'HIGH', 'parent': 'BRANCH-25', 'branch': 'BRANCH-25',
                  'evidence': sorted(set(files))[:3]})
        edges.append({'from': a, 'to': b, 'type': 'DEPENDS_ON', 'evidence': sorted(set(files))[:1]})

    # curated non-containment edges
    for e in overlay['edges']:
        edges.append(dict(e))

    # containment edges from parent
    for nid, n in nodes.items():
        p = n.get('parent')
        if not p or n['kind'] == 'branch':
            continue
        edges.append({'from': p, 'to': nid, 'type': 'CONTAINS', 'evidence': ['docs/graph/graph-overlay.json'] if n['kind'] not in LEVEL_KIND.values() and n['kind'] != 'nano-feature' else ['docs/graph/feature-registry.json']})
        # branch membership for registry nodes in a different branch from their parent
        parent_branch = nodes[p].get('branch') if p in nodes else None
        if n['kind'] in ('module', 'feature', 'sub-feature', 'micro-feature', 'nano-feature') and n['branch'] != parent_branch and n['branch'] != p:
            edges.append({'from': n['branch'], 'to': nid, 'type': 'CONTAINS', 'evidence': ['docs/graph/feature-registry.json']})

    # test and issue links for registry nodes
    suite_for = lambda p: 'TEST-004' if p.startswith('backend/hosting/test') else ('TEST-003' if '.emulator.' in p else ('TEST-002' if p.startswith('backend/functions/test') else ('TEST-001' if p.startswith(('test/', 'integration_test/')) else None)))
    for f in feats:
        suites = {suite_for(t) for t in f.get('tests', [])} - {None}
        for s in sorted(suites):
            if s in nodes:
                first = next(t for t in f['tests'] if suite_for(t) == s)
                edges.append({'from': f['id'], 'to': s, 'type': 'TESTED_BY', 'evidence': [first]})
        for iid in f.get('issues', []):
            if iid in nodes and nodes[iid]['status'] in ('OPEN', 'PARTIAL', 'UNCONFIRMED'):
                code_ev = [e for e in nodes[iid].get('evidence', []) if e != 'IMPLEMENTATION.md'][:1]
                edges.append({'from': f['id'], 'to': iid, 'type': 'BLOCKED_BY', 'evidence': code_ev or ['IMPLEMENTATION.md']})

    # dedupe edges deterministically
    seen = set()
    uniq = []
    for e in edges:
        key = (e['from'], e['to'], e['type'])
        if key not in seen:
            seen.add(key)
            uniq.append(e)
    uniq.sort(key=lambda e: (e['from'], e['to'], e['type']))
    ordered = [nodes[k] for k in sorted(nodes, key=lambda k: (0 if k == 'ECOSYS-ROOT' else 1 if k.startswith('BRANCH-') else 2, k))]
    return {
        'schema_version': 2,
        'repository': registry['repository'],
        'snapshot_sha': registry['snapshot_sha'],
        'snapshot_note': registry.get('snapshot_note', ''),
        'review_date': registry['review_date'],
        'contract': {
            'status': 'IMPLEMENTED/PARTIALLY_IMPLEMENTED/NOT_IMPLEMENTED/BROKEN/MOCKED/UNREACHABLE/DEPRECATED/UNKNOWN describe repository source, not deployment or runtime proof; issue nodes use OPEN/PARTIAL/FIXED/UNCONFIRMED.',
            'confidence': 'HIGH = directly observed in source or execution; MEDIUM = source-mapped, not fully exercised; LOW = indirect.',
            'identity': 'IDs are stable and globally unique; parent defines the tree; edges are typed and carry evidence paths.',
            'generated': 'Do not edit; edit feature-registry.json, graph-overlay.json or issue-register.json and run tools/build_graph.py.',
        },
        'nodes': ordered,
        'edges': uniq,
    }


def to_dot(graph: dict) -> str:
    style = {'CONTAINS': 'style=solid,color="#9aa5b1"', 'DEPENDS_ON': 'style=dashed,color="#1f6feb"',
             'CALLS': 'style=solid,color="#c16a16"', 'NAVIGATES_TO': 'style=dotted,color="#3478c5"',
             'TESTED_BY': 'style=dotted,color="#318553"', 'BLOCKED_BY': 'style=bold,color="#b3261e"'}
    colour = {'IMPLEMENTED': '#e7f6ec', 'PARTIALLY_IMPLEMENTED': '#fff3cd', 'NOT_IMPLEMENTED': '#fbe4e6',
              'BROKEN': '#fbe4e6', 'OPEN': '#fbe4e6', 'PARTIAL': '#fff3cd', 'FIXED': '#e7f6ec'}
    out = ['digraph ProjectGraph {', '  rankdir=LR; node [shape=box,fontsize=9,style=filled];']
    for n in graph['nodes']:
        label = (n['id'] + '\\n' + n['label'][:60]).replace('"', "'")
        out.append(f'  "{n["id"]}" [label="{label}",fillcolor="{colour.get(n.get("status"), "#eceff1")}"];')
    for e in graph['edges']:
        out.append(f'  "{e["from"]}" -> "{e["to"]}" [{style.get(e["type"], "color=gray")},label="{e["type"] if e["type"] != "CONTAINS" else ""}",fontsize=7];')
    out.append('}')
    return '\n'.join(out) + '\n'


def render(graph: dict) -> dict[str, str]:
    data = json.dumps(graph, indent=1, ensure_ascii=False) + '\n'
    return {'master-project-graph.json': data, 'master-project-graph.dot': to_dot(graph)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    graph = build()
    outputs = render(graph)
    if args.check:
        stale = [n for n, text in outputs.items() if not (GRAPH / n).exists() or (GRAPH / n).read_text(encoding='utf-8') != text]
        if (GRAPH / 'graph-viewer.html').exists():
            stale.append('graph-viewer.html (documentation web UI is not generated)')
        if stale:
            print('stale graph outputs:', ', '.join(stale))
            return 1
        print(f"graph up to date: {len(graph['nodes'])} nodes, {len(graph['edges'])} edges")
        return 0
    for name, text in outputs.items():
        (GRAPH / name).write_text(text, encoding='utf-8')
    print(f"wrote {len(graph['nodes'])} nodes, {len(graph['edges'])} edges")
    return 0


if __name__ == '__main__':
    sys.exit(main())
