import type { AccountProjection } from '../account/model';
import type { AutomationProjection } from '../automation/model';
import type { SyncProjection } from '../contacts/model';
import type {
  DeviceEligibility,
  ReadinessProjection,
} from '../readiness/model';
import type { PlatformCapability } from '../shared/platform';

export const SETUP_STEPS = [
  'compatibility',
  'google-account',
  'contacts-disclosure',
  'sync-summary',
  'recipient-selection',
  'message-and-policy',
  'test-review',
  'test-progress',
  'reliability-repairs',
  'activation-review',
  'complete',
] as const;

export type SetupStep = (typeof SETUP_STEPS)[number];

export type BootstrapProjection = Readonly<{
  capability: PlatformCapability;
  eligibility: DeviceEligibility;
  account: AccountProjection;
  setupStep: SetupStep;
}>;

export type SetupProjection = Readonly<{
  step: SetupStep;
  eligibility: DeviceEligibility;
  account: AccountProjection;
  contacts: SyncProjection;
  readiness: ReadinessProjection;
  automation: AutomationProjection;
}>;
