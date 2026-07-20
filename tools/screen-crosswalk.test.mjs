import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const read = file => readFileSync(path.join(projectRoot, file), 'utf8');
const exists = file => existsSync(path.join(projectRoot, file));

const resolveProductionImport = (sourceFile, specifier) => {
  const base = path.normalize(path.join(path.dirname(sourceFile), specifier));
  return [
    `${base}.ts`,
    `${base}.tsx`,
    path.join(base, 'index.ts'),
    path.join(base, 'index.tsx'),
  ].find(exists);
};

const reachableProductionModules = root => {
  const reachable = new Set();
  const pending = [root];
  while (pending.length > 0) {
    const file = pending.pop();
    if (!file || reachable.has(file)) continue;
    reachable.add(file);
    const source = read(file);
    const specifiers = [
      ...source.matchAll(/\bfrom\s+['"](\.[^'"]+)['"]/gu),
      ...source.matchAll(/\bimport\s+['"](\.[^'"]+)['"]/gu),
    ].map(match => match[1]);
    for (const specifier of specifiers) {
      const dependency = resolveProductionImport(file, specifier);
      if (dependency && !reachable.has(dependency)) pending.push(dependency);
    }
  }
  return reachable;
};

test('every Stitch screen ID maps exactly once to production ownership and nearby contracts', () => {
  const manifestIds = [
    ...read('stitch/SCREEN_MANIFEST.md').matchAll(/^\| ([A-Z][0-9]{2}) \|/gmu),
  ].map(match => match[1]);
  const crosswalk = JSON.parse(read('stitch/IMPLEMENTATION_CROSSWALK.json'));
  const entries = crosswalk.entries;
  const mappedIds = entries.map(entry => entry.id);

  assert.equal(crosswalk.schemaVersion, 2);
  assert.equal(manifestIds.length, 63);
  assert.deepEqual(new Set(mappedIds).size, mappedIds.length);
  assert.deepEqual([...mappedIds].sort(), [...manifestIds].sort());

  for (const entry of entries) {
    assert.deepEqual(Object.keys(entry).sort(), [
      'id',
      'implementation',
      'nearestContractFiles',
      'platforms',
    ]);
    assert.match(entry.id, /^[A-Z][0-9]{2}$/u);
    assert.ok(entry.platforms.length > 0);
    assert.equal(new Set(entry.platforms).size, entry.platforms.length);
    assert.equal(
      entry.platforms.every(platform =>
        ['android', 'ios', 'web'].includes(platform),
      ),
      true,
    );
    assert.ok(entry.implementation.length > 0);
    assert.ok(entry.nearestContractFiles.length > 0);
    for (const file of [
      ...entry.implementation,
      ...entry.nearestContractFiles,
    ]) {
      assert.equal(exists(file), true, `${entry.id} missing ${file}`);
    }
    for (const file of entry.implementation) {
      assert.doesNotMatch(file, /fixtures?|Fixture/u);
      assert.match(file, /^(?:src|backend\/hosting\/src)\//u);
      assert.match(file, /\.(?:css|ts|tsx)$/u);
    }
    for (const file of entry.nearestContractFiles) {
      assert.match(file, /(?:\.test\.(?:mjs|ts|tsx)|\/test\/[^/]+\.mjs)$/u);
    }
  }

  for (const id of ['S16', 'L01', 'L02', 'L03']) {
    const entry = entries.find(candidate => candidate.id === id);
    assert.deepEqual(entry?.platforms, ['android'], id);
  }
});

test('crosswalk stays explicitly subordinate to physical and release evidence', () => {
  const crosswalk = JSON.parse(read('stitch/IMPLEMENTATION_CROSSWALK.json'));
  assert.equal(crosswalk.authority, 'PROJECT_ABOUT.md');
  assert.match(crosswalk.scope, /does not prove/iu);
  assert.match(crosswalk.scope, /not a test locator/iu);
  assert.match(crosswalk.scope, /physical-device UX/iu);
  assert.match(crosswalk.scope, /provider configuration/iu);
  assert.match(crosswalk.scope, /store approval/iu);
});

test('every mobile Stitch owner is reachable from the production app root', () => {
  const crosswalk = JSON.parse(read('stitch/IMPLEMENTATION_CROSSWALK.json'));
  const reachable = reachableProductionModules('src/app/AppRoot.tsx');
  const mobileOwners = new Set(
    crosswalk.entries
      .filter(entry =>
        entry.platforms.some(platform => ['android', 'ios'].includes(platform)),
      )
      .flatMap(entry => entry.implementation)
      .filter(file => file.startsWith('src/')),
  );

  for (const file of mobileOwners) {
    assert.equal(
      reachable.has(file),
      true,
      `${file} is mapped from Stitch but unreachable from AppRoot`,
    );
  }
});

test('high-risk Stitch owners contain their real production state and route anchors', () => {
  const crosswalk = JSON.parse(read('stitch/IMPLEMENTATION_CROSSWALK.json'));
  const entry = id => crosswalk.entries.find(candidate => candidate.id === id);
  const privacy = read('src/features/live/LivePrivacyScreen.tsx');
  const activity = read('src/features/live/LiveActivityScreen.tsx');
  const automation = read('src/features/live/LiveAutomationScreen.tsx');
  const composerReview = read('src/features/live/LiveComposerReviewScreen.tsx');
  const activationModel = read('src/domain/automation/model.ts');
  const shell = read('src/features/live/LiveAppShell.tsx');

  assert.deepEqual(entry('A06')?.implementation, [
    'src/features/live/LiveActivityScreen.tsx',
  ]);
  assert.match(activity, /kind: 'clear-activity'/u);
  assert.match(activity, /prepareAction/u);
  assert.match(activity, /confirmAction/u);
  assert.doesNotMatch(privacy, /kind: 'clear-activity'/u);

  assert.ok(
    entry('T06')?.implementation.includes(
      'src/features/live/LivePrivacyScreen.tsx',
    ),
  );
  for (const action of [
    'disconnect-contacts',
    'revoke-google-access',
    'sign-out-retain',
    'sign-out-wipe',
  ]) {
    assert.match(privacy, new RegExp(`kind: '${action}'`, 'u'));
  }
  assert.match(shell, /onOpenPrivacy=\{\(\) =>/u);
  assert.match(shell, /name="Privacy" component=\{LivePrivacyRoute\}/u);

  assert.match(activity, /activityDetailDisclosure/u);
  assert.match(activity, /composer-reported-sent/u);
  assert.match(activity, /composer-outcome-unknown/u);
  assert.match(activity, /live-activity-detail-ios-visibility/u);

  for (const field of [
    'plannedReminderCount',
    'reminderWindowLabel',
    'reminderHorizon',
    'coexistence',
    'contactsReady',
    'messageUiReady',
    'protectedStorageReady',
    'readiness',
  ]) {
    assert.match(activationModel, new RegExp(`\\b${field}\\b`, 'u'));
    assert.match(automation, new RegExp(`review\\.${field}`, 'u'));
  }
  assert.match(automation, /iosActivationSnapshotComplete/u);
  assert.match(automation, /live-ios-activation-snapshot-blocked/u);
  assert.doesNotMatch(automation, /live-open-composer/u);
  assert.match(composerReview, /live-open-composer/u);

  assert.deepEqual(entry('H03')?.implementation, [
    'src/features/live/LiveComposerReviewScreen.tsx',
    'src/features/live/LiveHomeScreen.tsx',
    'src/features/live/LiveMessageScreen.tsx',
  ]);
  assert.deepEqual(entry('H06')?.implementation, [
    'src/features/live/LiveComposerReviewScreen.tsx',
    'src/features/live/LiveHomeScreen.tsx',
  ]);
  assert.match(
    shell,
    /<Stack\.Screen[\s\S]{0,120}?name="ComposerReview"[\s\S]{0,120}?component=\{LiveComposerReviewRoute\}/u,
  );
});
