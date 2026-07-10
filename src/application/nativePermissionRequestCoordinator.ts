import { requestSystemPermission } from '../native/permissionRequest';
import {
  PermissionRequestCoordinator,
  type PermissionRequestCoordinatorDependencies
} from './permissionRequestCoordinator';

export type NativePermissionRequestCoordinatorHooks = Omit<
  PermissionRequestCoordinatorDependencies,
  'requestPermission'
>;

export const createNativePermissionRequestCoordinator = (
  hooks: NativePermissionRequestCoordinatorHooks = {}
): PermissionRequestCoordinator =>
  new PermissionRequestCoordinator({
    ...hooks,
    requestPermission: requestSystemPermission
  });
