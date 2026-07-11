export type CommandEvidenceStatus = 'Passed' | 'Failed' | 'Not recorded';

export type ReleaseEvidenceAssessmentMode = 'production' | 'source-only';

export type ReleaseEvidenceCommandId =
  | 'typecheck'
  | 'lint'
  | 'format-check'
  | 'test-coverage'
  | 'native-prebuild'
  | 'audit'
  | 'expo-dependencies'
  | 'diff-check';

export type ExpoPluginLike = string | [string, Record<string, unknown>];

export interface ReleaseEvidenceCommand {
  id: ReleaseEvidenceCommandId;
  command: string;
  status: CommandEvidenceStatus;
  exitCode?: number;
  startedAt?: string;
  completedAt?: string;
  durationMs?: number;
  outputSha256?: string;
  detail?: string;
}

export interface ReleaseEvidenceProvenance {
  schemaVersion: 2;
  commitSha: string;
  dirty: boolean;
  workingTreeSha256: string;
  lockfileSha256: string;
  nodeVersion: string;
  npmVersion: string;
  platform: string;
  architecture: string;
  runner: 'local' | 'github-actions';
  ci?: {
    repository?: string;
    runId?: string;
    runAttempt?: string;
    workflowRef?: string;
  };
}

export interface ReleaseEvidenceDeviceItem {
  id:
    | 'signed-android-build'
    | 'signed-ios-build'
    | 'android-device-smoke'
    | 'ios-device-smoke'
    | 'store-submission'
    | 'legacy-archive-decision';
  status: 'Attached' | 'Pending' | 'Failed';
  detail: string;
  attachment?: {
    schemaVersion: 1;
    evidenceId: string;
    recordedAt: string;
    owner: string;
    sourceUrl: string;
    candidate: {
      commitSha: string;
      workingTreeSha256: string;
      appVersion: string;
    };
    artifacts: {
      androidSha256?: string;
      iosSha256?: string;
    };
    deviceTest?: {
      platform: 'android' | 'ios';
      deviceModel: string;
      osVersion: string;
      testRunId: string;
    };
    storeSubmission?: {
      googlePlayRecordId: string;
      appStoreConnectRecordId: string;
    };
  };
}

export interface PackageJsonLike {
  name?: string;
  version?: string;
  main?: string;
  packageManager?: string;
  scripts?: Record<string, string>;
}

export interface ExpoAppConfigLike {
  expo?: {
    name?: string;
    slug?: string;
    version?: string;
    runtimeVersion?: { policy?: string } | string;
    scheme?: string;
    platforms?: string[];
    ios?: {
      bundleIdentifier?: string;
      buildNumber?: string;
    };
    android?: {
      package?: string;
      versionCode?: number;
      permissions?: string[];
      blockedPermissions?: string[];
    };
    plugins?: ExpoPluginLike[];
  };
}

export interface EasConfigLike {
  cli?: {
    appVersionSource?: string;
  };
  build?: Record<string, Record<string, unknown>>;
  submit?: Record<string, unknown>;
}

export interface ReleaseEvidenceInput {
  packageJson: PackageJsonLike;
  appConfig: ExpoAppConfigLike;
  easConfig: EasConfigLike;
  generatedAt: string;
  assessmentMode?: ReleaseEvidenceAssessmentMode;
  provenance?: ReleaseEvidenceProvenance;
  commands?: ReleaseEvidenceCommand[];
  deviceEvidence?: ReleaseEvidenceDeviceItem[];
  legacyKotlinGradleArtifactPaths?: string[];
}

export interface ReactNativeReleaseEvidence {
  generatedAt: string;
  assessmentMode: ReleaseEvidenceAssessmentMode;
  provenance: ReleaseEvidenceProvenance | null;
  app: {
    name: string;
    slug: string;
    version: string;
    entrypoint: string;
    scheme: string;
    androidPackage: string;
    androidVersionCode: number | null;
    iosBundleIdentifier: string;
    iosBuildNumber: string;
    runtimeVersionPolicy: string;
  };
  activeReleaseSurface: {
    platform: 'React Native / Expo (Android and iOS)';
    platforms: ('android' | 'ios')[];
    legacyKotlinGradleStatus: 'Reference only' | 'Removed from repository';
    legacyKotlinGradleReleaseRole: string;
    legacyKotlinGradleArtifactPaths: string[] | null;
  };
  releaseConfig: {
    npmTestUsesFullNonIsolatedSuite: boolean;
    expoPlatforms: string[];
    easAppVersionSource: string;
    productionAndroidBuildType: unknown;
    productionIosSimulator: unknown;
    hasProductionSubmitProfile: boolean;
    androidPlugins: ExpoPluginLike[];
  };
  permissions: {
    requestedAndroidPermissions: string[];
    blockedAndroidPermissions: string[];
    forbiddenAndroidPermissionsAbsent: boolean;
    forbiddenAndroidPermissionsBlocked: boolean;
  };
  commands: ReleaseEvidenceCommand[];
  deviceEvidence: ReleaseEvidenceDeviceItem[];
  blockers: string[];
  warnings: string[];
}

export interface SetupDoctorReleaseEvidence {
  blockers: string[];
  warnings: string[];
  legacyKotlinGradleArtifactPaths?: string[];
}

export const forbiddenDirectAndroidPermissions = [
  'SEND_SMS',
  'READ_SMS',
  'RECEIVE_SMS',
  'READ_CALL_LOG',
  'READ_PHONE_NUMBERS',
  'USE_EXACT_ALARM',
  'SCHEDULE_EXACT_ALARM',
  'BIND_ACCESSIBILITY_SERVICE',
  'android.permission.SEND_SMS',
  'android.permission.READ_SMS',
  'android.permission.RECEIVE_SMS',
  'android.permission.READ_CALL_LOG',
  'android.permission.READ_PHONE_NUMBERS',
  'android.permission.USE_EXACT_ALARM',
  'android.permission.SCHEDULE_EXACT_ALARM',
  'android.permission.BIND_ACCESSIBILITY_SERVICE'
];

export const defaultReleaseEvidenceCommands: ReleaseEvidenceCommand[] = [
  { id: 'typecheck', command: 'npm run typecheck', status: 'Not recorded' },
  { id: 'lint', command: 'npm run lint', status: 'Not recorded' },
  { id: 'format-check', command: 'npm run format:check', status: 'Not recorded' },
  { id: 'test-coverage', command: 'npm run test:coverage', status: 'Not recorded' },
  { id: 'native-prebuild', command: 'npm run test:native-prebuild', status: 'Not recorded' },
  { id: 'audit', command: 'npm audit --audit-level=moderate', status: 'Not recorded' },
  { id: 'expo-dependencies', command: 'npx expo install --check', status: 'Not recorded' },
  { id: 'diff-check', command: 'git diff --check', status: 'Not recorded' }
];

const requiredReleasePackageScripts = {
  typecheck: 'tsc --noEmit',
  lint: 'eslint src',
  'format:check': 'prettier --check "src/**/*.{ts,tsx}" "*.{json,js,cjs}"',
  test: 'tsx --test --test-isolation=none "src/**/*.test.ts"',
  'test:coverage':
    'tsx --test --test-isolation=none --experimental-test-coverage --test-coverage-lines=90 --test-coverage-branches=80 --test-coverage-functions=90 "src/**/*.test.ts"',
  'test:native-prebuild': 'node scripts/verify_native_prebuild.js',
  'release:evidence': 'node --import tsx src/config/releaseEvidenceCli.ts'
} as const;

export const defaultDeviceEvidence: ReleaseEvidenceDeviceItem[] = [
  {
    id: 'signed-android-build',
    status: 'Pending',
    detail: 'Attach signed EAS Android app-bundle build evidence for the exact release candidate.'
  },
  {
    id: 'signed-ios-build',
    status: 'Pending',
    detail: 'Attach signed EAS iOS build evidence for the exact release candidate.'
  },
  {
    id: 'android-device-smoke',
    status: 'Pending',
    detail:
      'Attach Android device smoke evidence for notifications, calendar, handoff, widget, shortcuts, and core review flows.'
  },
  {
    id: 'ios-device-smoke',
    status: 'Pending',
    detail:
      'Attach iOS device smoke evidence for notifications, calendar, handoff, backup, lock, and core review flows.'
  },
  {
    id: 'store-submission',
    status: 'Pending',
    detail: 'Attach store submission or dry-run evidence after signed builds are produced.'
  },
  {
    id: 'legacy-archive-decision',
    status: 'Pending',
    detail: 'Archive or remove the Kotlin/Gradle tree only after explicit approval for that destructive cleanup.'
  }
];

const requestedAndroidPermissions = (appConfig: ExpoAppConfigLike) => appConfig.expo?.android?.permissions ?? [];
const blockedAndroidPermissions = (appConfig: ExpoAppConfigLike) => appConfig.expo?.android?.blockedPermissions ?? [];
const sha256Pattern = /^[a-f0-9]{64}$/;
const commitPattern = /^[a-f0-9]{40,64}$/;
const exactNpmPackageManagerPattern = /^npm@(\d+\.\d+\.\d+)$/;
const evidenceIdPattern = /^[A-Za-z0-9][A-Za-z0-9._:-]{2,127}$/;
const requiredProductionDeviceEvidenceIds = new Set<ReleaseEvidenceDeviceItem['id']>([
  'signed-android-build',
  'signed-ios-build',
  'android-device-smoke',
  'ios-device-smoke',
  'store-submission'
]);

const withResolvedLegacyArchiveEvidence = (
  deviceEvidence: ReleaseEvidenceDeviceItem[],
  legacyKotlinGradleArtifactPaths: string[] | undefined
): ReleaseEvidenceDeviceItem[] => {
  if (!legacyKotlinGradleArtifactPaths) {
    return deviceEvidence;
  }

  return deviceEvidence.map(item => {
    if (item.id !== 'legacy-archive-decision') {
      return item;
    }

    if (legacyKotlinGradleArtifactPaths.length === 0) {
      return {
        ...item,
        status: 'Attached',
        detail: 'Legacy Kotlin/Gradle artifacts are absent from the active React Native repository.'
      };
    }

    return {
      ...item,
      status: 'Pending',
      detail: `Legacy Android artifact paths remain for explicit archive/removal approval: ${legacyKotlinGradleArtifactPaths.join(
        ', '
      )}.`
    };
  });
};

const validateProvenance = (
  provenance: ReleaseEvidenceProvenance | undefined,
  packageJson: PackageJsonLike
): string[] => {
  const pinnedNpmVersion = packageJson.packageManager?.match(exactNpmPackageManagerPattern)?.[1];
  if (!provenance) {
    return [
      'Release evidence provenance is missing.',
      ...(pinnedNpmVersion ? [] : ['package.json must pin npm with an exact npm@x.y.z packageManager value.'])
    ];
  }

  const issues: string[] = [];
  if (provenance.schemaVersion !== 2) {
    issues.push('Release evidence provenance schema is unsupported.');
  }
  if (!commitPattern.test(provenance.commitSha)) {
    issues.push('Release evidence is not bound to a valid Git commit SHA.');
  }
  if (!sha256Pattern.test(provenance.workingTreeSha256)) {
    issues.push('Release evidence working-tree fingerprint is missing or invalid.');
  }
  if (!sha256Pattern.test(provenance.lockfileSha256)) {
    issues.push('Release evidence package-lock fingerprint is missing or invalid.');
  }
  if (!/^v\d+\.\d+\.\d+/.test(provenance.nodeVersion)) {
    issues.push('Release evidence Node version is missing or invalid.');
  }
  if (!/^\d+\.\d+\.\d+/.test(provenance.npmVersion)) {
    issues.push('Release evidence npm version is missing or invalid.');
  } else if (!pinnedNpmVersion) {
    issues.push('package.json must pin npm with an exact npm@x.y.z packageManager value.');
  } else if (provenance.npmVersion !== pinnedNpmVersion) {
    issues.push(`Release evidence used npm ${provenance.npmVersion}, but package.json pins npm ${pinnedNpmVersion}.`);
  }
  if (!provenance.platform || !provenance.architecture) {
    issues.push('Release evidence runner platform is missing.');
  }
  if (provenance.dirty) {
    issues.push('Release evidence was generated from a dirty working tree.');
  }
  return issues;
};

const validatePackageScripts = (packageJson: PackageJsonLike): string[] =>
  Object.entries(requiredReleasePackageScripts).flatMap(([name, required]) =>
    packageJson.scripts?.[name] === required
      ? []
      : [`package.json script "${name}" must exactly match the required release gate.`]
  );

type ReleaseEvidenceAttachment = NonNullable<ReleaseEvidenceDeviceItem['attachment']>;

const isBoundedText = (value: unknown, maxLength = 256): value is string =>
  typeof value === 'string' && value.trim().length > 0 && value.length <= maxLength;

const isHttpsEvidenceUrl = (value: unknown): value is string => {
  if (!isBoundedText(value, 2048)) return false;
  try {
    const url = new URL(value);
    return (
      url.protocol === 'https:' && Boolean(url.hostname) && !url.username && !url.password && !url.search && !url.hash
    );
  } catch {
    return false;
  }
};

const validateAttachedDeviceEvidence = (
  id: ReleaseEvidenceDeviceItem['id'],
  attachmentValue: unknown,
  provenance: ReleaseEvidenceProvenance | undefined,
  appVersion: string,
  generatedAt: string
): string[] => {
  if (id === 'legacy-archive-decision') return [];
  if (!attachmentValue || typeof attachmentValue !== 'object' || Array.isArray(attachmentValue)) {
    return [`${id} is marked Attached without a structured candidate-bound attachment.`];
  }

  const attachment = attachmentValue as Partial<ReleaseEvidenceAttachment>;
  const candidate = attachment.candidate as Partial<ReleaseEvidenceAttachment['candidate']> | undefined;
  const artifacts = attachment.artifacts as Partial<ReleaseEvidenceAttachment['artifacts']> | undefined;
  const issues: string[] = [];
  const recordedAt = typeof attachment.recordedAt === 'string' ? Date.parse(attachment.recordedAt) : Number.NaN;
  const reportTime = Date.parse(generatedAt);

  if (attachment.schemaVersion !== 1) {
    issues.push(`${id} attachment schema is unsupported.`);
  }
  if (typeof attachment.evidenceId !== 'string' || !evidenceIdPattern.test(attachment.evidenceId)) {
    issues.push(`${id} attachment evidenceId is missing or invalid.`);
  }
  if (!Number.isFinite(recordedAt) || (Number.isFinite(reportTime) && recordedAt > reportTime + 5 * 60 * 1000)) {
    issues.push(`${id} attachment recordedAt is missing, invalid, or later than the release report.`);
  }
  if (!isBoundedText(attachment.owner, 128)) {
    issues.push(`${id} attachment owner is missing or invalid.`);
  }
  if (!isHttpsEvidenceUrl(attachment.sourceUrl)) {
    issues.push(`${id} attachment sourceUrl must be a credential-free HTTPS evidence URL.`);
  }
  if (!candidate || !provenance) {
    issues.push(`${id} attachment cannot be bound without release provenance.`);
  } else {
    if (candidate.commitSha !== provenance.commitSha) {
      issues.push(`${id} attachment is not bound to the release commit.`);
    }
    if (candidate.workingTreeSha256 !== provenance.workingTreeSha256) {
      issues.push(`${id} attachment is not bound to the release working-tree fingerprint.`);
    }
    if (candidate.appVersion !== appVersion) {
      issues.push(`${id} attachment is not bound to app version ${appVersion}.`);
    }
  }

  const androidSha256 = artifacts?.androidSha256;
  const iosSha256 = artifacts?.iosSha256;
  const requireAndroidArtifact = () => {
    if (!sha256Pattern.test(androidSha256 ?? '')) {
      issues.push(`${id} attachment is missing the signed Android artifact SHA-256.`);
    }
  };
  const requireIosArtifact = () => {
    if (!sha256Pattern.test(iosSha256 ?? '')) {
      issues.push(`${id} attachment is missing the signed iOS artifact SHA-256.`);
    }
  };

  if (id === 'signed-android-build') requireAndroidArtifact();
  if (id === 'signed-ios-build') requireIosArtifact();
  if (id === 'android-device-smoke' || id === 'ios-device-smoke') {
    const platform = id === 'android-device-smoke' ? 'android' : 'ios';
    if (platform === 'android') requireAndroidArtifact();
    else requireIosArtifact();
    const deviceTest = attachment.deviceTest as Partial<ReleaseEvidenceAttachment['deviceTest']> | undefined;
    if (
      deviceTest?.platform !== platform ||
      !isBoundedText(deviceTest.deviceModel, 128) ||
      !isBoundedText(deviceTest.osVersion, 64) ||
      !isBoundedText(deviceTest.testRunId, 128)
    ) {
      issues.push(`${id} attachment is missing matching device, OS, and test-run identity.`);
    }
  }
  if (id === 'store-submission') {
    requireAndroidArtifact();
    requireIosArtifact();
    const submission = attachment.storeSubmission as Partial<ReleaseEvidenceAttachment['storeSubmission']> | undefined;
    if (
      !isBoundedText(submission?.googlePlayRecordId, 128) ||
      !isBoundedText(submission?.appStoreConnectRecordId, 128)
    ) {
      issues.push(`${id} attachment must identify both Google Play and App Store Connect records.`);
    }
  }
  return issues;
};

const validateDeviceEvidence = (
  deviceEvidence: readonly ReleaseEvidenceDeviceItem[],
  provenance: ReleaseEvidenceProvenance | undefined,
  appVersion: string,
  generatedAt: string
): string[] => {
  const issues: string[] = [];
  const definitions = new Set(defaultDeviceEvidence.map(item => item.id));
  const seen = new Set<ReleaseEvidenceDeviceItem['id']>();
  const evidenceIds = new Set<string>();

  deviceEvidence.forEach(item => {
    const candidate = item as Partial<ReleaseEvidenceDeviceItem> | null | undefined;
    const id = candidate?.id;
    if (!id || !definitions.has(id)) {
      issues.push(`Release evidence contains an unsupported device evidence id: ${String(id)}.`);
      return;
    }
    if (seen.has(id)) {
      issues.push(`Release evidence contains duplicate ${id} device evidence.`);
      return;
    }
    seen.add(id);
    if (!candidate.status || !['Attached', 'Pending', 'Failed'].includes(candidate.status)) {
      issues.push(`${id} device evidence has an unsupported status.`);
    }
    if (typeof candidate.detail !== 'string' || !candidate.detail.trim()) {
      issues.push(`${id} device evidence is missing detail.`);
    }
    if (candidate.status === 'Attached') {
      issues.push(...validateAttachedDeviceEvidence(id, candidate.attachment, provenance, appVersion, generatedAt));
      const evidenceId = candidate.attachment?.evidenceId;
      if (evidenceId && evidenceIds.has(evidenceId)) {
        issues.push(`${id} attachment reuses evidenceId ${evidenceId}.`);
      } else if (evidenceId) {
        evidenceIds.add(evidenceId);
      }
    }
  });

  defaultDeviceEvidence.forEach(item => {
    if (!seen.has(item.id)) {
      issues.push(`${item.id} device evidence is missing.`);
    }
  });

  const attachmentFor = (id: ReleaseEvidenceDeviceItem['id']) =>
    deviceEvidence.find(item => item.id === id && item.status === 'Attached')?.attachment;
  const androidBuildSha = attachmentFor('signed-android-build')?.artifacts.androidSha256;
  const iosBuildSha = attachmentFor('signed-ios-build')?.artifacts.iosSha256;
  const androidSmokeSha = attachmentFor('android-device-smoke')?.artifacts.androidSha256;
  const iosSmokeSha = attachmentFor('ios-device-smoke')?.artifacts.iosSha256;
  const storeAttachment = attachmentFor('store-submission');
  if (androidBuildSha && androidSmokeSha && androidBuildSha !== androidSmokeSha) {
    issues.push('Android device-smoke evidence is not bound to the signed Android release artifact.');
  }
  if (iosBuildSha && iosSmokeSha && iosBuildSha !== iosSmokeSha) {
    issues.push('iOS device-smoke evidence is not bound to the signed iOS release artifact.');
  }
  if (androidBuildSha && storeAttachment?.artifacts.androidSha256 !== androidBuildSha) {
    issues.push('Store-submission evidence is not bound to the signed Android release artifact.');
  }
  if (iosBuildSha && storeAttachment?.artifacts.iosSha256 !== iosBuildSha) {
    issues.push('Store-submission evidence is not bound to the signed iOS release artifact.');
  }
  return issues;
};

const commandProofIsComplete = (command: ReleaseEvidenceCommand) => {
  const startedAt = command.startedAt ? Date.parse(command.startedAt) : Number.NaN;
  const completedAt = command.completedAt ? Date.parse(command.completedAt) : Number.NaN;
  return (
    Number.isInteger(command.exitCode) &&
    Number.isFinite(startedAt) &&
    Number.isFinite(completedAt) &&
    completedAt >= startedAt &&
    Number.isInteger(command.durationMs) &&
    (command.durationMs ?? -1) >= 0 &&
    sha256Pattern.test(command.outputSha256 ?? '')
  );
};

const validateCommands = (commands: ReleaseEvidenceCommand[]): string[] => {
  const issues: string[] = [];
  const definitions = new Map(defaultReleaseEvidenceCommands.map(command => [command.id, command.command]));
  const seen = new Set<ReleaseEvidenceCommandId>();

  commands.forEach(command => {
    if (seen.has(command.id)) {
      issues.push(`Release evidence contains duplicate ${command.id} command results.`);
      return;
    }
    seen.add(command.id);

    if (definitions.get(command.id) !== command.command) {
      issues.push(`${command.id} evidence did not execute the required command.`);
    }
    if (command.status === 'Not recorded') {
      issues.push(`${command.command} evidence has not been executed.`);
      return;
    }
    if (!commandProofIsComplete(command)) {
      issues.push(`${command.command} evidence is missing exit-code, timing, or output-hash proof.`);
    }
    if (command.status === 'Passed' && command.exitCode !== 0) {
      issues.push(`${command.command} is marked Passed with a non-zero exit code.`);
    }
    if (command.status === 'Failed') {
      issues.push(`${command.command} evidence is failing.`);
    }
  });

  defaultReleaseEvidenceCommands.forEach(command => {
    if (!seen.has(command.id)) {
      issues.push(`${command.command} evidence is missing.`);
    }
  });
  return issues;
};

export const buildReactNativeReleaseEvidence = (input: ReleaseEvidenceInput): ReactNativeReleaseEvidence => {
  const expo = input.appConfig.expo ?? {};
  const android = expo.android ?? {};
  const ios = expo.ios ?? {};
  const appVersion = expo.version ?? input.packageJson.version ?? '0.0.0';
  const easBuild = input.easConfig.build ?? {};
  const production = easBuild.production ?? {};
  const commands = input.commands ?? defaultReleaseEvidenceCommands;
  const assessmentMode = input.assessmentMode ?? 'production';
  const configuredPlatforms = expo.platforms ?? [];
  const mobilePlatformsAreExact =
    configuredPlatforms.length === 2 && configuredPlatforms.includes('android') && configuredPlatforms.includes('ios');
  const productionAndroidBuildType = (production.android as { buildType?: unknown } | undefined)?.buildType;
  const productionIosSimulator = (production.ios as { simulator?: unknown } | undefined)?.simulator;
  const requested = requestedAndroidPermissions(input.appConfig);
  const blocked = blockedAndroidPermissions(input.appConfig);
  const requestedSet = new Set(requested);
  const blockedSet = new Set(blocked);
  const forbiddenAbsent = forbiddenDirectAndroidPermissions.every(permission => !requestedSet.has(permission));
  const forbiddenBlocked = forbiddenDirectAndroidPermissions
    .filter(permission => permission.startsWith('android.permission.'))
    .every(permission => blockedSet.has(permission));
  const blockers = [
    ...validateProvenance(input.provenance, input.packageJson),
    ...validatePackageScripts(input.packageJson),
    ...validateCommands(commands)
  ];
  const warnings: string[] = [];
  const testScript = input.packageJson.scripts?.test ?? '';
  const legacyKotlinGradleArtifactPaths = input.legacyKotlinGradleArtifactPaths;
  const legacyKotlinGradleRemoved =
    legacyKotlinGradleArtifactPaths !== undefined && legacyKotlinGradleArtifactPaths.length === 0;
  const deviceEvidence = withResolvedLegacyArchiveEvidence(
    input.deviceEvidence ?? defaultDeviceEvidence,
    legacyKotlinGradleArtifactPaths
  );
  blockers.push(...validateDeviceEvidence(deviceEvidence, input.provenance, appVersion, input.generatedAt));

  if (!Number.isFinite(Date.parse(input.generatedAt))) {
    blockers.push('Release evidence generatedAt is missing or invalid.');
  }

  if (!mobilePlatformsAreExact) {
    blockers.push('Expo release platforms must be exactly Android and iOS; web is not a supported release surface.');
  }
  if (productionAndroidBuildType !== 'app-bundle') {
    blockers.push('Production Android EAS profile must build an app bundle.');
  }
  if (productionIosSimulator !== false) {
    blockers.push('Production iOS EAS profile must target physical devices, not simulator builds.');
  }
  if (!input.easConfig.submit?.production) {
    blockers.push('Production EAS submit profile is missing.');
  }
  if (!forbiddenAbsent) {
    blockers.push(
      'RN release config directly requests a forbidden SMS, SMS inbox, call-log, phone-number, exact-alarm, or AccessibilityService permission.'
    );
  }
  if (!forbiddenBlocked) {
    blockers.push('RN release config does not block all forbidden Android permissions from merged manifests.');
  }
  deviceEvidence.forEach(rawItem => {
    const item = rawItem as Partial<ReleaseEvidenceDeviceItem> | null | undefined;
    if (!item?.id || !item.status || typeof item.detail !== 'string' || item.status === 'Attached') return;
    const detail = `${item.id} (${item.status}): ${item.detail}`;
    if (assessmentMode === 'production' && requiredProductionDeviceEvidenceIds.has(item.id)) {
      blockers.push(`Required production evidence is not attached: ${detail}`);
      return;
    }
    warnings.push(detail);
  });

  return {
    generatedAt: input.generatedAt,
    assessmentMode,
    provenance: input.provenance ?? null,
    app: {
      name: expo.name ?? 'RelateAI',
      slug: expo.slug ?? 'relateai',
      version: appVersion,
      entrypoint: input.packageJson.main ?? 'index.js',
      scheme: expo.scheme ?? 'relateai',
      androidPackage: android.package ?? '',
      androidVersionCode: android.versionCode ?? null,
      iosBundleIdentifier: ios.bundleIdentifier ?? '',
      iosBuildNumber: ios.buildNumber ?? '',
      runtimeVersionPolicy:
        typeof expo.runtimeVersion === 'string' ? expo.runtimeVersion : (expo.runtimeVersion?.policy ?? '')
    },
    activeReleaseSurface: {
      platform: 'React Native / Expo (Android and iOS)',
      platforms: ['android', 'ios'],
      legacyKotlinGradleStatus: legacyKotlinGradleRemoved ? 'Removed from repository' : 'Reference only',
      legacyKotlinGradleReleaseRole: legacyKotlinGradleRemoved
        ? 'No active release role; legacy Android artifacts are absent from the repository.'
        : 'Excluded from RN release evidence and must not be used as a release gate.',
      legacyKotlinGradleArtifactPaths: legacyKotlinGradleArtifactPaths ?? null
    },
    releaseConfig: {
      npmTestUsesFullNonIsolatedSuite: testScript === requiredReleasePackageScripts.test,
      expoPlatforms: [...configuredPlatforms],
      easAppVersionSource: input.easConfig.cli?.appVersionSource ?? '',
      productionAndroidBuildType,
      productionIosSimulator,
      hasProductionSubmitProfile: Boolean(input.easConfig.submit?.production),
      androidPlugins: expo.plugins ?? []
    },
    permissions: {
      requestedAndroidPermissions: requested,
      blockedAndroidPermissions: blocked,
      forbiddenAndroidPermissionsAbsent: forbiddenAbsent,
      forbiddenAndroidPermissionsBlocked: forbiddenBlocked
    },
    commands,
    deviceEvidence,
    blockers,
    warnings
  };
};

export const setupDoctorReleaseEvidenceFromReport = (
  evidence: Pick<ReactNativeReleaseEvidence, 'activeReleaseSurface' | 'blockers' | 'warnings'>
): SetupDoctorReleaseEvidence => {
  const legacyPaths = evidence.activeReleaseSurface.legacyKotlinGradleArtifactPaths;
  return {
    blockers: [...evidence.blockers],
    warnings: [...evidence.warnings],
    ...(legacyPaths ? { legacyKotlinGradleArtifactPaths: [...legacyPaths] } : {})
  };
};
