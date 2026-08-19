import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = path => readFileSync(path, 'utf8');

test('Android destination blocking is a configuration CAS with persistent final gates', () => {
  const controller = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/configuration/AndroidConfigurationController.kt',
  );
  const dao = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/storage/database/ConfigurationDao.kt',
  );
  const ledger = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/storage/database/SafetyLedgerDao.kt',
  );

  assert.match(
    controller,
    /mutateSelectedDestinationBlock[\s\S]*?mutationGate\(expectedRevision\)/u,
  );
  assert.match(controller, /phone\.destinationFingerprint/u);
  assert.match(controller, /revokeApprovalsForDestination/u);
  assert.match(controller, /cancelUnclaimedOccurrencesForDestination/u);
  assert.match(controller, /bumpControlBlocker/u);
  assert.match(dao, /INSERT|insertDestinationBlock/u);
  assert.match(
    dao,
    /state IN \('PLANNED', 'PREPARED', 'SCHEDULED', 'COORDINATION_BLOCKED'\)/u,
  );
  assert.match(dao, /NOT EXISTS\([\s\S]*destination_blocks_v2/u);
  assert.match(ledger, /activeDestinationBlockCount[\s\S]*?return null/u);
});

test('React Native sends only opaque contact and revision for a reviewed block action', () => {
  const port = read('src/application/ports/PeoplePort.ts');
  const adapter = read('src/infrastructure/native/BirthdayNativeAdapter.ts');
  const screen = read('src/features/live/LivePersonDetailScreen.tsx');
  const schema = read('src/infrastructure/native/featureSchemas.ts');

  assert.match(
    port,
    /blockRecipientDestination\([\s\S]*?RevisionedContactCommand/u,
  );
  assert.match(
    adapter,
    /blockRecipientDestination: 'block-recipient-destination'/u,
  );
  assert.match(
    screen,
    /live-person-confirm-destination-\$\{currentDestinationReview\.kind\}/u,
  );
  assert.match(
    screen,
    /const input = \{[\s\S]*?contactId: review\.contactId,[\s\S]*?expectedRevision: review\.sourceRevision,[\s\S]*?\}/u,
  );
  assert.doesNotMatch(screen, /normalizedDestination/u);
  assert.match(schema, /selectedDestinationBlocked: z\.boolean\(\)/u);
});
