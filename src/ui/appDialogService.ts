import { createDialogController, type DialogActionRole } from './dialogController';

export type AppAlertButton = {
  text?: string;
  onPress?: () => void | Promise<void>;
  style?: 'default' | 'cancel' | 'destructive';
};

export const appDialogController = createDialogController();

const roleFor = (style: AppAlertButton['style']): DialogActionRole =>
  style === 'cancel' ? 'cancel' : style === 'destructive' ? 'destructive' : 'default';

const showActionFailure = () => {
  void appDialogController.show({
    title: 'Action could not be completed',
    description: 'Nothing else was changed. Review the current state and try again.',
    actions: [{ id: 'dismiss', label: 'Dismiss', role: 'cancel', preferredFocus: true }]
  });
};

/**
 * Compatibility entry point for migrated Alert.alert call sites. Unlike the
 * native Alert API, this is rendered by AppDialogHost on Android, iOS, and web.
 */
export const showAppAlert = (
  title: string,
  description: string,
  buttons: readonly AppAlertButton[] = [{ text: 'OK', style: 'cancel' }]
): void => {
  const actions = (buttons.length > 0 ? buttons : [{ text: 'OK', style: 'cancel' as const }]).map(
    (button, index) => ({
      id: `action-${index}`,
      label: button.text?.trim() || 'OK',
      role: roleFor(button.style),
      preferredFocus: button.style === 'cancel'
    })
  );

  void appDialogController
    .show({
      title,
      description,
      actions,
      dismissible: true
    })
    .then(result => {
      if (result.kind !== 'action') return;
      const index = Number(result.actionId.slice('action-'.length));
      const callback = buttons[index]?.onPress;
      if (callback) {
        void Promise.resolve()
          .then(callback)
          .catch(showActionFailure);
      }
    })
    .catch(showActionFailure);
};
