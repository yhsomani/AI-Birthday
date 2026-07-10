import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { readBoundedJsonResponse, staticJsonResponse } from './providerTransport';

describe('bounded provider response transport', () => {
  it('accepts bounded JSON and application/problem+json', async () => {
    const result = await readBoundedJsonResponse(
      staticJsonResponse({ ok: true }, { contentType: 'application/problem+json; charset=utf-8' }),
      1024
    );
    assert.deepEqual(result, { ok: true, value: { ok: true } });
  });

  it('rejects unexpected content types before parsing', async () => {
    const result = await readBoundedJsonResponse(staticJsonResponse({ ok: true }, { contentType: 'text/html' }), 1024);
    assert.deepEqual(result, { ok: false, reason: 'content-type' });
  });

  it('rejects invalid or oversized declared and actual response bodies', async () => {
    const declared = await readBoundedJsonResponse(staticJsonResponse({}, { contentLength: '5000' }), 100);
    assert.deepEqual(declared, { ok: false, reason: 'content-length' });

    const actual = staticJsonResponse({ payload: 'x'.repeat(100) }, { contentLength: '1' });
    assert.deepEqual(await readBoundedJsonResponse(actual, 32), {
      ok: false,
      reason: 'body-too-large'
    });
  });

  it('rejects malformed JSON without exposing its body', async () => {
    const response = staticJsonResponse({});
    response.text = async () => '{broken';
    response.headers.get = name => (name.toLowerCase() === 'content-type' ? 'application/json' : null);
    assert.deepEqual(await readBoundedJsonResponse(response, 1024), {
      ok: false,
      reason: 'invalid-json'
    });
  });
});
