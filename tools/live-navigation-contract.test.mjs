import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const read = file => readFileSync(path.join(projectRoot, file), 'utf8');

test('the production live shell is owned by React Navigation', () => {
  const source = read('src/features/live/LiveAppShell.tsx');

  assert.match(source, /createBottomTabNavigator/u);
  assert.match(source, /createNativeStackNavigator/u);
  assert.match(source, /<NavigationContainer/u);
  assert.match(source, /<Tabs\.Navigator[\s\S]*backBehavior="initialRoute"/u);
  assert.match(source, /<Stack\.Navigator[\s\S]*gestureEnabled: true/u);
  assert.doesNotMatch(source, /\bBackHandler\b/u);
  assert.doesNotMatch(source, /\buseState\b/u);
  assert.doesNotMatch(source, /type LiveRoute\b/u);
});

test('every production tab and live leaf is registered in the native hierarchy', () => {
  const source = read('src/features/live/LiveAppShell.tsx');

  assert.equal(
    (source.match(/<Tabs\.Screen\s+name=/gu) ?? []).length,
    3,
    'the production shell must expose exactly three primary tabs',
  );
  for (const tab of ['Home', 'People', 'Settings']) {
    assert.match(
      source,
      new RegExp(`<Tabs\\.Screen name="${tab}" component=`, 'u'),
      tab,
    );
  }

  for (const route of [
    'Main',
    'Person',
    'Activity',
    'ActivityDetail',
    'Attention',
    'Automation',
    'ComposerReview',
    'Diagnostics',
    'HelpLegal',
    'Message',
    'Privacy',
    'Schedule',
  ]) {
    assert.match(
      source,
      new RegExp(`<Stack\\.Screen(?:\\s+|[^>]*\\s+)name="${route}"`, 'u'),
      route,
    );
  }

  assert.match(source, /Person: Readonly<\{ contactId: ContactId \}>/u);
  assert.match(
    source,
    /ActivityDetail: Readonly<\{ activityId: ActivityId \}>/u,
  );
  assert.match(
    source,
    /navigation\.navigate\('ActivityDetail', \{ activityId: record\.id \}\)/u,
  );
  assert.match(source, /ComposerReview: undefined/u);
});

test('live navigation retains system back, accessibility, theme, and native-route contracts', () => {
  const source = read('src/features/live/LiveAppShell.tsx');

  assert.match(
    source,
    /function navigateToLeafFromTab[\s\S]*navigation\.navigate\('Main', \{ screen: tab \}\);[\s\S]*navigation\.navigate\(leaf\);/u,
  );
  const homeRoute = source.slice(
    source.indexOf('function LiveHomeRoute'),
    source.indexOf('function LivePeopleRoute'),
  );
  const settingsRoute = source.slice(
    source.indexOf('function LiveSettingsRoute'),
    source.indexOf('function LivePersonRoute'),
  );
  const helpRoute = source.slice(
    source.indexOf('function LiveHelpLegalRoute'),
    source.indexOf('function LiveMessageRoute'),
  );
  for (const leaf of [
    'Activity',
    'Attention',
    'Automation',
    'ComposerReview',
  ]) {
    assert.match(
      homeRoute,
      new RegExp(`navigateToLeafFromHome\\(navigation, '${leaf}'\\)`, 'u'),
      `Home must remain the visible origin for ${leaf}`,
    );
  }
  assert.doesNotMatch(
    homeRoute,
    /navigateToLeafFromHome\(navigation, 'Message'\)/u,
    'routine message editing must enter from Settings',
  );
  for (const leaf of [
    'Automation',
    'HelpLegal',
    'Message',
    'Privacy',
    'Schedule',
  ]) {
    assert.match(
      settingsRoute,
      new RegExp(
        `navigateToLeafFromTab\\(navigation, 'Settings', '${leaf}'\\)`,
        'u',
      ),
      `Settings must remain the visible origin for ${leaf}`,
    );
  }
  for (const removedLeaf of [
    'Activity',
    'Attention',
    'ComposerReview',
    'Diagnostics',
  ]) {
    assert.doesNotMatch(
      settingsRoute,
      new RegExp(`['"]${removedLeaf}['"]`, 'u'),
      `${removedLeaf} must not be linked directly from Settings`,
    );
  }
  assert.doesNotMatch(settingsRoute, /navigateToLeafFromHome/u);
  assert.match(
    helpRoute,
    /onOpenDiagnostics=\{\(\) => navigation\.navigate\('Diagnostics'\)\}/u,
  );
  assert.match(source, /useIsFocused/u);
  assert.match(source, /<RouteAccessibilityFocus/u);
  assert.match(source, /accessibilityRole="tablist"/u);
  assert.match(source, /accessibilityRole="tab"/u);
  assert.match(source, /live-tab-home/u);
  assert.match(source, /live-tab-people/u);
  assert.match(source, /live-tab-settings/u);
  assert.match(source, /theme\.isDark \? DarkTheme : DefaultTheme/u);
  assert.match(source, /direction=\{language === 'ar-XB' \? 'rtl' : 'ltr'\}/u);
  assert.match(source, /port\.subscribeRouteAvailable/u);
  assert.match(source, /port\.getPendingRoute/u);
  assert.match(
    source,
    /value\.kind === 'automation-review'[\s\S]*?pendingLeafRef\.current = 'ComposerReview'/u,
  );
  assert.match(source, /value\.kind === 'attention'/u);
  assert.doesNotMatch(
    source,
    /ComposerReview:\s*Readonly|navigate\(['"]ComposerReview['"],\s*\{/u,
    'native route IDs and private message material must not enter navigation params',
  );
  assert.match(source, /onReady=\{flushPendingLeaf\}/u);
});

test('every live stack leaf uses the same native-stack back destination as system Back', () => {
  const source = read('src/features/live/LiveAppShell.tsx');

  for (const route of [
    'Person',
    'Activity',
    'ActivityDetail',
    'Attention',
    'Automation',
    'ComposerReview',
    'Diagnostics',
    'HelpLegal',
    'Message',
    'Privacy',
    'Schedule',
  ]) {
    assert.match(
      source,
      new RegExp(
        `function Live${route}Route[\\s\\S]*?onBack=\\{\\(\\) => navigation\\.goBack\\(\\)\\}`,
        'u',
      ),
      route,
    );
  }
});

test('the fixture navigator mirrors shared tab, gesture, and bidi behavior', () => {
  const fixture = read('src/app/navigation/RootNavigator.tsx');

  assert.match(fixture, /<Tabs\.Navigator[\s\S]*backBehavior="initialRoute"/u);
  assert.match(fixture, /initialRouteName="Home"/u);
  assert.match(fixture, /gestureEnabled: true/u);
  assert.match(fixture, /direction=\{language === 'ar-XB' \? 'rtl' : 'ltr'\}/u);
});
