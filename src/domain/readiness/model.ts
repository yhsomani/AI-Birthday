import type { ActionHandle, IssueId } from '../shared/brand';
import type { PlatformCapability } from '../shared/platform';
import type { SafeReasonCode } from '../shared/reasonCodes';
import type { UtcInstant } from '../shared/temporal';

export type AndroidGateName = 'test' | 'activation' | 'birthday';
export type IosGateName = 'composer';

export type ReadinessIssue = Readonly<{
  id: IssueId;
  code: SafeReasonCode;
  severity: 'info' | 'warning' | 'blocking';
  blocks: readonly (AndroidGateName | IosGateName)[];
  action?:
    | Readonly<{
        kind: 'native-action';
        handle: ActionHandle;
        labelKey: string;
      }>
    | undefined;
}>;

export type GateDecision =
  | Readonly<{ kind: 'checking' }>
  | Readonly<{ kind: 'allowed' }>
  | Readonly<{ kind: 'blocked'; issues: readonly ReadinessIssue[] }>;

export type ReadinessProjection =
  | Readonly<{
      platform: 'android';
      test: GateDecision;
      activation: GateDecision;
      birthday: GateDecision;
      lastCheckedAt: UtcInstant;
    }>
  | Readonly<{
      platform: 'ios';
      composer: GateDecision;
      unattendedAutomation: Readonly<{
        kind: 'unavailable';
        reason: 'platform-composer-only';
      }>;
      lastCheckedAt: UtcInstant;
    }>;

type EligibilityIssueSet = Readonly<{
  primaryIssue: ReadinessIssue;
  otherIssues: readonly ReadinessIssue[];
}>;

export type DeviceEligibility =
  | Readonly<{
      kind: 'checking';
      capability: PlatformCapability;
    }>
  | Readonly<{
      kind: 'supported';
      capability: PlatformCapability;
      channelLabel: string;
      chargeDisclosureVersion: string;
    }>
  | (Readonly<{
      kind: 'limited';
      capability: PlatformCapability;
    }> &
      EligibilityIssueSet)
  | (Readonly<{
      kind: 'unsupported';
      capability: PlatformCapability;
    }> &
      EligibilityIssueSet);
