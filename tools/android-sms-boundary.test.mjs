import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { extname, join } from 'node:path';
import test from 'node:test';

const read = path => readFileSync(path, 'utf8');

const walk = directory =>
  readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? walk(path) : [path];
  });

test('only the typed native gateway contains an SMS submission call', () => {
  const sourceRoot = 'android/app/src/main/java';
  const callSites = walk(sourceRoot)
    .filter(path => extname(path) === '.kt' || extname(path) === '.java')
    .flatMap(path => {
      const source = read(path);
      const matches =
        source.match(/\.send(?:Multipart)?TextMessage\s*\(/gu) ?? [];
      return matches.map(() => path);
    });

  assert.deepEqual(callSites, [
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/sms/SmsGateway.kt',
  ]);
  const gateway = read(callSites[0]);
  assert.match(gateway, /ArmedAttemptPermit/u);
  assert.match(gateway, /commitApiBoundary/u);
  assert.match(gateway, /registerCallbackTokens/u);
});

test('dangerous telephony permissions remain restricted-release only', () => {
  const main = read('android/app/src/main/AndroidManifest.xml');
  const lab = read('android/app/src/lab/AndroidManifest.xml');
  const prod = read('android/app/src/prod/AndroidManifest.xml');

  for (const permission of ['SEND_SMS', 'READ_PHONE_STATE']) {
    assert.doesNotMatch(
      main,
      new RegExp(`android\\.permission\\.${permission}`, 'u'),
    );
    assert.match(lab, new RegExp(`android\\.permission\\.${permission}`, 'u'));
    assert.match(prod, new RegExp(`android\\.permission\\.${permission}`, 'u'));
  }
  assert.match(
    main,
    /android:name="\.automation\.sms\.SmsSentCallbackReceiver"[\s\S]*?android:exported="false"/u,
  );
  assert.match(
    main,
    /android:name="\.automation\.sms\.SmsDeliveryCallbackReceiver"[\s\S]*?android:exported="false"/u,
  );
});

test('React Native exposes no SMS or telephony submission method', () => {
  const spec = read('specs/native/NativeBirthday.ts');
  assert.doesNotMatch(
    spec,
    /sendSms|sendTextMessage|sendMultipartTextMessage|SmsManager/iu,
  );
});
