const fs = require('fs');
const path = require('path');
const {
  AndroidConfig,
  createRunOncePlugin,
  withAndroidManifest,
  withDangerousMod,
  withStringsXml
} = require('expo/config-plugins');

const shortcutDefinitions = require('../src/config/launcherShortcuts.json');

const RESOURCE_NAME = 'relateai_shortcuts';
const SHORTCUT_METADATA_NAME = 'android.app.shortcuts';

const resourceNameFor = (shortcut, suffix) =>
  `relateai_shortcut_${shortcut.id.replace(/[^a-zA-Z0-9_]/g, '_')}_${suffix}`;

const escapeXml = value =>
  String(value)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

const renderShortcutXml = (shortcuts, androidPackage) => {
  const items = shortcuts
    .map(
      shortcut => `  <shortcut
    android:shortcutId="${escapeXml(shortcut.id)}"
    android:enabled="true"
    android:icon="@mipmap/ic_launcher"
    android:shortcutShortLabel="@string/${resourceNameFor(shortcut, 'short')}"
    android:shortcutLongLabel="@string/${resourceNameFor(shortcut, 'long')}"
    android:shortcutDisabledMessage="@string/${resourceNameFor(shortcut, 'disabled')}"
    android:rank="${shortcut.rank}">
    <intent
      android:action="android.intent.action.VIEW"
      android:targetPackage="${escapeXml(androidPackage)}"
      android:targetClass="${escapeXml(androidPackage)}.MainActivity"
      android:data="${escapeXml(shortcut.url)}" />
  </shortcut>`
    )
    .join('\n');

  return `<?xml version="1.0" encoding="utf-8"?>
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
${items}
</shortcuts>
`;
};

const addShortcutMetadataToMainActivity = androidManifest => {
  const mainActivity = AndroidConfig.Manifest.getMainActivityOrThrow(androidManifest);
  mainActivity['meta-data'] = (mainActivity['meta-data'] ?? []).filter(
    item => item.$['android:name'] !== SHORTCUT_METADATA_NAME
  );
  mainActivity['meta-data'].push({
    $: {
      'android:name': SHORTCUT_METADATA_NAME,
      'android:resource': `@xml/${RESOURCE_NAME}`
    }
  });
  return androidManifest;
};

const withShortcutStrings = config =>
  withStringsXml(config, config => {
    const stringItems = shortcutDefinitions.flatMap(shortcut => [
      AndroidConfig.Resources.buildResourceItem({
        name: resourceNameFor(shortcut, 'short'),
        value: shortcut.shortLabel,
        translatable: true
      }),
      AndroidConfig.Resources.buildResourceItem({
        name: resourceNameFor(shortcut, 'long'),
        value: shortcut.longLabel,
        translatable: true
      }),
      AndroidConfig.Resources.buildResourceItem({
        name: resourceNameFor(shortcut, 'disabled'),
        value: shortcut.disabledMessage,
        translatable: true
      })
    ]);

    config.modResults = AndroidConfig.Strings.setStringItem(stringItems, config.modResults);
    return config;
  });

const withShortcutXml = config =>
  withDangerousMod(config, [
    'android',
    async config => {
      const androidPackage = config.android?.package ?? config.modRequest?.config?.android?.package;
      if (!androidPackage) {
        throw new Error('RelateAI launcher shortcuts require expo.android.package.');
      }

      const xmlDir = path.join(config.modRequest.projectRoot, 'android/app/src/main/res/xml');
      await fs.promises.mkdir(xmlDir, { recursive: true });
      await fs.promises.writeFile(
        path.join(xmlDir, `${RESOURCE_NAME}.xml`),
        renderShortcutXml(shortcutDefinitions, androidPackage)
      );
      return config;
    }
  ]);

const withShortcutManifest = config =>
  withAndroidManifest(config, config => {
    config.modResults = addShortcutMetadataToMainActivity(config.modResults);
    return config;
  });

const withRelateAiShortcuts = config => {
  config = withShortcutStrings(config);
  config = withShortcutXml(config);
  config = withShortcutManifest(config);
  return config;
};

module.exports = createRunOncePlugin(withRelateAiShortcuts, 'with-relateai-shortcuts', '1.0.0');
module.exports.renderShortcutXml = renderShortcutXml;
module.exports.addShortcutMetadataToMainActivity = addShortcutMetadataToMainActivity;
