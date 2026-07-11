import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';

const rootDir = process.cwd();
const appConfig = JSON.parse(readFileSync(join(rootDir, 'app.json'), 'utf8')) as {
  expo: {
    name: string;
    slug: string;
    version?: string;
    runtimeVersion?: { policy?: string } | string;
    scheme?: string;
    platforms?: string[];
    ios?: {
      bundleIdentifier?: string;
      buildNumber?: string;
      infoPlist?: Record<string, unknown>;
    };
    android?: {
      package?: string;
      versionCode?: number;
      allowBackup?: boolean;
      permissions?: string[];
      blockedPermissions?: string[];
    };
  };
};
const easConfig = JSON.parse(readFileSync(join(rootDir, 'eas.json'), 'utf8')) as {
  cli?: {
    appVersionSource?: string;
  };
  build?: Record<string, Record<string, unknown>>;
  submit?: Record<string, unknown>;
};
const readDoc = (path: string) => readFileSync(join(rootDir, path), 'utf8');

describe('react native release configuration contract', () => {
  it('defines native identifiers, app versioning, and runtime compatibility for production builds', () => {
    assert.equal(appConfig.expo.name, 'RelateAI');
    assert.equal(appConfig.expo.slug, 'relateai');
    assert.equal(appConfig.expo.scheme, 'relateai');
    assert.deepEqual(appConfig.expo.platforms, ['android', 'ios']);
    assert.match(appConfig.expo.version ?? '', /^\d+\.\d+\.\d+$/);
    assert.deepEqual(appConfig.expo.runtimeVersion, { policy: 'appVersion' });
    assert.equal(appConfig.expo.android?.package, 'com.relateai.app');
    assert.equal(appConfig.expo.ios?.bundleIdentifier, 'com.relateai.app');
    assert.ok(Number.isInteger(appConfig.expo.android?.versionCode));
    assert.ok((appConfig.expo.android?.versionCode ?? 0) > 0);
    assert.equal(appConfig.expo.android?.allowBackup, false);
    assert.match(appConfig.expo.ios?.buildNumber ?? '', /^\d+$/);
  });

  it('keeps high-risk SMS, phone-log, exact-alarm, and AccessibilityService permissions out of the RN release manifest', () => {
    const requested = new Set(appConfig.expo.android?.permissions ?? []);
    const blocked = new Set(appConfig.expo.android?.blockedPermissions ?? []);
    const forbidden = [
      'SEND_SMS',
      'READ_SMS',
      'RECEIVE_SMS',
      'READ_CALL_LOG',
      'READ_PHONE_NUMBERS',
      'USE_EXACT_ALARM',
      'SCHEDULE_EXACT_ALARM',
      'BIND_ACCESSIBILITY_SERVICE',
      'android.permission.SEND_SMS',
      'android.permission.READ_SMS',
      'android.permission.RECEIVE_SMS',
      'android.permission.READ_CALL_LOG',
      'android.permission.READ_PHONE_NUMBERS',
      'android.permission.USE_EXACT_ALARM',
      'android.permission.SCHEDULE_EXACT_ALARM',
      'android.permission.BIND_ACCESSIBILITY_SERVICE'
    ];

    forbidden.forEach(permission => {
      assert.equal(requested.has(permission), false, `${permission} should not be directly requested`);
    });
    [
      'android.permission.SEND_SMS',
      'android.permission.READ_SMS',
      'android.permission.RECEIVE_SMS',
      'android.permission.READ_CALL_LOG',
      'android.permission.READ_PHONE_NUMBERS',
      'android.permission.USE_EXACT_ALARM',
      'android.permission.SCHEDULE_EXACT_ALARM',
      'android.permission.BIND_ACCESSIBILITY_SERVICE'
    ].forEach(permission => {
      assert.equal(blocked.has(permission), true, `${permission} should be blocked from merged manifests`);
    });
    [
      'android.permission.WRITE_CONTACTS',
      'android.permission.READ_EXTERNAL_STORAGE',
      'android.permission.WRITE_EXTERNAL_STORAGE',
      'android.permission.SYSTEM_ALERT_WINDOW'
    ].forEach(permission => {
      assert.equal(blocked.has(permission), true, `${permission} should be removed from merged release manifests`);
    });
  });

  it('keeps sensitive permission copy explicit and scoped to user-initiated actions', () => {
    const infoPlist = appConfig.expo.ios?.infoPlist ?? {};

    assert.match(String(infoPlist.NSContactsUsageDescription ?? ''), /only when you ask/i);
    assert.match(String(infoPlist.NSCalendarsUsageDescription ?? ''), /only when you ask/i);
    assert.match(String(infoPlist.NSCalendarsFullAccessUsageDescription ?? ''), /only when you ask/i);
    assert.match(String(infoPlist.NSFaceIDUsageDescription ?? ''), /only when you enable/i);
    assert.deepEqual(infoPlist.NSAppTransportSecurity, {
      NSAllowsArbitraryLoads: false,
      NSAllowsLocalNetworking: false
    });
  });

  it('keeps WhatsApp and SMS policy aligned to manual handoff instead of AccessibilityService automation', () => {
    const fssot = readDoc('docs/feature-fssot.md');
    const roadmap = readDoc('docs/feature-roadmap-analysis.md');
    const privacyPolicy = readDoc('docs/security/privacy-and-permissions.md');

    assert.match(
      fssot,
      /WhatsApp handoff requires a phone number, app availability, and prominent manual handoff consent/i
    );
    assert.match(fssot, /SMS handoff requires a phone number and an available SMS-capable destination app/i);
    assert.doesNotMatch(fssot, /accessibility enablement/i);
    assert.doesNotMatch(fssot, /Android Accessibility settings for WhatsApp automation/i);
    assert.doesNotMatch(fssot, /WhatsApp automation must be narrow/i);

    assert.match(roadmap, /Prefer manual "Open in WhatsApp with approved text" or review-first handoff/i);
    assert.match(
      privacyPolicy,
      /The React Native release path uses manual WhatsApp handoff, not AccessibilityService automation/i
    );
    assert.match(privacyPolicy, /current contract excludes AccessibilityService automation/i);
  });

  it('keeps setup diagnostics branded as Setup Check instead of a standalone AI Doctor pillar', () => {
    const fssot = readDoc('docs/feature-fssot.md');
    const roadmap = readDoc('docs/feature-roadmap-analysis.md');
    const releaseChecklist = readDoc('docs/operations/release-checklist.md');

    assert.match(roadmap, /Rename or present as "Setup Check" or "Fix Issues"/);
    assert.match(roadmap, /Recommended secondary destinations:[\s\S]+- Setup Check/);
    assert.doesNotMatch(roadmap, /Recommended secondary destinations:[\s\S]+- AI Doctor/);
    assert.match(fssot, /## 23\. Setup Check and Setup Diagnostics/);
    assert.match(fssot, /Setup Check diagnoses why AI wishes feel generic/);
    assert.doesNotMatch(fssot, /\bAI Doctor\b/);
    assert.match(releaseChecklist, /Setup Check/);
    assert.doesNotMatch(releaseChecklist, /\bAI Doctor\b|HealthMonitor/);
    assert.doesNotMatch(releaseChecklist, /Permission denial paths[\s\S]+exact alarms[\s\S]+Accessibility\./);
  });

  it('defines EAS development, preview, and production build profiles for the RN replacement', () => {
    assert.equal(easConfig.cli?.appVersionSource, 'local');
    assert.ok(easConfig.build?.development);
    assert.ok(easConfig.build?.preview);
    assert.ok(easConfig.build?.production);
    assert.deepEqual(easConfig.build?.production?.android, { buildType: 'app-bundle' });
    assert.deepEqual(easConfig.build?.production?.ios, { simulator: false });
    assert.equal(easConfig.build?.production?.autoIncrement, true);
    assert.ok(easConfig.submit?.production);
  });
});
