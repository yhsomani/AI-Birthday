const fs = require('fs');
const path = require('path');
const {
  AndroidConfig,
  CodeGenerator,
  XML,
  createRunOncePlugin,
  withAndroidManifest,
  withDangerousMod,
  withFinalizedMod,
  withMainApplication,
  withStringsXml
} = require('expo/config-plugins');
const {
  assertCondition,
  countOccurrences,
  resolveAndroidPackage,
  writeGeneratedFileAsync
} = require('./android-generation');

const WIDGET_PROVIDER_CLASS = 'RelateAiHomeWidgetProvider';
const WIDGET_MODULE_CLASS = 'RelateAiHomeWidgetModule';
const WIDGET_PACKAGE_CLASS = 'RelateAiHomeWidgetPackage';
const WIDGET_PROVIDER_RESOURCE_NAME = 'relateai_home_widget';
const WIDGET_LAYOUT_NAME = 'relateai_home_widget';
const WIDGET_BACKGROUND_NAME = 'relateai_home_widget_background';
const WIDGET_ACTION_BACKGROUND_NAME = 'relateai_home_widget_action_background';
const WIDGET_METADATA_NAME = 'android.appwidget.provider';
const MAIN_APPLICATION_GENERATED_TAG = 'relateai-home-widget-package';

const widgetStringItems = [
  ['relateai_widget_default_title', 'RelateAI today'],
  ['relateai_widget_default_subtitle', 'Open RelateAI to refresh relationship reminders.'],
  ['relateai_widget_privacy_note', 'Widget summaries avoid message bodies, routes, and private notes.'],
  ['relateai_widget_open_home', 'Open dashboard'],
  ['relateai_widget_open_events', 'Open events'],
  ['relateai_widget_open_messages', 'Review messages'],
  ['relateai_widget_open_more', 'Open setup']
];

const androidPackagePath = androidPackage => androidPackage.split('.').join(path.sep);

const escapeXml = value =>
  String(value)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

const renderWidgetProviderInfoXml = () => `<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:minResizeWidth="180dp"
    android:minResizeHeight="90dp"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/${WIDGET_LAYOUT_NAME}"
    android:previewImage="@mipmap/ic_launcher"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
`;

const renderWidgetBackgroundXml = () => `<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#FFFDF8" />
    <stroke android:width="1dp" android:color="#D7DEE8" />
    <corners android:radius="18dp" />
</shape>
`;

const renderWidgetActionBackgroundXml = () => `<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#174A63" />
    <corners android:radius="14dp" />
</shape>
`;

const renderWidgetLayoutXml = () => `<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/${WIDGET_BACKGROUND_NAME}"
    android:contentDescription="@string/relateai_widget_default_title"
    android:orientation="vertical"
    android:padding="14dp">

    <TextView
        android:id="@+id/widget_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:ellipsize="end"
        android:maxLines="1"
        android:text="@string/relateai_widget_default_title"
        android:textColor="#111827"
        android:textSize="16sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/widget_subtitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="3dp"
        android:ellipsize="end"
        android:maxLines="2"
        android:text="@string/relateai_widget_default_subtitle"
        android:textColor="#4B5563"
        android:textSize="12sp" />

    <TextView
        android:id="@+id/widget_primary"
        android:layout_width="match_parent"
        android:layout_height="32dp"
        android:layout_marginTop="8dp"
        android:background="@drawable/${WIDGET_ACTION_BACKGROUND_NAME}"
        android:ellipsize="end"
        android:gravity="center_vertical"
        android:maxLines="1"
        android:paddingLeft="12dp"
        android:paddingRight="12dp"
        android:text="@string/relateai_widget_open_home"
        android:textColor="#FFFFFF"
        android:textSize="12sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/widget_secondary"
        android:layout_width="match_parent"
        android:layout_height="30dp"
        android:layout_marginTop="6dp"
        android:ellipsize="end"
        android:gravity="center_vertical"
        android:maxLines="1"
        android:paddingLeft="4dp"
        android:paddingRight="4dp"
        android:text="@string/relateai_widget_open_events"
        android:textColor="#174A63"
        android:textSize="12sp" />

    <TextView
        android:id="@+id/widget_tertiary"
        android:layout_width="match_parent"
        android:layout_height="30dp"
        android:ellipsize="end"
        android:gravity="center_vertical"
        android:maxLines="1"
        android:paddingLeft="4dp"
        android:paddingRight="4dp"
        android:text="@string/relateai_widget_open_messages"
        android:textColor="#174A63"
        android:textSize="12sp" />

    <TextView
        android:id="@+id/widget_privacy"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:ellipsize="end"
        android:maxLines="1"
        android:text="@string/relateai_widget_privacy_note"
        android:textColor="#6B7280"
        android:textSize="10sp" />
</LinearLayout>
`;

const renderWidgetProviderJava = androidPackage => {
  const providerPackage = `${androidPackage}.widget`;

  return `package ${providerPackage};

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;
import ${androidPackage}.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

public class ${WIDGET_PROVIDER_CLASS} extends AppWidgetProvider {
  public static final String PREFS_NAME = "relateai_home_widget";
  public static final String SUMMARY_KEY = "summary";
  public static final String REFRESH_ACTION = "${androidPackage}.widget.REFRESH";

  @Override
  public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
    for (int appWidgetId : appWidgetIds) {
      updateWidget(context, appWidgetManager, appWidgetId);
    }
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    super.onReceive(context, intent);
    if (intent != null && REFRESH_ACTION.equals(intent.getAction())) {
      AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
      ComponentName componentName = new ComponentName(context, ${WIDGET_PROVIDER_CLASS}.class);
      onUpdate(context, appWidgetManager, appWidgetManager.getAppWidgetIds(componentName));
    }
  }

  private static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.${WIDGET_LAYOUT_NAME});
    JSONObject summary = readFreshSummary(context);
    if (summary == null) {
      bindFallback(context, views, appWidgetId);
    } else {
      bindSummary(context, views, appWidgetId, summary);
    }
    appWidgetManager.updateAppWidget(appWidgetId, views);
  }

  private static JSONObject readFreshSummary(Context context) {
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String raw = prefs.getString(SUMMARY_KEY, null);
    if (raw == null || raw.trim().isEmpty()) {
      return null;
    }
    try {
      JSONObject summary = new JSONObject(raw);
      if (isExpired(summary.optString("expiresAt", ""))) {
        return null;
      }
      return summary;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static boolean isExpired(String expiresAt) {
    if (expiresAt == null || expiresAt.isEmpty()) {
      return true;
    }
    try {
      SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
      format.setTimeZone(TimeZone.getTimeZone("UTC"));
      Date expires = format.parse(expiresAt);
      return expires == null || expires.getTime() < System.currentTimeMillis();
    } catch (Exception ignored) {
      return true;
    }
  }

  private static void bindFallback(Context context, RemoteViews views, int appWidgetId) {
    views.setTextViewText(R.id.widget_title, context.getString(R.string.relateai_widget_default_title));
    views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.relateai_widget_default_subtitle));
    views.setTextViewText(R.id.widget_privacy, context.getString(R.string.relateai_widget_privacy_note));
    bindAction(context, views, R.id.widget_root, "home", appWidgetId, 0, context.getString(R.string.relateai_widget_open_home));
    bindAction(context, views, R.id.widget_primary, "home", appWidgetId, 1, context.getString(R.string.relateai_widget_open_home));
    bindAction(context, views, R.id.widget_secondary, "events", appWidgetId, 2, context.getString(R.string.relateai_widget_open_events));
    bindAction(context, views, R.id.widget_tertiary, "messages", appWidgetId, 3, context.getString(R.string.relateai_widget_open_messages));
  }

  private static void bindSummary(Context context, RemoteViews views, int appWidgetId, JSONObject summary) {
    String title = sanitizeText(summary.optString("title"), context.getString(R.string.relateai_widget_default_title), 48);
    String subtitle = sanitizeText(summary.optString("subtitle"), context.getString(R.string.relateai_widget_default_subtitle), 96);
    String privacy = sanitizeText(summary.optString("privacyNote"), context.getString(R.string.relateai_widget_privacy_note), 96);
    JSONArray tiles = summary.optJSONArray("tiles");

    views.setTextViewText(R.id.widget_title, title);
    views.setTextViewText(R.id.widget_subtitle, subtitle);
    views.setTextViewText(R.id.widget_privacy, privacy);
    views.setContentDescription(R.id.widget_root, title + ". " + subtitle);

    if (tiles == null || tiles.length() == 0) {
      bindFallback(context, views, appWidgetId);
      return;
    }

    bindTile(context, views, R.id.widget_primary, tiles, 0, appWidgetId, 1);
    bindTile(context, views, R.id.widget_secondary, tiles, 1, appWidgetId, 2);
    bindTile(context, views, R.id.widget_tertiary, tiles, 2, appWidgetId, 3);
    JSONObject firstTile = tiles.optJSONObject(0);
    bindAction(context, views, R.id.widget_root, safeRoute(firstTile), appWidgetId, 0, title);
  }

  private static void bindTile(Context context, RemoteViews views, int viewId, JSONArray tiles, int index, int appWidgetId, int requestCode) {
    JSONObject tile = tiles.optJSONObject(index);
    if (tile == null) {
      views.setViewVisibility(viewId, View.GONE);
      return;
    }
    views.setViewVisibility(viewId, View.VISIBLE);
    String label = sanitizeText(tile.optString("title"), context.getString(R.string.relateai_widget_open_home), 64);
    String route = safeRoute(tile);
    views.setTextViewText(viewId, label);
    bindAction(context, views, viewId, route, appWidgetId, requestCode, label);
  }

  private static String safeRoute(JSONObject tile) {
    if (tile == null) {
      return "home";
    }
    JSONObject route = tile.optJSONObject("route");
    String screen = route == null ? "" : route.optString("screen", "");
    if ("events".equals(screen) || "messages".equals(screen) || "more".equals(screen)) {
      return screen;
    }
    return "home";
  }

  private static void bindAction(Context context, RemoteViews views, int viewId, String route, int appWidgetId, int requestCode, String label) {
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriForRoute(route)));
    intent.setPackage(context.getPackageName());
    PendingIntent pendingIntent = PendingIntent.getActivity(
      context,
      appWidgetId * 10 + requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
    );
    views.setOnClickPendingIntent(viewId, pendingIntent);
    views.setContentDescription(viewId, label);
  }

  private static String uriForRoute(String route) {
    if ("events".equals(route)) {
      return "relateai://events";
    }
    if ("messages".equals(route)) {
      return "relateai://messages";
    }
    if ("more".equals(route)) {
      return "relateai://more";
    }
    return "relateai://home";
  }

  private static String sanitizeText(String value, String fallback, int maxLength) {
    String cleaned = value == null || value.trim().isEmpty() ? fallback : value;
    cleaned = cleaned.replaceAll("[\\\\r\\\\n]+", " ").trim();
    cleaned = cleaned.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\\\.[A-Z]{2,}", "[email hidden]");
    cleaned = cleaned.replaceAll("\\\\+?[0-9][0-9 .()\\\\-]{7,}[0-9]", "[route hidden]");
    if (cleaned.length() > maxLength) {
      return cleaned.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
    return cleaned;
  }
}
`;
};

const renderWidgetModuleJava = androidPackage => {
  const providerPackage = `${androidPackage}.widget`;

  return `package ${providerPackage};

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import org.json.JSONObject;

public class ${WIDGET_MODULE_CLASS} extends ReactContextBaseJavaModule {
  private final ReactApplicationContext reactContext;

  public ${WIDGET_MODULE_CLASS}(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @NonNull
  @Override
  public String getName() {
    return "RelateAiHomeWidget";
  }

  @ReactMethod
  public void updateHomeWidget(String summaryJson, Promise promise) {
    try {
      if (summaryJson == null || summaryJson.trim().isEmpty()) {
        throw new IllegalArgumentException("Widget summary is required.");
      }
      new JSONObject(summaryJson);
      SharedPreferences prefs = reactContext.getSharedPreferences(${WIDGET_PROVIDER_CLASS}.PREFS_NAME, Context.MODE_PRIVATE);
      prefs.edit().putString(${WIDGET_PROVIDER_CLASS}.SUMMARY_KEY, summaryJson).apply();
      refreshWidgets();
      promise.resolve(null);
    } catch (Exception error) {
      promise.reject("RELATEAI_WIDGET_UPDATE_FAILED", error);
    }
  }

  @ReactMethod
  public void clearHomeWidget(Promise promise) {
    try {
      SharedPreferences prefs = reactContext.getSharedPreferences(${WIDGET_PROVIDER_CLASS}.PREFS_NAME, Context.MODE_PRIVATE);
      prefs.edit().remove(${WIDGET_PROVIDER_CLASS}.SUMMARY_KEY).apply();
      refreshWidgets();
      promise.resolve(null);
    } catch (Exception error) {
      promise.reject("RELATEAI_WIDGET_CLEAR_FAILED", error);
    }
  }

  private void refreshWidgets() {
    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(reactContext);
    ComponentName componentName = new ComponentName(reactContext, ${WIDGET_PROVIDER_CLASS}.class);
    int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
    if (appWidgetIds.length == 0) {
      return;
    }
    Intent intent = new Intent(reactContext, ${WIDGET_PROVIDER_CLASS}.class);
    intent.setAction(${WIDGET_PROVIDER_CLASS}.REFRESH_ACTION);
    intent.setPackage(reactContext.getPackageName());
    reactContext.sendBroadcast(intent);
  }
}
`;
};

const renderWidgetPackageJava = androidPackage => {
  const providerPackage = `${androidPackage}.widget`;

  return `package ${providerPackage};

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ${WIDGET_PACKAGE_CLASS} implements ReactPackage {
  @Override
  public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
    return Arrays.<NativeModule>asList(new ${WIDGET_MODULE_CLASS}(reactContext));
  }

  @Override
  public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
    return Collections.emptyList();
  }
}
`;
};

const addWidgetPackageToMainApplication = (contents, androidPackage) => {
  const javaPackage = `${androidPackage}.widget.${WIDGET_PACKAGE_CLASS}`;
  const kotlinApplyRegistration = `add(${javaPackage}())`;
  const kotlinListRegistration = `packages.add(${javaPackage}())`;
  const javaRegistration = `packages.add(new ${javaPackage}());`;

  if (
    contents.includes(kotlinApplyRegistration) ||
    contents.includes(kotlinListRegistration) ||
    contents.includes(javaRegistration)
  ) {
    return contents;
  }

  const kotlinApplyAnchor = /^([ \t]*).*PackageList\(this\)\.packages\.apply\s*\{\s*$/m;
  const kotlinApplyMatch = contents.match(kotlinApplyAnchor);
  if (kotlinApplyMatch) {
    const indentation = `${kotlinApplyMatch[1]}  `;
    return CodeGenerator.mergeContents({
      src: contents,
      newSrc: `${indentation}${kotlinApplyRegistration}`,
      tag: MAIN_APPLICATION_GENERATED_TAG,
      anchor: kotlinApplyAnchor,
      offset: 1,
      comment: `${indentation}//`
    }).contents;
  }

  if (contents.includes('PackageList(this).packages') && contents.includes('return packages')) {
    const returnAnchor = /^([ \t]*)return packages\s*$/m;
    const returnMatch = contents.match(returnAnchor);
    if (returnMatch) {
      const indentation = returnMatch[1];
      return CodeGenerator.mergeContents({
        src: contents,
        newSrc: `${indentation}${kotlinListRegistration}`,
        tag: MAIN_APPLICATION_GENERATED_TAG,
        anchor: returnAnchor,
        offset: 0,
        comment: `${indentation}//`
      }).contents;
    }
  }

  if (contents.includes('new PackageList(this).getPackages()') && contents.includes('return packages;')) {
    const returnAnchor = /^([ \t]*)return packages;\s*$/m;
    const returnMatch = contents.match(returnAnchor);
    if (returnMatch) {
      const indentation = returnMatch[1];
      return CodeGenerator.mergeContents({
        src: contents,
        newSrc: `${indentation}${javaRegistration}`,
        tag: MAIN_APPLICATION_GENERATED_TAG,
        anchor: returnAnchor,
        offset: 0,
        comment: `${indentation}//`
      }).contents;
    }
  }

  throw new Error('RelateAI home widget could not register its React Native package in MainApplication.');
};

const addWidgetReceiverToManifest = (androidManifest, androidPackage) => {
  const application = AndroidConfig.Manifest.getMainApplicationOrThrow(androidManifest);
  const receiverName = `${androidPackage}.widget.${WIDGET_PROVIDER_CLASS}`;
  application.receiver = (application.receiver ?? []).filter(item => item.$?.['android:name'] !== receiverName);
  application.receiver.push({
    $: {
      'android:name': receiverName,
      'android:exported': 'false'
    },
    'intent-filter': [
      {
        action: [
          {
            $: {
              'android:name': 'android.appwidget.action.APPWIDGET_UPDATE'
            }
          }
        ]
      }
    ],
    'meta-data': [
      {
        $: {
          'android:name': WIDGET_METADATA_NAME,
          'android:resource': `@xml/${WIDGET_PROVIDER_RESOURCE_NAME}`
        }
      }
    ]
  });
  return androidManifest;
};

const withWidgetStrings = config =>
  withStringsXml(config, config => {
    const stringItems = widgetStringItems.map(([name, value]) =>
      AndroidConfig.Resources.buildResourceItem({
        name,
        value,
        translatable: true
      })
    );

    config.modResults = AndroidConfig.Strings.setStringItem(stringItems, config.modResults);
    return config;
  });

const withWidgetResources = config =>
  withDangerousMod(config, [
    'android',
    async config => {
      const androidPackage = resolveAndroidPackage(config, 'RelateAI home widget');

      const androidRoot = path.join(config.modRequest.projectRoot, 'android/app/src/main');
      const xmlDir = path.join(androidRoot, 'res/xml');
      const layoutDir = path.join(androidRoot, 'res/layout');
      const drawableDir = path.join(androidRoot, 'res/drawable');
      const providerDir = path.join(androidRoot, 'java', androidPackagePath(androidPackage), 'widget');

      await writeGeneratedFileAsync(
        path.join(xmlDir, `${WIDGET_PROVIDER_RESOURCE_NAME}.xml`),
        renderWidgetProviderInfoXml()
      );
      await writeGeneratedFileAsync(path.join(layoutDir, `${WIDGET_LAYOUT_NAME}.xml`), renderWidgetLayoutXml());
      await writeGeneratedFileAsync(
        path.join(drawableDir, `${WIDGET_BACKGROUND_NAME}.xml`),
        renderWidgetBackgroundXml()
      );
      await writeGeneratedFileAsync(
        path.join(drawableDir, `${WIDGET_ACTION_BACKGROUND_NAME}.xml`),
        renderWidgetActionBackgroundXml()
      );
      await writeGeneratedFileAsync(
        path.join(providerDir, `${WIDGET_PROVIDER_CLASS}.java`),
        renderWidgetProviderJava(androidPackage)
      );
      await writeGeneratedFileAsync(
        path.join(providerDir, `${WIDGET_MODULE_CLASS}.java`),
        renderWidgetModuleJava(androidPackage)
      );
      await writeGeneratedFileAsync(
        path.join(providerDir, `${WIDGET_PACKAGE_CLASS}.java`),
        renderWidgetPackageJava(androidPackage)
      );
      return config;
    }
  ]);

const withWidgetManifest = config =>
  withAndroidManifest(config, config => {
    const androidPackage = resolveAndroidPackage(config, 'RelateAI home widget');
    config.modResults = addWidgetReceiverToManifest(config.modResults, androidPackage);
    return config;
  });

const withWidgetMainApplication = config =>
  withMainApplication(config, config => {
    const androidPackage = resolveAndroidPackage(config, 'RelateAI home widget');
    config.modResults.contents = addWidgetPackageToMainApplication(config.modResults.contents, androidPackage);
    return config;
  });

const validateWidgetGenerationAsync = async (projectRoot, androidPackage) => {
  const androidRoot = path.join(projectRoot, 'android/app/src/main');
  const manifest = await XML.readXMLAsync({ path: path.join(androidRoot, 'AndroidManifest.xml') });
  const application = AndroidConfig.Manifest.getMainApplicationOrThrow(manifest);
  const receiverName = `${androidPackage}.widget.${WIDGET_PROVIDER_CLASS}`;
  const receivers = (application.receiver ?? []).filter(item => item.$?.['android:name'] === receiverName);

  assertCondition(receivers.length === 1, `RelateAI home widget expected exactly one ${receiverName} receiver.`);
  assertCondition(receivers[0].$?.['android:exported'] === 'false', 'RelateAI home widget receiver must not be exported.');
  assertCondition(
    receivers[0]['intent-filter']?.[0]?.action?.[0]?.$?.['android:name'] ===
      'android.appwidget.action.APPWIDGET_UPDATE',
    'RelateAI home widget receiver is missing APPWIDGET_UPDATE.'
  );
  assertCondition(
    receivers[0]['meta-data']?.[0]?.$?.['android:name'] === WIDGET_METADATA_NAME &&
      receivers[0]['meta-data']?.[0]?.$?.['android:resource'] === `@xml/${WIDGET_PROVIDER_RESOURCE_NAME}`,
    'RelateAI home widget receiver metadata is invalid.'
  );

  const providerInfo = await XML.readXMLAsync({
    path: path.join(androidRoot, 'res/xml', `${WIDGET_PROVIDER_RESOURCE_NAME}.xml`)
  });
  assertCondition(
    providerInfo['appwidget-provider']?.$?.['android:initialLayout'] === `@layout/${WIDGET_LAYOUT_NAME}`,
    'RelateAI home widget provider metadata references an invalid layout.'
  );

  const layout = await XML.readXMLAsync({
    path: path.join(androidRoot, 'res/layout', `${WIDGET_LAYOUT_NAME}.xml`)
  });
  const layoutIds = new Set();
  const collectLayoutIds = node => {
    if (!node || typeof node !== 'object') return;
    if (node.$?.['android:id']) {
      layoutIds.add(node.$['android:id'].replace(/^@\+?id\//, ''));
    }
    Object.values(node).forEach(value => {
      if (Array.isArray(value)) value.forEach(collectLayoutIds);
      else if (value && typeof value === 'object' && value !== node.$) collectLayoutIds(value);
    });
  };
  collectLayoutIds(layout);

  const providerDir = path.join(androidRoot, 'java', androidPackagePath(androidPackage), 'widget');
  const providerSource = await fs.promises.readFile(path.join(providerDir, `${WIDGET_PROVIDER_CLASS}.java`), 'utf8');
  const moduleSource = await fs.promises.readFile(path.join(providerDir, `${WIDGET_MODULE_CLASS}.java`), 'utf8');
  const packageSource = await fs.promises.readFile(path.join(providerDir, `${WIDGET_PACKAGE_CLASS}.java`), 'utf8');
  const packageDeclaration = `package ${androidPackage}.widget;`;
  assertCondition(
    [providerSource, moduleSource, packageSource].every(contents => contents.startsWith(packageDeclaration)),
    'RelateAI home widget generated Java package declarations do not match expo.android.package.'
  );

  for (const match of providerSource.matchAll(/R\.id\.([A-Za-z0-9_]+)/g)) {
    assertCondition(layoutIds.has(match[1]), `RelateAI home widget generated Java references missing layout id ${match[1]}.`);
  }

  const strings = await XML.readXMLAsync({ path: path.join(androidRoot, 'res/values/strings.xml') });
  const stringNames = new Set((strings.resources?.string ?? []).map(item => item.$?.name));
  for (const [name] of widgetStringItems) {
    assertCondition(stringNames.has(name), `RelateAI home widget is missing string resource ${name}.`);
  }
  for (const match of providerSource.matchAll(/R\.string\.([A-Za-z0-9_]+)/g)) {
    assertCondition(stringNames.has(match[1]), `RelateAI home widget generated Java references missing string ${match[1]}.`);
  }

  const mainApplicationPath = AndroidConfig.Paths.getProjectFilePath(projectRoot, 'MainApplication');
  const mainApplication = await fs.promises.readFile(mainApplicationPath, 'utf8');
  const registrationClass = `${androidPackage}.widget.${WIDGET_PACKAGE_CLASS}`;
  assertCondition(
    countOccurrences(mainApplication, registrationClass) === 1,
    'RelateAI home widget expected exactly one MainApplication package registration.'
  );
};

const withWidgetValidation = config =>
  withFinalizedMod(config, [
    'android',
    async config => {
      const androidPackage = resolveAndroidPackage(config, 'RelateAI home widget');
      await validateWidgetGenerationAsync(config.modRequest.projectRoot, androidPackage);
      return config;
    }
  ]);

const withRelateAiHomeWidget = config => {
  config = withWidgetStrings(config);
  config = withWidgetResources(config);
  config = withWidgetManifest(config);
  config = withWidgetMainApplication(config);
  config = withWidgetValidation(config);
  return config;
};

module.exports = createRunOncePlugin(withRelateAiHomeWidget, 'with-relateai-home-widget', '2.0.0');
module.exports.addWidgetPackageToMainApplication = addWidgetPackageToMainApplication;
module.exports.addWidgetReceiverToManifest = addWidgetReceiverToManifest;
module.exports.renderWidgetActionBackgroundXml = renderWidgetActionBackgroundXml;
module.exports.renderWidgetBackgroundXml = renderWidgetBackgroundXml;
module.exports.renderWidgetLayoutXml = renderWidgetLayoutXml;
module.exports.renderWidgetModuleJava = renderWidgetModuleJava;
module.exports.renderWidgetPackageJava = renderWidgetPackageJava;
module.exports.renderWidgetProviderInfoXml = renderWidgetProviderInfoXml;
module.exports.renderWidgetProviderJava = renderWidgetProviderJava;
module.exports.validateWidgetGenerationAsync = validateWidgetGenerationAsync;
