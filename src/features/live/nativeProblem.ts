import type { NativeProblem } from '../../domain/shared/result';
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { TranslationKey } from '../../localization/resources';

export const nativeBridgeProblem: NativeProblem = {
  kind: 'internal',
  supportCode:
    'NATIVE_BRIDGE_UNAVAILABLE' as import('../../domain/shared/brand').SafeSupportCode,
};

export const nativeContractProblem: NativeProblem = {
  kind: 'internal',
  supportCode:
    'NATIVE_CONTRACT_INVALID' as import('../../domain/shared/brand').SafeSupportCode,
};

export const nativePlatformMismatchProblem: NativeProblem = {
  kind: 'internal',
  supportCode:
    'LIVE_PLATFORM_MISMATCH' as import('../../domain/shared/brand').SafeSupportCode,
};

export const nativeProblemReference = (problem: NativeProblem): string => {
  switch (problem.kind) {
    case 'internal':
      return problem.supportCode;
    case 'temporarily-unavailable':
    case 'unsupported':
    case 'conflict':
      return problem.code;
    case 'stale-revision':
      return 'STALE_NATIVE_REVISION';
    case 'validation':
      return 'NATIVE_VALIDATION_REJECTED';
    case 'action-required':
      return 'NATIVE_ACTION_REQUIRED';
    case 'cancelled':
      return 'NATIVE_REQUEST_CANCELLED';
  }
};

export const nativeProblemMessageKey = (
  problem: NativeProblem,
): TranslationKey => {
  switch (problem.kind) {
    case 'stale-revision':
      return safeReasonMessageKey('stale-revision');
    case 'cancelled':
      return 'live.error.cancelled';
    case 'validation':
      return problem.issues[0]
        ? safeReasonMessageKey(problem.issues[0].code)
        : 'live.error.validation';
    case 'action-required':
      return 'live.error.actionRequired';
    case 'temporarily-unavailable':
    case 'unsupported':
    case 'conflict':
      return safeReasonMessageKey(problem.code);
    case 'internal':
      return 'live.error.bridge';
  }
};
