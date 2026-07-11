import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, it } from 'node:test';

const rootDir = process.cwd();

const filesBelow = (directory: string): string[] =>
  readdirSync(join(rootDir, directory), { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? filesBelow(path) : [path];
  });

describe('temporary functionality-only UI surface', () => {
  it('keeps exactly the shell, functional command input, and render-error boundary as React components', () => {
    const productionComponents = filesBelow('src')
      .filter(path => path.endsWith('.tsx') && !path.endsWith('.test.tsx'))
      .sort();

    assert.deepEqual(productionComponents, [
      'src/App.tsx',
      'src/app/MinimalFunctionalShell.tsx',
      'src/ui/AppErrorBoundary.tsx'
    ]);
  });

  it('rejects presentation styling, implicit opacity animation, and bitmap/vector UI assets', () => {
    const componentSource = ['src/App.tsx', 'src/app/MinimalFunctionalShell.tsx', 'src/ui/AppErrorBoundary.tsx']
      .map(path => readFileSync(join(rootDir, path), 'utf8'))
      .join('\n');
    assert.doesNotMatch(
      componentSource,
      /TouchableOpacity|\bStyleSheet\b|\bAnimated\.|<Image\b|style=|backgroundColor|fontFamily|shadowColor/
    );

    const visualAssetExtensions = /\.(?:png|jpe?g|webp|gif|svg|css|scss|sass|less)$/i;
    const visualAssets = [...filesBelow('src'), ...filesBelow('plugins')]
      .filter(path => visualAssetExtensions.test(path))
      .map(path => relative(rootDir, join(rootDir, path)));
    assert.deepEqual(visualAssets, []);
  });

  it('keeps obsolete pre-Figma design contracts out of the active repository', () => {
    assert.equal(existsSync(join(rootDir, 'docs/design/design-system.md')), false);
    assert.equal(existsSync(join(rootDir, 'src/ui/theme.ts')), false);
    assert.equal(existsSync(join(rootDir, 'src/ui/localizationContract.test.ts')), false);
    assert.equal(existsSync(join(rootDir, 'src/ui/primaryInteractionContract.test.ts')), false);
  });
});
