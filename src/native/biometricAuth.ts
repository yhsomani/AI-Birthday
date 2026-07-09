import * as LocalAuthentication from 'expo-local-authentication';

export interface BiometricCapability {
  hardwareAvailable: boolean;
  enrolled: boolean;
}

export const readBiometricCapability = async (): Promise<BiometricCapability> => {
  const [hardwareAvailable, enrolled] = await Promise.all([
    LocalAuthentication.hasHardwareAsync(),
    LocalAuthentication.isEnrolledAsync()
  ]);
  return {
    hardwareAvailable,
    enrolled
  };
};

export const authenticateWithBiometrics = async () => {
  const result = await LocalAuthentication.authenticateAsync({
    promptMessage: 'Unlock RelateAI',
    cancelLabel: 'Not now',
    disableDeviceFallback: false
  });
  return result.success;
};
