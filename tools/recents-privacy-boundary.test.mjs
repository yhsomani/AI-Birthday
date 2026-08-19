import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');

test('Android protects recents without globally disabling active support screenshots', () => {
  const activity = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/MainActivity.kt',
  );
  assert.match(activity, /setRecentsScreenshotEnabled\(false\)/u);
  assert.match(
    activity,
    /override fun onPause\(\)[\s\S]*?addFlags\(WindowManager\.LayoutParams\.FLAG_SECURE\)/u,
  );
  assert.match(
    activity,
    /override fun onResume\(\)[\s\S]*?clearFlags\(WindowManager\.LayoutParams\.FLAG_SECURE\)/u,
  );
});

test('Privacy UI truthfully discloses active screenshot and recording limits in both languages', () => {
  const screen = read('src/features/live/LivePrivacyScreen.tsx');
  const resources = read('src/localization/liveResources.ts');
  assert.match(screen, /live\.privacy\.screenCaptureTitle/u);
  assert.match(screen, /live\.privacy\.screenCaptureBody/u);
  assert.match(
    resources,
    /Screenshots and recordings taken while the app is open can still contain names, phone numbers or message text/u,
  );
  assert.match(resources, /स्क्रीनशॉट या रिकॉर्डिंग/u);
});
