import type { MessageChannel, MessageDraft } from './types';

export const MIN_MESSAGE_BODY_LENGTH = 12;
export const SMS_SEGMENT_SIZE = 160;
export const SMS_MAX_SEGMENTS = 6;

export const MESSAGE_BODY_LIMITS: Record<MessageChannel, number> = {
  SMS: SMS_SEGMENT_SIZE * SMS_MAX_SEGMENTS,
  WhatsApp: 4000,
  Email: 10000,
  Manual: 4000
};

export type MessageBodyPolicyResult =
  | {
      ok: true;
      channel: MessageChannel;
      characterCount: number;
      warning?: string;
      smsSegments?: number;
    }
  | {
      ok: false;
      channel: MessageChannel;
      characterCount: number;
      message: string;
      limit?: number;
    };

const formatCount = (value: number) => value.toLocaleString('en-US');

export const smsSegmentCountForBody = (body: string) =>
  Math.max(1, Math.ceil(body.trim().length / SMS_SEGMENT_SIZE));

export const validateMessageBodyForChannel = (
  message: Pick<MessageDraft, 'body' | 'channel'>
): MessageBodyPolicyResult => {
  const characterCount = message.body.trim().length;
  if (characterCount < MIN_MESSAGE_BODY_LENGTH) {
    return {
      ok: false,
      channel: message.channel,
      characterCount,
      message: 'Write a longer message before approval. The message text is too short.'
    };
  }

  const limit = MESSAGE_BODY_LIMITS[message.channel];
  if (characterCount > limit) {
    return {
      ok: false,
      channel: message.channel,
      characterCount,
      limit,
      message: `${message.channel} messages must be ${formatCount(limit)} characters or fewer. Shorten the message or switch channel.`
    };
  }

  if (message.channel === 'SMS' && characterCount > SMS_SEGMENT_SIZE) {
    const smsSegments = smsSegmentCountForBody(message.body);
    return {
      ok: true,
      channel: message.channel,
      characterCount,
      smsSegments,
      warning: `SMS may send as ${smsSegments} parts. Review before sending.`
    };
  }

  return {
    ok: true,
    channel: message.channel,
    characterCount
  };
};
