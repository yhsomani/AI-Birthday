import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = path => readFileSync(path, 'utf8');

const client = read(
  'ios/BirthdayAutopilot/Automation/IOSContactDerivedResetClient.swift',
);
const workflow = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
);
const bridge = read('ios/BirthdayAutopilot/BirthdayNativeModule.swift');
const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');

test('iOS contact-derived reset uses the replay-protected native callable contract', () => {
  assert.match(client, /callableName = "resetContactDerivedState"/u);
  assert.match(
    client,
    /HTTPSCallableOptions\(requireLimitedUseAppCheckTokens: true\)/u,
  );
  assert.match(client, /"contractVersion": 1,[\s\S]*"requestId": requestId/u);
  assert.match(client, /isCanonicalUUID\(requestId\)/u);
  assert.match(client, /hasExactFirebaseSession\(binding\)/u);
  assert.match(
    client,
    /raw\["operation"\] as\? String == "CONTACT_DERIVED_RESET"/u,
  );
  assert.match(client, /"RESET_DRAINING", "RESET_PURGING"/u);
  assert.match(
    client,
    /strictBoolean\(raw\["contactDerivedStateErased"\]\) == true/u,
  );
  assert.match(
    client,
    /strictBoolean\(raw\["firebaseAuthPreserved"\]\) == true/u,
  );
  for (const refusal of [
    'CONTINUITY_UNAVAILABLE',
    'COORDINATION_OPERATION_IN_PROGRESS',
    'DELETION_SUPPRESSED',
    'GENERATION_EXHAUSTED',
    'REQUEST_MISMATCH',
    'RESET_SUPPRESSED',
  ]) {
    assert.match(client, new RegExp(`"${refusal}"`, 'u'));
  }
});

test('iOS never clears contacts or revokes Google before reset completion', () => {
  const resetStart = workflow.indexOf(
    'private func performContactDerivedReset(',
  );
  const cleanupStart = workflow.indexOf(
    'private func finishContactDerivedLocalCleanup(',
  );
  const resetBody = workflow.slice(resetStart, cleanupStart);
  const cleanupBody = workflow.slice(
    cleanupStart,
    workflow.indexOf('private func clearContactDerivedWorkflow(', cleanupStart),
  );

  assert.ok(resetStart >= 0 && cleanupStart > resetStart);
  assert.ok(
    resetBody.indexOf('.ensureRecentExactGoogleAuthentication') <
      resetBody.indexOf('contactResetClient.startOrReplay'),
  );
  assert.ok(
    resetBody.indexOf('contactResetClient.startOrReplay') <
      resetBody.indexOf('finishContactDerivedLocalCleanup'),
  );
  assert.doesNotMatch(resetBody, /clearContactsRetainingBinding/u);
  assert.doesNotMatch(resetBody, /revokeGoogleAccessAfterSafetyShutdown/u);
  assert.match(cleanupBody, /clearContactsRetainingBinding/u);
  assert.match(cleanupBody, /revokeGoogleAccessAfterSafetyShutdown/u);

  const disconnectCase = workflow.slice(
    workflow.indexOf(
      'case "disconnect-contacts":',
      workflow.indexOf('private func performPrivacyAction('),
    ),
    workflow.indexOf(
      'case "sign-out-retain":',
      workflow.indexOf('private func performPrivacyAction('),
    ),
  );
  const revokeCase = workflow.slice(
    workflow.indexOf(
      'case "revoke-google-access":',
      workflow.indexOf('private func performPrivacyAction('),
    ),
    workflow.indexOf(
      'case "delete-account":',
      workflow.indexOf('private func performPrivacyAction('),
    ),
  );
  assert.match(disconnectCase, /performContactDerivedReset/u);
  assert.match(revokeCase, /performContactDerivedReset/u);
  assert.doesNotMatch(revokeCase, /wipeCompanionData/u);
});

test('iOS projects and resumes durable privacy work without exposing Android transfer UI', () => {
  assert.match(bridge, /"resume-lifecycle-operation"/u);
  assert.match(bridge, /case "current-operation":/u);
  assert.match(bridge, /currentPrivacyOperationProjection\(status: status\)/u);
  assert.match(workflow, /private func resumePrivacyOperation\(/u);
  assert.match(workflow, /performPrivacyAction\([\s\S]*operation: operation/u);
  assert.match(
    workflow,
    /"preissuedPermitMayFinish": \[[\s\S]*"delete-account", "disconnect-contacts", "revoke-google-access"/u,
  );
  assert.doesNotMatch(bridge, /prepare-sender-transfer/u);
});

test('the reset client is a compiled application source', () => {
  assert.match(project, /IOSContactDerivedResetClient\.swift in Sources/u);
  assert.match(
    project,
    /path = BirthdayAutopilot\/Automation\/IOSContactDerivedResetClient\.swift/u,
  );
});
