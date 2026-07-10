export type DialogActionRole = 'default' | 'cancel' | 'destructive';
export type DialogDismissReason = 'back' | 'escape' | 'accessibility-escape' | 'programmatic' | 'host-disposed';

export interface DialogAction {
  id: string;
  label: string;
  role?: DialogActionRole;
  disabled?: boolean;
  accessibilityHint?: string;
  preferredFocus?: boolean;
}

export interface DialogRequest {
  id?: string;
  title: string;
  description: string;
  actions: readonly DialogAction[];
  dismissible?: boolean;
}

export interface ActiveDialog {
  id: string;
  title: string;
  description: string;
  actions: readonly Readonly<DialogAction>[];
  dismissible: boolean;
}

export interface DialogControllerState {
  active: ActiveDialog | null;
  queued: readonly ActiveDialog[];
  revision: number;
}

export type DialogResult =
  | {
      kind: 'action';
      dialogId: string;
      actionId: string;
      role: DialogActionRole;
    }
  | {
      kind: 'dismissed';
      dialogId: string;
      reason: DialogDismissReason;
    };

export interface DialogController {
  show(request: DialogRequest): Promise<DialogResult>;
  chooseAction(actionId: string): boolean;
  dismiss(reason?: Exclude<DialogDismissReason, 'host-disposed'>): boolean;
  getState(): DialogControllerState;
  subscribe(listener: () => void): () => void;
  dispose(): void;
}

export interface DialogControllerOptions {
  createId?: () => string;
  onSubscriberError?: (error: unknown) => void;
}

const MAX_DIALOG_TITLE_LENGTH = 200;
const MAX_DIALOG_DESCRIPTION_LENGTH = 4_000;
const MAX_DIALOG_ACTION_LABEL_LENGTH = 160;
const MAX_DIALOG_ACTION_HINT_LENGTH = 300;
const MAX_DIALOG_ACTIONS = 6;

const requireBoundedText = (value: string, field: string, maximum: number): string => {
  const normalized = value.trim();
  if (normalized.length === 0) {
    throw new Error(`Dialog ${field} is required.`);
  }
  if (normalized.length > maximum) {
    throw new Error(`Dialog ${field} is too long.`);
  }
  return normalized;
};

const normalizeRequest = (request: DialogRequest, id: string): ActiveDialog => {
  if (!Array.isArray(request.actions) || request.actions.length < 1 || request.actions.length > MAX_DIALOG_ACTIONS) {
    throw new Error(`Dialog actions must contain between 1 and ${MAX_DIALOG_ACTIONS} items.`);
  }

  const actionIds = new Set<string>();
  let cancelActionCount = 0;
  let preferredFocusCount = 0;
  const actions = request.actions.map(action => {
    const actionId = requireBoundedText(action.id, 'action id', 100);
    if (actionIds.has(actionId)) {
      throw new Error('Dialog action ids must be unique.');
    }
    actionIds.add(actionId);

    const role = action.role ?? 'default';
    if (role !== 'default' && role !== 'cancel' && role !== 'destructive') {
      throw new Error('Dialog action role is invalid.');
    }
    if (role === 'cancel') {
      cancelActionCount += 1;
    }
    if (action.preferredFocus === true) {
      preferredFocusCount += 1;
    }
    return Object.freeze({
      id: actionId,
      label: requireBoundedText(action.label, 'action label', MAX_DIALOG_ACTION_LABEL_LENGTH),
      role,
      disabled: action.disabled === true,
      ...(action.accessibilityHint
        ? {
            accessibilityHint: requireBoundedText(
              action.accessibilityHint,
              'action accessibility hint',
              MAX_DIALOG_ACTION_HINT_LENGTH
            )
          }
        : {}),
      preferredFocus: action.preferredFocus === true
    });
  });

  if (cancelActionCount > 1) {
    throw new Error('A dialog can contain at most one cancel action.');
  }
  if (preferredFocusCount > 1) {
    throw new Error('A dialog can contain at most one preferred focus action.');
  }
  if (actions.every(action => action.disabled)) {
    throw new Error('A dialog must contain at least one enabled action.');
  }

  return Object.freeze({
    id,
    title: requireBoundedText(request.title, 'title', MAX_DIALOG_TITLE_LENGTH),
    description: requireBoundedText(request.description, 'description', MAX_DIALOG_DESCRIPTION_LENGTH),
    actions: Object.freeze(actions),
    dismissible: request.dismissible !== false
  });
};

export const preferredDialogActionIndex = (dialog: ActiveDialog): number => {
  const explicitIndex = dialog.actions.findIndex(action => action.preferredFocus && !action.disabled);
  if (explicitIndex >= 0) {
    return explicitIndex;
  }
  const cancelIndex = dialog.actions.findIndex(action => action.role === 'cancel' && !action.disabled);
  if (cancelIndex >= 0) {
    return cancelIndex;
  }
  const nonDestructiveIndex = dialog.actions.findIndex(
    action => action.role !== 'destructive' && !action.disabled
  );
  if (nonDestructiveIndex >= 0) {
    return nonDestructiveIndex;
  }
  return dialog.actions.findIndex(action => !action.disabled);
};

export const nextEnabledDialogActionIndex = (
  dialog: ActiveDialog,
  currentIndex: number,
  direction: 1 | -1
): number => {
  if (dialog.actions.length === 0) {
    return -1;
  }
  for (let offset = 1; offset <= dialog.actions.length; offset += 1) {
    const index = (currentIndex + direction * offset + dialog.actions.length) % dialog.actions.length;
    if (!dialog.actions[index].disabled) {
      return index;
    }
  }
  return -1;
};

export const createDialogController = (options: DialogControllerOptions = {}): DialogController => {
  let sequence = 0;
  const createId = options.createId ?? (() => `dialog-${++sequence}`);
  const listeners = new Set<() => void>();
  const pending = new Map<string, (result: DialogResult) => void>();
  let disposed = false;
  let state: DialogControllerState = Object.freeze({ active: null, queued: Object.freeze([]), revision: 0 });

  const emit = () => {
    for (const listener of [...listeners]) {
      try {
        listener();
      } catch (error) {
        try {
          options.onSubscriberError?.(error);
        } catch {
          // A diagnostics callback cannot compromise dialog resolution.
        }
      }
    }
  };

  const publish = (active: ActiveDialog | null, queued: readonly ActiveDialog[]) => {
    state = Object.freeze({
      active,
      queued: Object.freeze([...queued]),
      revision: state.revision + 1
    });
    emit();
  };

  const completeActive = (result: DialogResult) => {
    const completed = state.active;
    if (!completed) {
      return false;
    }
    const [next, ...remaining] = state.queued;
    publish(next ?? null, remaining);
    const resolve = pending.get(completed.id);
    pending.delete(completed.id);
    resolve?.(result);
    return true;
  };

  return {
    show(request) {
      if (disposed) {
        return Promise.reject(new Error('Dialog controller has been disposed.'));
      }
      const id = request.id?.trim() || createId();
      if (id.length > 100) {
        return Promise.reject(new Error('Dialog id is too long.'));
      }
      if (pending.has(id)) {
        return Promise.reject(new Error('Dialog id is already active or queued.'));
      }

      let dialog: ActiveDialog;
      try {
        dialog = normalizeRequest(request, id);
      } catch (error) {
        return Promise.reject(error);
      }

      return new Promise<DialogResult>(resolve => {
        pending.set(id, resolve);
        if (state.active) {
          publish(state.active, [...state.queued, dialog]);
        } else {
          publish(dialog, state.queued);
        }
      });
    },

    chooseAction(actionId) {
      const active = state.active;
      if (!active) {
        return false;
      }
      const action = active.actions.find(candidate => candidate.id === actionId);
      if (!action || action.disabled) {
        return false;
      }
      return completeActive({
        kind: 'action',
        dialogId: active.id,
        actionId: action.id,
        role: action.role ?? 'default'
      });
    },

    dismiss(reason = 'programmatic') {
      const active = state.active;
      if (!active || !active.dismissible) {
        return false;
      }
      return completeActive({ kind: 'dismissed', dialogId: active.id, reason });
    },

    getState() {
      return state;
    },

    subscribe(listener) {
      if (disposed) {
        return () => undefined;
      }
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },

    dispose() {
      if (disposed) {
        return;
      }
      disposed = true;
      const dialogs = [state.active, ...state.queued].filter((dialog): dialog is ActiveDialog => dialog !== null);
      publish(null, []);
      for (const dialog of dialogs) {
        pending.get(dialog.id)?.({ kind: 'dismissed', dialogId: dialog.id, reason: 'host-disposed' });
        pending.delete(dialog.id);
      }
      listeners.clear();
    }
  };
};
