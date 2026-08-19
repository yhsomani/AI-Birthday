import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { deflateSync } from 'node:zlib';
import { symlinksAvailable } from './test-capabilities.mjs';
import {
  calculateApprovalScopeSha256,
  calculateLocalizationSha256,
  parseStitchScreenIds,
  validateStoreSubmissionEvidence,
} from './validate-store-submission-evidence.mjs';

const ROOT = path.resolve(import.meta.dirname, '..');
const TEMPLATE = JSON.parse(
  readFileSync(
    path.join(ROOT, 'tools/store-submission-evidence.template.json'),
    'utf8',
  ),
);
const STITCH_IDS = parseStitchScreenIds(
  readFileSync(path.join(ROOT, 'stitch/SCREEN_MANIFEST.md'), 'utf8'),
);
const SHA = value => createHash('sha256').update(value).digest('hex');
const SOURCE_REVISION = '0123456789abcdef0123456789abcdef01234567';
const GENERATED_AT = '2026-07-12T00:00:00Z';
const VALID_UNTIL = '2026-07-19T00:00:00Z';
const NOW = Date.parse('2026-07-15T00:00:00Z');

const clone = value => structuredClone(value);

const CRC_TABLE = Object.freeze(
  Array.from({ length: 256 }, (_, value) => {
    let crc = value;
    for (let bit = 0; bit < 8; bit += 1) {
      // eslint-disable-next-line no-bitwise
      crc = (crc & 1) === 1 ? 0xedb88320 ^ (crc >>> 1) : crc >>> 1;
    }
    // eslint-disable-next-line no-bitwise
    return crc >>> 0;
  }),
);

const crc32 = bytes => {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    // eslint-disable-next-line no-bitwise
    crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  }
  // eslint-disable-next-line no-bitwise
  return (crc ^ 0xffffffff) >>> 0;
};

const pngChunk = (type, data) => {
  const typeBytes = Buffer.from(type, 'ascii');
  const chunk = Buffer.allocUnsafe(12 + data.length);
  chunk.writeUInt32BE(data.length, 0);
  typeBytes.copy(chunk, 4);
  data.copy(chunk, 8);
  chunk.writeUInt32BE(crc32(Buffer.concat([typeBytes, data])), 8 + data.length);
  return chunk;
};

const imageCache = new Map();
const png = (width, height, marker) => {
  const key = `${width}x${height}`;
  let common = imageCache.get(key);
  if (common === undefined) {
    const header = Buffer.alloc(13);
    header.writeUInt32BE(width, 0);
    header.writeUInt32BE(height, 4);
    header[8] = 8;
    header[9] = 2;
    const scanlines = Buffer.alloc(height * (1 + width * 3));
    common = {
      header: pngChunk('IHDR', header),
      data: pngChunk('IDAT', deflateSync(scanlines, { level: 1 })),
    };
    imageCache.set(key, common);
  }
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    common.header,
    pngChunk('tEXt', Buffer.from(`fixture\0${marker}`, 'utf8')),
    common.data,
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
};

const writeEvidence = (fixture, label, content = label) => {
  const file = `${label}.txt`;
  const bytes = Buffer.from(content, 'utf8');
  writeFileSync(path.join(fixture.evidenceRoot, file), bytes);
  return { reference: file, sha256: SHA(bytes) };
};

const approvePair = (fixture, object, label) => {
  const evidence = writeEvidence(fixture, label);
  object.status = 'approved';
  object.reference = evidence.reference;
  object.sha256 = evidence.sha256;
};

const makeReleaseFixture = () => {
  const directory = mkdtempSync(
    path.join(tmpdir(), 'birthday-store-evidence-'),
  );
  const assetRoot = path.join(directory, 'assets');
  const evidenceRoot = path.join(directory, 'evidence');
  const artifactRoot = path.join(directory, 'artifacts');
  mkdirSync(assetRoot);
  mkdirSync(evidenceRoot);
  mkdirSync(artifactRoot);
  const fixture = {
    directory,
    assetRoot,
    evidenceRoot,
    artifacts: {
      android: path.join(artifactRoot, 'birthday-autopilot-1.aab'),
      ios: path.join(artifactRoot, 'birthday-autopilot-1.ipa'),
    },
  };
  writeFileSync(fixture.artifacts.android, 'signed android release fixture');
  writeFileSync(fixture.artifacts.ios, 'signed ios release fixture');

  const document = clone(TEMPLATE);
  document.packageStage = 'release';
  document.sourceRevision = SOURCE_REVISION;
  document.generatedAt = GENERATED_AT;
  document.validUntil = VALID_UNTIL;
  document.launchCountries = ['IN'];
  const androidSha = SHA(readFileSync(fixture.artifacts.android));
  const iosSha = SHA(readFileSync(fixture.artifacts.ios));
  Object.assign(document.releaseCoordinates.android, {
    artifactFileName: path.basename(fixture.artifacts.android),
    artifactSha256: androidSha,
    signingCertificateSha256: 'a'.repeat(64),
  });
  Object.assign(document.releaseCoordinates.ios, {
    artifactFileName: path.basename(fixture.artifacts.ios),
    artifactSha256: iosSha,
    distributionCertificateSha256: 'b'.repeat(64),
  });
  Object.assign(document.publicIdentity, {
    developerDisplayName: 'Verified Developer Identity',
    supportEmail: 'support@birthday-autopilot.co.in',
    publicSiteBaseUrl: 'https://birthday-autopilot.co.in/',
    storeSupportUrl: 'https://birthday-autopilot.co.in/support/',
    privacyUrl: 'https://birthday-autopilot.co.in/privacy/',
    termsUrl: 'https://birthday-autopilot.co.in/terms/',
    deletionUrl: 'https://birthday-autopilot.co.in/delete/',
    identityVerifiedSupportUrl: 'https://account-help.co.in/delete-request',
  });

  for (const [locale, localization] of Object.entries(document.localizations)) {
    localization.status = 'approved';
    localization.copySha256 = calculateLocalizationSha256(localization);
    const review = writeEvidence(fixture, `${locale}-copy-review`);
    localization.humanReviewReference = review.reference;
    localization.humanReviewSha256 = review.sha256;
  }

  const feature = png(1024, 500, 'feature');
  writeFileSync(path.join(assetRoot, 'play-feature.png'), feature);
  Object.assign(document.assets.playFeatureGraphic, {
    status: 'captured',
    file: 'play-feature.png',
    sha256: SHA(feature),
    bytes: feature.length,
    width: 1024,
    height: 500,
    containsRealPersonalData: false,
    approvedForStore: true,
  });

  for (const [groupName, screenshots] of [
    ['play', document.assets.playPhoneScreenshots],
    ['app', document.assets.appStoreIphoneScreenshots],
  ]) {
    for (const screenshot of screenshots) {
      const isPlay = groupName === 'play';
      const width = isPlay ? 1080 : 1290;
      const height = isPlay ? 1920 : 2796;
      const bytes = png(width, height, screenshot.id);
      const file = `${screenshot.id}.png`;
      writeFileSync(path.join(assetRoot, file), bytes);
      Object.assign(screenshot, {
        status: 'captured',
        file,
        sha256: SHA(bytes),
        bytes: bytes.length,
        width,
        height,
        captureArtifactSha256: isPlay ? androidSha : iosSha,
        containsRealPersonalData: false,
        imitatesSystemUi: false,
        approvedForStore: true,
      });
    }
  }

  for (const [name, bundle] of [
    ['play-data-safety-export', document.play.dataSafety],
    ['app-privacy-export', document.appStore.appPrivacy],
  ]) {
    bundle.status = 'approved';
    const review = writeEvidence(fixture, name);
    bundle.consoleExportReference = review.reference;
    bundle.consoleExportSha256 = review.sha256;
    bundle.taxonomyReviewedAt = GENERATED_AT;
    bundle.allCurrentConsoleQuestionsAnswered = true;
    bundle.sdkPracticesReviewed = true;
    bundle.privacyPolicyConsistent = true;
    for (const answer of bundle.answers) {
      Object.assign(answer, {
        answer: 'not-collected',
        shared: false,
        ephemeral: false,
        required: false,
        linkedToIdentity: false,
        tracking: false,
        purposes: [],
      });
    }
  }

  const sms = document.play.smsPermissions;
  sms.status = 'approved';
  sms.unattendedPersonalBirthdaySmsOnly = true;
  sms.prominentDisclosureCovered = true;
  sms.carrierChargesDisclosed = true;
  sms.recipientAndContentPreapproved = true;
  Object.assign(sms, {
    ...Object.fromEntries(
      [
        ['declaration', 'declaration'],
        ['demoVideoEvidence', 'demo-video'],
        ['reviewerInstructions', 'play-reviewer-instructions'],
        ['policyDecision', 'play-policy-decision'],
      ].flatMap(([field, name]) => {
        const evidence = writeEvidence(fixture, name);
        return [
          [`${field}Reference`, evidence.reference],
          [`${field}Sha256`, evidence.sha256],
        ];
      }),
    ),
    demoVideoUrl: 'https://review-video.co.in/play-sms-review',
    policyDecision: 'approved',
  });

  for (const [name, access] of [
    ['play-access', document.play.reviewAccess],
    ['app-access', document.appStore.reviewAccess],
  ]) {
    access.testAccountProvisioned = true;
    access.credentialVaultReference = `vault/${name}`;
    const instructions = writeEvidence(fixture, `${name}-instructions`);
    access.instructionsReference = instructions.reference;
    access.instructionsSha256 = instructions.sha256;
  }
  approvePair(fixture, document.play.contentRating, 'play-content-rating');
  approvePair(fixture, document.play.targetAudience, 'play-target-audience');
  approvePair(fixture, document.appStore.ageRating, 'app-age-rating');
  approvePair(
    fixture,
    document.appStore.exportCompliance,
    'app-export-compliance',
  );

  const privacyManifestBytes = Buffer.from('<xml><dict/></xml>');
  const privacyManifestPath = path.join(
    fixture.directory,
    document.appStore.privacyManifest.sourcePath,
  );
  mkdirSync(path.dirname(privacyManifestPath), { recursive: true });
  writeFileSync(privacyManifestPath, privacyManifestBytes);
  document.appStore.privacyManifest.status = 'approved';
  document.appStore.privacyManifest.sha256 = SHA(privacyManifestBytes);
  document.appStore.privacyManifest.requiredReasonApisReviewed = true;

  const mergedManifest = writeEvidence(fixture, 'merged-privacy-manifest');
  document.appStore.privacyManifest.mergedArchiveManifestReference =
    mergedManifest.reference;
  document.appStore.privacyManifest.mergedArchiveManifestSha256 =
    mergedManifest.sha256;

  const login = document.appStore.googleOnlyLoginRationale;
  login.status = 'approved';
  login.reviewGuidelineReviewedAt = GENERATED_AT;
  login.appReviewDisposition = 'accepted';
  const rationale = writeEvidence(fixture, 'google-login-rationale');
  login.rationaleReference = rationale.reference;
  login.rationaleSha256 = rationale.sha256;
  const loginDecision = writeEvidence(fixture, 'google-login-app-review');
  login.appReviewReference = loginDecision.reference;
  login.appReviewSha256 = loginDecision.sha256;

  const reviewNotes = writeEvidence(fixture, 'app-review-notes-detail');
  document.appStore.reviewNotes.status = 'approved';
  document.appStore.reviewNotes.reference = reviewNotes.reference;
  document.appStore.reviewNotes.sha256 = reviewNotes.sha256;
  const appDecision = writeEvidence(fixture, 'app-review-decision');
  document.appStore.appReviewDecision = {
    disposition: 'accepted',
    reference: appDecision.reference,
    sha256: appDecision.sha256,
  };

  document.accessibility.status = 'approved';
  for (const key of Object.keys(document.accessibility)) {
    if (!['status', 'evidenceReference', 'evidenceSha256'].includes(key)) {
      document.accessibility[key] = true;
    }
  }
  const accessibility = writeEvidence(fixture, 'accessibility-evidence');
  document.accessibility.evidenceReference = accessibility.reference;
  document.accessibility.evidenceSha256 = accessibility.sha256;

  for (const reference of document.evidenceReferences) {
    const evidence = writeEvidence(fixture, reference.id);
    reference.path = evidence.reference;
    reference.sha256 = evidence.sha256;
  }

  document.approvalScopeSha256 = calculateApprovalScopeSha256(document);
  for (const approval of document.approvals) {
    const evidence = writeEvidence(fixture, `approval-${approval.role}`);
    Object.assign(approval, {
      status: 'approved',
      approver: `Named ${approval.role} owner`,
      reference: evidence.reference,
      sha256: evidence.sha256,
      scopeSha256: document.approvalScopeSha256,
      approvedAt: GENERATED_AT,
      validUntil: VALID_UNTIL,
    });
  }

  fixture.document = document;
  fixture.context = {
    mode: 'release',
    now: NOW,
    projectRoot: fixture.directory,
    currentSourceRevision: SOURCE_REVISION,

    artifacts: fixture.artifacts,
    assetRoot,
    evidenceRoot,
    stitchIds: STITCH_IDS,
    hosting: {
      publicBaseUrl: 'https://birthday-autopilot.co.in/',
      developerDisplayName: 'Verified Developer Identity',
      supportUrl: 'https://account-help.co.in/delete-request',
    },
  };
  return fixture;
};

test('the committed draft is complete, truthful, and cannot represent approval', () => {
  const result = validateStoreSubmissionEvidence(clone(TEMPLATE), {
    mode: 'template',
    projectRoot: ROOT,
    stitchIds: STITCH_IDS,
  });
  assert.deepEqual(result.errors, []);
  assert.equal(TEMPLATE.packageStage, 'draft');
  assert.equal(TEMPLATE.sourceRevision, null);
  assert.equal(TEMPLATE.launchCountries.length, 0);
  assert.ok(
    TEMPLATE.approvals.every(approval => approval.status === 'pending'),
  );
  assert.ok(
    [
      ...TEMPLATE.assets.playPhoneScreenshots,
      ...TEMPLATE.assets.appStoreIphoneScreenshots,
    ].every(
      screenshot => screenshot.status === 'missing' && screenshot.file === null,
    ),
  );
});

test('a fully bound release fixture passes every semantic and file check', () => {
  const fixture = makeReleaseFixture();
  const result = validateStoreSubmissionEvidence(
    fixture.document,
    fixture.context,
  );
  assert.deepEqual(result.errors, []);
  assert.equal(result.scopeSha256, fixture.document.approvalScopeSha256);
});

test('taxonomy reviews accept the exact seven-day validity boundary', () => {
  const fixture = makeReleaseFixture();
  assert.equal(
    Date.parse(fixture.document.validUntil) -
      Date.parse(fixture.document.play.dataSafety.taxonomyReviewedAt),
    7 * 86_400_000,
  );
  assert.equal(
    Date.parse(fixture.document.validUntil) -
      Date.parse(fixture.document.appStore.appPrivacy.taxonomyReviewedAt),
    7 * 86_400_000,
  );
  assert.deepEqual(
    validateStoreSubmissionEvidence(fixture.document, fixture.context).errors,
    [],
  );
});

test('every taxonomy review rejects a stale instant beyond the exact boundary', () => {
  for (const [bundleName, expectedLabel] of [
    ['dataSafety', 'Play Data Safety'],
    ['appPrivacy', 'App Privacy'],
  ]) {
    const fixture = makeReleaseFixture();
    const bundle =
      bundleName === 'dataSafety'
        ? fixture.document.play.dataSafety
        : fixture.document.appStore.appPrivacy;
    bundle.taxonomyReviewedAt = '2026-07-11T23:59:59.999Z';
    const messages = validateStoreSubmissionEvidence(
      fixture.document,
      fixture.context,
    ).errors.join('\n');
    assert.match(
      messages,
      new RegExp(
        `${expectedLabel}\\.taxonomyReviewedAt must remain current through package validUntil within 7 days`,
        'u',
      ),
    );
  }

  const oldAtGenerationAndValidation = makeReleaseFixture();
  oldAtGenerationAndValidation.document.play.dataSafety.taxonomyReviewedAt =
    '2026-07-04T23:59:59.999Z';
  oldAtGenerationAndValidation.document.appStore.appPrivacy.taxonomyReviewedAt =
    '2026-07-04T23:59:59.999Z';
  const oldMessages = validateStoreSubmissionEvidence(
    oldAtGenerationAndValidation.document,
    oldAtGenerationAndValidation.context,
  ).errors.join('\n');
  for (const expectedLabel of ['Play Data Safety', 'App Privacy']) {
    assert.match(
      oldMessages,
      new RegExp(
        `${expectedLabel}\\.taxonomyReviewedAt must be no more than 7 days old at package generation`,
        'u',
      ),
    );
    assert.match(
      oldMessages,
      new RegExp(
        `${expectedLabel}\\.taxonomyReviewedAt must be no more than 7 days old at validation time`,
        'u',
      ),
    );
  }
});

test('every taxonomy review rejects an instant after package generation or validation', () => {
  const fixture = makeReleaseFixture();
  fixture.document.play.dataSafety.taxonomyReviewedAt =
    '2026-07-12T00:00:00.001Z';
  fixture.document.appStore.appPrivacy.taxonomyReviewedAt =
    '2026-07-15T00:00:00.001Z';
  const messages = validateStoreSubmissionEvidence(
    fixture.document,
    fixture.context,
  ).errors.join('\n');
  assert.match(
    messages,
    /Play Data Safety\.taxonomyReviewedAt must not be later than package generatedAt/u,
  );
  assert.match(
    messages,
    /App Privacy\.taxonomyReviewedAt must not be later than package generatedAt/u,
  );
  assert.match(
    messages,
    /App Privacy\.taxonomyReviewedAt must not be later than validation time/u,
  );
});

test('later authority-bound mobile versions remain valid release coordinates', () => {
  const fixture = makeReleaseFixture();
  Object.assign(fixture.document.releaseCoordinates.android, {
    versionCode: 42,
    versionName: '2.7.0',
  });
  Object.assign(fixture.document.releaseCoordinates.ios, {
    shortVersion: '2.7.0',
    buildNumber: '42',
  });
  fixture.document.approvalScopeSha256 = calculateApprovalScopeSha256(
    fixture.document,
  );
  for (const approval of fixture.document.approvals) {
    approval.scopeSha256 = fixture.document.approvalScopeSha256;
  }

  assert.deepEqual(
    validateStoreSubmissionEvidence(fixture.document, fixture.context).errors,
    [],
  );

  fixture.document.releaseCoordinates.android.versionCode = 0;
  fixture.document.releaseCoordinates.ios.buildNumber = '01';
  const invalid = validateStoreSubmissionEvidence(
    fixture.document,
    fixture.context,
  ).errors.join('\n');
  assert.match(invalid, /versionCode must be a positive safe integer/u);
  assert.match(invalid, /iOS buildNumber is invalid/u);
});

test('submission permits pending store decisions but release requires acceptance', () => {
  const fixture = makeReleaseFixture();
  fixture.document.packageStage = 'submission';
  fixture.document.play.smsPermissions.policyDecision = 'pending';
  fixture.document.appStore.googleOnlyLoginRationale.appReviewDisposition =
    'pending';
  fixture.document.appStore.appReviewDecision.disposition = 'pending';
  fixture.document.approvalScopeSha256 = calculateApprovalScopeSha256(
    fixture.document,
  );
  for (const approval of fixture.document.approvals) {
    approval.scopeSha256 = fixture.document.approvalScopeSha256;
  }
  const submission = validateStoreSubmissionEvidence(fixture.document, {
    ...fixture.context,
    mode: 'submission',
  });
  assert.deepEqual(submission.errors, []);
  const release = validateStoreSubmissionEvidence(fixture.document, {
    ...fixture.context,
    mode: 'release',
  });
  assert.match(release.errors.join('\n'), /packageStage must be release/u);
  assert.match(
    release.errors.join('\n'),
    /Play SMS policy decision must be approved/u,
  );
  assert.match(
    release.errors.join('\n'),
    /App Review must accept the Google-only login rationale/u,
  );
});

test('copy, source, artifact, approval, and expiration mutations fail closed', () => {
  const fixture = makeReleaseFixture();
  fixture.document.localizations['en-US'].play.title = 'Changed title';
  fixture.document.sourceRevision = 'f'.repeat(40);
  fixture.document.releaseCoordinates.android.artifactSha256 = 'c'.repeat(64);
  fixture.document.approvals[0].validUntil = '2026-07-01T00:00:00Z';
  const result = validateStoreSubmissionEvidence(
    fixture.document,
    fixture.context,
  );
  const messages = result.errors.join('\n');
  assert.match(messages, /sourceRevision does not match/u);
  assert.match(messages, /android artifact digest does not match/u);
  assert.match(messages, /copySha256 does not bind/u);
  assert.match(messages, /approvalScopeSha256 does not bind/u);
  assert.match(messages, /approval is expired/u);
});

test('screenshots reject PII, system imitation, wrong dimensions, and unknown Stitch IDs', () => {
  const fixture = makeReleaseFixture();
  const shot = fixture.document.assets.appStoreIphoneScreenshots[0];
  shot.containsRealPersonalData = true;
  shot.imitatesSystemUi = true;
  shot.width = 393;
  shot.screenId = 'Z99';
  const result = validateStoreSubmissionEvidence(
    fixture.document,
    fixture.context,
  );
  const messages = result.errors.join('\n');
  assert.match(messages, /containsRealPersonalData must be false/u);
  assert.match(messages, /imitatesSystemUi must be false/u);
  assert.match(messages, /dimensions do not match/u);
  assert.match(messages, /absent from the Stitch manifest/u);
  assert.match(messages, /screen coverage/u);
});

test('privacy answers, iOS truth, credentials, and evidence traversal fail closed', () => {
  const fixture = makeReleaseFixture();
  fixture.document.play.dataSafety.answers[0].answer = 'pending';
  fixture.document.appStore.reviewNotes.unattendedOrBackgroundSmsClaimed = true;
  fixture.document.play.reviewAccess.password = 'should-never-exist';
  fixture.document.evidenceReferences[0].path = '../escape.txt';
  const result = validateStoreSubmissionEvidence(
    fixture.document,
    fixture.context,
  );
  const messages = result.errors.join('\n');
  assert.match(messages, /must have a final answer/u);
  assert.match(messages, /unattended\/background SMS claim must be false/u);
  assert.match(messages, /credential or secret-shaped value/u);
  assert.match(messages, /normalized relative path/u);
});

test('evidence symlinks are rejected even when their target stays under the root', t => {
  if (!symlinksAvailable) {
    t.skip('host cannot create symbolic links');
    return;
  }
  const fixture = makeReleaseFixture();
  const target = fixture.document.evidenceReferences[0].path;
  symlinkSync(target, path.join(fixture.evidenceRoot, 'alias.txt'));
  fixture.document.evidenceReferences[0].path = 'alias.txt';
  const result = validateStoreSubmissionEvidence(
    fixture.document,
    fixture.context,
  );
  assert.match(result.errors.join('\n'), /contains a symbolic link/u);
});

test('CLI validates only the draft mode without external release inputs', () => {
  const cli = path.join(ROOT, 'tools/validate-store-submission-evidence.mjs');
  const template = path.join(
    ROOT,
    'tools/store-submission-evidence.template.json',
  );
  const draft = spawnSync(
    process.execPath,
    [cli, '--file', template, '--mode', 'template'],
    { cwd: ROOT, encoding: 'utf8' },
  );
  assert.equal(draft.status, 0, draft.stderr);
  assert.match(draft.stdout, /PASS store submission evidence \(template\)/u);
  const promotedTemplate = spawnSync(
    process.execPath,
    [cli, '--file', template, '--mode', 'release'],
    { cwd: ROOT, encoding: 'utf8' },
  );
  assert.equal(promotedTemplate.status, 1);
  assert.match(promotedTemplate.stderr, /--android-artifact is required/u);
});

test('schema and template enumerate all release-sensitive store contracts', () => {
  const schema = readFileSync(
    path.join(ROOT, 'tools/store-submission-evidence.schema.json'),
    'utf8',
  );
  for (const required of [
    'com.yashsomani.birthdayautopilot',
    'playPhoneScreenshots',
    'appStoreIphoneScreenshots',
    'smsPermissions',
    'googleOnlyLoginRationale',
    'privacyManifest',
    'appStoreAccessibilityLabelsSubmitted',
    'approvalScopeSha256',
  ]) {
    assert.match(schema, new RegExp(required, 'u'));
  }
});
