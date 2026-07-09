import type { AppState, MessageDraft, StyleProfile } from './types';

export type StyleAnalysisSource = 'Manual samples' | 'Recent sent messages';

export type StyleAnalysisResult =
  | {
      ok: true;
      profile: StyleProfile;
      preview: string;
      source: StyleAnalysisSource;
    }
  | {
      ok: false;
      message: string;
      source: StyleAnalysisSource;
    };

const splitSamples = (text: string) =>
  text
    .split(/\n\s*\n|---+/)
    .map(sample => sample.trim())
    .filter(Boolean);

const words = (text: string) => text.trim().split(/\s+/).filter(Boolean);

const containsHinglish = (text: string) =>
  /\b(yaar|acha|accha|bahut|aaj|kal|thoda|haan|nahi|dil|khush|shukriya)\b/i.test(text);

const containsHindiScript = (text: string) => /[\u0900-\u097F]/.test(text);

const emojiCount = (text: string) => [...text].filter(char => /\p{Extended_Pictographic}/u.test(char)).length;

const formalSignals = (text: string) =>
  /\b(regards|sincerely|appreciate|congratulations|meaningful|continued success|apologize|properly)\b/i.test(text);

const casualSignals = (text: string) =>
  /\b(hey|hi|haha|lol|just checking|thinking of you|no rush)\b/i.test(text);

const greetingPreview = (samples: string[]) => {
  const first = samples
    .map(sample => sample.split(/[.!?\n]/)[0]?.trim())
    .find(line => line && line.length <= 80);
  return first ?? 'Hi, I was thinking of you.';
};

const buildProfile = (samples: string[], source: StyleAnalysisSource): StyleAnalysisResult => {
  const combined = samples.join('\n');
  const sampleWords = samples.flatMap(words);
  const totalCharacters = samples.reduce((sum, sample) => sum + sample.length, 0);
  if (samples.length < 2 || sampleWords.length < 18) {
    return {
      ok: false,
      source,
      message: 'Add at least two meaningful writing samples before training Style Coach.'
    };
  }
  if (totalCharacters > 8000) {
    return {
      ok: false,
      source,
      message: 'Style samples are too long. Use a shorter representative set.'
    };
  }

  const averageLength = Math.round(totalCharacters / samples.length);
  const formalCount = samples.filter(formalSignals).length;
  const casualCount = samples.filter(casualSignals).length;
  const formality =
    formalCount > casualCount
      ? 'Leans formal and respectful'
      : casualCount > formalCount
        ? 'Warm and conversational'
        : 'Balanced: adapts between casual and respectful';
  const language = containsHindiScript(combined)
    ? 'Hindi and English mix'
    : containsHinglish(combined)
      ? 'English with Hinglish touches'
      : 'English';
  const emojiUse = emojiCount(combined) >= samples.length ? 'Moderate' : emojiCount(combined) > 0 ? 'Light' : 'Rare';
  const confidence: StyleProfile['confidence'] =
    samples.length >= 5 && sampleWords.length >= 80 ? 'Strong' : samples.length >= 3 ? 'Growing' : 'Starting';

  return {
    ok: true,
    source,
    profile: {
      confidence,
      formality,
      language,
      averageLength,
      emojiUse,
      sampleCount: samples.length
    },
    preview: `${greetingPreview(samples)} (${source}, ${confidence.toLowerCase()} confidence)`
  };
};

export const analyzeManualStyleSamples = (text: string): StyleAnalysisResult =>
  buildProfile(splitSamples(text), 'Manual samples');

export const eligibleSentStyleMessages = (state: AppState): MessageDraft[] =>
  state.messages
    .filter(message => message.status === 'Sent' && message.body.trim().length >= 24)
    .sort((a, b) => (b.sentAt ?? b.scheduledFor ?? '').localeCompare(a.sentAt ?? a.scheduledFor ?? ''))
    .slice(0, 8);

export const analyzeSentMessageStyle = (state: AppState): StyleAnalysisResult => {
  const samples = eligibleSentStyleMessages(state).map(message => message.body);
  if (samples.length < 2) {
    return {
      ok: false,
      source: 'Recent sent messages',
      message: 'Send at least two messages before training from recent sent history.'
    };
  }
  return buildProfile(samples, 'Recent sent messages');
};
