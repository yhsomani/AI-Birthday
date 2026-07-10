import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';

const appSourceText = readFileSync(join(process.cwd(), 'src/App.tsx'), 'utf8');

type JsxOpening = {
  tag: string;
  body: string;
  line: number;
};

const lineForIndex = (index: number) => appSourceText.slice(0, index).split('\n').length;

const collectJsxOpenings = (): JsxOpening[] => {
  const openings: JsxOpening[] = [];
  const openingPattern = /<([A-Z][A-Za-z0-9.]*)\b/g;
  let match: RegExpExecArray | null;

  while ((match = openingPattern.exec(appSourceText))) {
    const start = match.index;
    let cursor = openingPattern.lastIndex;
    let braceDepth = 0;
    let quote: '"' | "'" | '`' | undefined;

    while (cursor < appSourceText.length) {
      const char = appSourceText[cursor];
      const previous = appSourceText[cursor - 1];
      if (quote) {
        if (char === quote && previous !== '\\') {
          quote = undefined;
        }
      } else if (char === '"' || char === "'" || char === '`') {
        quote = char;
      } else if (char === '{') {
        braceDepth += 1;
      } else if (char === '}') {
        braceDepth = Math.max(0, braceDepth - 1);
      } else if (char === '>' && braceDepth === 0) {
        cursor += 1;
        break;
      }
      cursor += 1;
    }

    openings.push({
      tag: match[1],
      body: appSourceText.slice(start, cursor),
      line: lineForIndex(start)
    });
    openingPattern.lastIndex = cursor;
  }

  return openings;
};

const hasAttribute = (opening: JsxOpening, attributeName: string) => {
  const attributePattern = new RegExp(`\\b${attributeName}(?:\\s*=|\\b)`);
  return attributePattern.test(opening.body);
};

const hasStringAttribute = (opening: JsxOpening, attributeName: string, value: string) => {
  const attributePattern = new RegExp(`\\b${attributeName}\\s*=\\s*["']${value}["']`);
  return attributePattern.test(opening.body);
};

const hasStateProperty = (opening: JsxOpening, propertyName: string) =>
  hasAttribute(opening, 'accessibilityState') && new RegExp(`\\b${propertyName}\\s*:`).test(opening.body);

const formatFailure = (opening: JsxOpening, problem: string) =>
  `${opening.tag} at src/App.tsx:${opening.line} ${problem}`;

describe('React Native accessibility contract', () => {
  const openings = collectJsxOpenings();

  it('labels every native touch target and gives it a screen-reader role', () => {
    const touchTargets = openings.filter(opening => ['TouchableOpacity', 'Pressable'].includes(opening.tag));
    const failures = touchTargets.flatMap(opening => {
      const problems: string[] = [];
      if (!hasAttribute(opening, 'accessibilityLabel')) {
        problems.push('is missing accessibilityLabel');
      }
      if (!hasAttribute(opening, 'accessibilityRole')) {
        problems.push('is missing accessibilityRole');
      }
      return problems.map(problem => formatFailure(opening, problem));
    });

    assert.ok(touchTargets.length > 0, 'Expected the RN shell to expose touch targets.');
    assert.deepEqual(failures, []);
  });

  it('labels every text input with its form purpose', () => {
    const textInputs = openings.filter(opening => opening.tag === 'TextInput');
    const failures = textInputs
      .filter(opening => !hasAttribute(opening, 'accessibilityLabel'))
      .map(opening => formatFailure(opening, 'is missing accessibilityLabel'));

    assert.ok(textInputs.length > 0, 'Expected the RN shell to expose text inputs.');
    assert.deepEqual(failures, []);
  });

  it('exposes selected state for tabs and checked state for checkboxes', () => {
    const failures = openings.flatMap(opening => {
      if (hasStringAttribute(opening, 'accessibilityRole', 'tab') && !hasStateProperty(opening, 'selected')) {
        return [formatFailure(opening, 'is a tab without selected accessibilityState')];
      }
      if (hasStringAttribute(opening, 'accessibilityRole', 'checkbox')) {
        const problems: string[] = [];
        if (!hasAttribute(opening, 'accessibilityLabel')) {
          problems.push('is a checkbox without accessibilityLabel');
        }
        if (!hasStateProperty(opening, 'checked')) {
          problems.push('is a checkbox without checked accessibilityState');
        }
        return problems.map(problem => formatFailure(opening, problem));
      }
      return [];
    });

    assert.deepEqual(failures, []);
  });
});
