import {
  PermissionReminderCoordinator,
  type PermissionReminderCoordinatorDependencies
} from './permissionReminderCoordinator';
import { refreshPermissionSnapshot } from '../native/permissionStatus';
import { reconcileReminderPlansWithoutPrompt } from '../native/reminderScheduler';

export type NativePermissionReminderCoordinatorHooks = Omit<
  PermissionReminderCoordinatorDependencies,
  'readPermissionSnapshot' | 'reconcileReminderNotifications'
>;

/** Production wiring kept outside the testable application coordinator. */
export const createNativePermissionReminderCoordinator = (hooks: NativePermissionReminderCoordinatorHooks = {}) =>
  new PermissionReminderCoordinator({
    ...hooks,
    readPermissionSnapshot: refreshPermissionSnapshot,
    reconcileReminderNotifications: reconcileReminderPlansWithoutPrompt
  });
