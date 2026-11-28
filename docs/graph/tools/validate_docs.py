#!/usr/bin/env python3
"""Validate the canonical documentation set, feature registry and project graph.

Checks (all offline, standard library only, read-only):
  * canonical Markdown: code fences balanced, relative links and #anchors resolve,
    Mermaid blocks open with a known diagram type, no links to retired paths, and no
    documentation web pages
  * feature registry: unique IDs, parents exist, no cycles, source/test paths exist
  * project graph: unique IDs, parent tree reaches the root, edge endpoints exist,
    relationship types are allowed, evidence paths exist, 25 branches present,
    generated outputs are up to date
  * traceability matrix: every referenced ID exists in the graph
  * issue register: every issue ID is documented in IMPLEMENTATION.md
  * migration matrix: every row points at an existing canonical heading

Usage: python3 docs/graph/tools/validate_docs.py [--format json]
Exit code 0 = no errors.
"""
from __future__ import annotations

import argparse
import csv
import importlib.util
import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[3]
GRAPH = ROOT / 'docs' / 'graph'
CANONICAL = ['README.md', 'docs/PRODUCT.md', 'docs/ARCHITECTURE.md', 'docs/SECURITY.md',
             'docs/IMPLEMENTATION.md', 'docs/OPERATIONS.md', 'docs/PROJECT_MINDMAP.md',
             'backend/README.md', 'backend/hosting/README.md']
CANONICAL_ALIASES = {
    Path(path).name: path for path in CANONICAL if path.startswith('docs/')
}
RETIRED_PREFIXES = ('docs/product-blueprint', 'docs/audit', 'docs/archive', 'docs/ui-redesign', 'docs/ui-ux',
                    'docs/knowledge', 'SSOT.md', 'PROJECT.md', 'DESIGN.md', 'DESIGN-SUMMARY.md',
                    'docs/ui-audit.md', 'docs/visibility-audit.md', 'docs/purchases-setup.md')
EDGE_TYPES = {'CONTAINS', 'DEPENDS_ON', 'TRIGGERS', 'CALLS', 'READS', 'WRITES', 'UPDATES', 'NAVIGATES_TO',
              'VALIDATES', 'AUTHORIZES', 'REQUIRES', 'PRODUCES', 'CONSUMES', 'TRANSFORMS', 'TESTED_BY',
              'FALLBACK_TO', 'BLOCKED_BY'}
NODE_STATUS = {'IMPLEMENTED', 'PARTIALLY_IMPLEMENTED', 'NOT_IMPLEMENTED', 'BROKEN', 'MOCKED', 'UNREACHABLE',
               'DEPRECATED', 'UNKNOWN'}
ISSUE_STATUS = {'OPEN', 'PARTIAL', 'FIXED', 'UNCONFIRMED'}
REGISTRY_STATUS = {'implemented', 'partially-implemented', 'not-implemented', 'broken', 'mocked',
                   'unreachable', 'deprecated', 'unknown'}
MERMAID_START = re.compile(r'^(flowchart|graph|sequenceDiagram|stateDiagram(-v2)?|erDiagram|classDiagram|mindmap|journey|gantt|timeline|pie|gitGraph|C4Context|quadrantChart|requirementDiagram)\b')
GENERATED_EVIDENCE = {'docs/graph/graph-overlay.json', 'docs/graph/feature-registry.json', 'docs/graph/issue-register.json',
                      'docs/graph/master-project-graph.json'}


def repo_path(path: str | Path) -> Path:
    """Resolve repository-root paths and legacy root-level canonical doc links."""
    candidate = (ROOT / path).resolve()
    if candidate.exists():
        return candidate
    alias = CANONICAL_ALIASES.get(Path(path).name)
    if alias is not None:
        canonical = ROOT / alias
        if canonical.exists():
            return canonical
    return candidate


def slug(heading: str) -> str:
    text = re.sub(r'`([^`]*)`', r'\1', heading.strip().lower())
    text = re.sub(r'[^\w\- ]', '', text, flags=re.UNICODE)
    return text.replace(' ', '-')


def headings(path: Path) -> set[str]:
    out: set[str] = set()
    counts: dict[str, int] = {}
    in_fence = False
    for line in path.read_text(encoding='utf-8').splitlines():
        if line.lstrip().startswith(('```', '~~~')):
            in_fence = not in_fence
        if in_fence:
            continue
        m = re.match(r'^#{1,6}\s+(.*?)\s*#*\s*$', line)
        if m:
            s = slug(m.group(1))
            n = counts.get(s, 0)
            counts[s] = n + 1
            out.add(s if n == 0 else f'{s}-{n}')
    return out


def validate_markdown(errors: list[str], stats: dict) -> None:
    anchors_cache: dict[Path, set[str]] = {}
    links = mermaid = 0
    for path in (ROOT / 'docs').rglob('*.html'):
        errors.append(f"{path.relative_to(ROOT)}: documentation web pages are not part of the Markdown documentation set")
    for rel in CANONICAL:
        path = repo_path(rel)
        if not path.exists():
            errors.append(f'{rel}: canonical document is missing')
            continue
        text = path.read_text(encoding='utf-8')
        if len(re.findall(r'^\s*(```|~~~)', text, flags=re.M)) % 2:
            errors.append(f'{rel}: unbalanced code fence')
        for block in re.findall(r'^```mermaid\s*\n(.*?)^```', text, flags=re.M | re.S):
            mermaid += 1
            first = next((ln.strip() for ln in block.splitlines() if ln.strip() and not ln.strip().startswith('%%')), '')
            if not MERMAID_START.match(first):
                errors.append(f'{rel}: mermaid block starts with unknown diagram type: {first[:40]!r}')
        body = re.sub(r'^```.*?^```', '', text, flags=re.M | re.S)
        body = re.sub(r'`[^`\n]*`', '', body)
        for url in re.findall(r'\]\(([^)\s]+)(?:\s+"[^"]*")?\)', body):
            url = url.strip('<>')
            if url.startswith(('http://', 'https://', 'mailto:', 'tel:', 'data:')):
                continue
            target_part, _, anchor = url.partition('#')
            links += 1
            if target_part:
                relative_target = unquote(target_part.split('?')[0])
                target = (path.parent / relative_target).resolve()
                if not target.exists():
                    target = repo_path(relative_target)
                if not target.is_relative_to(ROOT):
                    errors.append(f'{rel}: link escapes repository: {url}')
                    continue
                if not target.exists():
                    errors.append(f'{rel}: unresolved link {url}')
                    continue
                relt = target.relative_to(ROOT).as_posix()
                if relt.startswith(RETIRED_PREFIXES):
                    errors.append(f'{rel}: link to retired document {url}')
            else:
                target = path
            if anchor and target.suffix == '.md':
                anchors = anchors_cache.setdefault(target, headings(target))
                if slug(unquote(anchor)) not in anchors and unquote(anchor) not in anchors:
                    errors.append(f'{rel}: unresolved anchor {url}')
    stats['canonical_documents'] = len(CANONICAL)
    stats['relative_links_checked'] = links
    stats['mermaid_blocks'] = mermaid


def validate_registry(errors: list[str], stats: dict) -> dict:
    path = GRAPH / 'feature-registry.json'
    reg = json.loads(path.read_text(encoding='utf-8'))
    feats = reg['features']
    by = {}
    for f in feats:
        if f['id'] in by:
            errors.append(f'registry: duplicate ID {f["id"]}')
        by[f['id']] = f
        if f.get('implementation_status') not in REGISTRY_STATUS:
            errors.append(f'registry {f["id"]}: bad status {f.get("implementation_status")}')
        for field in ('source', 'tests'):
            for p in f.get(field, []):
                if not repo_path(p).is_file():
                    errors.append(f'registry {f["id"]}: missing {field} path {p}')
    for f in feats:
        seen = {f['id']}
        p = f.get('parent')
        while p:
            if p not in by:
                errors.append(f'registry {f["id"]}: unknown parent {p}')
                break
            if p in seen:
                errors.append(f'registry {f["id"]}: parent cycle')
                break
            seen.add(p)
            p = by[p].get('parent')
    stats['registry_nodes'] = len(feats)
    return reg


def evidence_exists(p: str) -> bool:
    if p in GENERATED_EVIDENCE:
        return True
    return repo_path(p).exists()


def validate_graph(errors: list[str], stats: dict) -> dict:
    spec = importlib.util.spec_from_file_location('build_graph', GRAPH / 'tools' / 'build_graph.py')
    mod = importlib.util.module_from_spec(spec)
    sys.modules['build_graph'] = mod
    spec.loader.exec_module(mod)
    graph = json.loads((GRAPH / 'master-project-graph.json').read_text(encoding='utf-8'))
    try:
        fresh = mod.build()
        rendered = mod.render(fresh)
        for name, text in rendered.items():
            if (GRAPH / name).read_text(encoding='utf-8') != text:
                errors.append(f'graph output stale: docs/graph/{name} (run tools/build_graph.py)')
    except SystemExit as exc:
        errors.append(f'graph build failed: {exc}')
    nodes = {}
    for n in graph['nodes']:
        if n['id'] in nodes:
            errors.append(f'graph: duplicate node {n["id"]}')
        nodes[n['id']] = n
        if n['kind'] == 'issue':
            if n.get('status') not in ISSUE_STATUS:
                errors.append(f'graph {n["id"]}: bad issue status {n.get("status")}')
        elif n['kind'] != 'branch' and n['kind'] != 'project' and n.get('status') not in NODE_STATUS:
            errors.append(f'graph {n["id"]}: bad status {n.get("status")}')
        if n['kind'] not in ('branch',) and not n.get('evidence'):
            errors.append(f'graph {n["id"]}: no evidence')
        for p in n.get('evidence', []):
            if not evidence_exists(p):
                errors.append(f'graph {n["id"]}: evidence path missing {p}')
    branches = [n for n in nodes.values() if n['kind'] == 'branch']
    if len(branches) != 25:
        errors.append(f'graph: expected 25 branches, found {len(branches)}')
    for n in nodes.values():
        p = n.get('parent')
        if n['id'] == 'ECOSYS-ROOT':
            continue
        if p not in nodes:
            errors.append(f'graph {n["id"]}: unknown parent {p}')
    for n in nodes.values():
        seen, cur = set(), n['id']
        while cur != 'ECOSYS-ROOT':
            if cur in seen or cur not in nodes or not nodes[cur].get('parent'):
                errors.append(f'graph {n["id"]}: not reachable from ECOSYS-ROOT')
                break
            seen.add(cur)
            cur = nodes[cur]['parent']
    for e in graph['edges']:
        if e['from'] not in nodes or e['to'] not in nodes:
            errors.append(f'graph edge endpoint missing: {e["from"]} -> {e["to"]}')
        if e['type'] not in EDGE_TYPES:
            errors.append(f'graph edge type not allowed: {e["type"]}')
        if not e.get('evidence'):
            errors.append(f'graph edge without evidence: {e["from"]} -> {e["to"]}')
        for p in e.get('evidence', []):
            if not evidence_exists(p):
                errors.append(f'graph edge evidence missing: {p}')
    reg = {f['id'] for f in json.loads((GRAPH / 'feature-registry.json').read_text())['features']}
    missing_feat = reg - set(nodes)
    if missing_feat:
        errors.append(f'graph omits registry nodes: {sorted(missing_feat)[:5]}')
    stats['graph_nodes'] = len(nodes)
    stats['graph_edges'] = len(graph['edges'])
    stats['graph_branches'] = len(branches)
    stats['graph_kinds'] = {k: v for k, v in sorted(__import__('collections').Counter(n['kind'] for n in nodes.values()).items())}
    return graph


def validate_traceability(errors: list[str], stats: dict, graph: dict) -> None:
    ids = {n['id'] for n in graph['nodes']}
    path = GRAPH / 'traceability-matrix.csv'
    rows = list(csv.DictReader(path.open(newline='', encoding='utf-8')))
    for row in rows:
        for col in ('feature_ids', 'screen_ids', 'action_ids', 'rule_ids', 'api_ids', 'service_ids', 'persona_id', 'journey_id'):
            for value in filter(None, re.split(r'[;,]', row.get(col, '') or '')):
                if value.strip() not in ids:
                    errors.append(f'traceability {row["journey_id"]}: unknown ID {value.strip()} in {col}')
        for p in filter(None, (row.get('test_evidence') or '').split(';')):
            if not repo_path(p).is_file():
                errors.append(f'traceability {row["journey_id"]}: missing test path {p}')
    stats['traceability_rows'] = len(rows)


STATUS_TAG = {'implemented': '[IMPLEMENTED]', 'partially-implemented': '[PARTIAL]', 'not-implemented': '[NOT IMPLEMENTED]',
              'unreachable': '[UNREACHABLE]', 'unknown': '[UNKNOWN]', 'broken': '[BROKEN]', 'mocked': '[MOCKED]'}


def validate_status_consistency(errors: list[str], stats: dict) -> None:
    """Status tags in PRODUCT.md / IMPLEMENTATION.md tables must equal the registry; spec paths must exist."""
    reg = {f['id']: f for f in json.loads((GRAPH / 'feature-registry.json').read_text(encoding='utf-8'))['features']}
    tag_re = re.compile(r'\[(?:IMPLEMENTED|PARTIAL|NOT IMPLEMENTED|UNREACHABLE|UNKNOWN|BROKEN|MOCKED)\]')
    checked = 0
    for doc in ('docs/PRODUCT.md', 'docs/IMPLEMENTATION.md'):
        path = repo_path(doc)
        if not path.exists():
            continue
        for n, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
            m = re.match(r'^\| `([A-Z0-9][A-Z0-9\-]*)`(?: Δ)? \|', line)
            if not m or m.group(1) not in reg:
                continue
            tag = tag_re.search(line)
            if tag:
                checked += 1
                want = STATUS_TAG[reg[m.group(1)]['implementation_status']]
                if tag.group(0) != want:
                    errors.append(f'{doc}:{n}: {m.group(1)} status {tag.group(0)} differs from registry {want}')
    for f in reg.values():
        for s in f.get('spec', []):
            token = s.split(' ')[0]
            if token.endswith(('.md', '.json', '.csv')) and not repo_path(token).exists():
                errors.append(f'registry {f["id"]}: spec path missing {token}')
            if re.search(r'docs/(product-blueprint|audit|archive|knowledge|ui-)', s):
                errors.append(f'registry {f["id"]}: spec references a retired document: {s}')
    stats['status_tags_checked_against_registry'] = checked


def validate_issue_table(errors: list[str], stats: dict) -> None:
    """IMPLEMENTATION.md section 4 issue statuses must equal docs/graph/issue-register.json."""
    issues = {i['id']: i for i in json.loads((GRAPH / 'issue-register.json').read_text(encoding='utf-8'))['issues']}
    path = repo_path('docs/IMPLEMENTATION.md')
    if not path.exists():
        return
    text = path.read_text(encoding='utf-8')
    a = text.index('## 4. Issue register')
    b = text.index('## 5.', a)
    seen: dict[str, tuple[str, str]] = {}
    for line in text[a:b].splitlines():
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        if len(cells) >= 5 and re.match(r'^`A\d-\d+`$', cells[0]) and re.match(r'^P[0-4]$', cells[1]):
            status = [c for c in cells[2:] if c in ('FIXED', 'PARTIAL', 'OPEN', 'UNCONFIRMED')]
            if status:
                seen[cells[0].strip('`')] = (cells[1], status[0])
    for iid, issue in issues.items():
        got = seen.get(iid)
        if got is None:
            errors.append(f'issue {iid} has no status row in IMPLEMENTATION.md section 4')
        elif got != (issue['severity'], issue['resolution']):
            errors.append(f'issue {iid}: IMPLEMENTATION.md says {got}, register says {(issue["severity"], issue["resolution"])}')
    counts = {k: sum(1 for i in issues.values() if i['resolution'] == k) for k in ('FIXED', 'PARTIAL', 'OPEN', 'UNCONFIRMED')}
    total_row = re.search(r'\| Total \| (\d+) \| (\d+) \| (\d+) \| (\d+) \| (\d+) \| (\d+) \| (\d+) \| (\d+) \| (\d+) \|', text)
    if total_row:
        doc_counts = tuple(int(x) for x in total_row.groups()[5:])
        want = (counts['FIXED'], counts['PARTIAL'], counts['OPEN'], counts['UNCONFIRMED'])
        if doc_counts != want:
            errors.append(f'IMPLEMENTATION.md issue totals {doc_counts} differ from register {want}')
    stats['issue_rows_checked'] = len(seen)


def validate_nano(errors: list[str], stats: dict) -> None:
    units = json.loads((GRAPH / 'nano-units.json').read_text(encoding='utf-8'))['units']
    product = repo_path('docs/PRODUCT.md')
    text = product.read_text(encoding='utf-8') if product.exists() else ''
    for u in units:
        if f"`{u['id']}`" not in text:
            errors.append(f"nano unit {u['id']} is not documented in PRODUCT.md section 5.5")
    stats['nano_units'] = len(units)


def validate_issues(errors: list[str], stats: dict) -> None:
    issues = json.loads((GRAPH / 'issue-register.json').read_text(encoding='utf-8'))['issues']
    implementation = repo_path('docs/IMPLEMENTATION.md')
    text = implementation.read_text(encoding='utf-8') if implementation.exists() else ''
    ids = set()
    for i in issues:
        if i['id'] in ids:
            errors.append(f'issue register: duplicate {i["id"]}')
        ids.add(i['id'])
        if i['resolution'] not in ISSUE_STATUS:
            errors.append(f'issue {i["id"]}: bad resolution {i["resolution"]}')
        if i['id'] not in text:
            errors.append(f'issue {i["id"]} is not documented in IMPLEMENTATION.md')
    stats['issues'] = len(issues)
    stats['issues_by_resolution'] = dict(__import__('collections').Counter(i['resolution'] for i in issues))


def validate_migration(errors: list[str], stats: dict) -> None:
    path = GRAPH / 'migration-matrix.csv'
    if not path.exists():
        errors.append('docs/graph/migration-matrix.csv is missing')
        return
    cache: dict[str, set[str]] = {}
    rows = list(csv.DictReader(path.open(newline='', encoding='utf-8')))
    for r in rows:
        dest = r['destination_doc']
        if r['preservation_status'] == 'kept-in-place':
            if not repo_path(dest).exists() or dest.startswith(RETIRED_PREFIXES):
                errors.append(f'migration {r["item_id"]}: kept-in-place file missing or retired: {dest}')
            continue
        if r['preservation_status'].startswith('dropped'):
            if not r.get('rationale'):
                errors.append(f'migration {r["item_id"]}: dropped without rationale')
            continue
        destination = repo_path(dest)
        if not destination.exists():
            errors.append(f'migration {r["item_id"]}: destination {dest} missing')
            continue
        anchors = cache.setdefault(dest, headings(destination))
        if r['anchor'] not in anchors:
            errors.append(f'migration {r["item_id"]}: anchor #{r["anchor"]} not in {dest}')
    stats['migration_rows'] = len(rows)
    stats['migration_by_status'] = dict(__import__('collections').Counter(r['preservation_status'] for r in rows))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--format', choices=('text', 'json'), default='text')
    args = parser.parse_args()
    errors: list[str] = []
    stats: dict = {}
    validate_markdown(errors, stats)
    validate_registry(errors, stats)
    graph = validate_graph(errors, stats)
    validate_traceability(errors, stats, graph)
    validate_status_consistency(errors, stats)
    validate_issue_table(errors, stats)
    validate_nano(errors, stats)
    validate_issues(errors, stats)
    validate_migration(errors, stats)
    report = {'passed': not errors, 'stats': stats, 'errors': errors}
    if args.format == 'json':
        print(json.dumps(report, indent=2))
    else:
        print('PASS' if not errors else f'FAIL ({len(errors)} errors)')
        for k, v in stats.items():
            print(f'  {k}: {v}')
        for e in errors[:200]:
            print('ERROR:', e)
    return 0 if not errors else 1


if __name__ == '__main__':
    sys.exit(main())
