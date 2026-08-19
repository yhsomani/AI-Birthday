import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');
const promptContract = JSON.parse(
  read('contracts/gemini-prompt-policy-v2.json'),
);

const concatenatedString = (source, startMarker, endMarker) => {
  const start = source.indexOf(startMarker);
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.ok(start >= 0 && end > start, `missing ${startMarker}`);
  const values = [
    ...source.slice(start, end).matchAll(/"((?:\\.|[^"\\])*)"/gu),
  ];
  return values.map(match => JSON.parse(`"${match[1]}"`)).join('');
};

const androidGateway = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/AndroidGeminiSuggestionGateway.kt',
);
const androidOperationalGate = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/AndroidGeminiOperationalGate.kt',
);
const androidAppGraph = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/AppGraph.kt',
);
const androidMainApplication = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/MainApplication.kt',
);
const androidMainActivity = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/MainActivity.kt',
);
const androidProvenance = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/GeminiCandidateProvenanceRegistry.kt',
);
const liveMessage = read('src/features/live/LiveMessageScreen.tsx');
const messageModel = read('src/domain/messages/model.ts');
const androidReleaseRunbook = read(
  'docs/ANDROID_RESTRICTED_RELEASE_EVIDENCE.md',
);

test('Android implements the exact canonical Gemini v2 prompt and account-scope contract', () => {
  assert.equal(promptContract.schemaVersion, 1);
  assert.equal(promptContract.policyVersion, 'birthday-greeting-prompt-v2');
  assert.equal(promptContract.maximumRetainedRateScopes, 8);

  assert.match(
    androidGateway,
    new RegExp(
      `PROMPT_POLICY_VERSION = "${promptContract.policyVersion}"`,
      'u',
    ),
  );
  const androidInstruction = concatenatedString(
    androidGateway,
    'const val SYSTEM_INSTRUCTION',
    'fun parseRequest',
  );
  assert.equal(androidInstruction, promptContract.systemInstruction);

  assert.ok(
    androidGateway.includes(`MODEL_NAME = "${promptContract.model.name}"`),
  );
  assert.ok(
    androidGateway.includes(
      `MODEL_LOCATION = "${promptContract.model.location}"`,
    ),
  );
  assert.ok(
    androidGateway.includes(
      `MODEL_IDENTIFIER = "${promptContract.model.identifier}"`,
    ),
  );
  assert.match(androidGateway, /setOf\("en", "hi"\)/u);
  assert.match(androidGateway, /setOf\("warm", "simple", "cheerful"\)/u);

  for (const domain of [
    promptContract.accountSessionDigestDomain,
    promptContract.rateScopeDigestDomain,
  ]) {
    assert.equal(
      (
        `${androidGateway}\n${androidProvenance}`.match(
          new RegExp(domain.replaceAll('.', '\\.'), 'gu'),
        ) ?? []
      ).length,
      1,
    );
  }
  assert.match(
    androidGateway,
    /tryAcquire\(accountSessionKey, wallClockMillis\(\), elapsedClockMillis\(\)\)/u,
  );
  assert.match(androidGateway, /MAXIMUM_GEMINI_RATE_SCOPES = 8/u);
  assert.match(
    androidAppGraph,
    /accountGeneration = \{[\s\S]*?currentOrNull\(\)\?\.callbackGeneration/u,
  );
  assert.ok(
    (
      androidAppGraph.match(
        /clearAndroidGeminiLocalRateState\(appContext\)/gu,
      ) ?? []
    ).length >= 2,
  );
  assert.doesNotMatch(
    androidGateway,
    /putString\([^\n]*(?:firebaseUid|googleSubject)/u,
  );
});

test('native Gemini dependencies are exact and use one Firebase family', () => {
  const gradle = read('android/app/build.gradle');
  const readme = read('README.md');
  assert.match(
    gradle,
    /implementation\("com\.google\.firebase:firebase-ai:17\.13\.0"\)/u,
  );
  assert.match(
    gradle,
    /implementation\("com\.google\.firebase:firebase-appcheck-playintegrity:19\.2\.0"\)/u,
  );
  assert.match(
    gradle,
    /implementation\("com\.google\.firebase:firebase-config:23\.1\.0"\)/u,
  );
  assert.doesNotMatch(androidGateway, /firebase\.ai\.ondevice|OnDevice/u);
  assert.match(readme, /beta-labeled on-device interop module/u);
  assert.doesNotMatch(gradle, /exclude[^\n]*firebase-ai-ondevice-interop/u);
});

test('Android gateway pins Vertex global, stable model, limited-use tokens and bounded output', () => {
  assert.match(androidGateway, /gemini-3\.5-flash/u);
  assert.match(
    androidGateway,
    /GenerativeBackend\.vertexAI\(MODEL_LOCATION\)/u,
  );
  assert.match(androidGateway, /const val MODEL_LOCATION = "global"/u);
  assert.match(androidGateway, /FirebaseAI\.getInstance\([\s\S]*?true,/u);
  assert.match(androidGateway, /maxOutputTokens = 512/u);
  assert.match(
    androidGateway,
    /RequestOptions\(timeoutInMillis = REQUEST_TIMEOUT_MILLIS\)/u,
  );
  assert.match(androidGateway, /const val REQUEST_TIMEOUT_MILLIS = 12_000L/u);
  assert.doesNotMatch(androidGateway, /GenerativeBackend\.googleAI/u);
  assert.doesNotMatch(androidGateway, /AIza[A-Za-z0-9_-]{20,}/u);
});

test('prompt builders interpolate only closed policy fields and provider content is ephemeral', () => {
  for (const forbidden of [
    'contactName',
    'phoneNumber',
    'birthdayDate',
    'messageHistory',
    'resourceName',
    'accessToken',
    'idToken',
  ]) {
    assert.doesNotMatch(
      androidGateway.match(/fun prompt[\s\S]*?fun validatedCandidates/u)?.[0] ??
        '',
      new RegExp(forbidden, 'u'),
    );
  }
  assert.match(androidGateway, /value\.keyNames\(\) != setOf\(/u);
  assert.match(androidGateway, /minItems\s*[=:]\s*1/u);
  assert.match(androidGateway, /maxItems\s*[=:]\s*3/u);
  assert.match(androidGateway, /MessageTemplateValidator/u);
  assert.doesNotMatch(
    androidGateway,
    /print\s*\(|Log\.[dievw]\s*\(|Logger\s*\(/u,
  );
  assert.doesNotMatch(androidGateway, /putString\([^\n]*(?:prompt|response)/iu);
});

test('Android Gemini is fail-closed behind one bounded native Remote Config switch', () => {
  assert.match(
    androidOperationalGate,
    /PARAMETER_KEY = "gemini_suggestions_enabled"/u,
  );
  assert.match(androidOperationalGate, /IN_APP_DEFAULT = false/u);
  assert.match(
    androidOperationalGate,
    /sourceIsRemote && canonicalString == "true" && boolValue/u,
  );
  assert.match(
    androidOperationalGate,
    /MINIMUM_FETCH_INTERVAL_SECONDS = 60L \* 60L/u,
  );
  assert.match(androidOperationalGate, /FIREBASE_FETCH_TIMEOUT_SECONDS = 8L/u);
  assert.match(
    androidOperationalGate,
    /LOCAL_COMPLETION_TIMEOUT_MILLIS = 10_000L/u,
  );
  assert.match(
    androidOperationalGate,
    /setMinimumFetchIntervalInSeconds\([\s\S]*?MINIMUM_FETCH_INTERVAL_SECONDS/u,
  );
  assert.match(
    androidOperationalGate,
    /setFetchTimeoutInSeconds\(AndroidGeminiOperationalPolicy\.FIREBASE_FETCH_TIMEOUT_SECONDS\)/u,
  );
  assert.match(
    androidOperationalGate,
    /setDefaultsAsync\([\s\S]*?PARAMETER_KEY\s+to\s+AndroidGeminiOperationalPolicy\.IN_APP_DEFAULT/u,
  );
  assert.match(androidOperationalGate, /remoteConfig\.fetchAndActivate\(\)/u);
  assert.match(
    androidOperationalGate,
    /value\.source == FirebaseRemoteConfig\.VALUE_SOURCE_REMOTE[\s\S]*?value\.asString\(\)[\s\S]*?value\.asBoolean\(\)/u,
  );
  assert.match(
    androidOperationalGate,
    /AndroidIdentityConfigurationResolver\(appContext, BuildConfig\.APP_ENV\)\.resolve\(\)[\s\S]*?IdentityConfigurationResult\.Missing -> return null/u,
  );

  const generationPath =
    androidGateway.match(
      /suspend fun generate\(requestJson:[\s\S]*?fun peekProvenance/u,
    )?.[0] ?? '';
  const switchRead = generationPath.indexOf(
    'operationalGate.foregroundSuggestionsEnabled()',
  );
  const accountBinding = generationPath.indexOf('client.accountSessionKey()');
  const appCheck = generationPath.indexOf('client.appCheckReady()');
  const providerCall = generationPath.indexOf('client.generate(');
  assert.ok(switchRead > 0);
  assert.ok(accountBinding > switchRead);
  assert.ok(appCheck > accountBinding);
  assert.ok(providerCall > appCheck);
  assert.match(
    generationPath,
    /refreshInBackground\(\)[\s\S]*?!operationalGate\.foregroundSuggestionsEnabled\(\)[\s\S]*?fallbackProjection\("policy-suspended"\)/u,
  );
  assert.match(
    androidAppGraph,
    /AndroidGeminiSuggestionGateway\([\s\S]*?operationalGate = geminiOperationalGate/u,
  );
  assert.match(
    androidMainApplication,
    /onCreate\(\)[\s\S]*?configureGeminiOperationalGate\(\)/u,
  );
  assert.match(
    androidMainActivity,
    /onResume\(\)[\s\S]*?refreshGeminiOperationalGate\(\)/u,
  );
  assert.doesNotMatch(
    androidOperationalGate,
    /FirebaseInstallations|installationID|addOnConfigUpdateListener|setCustomSignals|Log\.[dievw]\s*\(|Logger\s*\(/u,
  );
  assert.doesNotMatch(
    `${liveMessage}\n${messageModel}`,
    /gemini_suggestions_enabled|FirebaseRemoteConfig|getInstance\(configuration\.firebaseApp\)/u,
  );
  assert.match(androidReleaseRunbook, /gemini_suggestions_enabled/u);
  assert.match(androidReleaseRunbook, /in-app default is \*\*false\*\*/u);
  assert.match(androidReleaseRunbook, /Firebase's native Installations token/u);
});

test('native provenance is digest-only, bounded, expiring and account-bound', () => {
  assert.match(androidProvenance, /MAXIMUM_ENTRIES = 3/u);
  assert.match(androidProvenance, /DEFAULT_TTL_MILLIS = 15 \* 60 \* 1_000L/u);
  assert.match(androidProvenance, /GeminiAccountSession\.v1/u);
  assert.match(androidProvenance, /GeminiCandidateExactText\.v1/u);
  assert.match(androidGateway, /fun consumeProvenance/u);
  const entryStorage =
    androidProvenance.match(
      /private data class Entry\([\s\S]*?\n\s*\)/u,
    )?.[0] ?? '';
  assert.doesNotMatch(entryStorage, /text|candidate: String/u);
});
