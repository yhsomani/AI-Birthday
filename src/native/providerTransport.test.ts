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

  it('cancels an oversized chunked body before calling an all-at-once text reader', async () => {
    let readCount = 0;
    let cancelled = false;
    let textCalled = false;
    const chunks = [new TextEncoder().encode('{"payload":"'), new Uint8Array(128), new TextEncoder().encode('"}')];
    const response = {
      ok: true,
      status: 200,
      headers: {
        get: (name: string) => (name.toLowerCase() === 'content-type' ? 'application/json' : null)
      },
      body: {
        getReader: () => ({
          read: async () => (readCount < chunks.length ? { done: false, value: chunks[readCount++] } : { done: true }),
          cancel: async () => {
            cancelled = true;
          }
        })
      },
      text: async () => {
        textCalled = true;
        throw new Error('streaming path must not allocate the complete body');
      }
    };

    assert.deepEqual(await readBoundedJsonResponse(response, 64), { ok: false, reason: 'body-too-large' });
    assert.equal(cancelled, true);
    assert.equal(textCalled, false);
    assert.equal(readCount, 2);
  });

  it('requires a declared bound from compatibility transports without a readable body', async () => {
    const response = staticJsonResponse({ ok: true });
    response.headers.get = name => (name.toLowerCase() === 'content-type' ? 'application/json' : null);
    assert.deepEqual(await readBoundedJsonResponse(response, 1024), {
      ok: false,
      reason: 'content-length'
    });
  });

  it('rejects malformed JSON without exposing its body', async () => {
    const response = staticJsonResponse({});
    response.text = async () => '{broken';
    response.headers.get = name => {
      if (name.toLowerCase() === 'content-type') return 'application/json';
      if (name.toLowerCase() === 'content-length') return '7';
      return null;
    };
    assert.deepEqual(await readBoundedJsonResponse(response, 1024), {
      ok: false,
      reason: 'invalid-json'
    });
  });
});
