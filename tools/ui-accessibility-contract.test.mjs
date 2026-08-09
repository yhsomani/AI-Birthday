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
    const file = path.join(directory, entry.name).split(path.sep).join('/');
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
  const textInput = read(
    'src/design-system/components/AccessibleTextInput.tsx',
  );
  const shell = read('src/features/live/LiveAppShell.tsx');
  const theme = read('src/design-system/tokens/theme.ts');
  const cardSource = primitives.slice(
    primitives.indexOf('export function Card'),
    primitives.indexOf('export function InlineReviewCard'),
  );

  assert.match(theme, /minimumTargetSize = 48/u);
  assert.doesNotMatch(cardSource, /accessibilityLabel/u);
  assert.match(primitives, /accessibilityRole="button"/u);
  assert.match(
    primitives,
    /accessibilityState=\{\{[\s\S]*disabled,[\s\S]*expanded[\s\S]*\}\}/u,
  );
  assert.match(primitives, /accessibilityRole="radio"/u);
  assert.match(primitives, /accessibilityState=\{\{ selected \}\}/u);
  assert.match(primitives, /accessibilityRole="radiogroup"/u);
  assert.match(primitives, /selected-indicator/u);
  assert.match(primitives, /checked: selected/u);
  assert.match(primitives, /minHeight: minimumTargetSize/u);
  assert.match(
    primitives,
    /export function SettingRow[\s\S]*accessibilityRole="button"[\s\S]*accessibilityLabel=\{`\$\{title\}\. \$\{detail\}`\}/u,
  );
  assert.match(
    primitives,
    /settingRow: \{[\s\S]*minHeight: minimumTargetSize/u,
  );
  assert.match(shell, /accessibilityRole="tab"/u);
  assert.match(shell, /accessibilityRole="tablist"/u);
  assert.match(shell, /accessibilityState=\{\{ disabled, selected \}\}/u);
  assert.ok(
    (primitives.match(/onFocus=\{\(\) => setFocused\(true\)\}/gu) ?? [])
      .length >= 5,
  );
  assert.match(
    primitives,
    /outlineColor: color,[\s\S]*outlineWidth: focused \? 3 : 0/u,
  );
  assert.match(
    primitives,
    /export function FocusablePressable[\s\S]*onFocus=\{event =>[\s\S]*setFocused\(true\)[\s\S]*focusOutline\(focused, colors\.focus\)/u,
  );
  assert.match(textInput, /onFocus=\{event =>[\s\S]*setFocused\(true\)/u);
  assert.match(textInput, /outlineColor: colors\.focus/u);
  assert.match(shell, /<FocusablePressable[\s\S]*accessibilityRole="tab"/u);
});

test('production icons are decorative vectors rather than platform-font glyphs', () => {
  const icon = read('src/design-system/components/Icon.tsx');

  assert.equal(
    (icon.match(/from 'react-native-svg\/src\/fabric\/[A-Za-z]+'/gu) ?? [])
      .length,
    3,
  );
  assert.match(icon, /accessibilityElementsHidden/u);
  assert.match(icon, /importantForAccessibility="no-hide-descendants"/u);
  assert.match(icon, /focusable=\{false\}/u);
  assert.match(icon, /matrix: \[-1, 0, 0, 1, 24, 0\]/u);
  assert.match(icon, /processColor\(color\)/u);
  assert.match(icon, /payload: processedColor,[\s\S]*type: 0/u);
  assert.doesNotMatch(icon, /<Text|const glyphs/u);
});

test('focused Settings rows have one unique accessible destination each', () => {
  const settings = read('src/features/live/LiveSettingsScreen.tsx');
  const groupKeys = [
    'live.settings.birthdayPlan',
    'live.settings.accountPrivacy',
    'live.settings.help',
  ];
  const destinationIds = [
    'live-settings-message',
    'live-settings-schedule',
    'live-settings-automation',
    'live-settings-privacy',
    'live-settings-help-legal',
  ];

  for (const id of destinationIds) {
    assert.equal(
      (settings.match(new RegExp(`testID=["']${id}["']`, 'gu')) ?? []).length,
      1,
      `${id} must identify exactly one accessible Settings row`,
    );
  }
  assert.equal(new Set(destinationIds).size, destinationIds.length);
  for (const key of groupKeys) {
    assert.equal(
      (
        settings.match(
          new RegExp(
            `<SectionHeading\\s+title=\\{t\\(["']${key.replaceAll(
              '.',
              '\\.',
            )}["']\\)\\}\\s*/>`,
            'gu',
          ),
        ) ?? []
      ).length,
      1,
      `${key} must identify exactly one semantic Settings group`,
    );
  }
  assert.equal(new Set(groupKeys).size, groupKeys.length);
  assert.doesNotMatch(settings, /<Button\b/u);
  assert.doesNotMatch(
    settings,
    /testID=["']live-settings-(?:activity|attention|diagnostics|refresh)["']/u,
  );
  assert.doesNotMatch(
    settings,
    /onOpenActivity|onOpenAttention|onOpenDiagnostics|getReadiness|getInventory|LivePrivacyInventory|phoneAppearance|phoneLanguage|live\.settings\.(?:readiness|inventory|refresh)/u,
  );
});

test('focused Home keeps one prioritized action and hides routine internals', () => {
  const home = read('src/features/live/LiveHomeScreen.tsx');
  const retainedIds = [
    'live-home-setup-incomplete',
    'live-home-continue-setup',
    'live-home-approved-message-toggle',
    'live-home-approved-message',
    'live-home-attention',
    'live-home-review-today',
    'live-home-automation',
    'live-home-activity',
    'live-home-pause',
    'live-home-confirm-pause',
  ];

  for (const id of retainedIds) {
    assert.equal(
      (home.match(new RegExp(`testID=["']${id}["']`, 'gu')) ?? []).length,
      1,
      `${id} must identify exactly one Home control or disclosure`,
    );
  }
  assert.equal(new Set(retainedIds).size, retainedIds.length);
  assert.doesNotMatch(
    home,
    /testID=["']live-home-(?:message|refresh|active-sender)["']/u,
  );
  assert.doesNotMatch(
    home,
    /live\.home\.(?:openMessage|refresh|service|scheduler|coordination|activeSender)/u,
  );
  assert.doesNotMatch(
    home,
    /schedulerHeartbeatAt|lastCoordinationSuccessAt|SenderProjection|formatLiveInstant/u,
  );

  const setupResumeStart = home.indexOf('if (productSetupRequired)');
  const homeLoadingStart = home.indexOf("if (home.state.kind === 'loading')");
  assert.ok(
    setupResumeStart >= 0 && homeLoadingStart > setupResumeStart,
    'unfinished setup must stay resumable even when ordinary Home truth is unavailable',
  );
  const setupResume = home.slice(setupResumeStart, homeLoadingStart);
  assert.match(setupResume, /testID="live-home-continue-setup"/u);
  assert.doesNotMatch(
    setupResume,
    /onOpenAttention|onOpenAutomation|prepareToday|pauseFromHome/u,
  );

  const priorityStart = home.indexOf('{homeStable && !isInlineReviewOpen ?');
  const priorityEnd = home.indexOf(
    "<SectionHeading title={t('live.home.atAGlance')} />",
    priorityStart,
  );
  assert.ok(priorityStart >= 0 && priorityEnd > priorityStart);
  const priority = home.slice(priorityStart, priorityEnd);
  assert.ok(priority.indexOf('needsRepair ?') >= 0);
  assert.ok(
    priority.indexOf('hasTodayReview ?') > priority.indexOf('needsRepair ?'),
  );
  assert.ok(
    priority.indexOf('showPlanAction ?') > priority.indexOf('hasTodayReview ?'),
  );
  assert.match(priority, /testID="live-home-attention"/u);
  assert.match(priority, /testID="live-home-review-today"/u);
  assert.match(priority, /testID="live-home-automation"/u);

  const summaryStart = priorityEnd;
  const summaryEnd = home.indexOf(
    "{projection.contactsSync.kind !== 'fresh' ?",
    summaryStart,
  );
  const summary = home.slice(summaryStart, summaryEnd);
  assert.equal((summary.match(/<StatusRow\b/gu) ?? []).length, 2);
  assert.match(summary, /live\.home\.birthdaysSummary/u);
  assert.match(summary, /live\.home\.peopleSummary/u);

  assert.match(
    home,
    /approvedMessageVisible[\s\S]*live\.home\.hideApprovedMessage[\s\S]*live\.home\.viewApprovedMessage[\s\S]*testID="live-home-approved-message-toggle"[\s\S]*approvedMessageVisible \? \([\s\S]*testID="live-home-approved-message"/u,
  );
  assert.doesNotMatch(
    home,
    /live\.companion\.(?:scheduled|planned)|live\.home\.messageUiAvailable/u,
  );
  for (const warningKey of [
    'live.home.notificationVisibility',
    'live.companion.failedReminderCount',
    'live.companion.truncated',
    'live.companion.earliestUnscheduled',
    'live.home.messageUiUnavailable',
  ]) {
    assert.match(home, new RegExp(warningKey.replaceAll('.', '\\.'), 'u'));
  }
});

test('Composer Review owns one focused inline review and Automation exposes no composer controls', () => {
  const composer = read('src/features/live/LiveComposerReviewScreen.tsx');
  const automation = read('src/features/live/LiveAutomationScreen.tsx');
  const composerIds = [
    'live-composer-review-screen',
    'live-composer-review-back',
    'live-prepare-composer',
    'live-ios-composer-review',
    'live-composer-final-disclosure',
    'live-open-composer',
    'live-composer-repair-contacts',
    'live-composer-post-safety',
  ];

  for (const id of composerIds) {
    assert.equal(
      (composer.match(new RegExp(`testID=["']${id}["']`, 'gu')) ?? []).length,
      1,
      `${id} must identify exactly one Composer Review element`,
    );
    assert.doesNotMatch(
      automation,
      new RegExp(`testID=["']${id}["']`, 'u'),
      `${id} must not leak back into Settings Automation`,
    );
  }
  assert.equal(new Set(composerIds).size, composerIds.length);
  assert.match(
    composer,
    /<InlineReviewCard[\s\S]*?reviewKey=\{`\$\{review\.proposalId\}:\$\{review\.revision\}`\}[\s\S]*?testID="live-ios-composer-review"[\s\S]*?title=\{t\('live\.companion\.reviewTitle'\)\}/u,
  );
  assert.match(
    composer,
    /<AppText variant="title" accessibilityRole="header">/u,
  );
  assert.doesNotMatch(
    automation,
    /getNextComposerProposal|prepareComposerReview|canOpenComposer|openUserConfirmedComposer/u,
  );
});

test('Message keeps exact review ahead of optional authoring disclosures', () => {
  const message = read('src/features/live/LiveMessageScreen.tsx');
  const orderedMarkers = [
    'testID="live-message-current"',
    'testID="live-message-language-en"',
    'testID="live-message-input"',
    'testID="live-message-preview"',
    'testID="live-message-help-toggle"',
    'testID="live-message-options-toggle"',
  ];

  let prior = -1;
  for (const marker of orderedMarkers) {
    const index = message.indexOf(marker);
    assert.ok(index > prior, `${marker} must retain the focused reading order`);
    prior = index;
  }
  for (const id of [
    'live-message-help-toggle',
    'live-message-options-toggle',
    'live-message-review',
    'live-message-approval-consequence',
  ]) {
    assert.equal(
      (message.match(new RegExp(`testID=["']${id}["']`, 'gu')) ?? []).length,
      1,
      `${id} must identify one Message disclosure or review`,
    );
  }
  assert.match(
    message,
    /<InlineReviewCard[\s\S]*reviewKey=\{preview\.preview\.handle\}[\s\S]*testID="live-message-review"/u,
  );
  assert.match(
    message,
    /port\.generateSuggestions\(\{[\s\S]*language,[\s\S]*tone,[\s\S]*placeholderMode:[\s\S]*requestedSegmentCap: segmentCap,[\s\S]*\}\)/u,
  );
});

test('platform settings expose stable action and support-detail identifiers', () => {
  const automation = read('src/features/live/LiveAutomationScreen.tsx');
  const schedule = read('src/features/live/LiveScheduleScreen.tsx');
  const policy = read('src/features/live/LivePolicyEditor.tsx');
  const device = read('src/features/live/LiveAndroidDeviceControls.tsx');
  const automationIds = [
    'live-run-another-test',
    'live-automation-support-toggle',
    'live-automation-support-details',
    'live-automation-open-schedule',
    'live-reminder-permission',
    'live-reminder-settings',
    'live-reminder-check-status',
    'live-reminder-support-toggle',
    'live-reminder-support-details',
    'live-ios-open-schedule',
    'live-ios-check-pause-status',
  ];
  const transferIds = [
    'live-prepare-sender-transfer',
    'live-confirm-sender-transfer',
    'live-continue-sender-transfer',
    'live-transfer-open-automation',
    'live-transfer-support-toggle',
    'live-transfer-support-details',
    'live-check-sender-transfer',
  ];

  for (const id of automationIds) {
    assert.equal(
      (automation.match(new RegExp(`testID=["']${id}["']`, 'gu')) ?? []).length,
      1,
      `${id} must identify exactly one Automation control or disclosure`,
    );
  }
  assert.equal(
    (
      automation.match(
        /const androidTestStatusButtonId = 'live-check-test-status';/gu,
      ) ?? []
    ).length,
    1,
    'live-check-test-status must have one stable Automation identifier',
  );
  assert.match(
    automation,
    /(?:retryTestID|testID)=\{androidTestStatusButtonId\}/u,
  );
  for (const id of transferIds) {
    assert.equal(
      (device.match(new RegExp(`testID=["']${id}["']`, 'gu')) ?? []).length,
      1,
      `${id} must identify exactly one sender-transfer control or disclosure`,
    );
  }

  assert.match(automation, /androidTestInFlightPhases/u);
  assert.match(automation, /problem\.supportCode === 'NATIVE_NOT_CONFIGURED'/u);
  assert.match(automation, /showTestForm/u);
  assert.match(automation, /readiness\.activation\.kind === 'allowed'/u);
  assert.match(automation, /homeTrusted/u);
  assert.doesNotMatch(automation, /LivePolicyEditor/u);
  assert.doesNotMatch(
    automation,
    /testID=["']live-(?:automation|ios)-policy-toggle["']/u,
  );
  assert.match(
    automation,
    /androidPolicyRepairRequired[\s\S]*?testID="live-automation-open-schedule"/u,
  );
  assert.match(
    automation,
    /iosPolicyRepairRequired[\s\S]*?testID="live-ios-open-schedule"/u,
  );
  assert.match(
    schedule,
    /import \{ LivePolicyEditor \} from '\.\/LivePolicyEditor';/u,
  );
  assert.match(
    schedule,
    /<LivePolicyEditor platform=\{platform\} port=\{port\} \/>/u,
  );
  for (const id of [
    'live-policy-current-summary',
    'live-policy-options-toggle',
    'live-policy-review',
    'live-policy-save-consequence',
  ]) {
    assert.equal(
      (policy.match(new RegExp(`testID=["']${id}["']`, 'gu')) ?? []).length,
      id === 'live-policy-save-consequence' ? 2 : 1,
      `${id} must retain its platform-appropriate Schedule identifier`,
    );
  }
  assert.match(
    policy,
    /optionsExpanded \? \([\s\S]*testID="live-policy-grace"[\s\S]*testID="live-policy-daily-cap"/u,
  );
  assert.match(
    policy,
    /<InlineReviewCard[\s\S]*reviewKey=\{preview\.preview\.handle\}[\s\S]*testID="live-policy-review"/u,
  );
  assert.match(device, /transferProjectionStable/u);
  assert.match(device, /!transferApplicable/u);
});

test('Attention keeps one safe repair action and discloses support identifiers explicitly', () => {
  const attention = read('src/features/live/LiveAttentionScreen.tsx');

  assert.doesNotMatch(
    attention,
    /issue\.action\.labelKey/u,
    'native label keys are protocol data and must never become user copy',
  );
  assert.match(
    attention,
    /issue\.action \? \([\s\S]*live-attention-action-\$\{issue\.id\}[\s\S]*\) : repairRoute \? \(/u,
    'a native action must take precedence over any derived app route',
  );
  assert.match(attention, /testID="live-attention-support-toggle"/u);
  assert.match(attention, /testID="live-attention-support-details"/u);
  assert.match(
    attention,
    /testID=\{`live-attention-support-\$\{issue\.id\}`\}/u,
  );
  assert.match(attention, /testID="live-attention-support-load-error"/u);
  assert.match(attention, /testID="live-attention-support-action-error"/u);

  const visibleStart = attention.indexOf(
    'testID={`live-attention-issue-${issue.id}`}',
  );
  const supportStart = attention.indexOf(
    'testID="live-attention-support-details"',
  );
  assert.ok(visibleStart >= 0 && supportStart > visibleStart);
  const visibleRepairList = attention.slice(visibleStart, supportStart);
  assert.doesNotMatch(
    visibleRepairList,
    /live\.common\.(?:code|blocks|reference)/u,
  );
  assert.equal(
    (attention.match(/showSupportReference=\{false\}/gu) ?? []).length,
    2,
  );

  const supportDetails = attention.slice(supportStart);
  assert.match(supportDetails, /live\.common\.code/u);
  assert.match(supportDetails, /live\.common\.blocks/u);
  assert.match(supportDetails, /live\.common\.reference/u);
  assert.match(supportDetails, /nativeProblemReference\(loadProblem\)/u);
  assert.match(supportDetails, /nativeProblemReference\(problem\)/u);

  const projectionState = read('src/features/live/LiveProjectionState.tsx');
  assert.equal(
    (projectionState.match(/showSupportReference = false/gu) ?? []).length,
    2,
    'ordinary projection and action errors must hide technical support references by default',
  );
  const diagnostics = read('src/features/live/LiveDiagnosticsScreen.tsx');
  assert.match(
    diagnostics,
    /<LiveActionFeedback[\s\S]*showSupportReference[\s\S]*\/>/u,
    'Diagnostics remains the explicit surface that may reveal a support reference',
  );

  assert.match(
    attention,
    /case 'blocking':[\s\S]*return 'critical'[\s\S]*case 'warning':[\s\S]*return 'warning'[\s\S]*case 'info':[\s\S]*return 'info'/u,
  );
  assert.match(
    attention,
    /issue\.code\.startsWith\('phone-'\)[\s\S]*!phoneStatePermissionCodes\.has\(issue\.code\)/u,
  );
  assert.match(
    attention,
    /issue\.code === 'invalid-segment-cap'[\s\S]*return 'message'/u,
  );
});

test('high-consequence inline reviews reveal, announce and expose one choice state', () => {
  const primitives = read('src/design-system/components/Primitives.tsx');
  const privacy = read('src/features/live/LivePrivacyScreen.tsx');
  const automation = read('src/features/live/LiveAutomationScreen.tsx');
  const composerReview = read('src/features/live/LiveComposerReviewScreen.tsx');
  const inlineReview = primitives.slice(
    primitives.indexOf('export function InlineReviewCard'),
    primitives.indexOf('type ButtonProps'),
  );

  assert.match(primitives, /scrollView\.current\?\.scrollTo/u);
  assert.match(inlineReview, /onLayout=\{onLayout\}/u);
  assert.match(inlineReview, /scrollToReview\(y\)/u);
  assert.match(inlineReview, /isScreenReaderEnabled/u);
  assert.match(inlineReview, /setAccessibilityFocus/u);
  assert.match(inlineReview, /announceForAccessibilityWithOptions/u);
  assert.match(inlineReview, /accessibilityRole="header"/u);

  assert.doesNotMatch(privacy, /<SingleChoiceGroup|live-privacy-prepare/u);
  for (const group of [
    'live-privacy-group-data-on-phone',
    'live-privacy-group-contacts-google',
    'live-privacy-group-sign-out',
    'live-privacy-group-wipe-local',
    'live-privacy-group-delete-account',
  ]) {
    assert.match(privacy, new RegExp(`testID="${group}"`, 'u'));
  }
  assert.match(privacy, /onPress=\{\(\) => prepareActionReview\(kind/u);
  assert.match(privacy, /testID="live-privacy-review"/u);
  assert.match(automation, /testID="live-ios-activation-review"/u);
  assert.match(composerReview, /testID="live-ios-composer-review"/u);
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

test('setup stays progressive resumable and fail-closed around native truth', () => {
  const setup = read('src/features/live/LiveSetupScreen.tsx');
  const journey = read('src/features/live/LiveProductSetupJourney.tsx');
  const boundary = read('src/app/NativeAppBoundary.tsx');
  const shell = read('src/features/live/LiveAppShell.tsx');
  const automation = read('src/features/live/LiveAutomationScreen.tsx');
  const approvals = read('src/features/live/LiveBatchApprovalScreen.tsx');

  assert.match(
    setup,
    /case 'compatibility':\s*case 'google-account':\s*return 1;/u,
  );
  assert.match(
    setup,
    /case 'contacts-disclosure':\s*case 'sync-summary':\s*return 2;/u,
  );
  const eligibility = setup.indexOf('testID="live-setup-eligibility"');
  const cost = setup.indexOf('testID="live-setup-cost-consent"');
  const setupAction = setup.indexOf('testID="live-setup-action"');
  assert.ok(eligibility >= 0 && cost > eligibility && setupAction > cost);
  assert.match(setup, /testID="live-setup-defer"/u);
  assert.match(
    setup,
    /disabled=\{actionPending \|\| !setupProjectionStable\}/u,
  );
  assert.doesNotMatch(setup, /SectionHeading|gateLabel|deliveryReadiness/u);

  assert.match(journey, /testID="live-product-setup-progress-summary"/u);
  assert.match(journey, /\? \{ kind: 'schedule', returnTo: 'overview' \}/u);
  assert.doesNotMatch(
    journey,
    /live-product-setup-(?:people|message|automation|approvals)/u,
  );
  assert.equal(
    (journey.match(/testID="live-product-setup-next"/gu) ?? []).length,
    1,
  );

  assert.match(boundary, /!lifecycleRecoveryRequired && earlySetupDeferred/u);
  assert.match(boundary, /productSetupRequired/u);
  assert.match(
    boundary,
    /retainedSetupEnvelope\.revision !== bootstrap\.revision/u,
  );
  assert.match(boundary, /accountRequiresLifecycleRecovery/u);
  assert.match(setup, /projectionRevisionMatchesBootstrap/u);
  assert.match(setup, /bootstrapLifecycleConflict/u);
  assert.match(
    shell,
    /const disabled = productSetupRequired && name !== 'Home'/u,
  );
  assert.match(shell, /accessibilityState=\{\{ disabled, selected \}\}/u);
  assert.match(boundary, /onContinueSetup/u);
  assert.match(boundary, /onDefer/u);

  assert.match(automation, /const performReadinessAction = async/u);
  assert.match(
    automation,
    /port\.performAction\(\{\s*handle: action\.handle,\s*expectedRevision: sourceRevision/u,
  );
  assert.match(automation, /testID="live-automation-readiness-action"/u);
  assert.match(automation, /t\('live\.attention\.openAction'\)/u);
  assert.doesNotMatch(automation, /\.labelKey/u);

  assert.match(approvals, /sourceRevision: NativeRevision/u);
  assert.match(approvals, /candidateUsable/u);
  assert.match(approvals, /protectedWorkGenerationRef/u);
  assert.match(approvals, /AppState\.addEventListener/u);
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
  assert.match(
    primitives,
    /contentContainerStyle=\{\[[\s\S]*styles\.screenContent,[\s\S]*contentStyle,[\s\S]*styles\.screenBounds/u,
  );
  assert.match(
    primitives,
    /screenBounds: \{[\s\S]*alignSelf: 'center',[\s\S]*maxWidth: 720,[\s\S]*width: '100%'/u,
  );
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
