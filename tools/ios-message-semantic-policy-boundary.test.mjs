import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');

const workflow = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
);
const placeholderPolicy = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionMessagePlaceholderPolicy.swift',
);
const composer = read('ios/BirthdayAutopilot/CompanionMessageModule.swift');
const protectedStore = read(
  'ios/BirthdayAutopilot/CompanionProtectedStore.swift',
);
const corpusText = read('contracts/birthday-message-semantic-policy-v2.json');
const corpus = JSON.parse(corpusText);

test('iOS semantic policy is versioned and applied to rendered proposals and composer preflight', () => {
  assert.equal(corpus.schemaVersion, 1);
  assert.equal(corpus.policyVersion, 'birthday-message-semantic-v2');
  assert.ok(corpus.cases.length >= 60);
  assert.match(
    workflow,
    /static let policyVersion = "birthday-message-semantic-v2"/u,
  );
  assert.match(
    workflow,
    /static let validatorVersion = "sms-template-validator-v2"/u,
  );
  assert.match(
    workflow,
    /private static func render[\s\S]*?IOSBirthdayMessageContentPolicy\.renderedBody/u,
  );
  assert.match(
    workflow,
    /private func lazyProposalMaterial[\s\S]*?let body = Self\.render[\s\S]*?Self\.smsEstimate\(body\)/u,
  );
  assert.ok(
    (
      composer.match(/IOSBirthdayMessageContentPolicy\.isSafeRenderedBody/gu) ??
      []
    ).length >= 1,
  );
  assert.match(
    protectedStore,
    /validatorVersion: IOSBirthdayMessageContentPolicy\.validatorVersion/u,
  );
  assert.match(
    protectedStore,
    /case \.revalidatedDraft, \.clearedInvalidDraft:[\s\S]*?snapshot\.proposals\.removeAll\(\)/u,
  );
});

test(
  'production iOS classifier matches every shared semantic fixture',
  { skip: process.platform !== 'darwin' },
  () => {
    const policyStart = workflow.indexOf(
      'enum IOSBirthdayMessageContentCategory',
    );
    const policyEnd = workflow.indexOf(
      'private struct IOSCompanionEffectiveContact',
    );
    assert.ok(policyStart >= 0 && policyEnd > policyStart);
    const productionPolicy = workflow.slice(policyStart, policyEnd);
    const encodedCorpus = Buffer.from(corpusText, 'utf8').toString('base64');
    const source = `
      import Foundation

      ${placeholderPolicy}
      ${productionPolicy}

      struct Fixture: Decodable {
        let schemaVersion: Int
        let policyVersion: String
        let cases: [FixtureCase]
      }
      struct FixtureCase: Decodable {
        let id: String
        let language: String
        let text: String
        let expectedCategories: [String]
      }

      let encoded = "${encodedCorpus}"
      guard let data = Data(base64Encoded: encoded),
        let fixture = try? JSONDecoder().decode(Fixture.self, from: data),
        fixture.schemaVersion == 1,
        fixture.policyVersion == IOSBirthdayMessageContentPolicy.policyVersion
      else { fatalError("fixture-contract-invalid") }

      for item in fixture.cases {
        let actual = IOSBirthdayMessageContentPolicy.classify(
          text: item.text,
          declaredLanguage: item.language
        ).map { $0.rawValue }.sorted()
        if actual != item.expectedCategories.sorted() {
          fatalError("semantic-parity-" + item.id + "-" + actual.joined(separator: ","))
        }
      }
    `;
    const result = spawnSync('swift', ['-'], {
      input: source,
      encoding: 'utf8',
      env: {
        ...process.env,
        CLANG_MODULE_CACHE_PATH: '/tmp/birthday-clang-module-cache',
        SWIFT_MODULECACHE_PATH: '/tmp/birthday-swift-module-cache',
      },
      maxBuffer: 10 * 1024 * 1024,
    });
    assert.equal(result.status, 0, result.stderr || result.stdout);
  },
);
