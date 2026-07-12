#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

import { TOOLCHAIN_VERSIONS } from './toolchain-versions.mjs';

export const SUPPORTED_REACT_NATIVE_VERSION = TOOLCHAIN_VERSIONS.reactNative;
export const CODEGEN_EXECUTOR_PATH = path.join(
  'scripts',
  'codegen',
  'generate-artifacts-executor',
  'generateReactCodegenPodspec.js',
);
export const COCOAPODS_NEW_ARCHITECTURE_PATH = path.join(
  'scripts',
  'cocoapods',
  'new_architecture.rb',
);

const CHILD_PROCESS_BEFORE = "const {execSync} = require('child_process');";
const CHILD_PROCESS_AFTER = "const {execFileSync} = require('child_process');";

const XCODE_PROJECT_FIND_BEFORE = `  const xcodeproj = String(
    execSync(\`find \${resolvedAppPath} -type d -name "*.xcodeproj"\`),
  )`;
const XCODE_PROJECT_FIND_AFTER = `  const xcodeproj = String(
    execFileSync('find', [
      resolvedAppPath,
      '-type',
      'd',
      '-name',
      '*.xcodeproj',
    ]),
  )`;

const CODEGEN_FILE_FIND_BEFORE = `  const jsFiles = '-name "Native*.js" -or -name "*NativeComponent.js"';
  const tsFiles = '-name "Native*.ts" -or -name "*NativeComponent.ts"';
  const findCommand = \`find \${path.join(resolvedAppPath, jsSrcsDir)} -type f -not -path "*/__mocks__/*" -and \\\\( \${jsFiles} -or \${tsFiles} \\\\)\`;
  const list = String(execSync(findCommand))`;
const CODEGEN_FILE_FIND_AFTER = `  const findArgs = [
    path.join(resolvedAppPath, jsSrcsDir),
    '-type',
    'f',
    '-not',
    '-path',
    '*/__mocks__/*',
    '-and',
    '(',
    '-name',
    'Native*.js',
    '-or',
    '-name',
    '*NativeComponent.js',
    '-or',
    '-name',
    'Native*.ts',
    '-or',
    '-name',
    '*NativeComponent.ts',
    ')',
  ];
  const list = String(execFileSync('find', findArgs))`;

const INFO_PLIST_FIND_BEFORE = `            infoPlistFiles = \`find #{projectFolderPath} -name "Info.plist"\`
            infoPlistFiles = infoPlistFiles.split("\\n").map { |f| f.strip }`;
const INFO_PLIST_FIND_AFTER = `            infoPlistFiles = Dir.glob(
                File.join(projectFolderPath, "**", "Info.plist")
            )`;

function replaceExactlyOnce(source, before, after, label) {
  const occurrences = source.split(before).length - 1;
  if (occurrences !== 1) {
    throw new Error(
      `Cannot safely patch React Native codegen: expected one ${label} block, found ${occurrences}.`,
    );
  }
  return source.replace(before, after);
}

export function patchCodegenSource(source) {
  const alreadyPatched =
    source.includes(CHILD_PROCESS_AFTER) &&
    source.includes(XCODE_PROJECT_FIND_AFTER) &&
    source.includes(CODEGEN_FILE_FIND_AFTER);

  if (alreadyPatched) {
    if (
      source.includes(XCODE_PROJECT_FIND_BEFORE) ||
      source.includes(CODEGEN_FILE_FIND_BEFORE)
    ) {
      throw new Error(
        'React Native codegen contains a mixture of patched and vulnerable find calls.',
      );
    }
    return { changed: false, source };
  }

  let patched = replaceExactlyOnce(
    source,
    CHILD_PROCESS_BEFORE,
    CHILD_PROCESS_AFTER,
    'child_process import',
  );
  patched = replaceExactlyOnce(
    patched,
    XCODE_PROJECT_FIND_BEFORE,
    XCODE_PROJECT_FIND_AFTER,
    'Xcode project lookup',
  );
  patched = replaceExactlyOnce(
    patched,
    CODEGEN_FILE_FIND_BEFORE,
    CODEGEN_FILE_FIND_AFTER,
    'codegen input lookup',
  );

  return { changed: true, source: patched };
}

export function patchCocoaPodsNewArchitectureSource(source) {
  if (source.includes(INFO_PLIST_FIND_AFTER)) {
    if (source.includes(INFO_PLIST_FIND_BEFORE)) {
      throw new Error(
        'React Native CocoaPods integration contains both patched and vulnerable Info.plist lookups.',
      );
    }
    return { changed: false, source };
  }

  return {
    changed: true,
    source: replaceExactlyOnce(
      source,
      INFO_PLIST_FIND_BEFORE,
      INFO_PLIST_FIND_AFTER,
      'CocoaPods Info.plist lookup',
    ),
  };
}

export function patchInstalledReactNative(projectRoot = process.cwd()) {
  const { executorPath, newArchitecturePath } =
    resolveInstalledCodegen(projectRoot);
  const codegenResult = patchCodegenSource(
    fs.readFileSync(executorPath, 'utf8'),
  );
  const cocoaPodsResult = patchCocoaPodsNewArchitectureSource(
    fs.readFileSync(newArchitecturePath, 'utf8'),
  );
  if (codegenResult.changed) {
    fs.writeFileSync(executorPath, codegenResult.source, 'utf8');
  }
  if (cocoaPodsResult.changed) {
    fs.writeFileSync(newArchitecturePath, cocoaPodsResult.source, 'utf8');
  }
  return {
    changed: codegenResult.changed || cocoaPodsResult.changed,
    executorPath,
    newArchitecturePath,
  };
}

function resolveInstalledCodegen(projectRoot) {
  const reactNativeRoot = path.join(
    projectRoot,
    'node_modules',
    'react-native',
  );
  const packageJsonPath = path.join(reactNativeRoot, 'package.json');
  const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));

  if (packageJson.version !== SUPPORTED_REACT_NATIVE_VERSION) {
    throw new Error(
      `Refusing to patch react-native ${packageJson.version}; expected ${SUPPORTED_REACT_NATIVE_VERSION}. Review or remove the compatibility patch before upgrading.`,
    );
  }

  const executorPath = path.join(reactNativeRoot, CODEGEN_EXECUTOR_PATH);
  const newArchitecturePath = path.join(
    reactNativeRoot,
    COCOAPODS_NEW_ARCHITECTURE_PATH,
  );
  return { executorPath, newArchitecturePath };
}

export function verifyInstalledReactNativeCodegen(projectRoot = process.cwd()) {
  const { executorPath, newArchitecturePath } =
    resolveInstalledCodegen(projectRoot);
  const source = fs.readFileSync(executorPath, 'utf8');
  const cocoaPodsSource = fs.readFileSync(newArchitecturePath, 'utf8');
  if (
    patchCodegenSource(source).changed ||
    patchCocoaPodsNewArchitectureSource(cocoaPodsSource).changed
  ) {
    throw new Error(
      'React Native iOS tooling is not path-safe. Run npm ci (or npm run postinstall) before CocoaPods or Xcode.',
    );
  }
  return { executorPath, newArchitecturePath };
}

const isMain =
  process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;

if (isMain) {
  if (process.argv.includes('--check')) {
    verifyInstalledReactNativeCodegen();
    process.stdout.write('PASS React Native iOS path-safety patches\n');
  } else {
    const result = patchInstalledReactNative();
    process.stdout.write(
      result.changed
        ? 'Applied the React Native iOS path-safety patches.\n'
        : 'React Native iOS path-safety patches are already applied.\n',
    );
  }
}
