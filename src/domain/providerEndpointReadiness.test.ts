import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { evaluateProviderEndpointReadiness } from './providerEndpointReadiness';

describe('provider endpoint readiness contract', () => {
  it('marks HTTPS endpoints without embedded credentials as production-ready', () => {
    const readiness = evaluateProviderEndpointReadiness('https://provider.example.com/draft');

    assert.equal(readiness.configured, true);
    assert.equal(readiness.canUseProviderEndpoint, true);
    assert.equal(readiness.productionReady, true);
    assert.equal(readiness.status, 'Ready');
    assert.doesNotMatch(readiness.summary, /provider\.example\.com|draft/);
  });

  it('keeps missing and malformed endpoints redacted and unusable', () => {
    const missing = evaluateProviderEndpointReadiness(undefined);
    const malformed = evaluateProviderEndpointReadiness('not a url');

    assert.equal(missing.status, 'Missing');
    assert.equal(missing.canUseProviderEndpoint, false);
    assert.equal(malformed.status, 'Blocked');
    assert.equal(malformed.issue, 'invalid-url');
    assert.doesNotMatch(JSON.stringify({ missing, malformed }), /not a url/);
  });

  it('blocks production use of insecure, credentialed, and local endpoints', () => {
    const insecure = evaluateProviderEndpointReadiness('http://provider.example.com/send');
    const credentialed = evaluateProviderEndpointReadiness('https://user:secret@provider.example.com/send');
    const local = evaluateProviderEndpointReadiness('http://localhost:8787/draft');

    assert.equal(insecure.issue, 'insecure-protocol');
    assert.equal(credentialed.issue, 'credentials-in-url');
    assert.equal(local.issue, 'private-network');
    assert.equal(insecure.canUseProviderEndpoint, false);
    assert.equal(credentialed.canUseProviderEndpoint, false);
    assert.equal(local.canUseProviderEndpoint, false);
    assert.doesNotMatch(JSON.stringify({ insecure, credentialed, local }), /secret|provider\.example\.com|localhost/);
  });

  it('blocks link-local, reserved, private IPv6, and alternate numeric localhost forms', () => {
    const endpoints = [
      'https://169.254.10.2/send',
      'https://100.64.0.1/send',
      'https://[fe80::1]/send',
      'https://[fd00::1]/send',
      'https://2130706433/send',
      'https://0x7f000001/send'
    ];

    for (const endpoint of endpoints) {
      const readiness = evaluateProviderEndpointReadiness(endpoint);
      assert.equal(readiness.status, 'Blocked');
      assert.equal(readiness.issue, 'private-network');
      assert.equal(readiness.canUseProviderEndpoint, false);
      assert.doesNotMatch(readiness.summary, /169\.254|100\.64|fe80|fd00|2130706433|7f000001/);
    }
  });

  it('allows explicitly approved local endpoints only as development-only', () => {
    const readiness = evaluateProviderEndpointReadiness('http://192.168.1.12:8787/draft', {
      allowLocalDevelopment: true
    });

    assert.equal(readiness.status, 'Development only');
    assert.equal(readiness.canUseProviderEndpoint, true);
    assert.equal(readiness.productionReady, false);
    assert.doesNotMatch(readiness.summary, /192\.168|8787|draft/);
  });
});
