import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');

const androidGateway = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/AndroidGeminiSuggestionGateway.kt',
);
const iosPolicyPath =
  'ios/BirthdayAutopilot/Gemini/GeminiSuggestionPolicy.swift';
const iosGatewayPath =
  'ios/BirthdayAutopilot/Gemini/IOSGeminiSuggestionGateway.swift';
const iosProvenancePath =
  'ios/BirthdayAutopilot/Gemini/GeminiCandidateProvenanceRegistry.swift';
const iosPolicy = read(iosPolicyPath);
const iosGateway = read(iosGatewayPath);
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
  for (const pod of [
    'FirebaseCore',
    'FirebaseAuth',
    'FirebaseAppCheck',
    'FirebaseFunctions',
    'FirebaseAILogic',
  ]) {
    assert.match(podfile, new RegExp(`pod '${pod}', '12\\.15\\.0'`, 'u'));
  }
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

test('native provenance is digest-only, bounded, expiring and account-bound', () => {
  const combined = `${androidProvenance}\n${iosProvenance}`;
  assert.match(androidProvenance, /MAXIMUM_ENTRIES = 3/u);
  assert.match(androidProvenance, /DEFAULT_TTL_MILLIS = 15 \* 60 \* 1_000L/u);
  assert.match(iosProvenance, /\(1\.\.\.3\)\.contains\(candidates\.count\)/u);
  assert.match(iosProvenance, /ttl: TimeInterval = 15 \* 60/u);
  assert.match(androidGateway, /GeminiAccountSession\.v1/u);
  assert.match(iosProvenance, /GeminiAccountSession\.v1/u);
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
  'new Swift sources parse with the installed compiler even when full Xcode is unavailable',
  { skip: process.platform !== 'darwin' },
  () => {
    for (const path of [iosPolicyPath, iosProvenancePath, iosGatewayPath]) {
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
