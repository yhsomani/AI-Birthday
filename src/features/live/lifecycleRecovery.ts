import type { AccountProjection } from '../../domain/account/model';
import type { PlatformCapability } from '../../domain/shared/platform';

export const lifecycleRecoveryIdentity = (
  account: AccountProjection,
  platform: PlatformCapability['platform'],
): string | undefined => {
  if (account.kind === 'cleanup-pending') {
    return `cleanup:${account.operation}`;
  }
  if (
    platform === 'android' &&
    account.kind === 'connected' &&
    account.sender.platform === 'android' &&
    account.sender.kind === 'deleting'
  ) {
    return 'android-sender:deleting';
  }
  return undefined;
};

export const accountRequiresLifecycleRecovery = (
  account: AccountProjection,
  platform: PlatformCapability['platform'],
): boolean => lifecycleRecoveryIdentity(account, platform) !== undefined;
