export type CommandEvidenceStatus = 'Passed' | 'Failed' | 'Not recorded';

export type ReleaseEvidenceCommandId =
  'typecheck' | 'test' | 'native-prebuild' | 'audit' | 'expo-dependencies' | 'web-export' | 'diff-check';

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
  status: 'Attached' | 'Pending';
  detail: string;
}

export interface PackageJsonLike {
  name?: string;
  version?: string;
  main?: string;
  scripts?: Record<string, string>;
}

export interface ExpoAppConfigLike {
  expo?: {
    name?: string;
    slug?: string;
    version?: string;
    runtimeVersion?: { policy?: string } | string;
    scheme?: string;
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
    plugins?: string[];
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
  provenance?: ReleaseEvidenceProvenance;
  commands?: ReleaseEvidenceCommand[];
  deviceEvidence?: ReleaseEvidenceDeviceItem[];
  legacyKotlinGradleArtifactPaths?: string[];
}

export interface ReactNativeReleaseEvidence {
  generatedAt: string;
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
    platform: 'React Native / Expo';
    legacyKotlinGradleStatus: 'Reference only' | 'Removed from repository';
    legacyKotlinGradleReleaseRole: string;
    legacyKotlinGradleArtifactPaths: string[] | null;
  };
  releaseConfig: {
    npmTestUsesFullNonIsolatedSuite: boolean;
    easAppVersionSource: string;
    productionAndroidBuildType: unknown;
    productionIosSimulator: unknown;
    hasProductionSubmitProfile: boolean;
    androidPlugins: string[];
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
  { id: 'test', command: 'npm test', status: 'Not recorded' },
  { id: 'native-prebuild', command: 'npm run test:native-prebuild', status: 'Not recorded' },
  { id: 'audit', command: 'npm audit --audit-level=moderate', status: 'Not recorded' },
  { id: 'expo-dependencies', command: 'npx expo install --check', status: 'Not recorded' },
  {
    id: 'web-export',
    command: 'npx expo export --platform web --output-dir reports/web-export',
    status: 'Not recorded'
  },
  { id: 'diff-check', command: 'git diff --check', status: 'Not recorded' }
];

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

const validateProvenance = (provenance: ReleaseEvidenceProvenance | undefined): string[] => {
  if (!provenance) {
    return ['Release evidence provenance is missing.'];
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
  }
  if (!provenance.platform || !provenance.architecture) {
    issues.push('Release evidence runner platform is missing.');
  }
  if (provenance.dirty) {
    issues.push('Release evidence was generated from a dirty working tree.');
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
  const easBuild = input.easConfig.build ?? {};
  const production = easBuild.production ?? {};
  const commands = input.commands ?? defaultReleaseEvidenceCommands;
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
  const blockers = [...validateProvenance(input.provenance), ...validateCommands(commands)];
  const warnings: string[] = [];
  const testScript = input.packageJson.scripts?.test ?? '';
  const legacyKotlinGradleArtifactPaths = input.legacyKotlinGradleArtifactPaths;
  const legacyKotlinGradleRemoved =
    legacyKotlinGradleArtifactPaths !== undefined && legacyKotlinGradleArtifactPaths.length === 0;
  const deviceEvidence = withResolvedLegacyArchiveEvidence(
    input.deviceEvidence ?? defaultDeviceEvidence,
    legacyKotlinGradleArtifactPaths
  );

  if (!testScript.includes('--test-isolation=none') || !testScript.includes('src/**/*.test.ts')) {
    blockers.push('npm test must run the full React Native source-contract suite without per-file process isolation.');
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
  deviceEvidence
    .filter(item => item.status !== 'Attached')
    .forEach(item => warnings.push(`${item.id}: ${item.detail}`));

  return {
    generatedAt: input.generatedAt,
    provenance: input.provenance ?? null,
    app: {
      name: expo.name ?? 'RelateAI',
      slug: expo.slug ?? 'relateai',
      version: expo.version ?? input.packageJson.version ?? '0.0.0',
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
      platform: 'React Native / Expo',
      legacyKotlinGradleStatus: legacyKotlinGradleRemoved ? 'Removed from repository' : 'Reference only',
      legacyKotlinGradleReleaseRole: legacyKotlinGradleRemoved
        ? 'No active release role; legacy Android artifacts are absent from the repository.'
        : 'Excluded from RN release evidence and must not be used as a release gate.',
      legacyKotlinGradleArtifactPaths: legacyKotlinGradleArtifactPaths ?? null
    },
    releaseConfig: {
      npmTestUsesFullNonIsolatedSuite:
        testScript.includes('--test-isolation=none') && testScript.includes('src/**/*.test.ts'),
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
