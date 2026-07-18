import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

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
const iosPolicyPath =
  'ios/BirthdayAutopilot/Gemini/GeminiSuggestionPolicy.swift';
const iosGatewayPath =
  'ios/BirthdayAutopilot/Gemini/IOSGeminiSuggestionGateway.swift';
const iosProvenancePath =
  'ios/BirthdayAutopilot/Gemini/GeminiCandidateProvenanceRegistry.swift';
const iosOperationalGatePath =
  'ios/BirthdayAutopilot/Gemini/IOSGeminiOperationalGate.swift';
const iosOperationalPolicyPath =
  'ios/BirthdayAutopilot/Gemini/IOSGeminiOperationalPolicy.swift';
const iosPolicy = read(iosPolicyPath);
const iosGateway = read(iosGatewayPath);
const iosOperationalGate = read(iosOperationalGatePath);
const iosOperationalPolicy = read(iosOperationalPolicyPath);
const iosProvenance = read(iosProvenancePath);
const androidProvenance = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/GeminiCandidateProvenanceRegistry.kt',
);
const iosWorkflow = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
);
const iosWorkflowModels = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowModels.swift',
);
const iosIdentity = read(
  'ios/BirthdayAutopilot/Identity/IOSGoogleIdentityCoordinator.swift',
);
const iosAppDelegate = read('ios/BirthdayAutopilot/AppDelegate.swift');
const iosProject = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
const podLock = read('ios/Podfile.lock');
const liveMessage = read('src/features/live/LiveMessageScreen.tsx');
const messageModel = read('src/domain/messages/model.ts');
const releaseRunbook = read('docs/IOS_RELEASE_EVIDENCE.md');
const androidReleaseRunbook = read(
  'docs/ANDROID_RESTRICTED_RELEASE_EVIDENCE.md',
);

test('Android and iOS implement the exact canonical Gemini v2 prompt and account-scope contract', () => {
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
  assert.match(
    iosPolicy,
    new RegExp(`promptPolicyVersion = "${promptContract.policyVersion}"`, 'u'),
  );
  const androidInstruction = concatenatedString(
    androidGateway,
    'const val SYSTEM_INSTRUCTION',
    'fun parseRequest',
  );
  const iosInstruction = concatenatedString(
    iosPolicy,
    'static let systemInstruction',
    'static func parseRequest',
  );
  assert.equal(androidInstruction, promptContract.systemInstruction);
  assert.equal(iosInstruction, promptContract.systemInstruction);
  assert.equal(androidInstruction, iosInstruction);

  for (const [source, patterns] of [
    [
      androidGateway,
      [
        `MODEL_NAME = "${promptContract.model.name}"`,
        `MODEL_LOCATION = "${promptContract.model.location}"`,
        `MODEL_IDENTIFIER = "${promptContract.model.identifier}"`,
      ],
    ],
    [
      iosPolicy,
      [
        `modelName = "${promptContract.model.name}"`,
        `modelLocation = "${promptContract.model.location}"`,
        `modelIdentifier = "${promptContract.model.identifier}"`,
      ],
    ],
  ]) {
    for (const pattern of patterns)
      assert.ok(source.includes(pattern), pattern);
  }
  assert.match(androidGateway, /setOf\("en", "hi"\)/u);
  assert.match(androidGateway, /setOf\("warm", "simple", "cheerful"\)/u);
  assert.match(iosPolicy, /\["en", "hi"\]\.contains/u);
  assert.match(iosPolicy, /\["warm", "simple", "cheerful"\]\.contains/u);

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
    assert.equal(
      (iosPolicy.match(new RegExp(domain.replaceAll('.', '\\.'), 'gu')) ?? [])
        .length,
      1,
    );
  }
  assert.match(
    androidGateway,
    /tryAcquire\(accountSessionKey, wallClockMillis\(\), elapsedClockMillis\(\)\)/u,
  );
  assert.match(
    iosGateway,
    /rateGuard\.tryAcquire\([\s\S]*?accountSessionKey: accountSessionKey/u,
  );
  assert.match(androidGateway, /MAXIMUM_GEMINI_RATE_SCOPES = 8/u);
  assert.match(iosPolicy, /maximumRetainedScopes = 8/u);
  assert.match(
    androidAppGraph,
    /accountGeneration = \{[\s\S]*?currentOrNull\(\)\?\.callbackGeneration/u,
  );
  assert.match(
    iosGateway,
    /IOSGeminiAccountScope\.accountSessionKey\([\s\S]*?accountGeneration: binding\.accountGeneration/u,
  );
  assert.ok(
    (
      androidAppGraph.match(
        /clearAndroidGeminiLocalRateState\(appContext\)/gu,
      ) ?? []
    ).length >= 2,
  );
  assert.match(
    iosIdentity,
    /completeSignOutAfterSafetyShutdown\(retainData:[\s\S]*?geminiDataCleared = retainData[\s\S]*?clearLocalDataForAccountWipe/u,
  );
  assert.ok(
    (iosIdentity.match(/clearLocalDataForAccountWipe\(\)/gu) ?? []).length >= 4,
  );
  assert.doesNotMatch(
    `${androidGateway}\n${iosPolicy}`,
    /putString\([^\n]*(?:firebaseUid|googleSubject)|defaults\.set\([^\n]*(?:firebaseUID|googleSubject)/u,
  );
});

test('native Gemini dependencies are exact and use one Firebase family', () => {
  const gradle = read('android/app/build.gradle');
  const podfile = read('ios/Podfile');
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
  for (const pod of [
    'FirebaseCore',
    'FirebaseABTesting',
    'FirebaseAuth',
    'FirebaseAppCheck',
    'FirebaseFunctions',
    'FirebaseAILogic',
    'FirebaseRemoteConfig',
  ]) {
    assert.match(podfile, new RegExp(`pod '${pod}', '12\\.15\\.0'`, 'u'));
  }
  assert.match(podLock, /- FirebaseRemoteConfig \(12\.15\.0\):/u);
  assert.match(podLock, /- FirebaseRemoteConfig \(= 12\.15\.0\)/u);
  assert.match(
    podfile,
    /pod 'FirebaseABTesting', '12\.15\.0', :modular_headers => true/u,
  );
  assert.match(podLock, /- FirebaseABTesting \(12\.15\.0\):/u);
  assert.match(podLock, /- FirebaseABTesting \(= 12\.15\.0\)/u);
  assert.match(podLock, /- FirebaseInstallations \(12\.15\.0\):/u);
  assert.match(podLock, /- FirebaseRemoteConfigInterop \(12\.15\.0\)/u);
  assert.doesNotMatch(`${podfile}\n${podLock}`, /FirebaseAnalytics/u);
  assert.doesNotMatch(androidGateway, /firebase\.ai\.ondevice|OnDevice/u);
  assert.match(readme, /beta-labeled on-device interop module/u);
  assert.doesNotMatch(gradle, /exclude[^\n]*firebase-ai-ondevice-interop/u);
});

test('both gateways pin Vertex global, stable model, limited-use tokens and bounded output', () => {
  const combined = `${androidGateway}\n${iosGateway}\n${iosPolicy}`;
  assert.ok((combined.match(/gemini-3\.5-flash/gu) ?? []).length >= 2);
  assert.match(
    androidGateway,
    /GenerativeBackend\.vertexAI\(MODEL_LOCATION\)/u,
  );
  assert.match(androidGateway, /const val MODEL_LOCATION = "global"/u);
  assert.match(
    iosGateway,
    /backend: \.vertexAI\(location: IOSGeminiSuggestionPolicy\.modelLocation\)/u,
  );
  assert.match(iosPolicy, /static let modelLocation = "global"/u);
  assert.match(androidGateway, /FirebaseAI\.getInstance\([\s\S]*?true,/u);
  assert.match(iosGateway, /useLimitedUseAppCheckTokens: true/u);
  assert.match(androidGateway, /maxOutputTokens = 512/u);
  assert.match(iosGateway, /maxOutputTokens: 512/u);
  assert.match(
    androidGateway,
    /RequestOptions\(timeoutInMillis = REQUEST_TIMEOUT_MILLIS\)/u,
  );
  assert.match(androidGateway, /const val REQUEST_TIMEOUT_MILLIS = 12_000L/u);
  assert.match(iosGateway, /RequestOptions\(timeout: 12\)/u);
  assert.doesNotMatch(combined, /GenerativeBackend\.googleAI|\.googleAI\(\)/u);
  assert.doesNotMatch(combined, /AIza[A-Za-z0-9_-]{20,}/u);
});

test('prompt builders interpolate only closed policy fields and provider content is ephemeral', () => {
  const combined = `${androidGateway}\n${iosGateway}\n${iosPolicy}`;
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
    assert.doesNotMatch(
      iosPolicy.match(
        /static func prompt[\s\S]*?static func validatedCandidates/u,
      )?.[0] ?? '',
      new RegExp(forbidden, 'u'),
    );
  }
  assert.match(androidGateway, /value\.keyNames\(\) != setOf\(/u);
  assert.match(iosPolicy, /Set\(value\.keys\) == Set\(/u);
  assert.match(combined, /minItems\s*[=:]\s*1/u);
  assert.match(combined, /maxItems\s*[=:]\s*3/u);
  assert.match(combined, /MessageTemplateValidator/u);
  assert.doesNotMatch(
    combined,
    /print\s*\(|NSLog\s*\(|Log\.[dievw]\s*\(|Logger\s*\(/u,
  );
  assert.doesNotMatch(
    combined,
    /putString\([^\n]*(?:prompt|response)|UserDefaults[^\n]*(?:prompt|response)/iu,
  );
});

test('iOS generate-suggestions route awaits the native gateway and returns its strict projection', () => {
  assert.match(
    iosWorkflow,
    /case "generate-suggestions":[\s\S]*?await IOSGeminiSuggestionGateway\.shared\.generate\(request: payload\)[\s\S]*?completion\(\.success\(projection\)\)/u,
  );
});

test('iOS Gemini is fail-closed behind one bounded native Remote Config switch', () => {
  assert.match(
    iosOperationalPolicy,
    /parameterKey = "gemini_suggestions_enabled"/u,
  );
  assert.match(iosOperationalPolicy, /inAppDefault = false/u);
  assert.match(
    iosOperationalPolicy,
    /sourceIsRemote && canonicalString == "true" && boolValue/u,
  );
  assert.match(
    iosOperationalGate,
    /settings\.minimumFetchInterval =[\s\S]*?minimumFetchIntervalSeconds/u,
  );
  assert.match(
    iosOperationalGate,
    /settings\.fetchTimeout = IOSGeminiOperationalPolicy\.fetchTimeoutSeconds/u,
  );
  assert.match(
    iosOperationalGate,
    /config\.setDefaults\([\s\S]*?NSNumber\(value: IOSGeminiOperationalPolicy\.inAppDefault\)/u,
  );
  assert.match(iosOperationalGate, /try await config\.fetchAndActivate\(\)/u);
  assert.match(
    iosOperationalGate,
    /localCompletionTimeoutSeconds[\s\S]*?fetchGeneration &\+= 1[\s\S]*?fetchInFlight = false/u,
  );
  assert.match(
    iosOperationalGate,
    /sourceIsRemote: value\.source == \.remote[\s\S]*?canonicalString: value\.stringValue[\s\S]*?boolValue: value\.boolValue/u,
  );

  const generationPath =
    iosGateway.match(
      /func generate\(request payload:[\s\S]*?private func provenance/u,
    )?.[0] ?? '';
  const switchRead = generationPath.indexOf(
    'operationalGate.foregroundSuggestionsEnabled()',
  );
  const appCheck = generationPath.indexOf('appCheckReadyWithinTimeout()');
  const providerCall = generationPath.indexOf('generateProviderText');
  assert.ok(switchRead > 0);
  assert.ok(appCheck > switchRead);
  assert.ok(providerCall > appCheck);
  assert.match(
    generationPath,
    /foregroundSuggestionsEnabled\(\) else[\s\S]*?fallback\("policy-suspended"\)/u,
  );
  assert.match(
    iosIdentity,
    /FirebaseApp\.configure[\s\S]*?configureOperationalGateAfterFirebaseLaunch/u,
  );
  assert.match(
    iosAppDelegate,
    /applicationDidBecomeActive[\s\S]*?refreshOperationalGateInBackground/u,
  );

  assert.doesNotMatch(
    iosOperationalGate,
    /import FirebaseInstallations|Installations\.installations|installationID|addOnConfigUpdateListener|setCustomSignals|print\s*\(|NSLog\s*\(|Logger\s*\(/u,
  );
  assert.doesNotMatch(
    `${liveMessage}\n${messageModel}`,
    /gemini_suggestions_enabled|FirebaseRemoteConfig|RemoteConfig\.remoteConfig/u,
  );
  assert.match(liveMessage, /BUILT_IN_MESSAGE_TEMPLATES\.map/u);
  assert.match(
    liveMessage,
    /suggestions\?\.kind === 'fallback'[\s\S]*?suggestionUnavailable/u,
  );
  const builtInTemplates =
    messageModel.match(
      /export const BUILT_IN_MESSAGE_TEMPLATES:[\s\S]*?\] as const;/u,
    )?.[0] ?? '';
  assert.equal(
    (builtInTemplates.match(/id: '(?:en|hi)-(?:personalized|generic)'/gu) ?? [])
      .length,
    4,
  );
  assert.match(releaseRunbook, /gemini_suggestions_enabled/u);
  assert.match(releaseRunbook, /in-app default is \*\*false\*\*/u);
  assert.match(releaseRunbook, /Firebase's native Installations token/u);
  assert.equal(
    (iosProject.match(/IOSGeminiOperationalPolicy\.swift in Sources/gu) ?? [])
      .length,
    2,
  );
  assert.equal(
    (iosProject.match(/IOSGeminiOperationalGate\.swift in Sources/gu) ?? [])
      .length,
    2,
  );
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
  const combined = `${androidProvenance}\n${iosProvenance}`;
  assert.match(androidProvenance, /MAXIMUM_ENTRIES = 3/u);
  assert.match(androidProvenance, /DEFAULT_TTL_MILLIS = 15 \* 60 \* 1_000L/u);
  assert.match(iosProvenance, /\(1\.\.\.3\)\.contains\(candidates\.count\)/u);
  assert.match(iosProvenance, /ttl: TimeInterval = 15 \* 60/u);
  assert.match(androidProvenance, /GeminiAccountSession\.v1/u);
  assert.match(iosPolicy, /GeminiAccountSession\.v1/u);
  assert.ok(
    (combined.match(/GeminiCandidateExactText\.v1/gu) ?? []).length === 2,
  );
  assert.match(androidGateway, /fun consumeProvenance/u);
  assert.match(iosGateway, /func consumeProvenance/u);
  const entryStorage = [
    androidProvenance.match(
      /private data class Entry\([\s\S]*?\n\s*\)/u,
    )?.[0] ?? '',
    iosProvenance.match(/private struct Entry \{[\s\S]*?\n\s*\}/u)?.[0] ?? '',
  ].join('\n');
  assert.doesNotMatch(entryStorage, /text|candidate: String/u);
});

test('iOS protects exact provenance and clears only Gemini-owned template state', () => {
  for (const field of [
    'source',
    'modelIdentifier',
    'promptPolicyVersion',
    'validatorVersion',
  ]) {
    assert.match(iosWorkflowModels, new RegExp(`let ${field}:`, 'u'));
  }
  assert.match(
    iosWorkflow,
    /peekProvenance\([\s\S]*?CompanionWorkflowMessageProvenance\(/u,
  );
  assert.match(
    iosWorkflow,
    /case \.success\(let value\)[\s\S]*?provenance\?\.source == "GEMINI"[\s\S]*?consumeProvenance/u,
  );
  assert.match(
    iosWorkflow,
    /case "clear-gemini-templates":[\s\S]*?if workflow\.messageDraft\?\.provenance\?\.source == "GEMINI" \{[\s\S]*?workflow\.messageDraft = nil/u,
  );
  assert.match(
    iosWorkflow,
    /IOSGeminiSuggestionGateway\.shared\.clearProvenance\(\)/u,
  );
  assert.match(
    iosIdentity,
    /IOSGeminiSuggestionGateway\.shared\.clearProvenance\(\)/u,
  );
});

test(
  'production Swift Gemini v2 account and rate policy is executable',
  { skip: process.platform !== 'darwin' },
  t => {
    const directory = mkdtempSync(join(tmpdir(), 'birthday-gemini-policy-'));
    const binary = join(directory, 'gemini-policy-tests');
    const moduleCache = join(directory, 'module-cache');
    mkdirSync(moduleCache);
    try {
      const compile = spawnSync(
        'xcrun',
        [
          'swiftc',
          fileURLToPath(new URL(iosPolicyPath, root)),
          fileURLToPath(
            new URL('tests/ios/GeminiPromptAndRatePolicyTests.swift', root),
          ),
          '-module-cache-path',
          moduleCache,
          '-o',
          binary,
        ],
        { encoding: 'utf8' },
      );
      if (
        compile.status !== 0 &&
        /SDK is not supported by the compiler|compiler\/SDK version mismatch/u.test(
          compile.stderr,
        )
      ) {
        t.skip('host Command Line Tools compiler and SDK do not match');
        return;
      }
      assert.equal(compile.status, 0, compile.stderr || compile.stdout);
      const run = spawnSync(binary, [], { encoding: 'utf8' });
      assert.equal(run.status, 0, run.stderr || run.stdout);
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  },
);

test(
  'new Swift sources parse with the installed compiler even when full Xcode is unavailable',
  { skip: process.platform !== 'darwin' },
  () => {
    for (const path of [
      iosPolicyPath,
      iosProvenancePath,
      iosOperationalPolicyPath,
      iosOperationalGatePath,
      iosGatewayPath,
    ]) {
      const result = spawnSync(
        'swiftc',
        ['-frontend', '-parse', fileURLToPath(new URL(path, root))],
        {
          encoding: 'utf8',
          env: {
            ...process.env,
            CLANG_MODULE_CACHE_PATH: '/tmp/birthday-clang-module-cache',
            SWIFT_MODULECACHE_PATH: '/tmp/birthday-swift-module-cache',
          },
        },
      );
      assert.equal(result.status, 0, result.stderr);
    }
  },
);
