import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = path => readFileSync(path, 'utf8');

const normalizer = read(
  'ios/BirthdayAutopilot/Contacts/IOSPhoneNumberNormalizer.swift',
);
const store = read('ios/BirthdayAutopilot/Database/CompanionPeopleStore.swift');
const models = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowModels.swift',
);
const workflow = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
);
const podfile = read('ios/Podfile');
const podLock = read('ios/Podfile.lock');
const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
const regexEscape = value => value.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');

test('iOS BA-04 pins one exact core and short-number metadata family', () => {
  for (const pod of [
    'libPhoneNumber-iOS',
    'libPhoneNumberShortNumber',
    'libPhoneNumber-iOS-SwiftCore',
    'libPhoneNumber-iOS-SwiftShortNumber',
  ]) {
    const escapedPod = regexEscape(pod);
    assert.match(podfile, new RegExp(`pod '${escapedPod}', '1\\.7\\.3'`, 'u'));
    assert.match(podLock, new RegExp(`- ${escapedPod} \\(1\\.7\\.3\\)`, 'u'));
    assert.doesNotMatch(
      podLock,
      new RegExp(`- ${escapedPod} \\(1\\.7\\.4\\)`, 'u'),
    );
  }
  assert.match(
    podfile,
    /pod 'libPhoneNumber-iOS', '1\.7\.3', :modular_headers => true/u,
  );
  assert.match(
    podfile,
    /pod 'libPhoneNumberShortNumber', '1\.7\.3', :modular_headers => true/u,
  );
  assert.match(normalizer, /metadataRelease = "libPhoneNumber-iOS-1\.7\.3"/u);
});

test('iOS normalization requires core validity and dedicated short metadata', () => {
  assert.match(normalizer, /import libPhoneNumberSwiftCore/u);
  assert.match(normalizer, /import libPhoneNumberSwiftShortNumber/u);
  for (const contract of [
    /phoneUtility\.parse/u,
    /phoneUtility\.isPossibleNumber/u,
    /phoneUtility\.isValidNumber/u,
    /phoneUtility\.type/u,
    /phoneUtility\.format\(number, as: \.e164\)/u,
    /shortUtility\.isEmergencyNumber/u,
    /shortUtility\.connectsToEmergencyNumber/u,
    /shortUtility\.isPossibleShortNumber/u,
    /shortUtility\.isValidShortNumber/u,
    /shortUtility\.expectedCost/u,
  ]) {
    assert.match(normalizer, contract);
  }
  assert.match(
    normalizer,
    /if evidence\.emergency \{ return \.emergencyNumber \}[\s\S]*?if evidence\.shortCode \{ return \.shortCode \}[\s\S]*?evidence\.premiumRate[\s\S]*?return \.premiumRate/u,
  );
  assert.match(normalizer, /\.fixedLineOrMobile, \.mobile/u);
  assert.match(
    normalizer,
    /case \.ambiguous, \.regionInvalid, \.regionRequired/u,
  );
});

test('People projections consume canonical metadata and remove legacy digit heuristics', () => {
  assert.match(store, /IOSPhoneNumberNormalizer\.shared\.normalize/u);
  assert.match(store, /acceptedByDestination\[value\.e164/u);
  assert.match(store, /acceptedPhoneChoices\.count > 1/u);
  assert.match(store, /selectable: false/u);
  assert.match(store, /issue: value\.rejection\.issue\.publicReasonCode/u);
  assert.doesNotMatch(store, /private static func analyzePhone/u);
  assert.doesNotMatch(store, /private static func normalizedE164/u);
});

test('phone metadata remains native-only and both sources are compiled into XCTest', () => {
  assert.doesNotMatch(
    normalizer,
    /React|@objc|Firebase|GoogleSignIn|AccessToken/u,
  );
  assert.equal(
    (project.match(/IOSPhoneNumberNormalizer\.swift in Sources/gu) ?? [])
      .length,
    2,
  );
  assert.equal(
    (project.match(/IOSPhoneNumberNormalizerTests\.swift in Sources/gu) ?? [])
      .length,
    2,
  );
});

test('iOS approvals and final composer review bind the exact normalized E.164 destination', () => {
  assert.match(
    models,
    /struct IOSCompanionApprovalDestinationBinding: Equatable/u,
  );
  assert.match(models, /static let version = "ios-approved-e164-v1"/u);
  assert.match(
    models,
    /phones\.first\(where: \{ \$0\.localId == selectedPhoneId \}\)\?\.e164/u,
  );
  assert.match(
    models,
    /IOSCompanionDestinationBlocklistPolicy\.isCanonical\(e164\)/u,
  );
  assert.match(
    models,
    /metadataRelease: IOSPhoneNumberNormalizer\.metadataRelease/u,
  );
  assert.match(
    workflow,
    /let destination = IOSCompanionApprovalDestinationBinding\.resolve\([\s\S]*?\] \+ destination\.hashComponents \+ \[/u,
  );
  assert.match(
    workflow,
    /destination\?\.e164 \?\? "none"[\s\S]*?IOSCompanionApprovalDestinationBinding\.version/u,
  );
  assert.match(
    workflow,
    /let selectedPhoneId = configuration\.selectedPhoneId[\s\S]*?let recipient = contact\.phones\.first[\s\S]*?IOSCompanionDestinationBlocklistPolicy\.isCanonical\(recipient\)[\s\S]*?selectedPhoneId: selectedPhoneId[\s\S]*?recipient: recipient/u,
  );
});
