#!/usr/bin/env python3
"""Dependency-free validator for the AI-Birthday Markdown knowledge base.

Example: python docs/knowledge/tools/validate_knowledge.py --format json
Runs locally; does not make network calls or mutate source files.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys
from urllib.parse import unquote


def validate(root: Path) -> dict:
    root = root.resolve()
    knowledge = root / 'docs' / 'knowledge'
    errors: list[str] = []
    markdown_files = sorted(knowledge.rglob('*.md'))
    checked_links = 0
    mermaid_blocks = 0

    for markdown in markdown_files:
        content = markdown.read_text(encoding='utf-8')
        name = markdown.relative_to(root).as_posix()
        fences = re.findall(r'^\s*(`{3,}|~{3,})', content, flags=re.MULTILINE)
        if len(fences) % 2:
            errors.append(f'{name}: unmatched Markdown code fence')
        mermaid_blocks += len(re.findall(r'^\s*```mermaid\s*$', content, flags=re.MULTILINE))
        for url in re.findall(r'\]\(([^)]+)\)', content):
            url = url.strip().split(' "', 1)[0].strip('<>')
            if url.startswith(('https://', 'http://', 'mailto:', 'tel:', '#', 'data:')):
                continue
            rel = unquote(url.split('#', 1)[0].split('?', 1)[0])
            if not rel:
                continue
            checked_links += 1
            target = (markdown.parent / rel).resolve()
            if not target.is_relative_to(root):
                errors.append(f'{name}: link escapes repository: {url}')
            elif not target.exists():
                errors.append(f'{name}: unresolved link: {url}')

    registry_path = knowledge / 'feature-registry.json'
    entries = []
    if registry_path.exists():
        try:
            document = json.loads(registry_path.read_text(encoding='utf-8'))
            entries = document.get('features', [])
            if not isinstance(entries, list):
                errors.append('feature-registry.json: features must be an array')
                entries = []
        except (json.JSONDecodeError, OSError) as exc:
            errors.append(f'feature-registry.json: cannot parse: {exc}')
        id_to_node: dict[str, dict] = {}
        for node in entries:
            if not isinstance(node, dict):
                errors.append('feature-registry.json: feature is not an object')
                continue
            feature_id = node.get('id')
            if not isinstance(feature_id, str) or not feature_id:
                errors.append('feature-registry.json: missing valid feature ID')
                continue
            if feature_id in id_to_node:
                errors.append(f'duplicate feature ID: {feature_id}')
            id_to_node[feature_id] = node
            if not node.get('name'):
                errors.append(f'{feature_id}: missing display name')
            for field in ('source', 'tests'):
                references = node.get(field, [])
                if not isinstance(references, list):
                    errors.append(f'{feature_id}: {field} must be a list')
                    continue
                for ref in references:
                    if not isinstance(ref, str):
                        errors.append(f'{feature_id}: nonstring {field} reference')
                    elif not (root / ref).is_file():
                        errors.append(f'{feature_id}: missing {field} reference {ref}')
        for feature_id, node in id_to_node.items():
            chain = {feature_id}
            parent = node.get('parent')
            while parent:
                if parent in chain:
                    errors.append(f'{feature_id}: cycle in parent hierarchy')
                    break
                chain.add(parent)
                parent_node = id_to_node.get(parent)
                if parent_node is None:
                    errors.append(f'{feature_id}: unknown parent {parent}')
                    break
                parent = parent_node.get('parent')
    else:
        errors.append('docs/knowledge/feature-registry.json is missing')

    return {
        'repository_root': str(root),
        'markdown_files_scanned': len(markdown_files),
        'relative_links_checked': checked_links,
        'mermaid_blocks_detected': mermaid_blocks,
        'feature_registry_nodes': len(entries),
        'errors': errors,
        'passed': len(errors) == 0,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[3],
                        help='Repository root (defaults to root containing this script)')
    parser.add_argument('--format', choices=('text', 'json'), default='text')
    args = parser.parse_args()
    report = validate(args.root)
    if args.format == 'json':
        print(json.dumps(report, indent=2))
    else:
        print(f"Markdown: {report['markdown_files_scanned']}; relative links: "
              f"{report['relative_links_checked']}; Mermaid blocks: "
              f"{report['mermaid_blocks_detected']}; feature nodes: "
              f"{report['feature_registry_nodes']}; errors: {len(report['errors'])}")
        for error in report['errors']:
            print('ERROR:', error)
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
