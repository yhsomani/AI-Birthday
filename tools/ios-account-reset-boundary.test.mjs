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

test('iOS disconnect is local-only while all-scope revoke waits for reset completion', () => {
  const localStart = workflow.indexOf(
    'private func performLocalContactsDisconnect(',
  );
  const resetStart = workflow.indexOf(
    'private func performContactDerivedReset(',
  );
  const providerStart = workflow.indexOf(
    'private func disconnectGoogleProviderAfterRemoteReset(',
  );
  const finishStart = workflow.indexOf(
    'private func finishProviderRevocationCleanup(',
  );
  const localBody = workflow.slice(localStart, resetStart);
  const resetBody = workflow.slice(resetStart, providerStart);
  const providerBody = workflow.slice(providerStart, finishStart);

  assert.ok(localStart >= 0 && resetStart > localStart);
  assert.ok(providerStart > resetStart && finishStart > providerStart);
  assert.ok(
    localBody.indexOf('cancelPlansAndNotifications') <
      localBody.indexOf('clearContactsRetainingBinding'),
  );
  assert.ok(
    localBody.indexOf('clearContactsRetainingBinding') <
      localBody.indexOf('clearContactDerivedState'),
  );
  assert.ok(
    localBody.indexOf('clearContactDerivedState') <
      localBody.indexOf('performContactDerivedReset'),
  );
  assert.ok(
    resetBody.indexOf('.ensureRecentExactGoogleAuthentication') <
      resetBody.indexOf('contactResetClient.startOrReplay'),
  );
  assert.ok(
    resetBody.indexOf('contactResetClient.startOrReplay') <
      resetBody.indexOf('disconnectGoogleProviderAfterRemoteReset'),
  );
  assert.doesNotMatch(resetBody, /clearContactsRetainingBinding/u);
  assert.match(providerBody, /disconnectGoogleProviderAfterLocalCleanup/u);
  assert.match(providerBody, /markProviderRevoked/u);

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
  assert.match(disconnectCase, /performLocalContactsDisconnect/u);
  assert.doesNotMatch(disconnectCase, /performContactDerivedReset/u);
  assert.match(
    revokeCase,
    /"local-cleared", "verifying", "remote-draining", "provider-revoked"/u,
  );
  assert.match(revokeCase, /performContactDerivedReset/u);
  assert.match(revokeCase, /performLocalContactsDisconnect/u);
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
    /"preissuedPermitMayFinish": \[[\s\S]*"delete-account", "revoke-google-access"/u,
  );
  assert.doesNotMatch(
    workflow,
    /"preissuedPermitMayFinish": \[[\s\S]{0,160}"disconnect-contacts"/u,
  );
  assert.match(
    workflow,
    /"remoteConnectionRequired": \[[\s\S]*"delete-account", "revoke-google-access"/u,
  );
  const localDisconnect = workflow.slice(
    workflow.indexOf('private func performLocalContactsDisconnect('),
    workflow.indexOf('private func performContactDerivedReset('),
  );
  assert.match(localDisconnect, /cancelPlansAndNotifications/u);
  assert.match(localDisconnect, /clearContactsRetainingBinding/u);
  assert.match(localDisconnect, /clearContactDerivedState/u);
  assert.doesNotMatch(
    localDisconnect,
    /ensureRecentExactGoogleAuthentication/u,
  );
  assert.doesNotMatch(localDisconnect, /contactResetClient/u);
  assert.doesNotMatch(bridge, /prepare-sender-transfer/u);
});

test('durable privacy operation projections reuse their persisted transition timestamp', () => {
  const projection = workflow.slice(
    workflow.indexOf('private static func privacyOperationPayload('),
    workflow.indexOf('static func accountDeletionReceiptPayload('),
  );
  assert.match(
    projection,
    /"completedAt": dateString\(operation\.updatedAt\)/u,
  );
  assert.equal(
    (
      projection.match(/"updatedAt": dateString\(operation\.updatedAt\)/gu) ??
      []
    ).length,
    4,
  );
  assert.doesNotMatch(projection, /dateString\(Date\(\)\)/u);
});

test('the reset client is a compiled application source', () => {
  assert.match(project, /IOSContactDerivedResetClient\.swift in Sources/u);
  assert.match(
    project,
    /path = BirthdayAutopilot\/Automation\/IOSContactDerivedResetClient\.swift/u,
  );
});
