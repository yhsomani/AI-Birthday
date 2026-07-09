export interface BiometricInputs {
  enabled: boolean;
  hardwareAvailable: boolean;
  enrolled: boolean;
  sessionUnlocked: boolean;
}

export type BiometricDecision =
  | {
      state: 'unlocked';
      reason: 'disabled' | 'already-unlocked';
    }
  | {
      state: 'locked';
      reason: 'requires-authentication';
    }
  | {
      state: 'unavailable';
      reason: 'no-hardware' | 'not-enrolled';
    };

export const resolveBiometricLock = (inputs: BiometricInputs): BiometricDecision => {
  if (!inputs.enabled) {
    return { state: 'unlocked', reason: 'disabled' };
  }
  if (!inputs.hardwareAvailable) {
    return { state: 'unavailable', reason: 'no-hardware' };
  }
  if (!inputs.enrolled) {
    return { state: 'unavailable', reason: 'not-enrolled' };
  }
  if (inputs.sessionUnlocked) {
    return { state: 'unlocked', reason: 'already-unlocked' };
  }
  return { state: 'locked', reason: 'requires-authentication' };
};
