import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildAllowsLocalProviderEndpoints } from './providerDevelopmentMode';

describe('provider development build boundary', () => {
  it('never lets a public environment flag enable local endpoints in a release build', () => {
    assert.equal(buildAllowsLocalProviderEndpoints('true', false), false);
  });

  it('requires both a development build and an explicit local-endpoint flag', () => {
    assert.equal(buildAllowsLocalProviderEndpoints('false', true), false);
    assert.equal(buildAllowsLocalProviderEndpoints(undefined, true), false);
    assert.equal(buildAllowsLocalProviderEndpoints(' TRUE ', true), true);
  });

  it('fails closed when the compile-time development global is absent', () => {
    assert.equal(buildAllowsLocalProviderEndpoints('true'), false);
  });
});
