#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';
import { pathToFileURL } from 'node:url';

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const encode = value => encodeURIComponent(value);

const component = ({ group, name, version, purl }) => ({
  type: 'library',
  'bom-ref': purl,
  ...(group ? { group } : {}),
  name,
  version,
  purl,
});

export function parseGradleLock(raw, { configuration = null } = {}) {
  if (
    configuration !== null &&
    (typeof configuration !== 'string' ||
      !/^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/u.test(configuration))
  ) {
    throw new Error('Gradle configuration filter is invalid');
  }
  const components = new Map();
  for (const [index, rawLine] of raw.split(/\r?\n/u).entries()) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#') || line.startsWith('empty=')) continue;
    const separator = line.indexOf('=');
    if (separator < 0) {
      throw new Error(`invalid Gradle lock coordinate on line ${index + 1}`);
    }
    const coordinate = line.slice(0, separator);
    const lockedConfigurations = line.slice(separator + 1).split(',');
    if (
      configuration !== null &&
      !lockedConfigurations.includes(configuration)
    ) {
      continue;
    }
    const parts = coordinate.split(':');
    if (parts.length !== 3 || parts.some(part => part.length === 0)) {
      throw new Error(`invalid Gradle lock coordinate on line ${index + 1}`);
    }
    const [group, name, version] = parts;
    const purl = `pkg:maven/${encode(group)}/${encode(name)}@${encode(
      version,
    )}`;
    components.set(purl, component({ group, name, version, purl }));
  }
  if (components.size === 0) {
    throw new Error(
      configuration === null
        ? 'Gradle lock has no components'
        : 'Gradle lock has no components for the selected configuration',
    );
  }
  return [...components.values()].sort((left, right) =>
    left['bom-ref'].localeCompare(right['bom-ref']),
  );
}

export function parseCocoaPodsLock(raw) {
  const components = new Map();
  let inPods = false;
  for (const [index, line] of raw.split(/\r?\n/u).entries()) {
    if (/^[A-Z][A-Z ]+:\s*$/u.test(line)) {
      inPods = line === 'PODS:';
      continue;
    }
    if (!inPods || !line.startsWith('  - ') || line.startsWith('    - ')) {
      continue;
    }
    const rawComponent = line.slice(4).replace(/:$/u, '');
    const decodedComponent = rawComponent.startsWith('"')
      ? (() => {
          try {
            return JSON.parse(rawComponent);
          } catch {
            return null;
          }
        })()
      : rawComponent;
    const match =
      typeof decodedComponent === 'string'
        ? /^(.+?) \(([^()\s]+)\)$/u.exec(decodedComponent)
        : null;
    if (match === null) {
      throw new Error(`invalid CocoaPods lock component on line ${index + 1}`);
    }
    // CocoaPods lockfiles list subspecs (for example Firebase/Auth) as PODS
    // entries, but vulnerability and license databases identify the owning
    // pod. Canonicalize every subspec to its root pod and de-duplicate it.
    const name = match[1].split('/', 1)[0];
    const version = match[2];
    const purl = `pkg:cocoapods/${encode(name)}@${encode(version)}`;
    components.set(purl, component({ name, version, purl }));
  }
  if (components.size === 0)
    throw new Error('CocoaPods lock has no components');
  return [...components.values()].sort((left, right) =>
    left['bom-ref'].localeCompare(right['bom-ref']),
  );
}

export function createNativeSbom({
  kind,
  lockBytes,
  applicationName,
  version,
  configuration = null,
}) {
  const raw = lockBytes.toString('utf8');
  const components =
    kind === 'gradle'
      ? parseGradleLock(raw, { configuration })
      : kind === 'cocoapods'
        ? parseCocoaPodsLock(raw)
        : (() => {
            throw new Error('kind must be gradle or cocoapods');
          })();
  return {
    bomFormat: 'CycloneDX',
    specVersion: '1.6',
    version: 1,
    metadata: {
      component: {
        type: 'application',
        name: applicationName,
        version,
      },
      properties: [
        { name: 'birthday:dependency-manager', value: kind },
        { name: 'birthday:lockfile-sha256', value: sha256(lockBytes) },
        ...(configuration === null
          ? []
          : [
              {
                name: 'birthday:gradle-configuration',
                value: configuration,
              },
            ]),
      ],
    },
    components,
  };
}

const parseArguments = argv => {
  if (argv.length % 2 !== 0) throw new Error('arguments must be pairs');
  const allowed = new Set([
    'kind',
    'lock',
    'output',
    'application-name',
    'version',
    'configuration',
  ]);
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    if (!flag?.startsWith('--')) throw new Error('invalid argument');
    const name = flag.slice(2);
    if (!allowed.has(name) || values.has(name)) {
      throw new Error(`unsupported or duplicate argument ${flag}`);
    }
    values.set(name, argv[index + 1]);
  }
  for (const name of [...allowed].filter(
    option => option !== 'configuration',
  )) {
    if (!values.has(name)) throw new Error(`missing --${name}`);
  }
  if (values.get('kind') !== 'gradle' && values.has('configuration')) {
    throw new Error('--configuration is valid only for Gradle');
  }
  return values;
};

const direct =
  process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href;

if (direct) {
  try {
    const values = parseArguments(process.argv.slice(2));
    const lockBytes = readFileSync(values.get('lock'));
    if (lockBytes.length === 0 || lockBytes.length > 5 * 1024 * 1024) {
      throw new Error('lock file has an invalid size');
    }
    const output = createNativeSbom({
      kind: values.get('kind'),
      lockBytes,
      applicationName: values.get('application-name'),
      version: values.get('version'),
      configuration: values.get('configuration') ?? null,
    });
    writeFileSync(
      values.get('output'),
      `${JSON.stringify(output, null, 2)}\n`,
      {
        encoding: 'utf8',
        flag: 'wx',
        mode: 0o600,
      },
    );
  } catch (error) {
    process.stderr.write(
      `FAIL ${
        error instanceof Error ? error.message : 'SBOM generation failed'
      }\n`,
    );
    process.exitCode = 1;
  }
}
