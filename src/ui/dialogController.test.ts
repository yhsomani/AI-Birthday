import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  createDialogController,
  nextEnabledDialogActionIndex,
  preferredDialogActionIndex,
  type ActiveDialog
} from './dialogController';

const confirmation = (id?: string) => ({
  id,
  title: 'Delete memory?',
  description: 'This removes the selected private memory from this device.',
  actions: [
    { id: 'cancel', label: 'Keep memory', role: 'cancel' as const },
    { id: 'delete', label: 'Delete memory', role: 'destructive' as const }
  ]
});

describe('dialog controller', () => {
  it('publishes an immutable dialog and resolves its selected action', async () => {
    const controller = createDialogController({ createId: () => 'dialog-delete' });
    let notifications = 0;
    controller.subscribe(() => {
      notifications += 1;
    });

    const resultPromise = controller.show(confirmation());
    const state = controller.getState();
    assert.equal(state.active?.id, 'dialog-delete');
    assert.equal(state.active?.title, 'Delete memory?');
    assert.equal(Object.isFrozen(state.active), true);
    assert.equal(Object.isFrozen(state.active?.actions), true);

    assert.equal(controller.chooseAction('delete'), true);
    assert.deepEqual(await resultPromise, {
      kind: 'action',
      dialogId: 'dialog-delete',
      actionId: 'delete',
      role: 'destructive'
    });
    assert.equal(controller.getState().active, null);
    assert.equal(notifications, 2);
  });

  it('queues simultaneous requests and advances in insertion order', async () => {
    let sequence = 0;
    const controller = createDialogController({ createId: () => `dialog-${++sequence}` });
    const first = controller.show(confirmation());
    const second = controller.show({
      title: 'Save changes?',
      description: 'Choose whether to keep the edited message.',
      actions: [{ id: 'save', label: 'Save' }]
    });

    assert.equal(controller.getState().active?.id, 'dialog-1');
    assert.deepEqual(controller.getState().queued.map(dialog => dialog.id), ['dialog-2']);
    controller.chooseAction('cancel');
    assert.equal(controller.getState().active?.id, 'dialog-2');
    controller.chooseAction('save');

    assert.equal((await first).kind, 'action');
    assert.equal((await second).kind, 'action');
  });

  it('honors non-dismissible dialogs for back and escape requests', async () => {
    const controller = createDialogController();
    const result = controller.show({ ...confirmation(), dismissible: false });

    assert.equal(controller.dismiss('back'), false);
    assert.equal(controller.dismiss('escape'), false);
    assert.ok(controller.getState().active);
    controller.chooseAction('cancel');
    assert.equal((await result).kind, 'action');
  });

  it('resolves accessible dismiss reasons and rejects stale or disabled actions', async () => {
    const controller = createDialogController();
    const result = controller.show({
      title: 'Unavailable action',
      description: 'The disabled action cannot be selected.',
      actions: [
        { id: 'disabled', label: 'Unavailable', disabled: true },
        { id: 'close', label: 'Close', role: 'cancel' }
      ]
    });

    assert.equal(controller.chooseAction('missing'), false);
    assert.equal(controller.chooseAction('disabled'), false);
    assert.equal(controller.dismiss('accessibility-escape'), true);
    assert.deepEqual(await result, {
      kind: 'dismissed',
      dialogId: 'dialog-1',
      reason: 'accessibility-escape'
    });
  });

  it('validates action identity, roles, focus preference, and usable controls', async () => {
    const invalidRequests = [
      { ...confirmation(), actions: [] },
      { ...confirmation(), actions: [{ id: 'same', label: 'One' }, { id: 'same', label: 'Two' }] },
      {
        ...confirmation(),
        actions: [
          { id: 'one', label: 'One', role: 'cancel' as const },
          { id: 'two', label: 'Two', role: 'cancel' as const }
        ]
      },
      { ...confirmation(), actions: [{ id: 'disabled', label: 'Disabled', disabled: true }] }
    ];
    for (const request of invalidRequests) {
      const controller = createDialogController();
      await assert.rejects(() => controller.show(request), /actions|unique|cancel|enabled/i);
    }
  });

  it('chooses safe initial focus and cycles only through enabled actions', () => {
    const dialog: ActiveDialog = {
      id: 'focus-dialog',
      title: 'Confirm',
      description: 'Choose an action.',
      dismissible: true,
      actions: [
        { id: 'disabled', label: 'Disabled', disabled: true, role: 'default' },
        { id: 'delete', label: 'Delete', role: 'destructive' },
        { id: 'cancel', label: 'Cancel', role: 'cancel' }
      ]
    };

    assert.equal(preferredDialogActionIndex(dialog), 2);
    assert.equal(nextEnabledDialogActionIndex(dialog, 2, 1), 1);
    assert.equal(nextEnabledDialogActionIndex(dialog, 1, 1), 2);
    assert.equal(nextEnabledDialogActionIndex(dialog, 1, -1), 2);
  });

  it('settles every pending request when its host is disposed', async () => {
    const controller = createDialogController();
    const first = controller.show(confirmation('first'));
    const second = controller.show(confirmation('second'));

    controller.dispose();

    assert.deepEqual(await first, { kind: 'dismissed', dialogId: 'first', reason: 'host-disposed' });
    assert.deepEqual(await second, { kind: 'dismissed', dialogId: 'second', reason: 'host-disposed' });
    await assert.rejects(() => controller.show(confirmation('third')), /disposed/i);
  });
});
