import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';

const source = readFileSync(new URL('./createProductionRuntime.ts', import.meta.url), 'utf8');

describe('production runtime composition contract', () => {
  it('uses protected storage, ordered persistence, live permissions, reminders, and privacy-safe issues', () => {
    assert.match(source, /secureStateStore/);
    assert.match(source, /PersistenceCoordinator/);
    assert.match(source, /createNativePermissionReminderCoordinator/);
    assert.match(source, /permissionsReconciled/);
    assert.match(source, /reminderPlansReconciled/);
    assert.match(source, /appOperationalIssues/);
  });

  it('hydrates and commits only through the migrating encrypted entity repository', () => {
    assert.match(source, /loadDefaultMigratingEntityRepository/);
    assert.match(source, /createEntityRepositoryPersistenceAdapter/);
    assert.match(source, /loadEntityRepositoryState/);
    assert.match(source, /pruneRollbackGenerations/);
    assert.doesNotMatch(source, /loadStateWithRecovery|inspectPersistedState|saveState\(/);
  });

  it('keeps lifecycle side effects outside the reducer and synchronizes the privacy-filtered widget', () => {
    assert.match(source, /AppRuntimeController/);
    assert.match(source, /localizeHomeWidgetSummary\(buildHomeWidgetSummary/);
    assert.doesNotMatch(source, /AsyncStorage|Alert\.alert/);
  });

  it('connects the bounded command and navigation runtimes to real production adapters', () => {
    assert.match(source, /HarnessCommandRuntime/);
    assert.match(source, /NavigationRuntimeController/);
    assert.match(source, /dispatchAndCommit/);
    assert.match(source, /importDeviceContacts/);
    assert.match(source, /importEventsFromDeviceCalendar/);
    assert.match(source, /exportEventsToDeviceCalendar/);
    assert.match(source, /requestAiDraft/);
    assert.match(source, /sendEmailMessage/);
    assert.match(source, /restoreLocalDataTransaction/);
    assert.match(source, /clearLocalDataTransaction/);
    assert.match(source, /runDataReplacement: operation => runtime\.runDataReplacement\(operation\)/);
    assert.match(source, /authoritativeState = await repository\.loadState\(\)/);
    assert.match(source, /runtime\.installVerifiedState\(authoritativeState\)/);
  });
});
