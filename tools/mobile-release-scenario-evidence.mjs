const SHA256 = /^[0-9a-f]{64}$/u;
const REVISION = /^[0-9a-f]{40}$/u;
const VERSION = /^[0-9A-Za-z][0-9A-Za-z ._()+-]{0,95}$/u;
const UTC_INSTANT =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const PLACEHOLDER =
  /(?:^|[\s._/-])(?:example|fixture|placeholder|replace|sample|tbd|todo|unknown)(?:$|[\s._/-])/iu;
const EVIDENCE_SET_ID = /^[a-z0-9](?:[a-z0-9-]{6,62}[a-z0-9])$/u;
const LOCALE = /^[a-z]{2}(?:-[A-Z][A-Za-z0-9]{1,7})?$/u;
const MAXIMUM_OBSERVATION_AGE_MS = 30 * 24 * 60 * 60 * 1_000;
const MAXIMUM_FUTURE_SKEW_MS = 5 * 60 * 1_000;
const MAXIMUM_RAW_EVIDENCE_BYTES = 512 * 1024 * 1024;

export const MOBILE_SCENARIO_SCHEMA_VERSION = 2;

export const REQUIRED_MOBILE_RELEASE_SCENARIOS = Object.freeze({
  'android-physical': Object.freeze([
    'android-single-sim-send',
    'android-dual-sim-explicit-send',
    'android-background-recovery',
    'android-no-duplicate-race',
  ]),
  'android-carrier': Object.freeze([
    'android-gsm7-multipart',
    'android-unicode-multipart',
    'android-carrier-rejection',
    'android-no-delivery-guarantee',
  ]),
  'android-accessibility': Object.freeze([
    'android-talkback-core-flow',
    'android-text-200-percent',
    'android-reduced-motion',
    'android-pseudo-rtl',
  ]),
  'ios-physical': Object.freeze([
    'ios-reminder-and-foreground-composer',
    'ios-composer-cancel-failed-sent-unknown',
    'ios-no-background-sms',
    'ios-android-coexistence-suppression',
    'ios-single-dual-esim-composer',
    'ios-no-sim-can-send-text-unavailable',
    'ios-no-carrier-delivery-claim',
  ]),
  'ios-accessibility': Object.freeze([
    'ios-voiceover-core-flow',
    'ios-dynamic-type-accessibility',
    'ios-reduced-motion-increased-contrast',
    'ios-pseudo-rtl',
  ]),
});

const DOCUMENT_KEYS = new Set([
  '$schema',
  'schemaVersion',
  'evidenceKind',
  'evidenceSetId',
  'rows',
]);
const ROW_KEYS = new Set([
  'scenarioId',
  'sourceRevision',
  'artifactSha256',
  'signingCertificateSha256',
  'artifactVersion',
  'platform',
  'deviceModel',
  'osVersion',
  'observedAt',
  'result',
  'rawEvidenceReference',
  'rawEvidenceSha256',
  'rawEvidenceBytes',
  'observed',
]);
const COMMON_OBSERVED_KEYS = [
  'physicalDevice',
  'deviceIdSha256',
  'installationSource',
  'collector',
  'collectorVersion',
  'protocolVersion',
];
const OBSERVED_KEYS = Object.freeze({
  'android-physical': new Set([
    ...COMMON_OBSERVED_KEYS,
    'apiLevel',
    'oem',
    'simTopology',
    'activeSubscriptionCount',
    'selectedSubscriptionIdSha256',
    'backgroundState',
    'installerAllowlistObserved',
    'duplicateSubmissionsObserved',
  ]),
  'android-carrier': new Set([
    ...COMMON_OBSERVED_KEYS,
    'apiLevel',
    'oem',
    'simTopology',
    'activeSubscriptionCount',
    'selectedSubscriptionIdSha256',
    'countryCode',
    'carrierMccMnc',
    'radioAccessTechnology',
    'messageEncoding',
    'submittedPartCount',
    'sentResult',
    'deliveryEvidence',
    'carrierDeliveryClaimed',
  ]),
  'android-accessibility': new Set([
    ...COMMON_OBSERVED_KEYS,
    'apiLevel',
    'oem',
    'assistiveTechnology',
    'assistiveTechnologyVersion',
    'textScalePercent',
    'reducedMotion',
    'increasedContrast',
    'layoutDirection',
    'locale',
    'humanReviewerIdSha256',
    'checkpointsPassed',
    'checkpointsTotal',
  ]),
  'ios-physical': new Set([
    ...COMMON_OBSERVED_KEYS,
    'deviceClass',
    'simTopologiesCovered',
    'canSendTextStatesCovered',
    'notificationStatesCovered',
    'applicationStatesCovered',
    'composerOutcomesCovered',
    'androidCoexistenceStatesCovered',
    'backgroundSmsAttempted',
    'carrierDeliveryClaimed',
    'reservationHoldHours',
  ]),
  'ios-accessibility': new Set([
    ...COMMON_OBSERVED_KEYS,
    'deviceClass',
    'assistiveTechnology',
    'assistiveTechnologyVersion',
    'contentSizeCategory',
    'reducedMotion',
    'increasedContrast',
    'layoutDirection',
    'locale',
    'humanReviewerIdSha256',
    'checkpointsPassed',
    'checkpointsTotal',
  ]),
});

const isObject = value =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const exactKeys = (value, expected, label, errors) => {
  if (!isObject(value)) {
    errors.push(`${label} must be an object`);
    return false;
  }
  for (const key of Object.keys(value)) {
    if (!expected.has(key))
      errors.push(`${label} has unsupported field ${key}`);
  }
  for (const key of expected) {
    if (!Object.hasOwn(value, key)) errors.push(`${label} is missing ${key}`);
  }
  return true;
};

const parseInstant = value => {
  if (typeof value !== 'string' || !UTC_INSTANT.test(value)) return null;
  const parsed = Date.parse(value);
  if (!Number.isFinite(parsed)) return null;
  const instant = new Date(parsed);
  return instant.getUTCFullYear() === Number(value.slice(0, 4)) &&
    instant.getUTCMonth() + 1 === Number(value.slice(5, 7)) &&
    instant.getUTCDate() === Number(value.slice(8, 10)) &&
    instant.getUTCHours() === Number(value.slice(11, 13)) &&
    instant.getUTCMinutes() === Number(value.slice(14, 16)) &&
    instant.getUTCSeconds() === Number(value.slice(17, 19))
    ? parsed
    : null;
};

const requireDigest = (value, label, errors) => {
  if (!SHA256.test(value ?? '') || /^0{64}$/u.test(value)) {
    errors.push(`${label} must be a nonzero lowercase SHA-256 digest`);
  }
};

const requireObservedText = (value, label, errors, minimum = 2) => {
  if (
    typeof value !== 'string' ||
    value !== value.trim() ||
    value.length < minimum ||
    value.length > 120 ||
    PLACEHOLDER.test(value)
  ) {
    errors.push(`${label} must identify a real observed value`);
  }
};

const requireEnum = (value, allowed, label, errors) => {
  if (!allowed.includes(value)) {
    errors.push(`${label} must be one of ${allowed.join(', ')}`);
  }
};

const requireInteger = (value, minimum, maximum, label, errors) => {
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    errors.push(`${label} must be an integer from ${minimum} to ${maximum}`);
  }
};

const requireExactCoverage = (
  value,
  required,
  label,
  errors,
  allowed = required,
) => {
  if (
    !Array.isArray(value) ||
    value.length === 0 ||
    value.length !== new Set(value).size ||
    value.some(item => !allowed.includes(item))
  ) {
    errors.push(`${label} must be a unique supported coverage list`);
    return;
  }
  for (const requiredItem of required) {
    if (!value.includes(requiredItem)) {
      errors.push(`${label} is missing ${String(requiredItem)}`);
    }
  }
};

export const expectedMobileScenarioRawReference = (
  evidenceSetId,
  evidenceKind,
  scenarioId,
) =>
  `mobile-scenario-raw--${String(evidenceSetId)}--${String(
    evidenceKind,
  )}--${String(scenarioId)}.json`;

const validateCommonObserved = (observed, evidenceKind, label, errors) => {
  if (observed.physicalDevice !== true) {
    errors.push(`${label}.physicalDevice must be true`);
  }
  requireDigest(observed.deviceIdSha256, `${label}.deviceIdSha256`, errors);
  const installationSources = evidenceKind.startsWith('android-')
    ? ['google-play', 'managed-enterprise', 'controlled-direct']
    : ['testflight', 'app-store', 'ad-hoc'];
  requireEnum(
    observed.installationSource,
    installationSources,
    `${label}.installationSource`,
    errors,
  );
  requireObservedText(observed.collector, `${label}.collector`, errors, 3);
  requireObservedText(
    observed.collectorVersion,
    `${label}.collectorVersion`,
    errors,
    1,
  );
  if (observed.protocolVersion !== `${evidenceKind}-v1`) {
    errors.push(`${label}.protocolVersion must be ${evidenceKind}-v1`);
  }
};

const validateAndroidDeviceCoordinates = (observed, label, errors) => {
  requireInteger(observed.apiLevel, 29, 37, `${label}.apiLevel`, errors);
  requireObservedText(observed.oem, `${label}.oem`, errors);
  requireEnum(
    observed.simTopology,
    ['single-sim', 'dual-sim', 'esim', 'dual-sim-esim'],
    `${label}.simTopology`,
    errors,
  );
  requireInteger(
    observed.activeSubscriptionCount,
    1,
    4,
    `${label}.activeSubscriptionCount`,
    errors,
  );
  requireDigest(
    observed.selectedSubscriptionIdSha256,
    `${label}.selectedSubscriptionIdSha256`,
    errors,
  );
};

const validateAccessibilityCoordinates = (observed, label, errors) => {
  requireObservedText(
    observed.assistiveTechnologyVersion,
    `${label}.assistiveTechnologyVersion`,
    errors,
    1,
  );
  requireEnum(
    observed.layoutDirection,
    ['ltr', 'rtl'],
    `${label}.layoutDirection`,
    errors,
  );
  if (typeof observed.locale !== 'string' || !LOCALE.test(observed.locale)) {
    errors.push(`${label}.locale is invalid`);
  }
  requireDigest(
    observed.humanReviewerIdSha256,
    `${label}.humanReviewerIdSha256`,
    errors,
  );
  requireInteger(
    observed.checkpointsTotal,
    1,
    10_000,
    `${label}.checkpointsTotal`,
    errors,
  );
  if (observed.checkpointsPassed !== observed.checkpointsTotal) {
    errors.push(`${label}.checkpointsPassed must equal checkpointsTotal`);
  }
};

const validateObservedCoordinates = (
  observed,
  evidenceKind,
  scenarioId,
  label,
  errors,
) => {
  const expectedKeys = OBSERVED_KEYS[evidenceKind];
  if (expectedKeys === undefined) return;
  if (!exactKeys(observed, expectedKeys, label, errors)) return;
  validateCommonObserved(observed, evidenceKind, label, errors);

  if (evidenceKind === 'android-physical') {
    validateAndroidDeviceCoordinates(observed, label, errors);
    requireEnum(
      observed.backgroundState,
      [
        'foreground',
        'background',
        'force-stopped-recovered',
        'reboot-recovered',
      ],
      `${label}.backgroundState`,
      errors,
    );
    if (observed.installerAllowlistObserved !== true) {
      errors.push(`${label}.installerAllowlistObserved must be true`);
    }
    if (observed.duplicateSubmissionsObserved !== 0) {
      errors.push(`${label}.duplicateSubmissionsObserved must be zero`);
    }
    if (
      scenarioId === 'android-single-sim-send' &&
      (observed.simTopology !== 'single-sim' ||
        observed.activeSubscriptionCount !== 1)
    ) {
      errors.push(`${label} must bind one active single-SIM subscription`);
    }
    if (
      scenarioId === 'android-dual-sim-explicit-send' &&
      (!['dual-sim', 'dual-sim-esim'].includes(observed.simTopology) ||
        observed.activeSubscriptionCount < 2)
    ) {
      errors.push(`${label} must bind an explicit dual-SIM subscription`);
    }
    if (
      scenarioId === 'android-background-recovery' &&
      observed.backgroundState === 'foreground'
    ) {
      errors.push(`${label} must bind a non-foreground recovery state`);
    }
  } else if (evidenceKind === 'android-carrier') {
    validateAndroidDeviceCoordinates(observed, label, errors);
    if (!/^[A-Z]{2}$/u.test(observed.countryCode ?? '')) {
      errors.push(`${label}.countryCode must be ISO 3166-1 alpha-2`);
    }
    if (!/^\d{5,6}$/u.test(observed.carrierMccMnc ?? '')) {
      errors.push(
        `${label}.carrierMccMnc must be a five- or six-digit MCC/MNC`,
      );
    }
    requireEnum(
      observed.radioAccessTechnology,
      ['2g', '3g', '4g', '5g', 'wifi-calling'],
      `${label}.radioAccessTechnology`,
      errors,
    );
    requireEnum(
      observed.messageEncoding,
      ['gsm-7', 'ucs-2'],
      `${label}.messageEncoding`,
      errors,
    );
    requireInteger(
      observed.submittedPartCount,
      1,
      100,
      `${label}.submittedPartCount`,
      errors,
    );
    requireEnum(
      observed.sentResult,
      ['accepted', 'rejected', 'unknown'],
      `${label}.sentResult`,
      errors,
    );
    requireEnum(
      observed.deliveryEvidence,
      ['reported', 'not-reported', 'not-observable'],
      `${label}.deliveryEvidence`,
      errors,
    );
    if (observed.carrierDeliveryClaimed !== false) {
      errors.push(`${label}.carrierDeliveryClaimed must be false`);
    }
    if (
      scenarioId === 'android-gsm7-multipart' &&
      (observed.messageEncoding !== 'gsm-7' || observed.submittedPartCount < 2)
    ) {
      errors.push(`${label} must bind a multipart GSM-7 submission`);
    }
    if (
      scenarioId === 'android-unicode-multipart' &&
      (observed.messageEncoding !== 'ucs-2' || observed.submittedPartCount < 2)
    ) {
      errors.push(`${label} must bind a multipart Unicode submission`);
    }
    if (
      scenarioId === 'android-carrier-rejection' &&
      observed.sentResult !== 'rejected'
    ) {
      errors.push(`${label} must bind an observed carrier rejection`);
    }
  } else if (evidenceKind === 'android-accessibility') {
    requireInteger(observed.apiLevel, 29, 37, `${label}.apiLevel`, errors);
    requireObservedText(observed.oem, `${label}.oem`, errors);
    requireEnum(
      observed.assistiveTechnology,
      ['talkback', 'system-settings'],
      `${label}.assistiveTechnology`,
      errors,
    );
    requireInteger(
      observed.textScalePercent,
      100,
      300,
      `${label}.textScalePercent`,
      errors,
    );
    if (typeof observed.reducedMotion !== 'boolean') {
      errors.push(`${label}.reducedMotion must be boolean`);
    }
    if (typeof observed.increasedContrast !== 'boolean') {
      errors.push(`${label}.increasedContrast must be boolean`);
    }
    validateAccessibilityCoordinates(observed, label, errors);
    if (
      scenarioId === 'android-talkback-core-flow' &&
      observed.assistiveTechnology !== 'talkback'
    ) {
      errors.push(`${label} must bind TalkBack`);
    }
    if (
      scenarioId === 'android-text-200-percent' &&
      observed.textScalePercent < 200
    ) {
      errors.push(`${label} must bind at least 200 percent text`);
    }
    if (
      scenarioId === 'android-reduced-motion' &&
      observed.reducedMotion !== true
    ) {
      errors.push(`${label} must bind reduced motion`);
    }
    if (
      scenarioId === 'android-pseudo-rtl' &&
      (observed.layoutDirection !== 'rtl' || observed.locale !== 'ar-XB')
    ) {
      errors.push(`${label} must bind the ar-XB pseudo-RTL locale`);
    }
  } else if (evidenceKind === 'ios-physical') {
    if (observed.deviceClass !== 'iphone') {
      errors.push(`${label}.deviceClass must be iphone`);
    }
    requireExactCoverage(
      observed.simTopologiesCovered,
      [],
      `${label}.simTopologiesCovered`,
      errors,
      ['no-sim', 'single-sim', 'dual-sim', 'esim'],
    );
    requireExactCoverage(
      observed.canSendTextStatesCovered,
      [],
      `${label}.canSendTextStatesCovered`,
      errors,
      [true, false],
    );
    requireExactCoverage(
      observed.notificationStatesCovered,
      ['authorized'],
      `${label}.notificationStatesCovered`,
      errors,
      ['authorized', 'denied', 'provisional', 'not-determined'],
    );
    requireExactCoverage(
      observed.applicationStatesCovered,
      ['foreground'],
      `${label}.applicationStatesCovered`,
      errors,
      ['foreground', 'background', 'terminated'],
    );
    requireExactCoverage(
      observed.composerOutcomesCovered,
      [],
      `${label}.composerOutcomesCovered`,
      errors,
      ['cancelled', 'failed', 'reported-sent', 'unknown'],
    );
    requireExactCoverage(
      observed.androidCoexistenceStatesCovered,
      [],
      `${label}.androidCoexistenceStatesCovered`,
      errors,
      ['no-live-android', 'live-android-suppressed'],
    );
    if (observed.backgroundSmsAttempted !== false) {
      errors.push(`${label}.backgroundSmsAttempted must be false`);
    }
    if (observed.carrierDeliveryClaimed !== false) {
      errors.push(`${label}.carrierDeliveryClaimed must be false`);
    }
    if (observed.reservationHoldHours !== 72) {
      errors.push(`${label}.reservationHoldHours must be 72`);
    }
    if (scenarioId === 'ios-composer-cancel-failed-sent-unknown') {
      requireExactCoverage(
        observed.composerOutcomesCovered,
        ['cancelled', 'failed', 'reported-sent', 'unknown'],
        `${label}.composerOutcomesCovered`,
        errors,
      );
    }
    if (scenarioId === 'ios-android-coexistence-suppression') {
      requireExactCoverage(
        observed.androidCoexistenceStatesCovered,
        ['live-android-suppressed'],
        `${label}.androidCoexistenceStatesCovered`,
        errors,
      );
    }
    if (scenarioId === 'ios-single-dual-esim-composer') {
      requireExactCoverage(
        observed.simTopologiesCovered,
        ['single-sim', 'dual-sim', 'esim'],
        `${label}.simTopologiesCovered`,
        errors,
      );
    }
    if (scenarioId === 'ios-no-sim-can-send-text-unavailable') {
      requireExactCoverage(
        observed.simTopologiesCovered,
        ['no-sim'],
        `${label}.simTopologiesCovered`,
        errors,
      );
      requireExactCoverage(
        observed.canSendTextStatesCovered,
        [false],
        `${label}.canSendTextStatesCovered`,
        errors,
      );
    }
  } else if (evidenceKind === 'ios-accessibility') {
    if (observed.deviceClass !== 'iphone') {
      errors.push(`${label}.deviceClass must be iphone`);
    }
    requireEnum(
      observed.assistiveTechnology,
      ['voiceover', 'system-settings'],
      `${label}.assistiveTechnology`,
      errors,
    );
    requireObservedText(
      observed.contentSizeCategory,
      `${label}.contentSizeCategory`,
      errors,
    );
    if (typeof observed.reducedMotion !== 'boolean') {
      errors.push(`${label}.reducedMotion must be boolean`);
    }
    if (typeof observed.increasedContrast !== 'boolean') {
      errors.push(`${label}.increasedContrast must be boolean`);
    }
    validateAccessibilityCoordinates(observed, label, errors);
    if (
      scenarioId === 'ios-voiceover-core-flow' &&
      observed.assistiveTechnology !== 'voiceover'
    ) {
      errors.push(`${label} must bind VoiceOver`);
    }
    if (
      scenarioId === 'ios-dynamic-type-accessibility' &&
      observed.contentSizeCategory !== 'accessibility-extra-extra-extra-large'
    ) {
      errors.push(
        `${label} must bind the maximum accessibility Dynamic Type category`,
      );
    }
    if (
      scenarioId === 'ios-reduced-motion-increased-contrast' &&
      (observed.reducedMotion !== true || observed.increasedContrast !== true)
    ) {
      errors.push(`${label} must bind reduced motion and increased contrast`);
    }
    if (
      scenarioId === 'ios-pseudo-rtl' &&
      (observed.layoutDirection !== 'rtl' || observed.locale !== 'ar-XB')
    ) {
      errors.push(`${label} must bind the ar-XB pseudo-RTL locale`);
    }
  }
};

export function validateMobileReleaseScenarioEvidence(
  document,
  {
    expectedKind,
    expectedPlatform,
    expectedSourceRevision,
    expectedArtifactSha256,
    expectedSigningCertificateSha256,
    expectedArtifactVersion,
    evidenceFiles,
    nowMillis = Date.now(),
  },
) {
  const errors = [];
  const rawEvidenceReferences = [];
  if (!exactKeys(document, DOCUMENT_KEYS, 'scenario evidence', errors)) {
    return { errors, rawEvidenceReferences };
  }
  if (document.$schema !== './mobile-release-scenario-evidence.schema.json') {
    errors.push(
      '$schema must reference ./mobile-release-scenario-evidence.schema.json',
    );
  }
  if (document.schemaVersion !== MOBILE_SCENARIO_SCHEMA_VERSION) {
    errors.push(`schemaVersion must be ${MOBILE_SCENARIO_SCHEMA_VERSION}`);
  }
  if (document.evidenceKind !== expectedKind) {
    errors.push(`evidenceKind must be ${String(expectedKind)}`);
  }
  if (
    typeof document.evidenceSetId !== 'string' ||
    !EVIDENCE_SET_ID.test(document.evidenceSetId) ||
    PLACEHOLDER.test(document.evidenceSetId)
  ) {
    errors.push('evidenceSetId must be a non-placeholder stable evidence ID');
  }
  const requiredScenarios = REQUIRED_MOBILE_RELEASE_SCENARIOS[expectedKind];
  if (requiredScenarios === undefined) {
    return {
      errors: [...errors, 'expected evidence kind is unsupported'],
      rawEvidenceReferences,
    };
  }
  if (!REVISION.test(expectedSourceRevision ?? '')) {
    errors.push('expected source revision is invalid');
  }
  requireDigest(
    expectedSigningCertificateSha256,
    'expected signing certificate',
    errors,
  );
  if (
    typeof expectedArtifactVersion !== 'string' ||
    !VERSION.test(expectedArtifactVersion)
  ) {
    errors.push('expected artifact version is invalid');
  }
  if (
    expectedArtifactSha256 !== undefined &&
    (!SHA256.test(expectedArtifactSha256) ||
      /^0{64}$/u.test(expectedArtifactSha256))
  ) {
    errors.push('expected artifact digest is invalid');
  }
  if (!Array.isArray(document.rows)) {
    return {
      errors: [...errors, 'rows must be an array'],
      rawEvidenceReferences,
    };
  }

  const scenarioIds = document.rows.map(row => row?.scenarioId);
  if (new Set(scenarioIds).size !== scenarioIds.length) {
    errors.push('scenario IDs must be unique');
  }
  const requiredSet = new Set(requiredScenarios);
  for (const scenarioId of requiredScenarios) {
    if (!scenarioIds.includes(scenarioId)) {
      errors.push(`required scenario ${scenarioId} is missing`);
    }
  }
  for (const scenarioId of scenarioIds) {
    if (!requiredSet.has(scenarioId)) {
      errors.push(`scenario ${String(scenarioId)} is not allowed`);
    }
  }

  const firstRow = document.rows.find(isObject);
  const boundArtifactSha256 =
    expectedArtifactSha256 ?? firstRow?.artifactSha256;
  const expectedRawReferences = new Set();
  for (const [index, row] of document.rows.entries()) {
    const label = `rows[${index}]`;
    if (!exactKeys(row, ROW_KEYS, label, errors)) continue;
    if (!REVISION.test(row.sourceRevision ?? '')) {
      errors.push(`${label}.sourceRevision is invalid`);
    } else if (row.sourceRevision !== expectedSourceRevision) {
      errors.push(`${label}.sourceRevision crosses the release source`);
    }
    requireDigest(row.artifactSha256, `${label}.artifactSha256`, errors);
    if (row.artifactSha256 !== boundArtifactSha256) {
      errors.push(`${label}.artifactSha256 crosses the release artifact`);
    }
    requireDigest(
      row.signingCertificateSha256,
      `${label}.signingCertificateSha256`,
      errors,
    );
    if (row.signingCertificateSha256 !== expectedSigningCertificateSha256) {
      errors.push(
        `${label}.signingCertificateSha256 crosses the release certificate`,
      );
    }
    if (
      typeof row.artifactVersion !== 'string' ||
      !VERSION.test(row.artifactVersion)
    ) {
      errors.push(`${label}.artifactVersion is invalid`);
    } else if (row.artifactVersion !== expectedArtifactVersion) {
      errors.push(`${label}.artifactVersion crosses the release version`);
    }
    if (row.platform !== expectedPlatform) {
      errors.push(`${label}.platform must be ${String(expectedPlatform)}`);
    }
    requireObservedText(row.deviceModel, `${label}.deviceModel`, errors);
    requireObservedText(row.osVersion, `${label}.osVersion`, errors);
    const observedAt = parseInstant(row.observedAt);
    if (observedAt === null) {
      errors.push(`${label}.observedAt must be an RFC 3339 UTC instant`);
    } else if (
      observedAt > nowMillis + MAXIMUM_FUTURE_SKEW_MS ||
      nowMillis - observedAt > MAXIMUM_OBSERVATION_AGE_MS
    ) {
      errors.push(`${label}.observedAt is future-dated or older than 30 days`);
    }
    if (row.result !== 'passed') {
      errors.push(`${label}.result must be passed`);
    }
    const expectedRawReference = expectedMobileScenarioRawReference(
      document.evidenceSetId,
      expectedKind,
      row.scenarioId,
    );
    if (row.rawEvidenceReference !== expectedRawReference) {
      errors.push(
        `${label}.rawEvidenceReference must be ${expectedRawReference}`,
      );
    } else if (expectedRawReferences.has(row.rawEvidenceReference)) {
      errors.push(`${label}.rawEvidenceReference is duplicated`);
    } else {
      expectedRawReferences.add(row.rawEvidenceReference);
      rawEvidenceReferences.push(row.rawEvidenceReference);
    }
    requireDigest(row.rawEvidenceSha256, `${label}.rawEvidenceSha256`, errors);
    if (
      !Number.isSafeInteger(row.rawEvidenceBytes) ||
      row.rawEvidenceBytes < 1 ||
      row.rawEvidenceBytes > MAXIMUM_RAW_EVIDENCE_BYTES
    ) {
      errors.push(
        `${label}.rawEvidenceBytes must be 1 to ${MAXIMUM_RAW_EVIDENCE_BYTES}`,
      );
    }
    const rawEvidence =
      evidenceFiles instanceof Map
        ? evidenceFiles.get(row.rawEvidenceReference)
        : undefined;
    if (rawEvidence === undefined) {
      errors.push(
        `${label}.rawEvidenceReference is missing from the explicit evidence root`,
      );
    } else if (
      rawEvidence.sha256 !== row.rawEvidenceSha256 ||
      rawEvidence.bytes !== row.rawEvidenceBytes
    ) {
      errors.push(
        `${label}.rawEvidenceReference bytes do not match its digest and size`,
      );
    }
    validateObservedCoordinates(
      row.observed,
      expectedKind,
      row.scenarioId,
      `${label}.observed`,
      errors,
    );
  }

  if (
    evidenceFiles instanceof Map &&
    EVIDENCE_SET_ID.test(document.evidenceSetId ?? '')
  ) {
    const namespace = `mobile-scenario-raw--${document.evidenceSetId}--${expectedKind}--`;
    for (const reference of evidenceFiles.keys()) {
      if (
        typeof reference === 'string' &&
        reference.startsWith(namespace) &&
        !expectedRawReferences.has(reference)
      ) {
        errors.push(
          `explicit evidence root contains an unreferenced scenario raw file: ${reference}`,
        );
      }
    }
  }
  return { errors, rawEvidenceReferences };
}
