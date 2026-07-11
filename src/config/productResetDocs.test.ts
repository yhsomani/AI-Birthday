import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { describe, it } from 'node:test';

import { supportedHarnessCommandTypes } from '../application/commandCatalog';

const rootDir = process.cwd();
const readDoc = (path: string) => readFileSync(join(rootDir, path), 'utf8');

const assessmentPaths = [
  'SSOT.md',
  'docs/product-reset/README.md',
  'docs/product-reset/current-product-assessment.md',
  'docs/product-reset/user-experience-and-ideal-product.md',
  'docs/product-reset/technical-rebuild-assessment.md',
  'docs/product-reset/product-vision-and-roadmap.md'
] as const;

describe('product reset documentation contract', () => {
  it('provides an indexed artifact for all 20 requested deliverables', () => {
    assessmentPaths.forEach(path => {
      assert.equal(existsSync(join(rootDir, path)), true, `${path} must exist`);

      const source = readDoc(path);
      for (const match of source.matchAll(/\[[^\]]+\]\(([^)]+)\)/g)) {
        const target = match[1];
        if (!target || /^(?:https?:|mailto:|#)/.test(target)) {
          continue;
        }
        const relativeTarget = decodeURIComponent(target.split('#')[0] ?? '');
        const resolvedTarget = resolve(dirname(join(rootDir, path)), relativeTarget);
        assert.equal(existsSync(resolvedTarget), true, `${path} links to missing ${target}`);
      }
    });

    const index = readDoc('docs/product-reset/README.md');
    const indexedDeliverables = [...index.matchAll(/^\|\s+(\d+)\s+\|/gm)].map(match => Number(match[1]));

    assert.deepEqual(
      indexedDeliverables,
      Array.from({ length: 20 }, (_, indexValue) => indexValue + 1)
    );
  });

  it('scores every inventoried capability across all five required dimensions', () => {
    const resetIndex = readDoc('docs/product-reset/README.md');
    const legacyFeatureIndex = readDoc('docs/feature-fssot.md')
      .split('## Feature Index')[1]
      ?.split('## 1. App Entry')[0];
    const assessment = readDoc('docs/product-reset/current-product-assessment.md');
    const inventory = assessment
      .split('## Exhaustive feature inventory')[1]
      ?.split('## End-to-end workflow assessment')[0];

    assert.ok(inventory, 'the exhaustive feature inventory must be bounded');
    assert.ok(legacyFeatureIndex, 'the legacy feature index must be bounded');
    assert.equal(supportedHarnessCommandTypes.length, 141);
    assert.equal((legacyFeatureIndex.match(/^\d+(?:[A-Z])?\. /gm) ?? []).length, 28);
    assert.match(resetIndex, /141 strict commands across 28 previously documented feature areas/);
    [
      'Feature',
      'Current state and evidence',
      'Business outcome',
      'User outcome',
      'Gap or mismatch',
      'Decision',
      'B/U/UX/C/S'
    ].forEach(column => assert.match(inventory, new RegExp(`\\| ${column}`)));

    const scoreRows = [...inventory.matchAll(/\|\s+((?:10|\d)\/(?:10|\d)\/(?:10|\d)\/(?:10|\d)\/(?:10|\d))\s+\|$/gm)];

    assert.equal(scoreRows.length, 68, 'every one of the 68 inventoried capabilities must have five scores');
    scoreRows.forEach(scoreRow => {
      const scores = scoreRow[1]?.split('/').map(Number) ?? [];
      assert.equal(scores.length, 5);
      scores.forEach(score => {
        assert.ok(score >= 0 && score <= 10, `invalid feature score: ${score}`);
      });
    });
  });

  it('keeps one product authority with complete feature contracts', () => {
    const ssot = readDoc('SSOT.md');
    const docsIndex = readDoc('docs/README.md');
    const contracts = ssot.split('## 9. Feature Contracts')[1]?.split('## 10. Cross-Feature Business Rules')[0];

    assert.match(docsIndex, /SSOT\.md` is the only normative business and end-user product scope/);
    assert.match(ssot, /Status: \*\*normative product reset baseline; product hypotheses require validation\*\*/);
    assert.match(ssot, /Decision owner: RelateAI product owner/);
    assert.ok(contracts, 'feature contracts must be bounded in the SSOT');
    assert.equal((contracts.match(/^### 9\.\d+ /gm) ?? []).length, 10);

    [
      'Why it exists',
      'Who uses it',
      'Problem',
      'Expected behavior',
      'Business value',
      'User value',
      'Success criteria',
      'Acceptance criteria'
    ].forEach(label => {
      const occurrences = contracts.match(new RegExp(`\\*\\*${label}:`, 'g')) ?? [];
      assert.equal(occurrences.length, 10, `every SSOT feature contract must define ${label}`);
    });
  });

  it('keeps the reset vocabulary, sequence, and historical-document status aligned', () => {
    const ssot = readDoc('SSOT.md');
    const navigation = ssot.split('### Primary navigation')[1]?.split('### Today')[0];
    const experience = readDoc('docs/product-reset/user-experience-and-ideal-product.md');
    const technical = readDoc('docs/product-reset/technical-rebuild-assessment.md');

    assert.ok(navigation, 'primary navigation must be bounded in the SSOT');
    assert.match(navigation, /1\. \*\*Today\*\*/);
    assert.match(navigation, /2\. \*\*Moments\*\*/);
    assert.match(navigation, /3\. \*\*People\*\*/);
    assert.doesNotMatch(navigation, /4\.|\*\*Messages\*\*/);
    assert.match(experience, /three primary destinations/);
    assert.doesNotMatch(
      experience,
      /26 feature areas|### Primary personas|\| Business value \| User value \| Usability now/
    );
    const experienceIa = experience.split('## Ideal Information Architecture')[1]?.split('### Navigation rules')[0];
    assert.ok(experienceIa, 'the ideal information architecture must be bounded');
    assert.ok(experienceIa.indexOf('Today') < experienceIa.indexOf('Moments'));
    assert.ok(experienceIa.indexOf('Moments') < experienceIa.indexOf('People'));
    assert.match(ssot, /Before any production vertical-slice implementation:/);
    assert.match(technical, /\|\s+1\. Figma experience proof\s+\|/);
    assert.match(technical, /\|\s+2\. Architecture foundation\s+\|/);
    assert.match(technical, /243 `\.ts`\/`\.tsx` files/);
    assert.match(technical, /846 passed, 0 failed, across 123 suites/);
    assert.doesNotMatch(technical, /147 commands|147-command/);

    ['docs/feature-fssot.md', 'docs/feature-roadmap-analysis.md'].forEach(path => {
      assert.match(readDoc(path), /Superseded on 2026-07-11/);
    });
    assert.match(readDoc('docs/react-native-migration-status.md'), /implementation and release status only/);
  });
});
