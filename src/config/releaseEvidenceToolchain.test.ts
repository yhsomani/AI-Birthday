import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { extname, join } from 'node:path';
import { describe, it } from 'node:test';

const rootDir = process.cwd();
const read = (path: string) => readFileSync(join(rootDir, path), 'utf8');

const sourceFilesUnder = (directory: string): string[] =>
  readdirSync(join(rootDir, directory), { withFileTypes: true }).flatMap(entry => {
    const relativePath = join(directory, entry.name);
    if (entry.isDirectory()) {
      return sourceFilesUnder(relativePath);
    }
    return ['.ts', '.tsx'].includes(extname(entry.name)) ? [relativePath] : [];
  });

describe('release toolchain and clean-checkout contract', () => {
  it('pins the Node and npm versions consistently and enforces engines', () => {
    const packageJson = JSON.parse(read('package.json')) as {
      packageManager?: string;
      engines?: { node?: string; npm?: string };
      scripts?: Record<string, string>;
      dependencies?: Record<string, string>;
    };
    const packageLock = JSON.parse(read('package-lock.json')) as {
      packages?: Record<string, { engines?: { node?: string; npm?: string } }>;
    };

    assert.equal(read('.nvmrc').trim(), '24.18.0');
    assert.equal(read('.node-version').trim(), '24.18.0');
    assert.equal(read('.npmrc').trim(), 'engine-strict=true');
    assert.equal(packageJson.packageManager, 'npm@11.6.0');
    assert.deepEqual(packageJson.engines, { node: '>=24.18.0 <25', npm: '11.6.0' });
    assert.deepEqual(packageLock.packages?.['']?.engines, packageJson.engines);
    assert.equal(packageJson.scripts?.['release:evidence'], 'node --import tsx src/config/releaseEvidenceCli.ts');
    assert.equal(packageJson.scripts?.web, undefined);
    assert.equal(packageJson.scripts?.['web:export'], undefined);
    assert.equal(packageJson.dependencies?.['react-dom'], undefined);
    assert.equal(packageJson.dependencies?.['react-native-web'], undefined);
  });

  it('keeps ignored release reports outside source module dependencies', () => {
    sourceFilesUnder('src').forEach(path => {
      const source = read(path);
      assert.doesNotMatch(
        source,
        /(?:from\s+|import\s*\()['"][^'"]*reports\/react-native-release-evidence\.json/,
        `${path} must not import the ignored release report`
      );
    });
  });

  it('proves the clean-checkout order and attests generated CI evidence', () => {
    const workflow = read('.github/workflows/android.yml');
    const evidenceCli = read('src/config/releaseEvidenceCli.ts');
    assert.match(workflow, /node-version-file: \.nvmrc/);
    assert.match(workflow, /corepack enable npm[\s\S]+npm --version[\s\S]+11\.6\.0/);
    assert.match(workflow, /run: npm ci/);
    assert.match(
      workflow,
      /test ! -e reports\/react-native-release-evidence\.json[\s\S]+node --import tsx src\/config\/releaseEvidenceCli\.ts --source-only --fail-on-blockers/
    );
    assert.doesNotMatch(workflow, /npm run release:evidence/);
    assert.doesNotMatch(
      workflow,
      /run: npm run (?:typecheck|lint|format:check|test:coverage|test:native-prebuild)\s*$/m
    );
    assert.match(workflow, /actions\/setup-java@v4[\s\S]+java-version: '17'/);
    assert.match(workflow, /actions\/attest-build-provenance@v2/);
    assert.doesNotMatch(workflow, /web:export|expo export --platform web/);
    assert.match(evidenceCli, /executeReleaseEvidenceCommands/);
    assert.match(evidenceCli, /collectReleaseEvidenceProvenance/);
    assert.match(evidenceCli, /--source-only/);
    assert.match(evidenceCli, /--device-evidence=/);
    assert.match(evidenceCli, /'android',[\s\S]+\s+'ios',/);
    assert.doesNotMatch(evidenceCli, /RELATEAI_RELEASE_[A-Z_]+_STATUS/);
  });

  it('keeps the native prebuild command temporary and compile-gated', () => {
    const packageJson = JSON.parse(read('package.json')) as {
      scripts?: Record<string, string>;
    };
    const nativePrebuildScript = read('scripts/verify_native_prebuild.js');

    assert.equal(packageJson.scripts?.['test:native-prebuild'], 'node scripts/verify_native_prebuild.js');
    assert.match(nativePrebuildScript, /mkdtemp/);
    assert.match(nativePrebuildScript, /'prebuild', '--clean', '--no-install', '--platform', 'android'/);
    assert.match(nativePrebuildScript, /':app:assembleDebug'/);
    assert.match(nativePrebuildScript, /assertAndroidReleasePolicyAsync/);
    assert.match(nativePrebuildScript, /android:allowBackup/);
    assert.match(nativePrebuildScript, /WRITE_CONTACTS/);
    assert.match(nativePrebuildScript, /requires JDK 17/);
    assert.match(nativePrebuildScript, /rm\(fixtureRoot, \{ recursive: true, force: true \}\)/);
  });

  it('documents endpoint-only public configuration and no client provider secret', () => {
    const environmentTemplate = read('.env.example');
    const releaseChecklist = read('docs/operations/release-checklist.md');
    const providerDevelopmentMode = read('src/native/providerDevelopmentMode.ts');
    const aiProviderClient = read('src/native/aiProviderClient.ts');
    const emailSenderClient = read('src/native/emailSenderClient.ts');

    assert.match(environmentTemplate, /EXPO_PUBLIC_RELATE_AI_ENDPOINT=/);
    assert.match(environmentTemplate, /EXPO_PUBLIC_RELATE_EMAIL_ENDPOINT=/);
    assert.match(environmentTemplate, /EXPO_PUBLIC_RELATE_EMAIL_STATUS_ENDPOINT=/);
    assert.match(environmentTemplate, /Never put API[\s\S]+provider secrets here/i);
    assert.doesNotMatch(environmentTemplate, /GEMINI_API_KEY|AIza[0-9A-Za-z_-]+/);
    assert.match(providerDevelopmentMode, /typeof __DEV__ !== 'undefined'[\s\S]+developmentBuild && publicFlag/);
    assert.match(aiProviderClient, /buildAllowsLocalProviderEndpoints/);
    assert.match(emailSenderClient, /buildAllowsLocalProviderEndpoints/);
    assert.doesNotMatch(
      releaseChecklist,
      /ProductionReadinessConfigTest|network_security_config\.xml|AuthManager\.signOut/
    );
  });
});
