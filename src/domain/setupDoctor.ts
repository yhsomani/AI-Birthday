import { buildContactEnrichmentPlan } from './contactEnrichment';
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

export interface SetupDoctorEnvironment {
  aiEndpointConfigured: boolean;
  emailEndpointConfigured: boolean;
}

const groups: SetupDoctorGroup[] = ['Required', 'Quality', 'Reliability', 'Recovery'];

const daysSince = (iso: string | undefined, now: Date) => {
  if (!iso) {
    return Number.POSITIVE_INFINITY;
  }
  return Math.floor((now.getTime() - new Date(iso).getTime()) / (1000 * 60 * 60 * 24));
};

const check = (value: SetupDoctorCheck) => value;

export const buildSetupDoctorReport = (
  state: AppState,
  env: SetupDoctorEnvironment,
  now: Date = new Date()
): SetupDoctorReport => {
  const pendingMessages = state.messages.filter(message => message.status === 'Needs review' || message.status === 'Draft');
  const failedMessages = state.messages.filter(message => message.status === 'Failed' || message.status === 'Blocked');
  const weakContactPlans = state.contacts
    .map(contact => buildContactEnrichmentPlan(state, contact.id))
    .filter((plan): plan is NonNullable<typeof plan> => Boolean(plan))
    .filter(plan => plan.score < 50)
    .sort((a, b) => a.score - b.score);
  const recentWarnings = state.activity.filter(item => item.severity !== 'Info').slice(0, 5);
  const newestBackup = state.backups[0];
  const backupAgeDays = daysSince(newestBackup?.createdAt, now);
  const checks: SetupDoctorCheck[] = [
    check({
      id: 'ai-provider',
      group: 'Required',
      status: !state.settings.aiEnabled || env.aiEndpointConfigured ? 'Ready' : 'Needs action',
      title: state.settings.aiEnabled ? 'AI provider endpoint' : 'AI drafts disabled',
      impact: state.settings.aiEnabled
        ? env.aiEndpointConfigured
          ? 'Provider drafts can be tested before use.'
          : 'AI drafts will fall back to local templates until a secure endpoint is configured.'
        : 'Local templates remain available while AI is disabled.',
      actionLabel: env.aiEndpointConfigured ? 'Test AI provider' : 'Open setup',
      targetScreen: 'more',
      command: env.aiEndpointConfigured ? 'testAiProvider' : undefined,
      priority: 10
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
      id: 'reminders',
      group: 'Reliability',
      status: state.settings.notificationsEnabled && state.reminderPlans.length > 0 ? 'Ready' : 'Warning',
      title: 'Reminder readiness',
      impact: state.settings.notificationsEnabled
        ? state.reminderPlans.length > 0
          ? `${state.reminderPlans.length} reminder plan(s) are ready.`
          : 'Plan reminders so upcoming events can notify you.'
        : 'Notifications are disabled; reminders stay visible in-app.',
      actionLabel: state.settings.notificationsEnabled ? 'Plan reminders' : 'Open settings',
      targetScreen: 'more',
      command: state.settings.notificationsEnabled ? 'planReminders' : undefined,
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
