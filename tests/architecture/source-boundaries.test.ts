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
  sep: string;
  join(...parts: string[]): string;
  relative(from: string, to: string): string;
  resolve(...parts: string[]): string;
}>;

declare const __dirname: string;

const fs = require('fs') as FileSystem;
const path = require('path') as PathApi;

const workspaceRoot = path.resolve(__dirname, '../..');
const sourceRoot = path.join(workspaceRoot, 'src');

const collectSourceFiles = (directory: string): string[] =>
  fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      return collectSourceFiles(absolute);
    }
    return /\.[cm]?[jt]sx?$/u.test(entry.name) ? [absolute] : [];
  });

const sourceFiles = collectSourceFiles(sourceRoot);
const relative = (file: string): string => path.relative(workspaceRoot, file);

const violationsFor = (pattern: RegExp): string[] =>
  sourceFiles.flatMap(file =>
    pattern.test(fs.readFileSync(file, 'utf8')) ? [relative(file)] : [],
  );

describe('source architecture boundaries', () => {
  it.each([
    ['AsyncStorage', /\bAsyncStorage\b/u],
    [
      'Firebase client imports',
      /(?:from\s+['"](?:@react-native-firebase|firebase(?:\/|['"]))|require\(\s*['"](?:@react-native-firebase|firebase(?:\/|['"])))/u,
    ],
    ['console APIs', /\bconsole\s*\./u],
    [
      'direct SMS APIs',
      /\b(?:sendSms|sendTextMessage|sendMultipartTextMessage|SmsManager)\b/u,
    ],
    [
      'coordination mutation APIs',
      /\b(?:claimOccurrence|claimTest|armOccurrence|armAttempt|createPermit|setSenderEpoch)\s*\(/u,
    ],
    [
      'scheduler mutation APIs',
      /\b(?:scheduleJob|enqueueSend|retrySms|transitionJob|markDelivered)\s*\(/u,
    ],
  ] as const)('forbids %s under src', (_label, pattern) => {
    expect(violationsFor(pattern)).toEqual([]);
  });

  it('keeps the pure domain independent from UI, native, and infrastructure', () => {
    const domainFiles = sourceFiles.filter(file =>
      file.startsWith(path.join(sourceRoot, 'domain') + path.sep),
    );
    const forbiddenImport =
      /(?:from|require\()\s*['"][^'"]*(?:react|react-native|infrastructure|specs\/native)/u;
    const violations = domainFiles.flatMap(file =>
      forbiddenImport.test(fs.readFileSync(file, 'utf8'))
        ? [relative(file)]
        : [],
    );

    expect(violations).toEqual([]);
  });

  it('keeps application ports independent from React Native and native specs', () => {
    const portFiles = sourceFiles.filter(file =>
      file.startsWith(path.join(sourceRoot, 'application', 'ports') + path.sep),
    );
    const forbiddenImport =
      /(?:from|require\()\s*['"][^'"]*(?:react-native|specs\/native|infrastructure)/u;
    const violations = portFiles.flatMap(file =>
      forbiddenImport.test(fs.readFileSync(file, 'utf8'))
        ? [relative(file)]
        : [],
    );

    expect(violations).toEqual([]);
  });

  it('confines the native codegen specification import to native infrastructure', () => {
    const specImport = /specs\/native\/NativeBirthday/u;
    const violations = sourceFiles.flatMap(file => {
      const isNativeInfrastructure = file.startsWith(
        path.join(sourceRoot, 'infrastructure', 'native') + path.sep,
      );
      return !isNativeInfrastructure &&
        specImport.test(fs.readFileSync(file, 'utf8'))
        ? [relative(file)]
        : [];
    });

    expect(violations).toEqual([]);
  });
});
