import assert from 'node:assert/strict';
import {
  createHash,
  generateKeyPairSync,
  sign as createSignature,
} from 'node:crypto';
import {
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { symlinksAvailable } from './test-capabilities.mjs';

import { createNativeSbom } from './generate-native-sbom.mjs';
import {
  OSV_API_ENDPOINT,
  buildNativeAdvisoryReport,
  parseArguments,
  prepareNativeTargets,
  queryOsvPurls,
  requestOsvBatch,
  validateExceptions,
  validatePurl,
  verifyCocoaPodsPodspecSources,
  writeNativeAdvisoryReport,
} from './scan-native-vulnerabilities.mjs';

const scanTime = new Date('2026-07-12T12:00:00.000Z');
const emptyExceptionBytes = Buffer.from(
  '{\n  "schemaVersion": 1,\n  "exceptions": []\n}\n',
);
const exceptionAuthority = generateKeyPairSync('ed25519');
const exceptionAuthorityPublicKeyBytes = Buffer.from(
  exceptionAuthority.publicKey.export({ format: 'pem', type: 'spki' }),
);
const exceptionAuthorityPin = {
  schemaVersion: 1,
  algorithm: 'Ed25519',
  publicKeySpkiSha256: createHash('sha256')
    .update(
      exceptionAuthority.publicKey.export({ format: 'der', type: 'spki' }),
    )
    .digest('hex'),
};

const signedExceptionInputs = exceptionBytes => ({
  detachedExceptionSignature: createSignature(
    null,
    exceptionBytes,
    exceptionAuthority.privateKey,
  ),
  exceptionAuthorityPublicKeyBytes,
  authorityPinDocument: exceptionAuthorityPin,
});

const createFixtureRoot = t => {
  const root = mkdtempSync(path.join(tmpdir(), 'birthday-native-osv-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  return root;
};

const writeGradleFixture = (
  t,
  { lock = 'com.example:library:1.2.3=runtime\n' } = {},
) => {
  const root = createFixtureRoot(t);
  const lockBytes = Buffer.from(lock);
  const sbom = createNativeSbom({
    kind: 'gradle',
    lockBytes,
    applicationName: 'Fixture',
    version: '1.0',
  });
  writeFileSync(path.join(root, 'gradle.lockfile'), lockBytes);
  writeFileSync(
    path.join(root, 'native.cdx.json'),
    `${JSON.stringify(sbom)}\n`,
  );
  const [target] = prepareNativeTargets(
    [
      {
        label: 'android-runtime',
        kind: 'gradle',
        lockPath: 'gradle.lockfile',
        sbomPath: 'native.cdx.json',
      },
    ],
    {
      root,
      sourceMapBytes: Buffer.from('{"schemaVersion":1}'),
      packageLockBytes: Buffer.from('{"lockfileVersion":3,"packages":{}}'),
    },
  );
  return { root, target, sbom };
};

const canaryResults = new Map([
  [
    'pkg:maven/com.google.guava/guava@30.1.1-jre',
    { id: 'GHSA-5mg8-w23w-74h3', modified: '2026-07-08T06:49:56Z' },
  ],
  [
    'pkg:npm/lodash@4.17.20',
    { id: 'GHSA-35jh-r3h4-6jhm', modified: '2026-07-08T18:29:36Z' },
  ],
  [
    'pkg:swift/github.com/apple/swift-asn1@1.3.0',
    { id: 'GHSA-w8xv-rwgf-4fwh', modified: '2025-01-15T15:26:01Z' },
  ],
]);

const fakeOsv =
  (targetFindings = new Map()) =>
  async queries =>
    queries.map(({ purl }) => ({
      vulns: [
        canaryResults.get(purl),
        ...(targetFindings.get(purl) ?? []),
      ].filter(Boolean),
      nextPageToken: null,
    }));

const validException = overrides => ({
  vulnerabilityId: 'GHSA-abcd-1234-5678',
  dependencySet: 'android-runtime',
  componentPurl: 'pkg:maven/com.example/library@1.2.3',
  queryPurl: 'pkg:maven/com.example/library@1.2.3',
  owner: 'Mobile Security Owner',
  approvedBy: 'Independent Security Reviewer',
  rationale:
    'The affected parser is unreachable in the signed mobile artifact; remediation is tracked and required before this short waiver expires.',
  trackingReference: 'SECURITY-2026-1042',
  approvalEvidenceSha256:
    '1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef',
  approvedAt: '2026-07-10T12:00:00Z',
  expiresAt: '2026-07-20T12:00:00Z',
  ...overrides,
});

test('builds a pass report only after all ecosystem canaries and every component query respond', async t => {
  const { target } = writeGradleFixture(t);
  const report = await buildNativeAdvisoryReport({
    targets: [target],
    exceptionDocument: { schemaVersion: 1, exceptions: [] },
    exceptionBytes: emptyExceptionBytes,
    now: scanTime,
    requestBatch: fakeOsv(),
  });
  assert.equal(report.summary.status, 'pass');
  assert.equal(report.summary.componentCount, 1);
  assert.equal(report.summary.findingCount, 0);
  assert.equal(report.scanner.apiEndpoint, OSV_API_ENDPOINT);
  assert.equal(
    report.scanner.policy,
    'block-every-active-osv-match-unless-exact-unexpired-exception',
  );
  assert.match(report.exceptionPolicy.sha256, /^[0-9a-f]{64}$/u);
});

test('retains a deterministic blocked report for every unresolved active advisory', async t => {
  const { target } = writeGradleFixture(t);
  const finding = {
    id: 'GHSA-abcd-1234-5678',
    modified: '2026-07-11T00:00:00Z',
  };
  const report = await buildNativeAdvisoryReport({
    targets: [target],
    exceptionDocument: { schemaVersion: 1, exceptions: [] },
    exceptionBytes: emptyExceptionBytes,
    now: scanTime,
    requestBatch: fakeOsv(
      new Map([['pkg:maven/com.example/library@1.2.3', [finding]]]),
    ),
  });
  assert.equal(report.summary.status, 'blocked');
  assert.equal(report.summary.unresolvedCount, 1);
  assert.deepEqual(report.findings[0].exception, null);
  assert.equal(report.findings[0].vulnerabilityId, finding.id);
});

test('accepts only an exact, independently approved, short-lived exception', async t => {
  const { target } = writeGradleFixture(t);
  const exception = validException();
  const exceptionDocument = { schemaVersion: 1, exceptions: [exception] };
  const exceptionBytes = Buffer.from(JSON.stringify(exceptionDocument));
  const report = await buildNativeAdvisoryReport({
    targets: [target],
    exceptionDocument,
    exceptionBytes,
    ...signedExceptionInputs(exceptionBytes),
    now: scanTime,
    requestBatch: fakeOsv(
      new Map([
        [
          exception.queryPurl,
          [
            {
              id: exception.vulnerabilityId,
              modified: '2026-07-11T00:00:00Z',
            },
          ],
        ],
      ]),
    ),
  });
  assert.equal(report.summary.status, 'pass');
  assert.equal(report.summary.exceptedCount, 1);
  assert.equal(report.findings[0].exception.approvedBy, exception.approvedBy);
  assert.equal(
    report.exceptionPolicy.authorityPublicKeySpkiSha256,
    exceptionAuthorityPin.publicKeySpkiSha256,
  );
});

test('ordinary CI requires zero exceptions and rejects forged detached approval', async t => {
  const { target } = writeGradleFixture(t);
  const exceptionDocument = {
    schemaVersion: 1,
    exceptions: [validException()],
  };
  const exceptionBytes = Buffer.from(JSON.stringify(exceptionDocument));
  await assert.rejects(
    buildNativeAdvisoryReport({
      targets: [target],
      exceptionDocument,
      exceptionBytes,
      now: scanTime,
      requestBatch: fakeOsv(),
    }),
    /ordinary native advisory scans require zero exceptions/u,
  );
  await assert.rejects(
    buildNativeAdvisoryReport({
      targets: [target],
      exceptionDocument,
      exceptionBytes,
      ...signedExceptionInputs(Buffer.from('different bytes')),
      now: scanTime,
      requestBatch: fakeOsv(),
    }),
    /detached distribution evidence signature is invalid/u,
  );
});

test('fails on expired, future, overlong, placeholder, self-approved, or zero-proof exceptions', () => {
  const invalid = [
    validException({ expiresAt: '2026-07-12T12:00:00Z' }),
    validException({ approvedAt: '2026-07-13T12:00:00Z' }),
    validException({ expiresAt: '2026-08-20T12:00:00Z' }),
    validException({
      rationale:
        'TODO this is a placeholder rationale that is intentionally invalid.',
    }),
    validException({ approvedBy: 'Mobile Security Owner' }),
    validException({ approvalEvidenceSha256: '0'.repeat(64) }),
  ];
  for (const exception of invalid) {
    assert.throws(
      () =>
        validateExceptions(
          { schemaVersion: 1, exceptions: [exception] },
          scanTime,
        ),
      /invalid|expired|future-dated|over-broad/u,
    );
  }
});

test('fails when an exception is stale or does not match the exact dependency identity', async t => {
  const { target } = writeGradleFixture(t);
  const exceptionDocument = {
    schemaVersion: 1,
    exceptions: [validException({ vulnerabilityId: 'GHSA-unused-1111-2222' })],
  };
  const exceptionBytes = Buffer.from(JSON.stringify(exceptionDocument));
  await assert.rejects(
    buildNativeAdvisoryReport({
      targets: [target],
      exceptionDocument,
      exceptionBytes,
      ...signedExceptionInputs(exceptionBytes),
      now: scanTime,
      requestBatch: fakeOsv(),
    }),
    /stale, unmatched, or out of scope/u,
  );
});

test('requires Maven, npm, and Swift canaries so an incompatible advisory backend cannot look clean', async t => {
  const { target } = writeGradleFixture(t);
  for (const missingPurl of canaryResults.keys()) {
    await assert.rejects(
      buildNativeAdvisoryReport({
        targets: [target],
        exceptionDocument: { schemaVersion: 1, exceptions: [] },
        exceptionBytes: emptyExceptionBytes,
        now: scanTime,
        requestBatch: async queries =>
          queries.map(({ purl }) => ({
            vulns:
              purl === missingPurl
                ? []
                : [canaryResults.get(purl)].filter(Boolean),
            nextPageToken: null,
          })),
      }),
      /ecosystem canary did not match/u,
      `missing ${missingPurl} must block the scan`,
    );
  }
});

test('collects bounded pagination and rejects repeated page tokens or inconsistent revisions', async () => {
  let calls = 0;
  const result = await queryOsvPurls(['pkg:maven/com.example/library@1.2.3'], {
    requestBatch: async queries => {
      calls += 1;
      return queries.map(query =>
        query.pageToken === null
          ? {
              vulns: [
                { id: 'GHSA-page-1111-2222', modified: '2026-01-01T00:00:00Z' },
              ],
              nextPageToken: 'page-2',
            }
          : {
              vulns: [
                { id: 'GHSA-page-3333-4444', modified: '2026-01-02T00:00:00Z' },
              ],
              nextPageToken: null,
            },
      );
    },
  });
  assert.equal(calls, 2);
  assert.equal(result.findings.values().next().value.size, 2);

  await assert.rejects(
    queryOsvPurls(['pkg:maven/com.example/library@1.2.3'], {
      requestBatch: async queries =>
        queries.map(() => ({ vulns: [], nextPageToken: 'same-token' })),
    }),
    /pagination token repeated/u,
  );
});

test('the HTTP adapter is credential-free, validates schema, and retries only bounded transient failures', async () => {
  let requestOptions;
  const result = await requestOsvBatch(
    [{ purl: 'pkg:maven/com.example/library@1.2.3', pageToken: null }],
    {
      fetchImpl: async (url, options) => {
        assert.equal(url, OSV_API_ENDPOINT);
        requestOptions = options;
        return new Response(
          JSON.stringify({
            results: [
              {
                vulns: [
                  {
                    id: 'GHSA-http-1111-2222',
                    modified: '2026-01-01T00:00:00Z',
                  },
                ],
              },
            ],
          }),
          { status: 200, headers: { 'content-type': 'application/json' } },
        );
      },
      sleeper: async () => {},
    },
  );
  assert.equal(result[0].vulns.length, 1);
  assert.equal(requestOptions.credentials, 'omit');
  assert.equal(requestOptions.redirect, 'error');
  assert.doesNotMatch(
    JSON.stringify(requestOptions),
    /authorization|api.?key/iu,
  );

  let attempts = 0;
  await assert.rejects(
    requestOsvBatch(
      [{ purl: 'pkg:maven/com.example/library@1.2.3', pageToken: null }],
      {
        fetchImpl: async () => {
          attempts += 1;
          return new Response('', { status: 503 });
        },
        sleeper: async () => {},
      },
    ),
    /service unavailable; release remains blocked/u,
  );
  assert.equal(attempts, 3);
});

test('rejects malformed, count-mismatched, oversized, or non-JSON OSV responses', async () => {
  const query = [
    { purl: 'pkg:maven/com.example/library@1.2.3', pageToken: null },
  ];
  const fixtures = [
    new Response('{', {
      status: 200,
      headers: { 'content-type': 'application/json' },
    }),
    new Response('{"results":[]}', {
      status: 200,
      headers: { 'content-type': 'application/json' },
    }),
    new Response('{"results":[{}]}', {
      status: 200,
      headers: { 'content-type': 'text/html' },
    }),
    new Response('{"results":[{}]}', {
      status: 200,
      headers: {
        'content-type': 'application/json',
        'content-length': String(33 * 1024 * 1024),
      },
    }),
  ];
  for (const response of fixtures) {
    await assert.rejects(
      requestOsvBatch(query, {
        fetchImpl: async () => response.clone(),
        sleeper: async () => {},
      }),
      /OSV|response|unavailable/u,
    );
  }
});

test('rejects a truncated SBOM, lock hash mismatch, or duplicate component', t => {
  const { root, sbom } = writeGradleFixture(t);
  const assertInvalid = (document, pattern) => {
    writeFileSync(
      path.join(root, 'tampered.cdx.json'),
      JSON.stringify(document),
    );
    assert.throws(
      () =>
        prepareNativeTargets(
          [
            {
              label: 'android-tampered',
              kind: 'gradle',
              lockPath: 'gradle.lockfile',
              sbomPath: 'tampered.cdx.json',
            },
          ],
          {
            root,
            sourceMapBytes: Buffer.from('{"schemaVersion":1}'),
            packageLockBytes: Buffer.from(
              '{"lockfileVersion":3,"packages":{}}',
            ),
          },
        ),
      pattern,
    );
  };
  assertInvalid({ ...sbom, components: [] }, /does not exactly represent/u);
  assertInvalid(
    {
      ...sbom,
      metadata: {
        ...sbom.metadata,
        properties: sbom.metadata.properties.map(property =>
          property.name === 'birthday:lockfile-sha256'
            ? { ...property, value: '0'.repeat(64) }
            : property,
        ),
      },
    },
    /not bound to the exact lockfile/u,
  );
  assertInvalid(
    { ...sbom, components: [...sbom.components, sbom.components[0]] },
    /duplicate component/u,
  );
});

test('preserves and validates an exact Gradle production-runtime configuration scope', t => {
  const root = createFixtureRoot(t);
  const lockBytes = Buffer.from(
    [
      'com.example:runtime:1.0=prodReleaseRuntimeClasspath,testRuntimeClasspath',
      'com.example:test-only:2.0=testRuntimeClasspath',
      '',
    ].join('\n'),
  );
  const sbom = createNativeSbom({
    kind: 'gradle',
    lockBytes,
    applicationName: 'Fixture runtime',
    version: '1.0',
    configuration: 'prodReleaseRuntimeClasspath',
  });
  writeFileSync(path.join(root, 'gradle.lockfile'), lockBytes);
  writeFileSync(path.join(root, 'runtime.cdx.json'), JSON.stringify(sbom));
  const [target] = prepareNativeTargets(
    [
      {
        label: 'android-prod-runtime',
        kind: 'gradle',
        lockPath: 'gradle.lockfile',
        sbomPath: 'runtime.cdx.json',
      },
    ],
    {
      root,
      sourceMapBytes: Buffer.from('{"schemaVersion":1}'),
      packageLockBytes: Buffer.from('{"lockfileVersion":3,"packages":{}}'),
    },
  );
  assert.equal(target.configuration, 'prodReleaseRuntimeClasspath');
  assert.equal(target.componentCount, 1);
  assert.equal(
    target.componentMappings[0].queryPurl,
    'pkg:maven/com.example/runtime@1.0',
  );
});

test('verifies exact CocoaPods CDN bytes, identity, repository, and tag before an iOS scan', async () => {
  const podspecBytes = Buffer.from(
    JSON.stringify({
      name: 'SecurePod',
      version: '1.2.3',
      source: {
        git: 'https://github.com/Example/SecurePod.git',
        tag: 'CocoaPods-1.2.3',
      },
    }),
  );
  const source = {
    podName: 'SecurePod',
    podVersion: '1.2.3',
    podspecSha1: createHash('sha1').update(podspecBytes).digest('hex'),
    sourceRepository: 'github.com/example/securepod',
    sourceTag: 'CocoaPods-1.2.3',
    queryPurl: 'pkg:swift/github.com/example/securepod@1.2.3',
  };
  let requestedUrl;
  const [verified] = await verifyCocoaPodsPodspecSources([source], {
    fetchImpl: async url => {
      requestedUrl = url;
      return new Response(podspecBytes, {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    },
    sleeper: async () => {},
  });
  assert.match(
    requestedUrl,
    /^https:\/\/cdn\.cocoapods\.org\/Specs\/[0-9a-f]\/[0-9a-f]\/[0-9a-f]\/SecurePod\/1\.2\.3\/SecurePod\.podspec\.json$/u,
  );
  assert.deepEqual(verified, source);

  const redirectRequests = [];
  const [redirectVerified] = await verifyCocoaPodsPodspecSources([source], {
    fetchImpl: async url => {
      redirectRequests.push(url);
      if (url.startsWith('https://cdn.cocoapods.org/')) {
        return new Response(null, {
          status: 301,
          headers: {
            location: url.replace(
              'https://cdn.cocoapods.org/',
              'https://cdn.jsdelivr.net/cocoa/',
            ),
          },
        });
      }
      return new Response(podspecBytes, {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    },
    sleeper: async () => {},
  });
  assert.equal(redirectRequests.length, 2);
  assert.match(
    redirectRequests[1],
    /^https:\/\/cdn\.jsdelivr\.net\/cocoa\/Specs\//u,
  );
  assert.deepEqual(redirectVerified, source);

  await assert.rejects(
    verifyCocoaPodsPodspecSources([source], {
      fetchImpl: async () =>
        new Response(null, {
          status: 301,
          headers: { location: 'https://example.invalid/podspec.json' },
        }),
      sleeper: async () => {},
    }),
    /redirect is not trusted/u,
  );

  await assert.rejects(
    verifyCocoaPodsPodspecSources([source], {
      fetchImpl: async () =>
        new Response(Buffer.from(`${podspecBytes.toString('utf8')} `), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }),
      sleeper: async () => {},
    }),
    /do not match the lock checksum/u,
  );
});

test('rejects noncanonical package identities and malformed CLI argument groups', () => {
  assert.throws(
    () => validatePurl('pkg:maven/com.example/library@*', new Set(['maven'])),
    /canonically encoded/u,
  );
  assert.throws(
    () =>
      validatePurl('pkg:cocoapods/Unsafe%2fpod@1.0', new Set(['cocoapods'])),
    /canonical/u,
  );
  assert.deepEqual(
    parseArguments([
      '--dependency-set',
      'android-runtime',
      'gradle',
      'lock',
      'sbom',
      '--output',
      'release-evidence/report.json',
    ]),
    {
      targetSpecs: [
        {
          label: 'android-runtime',
          kind: 'gradle',
          lockPath: 'lock',
          sbomPath: 'sbom',
        },
      ],
      output: 'release-evidence/report.json',
      exceptionSignature: null,
      exceptionPublicKey: null,
    },
  );
  assert.throws(() => parseArguments(['--output', 'x']), /requires output/u);
  assert.throws(
    () => parseArguments(['--dependency-set', 'a', 'gradle']),
    /usage/u,
  );
});

test('writes reports create-only under release-evidence and rejects escapes or symlink segments', t => {
  const root = createFixtureRoot(t);
  mkdirSync(path.join(root, 'release-evidence'));
  mkdirSync(path.join(root, 'release-evidence/native'));
  const report = { schemaVersion: 1, summary: { status: 'pass' } };
  const output = writeNativeAdvisoryReport(
    'release-evidence/native/report.json',
    report,
    root,
  );
  assert.deepEqual(JSON.parse(readFileSync(output, 'utf8')), report);
  assert.throws(
    () =>
      writeNativeAdvisoryReport(
        'release-evidence/native/report.json',
        report,
        root,
      ),
    /EEXIST/u,
  );
  assert.throws(
    () => writeNativeAdvisoryReport('../escaped.json', report, root),
    /inside release-evidence/u,
  );
  const outside = path.join(root, 'outside');
  mkdirSync(outside);
  if (symlinksAvailable) {
    symlinkSync(outside, path.join(root, 'release-evidence/link'));
    assert.throws(
      () =>
        writeNativeAdvisoryReport(
          'release-evidence/link/report.json',
          report,
          root,
        ),
      /symbolic links/u,
    );
  } else {
    t.diagnostic(
      'host cannot create symbolic links; link-segment case skipped',
    );
  }
});
