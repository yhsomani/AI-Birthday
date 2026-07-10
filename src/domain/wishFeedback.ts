import type { MessageDraft, MessageRegenerationFeedback } from './types';

export type WishFeedbackOptionId =
  | 'more-personal'
  | 'warmer'
  | 'shorter'
  | 'more-formal'
  | 'less-generic'
  | 'avoid-emoji';

export type WishFeedbackOption = {
  id: WishFeedbackOptionId;
  label: string;
  instruction: string;
  detail: string;
  recommendedFor: Array<MessageDraft['quality']>;
};

export type WishFeedbackInput = {
  selectedOptionIds?: WishFeedbackOptionId[];
  customText?: string;
};

export type WishFeedbackPlan = {
  options: WishFeedbackOption[];
  selectedOptions: WishFeedbackOption[];
  customText: string;
  characterCount: number;
  improvementSummary: string;
  action: {
    enabled: boolean;
    detail: string;
  };
  warnings: string[];
  requestFeedback?: MessageRegenerationFeedback;
};

export const WISH_CUSTOM_FEEDBACK_MAX_LENGTH = 240;

export const wishFeedbackOptions: WishFeedbackOption[] = [
  {
    id: 'more-personal',
    label: 'More personal',
    instruction: 'Make the draft more specific to the relationship and approved memories.',
    detail: 'Ask for more relationship-specific wording.',
    recommendedFor: ['Needs more context', 'Template fallback']
  },
  {
    id: 'warmer',
    label: 'Warmer',
    instruction: 'Make the draft warmer and more emotionally natural.',
    detail: 'Increase warmth while keeping review-first wording.',
    recommendedFor: ['AI draft', 'Template fallback']
  },
  {
    id: 'shorter',
    label: 'Shorter',
    instruction: 'Make the draft shorter and easier to send.',
    detail: 'Reduce length without losing the main sentiment.',
    recommendedFor: ['AI draft', 'Template fallback', 'Needs more context']
  },
  {
    id: 'more-formal',
    label: 'More formal',
    instruction: 'Make the draft more formal and respectful.',
    detail: 'Use more polished wording for professional or elder recipients.',
    recommendedFor: ['AI draft', 'Template fallback']
  },
  {
    id: 'less-generic',
    label: 'Less generic',
    instruction: 'Avoid generic wishes and make the message feel less templated.',
    detail: 'Push away from stock greetings.',
    recommendedFor: ['Needs more context', 'Template fallback', 'AI draft']
  },
  {
    id: 'avoid-emoji',
    label: 'No emoji',
    instruction: 'Do not include emoji or decorative symbols.',
    detail: 'Keep the next draft plain-text.',
    recommendedFor: ['AI draft', 'Template fallback', 'Needs more context']
  }
];

const normalize = (value: string, maxLength: number) => value.trim().replace(/\s+/g, ' ').slice(0, maxLength);

const previousDraftExcerpt = (message: MessageDraft) => normalize(message.body, 220);

export const buildWishFeedbackPlan = (
  message: MessageDraft,
  input: WishFeedbackInput = {}
): WishFeedbackPlan => {
  const selectedIds = new Set(input.selectedOptionIds ?? []);
  const selectedOptions = wishFeedbackOptions.filter(option => selectedIds.has(option.id));
  const customText = normalize(input.customText ?? '', WISH_CUSTOM_FEEDBACK_MAX_LENGTH + 1);
  const customTooLong = customText.length > WISH_CUSTOM_FEEDBACK_MAX_LENGTH;
  const warnings = customTooLong
    ? [`Feedback must be ${WISH_CUSTOM_FEEDBACK_MAX_LENGTH} characters or less.`]
    : [];
  const hasGuidance = selectedOptions.length > 0 || customText.length > 0;
  const recommendedOptions = wishFeedbackOptions.filter(option => option.recommendedFor.includes(message.quality));
  const requestFeedback =
    hasGuidance && !customTooLong
      ? {
          instructions: selectedOptions.map(option => option.instruction),
          customInstruction: customText || undefined,
          previousDraftExcerpt: previousDraftExcerpt(message)
        }
      : undefined;
  const selectedLabels = selectedOptions.map(option => option.label.toLowerCase());
  const summaryParts = [
    ...selectedLabels,
    ...(customText && !customTooLong ? ['custom guidance'] : [])
  ];

  return {
    options: [
      ...recommendedOptions,
      ...wishFeedbackOptions.filter(option => !recommendedOptions.includes(option))
    ],
    selectedOptions,
    customText,
    characterCount: customText.length,
    improvementSummary:
      summaryParts.length > 0
        ? `Regeneration will ask for ${summaryParts.join(', ')} while keeping this draft review-first.`
        : 'Regeneration will keep the current contact tone and approved context. Add feedback when you want a more specific change.',
    action: {
      enabled: !customTooLong,
      detail: customTooLong
        ? 'Shorten the feedback before regenerating.'
        : hasGuidance
          ? 'Feedback will guide the next draft and be saved with the regenerated message.'
          : 'Regenerate without extra feedback, or select a chip to guide the next draft.'
    },
    warnings,
    requestFeedback
  };
};
