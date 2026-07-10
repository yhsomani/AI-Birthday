import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import {
  buildReactNativeReleaseEvidence,
  type EasConfigLike,
  type ExpoAppConfigLike,
  type PackageJsonLike
} from './releaseEvidence';
import { collectReleaseEvidenceProvenance, executeReleaseEvidenceCommands } from './releaseEvidenceRunner';

const rootDir = process.cwd();
const outputArg = process.argv.find(arg => arg.startsWith('--output='));
const outputPath =
  outputArg?.slice('--output='.length) || join(rootDir, 'reports', 'react-native-release-evidence.json');

const readJson = <T>(path: string): T => JSON.parse(readFileSync(path, 'utf8')) as T;

const legacyKotlinGradleArtifactCandidates = [
  'app',
  'core',
  'build',
  'gradle',
  'build.gradle.kts',
  'settings.gradle.kts',
  'gradle.properties',
  'local.properties',
  'gradlew',
  'gradlew.bat'
];

const packageJson = readJson<PackageJsonLike>(join(rootDir, 'package.json'));
const appConfig = readJson<ExpoAppConfigLike>(join(rootDir, 'app.json'));
const easConfig = readJson<EasConfigLike>(join(rootDir, 'eas.json'));
const commands = executeReleaseEvidenceCommands({
  rootDir,
  onStart: command => process.stdout.write(`Running release check: ${command}\n`),
  onOutput: (_command, stdout, stderr) => {
    if (stdout) {
      process.stdout.write(stdout);
    }
    if (stderr) {
      process.stderr.write(stderr);
    }
  }
});
const provenance = collectReleaseEvidenceProvenance(rootDir);
const evidence = buildReactNativeReleaseEvidence({
  packageJson,
  appConfig,
  easConfig,
  generatedAt: new Date().toISOString(),
  commands,
  provenance,
  legacyKotlinGradleArtifactPaths: legacyKotlinGradleArtifactCandidates.filter(path => existsSync(join(rootDir, path)))
});

mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, `${JSON.stringify(evidence, null, 2)}\n`, 'utf8');

const summary = [
  `React Native release evidence written to ${outputPath}`,
  `Commit: ${evidence.provenance?.commitSha || 'unavailable'}${evidence.provenance?.dirty ? ' (dirty)' : ''}`,
  `Blockers: ${evidence.blockers.length}`,
  `Warnings: ${evidence.warnings.length}`
].join('\n');

process.stdout.write(`${summary}\n`);

if (evidence.blockers.length > 0 && process.argv.includes('--fail-on-blockers')) {
  process.exitCode = 1;
}
