import assert from 'node:assert/strict';
import {
  appendFileSync,
  chmodSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { execFileSync, spawnSync } from 'node:child_process';
import test from 'node:test';

const verifier = resolve('tools/verify-android-apk.sh');

const fakeApksignerSource = `#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 4 || $1 != verify || $2 != --verbose || $3 != --print-certs ]]; then
  printf 'unexpected apksigner arguments\\n' >&2
  exit 90
fi
case \${APK_TEST_SIGNATURE_STATE:?} in
  unsigned)
    printf 'DOES NOT VERIFY\\nERROR: Missing META-INF/MANIFEST.MF\\n' >&2
    exit 1
    ;;
  valid)
    printf 'Verifies\\nVerified using v2 scheme (APK Signature Scheme v2): true\\n'
    ;;
  invalid)
    printf 'DOES NOT VERIFY\\nERROR: APK integrity check failed: digest mismatch\\n' >&2
    exit 1
    ;;
  *)
    exit 91
    ;;
esac
`;

const classify = ({ state, jarSignature = false, signingBlock = false }) => {
  const directory = mkdtempSync(join(tmpdir(), 'birthday-apk-signature-'));
  try {
    const apk = join(directory, 'candidate.apk');
    const payload = join(directory, 'payload.txt');
    const apksigner = join(directory, 'apksigner');
    writeFileSync(payload, 'unsigned test payload\n');
    writeFileSync(apksigner, fakeApksignerSource);
    chmodSync(apksigner, 0o755);
    execFileSync('zip', ['-q', apk, 'payload.txt'], { cwd: directory });

    if (jarSignature) {
      const metadataDirectory = join(directory, 'META-INF');
      mkdirSync(metadataDirectory);
      writeFileSync(join(metadataDirectory, 'CERT.SF'), 'signature metadata\n');
      execFileSync('zip', ['-q', apk, 'META-INF/CERT.SF'], {
        cwd: directory,
      });
    }
    if (signingBlock) appendFileSync(apk, 'APK Sig Block 42');

    return spawnSync(
      'bash',
      [
        '-c',
        'source "$1"; assert_truly_unsigned_apk "$2" "$3"',
        'signature-classifier',
        verifier,
        apk,
        apksigner,
      ],
      {
        encoding: 'utf8',
        env: { ...process.env, APK_TEST_SIGNATURE_STATE: state },
      },
    );
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
};

test('accepts only the pinned apksigner canonical unsigned state', () => {
  const result = classify({ state: 'unsigned' });
  assert.equal(result.status, 0, result.stderr);
});

test('rejects a valid signature in unsigned-dev-release mode', () => {
  const result = classify({ state: 'valid' });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /unexpectedly has a valid signature/u);
});

test('rejects malformed or tampered signature verification failures', () => {
  const result = classify({ state: 'invalid' });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /malformed or unverifiable signature metadata/u);
});

test('rejects JAR and APK Signing Block metadata even after signature stripping', () => {
  const jarSigned = classify({ state: 'unsigned', jarSignature: true });
  assert.equal(jarSigned.status, 1);
  assert.match(jarSigned.stderr, /contains JAR signature metadata/u);

  const blockSigned = classify({ state: 'unsigned', signingBlock: true });
  assert.equal(blockSigned.status, 1);
  assert.match(blockSigned.stderr, /contains an APK Signing Block/u);
});

test('restricted verifier separates direct and post-Play APK evidence and binds device bytes', () => {
  const source = readFileSync(verifier, 'utf8');
  assert.match(source, /\$# -ne 7/u);
  assert.match(source, /\$# -ne 8/u);
  assert.match(source, /--signature "\$restricted_evidence_signature"/u);
  assert.match(source, /--public-key "\$distribution_authority_public_key"/u);
  assert.match(source, /--version-name "\$version_name"/u);
  assert.match(source, /--artifact-mode "\$artifact_mode"/u);
  assert.match(
    source,
    /--artifact-signing-certificate "\$signing_certificate"/u,
  );
  assert.match(source, /--artifact-file "\$apk"/u);
  assert.match(source, /--play-delivered-evidence/u);
  assert.match(source, /cmd package get-install-source/u);
  assert.match(source, /toybox sha256sum/u);
  assert.match(
    source,
    /Play-delivered verification requires a physical Android device/u,
  );

  const legacy = spawnSync(
    'bash',
    [
      verifier,
      'candidate.apk',
      'example.package',
      '--restricted-evidence',
      'evidence.json',
      'prod',
    ],
    { encoding: 'utf8' },
  );
  assert.equal(legacy.status, 64);
  assert.match(legacy.stderr, /raw-signature.*authority-public-key/u);
});
