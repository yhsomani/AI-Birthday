const path = require('path');
const {
  AndroidConfig,
  XML,
  createRunOncePlugin,
  withAndroidManifest,
  withDangerousMod,
  withFinalizedMod,
  withStringsXml
} = require('expo/config-plugins');
const {
  assertCondition,
  resolveAndroidPackage,
  writeGeneratedFileAsync
} = require('./android-generation');

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
      const androidPackage = resolveAndroidPackage(config, 'RelateAI launcher shortcuts');

      const xmlDir = path.join(config.modRequest.projectRoot, 'android/app/src/main/res/xml');
      await writeGeneratedFileAsync(
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

const validateShortcutGenerationAsync = async (projectRoot, androidPackage) => {
  const androidRoot = path.join(projectRoot, 'android/app/src/main');
  const manifest = await XML.readXMLAsync({ path: path.join(androidRoot, 'AndroidManifest.xml') });
  const mainActivity = AndroidConfig.Manifest.getMainActivityOrThrow(manifest);
  const metadataItems = (mainActivity['meta-data'] ?? []).filter(
    item => item.$?.['android:name'] === SHORTCUT_METADATA_NAME
  );
  assertCondition(metadataItems.length === 1, 'RelateAI launcher shortcuts expected exactly one manifest metadata item.');
  assertCondition(
    metadataItems[0].$?.['android:resource'] === `@xml/${RESOURCE_NAME}`,
    'RelateAI launcher shortcuts manifest metadata references an invalid resource.'
  );

  const shortcutXml = await XML.readXMLAsync({
    path: path.join(androidRoot, 'res/xml', `${RESOURCE_NAME}.xml`)
  });
  const generatedShortcuts = shortcutXml.shortcuts?.shortcut ?? [];
  assertCondition(
    generatedShortcuts.length === shortcutDefinitions.length,
    'RelateAI launcher shortcuts generated an unexpected number of shortcuts.'
  );

  for (const definition of shortcutDefinitions) {
    const matches = generatedShortcuts.filter(item => item.$?.['android:shortcutId'] === definition.id);
    assertCondition(matches.length === 1, `RelateAI launcher shortcut ${definition.id} must be generated exactly once.`);
    const intents = matches[0].intent ?? [];
    assertCondition(intents.length === 1, `RelateAI launcher shortcut ${definition.id} must have exactly one intent.`);
    assertCondition(
      intents[0].$?.['android:action'] === 'android.intent.action.VIEW' &&
        intents[0].$?.['android:targetPackage'] === androidPackage &&
        intents[0].$?.['android:targetClass'] === `${androidPackage}.MainActivity` &&
        intents[0].$?.['android:data'] === definition.url,
      `RelateAI launcher shortcut ${definition.id} generated an invalid navigation intent.`
    );
  }

  const strings = await XML.readXMLAsync({ path: path.join(androidRoot, 'res/values/strings.xml') });
  const stringNames = new Set((strings.resources?.string ?? []).map(item => item.$?.name));
  for (const definition of shortcutDefinitions) {
    for (const suffix of ['short', 'long', 'disabled']) {
      const name = resourceNameFor(definition, suffix);
      assertCondition(stringNames.has(name), `RelateAI launcher shortcuts are missing string resource ${name}.`);
    }
  }
};

const withShortcutValidation = config =>
  withFinalizedMod(config, [
    'android',
    async config => {
      const androidPackage = resolveAndroidPackage(config, 'RelateAI launcher shortcuts');
      await validateShortcutGenerationAsync(config.modRequest.projectRoot, androidPackage);
      return config;
    }
  ]);

const withRelateAiShortcuts = config => {
  config = withShortcutStrings(config);
  config = withShortcutXml(config);
  config = withShortcutManifest(config);
  config = withShortcutValidation(config);
  return config;
};

module.exports = createRunOncePlugin(withRelateAiShortcuts, 'with-relateai-shortcuts', '2.0.0');
module.exports.renderShortcutXml = renderShortcutXml;
module.exports.addShortcutMetadataToMainActivity = addShortcutMetadataToMainActivity;
module.exports.validateShortcutGenerationAsync = validateShortcutGenerationAsync;
