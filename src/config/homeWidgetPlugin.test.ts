import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join } from 'node:path';
import { describe, it } from 'node:test';

type AndroidManifest = {
  manifest: {
    application: {
      $?: Record<string, string>;
      receiver?: {
        $?: Record<string, string>;
        'intent-filter'?: { action?: { $?: Record<string, string> }[] }[];
        'meta-data'?: { $?: Record<string, string> }[];
      }[];
    }[];
  };
};

type HomeWidgetPlugin = {
  addWidgetPackageToMainApplication: (contents: string, androidPackage: string) => string;
  addWidgetReceiverToManifest: (manifest: AndroidManifest, androidPackage: string) => AndroidManifest;
  renderWidgetLayoutXml: () => string;
  renderWidgetModuleJava: (androidPackage: string) => string;
  renderWidgetPackageJava: (androidPackage: string) => string;
  renderWidgetProviderInfoXml: () => string;
  renderWidgetProviderJava: (androidPackage: string) => string;
};

const loadPlugin = createRequire(import.meta.url);
const homeWidgetPlugin = loadPlugin('../../plugins/with-relateai-home-widget') as HomeWidgetPlugin;
const bridgeSource = readFileSync(join(process.cwd(), 'src/native/homeWidgetBridge.ts'), 'utf8');
const appConfig = JSON.parse(readFileSync(join(process.cwd(), 'app.json'), 'utf8')) as {
  expo: {
    plugins?: string[];
  };
};

describe('React Native Android home widget plugin contract', () => {
  it('keeps the RN replacement configured to package the home widget during prebuild', () => {
    assert.ok(appConfig.expo.plugins?.includes('./plugins/with-relateai-home-widget'));
    assert.ok(appConfig.expo.plugins?.includes('./plugins/with-relateai-shortcuts'));
  });

  it('renders widget metadata with periodic refresh and a fixed safe layout', () => {
    const providerInfo = homeWidgetPlugin.renderWidgetProviderInfoXml();
    const layout = homeWidgetPlugin.renderWidgetLayoutXml();

    assert.match(providerInfo, /android:updatePeriodMillis="1800000"/);
    assert.match(providerInfo, /android:initialLayout="@layout\/relateai_home_widget"/);
    assert.match(providerInfo, /android:resizeMode="horizontal\|vertical"/);
    assert.match(providerInfo, /android:widgetCategory="home_screen"/);
    assert.match(layout, /@string\/relateai_widget_privacy_note/);
    assert.match(layout, /@string\/relateai_widget_open_messages/);
    assert.doesNotMatch(layout, /SEND_SMS|READ_SMS|phone|email|delete|send now/i);
  });

  it('generates a navigation-only widget provider with immutable safe-route intents', () => {
    const provider = homeWidgetPlugin.renderWidgetProviderJava('com.relateai.app');

    assert.match(provider, /extends AppWidgetProvider/);
    assert.match(provider, /PendingIntent\.FLAG_UPDATE_CURRENT \| PendingIntent\.FLAG_IMMUTABLE/);
    assert.match(provider, /relateai:\/\/home/);
    assert.match(provider, /relateai:\/\/events/);
    assert.match(provider, /relateai:\/\/messages/);
    assert.match(provider, /relateai:\/\/more/);
    assert.match(provider, /setPackage\(context\.getPackageName\(\)\)/);
    assert.match(provider, /safeRoute/);
    assert.match(provider, /\[email hidden\]/);
    assert.match(provider, /\[route hidden\]/);
    assert.doesNotMatch(provider, /SEND_SMS|READ_SMS|SmsManager|delete|markSent|sendNow/i);
  });

  it('generates a native module that stores safe widget JSON and refreshes installed widgets', () => {
    const moduleSource = homeWidgetPlugin.renderWidgetModuleJava('com.relateai.app');
    const packageSource = homeWidgetPlugin.renderWidgetPackageJava('com.relateai.app');

    assert.match(moduleSource, /extends ReactContextBaseJavaModule/);
    assert.match(moduleSource, /return "RelateAiHomeWidget"/);
    assert.match(moduleSource, /updateHomeWidget\(String summaryJson, Promise promise\)/);
    assert.match(moduleSource, /new JSONObject\(summaryJson\)/);
    assert.match(moduleSource, /putString\(RelateAiHomeWidgetProvider\.SUMMARY_KEY, summaryJson\)/);
    assert.match(moduleSource, /setAction\(RelateAiHomeWidgetProvider\.REFRESH_ACTION\)/);
    assert.match(moduleSource, /setPackage\(reactContext\.getPackageName\(\)\)/);
    assert.match(moduleSource, /clearHomeWidget\(Promise promise\)/);
    assert.doesNotMatch(moduleSource, /SEND_SMS|READ_SMS|SmsManager|markSent|sendNow/i);

    assert.match(packageSource, /implements ReactPackage/);
    assert.match(packageSource, /new RelateAiHomeWidgetModule\(reactContext\)/);
  });

  it('registers the generated native module package in Expo MainApplication templates', () => {
    const currentKotlinMainApplication = `
      override fun getPackages(): List<ReactPackage> =
        PackageList(this).packages.apply {
          // Add packages that cannot be autolinked here.
        }
    `;
    const kotlinMainApplication = `
      override fun getPackages(): List<ReactPackage> {
        val packages = PackageList(this).packages
        return packages
      }
    `;
    const javaMainApplication = `
      protected List<ReactPackage> getPackages() {
        List<ReactPackage> packages = new PackageList(this).getPackages();
        return packages;
      }
    `;

    const currentKotlinResult = homeWidgetPlugin.addWidgetPackageToMainApplication(
      currentKotlinMainApplication,
      'com.relateai.app'
    );
    assert.match(currentKotlinResult, /add\(com\.relateai\.app\.widget\.RelateAiHomeWidgetPackage\(\)\)/);
    assert.match(currentKotlinResult, /@generated begin relateai-home-widget-package/);
    assert.equal(
      homeWidgetPlugin.addWidgetPackageToMainApplication(currentKotlinResult, 'com.relateai.app'),
      currentKotlinResult
    );
    assert.match(
      homeWidgetPlugin.addWidgetPackageToMainApplication(kotlinMainApplication, 'com.relateai.app'),
      /packages\.add\(com\.relateai\.app\.widget\.RelateAiHomeWidgetPackage\(\)\)/
    );
    assert.match(
      homeWidgetPlugin.addWidgetPackageToMainApplication(javaMainApplication, 'com.relateai.app'),
      /packages\.add\(new com\.relateai\.app\.widget\.RelateAiHomeWidgetPackage\(\)\);/
    );
  });

  it('keeps the JavaScript bridge no-op safe outside Android and serializes through the privacy filter', () => {
    assert.match(bridgeSource, /Platform\.OS !== 'android'/);
    assert.match(bridgeSource, /NativeModules\.RelateAiHomeWidget/);
    assert.match(bridgeSource, /serializeHomeWidgetSummaryForNative\(summary\)/);
    assert.match(bridgeSource, /reason: 'native-module-missing'/);
  });

  it('registers the Android app-widget receiver without exporting it', () => {
    const manifest = homeWidgetPlugin.addWidgetReceiverToManifest(
      {
        manifest: {
          application: [
            {
              $: {
                'android:name': '.MainApplication'
              }
            }
          ]
        }
      },
      'com.relateai.app'
    );
    const receiver = manifest.manifest.application[0].receiver?.find(
      item => item.$?.['android:name'] === 'com.relateai.app.widget.RelateAiHomeWidgetProvider'
    );

    assert.equal(receiver?.$?.['android:exported'], 'false');
    assert.equal(
      receiver?.['intent-filter']?.[0]?.action?.[0]?.$?.['android:name'],
      'android.appwidget.action.APPWIDGET_UPDATE'
    );
    assert.equal(receiver?.['meta-data']?.[0]?.$?.['android:name'], 'android.appwidget.provider');
    assert.equal(receiver?.['meta-data']?.[0]?.$?.['android:resource'], '@xml/relateai_home_widget');
  });
});
