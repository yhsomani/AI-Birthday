import type { AccountMode, AutomationMode } from '../domain/types';

/**
 * Release capability switches describe behavior that is genuinely implemented.
 * They are deliberately static until a provider identity/sync adapter or durable
 * background job runner is shipped and certified.
 */
export const productAvailability = Object.freeze({
  googleSync: Object.freeze({
    available: false,
    reason: 'Google sync is not available in this release. Local mode remains fully usable.'
  }),
  durableUnattendedAutomation: Object.freeze({
    available: false,
    reason: 'Unattended message generation and sending are not available. Drafts and sends remain review-controlled.'
  })
});

export const availableAccountModes: readonly AccountMode[] = productAvailability.googleSync.available
  ? ['Local', 'Google sync']
  : ['Local'];

export const availableAutomationModes: readonly AutomationMode[] = productAvailability.durableUnattendedAutomation
  .available
  ? ['Always ask', 'Smart approve', 'VIP approve', 'Fully auto']
  : ['Always ask', 'Smart approve', 'VIP approve'];

export const isAccountModeAvailable = (mode: AccountMode | undefined): mode is AccountMode =>
  mode !== undefined && availableAccountModes.includes(mode);

export const isAutomationModeAvailable = (mode: AutomationMode | undefined): mode is AutomationMode =>
  mode !== undefined && availableAutomationModes.includes(mode);
