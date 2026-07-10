import type { AppState, MemoryCategory, MemoryNote } from './types';

export const MEMORY_NOTE_MAX_LENGTH = 500;

export type MemoryVaultValidationCode = 'missing-contact' | 'blank-note' | 'too-long';

export type MemoryVaultValidationResult =
  | {
      ok: true;
      value: {
        body: string;
      };
    }
  | {
      ok: false;
      code: MemoryVaultValidationCode;
      message: string;
    };

export interface MemoryVaultItem {
  note: MemoryNote;
  aiUseLabel: string;
}

export interface MemoryVaultReport {
  contactExists: boolean;
  query: string;
  totalCount: number;
  visibleCount: number;
  pinnedCount: number;
  privateCount: number;
  aiEligibleCount: number;
  notes: MemoryVaultItem[];
  emptyMessage?: string;
}

const normalizeBody = (body: string) => body.trim().replace(/\s+/g, ' ');

const categoryAiUseLabel = (category: MemoryCategory) =>
  category === 'Private'
    ? 'Private note: excluded from AI drafts, Gift Advisor prompts, and provider context.'
    : 'AI-eligible note: can improve drafts and Gift Advisor suggestions when AI is enabled.';

const compareMemoryNotes = (left: MemoryNote, right: MemoryNote) => {
  if (left.pinned !== right.pinned) {
    return left.pinned ? -1 : 1;
  }
  return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
};

export const validateMemoryNoteInput = (
  state: AppState,
  contactId: string,
  body: string
): MemoryVaultValidationResult => {
  if (!state.contacts.some(contact => contact.id === contactId)) {
    return {
      ok: false,
      code: 'missing-contact',
      message: 'This contact is no longer available. Return to Contacts and choose an active contact.'
    };
  }

  const normalized = normalizeBody(body);
  if (normalized.length === 0) {
    return {
      ok: false,
      code: 'blank-note',
      message: 'Write a note before saving it.'
    };
  }

  if (normalized.length > MEMORY_NOTE_MAX_LENGTH) {
    return {
      ok: false,
      code: 'too-long',
      message: `Keep memory notes at ${MEMORY_NOTE_MAX_LENGTH} characters or fewer.`
    };
  }

  return {
    ok: true,
    value: {
      body: normalized
    }
  };
};

export const buildMemoryVaultReport = (
  state: AppState,
  contactId: string,
  query = ''
): MemoryVaultReport => {
  const contactExists = state.contacts.some(contact => contact.id === contactId);
  const normalizedQuery = query.trim().toLowerCase();
  const allNotes = state.memories.filter(memory => memory.contactId === contactId);
  const visibleNotes = allNotes
    .filter(memory => {
      if (!normalizedQuery) {
        return true;
      }
      return `${memory.category} ${memory.body}`.toLowerCase().includes(normalizedQuery);
    })
    .sort(compareMemoryNotes);
  const privateCount = allNotes.filter(memory => memory.category === 'Private').length;
  const aiEligibleCount = allNotes.length - privateCount;
  const emptyMessage = !contactExists
    ? 'This contact is no longer available. Memory notes are hidden until an active contact is selected.'
    : allNotes.length === 0
      ? 'No memories saved yet. Add a preference, milestone, thing to mention, or thing to avoid.'
      : visibleNotes.length === 0
        ? `No memories match "${query.trim()}".`
        : undefined;

  return {
    contactExists,
    query: query.trim(),
    totalCount: allNotes.length,
    visibleCount: visibleNotes.length,
    pinnedCount: allNotes.filter(memory => memory.pinned).length,
    privateCount,
    aiEligibleCount,
    notes: visibleNotes.map(note => ({
      note,
      aiUseLabel: categoryAiUseLabel(note.category)
    })),
    emptyMessage
  };
};
