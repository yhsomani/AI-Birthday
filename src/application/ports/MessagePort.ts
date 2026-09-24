import type {
  AIMessageSuggestionRequest,
  AIMessageSuggestionResult,
  GeminiRequest,
  GeminiSuggestionsProjection,
  MessageDraft,
  MessageEditorProjection,
  MessagePreview,
} from '../../domain/messages/model';
import type {
  MessagePreviewHandle,
  NativeRevision,
} from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';

export type SavedMessageProjection = Readonly<{
  draft: MessageDraft;
  affectedRecipientCount: number;
  invalidatedApprovalCount: number;
}>;

export interface MessagePort {
  getMessageEditor(): Promise<NativeResult<MessageEditorProjection>>;
  previewMessage(input: {
    draft: MessageDraft;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<MessagePreview>>;
  saveMessage(input: {
    handle: MessagePreviewHandle;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<SavedMessageProjection>>;
  /**
   * Provider-agnostic AI drafting entry point. The native gateway performs the
   * entitlement check (app subscription + quota) BEFORE any provider call and
   * routes to whichever provider adapter is authorised (application-owned
   * Gemini today; provider sign-in / on-device adapters later). Callers never
   * select a provider here, and users never handle API keys — end-user
   * provider authorisation is OAuth sign-in only.
   */
  generateSuggestions(
    request: AIMessageSuggestionRequest,
  ): Promise<NativeResult<AIMessageSuggestionResult>>;
}
