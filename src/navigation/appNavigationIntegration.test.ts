import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';

const appSource = readFileSync(join(process.cwd(), 'src/App.tsx'), 'utf8');

describe('App typed navigation integration contract', () => {
  it('routes existing navigate actions through typed push/replace history', () => {
    assert.match(appSource, /const \[state, rawDispatch\] = useReducer/);
    assert.match(appSource, /action\.type === 'navigate'/);
    assert.match(appSource, /preservesNavigationOrigin\(action\.screen\) \? 'push' : 'replace'/);
    assert.match(appSource, /currentNavigationRoute\(transition\.state\)/);
    assert.match(appSource, /screen === 'wishPreview'/);
    assert.match(appSource, /screen === 'manualComposer'/);
    assert.match(appSource, /screen === 'contactDetail'/);
  });

  it('uses stack history for the visible Back action instead of screen-specific ternaries', () => {
    const start = appSource.indexOf('const routeBack = () =>');
    const end = appSource.indexOf('const lockDecision', start);
    const routeBackSource = appSource.slice(start, end);

    assert.ok(start >= 0 && end > start);
    assert.match(routeBackSource, /window\.history\.back\(\)/);
    assert.match(routeBackSource, /commitNavigation\(\{ type: 'back', source: 'ui' \}\)/);
    assert.doesNotMatch(routeBackSource, /state\.activeScreen ===/);
  });

  it('consumes Android hardware back only when the navigation reducer handles it', () => {
    assert.match(appSource, /BackHandler\.addEventListener\('hardwareBackPress'/);
    assert.match(appSource, /source: 'android-hardware'/);
    assert.match(
      appSource,
      /return transition\.outcome\.back\?\.disposition === 'consumed'/
    );
  });

  it('restores serializable browser history on popstate with stale-safe entity resolution', () => {
    assert.match(appSource, /window\.addEventListener\('popstate', handleBrowserHistory\)/);
    assert.match(appSource, /readBrowserNavigationHistoryState\(/);
    assert.match(appSource, /browserHistoryDepthRef\.current = snapshot\.depth/);
    assert.match(appSource, /navigationStateRef\.current = snapshot\.navigation/);
    assert.match(appSource, /source: 'browser-history'/);
  });

  it('reconciles navigation references whenever contacts or messages change', () => {
    assert.match(appSource, /\{ type: 'reconcile' \}/);
    assert.match(appSource, /state\.contacts,/);
    assert.match(appSource, /state\.messages,/);
    assert.match(appSource, /resolveNavigationDestination\(desiredDestination, entities\)/);
    assert.match(appSource, /browserHistoryMode = 'push'/);
  });
});
