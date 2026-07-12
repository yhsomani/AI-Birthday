import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const read = path => readFileSync(resolve(root, path), 'utf8');

test('publishes deterministic privacy, terms, support, and deletion routes', () => {
  for (const path of [
    'index.html',
    'delete/index.html',
    'privacy/index.html',
    'terms/index.html',
    'support/index.html',
    '404.html',
  ]) {
    const html = read(path);
    assert.match(html, /<meta name="viewport"/u, path);
    assert.match(html, /<meta name="referrer" content="no-referrer"/u, path);
    assert.match(html, /<title\s+data-en="[^"]+"\s+data-hi="[^"]+"\s*>/u, path);
    assert.match(
      html,
      /<script type="module" src="\/src\/app\.ts"><\/script>/u,
      path,
    );
    assert.doesNotMatch(html, /\son(?:click|load|error)=/iu, path);
    assert.doesNotMatch(html, /\sstyle=/iu, path);
  }
  assert.match(read('delete/index.html'), /href="\/delete\/"/u);
  assert.doesNotMatch(read('index.html'), /delete-account\//u);
});

test('deletion client preserves the native deletion security contract', () => {
  const source = read('src/app.ts');
  assert.match(source, /setPersistence\(auth, inMemoryPersistence\)/u);
  assert.match(source, /reauthenticateWithPopup\(user, googleProvider\(\)\)/u);
  assert.match(source, /limitedUseAppCheckTokens:\s*true/u);
  assert.match(source, /ReCaptchaEnterpriseProvider/u);
  assert.match(source, /requestAccountDeletion/u);
  assert.match(source, /contractVersion:\s*1/u);
  assert.match(source, /crypto\.randomUUID\(\)/u);
  assert.match(source, /restorePendingReceipt\(\)/u);
  assert.match(
    source,
    /sessionStorage\.setItem\(PENDING_RECEIPT_SESSION_KEY, receiptId\)/u,
  );
  assert.match(
    source,
    /persistPendingReceipt\(submittedRequestId\)[\s\S]*services\.deleteAccount/u,
  );
  assert.match(source, /const submittedRequestId\s*=/u);
  assert.match(source, /requestId: submittedRequestId/u);
  assert.match(
    source,
    /deletionStartProjection\(\s*response\.data,\s*submittedRequestId/u,
  );
  assert.match(
    source,
    /acceptedReceiptStorageVerified = persistPendingReceipt\(\s*outcome\.receiptId/u,
  );
  assert.match(
    source,
    /clearReceiptButton\.disabled =\s*deletionSubmissionInFlight \|\| receiptCheckInFlight/u,
  );
  assert.match(source, /await checkReceipt\(true\)/u);
  assert.match(
    source,
    /if \(services\.auth\.currentUser !== null\)[\s\S]*receiptSignOutRequired[\s\S]*return;/u,
  );
  assert.match(
    source,
    /if \(deletionSubmissionInFlight \|\| receiptCheckInFlight\)/u,
  );
  assert.match(source, /if \(receiptCheckInFlight\) \{\s*continueButton\.disabled = true;/u);
  assert.match(source, /services\.auth\.currentUser !== null/u);
  assert.match(source, /postAcceptanceSignOutFailed/u);
  assert.match(source, /associated with this deletion request/u);
  assert.match(source, /#retry-sign-out/u);
  assert.match(source, /#clear-receipt/u);
  assert.equal(source.match(/clearPendingReceipt\(\)/gu)?.length, 3);
  assert.doesNotMatch(source, /\.addScope\(/u);
  assert.doesNotMatch(source, /credentialFromResult/u);
  assert.doesNotMatch(source, /(?:localStorage|indexedDB|document\.cookie)/u);
  assert.doesNotMatch(source, /signOut\(services\.auth\)\.catch/u);
  assert.doesNotMatch(source, /JSON\.stringify\([^)]*receipt/iu);
  assert.doesNotMatch(source, /(?:console|logger)\s*\./u);
  assert.doesNotMatch(source, /innerHTML/u);
});

test('missing release configuration visibly disables every secure workflow control', () => {
  const source = read('src/app.ts');
  const start = source.indexOf('function showConfigurationFailure');
  const end = source.indexOf('\nfunction firebaseOptions', start);
  assert.notEqual(start, -1);
  assert.notEqual(end, -1);
  const failureHandler = source.slice(start, end);
  for (const selector of [
    '#continue-google',
    '#delete-confirm',
    '#delete-account',
    '#sign-out',
    '#copy-reference',
    '#retry-sign-out',
    '#receipt-check-id',
    '#check-receipt',
    '#clear-receipt',
  ]) {
    assert.match(
      failureHandler,
      new RegExp(`['"]${selector}['"]`, 'u'),
      selector,
    );
  }
  assert.match(failureHandler, /control\.disabled = true/u);
  assert.doesNotMatch(failureHandler, /#language-toggle/u);
});

test('deletion and privacy copy state the irreducible boundaries', () => {
  const deletion = read('delete/index.html');
  const privacy = read('privacy/index.html');
  assert.match(deletion, /previously issued permit/iu);
  assert.match(
    deletion,
    /cannot erase protected data that remains on a phone/iu,
  );
  assert.match(deletion, /does not remove source contacts from Google/iu);
  assert.match(deletion, /receipt confirms acceptance, not completion/iu);
  assert.match(deletion, /temporary session storage/iu);
  assert.match(deletion, /id="clear-receipt"/u);
  assert.match(deletion, /id="retry-sign-out"/u);
  assert.match(deletion, /identity-verified support workflow/iu);
  assert.match(
    privacy,
    /no advertising, analytics, session replay, or non-essential cookies/iu,
  );
  assert.match(privacy, /400 days/iu);
  assert.match(privacy, /never stores raw contacts/iu);
  assert.match(privacy, /temporary session storage/iu);
  assert.match(privacy, /content-free, not data-free/iu);
  assert.match(privacy, /Firebase Installations token/iu);
  assert.match(privacy, /Only for people enabled on Android/iu);
  assert.match(privacy, /iOS does not register recipients/iu);
  assert.match(privacy, /message history/iu);
  assert.match(privacy, /cannot promise immediate erasure of provider logs/iu);
});

test('Firebase Hosting deploy is release-gated and applies security headers', () => {
  const firebase = JSON.parse(read('../firebase.json'));
  assert.equal(firebase.hosting.public, 'hosting/public');
  assert.equal(firebase.hosting.trailingSlash, true);
  assert.deepEqual(firebase.hosting.predeploy, [
    'npm --prefix hosting run build:release',
  ]);
  const headers = firebase.hosting.headers
    .flatMap(entry => entry.headers)
    .reduce(
      (result, header) => ({ ...result, [header.key]: header.value }),
      {},
    );
  assert.match(headers['Content-Security-Policy'], /frame-ancestors 'none'/u);
  assert.match(headers['Content-Security-Policy'], /form-action 'none'/u);
  assert.equal(headers['Referrer-Policy'], 'no-referrer');
  assert.equal(headers['X-Content-Type-Options'], 'nosniff');
  assert.equal(headers['X-Frame-Options'], 'DENY');
  assert.equal(
    headers['Cross-Origin-Opener-Policy'],
    'same-origin-allow-popups',
  );
});
