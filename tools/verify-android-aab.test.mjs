import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import {
  chmodSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';

const verifier = resolve('tools/verify-android-aab.sh');
const certificate = 'ab'.repeat(32).toUpperCase().match(/../gu).join(':');

const executable = (path, source) => {
  writeFileSync(path, `#!/usr/bin/env bash\nset -euo pipefail\n${source}\n`);
  chmodSync(path, 0o755);
};

const runFixture = ({
  firebaseState = 'valid',
  manifestState = 'valid',
  signatureState = 'valid',
  signerCount = 1,
} = {}) => {
  const directory = mkdtempSync(join(tmpdir(), 'birthday-aab-verifier-'));
  try {
    const bin = join(directory, 'bin');
    const javaHome = join(directory, 'java');
    const sdk = join(directory, 'sdk');
    const hostTag =
      process.platform === 'darwin' ? 'darwin-x86_64' : 'linux-x86_64';
    const readelfDirectory = join(
      sdk,
      'ndk/27.1.12297006/toolchains/llvm/prebuilt',
      hostTag,
      'bin',
    );
    mkdirSync(bin, { recursive: true });
    mkdirSync(join(javaHome, 'bin'), { recursive: true });
    mkdirSync(readelfDirectory, { recursive: true });

    const aab = join(directory, 'candidate.aab');
    const evidenceRoot = join(directory, 'supporting-evidence');
    const validatorArguments = join(directory, 'validator-arguments.txt');
    mkdirSync(evidenceRoot);
    writeFileSync(aab, 'not-a-real-zip-the-test-tools-own-the-boundary');
    executable(
      join(bin, 'zipinfo'),
      "printf '%s\\n' BundleConfig.pb base/manifest/AndroidManifest.xml base/resources.pb base/dex/classes.dex base/lib/arm64-v8a/libappmodules.so",
    );
    executable(join(bin, 'unzip'), "printf 'ELF-fixture'");
    executable(
      join(bin, 'file'),
      "printf '%s\\n' 'ELF 64-bit LSB shared object, ARM aarch64'",
    );
    executable(
      join(javaHome, 'bin', 'jarsigner'),
      signatureState === 'valid'
        ? "printf '%s\\n' 'jar verified.'"
        : "printf '%s\\n' 'jar is unsigned.' >&2; exit 1",
    );
    executable(
      join(javaHome, 'bin', 'keytool'),
      `${Array.from(
        { length: signerCount },
        (_, index) =>
          `printf '%s\\n' 'Signer #${
            index + 1
          }:' 'Certificate #1:' 'Certificate fingerprints:' ' SHA256: ${certificate}'`,
      ).join('\n')}`,
    );
    executable(
      join(readelfDirectory, 'llvm-readelf'),
      "printf '%s\\n' 'LOAD 0x000000 0x000000 0x000000 0x000001 0x000001 R E 0x4000'",
    );
    executable(
      join(bin, 'node'),
      `if [[ $1 == */inspect-android-aab-manifest.mjs ]]; then
  case \${AAB_TEST_MANIFEST_STATE:?} in
    valid) printf '%s\\t%s\\t%s\\t%s\\t%s\\n' com.yashsomani.birthdayautopilot 1 1.0 29 36 ;;
    wrong-package) printf '%s\\t%s\\t%s\\t%s\\t%s\\n' com.attacker.wrong 1 1.0 29 36 ;;
    rejected) printf '%s\\n' 'FAIL decoded manifest is not release-safe' >&2; exit 1 ;;
    *) exit 92 ;;
  esac
  exit
fi
if [[ $1 == */inspect-android-aab-firebase.mjs ]]; then
  case \${AAB_TEST_FIREBASE_STATE:?} in
    valid) printf '%s\t%s\t%s\t%s\n' birthday-production 123456789012 1:123456789012:android:abcdef1234567890 123456789012-release.apps.googleusercontent.com ;;
    wrong-project) printf '%s\t%s\t%s\t%s\n' attacker-production 999999999999 1:999999999999:android:abcdef1234567890 999999999999-release.apps.googleusercontent.com ;;
    rejected) printf '%s\n' 'FAIL compiled Firebase resources are invalid' >&2; exit 1 ;;
    *) exit 93 ;;
  esac
  exit
fi
printf '%s\\n' "$@" > '${validatorArguments}'`,
    );

    const result = spawnSync(
      'bash',
      [
        verifier,
        aab,
        'com.yashsomani.birthdayautopilot',
        '--play-evidence',
        join(directory, 'evidence.json'),
        join(directory, 'evidence.sig'),
        join(directory, 'authority.pem'),
        evidenceRoot,
        'prod',
      ],
      {
        encoding: 'utf8',
        env: {
          ...process.env,
          AAB_TEST_FIREBASE_STATE: firebaseState,
          AAB_TEST_MANIFEST_STATE: manifestState,
          ANDROID_HOME: sdk,
          JAVA_HOME: javaHome,
          PATH: `${bin}:${process.env.PATH ?? ''}`,
        },
      },
    );
    return {
      result,
      validatorArguments: existsSync(validatorArguments)
        ? readFileSync(validatorArguments, 'utf8')
        : '',
    };
  } catch (error) {
    if (error instanceof Error && 'code' in error && error.code === 'ENOENT') {
      return {
        result: { status: 1, stderr: error.message },
        validatorArguments: '',
      };
    }
    throw error;
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
};

test('AAB verifier binds exact bytes to the upload signer and Play evidence mode', () => {
  const { result, validatorArguments } = runFixture();
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /AAB .*upload-signature=verified/u);
  assert.match(validatorArguments, /--artifact-mode\nplay-aab/u);
  assert.match(
    validatorArguments,
    /--artifact-signing-certificate\n(?:ab){32}/u,
  );
  assert.match(validatorArguments, /--artifact-file/u);
  assert.match(validatorArguments, /--evidence-root\n.*supporting-evidence/u);
  assert.match(result.stdout, /version=1 min=29 target=36/u);
  assert.match(
    result.stdout,
    /PASS Android Firebase project=birthday-production number=123456789012 app-id=1:123456789012:android:abcdef1234567890 web-oauth-client=123456789012-release\.apps\.googleusercontent\.com/u,
  );
});

test('AAB verifier fails closed when compiled Firebase identity cannot be decoded', () => {
  const rejected = runFixture({ firebaseState: 'rejected' });
  assert.notEqual(rejected.result.status, 0);
  assert.match(rejected.result.stderr, /Firebase resources are invalid/u);
  assert.equal(rejected.validatorArguments, '');
});

test('AAB verifier rejects an unsigned artifact and multiple signers', () => {
  const unsigned = runFixture({ signatureState: 'unsigned' }).result;
  assert.equal(unsigned.status, 1);
  assert.match(unsigned.stderr, /signature does not verify/u);

  const multiple = runFixture({ signerCount: 2 }).result;
  assert.equal(multiple.status, 1);
  assert.match(multiple.stderr, /exactly one signer/u);
});

test('AAB verifier rejects wrong or policy-invalid decoded manifests before evidence acceptance', () => {
  const wrong = runFixture({ manifestState: 'wrong-package' });
  assert.equal(wrong.result.status, 1);
  assert.match(wrong.result.stderr, /manifest summary is malformed/u);
  assert.equal(wrong.validatorArguments, '');

  const rejected = runFixture({ manifestState: 'rejected' });
  assert.notEqual(rejected.result.status, 0);
  assert.match(rejected.result.stderr, /manifest is not release-safe/u);
  assert.equal(rejected.validatorArguments, '');
});

test('AAB verifier requires required bundle structure and arm64-only 16 KB ELF inputs', () => {
  const source = readFileSync(verifier, 'utf8');
  for (const entry of [
    'BundleConfig.pb',
    'base/manifest/AndroidManifest.xml',
    'base/resources.pb',
    'base/dex/classes.dex',
  ]) {
    assert.match(source, new RegExp(entry.replaceAll('.', '\\.')));
  }
  assert.match(source, /base\/lib\/arm64-v8a/u);
  assert.match(source, /alignment < 0x4000/u);
  assert.match(source, /artifactAabSha256|play-aab/u);
  assert.match(source, /inspect-android-aab-manifest\.mjs/u);
  assert.match(source, /inspect-android-aab-firebase\.mjs/u);
  assert.match(source, /--version-code "\$version_code"/u);
  assert.match(source, /--version-name "\$version_name"/u);
  assert.doesNotMatch(source, /--version-code 1/u);
});
