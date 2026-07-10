import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import { createProtectedStateStore } from './secureStateStoreCore';

export * from './secureStateStoreCore';

export const secureStateStore = createProtectedStateStore({
  protectedBackend: SecureStore,
  legacyInventory: AsyncStorage,
  keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY
});
