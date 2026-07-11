import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { describe, it } from 'node:test';
import { AndroidConfig, compileModsAsync, XML } from 'expo/config-plugins';

type NativeConfig = Parameters<typeof compileModsAsync>[0];
type NativeConfigPlugin = (config: NativeConfig) => NativeConfig;
type XmlNode = {
  $?: Record<string, string>;
  [key: string]: unknown;
};
type ManifestComponent = {
  $?: Record<string, string>;
  'intent-filter'?: {
    action?: { $?: Record<string, string> }[];
  }[];
  'meta-data'?: { $?: Record<string, string> }[];
};
type ManifestApplication = ManifestComponent & {
  receiver?: ManifestComponent[];
};
type ShortcutNode = {
  $?: Record<string, string>;
  intent?: { $?: Record<string, string> }[];
};
type ShortcutDocument = {
  shortcuts?: {
    shortcut?: ShortcutNode[];
  };
};
type ProviderInfoDocument = {
  'appwidget-provider'?: {
    $?: Record<string, string>;
  };
};
type StringsDocument = {
  resources?: {
    string?: { $?: { name?: string } }[];
  };
};

const loadPlugin = createRequire(import.meta.url);
const shortcutsPlugin = loadPlugin('../../plugins/with-relateai-shortcuts') as NativeConfigPlugin;
const homeWidgetPlugin = loadPlugin('../../plugins/with-relateai-home-widget') as NativeConfigPlugin;

const androidPackage = 'com.relateai.fixture';
const packageDirectory = androidPackage.replaceAll('.', '/');

const manifestFixture = `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="${androidPackage}">
  <application android:name=".MainApplication" android:label="@string/app_name">
    <activity android:name=".MainActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>
  </application>
</manifest>
`;

const mainApplicationFixture = `package ${androidPackage}

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage

class MainApplication : Application(), ReactApplication {
  override val reactNativeHost: ReactNativeHost = object : ReactNativeHost(this) {
    override fun getPackages(): List<ReactPackage> =
      PackageList(this).packages.apply {
        // Packages that cannot be autolinked can be added here.
      }
  }
}
`;

const writeFixtureFile = async (projectRoot: string, relativePath: string, contents: string): Promise<void> => {
  const filePath = join(projectRoot, relativePath);
  await mkdir(dirname(filePath), { recursive: true });
  await writeFile(filePath, contents, 'utf8');
};

const createAndroidFixtureAsync = async (): Promise<string> => {
  const projectRoot = await mkdtemp(join(tmpdir(), 'relateai-native-prebuild-'));
  await writeFixtureFile(projectRoot, 'android/app/src/main/AndroidManifest.xml', manifestFixture);
  await writeFixtureFile(
    projectRoot,
    'android/app/src/main/res/values/strings.xml',
    '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n  <string name="app_name">RelateAI fixture</string>\n</resources>\n'
  );
  await writeFixtureFile(
    projectRoot,
    `android/app/src/main/java/${packageDirectory}/MainApplication.kt`,
    mainApplicationFixture
  );
  return projectRoot;
};

const runNativeModsAsync = async (projectRoot: string): Promise<void> => {
  let config: NativeConfig = {
    name: 'RelateAI native generation fixture',
    slug: 'relateai-native-generation-fixture',
    android: { package: androidPackage }
  };
  config = shortcutsPlugin(config);
  config = homeWidgetPlugin(config);
  await compileModsAsync(config, { projectRoot, platforms: ['android'] });
};

const countOccurrences = (contents: string, value: string): number => contents.split(value).length - 1;

const collectAndroidIds = (node: unknown, ids: Set<string>): void => {
  if (!node || typeof node !== 'object') return;
  const xmlNode = node as XmlNode;
  const id = xmlNode.$?.['android:id'];
  if (id) ids.add(id.replace(/^@\+?id\//, ''));
  for (const [key, value] of Object.entries(xmlNode)) {
    if (key === '$') continue;
    if (Array.isArray(value)) value.forEach(item => collectAndroidIds(item, ids));
    else collectAndroidIds(value, ids);
  }
};

describe('Expo Android native plugin prebuild integration', () => {
  it('executes Expo mods twice in a temporary native project and leaves compile-ready generated symbols', async () => {
    const projectRoot = await createAndroidFixtureAsync();

    try {
      await runNativeModsAsync(projectRoot);
      await runNativeModsAsync(projectRoot);

      const androidRoot = join(projectRoot, 'android/app/src/main');
      const manifest = await XML.readXMLAsync({ path: join(androidRoot, 'AndroidManifest.xml') });
      const application = AndroidConfig.Manifest.getMainApplicationOrThrow(
        manifest as unknown as Parameters<typeof AndroidConfig.Manifest.getMainApplicationOrThrow>[0]
      ) as unknown as ManifestApplication;
      const mainActivity = AndroidConfig.Manifest.getMainActivityOrThrow(
        manifest as unknown as Parameters<typeof AndroidConfig.Manifest.getMainActivityOrThrow>[0]
      ) as unknown as ManifestComponent;
      const receivers = (application.receiver ?? []).filter(
        item => item.$?.['android:name'] === `${androidPackage}.widget.RelateAiHomeWidgetProvider`
      );
      const shortcutMetadata = (mainActivity['meta-data'] ?? []).filter(
        item => item.$?.['android:name'] === 'android.app.shortcuts'
      );

      assert.equal(receivers.length, 1);
      assert.equal(receivers[0].$?.['android:exported'], 'false');
      assert.equal(receivers[0]['meta-data']?.[0]?.$?.['android:resource'], '@xml/relateai_home_widget');
      assert.equal(shortcutMetadata.length, 1);
      assert.equal(shortcutMetadata[0].$?.['android:resource'], '@xml/relateai_shortcuts');

      const shortcuts = (await XML.readXMLAsync({
        path: join(androidRoot, 'res/xml/relateai_shortcuts.xml')
      })) as unknown as ShortcutDocument;
      const shortcutNodes = shortcuts.shortcuts?.shortcut ?? [];
      assert.deepEqual(
        shortcutNodes.map(item => item.$?.['android:shortcutId']),
        ['review-messages', 'add-event']
      );
      assert.deepEqual(
        shortcutNodes.map(item => item.intent?.[0]?.$?.['android:data']),
        ['relateai://messages', 'relateai://event/new']
      );
      assert.ok(shortcutNodes.every(item => item.intent?.[0]?.$?.['android:action'] === 'android.intent.action.VIEW'));
      assert.ok(shortcutNodes.every(item => item.$?.['android:rank'] === undefined));

      const providerInfo = (await XML.readXMLAsync({
        path: join(androidRoot, 'res/xml/relateai_home_widget.xml')
      })) as unknown as ProviderInfoDocument;
      assert.equal(providerInfo['appwidget-provider']?.$?.['android:initialLayout'], '@layout/relateai_home_widget');

      const layout = await XML.readXMLAsync({ path: join(androidRoot, 'res/layout/relateai_home_widget.xml') });
      const layoutIds = new Set<string>();
      collectAndroidIds(layout, layoutIds);

      const providerDirectory = join(androidRoot, 'java', packageDirectory, 'widget');
      const providerSource = await readFile(join(providerDirectory, 'RelateAiHomeWidgetProvider.java'), 'utf8');
      const moduleSource = await readFile(join(providerDirectory, 'RelateAiHomeWidgetModule.java'), 'utf8');
      const packageSource = await readFile(join(providerDirectory, 'RelateAiHomeWidgetPackage.java'), 'utf8');
      for (const source of [providerSource, moduleSource, packageSource]) {
        assert.match(source, new RegExp(`^package ${androidPackage.replaceAll('.', '\\.')}\\.widget;`));
      }
      for (const match of providerSource.matchAll(/R\.id\.([A-Za-z0-9_]+)/g)) {
        assert.ok(layoutIds.has(match[1]), `missing generated layout id ${match[1]}`);
      }
      assert.doesNotMatch(providerSource, /SEND_SMS|READ_SMS|SmsManager|sendNow|markSent/i);

      const strings = (await XML.readXMLAsync({
        path: join(androidRoot, 'res/values/strings.xml')
      })) as unknown as StringsDocument;
      const stringNames = (strings.resources?.string ?? []).map(item => item.$?.name);
      const generatedStringNames = stringNames.filter(name => name?.startsWith('relateai_'));
      assert.ok(generatedStringNames.length > 0);
      assert.equal(new Set(generatedStringNames).size, generatedStringNames.length);
      for (const match of providerSource.matchAll(/R\.string\.([A-Za-z0-9_]+)/g)) {
        assert.ok(stringNames.includes(match[1]), `missing generated string ${match[1]}`);
      }

      const mainApplication = await readFile(join(androidRoot, 'java', packageDirectory, 'MainApplication.kt'), 'utf8');
      assert.equal(countOccurrences(mainApplication, '@generated begin relateai-home-widget-package'), 1);
      assert.equal(countOccurrences(mainApplication, '@generated end relateai-home-widget-package'), 1);
      assert.equal(countOccurrences(mainApplication, `${androidPackage}.widget.RelateAiHomeWidgetPackage`), 1);
    } finally {
      await rm(projectRoot, { recursive: true, force: true });
    }
  });
});
