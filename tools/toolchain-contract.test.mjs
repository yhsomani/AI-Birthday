import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import {
  chmodSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { TOOLCHAIN_VERSIONS } from './toolchain-versions.mjs';

const read = file => readFileSync(file, 'utf8');

test('mobile Node and npm pins agree across install contracts', () => {
  const packageJson = JSON.parse(read('package.json'));
  const packageLock = JSON.parse(read('package-lock.json'));

  assert.equal(read('.nvmrc').trim(), TOOLCHAIN_VERSIONS.node);
  assert.equal(packageJson.packageManager, `npm@${TOOLCHAIN_VERSIONS.npm}`);
  assert.deepEqual(packageJson.engines, {
    node: TOOLCHAIN_VERSIONS.node,
    npm: TOOLCHAIN_VERSIONS.npm,
  });
  assert.deepEqual(packageLock.packages[''].engines, packageJson.engines);
  assert.equal(
    packageJson.scripts['codegen:check'],
    'node tools/patch-react-native-codegen.mjs --check',
  );
  assert.equal(
    packageJson.scripts['ios:pods'],
    'cd ios && bundle exec pod install --deployment',
  );
});

test('Ruby, Bundler, and CocoaPods pins agree with the lockfile', () => {
  const gemfile = read('Gemfile');
  const lockfile = read('Gemfile.lock');
  const podLockfile = read('ios/Podfile.lock');

  assert.equal(read('.ruby-version').trim(), TOOLCHAIN_VERSIONS.ruby);
  assert.match(
    gemfile,
    new RegExp(
      `^ruby '${TOOLCHAIN_VERSIONS.ruby.replaceAll('.', '\\.')}'$`,
      'mu',
    ),
  );
  assert.match(
    gemfile,
    new RegExp(
      `gem 'cocoapods', '${TOOLCHAIN_VERSIONS.cocoaPods.replaceAll(
        '.',
        '\\.',
      )}'`,
    ),
  );
  assert.match(
    lockfile,
    new RegExp(
      `RUBY VERSION\\n  ruby ${TOOLCHAIN_VERSIONS.ruby.replaceAll('.', '\\.')}`,
    ),
  );
  assert.match(
    lockfile,
    new RegExp(
      `BUNDLED WITH\\n  ${TOOLCHAIN_VERSIONS.bundler.replaceAll('.', '\\.')}`,
    ),
  );
  assert.match(
    podLockfile,
    new RegExp(
      `COCOAPODS: ${TOOLCHAIN_VERSIONS.cocoaPods.replaceAll('.', '\\.')}$`,
      'mu',
    ),
  );
});

test('iOS bundle wrapper handles spaces and rejects a mismatched Node', () => {
  const temporaryRoot = mkdtempSync(
    path.join(os.tmpdir(), 'birthday-ios-toolchain-'),
  );
  const fixtureRoot = path.join(temporaryRoot, 'fixture with spaces');
  const nodeBinary = path.join(fixtureRoot, 'node binary');
  const reactNativeRoot = path.join(fixtureRoot, 'react native');
  const reactNativeScript = path.join(
    reactNativeRoot,
    'scripts',
    'react-native-xcode.sh',
  );
  mkdirSync(path.dirname(reactNativeScript), { recursive: true });

  try {
    writeFileSync(nodeBinary, `#!/bin/sh\nprintf '%s\\n' 'v24.18.0'\n`);
    writeFileSync(
      reactNativeScript,
      `#!/bin/sh\nprintf '%s\\n' 'bundle-called'\n`,
    );
    chmodSync(nodeBinary, 0o755);
    chmodSync(reactNativeScript, 0o755);

    const valid = spawnSync('sh', ['ios/scripts/bundle-react-native.sh'], {
      encoding: 'utf8',
      env: {
        ...process.env,
        NODE_BINARY: nodeBinary,
        REACT_NATIVE_PATH: reactNativeRoot,
      },
    });
    assert.equal(valid.status, 0, valid.stderr);
    assert.equal(valid.stdout.trim(), 'bundle-called');

    writeFileSync(nodeBinary, `#!/bin/sh\nprintf '%s\\n' 'v20.19.6'\n`);
    const invalid = spawnSync('sh', ['ios/scripts/bundle-react-native.sh'], {
      encoding: 'utf8',
      env: {
        ...process.env,
        NODE_BINARY: nodeBinary,
        REACT_NATIVE_PATH: reactNativeRoot,
      },
    });
    assert.equal(invalid.status, 1);
    assert.match(invalid.stderr, /requires Node v24\.18\.0/u);
  } finally {
    rmSync(temporaryRoot, { recursive: true, force: true });
  }
});

test('iOS build and CI reject toolchain drift and build a real app target', () => {
  const workflow = read('.github/workflows/ci.yml');
  const bundleScript = read('ios/scripts/bundle-react-native.sh');
  const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
  const scheme = read(
    'ios/BirthdayAutopilot.xcodeproj/xcshareddata/xcschemes/BirthdayAutopilot.xcscheme',
  );

  assert.match(workflow, /runs-on: macos-26/u);
  assert.match(
    workflow,
    new RegExp(`Xcode_${TOOLCHAIN_VERSIONS.xcode.replaceAll('.', '\\.')}`),
  );
  assert.match(workflow, /ruby\/setup-ruby@[a-f0-9]{40} # v1/u);
  assert.match(workflow, /npm run ios:pods/u);
  assert.match(workflow, /npm run doctor:ios/u);
  assert.match(workflow, /-workspace ios\/BirthdayAutopilot\.xcworkspace/u);
  assert.match(workflow, /CODE_SIGNING_ALLOWED=NO/u);
  assert.match(workflow, /BIRTHDAY_FIREBASE_CONFIG_REQUIRED=NO/u);

  assert.match(
    bundleScript,
    new RegExp(`v${TOOLCHAIN_VERSIONS.node.replaceAll('.', '\\.')}`),
  );
  assert.match(project, /scripts\/bundle-react-native\.sh/u);
  assert.doesNotMatch(scheme, /BirthdayAutopilotTests/u);
});

test('formatting ignores generated Bundler gems but not repository-owned vendor source', () => {
  const ignored = new Set(
    read('.prettierignore').split(/\r?\n/u).filter(Boolean),
  );

  assert.ok(ignored.has('/vendor/bundle/'));
  assert.ok(!ignored.has('vendor/'));
  assert.ok(!ignored.has('/vendor/'));
});
