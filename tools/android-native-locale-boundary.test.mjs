import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../', import.meta.url);

const read = async path => readFile(new URL(path, root), 'utf8');

test('Android native locale policy is current, bounded to en/hi, and region preserving', async () => {
  const policy = await read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/localization/AndroidNativeLocalePolicy.kt',
  );

  assert.match(policy, /resources\.configuration\.locales/);
  assert.match(policy, /fun current\(\).*resolve\(localeSource\(\)\)/);
  assert.match(policy, /HINDI_LANGUAGE[\s\S]*else \{[\s\S]*ENGLISH_LANGUAGE/);
  assert.match(policy, /phoneRegion = deviceLocale\?\.country/);
  assert.match(policy, /Locale\.getISOCountries\(\)/);
  assert.doesNotMatch(
    policy,
    /Approval|blockerRevision|ConfigurationDao|PeopleSyncDao/,
  );
});

test('Android projections and People normalization never use attachment-time account locale', async () => {
  const [controller, bridge, service, store, requestFactory, accountEntity] =
    await Promise.all([
      read(
        'android/app/src/main/java/com/yashsomani/birthdayautopilot/configuration/AndroidConfigurationController.kt',
      ),
      read(
        'android/app/src/main/java/com/yashsomani/birthdayautopilot/bridge/BirthdayNativeModule.kt',
      ),
      read(
        'android/app/src/main/java/com/yashsomani/birthdayautopilot/people/AndroidPeopleSyncService.kt',
      ),
      read(
        'android/app/src/main/java/com/yashsomani/birthdayautopilot/people/RoomPeopleSyncStagingStore.kt',
      ),
      read(
        'android/app/src/main/java/com/yashsomani/birthdayautopilot/people/PeopleRequestFactory.kt',
      ),
      read(
        'android/app/src/main/java/com/yashsomani/birthdayautopilot/storage/database/IdentityPolicyEntities.kt',
      ),
    ]);

  for (const authoritativeSource of [controller, bridge, service, store]) {
    assert.doesNotMatch(
      authoritativeSource,
      /account\.localeTag|accountLocaleTag/,
    );
  }
  assert.match(
    controller,
    /nativeLocaleProvider\.current\(\)\.presentationLocale/,
  );
  assert.match(controller, /nativeLocaleProvider\.current\(\)\.phoneRegion/);
  assert.match(bridge, /nativeLocaleProvider\.current\(\)\.presentationLocale/);
  assert.match(service, /val nativeLocale = nativeLocaleProvider\.current\(\)/);
  assert.match(service, /homeRegion = nativeLocale\.phoneRegion/);
  assert.doesNotMatch(store, /previousRegion\s*\?:\s*homeRegion/);
  assert.match(requestFactory, /phoneNormalizationRegion=/);
  assert.match(
    accountEntity,
    /Attachment-time metadata retained for schema compatibility/,
  );
});
