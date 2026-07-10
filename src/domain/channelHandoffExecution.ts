import type { HandoffTarget } from './channelHandoff';

export type HandoffShareStatus = 'shared' | 'dismissed';

export interface HandoffSharePayload {
  title: string;
  message: string;
}

export interface HandoffExecutionInput {
  target: HandoffTarget;
  body: string;
  contactName?: string;
  preferFallback?: boolean;
}

export interface HandoffExecutionDependencies {
  canOpenUrl: (url: string) => Promise<boolean>;
  openUrl: (url: string) => Promise<void>;
  share: (payload: HandoffSharePayload) => Promise<HandoffShareStatus>;
}

export type HandoffExecutionOutcome = 'opened-destination' | 'shared-fallback' | 'dismissed-fallback' | 'failed';

export interface HandoffExecutionResult {
  outcome: HandoffExecutionOutcome;
  usedFallback: boolean;
  needsSentConfirmation: boolean;
  errorMessage?: string;
}

export const buildHandoffSharePayload = (body: string, _contactName?: string): HandoffSharePayload => ({
  title: 'Approved message',
  message: body
});

const shareFallback = async (
  input: HandoffExecutionInput,
  dependencies: HandoffExecutionDependencies
): Promise<HandoffExecutionResult> => {
  if (!input.target.shareFallback) {
    return {
      outcome: 'failed',
      usedFallback: false,
      needsSentConfirmation: false,
      errorMessage: 'No manual handoff fallback is available.'
    };
  }

  try {
    const shareStatus = await dependencies.share(buildHandoffSharePayload(input.body, input.contactName));
    return shareStatus === 'shared'
      ? { outcome: 'shared-fallback', usedFallback: true, needsSentConfirmation: true }
      : { outcome: 'dismissed-fallback', usedFallback: true, needsSentConfirmation: false };
  } catch {
    return {
      outcome: 'failed',
      usedFallback: true,
      needsSentConfirmation: false,
      errorMessage: 'The manual handoff fallback could not be opened.'
    };
  }
};

export const runHandoffTarget = async (
  input: HandoffExecutionInput,
  dependencies: HandoffExecutionDependencies
): Promise<HandoffExecutionResult> => {
  if (!input.preferFallback && input.target.url) {
    try {
      if (await dependencies.canOpenUrl(input.target.url)) {
        await dependencies.openUrl(input.target.url);
        return {
          outcome: 'opened-destination',
          usedFallback: false,
          needsSentConfirmation: true
        };
      }
    } catch {
      return shareFallback(input, dependencies);
    }
  }

  return shareFallback(input, dependencies);
};
