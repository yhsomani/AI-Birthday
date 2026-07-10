import type { MessageChannel, MessageDraft } from './types';

export const MIN_MESSAGE_BODY_LENGTH = 12;
export const SMS_SEGMENT_SIZE = 160;
export const SMS_CONCATENATED_SEGMENT_SIZE = 153;
export const SMS_UCS2_SEGMENT_SIZE = 70;
export const SMS_UCS2_CONCATENATED_SEGMENT_SIZE = 67;
export const SMS_MAX_SEGMENTS = 6;

export const MESSAGE_BODY_LIMITS: Record<MessageChannel, number> = {
  SMS: SMS_CONCATENATED_SEGMENT_SIZE * SMS_MAX_SEGMENTS,
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
      smsEncoding?: 'GSM-7' | 'UCS-2';
    }
  | {
      ok: false;
      channel: MessageChannel;
      characterCount: number;
      message: string;
      limit?: number;
    };

const formatCount = (value: number) => value.toLocaleString('en-US');

const GSM7_BASIC = new Set(
  `@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà`
);
const GSM7_EXTENDED = new Set('^{}\\[~]|€');

const smsEncodingMetrics = (body: string) => {
  const value = body.trim();
  let septets = 0;
  for (const character of value) {
    if (GSM7_BASIC.has(character)) septets += 1;
    else if (GSM7_EXTENDED.has(character)) septets += 2;
    else {
      const units = value.length;
      return {
        encoding: 'UCS-2' as const,
        units,
        segments: Math.max(
          1,
          units <= SMS_UCS2_SEGMENT_SIZE ? 1 : Math.ceil(units / SMS_UCS2_CONCATENATED_SEGMENT_SIZE)
        ),
        limit: SMS_UCS2_CONCATENATED_SEGMENT_SIZE * SMS_MAX_SEGMENTS
      };
    }
  }
  return {
    encoding: 'GSM-7' as const,
    units: septets,
    segments: Math.max(1, septets <= SMS_SEGMENT_SIZE ? 1 : Math.ceil(septets / SMS_CONCATENATED_SEGMENT_SIZE)),
    limit: SMS_CONCATENATED_SEGMENT_SIZE * SMS_MAX_SEGMENTS
  };
};

export const smsSegmentCountForBody = (body: string) => smsEncodingMetrics(body).segments;

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

  if (message.channel === 'SMS') {
    const metrics = smsEncodingMetrics(message.body);
    if (metrics.segments > SMS_MAX_SEGMENTS) {
      return {
        ok: false,
        channel: message.channel,
        characterCount,
        limit: metrics.limit,
        message: `SMS messages must fit within ${SMS_MAX_SEGMENTS} parts (${formatCount(metrics.limit)} ${
          metrics.encoding === 'GSM-7' ? 'GSM-7 units' : 'UTF-16 units'
        }). Shorten the message or switch channel.`
      };
    }
    if (metrics.segments === 1) {
      return {
        ok: true,
        channel: message.channel,
        characterCount,
        smsSegments: 1,
        smsEncoding: metrics.encoding
      };
    }
    return {
      ok: true,
      channel: message.channel,
      characterCount,
      smsSegments: metrics.segments,
      smsEncoding: metrics.encoding,
      warning: `SMS may send as ${metrics.segments} parts using ${metrics.encoding}. Review before sending.`
    };
  }

  return {
    ok: true,
    channel: message.channel,
    characterCount
  };
};
