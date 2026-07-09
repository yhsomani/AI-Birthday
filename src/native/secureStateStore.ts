import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import type { KeyValueStore } from '../state/persistence';

const fallbackPrefix = 'fallback.';

export const secureStateStore: KeyValueStore = {
  async getItem(key: string) {
    try {
      if (await SecureStore.isAvailableAsync()) {
        const value = await SecureStore.getItemAsync(key);
        if (value !== null) {
          return value;
        }
      }
    } catch {
      // Fallback below keeps the app usable on platforms without SecureStore.
    }
    return AsyncStorage.getItem(`${fallbackPrefix}${key}`);
  },
  async setItem(key: string, value: string) {
    try {
      if (await SecureStore.isAvailableAsync()) {
        await SecureStore.setItemAsync(key, value, {
          keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY
        });
        await AsyncStorage.removeItem(`${fallbackPrefix}${key}`);
        return;
      }
    } catch {
      // Fallback below keeps web/simulator flows working while surfacing status in UI.
    }
    await AsyncStorage.setItem(`${fallbackPrefix}${key}`, value);
  },
  async removeItem(key: string) {
    try {
      if (await SecureStore.isAvailableAsync()) {
        await SecureStore.deleteItemAsync(key);
      }
    } catch {
      // Continue clearing fallback storage.
    }
    await AsyncStorage.removeItem(`${fallbackPrefix}${key}`);
  }
};
