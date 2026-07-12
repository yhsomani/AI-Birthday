import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import {
  COCOAPODS_NEW_ARCHITECTURE_PATH,
  CODEGEN_EXECUTOR_PATH,
  patchCocoaPodsNewArchitectureSource,
  patchCodegenSource,
  patchInstalledReactNative,
  SUPPORTED_REACT_NATIVE_VERSION,
  verifyInstalledReactNativeCodegen,
} from './patch-react-native-codegen.mjs';

const vulnerableSource = `
const {execSync} = require('child_process');
  const xcodeproj = String(
    execSync(\`find \${resolvedAppPath} -type d -name "*.xcodeproj"\`),
  )
  const jsFiles = '-name "Native*.js" -or -name "*NativeComponent.js"';
  const tsFiles = '-name "Native*.ts" -or -name "*NativeComponent.ts"';
  const findCommand = \`find \${path.join(resolvedAppPath, jsSrcsDir)} -type f -not -path "*/__mocks__/*" -and \\\\( \${jsFiles} -or \${tsFiles} \\\\)\`;
  const list = String(execSync(findCommand))
`;

const vulnerableCocoaPodsSource = `
            infoPlistFiles = \`find #{projectFolderPath} -name "Info.plist"\`
            infoPlistFiles = infoPlistFiles.split("\\n").map { |f| f.strip }
`;

test('replaces shell interpolation with argument-safe find calls', () => {
  const result = patchCodegenSource(vulnerableSource);

  assert.equal(result.changed, true);
  assert.match(result.source, /execFileSync\('find', \[/);
  assert.match(result.source, /execFileSync\('find', findArgs\)/);
  assert.doesNotMatch(result.source, /execSync/);
  assert.doesNotMatch(result.source, /find \$\{resolvedAppPath\}/);
});

test('is idempotent', () => {
  const first = patchCodegenSource(vulnerableSource);
  const second = patchCodegenSource(first.source);

  assert.equal(second.changed, false);
  assert.equal(second.source, first.source);
});

test('makes the CocoaPods Info.plist lookup safe for space-containing paths', () => {
  const first = patchCocoaPodsNewArchitectureSource(vulnerableCocoaPodsSource);
  const second = patchCocoaPodsNewArchitectureSource(first.source);

  assert.equal(first.changed, true);
  assert.match(first.source, /Dir\.glob/u);
  assert.match(first.source, /File\.join\(projectFolderPath/u);
  assert.doesNotMatch(first.source, /`find/u);
  assert.equal(second.changed, false);
});

test('fails closed when upstream source no longer matches', () => {
  assert.throws(
    () => patchCodegenSource("const {execSync} = require('child_process');"),
    /expected one Xcode project lookup block, found 0/,
  );
});

test('patches only the pinned React Native version', () => {
  const temporaryRoot = fs.mkdtempSync(
    path.join(os.tmpdir(), 'birthday-codegen-patch-'),
  );
  const root = path.join(temporaryRoot, 'project with spaces');
  const reactNativeRoot = path.join(root, 'node_modules', 'react-native');
  const executorPath = path.join(reactNativeRoot, CODEGEN_EXECUTOR_PATH);
  const newArchitecturePath = path.join(
    reactNativeRoot,
    COCOAPODS_NEW_ARCHITECTURE_PATH,
  );
  fs.mkdirSync(path.dirname(executorPath), { recursive: true });
  fs.mkdirSync(path.dirname(newArchitecturePath), { recursive: true });
  fs.writeFileSync(
    path.join(reactNativeRoot, 'package.json'),
    JSON.stringify({ version: SUPPORTED_REACT_NATIVE_VERSION }),
  );
  fs.writeFileSync(executorPath, vulnerableSource);
  fs.writeFileSync(newArchitecturePath, vulnerableCocoaPodsSource);

  try {
    assert.throws(
      () => verifyInstalledReactNativeCodegen(root),
      /React Native iOS tooling is not path-safe/,
    );
    assert.equal(patchInstalledReactNative(root).changed, true);
    assert.equal(patchInstalledReactNative(root).changed, false);
    assert.equal(
      verifyInstalledReactNativeCodegen(root).executorPath,
      executorPath,
    );

    fs.writeFileSync(
      path.join(reactNativeRoot, 'package.json'),
      JSON.stringify({ version: '0.87.0' }),
    );
    assert.throws(
      () => patchInstalledReactNative(root),
      /Refusing to patch react-native 0\.87\.0/,
    );
  } finally {
    fs.rmSync(temporaryRoot, { recursive: true, force: true });
  }
});
