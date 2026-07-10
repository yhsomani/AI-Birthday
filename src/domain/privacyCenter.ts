import type {
  AppState,
  PermissionCapability,
  PermissionDecision,
  PermissionPromptOutcome,
  PermissionUserIntent,
  Screen,
  SystemAuthorization,
  SystemPermissionCapability
} from './types';

export interface PrivacyCapabilityRow {
  capability: PermissionCapability;
  decision: PermissionDecision;
  status: 'Enabled' | 'Needs review' | 'Denied' | 'Off' | 'Unavailable' | 'Recommended';
  purpose: string;
  fallback: string;
  actionLabel: string;
  targetScreen?: Screen;
  sensitive: boolean;
  systemAuthorization?: SystemAuthorization;
  userIntent?: PermissionUserIntent;
  lastPromptOutcome?: PermissionPromptOutcome;
  systemDetail?: string;
}

export interface PrivacyCenterReport {
  summary: string;
  highRiskCount: number;
  recommendationCount: number;
  rows: PrivacyCapabilityRow[];
}

const row = (
  state: AppState,
  capability: PermissionCapability,
  purpose: string,
  fallback: string,
  options: {
    enabled?: boolean;
    recommended?: boolean;
    targetScreen?: Screen;
    sensitive?: boolean;
  } = {}
): PrivacyCapabilityRow => {
  const decision = state.privacy.permissionDecisions[capability];
  const systemRecord = (
    ['Contacts', 'Notifications', 'Calendar', 'Biometric lock'] as SystemPermissionCapability[]
  ).includes(capability as SystemPermissionCapability)
    ? state.privacy.permissionRecords?.[capability as SystemPermissionCapability]
    : undefined;
  const enabled = options.enabled ?? decision === 'Granted';
  const recommended = options.recommended ?? false;
  const status =
    decision === 'Unavailable'
      ? 'Unavailable'
      : decision === 'Denied'
        ? 'Denied'
        : enabled && decision === 'Granted'
          ? 'Enabled'
          : enabled
            ? 'Needs review'
            : recommended
              ? 'Recommended'
              : 'Off';

  return {
    capability,
    decision,
    status,
    purpose,
    fallback,
    actionLabel:
      status === 'Enabled'
        ? 'Review'
        : status === 'Denied'
          ? 'View fallback'
          : status === 'Unavailable'
            ? 'View recovery'
            : 'Review setup',
    targetScreen: options.targetScreen,
    sensitive: options.sensitive ?? true,
    systemAuthorization: systemRecord?.systemAuthorization,
    userIntent: systemRecord?.userIntent,
    lastPromptOutcome: systemRecord?.lastPromptOutcome,
    systemDetail: systemRecord
      ? `${systemRecord.systemAuthorization}${
          systemRecord.platformStatus ? ` (${systemRecord.platformStatus})` : ''
        }${systemRecord.canAskAgain === false ? '; use device settings' : ''}`
      : undefined
  };
};

export const buildPrivacyCenterReport = (state: AppState): PrivacyCenterReport => {
  const hasPrivateNotes = state.memories.some(memory => memory.category === 'Private');
  const hasProviderSetup =
    state.aiProvider.status !== 'Not configured' ||
    state.emailDelivery.status !== 'Not configured' ||
    Boolean(state.emailDelivery.senderEmail);
  const recommendBiometricLock = !state.settings.biometricLockEnabled && (hasPrivateNotes || hasProviderSetup);
  const rows: PrivacyCapabilityRow[] = [
    row(
      state,
      'Contacts',
      'Import names, phone numbers, emails, and birthdays only after the user starts contact import.',
      'Manual contacts and manual events stay available when contacts permission is denied.',
      { enabled: state.privacy.permissionDecisions.Contacts === 'Granted', targetScreen: 'contacts' }
    ),
    row(
      state,
      'Notifications',
      'Notify about event reminders, pending approvals, and recovery issues.',
      'Home, Messages, and Events still show in-app reminder states when notifications are denied.',
      { enabled: state.settings.notificationsEnabled, targetScreen: 'settings' }
    ),
    row(
      state,
      'SMS',
      'Open approved SMS handoff only after a message is reviewed and the contact has a phone number.',
      'Manual share or another channel can be used when SMS is disabled or denied.',
      { enabled: state.settings.smsEnabled, targetScreen: 'settings' }
    ),
    row(
      state,
      'Calendar',
      'Export relationship events or import calendar candidates after user-initiated calendar sync.',
      'Events can be created and reviewed manually without calendar permission.',
      { enabled: state.privacy.permissionDecisions.Calendar === 'Granted', targetScreen: 'events' }
    ),
    row(
      state,
      'Biometric lock',
      recommendBiometricLock
        ? 'Private notes or provider setup are now present. Biometric lock can protect this app on shared devices.'
        : 'Protect private contacts, memories, drafts, backups, and sent history on shared devices.',
      'Biometric lock remains optional; core manual workflows still work without it.',
      { enabled: state.settings.biometricLockEnabled, recommended: recommendBiometricLock, targetScreen: 'settings' }
    ),
    row(
      state,
      'AI provider',
      'Generate drafts through a secure backend using only approved, privacy-filtered context.',
      'Local templates remain available when AI is disabled, denied, or unavailable.',
      { enabled: state.settings.aiEnabled && state.aiProvider.status === 'Ready', targetScreen: 'settings' }
    ),
    row(
      state,
      'Email provider',
      'Send approved Email messages through a configured backend without storing provider secrets on device.',
      'Manual mailto handoff remains available when provider delivery is off.',
      { enabled: state.settings.emailEnabled && state.emailDelivery.status === 'Ready', targetScreen: 'settings' }
    ),
    row(
      state,
      'WhatsApp handoff',
      'Open approved text in WhatsApp while keeping the user in control of final send.',
      'Manual share or SMS/email can be used if WhatsApp handoff is disabled.',
      {
        enabled: state.settings.whatsappHandoffEnabled && state.privacy.whatsappHandoffConsent,
        targetScreen: 'settings'
      }
    ),
    row(
      state,
      'Backup export',
      'Create explicit encrypted backup files protected by a passphrase the app does not store.',
      'Without export, local data remains on this device and may be lost if the app is removed.',
      { enabled: state.backups.length > 0, targetScreen: 'backup' }
    )
  ];

  const highRiskCount = rows.filter(item => item.status === 'Needs review' || item.status === 'Denied').length;
  const recommendationCount = rows.filter(item => item.status === 'Recommended').length;
  return {
    rows,
    highRiskCount,
    recommendationCount,
    summary:
      highRiskCount > 0
        ? `${highRiskCount} privacy-sensitive capability/capabilities need review or have a fallback active.`
        : recommendationCount > 0
          ? `${recommendationCount} privacy recommendation(s) available.`
          : 'Privacy-sensitive capabilities have clear user-controlled states.'
  };
};
