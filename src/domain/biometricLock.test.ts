import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { resolveBiometricLock } from './biometricLock';

describe('biometric lock policy', () => {
  it('does not lock when biometric lock is disabled', () => {
    const decision = resolveBiometricLock({
      enabled: false,
      hardwareAvailable: false,
      enrolled: false,
      sessionUnlocked: false
    });

    assert.equal(decision.state, 'unlocked');
    assert.equal(decision.reason, 'disabled');
  });

  it('reports unavailable hardware before requesting authentication', () => {
    const decision = resolveBiometricLock({
      enabled: true,
      hardwareAvailable: false,
      enrolled: true,
      sessionUnlocked: false
    });

    assert.equal(decision.state, 'unavailable');
    assert.equal(decision.reason, 'no-hardware');
  });

  it('reports missing enrollment before requesting authentication', () => {
    const decision = resolveBiometricLock({
      enabled: true,
      hardwareAvailable: true,
      enrolled: false,
      sessionUnlocked: false
    });

    assert.equal(decision.state, 'unavailable');
    assert.equal(decision.reason, 'not-enrolled');
  });

  it('locks when enabled and the session is not unlocked', () => {
    const decision = resolveBiometricLock({
      enabled: true,
      hardwareAvailable: true,
      enrolled: true,
      sessionUnlocked: false
    });

    assert.equal(decision.state, 'locked');
  });

  it('stays unlocked after successful session authentication', () => {
    const decision = resolveBiometricLock({
      enabled: true,
      hardwareAvailable: true,
      enrolled: true,
      sessionUnlocked: true
    });

    assert.equal(decision.state, 'unlocked');
    assert.equal(decision.reason, 'already-unlocked');
  });
});
