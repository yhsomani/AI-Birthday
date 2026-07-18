import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import test from 'node:test';

import {
  expectedMobileScenarioRawReference,
  MOBILE_SCENARIO_SCHEMA_VERSION,
  REQUIRED_MOBILE_RELEASE_SCENARIOS,
  validateMobileReleaseScenarioEvidence,
} from './mobile-release-scenario-evidence.mjs';

const SOURCE = 'ab'.repeat(20);
const ARTIFACT = 'cd'.repeat(32);
const CERTIFICATE = 'ef'.repeat(32);
const DEVICE = '12'.repeat(32);
const SUBSCRIPTION = '34'.repeat(32);
const REVIEWER = '56'.repeat(32);
const NOW = Date.parse('2026-07-12T12:00:00Z');
const EVIDENCE_SET_ID = 'release-2026-07-12';
const digest = value => createHash('sha256').update(value).digest('hex');

const commonObserved = (kind, platform) => ({
  physicalDevice: true,
  deviceIdSha256: DEVICE,
  installationSource: platform === 'android' ? 'google-play' : 'testflight',
  collector: `${platform}-release-lab`,
  collectorVersion: '1.0.0',
  protocolVersion: `${kind}-v1`,
});

const observed = (kind, scenarioId) => {
  if (kind === 'android-physical') {
    return {
      ...commonObserved(kind, 'android'),
      apiLevel: 36,
      oem: 'Google',
      simTopology:
        scenarioId === 'android-dual-sim-explicit-send'
          ? 'dual-sim'
          : 'single-sim',
      activeSubscriptionCount:
        scenarioId === 'android-dual-sim-explicit-send' ? 2 : 1,
      selectedSubscriptionIdSha256: SUBSCRIPTION,
      backgroundState:
        scenarioId === 'android-background-recovery'
          ? 'reboot-recovered'
          : 'foreground',
      installerAllowlistObserved: true,
      duplicateSubmissionsObserved: 0,
    };
  }
  if (kind === 'android-carrier') {
    return {
      ...commonObserved(kind, 'android'),
      apiLevel: 36,
      oem: 'Google',
      simTopology: 'single-sim',
      activeSubscriptionCount: 1,
      selectedSubscriptionIdSha256: SUBSCRIPTION,
      countryCode: 'IN',
      carrierMccMnc: '40445',
      radioAccessTechnology: '5g',
      messageEncoding:
        scenarioId === 'android-unicode-multipart' ? 'ucs-2' : 'gsm-7',
      submittedPartCount: 2,
      sentResult:
        scenarioId === 'android-carrier-rejection' ? 'rejected' : 'accepted',
      deliveryEvidence: 'not-observable',
      carrierDeliveryClaimed: false,
    };
  }
  if (kind === 'android-accessibility') {
    return {
      ...commonObserved(kind, 'android'),
      apiLevel: 36,
      oem: 'Google',
      assistiveTechnology:
        scenarioId === 'android-talkback-core-flow'
          ? 'talkback'
          : 'system-settings',
      assistiveTechnologyVersion: '15.2',
      textScalePercent: scenarioId === 'android-text-200-percent' ? 200 : 100,
      reducedMotion: scenarioId === 'android-reduced-motion',
      increasedContrast: false,
      layoutDirection: scenarioId === 'android-pseudo-rtl' ? 'rtl' : 'ltr',
      locale: scenarioId === 'android-pseudo-rtl' ? 'ar-XB' : 'en-US',
      humanReviewerIdSha256: REVIEWER,
      checkpointsPassed: 24,
      checkpointsTotal: 24,
    };
  }
  if (kind === 'ios-physical') {
    const value = {
      ...commonObserved(kind, 'ios'),
      deviceClass: 'iphone',
      simTopologiesCovered: ['single-sim'],
      canSendTextStatesCovered: [true],
      notificationStatesCovered: ['authorized'],
      applicationStatesCovered: ['foreground'],
      composerOutcomesCovered: ['cancelled'],
      androidCoexistenceStatesCovered: ['no-live-android'],
      backgroundSmsAttempted: false,
      carrierDeliveryClaimed: false,
      reservationHoldHours: 72,
    };
    if (scenarioId === 'ios-composer-cancel-failed-sent-unknown') {
      value.composerOutcomesCovered = [
        'cancelled',
        'failed',
        'reported-sent',
        'unknown',
      ];
    } else if (scenarioId === 'ios-android-coexistence-suppression') {
      value.androidCoexistenceStatesCovered = ['live-android-suppressed'];
    } else if (scenarioId === 'ios-single-dual-esim-composer') {
      value.simTopologiesCovered = ['single-sim', 'dual-sim', 'esim'];
    } else if (scenarioId === 'ios-no-sim-can-send-text-unavailable') {
      value.simTopologiesCovered = ['no-sim'];
      value.canSendTextStatesCovered = [false];
    }
    return value;
  }
  return {
    ...commonObserved(kind, 'ios'),
    deviceClass: 'iphone',
    assistiveTechnology:
      scenarioId === 'ios-voiceover-core-flow'
        ? 'voiceover'
        : 'system-settings',
    assistiveTechnologyVersion: '26.5',
    contentSizeCategory:
      scenarioId === 'ios-dynamic-type-accessibility'
        ? 'accessibility-extra-extra-extra-large'
        : 'large',
    reducedMotion: scenarioId === 'ios-reduced-motion-increased-contrast',
    increasedContrast: scenarioId === 'ios-reduced-motion-increased-contrast',
    layoutDirection: scenarioId === 'ios-pseudo-rtl' ? 'rtl' : 'ltr',
    locale: scenarioId === 'ios-pseudo-rtl' ? 'ar-XB' : 'en-US',
    humanReviewerIdSha256: REVIEWER,
    checkpointsPassed: 24,
    checkpointsTotal: 24,
  };
};

export const createMobileScenarioFixture = (
  kind = 'android-physical',
  {
    sourceRevision = SOURCE,
    artifactSha256 = ARTIFACT,
    signingCertificateSha256 = CERTIFICATE,
    artifactVersion = '1.0',
    evidenceSetId = EVIDENCE_SET_ID,
  } = {},
) => {
  const evidenceFiles = new Map();
  const platform = kind.startsWith('android-') ? 'android' : 'ios';
  const rows = REQUIRED_MOBILE_RELEASE_SCENARIOS[kind].map(scenarioId => {
    const rawEvidenceReference = expectedMobileScenarioRawReference(
      evidenceSetId,
      kind,
      scenarioId,
    );
    const raw = Buffer.from(
      JSON.stringify({ schemaVersion: 1, scenarioId, retained: true }),
    );
    const rawEvidenceSha256 = digest(raw);
    evidenceFiles.set(rawEvidenceReference, {
      bytes: raw.byteLength,
      sha256: rawEvidenceSha256,
    });
    return {
      scenarioId,
      sourceRevision,
      artifactSha256,
      signingCertificateSha256,
      artifactVersion,
      platform,
      deviceModel: platform === 'android' ? 'Pixel 10' : 'iPhone 17',
      osVersion: platform === 'android' ? 'Android 16' : 'iOS 26.5',
      observedAt: '2026-07-12T00:00:00Z',
      result: 'passed',
      rawEvidenceReference,
      rawEvidenceSha256,
      rawEvidenceBytes: raw.byteLength,
      observed: observed(kind, scenarioId),
    };
  });
  return {
    document: {
      $schema: './mobile-release-scenario-evidence.schema.json',
      schemaVersion: MOBILE_SCENARIO_SCHEMA_VERSION,
      evidenceKind: kind,
      evidenceSetId,
      rows,
    },
    evidenceFiles,
  };
};

const validate = (
  value,
  kind = value.document.evidenceKind,
  evidenceFiles = value.evidenceFiles,
) =>
  validateMobileReleaseScenarioEvidence(value.document, {
    expectedKind: kind,
    expectedPlatform: kind.startsWith('android-') ? 'android' : 'ios',
    expectedSourceRevision: SOURCE,
    expectedArtifactSha256: ARTIFACT,
    expectedSigningCertificateSha256: CERTIFICATE,
    expectedArtifactVersion: '1.0',
    evidenceFiles,
    nowMillis: NOW,
  });

test('accepts every exact required Android and iOS scenario inventory with raw bytes', () => {
  for (const kind of Object.keys(REQUIRED_MOBILE_RELEASE_SCENARIOS)) {
    assert.deepEqual(
      validate(createMobileScenarioFixture(kind), kind).errors,
      [],
    );
  }
});

test('rejects missing and duplicate required scenario rows', () => {
  const value = createMobileScenarioFixture();
  value.document.rows.pop();
  value.document.rows.push(structuredClone(value.document.rows[0]));
  const errors = validate(value).errors.join('\n');
  assert.match(errors, /scenario IDs must be unique/u);
  assert.match(
    errors,
    /required scenario android-no-duplicate-race is missing/u,
  );
});

test('rejects stale and future-dated observations', () => {
  const value = createMobileScenarioFixture();
  value.document.rows[0].observedAt = '2026-06-12T11:59:59.999Z';
  value.document.rows[1].observedAt = '2026-07-12T12:05:00.001Z';
  const errors = validate(value).errors.join('\n');
  assert.match(errors, /rows\[0\]\.observedAt is future-dated or older/u);
  assert.match(errors, /rows\[1\]\.observedAt is future-dated or older/u);
});

test('rejects cross-source, artifact, certificate, and version rows', () => {
  const value = createMobileScenarioFixture();
  value.document.rows[0].sourceRevision = '12'.repeat(20);
  value.document.rows[1].artifactSha256 = '34'.repeat(32);
  value.document.rows[2].signingCertificateSha256 = '56'.repeat(32);
  value.document.rows[3].artifactVersion = '2.0';
  const errors = validate(value).errors.join('\n');
  assert.match(errors, /crosses the release source/u);
  assert.match(errors, /crosses the release artifact/u);
  assert.match(errors, /crosses the release certificate/u);
  assert.match(errors, /crosses the release version/u);
});

test('binds every row to exact raw evidence bytes and rejects namespace extras', () => {
  const value = createMobileScenarioFixture();
  const first = value.document.rows[0];
  value.evidenceFiles.delete(first.rawEvidenceReference);
  assert.match(
    validate(value).errors.join('\n'),
    /missing from the explicit evidence root/u,
  );

  value.evidenceFiles.set(first.rawEvidenceReference, {
    bytes: first.rawEvidenceBytes + 1,
    sha256: first.rawEvidenceSha256,
  });
  assert.match(
    validate(value).errors.join('\n'),
    /bytes do not match its digest and size/u,
  );

  value.evidenceFiles.set(
    `mobile-scenario-raw--${EVIDENCE_SET_ID}--android-physical--android-extra.json`,
    { bytes: 1, sha256: '78'.repeat(32) },
  );
  assert.match(
    validate(value).errors.join('\n'),
    /unreferenced scenario raw file/u,
  );
});

test('rejects incomplete carrier, SIM, accessibility, and iOS truth coordinates', () => {
  const carrier = createMobileScenarioFixture('android-carrier');
  carrier.document.rows[0].observed.carrierMccMnc = 'carrier';
  carrier.document.rows[1].observed.selectedSubscriptionIdSha256 = '0'.repeat(
    64,
  );
  assert.match(validate(carrier).errors.join('\n'), /carrierMccMnc/u);
  assert.match(
    validate(carrier).errors.join('\n'),
    /selectedSubscriptionIdSha256/u,
  );

  const androidA11y = createMobileScenarioFixture('android-accessibility');
  androidA11y.document.rows[0].observed.checkpointsPassed = 23;
  assert.match(
    validate(androidA11y).errors.join('\n'),
    /checkpointsPassed must equal/u,
  );

  const ios = createMobileScenarioFixture('ios-physical');
  ios.document.rows[2].observed.backgroundSmsAttempted = true;
  assert.match(validate(ios).errors.join('\n'), /backgroundSmsAttempted/u);

  const iosA11y = createMobileScenarioFixture('ios-accessibility');
  iosA11y.document.rows[3].observed.locale = 'en-US';
  assert.match(validate(iosA11y).errors.join('\n'), /pseudo-RTL/u);
});

test('schema is v2 and the checked-in template cannot pass', () => {
  const schema = JSON.parse(
    readFileSync('tools/mobile-release-scenario-evidence.schema.json', 'utf8'),
  );
  assert.equal(schema.properties.schemaVersion.const, 2);
  assert.equal(schema.$defs.scenarioRow.additionalProperties, false);
  for (const required of [
    'sourceRevision',
    'artifactSha256',
    'signingCertificateSha256',
    'artifactVersion',
    'platform',
    'deviceModel',
    'osVersion',
    'observedAt',
    'scenarioId',
    'result',
    'rawEvidenceReference',
    'rawEvidenceSha256',
    'rawEvidenceBytes',
    'observed',
  ]) {
    assert.ok(schema.$defs.scenarioRow.required.includes(required));
  }
  const template = JSON.parse(
    readFileSync(
      'tools/mobile-release-scenario-evidence.template.json',
      'utf8',
    ),
  );
  const errors = validateMobileReleaseScenarioEvidence(template, {
    expectedKind: 'android-physical',
    expectedPlatform: 'android',
    expectedSourceRevision: SOURCE,
    expectedArtifactSha256: ARTIFACT,
    expectedSigningCertificateSha256: CERTIFICATE,
    expectedArtifactVersion: '1.0',
    evidenceFiles: new Map(),
    nowMillis: NOW,
  }).errors.join('\n');
  assert.match(errors, /evidenceKind must be android-physical/u);
  assert.match(errors, /evidenceSetId must be a non-placeholder/u);
  assert.match(errors, /required scenario android-single-sim-send is missing/u);
});
