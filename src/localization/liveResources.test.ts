import { liveEnglish, liveHindi } from './liveResources';
import { productionResources } from './productionResources';
import { safeReasonMessageKeys } from './reasonCopy';
import { resources } from './resources';
import { SAFE_REASON_CODES } from '../domain/shared/reasonCodes';

type DirectoryEntry = Readonly<{
  name: string;
  isDirectory(): boolean;
}>;
type FileSystem = Readonly<{
  readdirSync(
    directory: string,
    options: { withFileTypes: true },
  ): readonly DirectoryEntry[];
  readFileSync(file: string, encoding: 'utf8'): string;
}>;
type PathApi = Readonly<{
  join(...parts: string[]): string;
  resolve(...parts: string[]): string;
}>;
declare const __dirname: string;
const fs = require('fs') as FileSystem;
const path = require('path') as PathApi;

describe('production live localization', () => {
  it('keeps complete non-empty English and Hindi live dictionaries', () => {
    expect(Object.keys(liveHindi).sort()).toEqual(
      Object.keys(liveEnglish).sort(),
    );
    Object.entries(liveEnglish).forEach(([key, value]) => {
      expect(key.startsWith('live.')).toBe(true);
      expect(value.trim().length).toBeGreaterThan(0);
      expect(
        liveHindi[key as keyof typeof liveHindi].trim().length,
      ).toBeGreaterThan(0);
    });
  });

  it('generates pseudo-RTL copy for every production live key', () => {
    Object.keys(liveEnglish).forEach(key => {
      const value =
        resources['ar-XB'].translation[
          key as keyof (typeof resources)['ar-XB']['translation']
        ];
      expect(value).toMatch(/^⟦ /u);
    });
  });

  it('retains the explicit human Hindi review caveat', () => {
    expect(resources.hi.translation['settings.hindiCaveat']).toMatch(
      /मानव भाषा समीक्षा/u,
    );
  });

  it('maps every stable native reason to non-code English and Hindi copy', () => {
    expect(Object.keys(safeReasonMessageKeys).sort()).toEqual(
      [...SAFE_REASON_CODES].sort(),
    );
    SAFE_REASON_CODES.forEach(reason => {
      const key = safeReasonMessageKeys[reason];
      const english = productionResources.en.translation[key];
      const hindi = productionResources.hi.translation[key];
      expect(english).toBeTruthy();
      expect(hindi).toBeTruthy();
      expect(english).not.toContain(reason);
      expect(hindi).not.toContain(reason);
    });
  });

  it('keeps fixture and pseudo-RTL copy outside production resources', () => {
    const serialized = JSON.stringify(productionResources);
    expect(serialized).not.toContain('Interactive UI fixture');
    expect(serialized).not.toContain('Continue with synthetic account fixture');
    expect(productionResources).not.toHaveProperty('ar-XB');
  });

  it('keeps production live JSX copy behind localization lookups', () => {
    const liveRoot = path.resolve(__dirname, '../features/live');
    const files = fs
      .readdirSync(liveRoot, { withFileTypes: true })
      .filter(entry => !entry.isDirectory() && entry.name.endsWith('.tsx'))
      .map(entry => path.join(liveRoot, entry.name));
    const literalProp =
      /(?:title|detail|label|supporting|accessibilityLabel|accessibilityHint)=["'][^"']+["']/u;
    const literalTextNode = /(?<![=])>\s*[A-Za-z][^<{\n]*</u;
    const violations = files.filter(file => {
      const source = fs.readFileSync(file, 'utf8');
      return literalProp.test(source) || literalTextNode.test(source);
    });

    expect(violations).toEqual([]);
  });
});
