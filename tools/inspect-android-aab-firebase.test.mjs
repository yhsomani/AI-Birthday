import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

import { parseAabFirebaseResourceDump } from './inspect-android-aab-firebase.mjs';

const dump = ({
  projectId = 'birthday-production',
  projectNumber = '123456789012',
  androidAppId = '1:123456789012:android:abcdef1234567890',
  webOauthClientId = '123456789012-release.apps.googleusercontent.com',
} = {}) => `Package 'com.yashsomani.birthdayautopilot':
0x7f110001 - string/default_web_client_id
\t(default) - [STR] "${webOauthClientId}"
0x7f110002 - string/gcm_defaultSenderId
\t(default) - [STR] "${projectNumber}"
0x7f110003 - string/google_app_id
\t(default) - [STR] "${androidAppId}"
0x7f110004 - string/project_id
\t(default) - [STR] "${projectId}"
`;

test('projects exact Firebase identity from compiled AAB resource values', () => {
  assert.deepEqual(parseAabFirebaseResourceDump(dump()), {
    projectId: 'birthday-production',
    projectNumber: '123456789012',
    androidAppId: '1:123456789012:android:abcdef1234567890',
    webOauthClientId: '123456789012-release.apps.googleusercontent.com',
  });
});

test('rejects missing, duplicated, qualified, escaped, or cross-project Firebase values', () => {
  assert.throws(
    () =>
      parseAabFirebaseResourceDump(
        dump().replace('0x7f110004 - string/project_id\n', ''),
      ),
    /one string\/project_id/u,
  );
  assert.throws(
    () =>
      parseAabFirebaseResourceDump(
        `${dump()}0x7f110005 - string/google_app_id\n\t(default) - [STR] "1:123456789012:android:abcdef1234567890"\n`,
      ),
    /one string\/google_app_id/u,
  );
  assert.throws(
    () =>
      parseAabFirebaseResourceDump(
        dump().replace(
          '\t(default) - [STR] "birthday-production"',
          '\tlocale: "hi" - [STR] "birthday-production"',
        ),
      ),
    /one unqualified literal/u,
  );
  assert.throws(
    () =>
      parseAabFirebaseResourceDump(
        dump().replace('birthday-production', 'birthday\\-production'),
      ),
    /one unqualified literal/u,
  );
  assert.throws(
    () =>
      parseAabFirebaseResourceDump(
        dump({
          androidAppId: '1:999999999999:android:abcdef1234567890',
        }),
      ),
    /does not belong/u,
  );
  assert.throws(
    () =>
      parseAabFirebaseResourceDump(
        dump({
          webOauthClientId: '999999999999-release.apps.googleusercontent.com',
        }),
      ),
    /does not belong/u,
  );
});

test('artifact inspector uses only locked offline bundletool resource decoding', () => {
  const inspector = readFileSync(
    'tools/inspect-android-aab-firebase.mjs',
    'utf8',
  );
  const gradle = readFileSync('android/build.gradle', 'utf8');
  assert.match(inspector, /dumpBirthdayAabResources/u);
  assert.match(inspector, /'--offline'/u);
  assert.match(inspector, /beforePath[\s\S]*sameStableFile/u);
  assert.match(gradle, /tasks\.register\("dumpBirthdayAabResources"/u);
  assert.match(gradle, /"dump",\s*"resources"/u);
  assert.match(gradle, /"--values"/u);
  assert.match(
    gradle,
    /bundletoolArtifacts\[0\]\.moduleVersion\.id\.version != "1\.18\.1"/u,
  );
});
