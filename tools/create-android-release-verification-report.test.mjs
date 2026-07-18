import assert from 'node:assert/strict';
import test from 'node:test';

import { parseAndroidFirebaseVerification } from './create-android-release-verification-report.mjs';

const line =
  'PASS Android Firebase project=birthday-production number=123456789012 app-id=1:123456789012:android:abcdef1234567890 web-oauth-client=123456789012-release.apps.googleusercontent.com';

test('structured Android report accepts one internally consistent artifact-derived Firebase line', () => {
  assert.deepEqual(parseAndroidFirebaseVerification(`${line}\n`), {
    projectId: 'birthday-production',
    projectNumber: '123456789012',
    androidAppId: '1:123456789012:android:abcdef1234567890',
    webOauthClientId: '123456789012-release.apps.googleusercontent.com',
  });
});

test('structured Android report rejects missing, duplicate, or cross-project Firebase lines', () => {
  assert.throws(
    () => parseAndroidFirebaseVerification('PASS something else\n'),
    /one exact Firebase projection/u,
  );
  assert.throws(
    () => parseAndroidFirebaseVerification(`${line}\n${line}\n`),
    /one exact Firebase projection/u,
  );
  assert.throws(
    () =>
      parseAndroidFirebaseVerification(
        `${line.replace('app-id=1:123456789012:', 'app-id=1:999999999999:')}\n`,
      ),
    /inconsistent/u,
  );
});
