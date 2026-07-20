import { liveEnglish, liveHindi } from './liveResources';
import { appI18n } from './i18n';
import { productionResources } from './productionResources';
import { safeReasonMessageKeys } from './reasonCopy';
import { resources } from './resources';
import { SAFE_REASON_CODES } from '../domain/shared/reasonCodes';

jest.mock('react-native-localize', () => ({
  getLocales: () => [{ languageCode: 'en' }],
}));

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
const typescript = require('typescript') as typeof import('typescript');

const technicalOnlyHindiValues = {
  'live.common.sim': 'SIM',
  'live.error.actionBody': '{{message}} {{reference}}',
} as const satisfies Readonly<Partial<typeof liveHindi>>;

const explicitDictionaryKeys = (
  source: string,
  dictionaryName: string,
): readonly string[] => {
  const sourceFile = typescript.createSourceFile(
    'liveResources.ts',
    source,
    typescript.ScriptTarget.Latest,
    true,
    typescript.ScriptKind.TS,
  );
  const declarations = sourceFile.statements.flatMap(statement =>
    typescript.isVariableStatement(statement)
      ? statement.declarationList.declarations.filter(
          declaration =>
            typescript.isIdentifier(declaration.name) &&
            declaration.name.text === dictionaryName,
        )
      : [],
  );

  if (declarations.length !== 1) {
    throw new Error(
      `Expected exactly one ${dictionaryName} dictionary, found ${declarations.length}`,
    );
  }

  const initializer = declarations[0]?.initializer;
  if (!initializer || !typescript.isObjectLiteralExpression(initializer)) {
    throw new Error(`${dictionaryName} must be an object literal`);
  }

  const keys = initializer.properties
    .filter(typescript.isPropertyAssignment)
    .map(property => {
      if (!typescript.isStringLiteral(property.name)) {
        throw new Error(
          `${dictionaryName} dictionary keys must be string literals`,
        );
      }
      return property.name.text;
    });
  if (new Set(keys).size !== keys.length) {
    throw new Error(`${dictionaryName} contains a duplicate explicit key`);
  }

  return keys.sort();
};

const walkProductionTypeScript = (directory: string): readonly string[] =>
  fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      return walkProductionTypeScript(file);
    }
    return /\.(?:ts|tsx)$/u.test(entry.name) &&
      !entry.name.includes('.test.') &&
      entry.name !== 'liveResources.ts'
      ? [file]
      : [];
  });

const productionLocalizationLiterals = (): ReadonlySet<string> => {
  const literals = new Set<string>();
  walkProductionTypeScript(path.resolve(__dirname, '..')).forEach(file => {
    const sourceFile = typescript.createSourceFile(
      file,
      fs.readFileSync(file, 'utf8'),
      typescript.ScriptTarget.Latest,
      true,
      file.endsWith('.tsx')
        ? typescript.ScriptKind.TSX
        : typescript.ScriptKind.TS,
    );
    const visit = (node: import('typescript').Node) => {
      if (typescript.isStringLiteralLike(node)) {
        literals.add(node.text);
      }
      typescript.forEachChild(node, visit);
    };
    visit(sourceFile);
  });
  return literals;
};

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

  it('explicitly overrides every English live key in Hindi', () => {
    const source = fs.readFileSync(
      path.resolve(__dirname, 'liveResources.ts'),
      'utf8',
    );

    expect(explicitDictionaryKeys(source, 'liveHindi')).toEqual(
      explicitDictionaryKeys(source, 'liveEnglish'),
    );
  });

  it('keeps every live key reachable from production copy paths', () => {
    const liveKeys = new Set(Object.keys(liveEnglish));
    const literals = productionLocalizationLiterals();
    const reachable = new Set(
      [...literals].filter(literal => liveKeys.has(literal)),
    );

    // i18next selects these suffixes from a literal base-key call with count.
    literals.forEach(literal => {
      for (const suffix of ['_one', '_other'] as const) {
        const pluralKey = `${literal}${suffix}`;
        if (liveKeys.has(pluralKey)) {
          reachable.add(pluralKey);
        }
      }
    });

    // These are the deliberately bounded template-literal key families.
    for (const tone of ['warm', 'simple', 'cheerful'] as const) {
      reachable.add(`live.message.${tone}`);
      reachable.add(`live.message.${tone}Sample`);
    }
    for (const enrollment of ['off', 'enabled', 'excluded'] as const) {
      reachable.add(`live.common.${enrollment}`);
    }

    expect([...liveKeys].filter(key => !reachable.has(key)).sort()).toEqual([]);
  });

  it('keeps non-Devanagari Hindi values limited to exact technical copy', () => {
    const keysWithoutDevanagari = Object.entries(liveHindi)
      .filter(([, value]) => !/[\u0900-\u097f]/u.test(value))
      .map(([key]) => key)
      .sort();
    const allowedKeys = Object.keys(technicalOnlyHindiValues).sort();

    expect(keysWithoutDevanagari).toEqual(allowedKeys);
    Object.entries(technicalOnlyHindiValues).forEach(([key, value]) => {
      expect(liveHindi[key as keyof typeof liveHindi]).toBe(value);
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

  it('selects locale-aware singular and plural copy for reported counts', () => {
    const cases = [
      ['live.common.countPeople', '1 person', '2 people', '1 व्यक्ति', '2 लोग'],
      [
        'live.setup.contactsVerified',
        '1 contact checked',
        '2 contacts checked',
        '1 संपर्क जाँचा गया',
        '2 संपर्क जाँचे गए',
      ],
      [
        'live.companion.scheduled',
        '1 reminder scheduled',
        '2 reminders scheduled',
        '1 रिमाइंडर तय है',
        '2 रिमाइंडर तय हैं',
      ],
      [
        'live.companion.planned',
        '1 birthday date planned',
        '2 birthday dates planned',
        '1 जन्मदिन तारीख नियोजित है',
        '2 जन्मदिन तारीखें नियोजित हैं',
      ],
      [
        'live.policy.simulatedDays',
        '1 day simulated',
        '2 days simulated',
        '1 दिन का सिमुलेशन',
        '2 दिनों का सिमुलेशन',
      ],
      [
        'live.diagnostics.healthReported',
        '1 technical check reported',
        '2 technical checks reported',
        '1 तकनीकी जाँच रिपोर्ट हुई',
        '2 तकनीकी जाँच रिपोर्ट हुईं',
      ],
    ] as const;

    cases.forEach(([key, englishOne, englishOther, hindiOne, hindiOther]) => {
      expect(appI18n.t(key, { count: 1, lng: 'en' })).toBe(englishOne);
      expect(appI18n.t(key, { count: 2, lng: 'en' })).toBe(englishOther);
      expect(appI18n.t(key, { count: 1, lng: 'hi' })).toBe(hindiOne);
      expect(appI18n.t(key, { count: 2, lng: 'hi' })).toBe(hindiOther);
    });
  });

  it('does not promise control over the iOS sender line or transport', () => {
    const english = [
      resources.en.translation['message.iosDisclosure'],
      liveEnglish['live.person.iosApprovalBody'],
      liveEnglish['live.companion.editableWarning'],
    ].join('\n');
    const hindi = [
      resources.hi.translation['message.iosDisclosure'],
      liveHindi['live.person.iosApprovalBody'],
      liveHindi['live.companion.editableWarning'],
    ].join('\n');

    expect(english).toMatch(/Messages and iOS control/u);
    expect(english).toMatch(/cannot select or guarantee/u);
    expect(english).not.toMatch(
      /choose a sender line|you control.*sender line/u,
    );
    expect(hindi).toMatch(/Messages व iOS नियंत्रित करते हैं/u);
    expect(hindi).toMatch(/चुन या पक्का नहीं कर सकता/u);
    expect(hindi).not.toMatch(/सेंडर लाइन चुनें/u);
    expect(liveEnglish['live.companion.editableWarning']).toMatch(
      /SMS or MMS carrier charges may apply/u,
    );
    expect(liveHindi['live.companion.editableWarning']).toMatch(
      /SMS या MMS पर कैरियर शुल्क लग सकता है/u,
    );
  });

  it('keeps Android TEST submission, device send, and protected pass truth distinct', () => {
    const englishSubmitted = liveEnglish['live.automation.test.submitted'];
    const englishSent = liveEnglish['live.automation.test.sent'];
    const englishPassed = liveEnglish['live.automation.test.passed'];
    const hindiSubmitted = liveHindi['live.automation.test.submitted'];
    const hindiSent = liveHindi['live.automation.test.sent'];
    const hindiPassed = liveHindi['live.automation.test.passed'];

    expect(new Set([englishSubmitted, englishSent, englishPassed]).size).toBe(
      3,
    );
    expect(new Set([hindiSubmitted, hindiSent, hindiPassed]).size).toBe(3);
    expect(englishSubmitted).toMatch(/submitted/u);
    expect(englishSubmitted).toMatch(/delivery is not confirmed/u);
    expect(englishSent).toMatch(/left this phone/u);
    expect(englishSent).toMatch(/carrier delivery is not confirmed/u);
    expect(englishPassed).toMatch(/passed/u);
    expect(hindiSubmitted).toMatch(/जमा/u);
    expect(hindiSent).toMatch(/फ़ोन से निकल/u);
    expect(hindiPassed).toMatch(/पास/u);
    expect(`${englishSubmitted}\n${englishSent}`).not.toMatch(
      /delivered|delivery confirmed/u,
    );
  });

  it('localizes compact platform actions and support disclosures in English and Hindi', () => {
    const compactKeys = [
      'live.privacy.showDataDetails',
      'live.privacy.hideDataDetails',
      'live.automation.testRequiredTitle',
      'live.automation.testRequiredBody',
      'live.automation.runAnotherTest',
      'live.automation.checkTestStatus',
      'live.automation.showSupportDetails',
      'live.automation.hideSupportDetails',
      'live.automation.supportDetailsBody',
      'live.companion.checkReminderStatus',
      'live.companion.showReminderDetails',
      'live.companion.hideReminderDetails',
      'live.companion.reminderDetailsBody',
      'live.companion.checkPauseStatus',
      'live.companion.pauseVerificationComplete',
      'live.companion.pauseVerificationStillRequired',
    ] as const;

    compactKeys.forEach(key => {
      expect(liveEnglish[key].trim().length).toBeGreaterThan(0);
      expect(liveHindi[key]).toMatch(/[\u0900-\u097f]/u);
      expect(liveHindi[key]).not.toBe(liveEnglish[key]);
    });

    const iosEnglish = [
      liveEnglish['live.automation.iosBody'],
      liveEnglish['live.companion.activationDisclosure'],
      liveEnglish['live.companion.reminderDetailsBody'],
    ].join('\n');
    const iosHindi = [
      liveHindi['live.automation.iosBody'],
      liveHindi['live.companion.activationDisclosure'],
      liveHindi['live.companion.reminderDetailsBody'],
    ].join('\n');
    expect(iosEnglish).toMatch(/never send|never sends/u);
    expect(iosEnglish).not.toMatch(/messages? (?:are|is) sent automatically/u);
    expect(iosHindi).toMatch(/(?:संदेश नहीं भेज|अपने-आप नहीं भेज)/u);
    expect(iosHindi).not.toMatch(/अपने-आप संदेश भेज/u);
  });

  it('keeps message authoring optional, privacy-bounded, and honest about approval invalidation', () => {
    const progressiveKeys = [
      'live.message.currentTitle',
      'live.message.showHelp',
      'live.message.helpBody',
      'live.message.showOptions',
      'live.message.optionsBody',
      'live.message.warmSample',
      'live.message.simpleSample',
      'live.message.cheerfulSample',
      'live.message.validationPassed',
      'live.message.approvalConsequenceTitle',
      'live.message.approvalConsequenceBody',
      'live.message.savedRecheckFailed',
    ] as const;

    progressiveKeys.forEach(key => {
      expect(liveEnglish[key].trim().length).toBeGreaterThan(0);
      expect(liveHindi[key]).toMatch(/[\u0900-\u097f]/u);
      expect(liveHindi[key]).not.toBe(liveEnglish[key]);
    });

    expect(liveEnglish['live.message.showHelp']).toBe('Help me write');
    expect(liveEnglish['live.message.showOptions']).toBe('Message options');
    expect(liveEnglish['live.message.helpBody']).toMatch(
      /work without Gemini/u,
    );
    expect(liveEnglish['live.message.geminiPrivacyBody']).toMatch(
      /only the selected language, tone, name style and SMS-part limit/u,
    );
    expect(liveEnglish['live.message.geminiPrivacyBody']).toMatch(
      /no contact names, phone numbers, birthdays/u,
    );
    expect(liveEnglish['live.message.geminiPrivacyBody']).toMatch(
      /no.*current saved or draft message text/u,
    );
    expect(liveEnglish['live.message.save']).toBe('Save message');
    expect(liveEnglish['live.message.approvalConsequenceBody']).toMatch(
      /clears affected recipient approvals/u,
    );
    expect(liveEnglish['live.message.saved']).toMatch(/checked again/u);
    expect(liveEnglish['live.message.savedRecheckFailed']).toMatch(
      /saved.*could not be checked/u,
    );
    expect(liveEnglish['live.message.savedRecheckFailed']).not.toMatch(
      /saved and checked again/u,
    );

    expect(
      new Set([
        liveEnglish['live.message.warmSample'],
        liveEnglish['live.message.simpleSample'],
        liveEnglish['live.message.cheerfulSample'],
      ]).size,
    ).toBe(3);
  });

  it('keeps Schedule progressive and platform-honest before saving', () => {
    const scheduleKeys = [
      'live.policy.currentTitle',
      'live.policy.currentChecking',
      'live.policy.currentUnverified',
      'live.policy.notConfigured',
      'live.policy.showOptions',
      'live.policy.hideOptions',
      'live.policy.reviewedWindow',
      'live.policy.androidSaveConsequenceTitle',
      'live.policy.androidSaveConsequenceBody',
      'live.policy.iosSaveConsequenceTitle',
      'live.policy.iosSaveConsequenceBody',
      'live.policy.savedNeedsCheck',
    ] as const;

    scheduleKeys.forEach(key => {
      expect(liveEnglish[key].trim().length).toBeGreaterThan(0);
      expect(liveHindi[key]).toMatch(/[\u0900-\u097f]/u);
      expect(liveHindi[key]).not.toBe(liveEnglish[key]);
    });

    expect(liveEnglish['live.policy.androidSafetySummary']).toMatch(
      /local time.*selected or default SIM/u,
    );
    expect(liveEnglish['live.policy.androidSafetySummary']).toMatch(
      /at least five minutes apart/u,
    );
    expect(liveEnglish['live.policy.androidSafetySummary']).toMatch(
      /20 distinct greetings in a rolling 24 hours/u,
    );
    expect(liveEnglish['live.policy.androidSafetySummary']).toMatch(
      /three explicit TEST messages.*separate rolling budget/u,
    );
    expect(liveEnglish['live.policy.androidSaveConsequenceBody']).toMatch(
      /clears affected recipient approvals.*invalidates the bound SMS TEST receipt/u,
    );
    expect(liveEnglish['live.policy.iosSafetySummary']).toMatch(
      /best effort.*never sends automatically/u,
    );
    expect(liveEnglish['live.policy.iosSaveConsequenceBody']).toMatch(
      /invalidates affected birthday proposals.*rebuilds.*reminder plan/u,
    );
    expect(liveEnglish['live.policy.savedNeedsCheck']).toMatch(
      /saved.*could not be checked/u,
    );
    expect(liveEnglish['live.policy.savedNeedsCheck']).not.toMatch(
      /saved and checked again/u,
    );
  });

  it('discloses the sticky account-wide iOS hold before the final Review message tap', () => {
    const english = liveEnglish['live.companion.editableWarning'];
    const hindi = liveHindi['live.companion.editableWarning'];
    expect(english).toMatch(
      /Tapping Review message commits an account-wide safety hold/u,
    );
    expect(english).toMatch(/before presentation/u);
    expect(english).toMatch(
      /pause Android birthday sending for up to 72 hours/u,
    );
    expect(english).toMatch(
      /even after Cancel, presentation failure, or an unknown result/u,
    );
    expect(english).toMatch(/Android birthdays may be missed/u);
    expect(hindi).toMatch(/संदेश की समीक्षा दबाते ही/u);
    expect(hindi).toMatch(/पूरे खाते की सुरक्षा रोक/u);
    expect(hindi).toMatch(/72 घंटे तक रोक सकती है/u);
    expect(hindi).toMatch(
      /Cancel, प्रस्तुति विफलता या अज्ञात परिणाम के बाद भी/u,
    );
    expect(hindi).toMatch(/जन्मदिन संदेश छूट सकते हैं/u);
  });

  it('describes cloud metadata and AI requests without claiming that cloud use is data-free', () => {
    const english = [
      liveEnglish['live.setup.contactsPrivacyAndroid'],
      liveEnglish['live.setup.contactsPrivacyIos'],
      liveEnglish['live.message.geminiPrivacyBody'],
      liveEnglish['live.privacy.cloudMetadataBody'],
      liveEnglish['live.privacy.androidCoordinationBoundary'],
      liveEnglish['live.privacy.iosCoordinationBoundary'],
      liveEnglish['live.privacy.providerRetentionBody'],
    ].join('\n');
    const hindi = [
      liveHindi['live.setup.contactsPrivacyAndroid'],
      liveHindi['live.setup.contactsPrivacyIos'],
      liveHindi['live.message.geminiPrivacyBody'],
      liveHindi['live.privacy.cloudMetadataBody'],
      liveHindi['live.privacy.androidCoordinationBoundary'],
      liveHindi['live.privacy.iosCoordinationBoundary'],
      liveHindi['live.privacy.providerRetentionBody'],
    ].join('\n');

    expect(english).toMatch(/fixed-length pseudonymous/u);
    expect(english).toMatch(/not anonymous/u);
    expect(english).toMatch(/Firebase Installations token/u);
    expect(english).toMatch(/does not register recipients/u);
    expect(english).toMatch(/composer-reservation/u);
    expect(english).toMatch(/one-way owner-capability key/u);
    expect(english).toMatch(
      /not released by Cancel, presentation failure, an unknown result/u,
    );
    expect(english).toMatch(/no contact names, phone numbers, birthdays/u);
    expect(english).toMatch(/provider logs/u);
    expect(english).not.toMatch(/no cloud data|nothing.*cloud/u);
    expect(hindi).toMatch(/तय लंबाई वाले छद्मनामित/u);
    expect(hindi).toMatch(/अनाम नहीं/u);
    expect(hindi).toMatch(/Firebase Installations टोकन/u);
    expect(hindi).toMatch(/पंजीकृत नहीं करता/u);
    expect(hindi).toMatch(/कंपोज़र-रिज़र्वेशन/u);
    expect(hindi).toMatch(/एकतरफ़ा मालिक-क्षमता कुंजी/u);
    expect(hindi).toMatch(/संपर्क नाम, फ़ोन नंबर, जन्मदिन/u);
    expect(hindi).toMatch(/प्रदाता लॉग/u);
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
      .filter(
        entry =>
          !entry.isDirectory() &&
          entry.name.endsWith('.tsx') &&
          !entry.name.endsWith('.test.tsx') &&
          !entry.name.endsWith('.spec.tsx'),
      )
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
