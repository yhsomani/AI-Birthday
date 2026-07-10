import { Linking, Share } from 'react-native';
import {
  runHandoffTarget,
  type HandoffExecutionInput,
  type HandoffExecutionResult,
  type HandoffShareStatus
} from '../domain/channelHandoffExecution';

const shareMessage = async (payload: { title: string; message: string }): Promise<HandoffShareStatus> => {
  const result = await Share.share(payload);
  return result.action === Share.dismissedAction ? 'dismissed' : 'shared';
};

export const openManualHandoffTarget = (input: HandoffExecutionInput): Promise<HandoffExecutionResult> =>
  runHandoffTarget(input, {
    canOpenUrl: Linking.canOpenURL,
    openUrl: Linking.openURL,
    share: shareMessage
  });
