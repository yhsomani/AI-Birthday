import { buildContactEnrichmentPlan } from './contactEnrichment';
import { buildNotificationReadinessReport } from './notificationReadiness';
import { buildPrivacyCenterReport } from './privacyCenter';
import {
  providerEndpointReadinessFromConfigured,
  type ProviderEndpointReadiness
} from './providerEndpointReadiness';
import { buildSchedulingPolicySummary } from './schedulingPolicy';
import type { AppState, Screen } from './types';

export type SetupDoctorGroup = 'Required' | 'Quality' | 'Reliability' | 'Recovery';
export type SetupDoctorStatus = 'Ready' | 'Needs action' | 'Warning';
export type SetupDoctorCommand = 'testAiProvider' | 'planReminders';

export interface SetupDoctorCheck {
  id: string;
  group: SetupDoctorGroup;
  status: SetupDoctorStatus;
  title: string;
  impact: string;
  actionLabel: string;
  priority: number;
  targetScreen?: Screen;
  contactId?: string;
  command?: SetupDoctorCommand;
}

export interface SetupDoctorReport {
  summary: string;
  readyCount: number;
  totalCount: number;
  recommendedCheck?: SetupDoctorCheck;
  checksByGroup: Array<{
    group: SetupDoctorGroup;
    checks: SetupDoctorCheck[];
  }>;
  dryRun: {
    safe: true;
    message: string;
  };
}

export interface SetupDoctorDryRunSnapshot {
  safe: true;
  readyCount: number;
  totalCount: number;
  needsActionCount: number;
  warningCount: number;
  recommendedTitle?: string;
  summary: string;
  activityDetail: string;
}

export interface SetupDoctorEnvironment {
  aiEndpointConfigured?: boolean;
  emailEndpointConfigured?: boolean;
  aiEndpointReadiness?: ProviderEndpointReadiness;
  emailEndpointReadiness?: ProviderEndpointReadiness;
  releaseEvidence?: {
    blockers: string[];
    warnings: string[];
    legacyKotlinGradleArtifactPaths?: string[];
  };
}

const groups: SetupDoctorGroup[] = ['Required', 'Quality', 'Reliability', 'Recovery'];

const daysSince = (iso: string | undefined, now: Date) => {
  if (!iso) {
    return Number.POSITIVE_INFINITY;
  }
  return Math.floor((now.getTime() - new Date(iso).getTime()) / (1000 * 60 * 60 * 24));
};

const check = (value: SetupDoctorCheck) => value;

const aiEndpointReadinessFor = (env: SetupDoctorEnvironment) =>
  env.aiEndpointReadiness ?? providerEndpointReadinessFromConfigured(env.aiEndpointConfigured);

const emailEndpointReadinessFor = (env: SetupDoctorEnvironment) =>
  env.emailEndpointReadiness ?? providerEndpointReadinessFromConfigured(env.emailEndpointConfigured);

const storageImpactFor = (state: AppState) => {
  const health = state.persistence.storageHealth;
  if (state.persistence.status === 'Error') {
    return state.persistence.error
      ? `Local data storage needs recovery: ${state.persistence.error}`
      : 'Local data storage needs recovery before release.';
  }
  if (!health || health.status === 'Missing') {
    return 'Local data storage has not been verified on this device yet.';
  }
  if (health.status === 'Corrupt') {
    return health.issue
      ? `Local data storage integrity failed: ${health.issue}`
      : 'Local data storage integrity failed and needs recovery.';
  }
  if (health.storageFormat === 'Normalized') {
    return `${health.entryCount} normalized storage item(s) verified across ${health.chunkCount} chunk(s).`;
  }
  return 'Local data is readable but should be rewritten into normalized storage before release verification.';
};

const storageStatusFor = (state: AppState): SetupDoctorStatus => {
  const health = state.persistence.storageHealth;
  if (state.persistence.status === 'Error' || health?.status === 'Corrupt') {
    return 'Needs action';
  }
  if (health?.status === 'Ready' && health.storageFormat === 'Normalized') {
    return 'Ready';
  }
  return 'Warning';
};

const releaseEvidenceStatusFor = (env: SetupDoctorEnvironment): SetupDoctorStatus => {
  if (!env.releaseEvidence) {
    return 'Warning';
  }
  if (env.releaseEvidence.blockers.length > 0) {
    return 'Needs action';
  }
  if (env.releaseEvidence.warnings.length > 0) {
    return 'Warning';
  }
  return 'Ready';
};

const releaseEvidenceImpactFor = (env: SetupDoctorEnvironment) => {
  const evidence = env.releaseEvidence;
  if (!evidence) {
    return 'React Native release evidence has not been attached to this Setup Check run.';
  }
  if (evidence.blockers.length > 0) {
    return evidence.blockers.length + ' React Native release blocker(s) must be resolved before release.';
  }
  if (evidence.warnings.length > 0) {
    const legacyCount = evidence.legacyKotlinGradleArtifactPaths?.length ?? 0;
    return legacyCount > 0
      ? evidence.warnings.length + ' React Native release evidence warning(s) remain, including ' + legacyCount + ' legacy Android artifact path(s).'
      : evidence.warnings.length + ' React Native release evidence warning(s) remain for signed builds, device smoke, or store evidence.';
  }
  return 'React Native release evidence has no blockers or warnings.';
};

export const buildSetupDoctorReport = (
  state: AppState,
  env: SetupDoctorEnvironment,
  now: Date = new Date()
): SetupDoctorReport => {
  const pendingMessages = state.messages.filter(message => message.status === 'Needs review' || message.status === 'Draft');
  const failedMessages = state.messages.filter(
    message =>
      message.status === 'Failed' ||
      message.status === 'Blocked' ||
      message.status === 'Delivery unknown'
  );
  const weakContactPlans = state.contacts
    .map(contact => buildContactEnrichmentPlan(state, contact.id))
    .filter((plan): plan is NonNullable<typeof plan> => Boolean(plan))
    .filter(plan => plan.score < 50)
    .sort((a, b) => a.score - b.score);
  const recentWarnings = state.activity.filter(item => item.severity !== 'Info').slice(0, 5);
  const newestBackup = state.backups[0];
  const backupAgeDays = daysSince(newestBackup?.createdAt, now);
  const schedulingPolicy = buildSchedulingPolicySummary(state);
  const schedulingBlockers = schedulingPolicy.issues.filter(issue => issue.severity === 'Error');
  const notificationReadiness = buildNotificationReadinessReport(state, now);
  const aiEndpointReadiness = aiEndpointReadinessFor(env);
  const emailEndpointReadiness = emailEndpointReadinessFor(env);
  const emailProviderConfigured = emailEndpointReadiness.configured;
  const emailProviderChosen =
    state.settings.emailEnabled ||
    emailProviderConfigured ||
    state.emailDelivery.status !== 'Not configured' ||
    Boolean(state.emailDelivery.senderEmail);
  const notificationSetupIssueIds = new Set([
    'notifications-disabled',
    'notification-permission-blocked',
    'notification-permission-not-reviewed'
  ]);
  const primaryNotificationIssue = notificationReadiness.issues[0];
  const canRunReminderPlanCommand =
    schedulingBlockers.length === 0 &&
    notificationReadiness.status !== 'Blocked' &&
    !notificationReadiness.issues.some(issue => notificationSetupIssueIds.has(issue.id));
  const privacyReport = buildPrivacyCenterReport(state);
  const aiProviderStatus: SetupDoctorStatus =
    !state.settings.aiEnabled || (aiEndpointReadiness.productionReady && state.aiProvider.status !== 'Error')
      ? 'Ready'
      : 'Needs action';
  const aiProviderImpact = !state.settings.aiEnabled
    ? 'Local templates remain available while AI is disabled.'
    : aiEndpointReadiness.productionReady
      ? 'Provider drafts can be tested before use.'
      : aiEndpointReadiness.status === 'Development only'
        ? 'Provider endpoint is local-development only; configure HTTPS before release.'
        : aiEndpointReadiness.configured
          ? 'Configured provider endpoint is not safe to use. Use HTTPS without credentials, localhost, or private-network hosts.'
          : 'AI drafts will fall back to local templates until a secure endpoint is configured.';
  const emailProviderStatus: SetupDoctorStatus =
    emailProviderConfigured && !emailEndpointReadiness.productionReady
      ? emailEndpointReadiness.status === 'Development only'
        ? 'Warning'
        : 'Needs action'
      : 'Ready';
  const emailProviderImpact = emailProviderConfigured
    ? emailEndpointReadiness.productionReady
        ? 'Email provider delivery uses a release-ready HTTPS endpoint.'
        : emailEndpointReadiness.status === 'Development only'
          ? 'Email provider endpoint is local-development only; configure HTTPS before release.'
          : 'Configured email endpoint is not safe to use. Use HTTPS without credentials, localhost, or private-network hosts.'
    : emailProviderChosen
      ? 'Email provider delivery is optional; manual email handoff remains available.'
      : 'Email provider delivery is disabled; manual handoff remains available.';
  const checks: SetupDoctorCheck[] = [
    check({
      id: 'ai-provider',
      group: 'Required',
      status: aiProviderStatus,
      title: state.settings.aiEnabled ? 'AI provider endpoint' : 'AI drafts disabled',
      impact: aiProviderImpact,
      actionLabel: aiEndpointReadiness.canUseProviderEndpoint ? 'Test AI provider' : 'Open setup',
      targetScreen: 'more',
      command: aiEndpointReadiness.canUseProviderEndpoint ? 'testAiProvider' : undefined,
      priority: 10
    }),
    check({
      id: 'email-provider',
      group: emailProviderConfigured && !emailEndpointReadiness.productionReady ? 'Required' : 'Reliability',
      status: emailProviderStatus,
      title: emailProviderConfigured
        ? 'Email provider endpoint'
        : emailProviderChosen
          ? 'Email provider optional'
          : 'Email provider disabled',
      impact: emailProviderImpact,
      actionLabel: 'Review email settings',
      targetScreen: 'more',
      priority: 18
    }),
    check({
      id: 'personalization',
      group: 'Quality',
      status: weakContactPlans.length > 0 ? 'Needs action' : 'Ready',
      title: 'Contact personalization',
      impact:
        weakContactPlans.length > 0
          ? `${weakContactPlans.length} contact(s) need more relationship context.`
          : 'Contacts have enough context for useful drafts.',
      actionLabel: weakContactPlans[0] ? 'Review contact' : 'Open contacts',
      targetScreen: weakContactPlans[0] ? 'contactDetail' : 'contacts',
      contactId: weakContactPlans[0]?.contactId,
      priority: 20
    }),
    check({
      id: 'style-profile',
      group: 'Quality',
      status: ['Strong', 'Growing'].includes(state.styleProfile.confidence) ? 'Ready' : 'Needs action',
      title: 'Style Coach confidence',
      impact:
        state.styleProfile.confidence === 'Strong' || state.styleProfile.confidence === 'Growing'
          ? `${state.styleProfile.confidence} confidence profile is available.`
          : 'Train Style Coach with writing samples before relying on tone matching.',
      actionLabel: 'Open Style Coach',
      targetScreen: 'more',
      priority: 30
    }),
    check({
      id: 'pending-review',
      group: 'Required',
      status: pendingMessages.length > 0 ? 'Needs action' : 'Ready',
      title: 'Messages waiting for review',
      impact:
        pendingMessages.length > 0
          ? `${pendingMessages.length} message(s) need review before scheduling or sending.`
          : 'No messages are waiting for approval.',
      actionLabel: 'Review messages',
      targetScreen: 'messages',
      priority: 15
    }),
    check({
      id: 'privacy-controls',
      group: 'Required',
      status: privacyReport.highRiskCount > 0 ? 'Warning' : 'Ready',
      title: 'Privacy and permissions',
      impact: privacyReport.summary,
      actionLabel: 'Open privacy settings',
      targetScreen: 'more',
      priority: 35
    }),
    check({
      id: 'reminders',
      group: 'Reliability',
      status:
        notificationReadiness.status === 'Ready'
          ? 'Ready'
          : notificationReadiness.status === 'Blocked'
            ? 'Needs action'
            : 'Warning',
      title: 'Reminder readiness',
      impact:
        schedulingBlockers.length > 0
          ? schedulingBlockers.map(issue => `${issue.title}: ${issue.detail}`).join(' ')
          : `${notificationReadiness.summary}${
              primaryNotificationIssue
                ? ` ${primaryNotificationIssue.title}: ${primaryNotificationIssue.detail}`
                : ''
            } ${notificationReadiness.privacyNote}`,
      actionLabel: notificationReadiness.issues[0]?.actionLabel ?? 'Review reminders',
      targetScreen: 'more',
      command: canRunReminderPlanCommand ? 'planReminders' : undefined,
      priority: 40
    }),
    check({
      id: 'backup-freshness',
      group: 'Reliability',
      status: newestBackup && backupAgeDays <= 30 ? 'Ready' : 'Warning',
      title: 'Backup freshness',
      impact:
        newestBackup && backupAgeDays <= 30
          ? `Last encrypted backup is ${backupAgeDays} day(s) old.`
          : 'Create an encrypted backup before relying on this as your only relationship record.',
      actionLabel: 'Open backup',
      targetScreen: 'more',
      priority: 50
    }),
    check({
      id: 'local-storage',
      group: 'Reliability',
      status: storageStatusFor(state),
      title: 'Local data storage',
      impact: storageImpactFor(state),
      actionLabel: 'Open persistence',
      targetScreen: 'more',
      priority: 45
    }),
    check({
      id: 'release-evidence',
      group: 'Reliability',
      status: releaseEvidenceStatusFor(env),
      title: 'React Native release evidence',
      impact: releaseEvidenceImpactFor(env),
      actionLabel: 'Review release evidence',
      targetScreen: 'more',
      priority: 48
    }),
    check({
      id: 'failed-messages',
      group: 'Recovery',
      status: failedMessages.length > 0 ? 'Needs action' : 'Ready',
      title: 'Failed or blocked messages',
      impact:
        failedMessages.length > 0
          ? `${failedMessages.length} message(s) need recovery before they can be sent.`
          : 'No failed message recovery is needed.',
      actionLabel: 'Open messages',
      targetScreen: 'messages',
      priority: 5
    }),
    check({
      id: 'recent-warnings',
      group: 'Recovery',
      status: recentWarnings.length > 0 ? 'Warning' : 'Ready',
      title: 'Recent warnings',
      impact:
        recentWarnings.length > 0
          ? `${recentWarnings.length} recent warning(s) are available in Activity History.`
          : 'No recent warnings require attention.',
      actionLabel: 'View activity',
      targetScreen: 'more',
      priority: 60
    })
  ];

  const readyCount = checks.filter(item => item.status === 'Ready').length;
  const recommendedCheck = checks
    .filter(item => item.status === 'Needs action')
    .sort((a, b) => a.priority - b.priority)[0];

  return {
    summary:
      recommendedCheck
        ? `${readyCount}/${checks.length} checks ready. Next fix: ${recommendedCheck.title}.`
        : `${readyCount}/${checks.length} checks ready. No required blockers found.`,
    readyCount,
    totalCount: checks.length,
    recommendedCheck,
    checksByGroup: groups.map(group => ({
      group,
      checks: checks.filter(item => item.group === group)
    })),
    dryRun: {
      safe: true,
      message: 'Readiness dry run only checks setup, quality, and recovery state; it does not create, approve, schedule, or send messages.'
    }
  };
};

export const buildSetupDoctorDryRunSnapshot = (report: SetupDoctorReport): SetupDoctorDryRunSnapshot => {
  const checks = report.checksByGroup.flatMap(group => group.checks);
  const needsActionCount = checks.filter(check => check.status === 'Needs action').length;
  const warningCount = checks.filter(check => check.status === 'Warning').length;
  const recommendedTitle = report.recommendedCheck?.title;

  return {
    safe: true,
    readyCount: report.readyCount,
    totalCount: report.totalCount,
    needsActionCount,
    warningCount,
    recommendedTitle,
    summary: report.summary,
    activityDetail: recommendedTitle
      ? `${report.readyCount}/${report.totalCount} checks ready. Next fix: ${recommendedTitle}. ${needsActionCount} blocker(s), ${warningCount} warning(s).`
      : `${report.readyCount}/${report.totalCount} checks ready. No required blockers found. ${warningCount} warning(s).`
  };
};
