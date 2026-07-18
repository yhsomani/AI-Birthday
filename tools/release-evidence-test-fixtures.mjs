import { createHash } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import {
  expectedMobileScenarioRawReference,
  MOBILE_SCENARIO_SCHEMA_VERSION,
  REQUIRED_MOBILE_RELEASE_SCENARIOS,
} from './mobile-release-scenario-evidence.mjs';

const DEVICE_ID = '12'.repeat(32);
const SUBSCRIPTION_ID = '34'.repeat(32);
const REVIEWER_ID = '56'.repeat(32);

export const sha256 = value => createHash('sha256').update(value).digest('hex');

const commonObserved = (evidenceKind, platform, installationSource) => ({
  physicalDevice: true,
  deviceIdSha256: DEVICE_ID,
  installationSource:
    installationSource ??
    (platform === 'android' ? 'google-play' : 'testflight'),
  collector: `${platform}-release-lab`,
  collectorVersion: '1.0.0',
  protocolVersion: `${evidenceKind}-v1`,
});

const observedCoordinates = (
  evidenceKind,
  scenarioId,
  platform,
  installationSource,
) => {
  if (evidenceKind === 'android-physical') {
    return {
      ...commonObserved(evidenceKind, platform, installationSource),
      apiLevel: 36,
      oem: 'Google',
      simTopology:
        scenarioId === 'android-dual-sim-explicit-send'
          ? 'dual-sim'
          : 'single-sim',
      activeSubscriptionCount:
        scenarioId === 'android-dual-sim-explicit-send' ? 2 : 1,
      selectedSubscriptionIdSha256: SUBSCRIPTION_ID,
      backgroundState:
        scenarioId === 'android-background-recovery'
          ? 'reboot-recovered'
          : 'foreground',
      installerAllowlistObserved: true,
      duplicateSubmissionsObserved: 0,
    };
  }
  if (evidenceKind === 'android-carrier') {
    return {
      ...commonObserved(evidenceKind, platform, installationSource),
      apiLevel: 36,
      oem: 'Google',
      simTopology: 'single-sim',
      activeSubscriptionCount: 1,
      selectedSubscriptionIdSha256: SUBSCRIPTION_ID,
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
  if (evidenceKind === 'android-accessibility') {
    return {
      ...commonObserved(evidenceKind, platform, installationSource),
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
      humanReviewerIdSha256: REVIEWER_ID,
      checkpointsPassed: 24,
      checkpointsTotal: 24,
    };
  }
  if (evidenceKind === 'ios-physical') {
    const value = {
      ...commonObserved(evidenceKind, platform, installationSource),
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
    ...commonObserved(evidenceKind, platform, installationSource),
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
    humanReviewerIdSha256: REVIEWER_ID,
    checkpointsPassed: 24,
    checkpointsTotal: 24,
  };
};

export const createMobileScenarioFixture = (
  evidenceKind,
  {
    sourceRevision,
    artifactSha256,
    signingCertificateSha256,
    artifactVersion = '1.0',
    evidenceSetId = 'release-2026-07-12',
    observedAt = '2026-07-12T00:00:00Z',
    installationSource,
  },
) => {
  const platform = evidenceKind.startsWith('android-') ? 'android' : 'ios';
  const fileContents = new Map();
  const evidenceFiles = new Map();
  const rows = REQUIRED_MOBILE_RELEASE_SCENARIOS[evidenceKind].map(
    scenarioId => {
      const rawEvidenceReference = expectedMobileScenarioRawReference(
        evidenceSetId,
        evidenceKind,
        scenarioId,
      );
      const raw = Buffer.from(
        JSON.stringify({
          schemaVersion: 1,
          evidenceSetId,
          evidenceKind,
          scenarioId,
          retained: true,
        }),
      );
      const rawEvidenceSha256 = sha256(raw);
      fileContents.set(rawEvidenceReference, raw);
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
        observedAt,
        result: 'passed',
        rawEvidenceReference,
        rawEvidenceSha256,
        rawEvidenceBytes: raw.byteLength,
        observed: observedCoordinates(
          evidenceKind,
          scenarioId,
          platform,
          installationSource,
        ),
      };
    },
  );
  return {
    document: {
      $schema: './mobile-release-scenario-evidence.schema.json',
      schemaVersion: MOBILE_SCENARIO_SCHEMA_VERSION,
      evidenceKind,
      evidenceSetId,
      rows,
    },
    evidenceFiles,
    fileContents,
  };
};

const samples = (count, value) => Array.from({ length: count }, () => value);

export const createPerformanceEvidenceFixture = (
  platform,
  {
    sourceRevision,
    artifactSha256,
    applicationId = 'com.yashsomani.birthdayautopilot',
    version = '1.0',
    evidenceSetId = `${platform}-release-2026-07-12`,
    measuredAt = '2026-07-12T00:00:00Z',
    installationSource,
  },
) => {
  const protocolReference = `performance-protocol--${evidenceSetId}.txt`;
  const rawResultsReference = `performance-raw--${evidenceSetId}.jsonl`;
  const protocol = Buffer.from(
    `Birthday Autopilot ${platform} physical-device performance protocol v1\n`,
  );
  const raw = Buffer.from(
    `${JSON.stringify({ evidenceSetId, platform, retained: true })}\n`,
  );
  const fileContents = new Map([
    [protocolReference, protocol],
    [rawResultsReference, raw],
  ]);
  const evidenceFiles = new Map(
    [...fileContents].map(([reference, bytes]) => [
      reference,
      { bytes: bytes.byteLength, sha256: sha256(bytes) },
    ]),
  );
  return {
    document: {
      schemaVersion: 2,
      platform,
      sourceRevision,
      measuredAt,
      artifact: {
        applicationId,
        version,
        sha256: artifactSha256,
        signedReleaseLike: true,
      },
      device: {
        model: platform === 'android' ? 'Pixel 10' : 'iPhone 17',
        osVersion: platform === 'android' ? 'Android 16' : 'iOS 26.5',
        ramMiB: platform === 'android' ? 6_144 : 4_096,
        physicalDevice: true,
        deviceIdSha256: DEVICE_ID,
        installationSource:
          installationSource ??
          (platform === 'android' ? 'google-play' : 'testflight'),
        measurementTool:
          platform === 'android' ? 'Perfetto' : 'XCTest MetricKit',
        measurementToolVersion: '1.0.0',
      },
      references: {
        protocolReference,
        protocolSha256: sha256(protocol),
        protocolBytes: protocol.byteLength,
        rawResultsReference,
        rawResultsSha256: sha256(raw),
        rawResultsBytes: raw.byteLength,
      },
      shared: {
        coldStartHomeMs: samples(30, 2_000),
        warmHomeMs: samples(30, 800),
        search10000Ms: samples(30, 120),
        normalizeCommit10000WallMs: samples(10, 4_200),
        normalizeCommit10000PeakRssMiB: samples(10, 220),
        crashAnrOomCount: 0,
      },
      platformMetrics:
        platform === 'android'
          ? {
              noDueReconcileCpuMs: samples(10, 1_500),
              claimArmLatencyMs: samples(100, 2_000),
              batteryDeltaPercentagePoints: samples(10, 0.08),
              batteryBenchmarkHours: 24,
            }
          : {
              reminderNoChangeCpuMs: samples(10, 1_500),
              reminderReplaceWallMs: samples(10, 1_600),
              composerReadyLatencyMs: samples(30, 800),
            },
    },
    evidenceFiles,
    fileContents,
  };
};

export const mergeEvidenceMaps = (...maps) =>
  new Map(maps.flatMap(map => [...map]));

export const writeEvidenceFiles = (root, fileContents) => {
  for (const [reference, bytes] of fileContents) {
    const file = join(root, reference);
    mkdirSync(dirname(file), { recursive: true });
    writeFileSync(file, bytes);
  }
};
