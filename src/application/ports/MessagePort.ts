import type {
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
  generateSuggestions(
    request: GeminiRequest,
  ): Promise<NativeResult<GeminiSuggestionsProjection>>;
}
