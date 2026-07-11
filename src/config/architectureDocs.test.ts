import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';

const rootDir = process.cwd();
const readDoc = (path: string) => readFileSync(join(rootDir, path), 'utf8');

describe('architecture documentation contract', () => {
  it('identifies ADR 0005 as the active React Native replacement architecture', () => {
    const docsIndex = readDoc('docs/README.md');
    const adr = readDoc('docs/architecture/adr/0005-react-native-replacement-architecture.md');

    assert.match(docsIndex, /ADR 0005 is the active React Native replacement architecture decision/);
    assert.match(adr, /Status: Accepted/);
    assert.match(adr, /React Native \/ Expo/);
    assert.match(adr, /src\/App\.tsx/);
    assert.match(adr, /legacy Android\/Gradle artifacts are historical references only/i);
    assert.match(adr, /node --import tsx src\/config\/releaseEvidenceCli\.ts/);
  });

  it('marks legacy Android architecture ADRs as historical for the active RN app', () => {
    [
      'docs/architecture/adr/0001-domain-purity-and-module-boundaries.md',
      'docs/architecture/adr/0002-occasion-model.md',
      'docs/architecture/adr/0003-durable-dispatch-attempts.md',
      'docs/architecture/adr/0004-database-keying-and-backup-recovery.md'
    ].forEach(path => {
      const adr = readDoc(path);
      assert.match(
        adr,
        /Status: Superseded for active React Native app by ADR 0005/,
        `${path} must not read as the active architecture decision`
      );
    });
  });

  it('keeps React Native migration status aligned with removed generated-native and legacy artifacts', () => {
    const migrationStatus = readDoc('docs/react-native-migration-status.md');
    const appSource = readDoc('src/App.tsx');
    const evidenceCli = readDoc('src/config/releaseEvidenceCli.ts');

    assert.match(migrationStatus, /legacy Android\/Gradle artifacts have been removed/i);
    assert.match(migrationStatus, /scans for generated-native and legacy artifact drift/i);
    assert.doesNotMatch(migrationStatus, /until explicit archival\/removal approval/i);
    assert.doesNotMatch(migrationStatus, /remaining legacy Android\/Gradle artifact paths/i);

    assert.doesNotMatch(appSource, /reports\/react-native-release-evidence\.json/);
    assert.match(evidenceCli, /legacyKotlinGradleArtifactCandidates/);
    assert.match(evidenceCli, /legacyKotlinGradleArtifactPaths/);
  });
});
