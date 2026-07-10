import { resolveContactPreferencesForContact } from './contactPreferences';
import type { AppState, Contact, GiftCategory, GiftRecord } from './types';

export type GiftBudgetFit = 'Within budget' | 'Over budget' | 'No budget set';
export type GiftSuggestionConfidence = 'Low' | 'Medium' | 'High';

export const giftCategories: GiftCategory[] = ['Experience', 'Food', 'Books', 'Wellness', 'Personal', 'Other'];
export const giftFeedbackOptions: GiftRecord['feedback'][] = ['Unknown', 'Liked', 'Disliked'];

export interface GiftAdvisorInput {
  name: string;
  category: GiftCategory;
  occasion: string;
  cost: number;
  notes?: string;
  feedback?: GiftRecord['feedback'];
}

export interface GiftBudgetSummary {
  annualBudget: number;
  spentThisYear: number;
  remaining: number;
  recordedGiftCount: number;
  overBudget: boolean;
}

export interface GiftBudgetInput {
  annualGiftBudget: number | string;
}

export interface GiftSuggestion {
  id: string;
  name: string;
  category: GiftCategory;
  occasion: string;
  estimatedCost: number;
  budgetFit: GiftBudgetFit;
  confidence: GiftSuggestionConfidence;
  rationale: string;
  duplicateWarning?: string;
}

export type GiftValidationResult = { ok: true; value: GiftAdvisorInput } | { ok: false; message: string };

export type GiftBudgetValidationResult = { ok: true; value: number } | { ok: false; message: string };

const currentYear = () => new Date().getFullYear();

const normalize = (value: string) => value.trim().replace(/\s+/g, ' ');

const words = (value: string) =>
  normalize(value)
    .toLowerCase()
    .split(/[^a-z0-9]+/i)
    .filter(word => word.length >= 4);

const hasOverlap = (a: string, b: string) => {
  const left = new Set(words(a));
  return words(b).some(word => left.has(word));
};

const budgetFitFor = (remaining: number, annualBudget: number, cost: number): GiftBudgetFit =>
  annualBudget <= 0 ? 'No budget set' : cost <= remaining ? 'Within budget' : 'Over budget';

export const buildGiftBudgetSummary = (
  contact: Contact,
  gifts: GiftRecord[],
  year = currentYear()
): GiftBudgetSummary => {
  const spentThisYear = gifts
    .filter(gift => gift.contactId === contact.id && gift.year === year)
    .reduce((sum, gift) => sum + gift.cost, 0);
  const remaining = Math.max(0, contact.annualGiftBudget - spentThisYear);
  return {
    annualBudget: contact.annualGiftBudget,
    spentThisYear,
    remaining,
    recordedGiftCount: gifts.filter(gift => gift.contactId === contact.id).length,
    overBudget: contact.annualGiftBudget > 0 && spentThisYear > contact.annualGiftBudget
  };
};

export const validateGiftInput = (input: GiftAdvisorInput): GiftValidationResult => {
  const name = normalize(input.name);
  const occasion = normalize(input.occasion);
  const notes = normalize(input.notes ?? '');
  if (name.length < 2) {
    return { ok: false, message: 'Gift name is required.' };
  }
  if (!giftCategories.includes(input.category)) {
    return { ok: false, message: 'Choose a gift category before saving.' };
  }
  if (occasion.length < 2) {
    return { ok: false, message: 'Gift occasion is required.' };
  }
  if (!Number.isFinite(input.cost) || input.cost < 0 || input.cost > 1000000) {
    return { ok: false, message: 'Gift cost must be a valid non-negative amount.' };
  }
  if (name.length > 120 || occasion.length > 120 || notes.length > 500) {
    return { ok: false, message: 'Gift details are too long.' };
  }
  return {
    ok: true,
    value: {
      ...input,
      name,
      occasion,
      notes,
      feedback: input.feedback ?? 'Unknown'
    }
  };
};

export const validateGiftBudgetInput = (input: GiftBudgetInput): GiftBudgetValidationResult => {
  const budget =
    typeof input.annualGiftBudget === 'number'
      ? input.annualGiftBudget
      : Number(String(input.annualGiftBudget).trim() || 0);

  if (!Number.isFinite(budget) || budget < 0 || budget > 500000) {
    return { ok: false, message: 'Gift budget must be between 0 and 500000.' };
  }

  return { ok: true, value: Math.round(budget) };
};

const suggestionDuplicateWarning = (
  suggestion: Pick<GiftSuggestion, 'name' | 'category' | 'occasion'>,
  gifts: GiftRecord[]
) => {
  const nameDuplicate = gifts.find(gift => hasOverlap(suggestion.name, gift.name));
  if (nameDuplicate) {
    return `Similar to previous gift: ${nameDuplicate.name}.`;
  }

  const sameOccasionCategory = gifts.find(
    gift =>
      gift.category === suggestion.category &&
      normalize(gift.occasion).toLowerCase() === normalize(suggestion.occasion).toLowerCase()
  );
  return sameOccasionCategory
    ? `Similar ${suggestion.category.toLowerCase()} gift already used for ${sameOccasionCategory.occasion}: ${sameOccasionCategory.name}.`
    : undefined;
};

export const buildGiftSuggestions = (state: AppState, contactId: string, occasion = 'Next event'): GiftSuggestion[] => {
  const contact = state.contacts.find(item => item.id === contactId);
  if (!contact) {
    return [];
  }

  const gifts = state.gifts.filter(gift => gift.contactId === contactId);
  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const budget = buildGiftBudgetSummary(contact, state.gifts);
  const nonPrivateMemoryText = state.memories
    .filter(memory => memory.contactId === contactId && memory.category !== 'Private')
    .map(memory => memory.body)
    .join(' ');
  const context = `${contact.notesSummary} ${nonPrivateMemoryText}`.toLowerCase();
  const hasRichContext = normalize(nonPrivateMemoryText).length > 0 || contact.notesSummary.length > 24;
  const confidence: GiftSuggestionConfidence =
    gifts.length > 0 && hasRichContext ? 'High' : hasRichContext ? 'Medium' : 'Low';
  const baseCost = budget.annualBudget > 0 ? Math.max(500, Math.round(Math.max(1, budget.remaining) / 2)) : 1000;
  const suggestions: Omit<GiftSuggestion, 'id' | 'budgetFit' | 'confidence' | 'duplicateWarning'>[] = [];

  if (/book|read|novel/.test(context)) {
    suggestions.push({
      name: 'Curated book and coffee bundle',
      category: 'Books',
      occasion,
      estimatedCost: Math.min(baseCost, 1800),
      rationale: 'Uses known reading or coffee preferences without exposing private notes.'
    });
  }
  if (/coffee|tea|food|mango|lassi/.test(context)) {
    suggestions.push({
      name: 'Favorite cafe or treat experience',
      category: 'Food',
      occasion,
      estimatedCost: Math.min(baseCost, 1500),
      rationale: 'Fits food and drink preferences already saved for this contact.'
    });
  }
  if (contact.group === 'Work' || preferences.tone.includes('Formal')) {
    suggestions.push({
      name: 'Premium desk notebook and pen set',
      category: 'Other',
      occasion,
      estimatedCost: Math.min(baseCost, 2200),
      rationale: 'Keeps the gift professional and practical for a work relationship.'
    });
  }
  suggestions.push({
    name: contact.group === 'Family' ? 'Personal photo memory frame' : 'Personalized note and small keepsake',
    category: 'Personal',
    occasion,
    estimatedCost: Math.min(baseCost, 2000),
    rationale: hasRichContext
      ? 'Uses relationship context while avoiding private memories.'
      : 'A low-risk personal option when gift history is sparse.'
  });

  return suggestions.slice(0, 3).map((suggestion, index) => ({
    ...suggestion,
    id: `gift-suggestion-${contactId}-${index + 1}`,
    budgetFit: budgetFitFor(budget.remaining, budget.annualBudget, suggestion.estimatedCost),
    confidence,
    duplicateWarning: suggestionDuplicateWarning(suggestion, gifts)
  }));
};
