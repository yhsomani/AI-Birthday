import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const read = file => readFileSync(path.join(projectRoot, file), 'utf8');

const walk = directory =>
  readdirSync(path.join(projectRoot, directory), {
    withFileTypes: true,
  }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(file) : [file];
  });

const productionTsxFiles = walk('src').filter(
  file => file.endsWith('.tsx') && !file.includes('.test.'),
);

test('all production text inputs cross one enforced accessible boundary', () => {
  const boundary = 'src/design-system/components/AccessibleTextInput.tsx';
  const rawInputFiles = productionTsxFiles.filter(file => {
    const source = read(file);
    return /<TextInput\b/u.test(source) || /\bTextInputProps\b/u.test(source);
  });
  assert.deepEqual(rawInputFiles, [boundary]);

  const source = read(boundary);
  assert.match(
    source,
    /Omit<[\s\S]*'accessibilityLabel'[\s\S]*'allowFontScaling'[\s\S]*'maxFontSizeMultiplier'/u,
  );
  assert.match(source, /accessibilityLabel\.trim\(\)/u);
  assert.match(source, /requires a non-empty accessibilityLabel/u);
  assert.match(
    source,
    /<TextInput[\s\S]*\{\.\.\.inputProps\}[\s\S]*allowFontScaling[\s\S]*maxFontSizeMultiplier=\{2\}/u,
  );

  let usageCount = 0;

  for (const file of productionTsxFiles.filter(
    candidate => candidate !== boundary,
  )) {
    for (const match of read(file).matchAll(
      /<AccessibleTextInput\b[\s\S]*?\/>/gu,
    )) {
      usageCount += 1;
      assert.match(match[0], /accessibilityLabel=/u, file);
    }
  }

  assert.ok(usageCount >= 7);
});

test('interactive production primitives keep semantic state and minimum targets', () => {
  const primitives = read('src/design-system/components/Primitives.tsx');
  const shell = read('src/features/live/LiveAppShell.tsx');
  const theme = read('src/design-system/tokens/theme.ts');
  const cardSource = primitives.slice(
    primitives.indexOf('export function Card'),
    primitives.indexOf('type ButtonProps'),
  );

  assert.match(theme, /minimumTargetSize = 48/u);
  assert.doesNotMatch(cardSource, /accessibilityLabel/u);
  assert.match(primitives, /accessibilityRole="button"/u);
  assert.match(primitives, /accessibilityState=\{\{ disabled \}\}/u);
  assert.match(primitives, /accessibilityRole="radio"/u);
  assert.match(primitives, /accessibilityState=\{\{ selected \}\}/u);
  assert.match(primitives, /checked: selected/u);
  assert.match(primitives, /minHeight: minimumTargetSize/u);
  assert.match(shell, /accessibilityRole="tab"/u);
  assert.match(shell, /accessibilityRole="tablist"/u);
  assert.match(shell, /accessibilityState=\{\{ selected \}\}/u);
});

test('production UI owns high contrast reduced motion live regions and bidi layout', () => {
  const appProviders = read('src/app/AppProviders.tsx');
  const themeProvider = read('src/app/providers/ThemeProvider.tsx');
  const projection = read('src/features/live/LiveProjectionState.tsx');
  const appText = read('src/design-system/components/AppText.tsx');

  assert.match(appProviders, /language === 'ar-XB'/u);
  assert.match(appProviders, /direction: 'rtl'/u);
  assert.match(themeProvider, /isHighTextContrastEnabled/u);
  assert.match(themeProvider, /isDarkerSystemColorsEnabled/u);
  assert.match(themeProvider, /darkerSystemColorsChanged/u);
  assert.match(themeProvider, /highTextContrastChanged/u);
  assert.match(themeProvider, /reduceMotionChanged/u);
  assert.match(
    read('src/design-system/components/Primitives.tsx'),
    /pressed && !isReduceMotionEnabled/u,
  );
  assert.match(projection, /announceForAccessibilityWithOptions/u);
  assert.match(projection, /isScreenReaderEnabled/u);
  assert.match(
    projection,
    /Platform\.OS === 'android' \? 'polite' : undefined/u,
  );
  assert.match(
    projection,
    /Platform\.OS === 'android' \? 'assertive' : undefined/u,
  );
  assert.match(
    projection,
    /Platform\.OS === 'android' \? 'alert' : undefined/u,
  );
  assert.match(appText, /allowFontScaling/u);
  assert.match(appText, /maxFontSizeMultiplier=\{2\}/u);
});

test('raw production presses stay inside the reviewed design-system and tab boundary', () => {
  const rawPressFiles = productionTsxFiles.filter(file =>
    /<Pressable\b/u.test(read(file)),
  );
  assert.deepEqual(rawPressFiles, [
    'src/design-system/components/Primitives.tsx',
    'src/features/live/LiveAppShell.tsx',
  ]);

  const rawSwitchFiles = productionTsxFiles.filter(file =>
    /<Switch\b/u.test(read(file)),
  );
  assert.deepEqual(rawSwitchFiles, [
    'src/design-system/components/Primitives.tsx',
  ]);

  for (const file of productionTsxFiles) {
    const source = read(file);
    assert.doesNotMatch(
      source,
      /<Touchable(?:Opacity|Highlight|WithoutFeedback)\b/u,
      file,
    );
    assert.doesNotMatch(
      source,
      /import\s*\{[^}]*\b(?:Button|TouchableOpacity|TouchableHighlight|TouchableWithoutFeedback)\b[^}]*\}\s*from ['"]react-native['"]/su,
      file,
    );
  }
});

test('production screens remain scrollable and adaptive at enlarged text sizes', () => {
  const primitives = read('src/design-system/components/Primitives.tsx');
  const appText = read('src/design-system/components/AppText.tsx');
  const shell = read('src/features/live/LiveAppShell.tsx');
  const liveScreenFiles = walk('src/features/live').filter(file =>
    /testID="live-[^"]+-screen"/u.test(read(file)),
  );

  assert.match(primitives, /<ScrollView/u);
  assert.match(primitives, /contentContainerStyle=/u);
  assert.match(primitives, /screenContent:[\s\S]*flexGrow: 1/u);
  assert.match(primitives, /button:[\s\S]*flexWrap: 'wrap'/u);
  assert.match(
    primitives,
    /buttonText: \{ textAlign: 'center', flexShrink: 1 \}/u,
  );
  assert.match(primitives, /flexText: \{ flex: 1, minWidth: 0 \}/u);
  assert.match(appText, /maxFontSizeMultiplier=\{2\}/u);
  assert.doesNotMatch(appText, /numberOfLines=/u);
  assert.match(shell, /tabBar:[\s\S]*minHeight: 72/u);
  assert.doesNotMatch(
    shell.slice(
      shell.indexOf('tabBar:'),
      shell.indexOf('tab:', shell.indexOf('tabBar:')),
    ),
    /(?:^|\s)height:/u,
  );

  assert.ok(liveScreenFiles.length >= 12);
  for (const file of liveScreenFiles) {
    assert.match(read(file), /<Screen\b/u, file);
  }
  assert.match(
    read('src/features/live/LiveProductSetupJourney.tsx'),
    /<Screen\b/u,
  );
});
