import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';

const source = readFileSync(join(process.cwd(), 'src/native/channelHandoffBridge.ts'), 'utf8');
const commandSource = readFileSync(join(process.cwd(), 'src/application/commandRuntime.ts'), 'utf8');
const productionSource = readFileSync(join(process.cwd(), 'src/application/createProductionRuntime.ts'), 'utf8');

describe('channel handoff native bridge source contract', () => {
  it('opens destinations through Linking and maps dismissed share sheets without recording sends', () => {
    assert.match(source, /import \{ Linking, Share \} from 'react-native'/);
    assert.match(source, /runHandoffTarget\(input,/);
    assert.match(source, /canOpenUrl: Linking\.canOpenURL/);
    assert.match(source, /openUrl: Linking\.openURL/);
    assert.match(source, /Share\.share\(payload\)/);
    assert.match(source, /Share\.dismissedAction/);
    assert.doesNotMatch(source, /manualHandoff|dispatch|relateReducer/);
  });

  it('keeps sent recording behind the command runtime explicit completion confirmation', () => {
    assert.match(productionSource, /openManualHandoffTarget/);
    assert.match(commandSource, /result\.needsSentConfirmation/);
    assert.match(commandSource, /handoffConfirmations\.set/);
    assert.match(commandSource, /type: 'manualHandoff'/);
    assert.doesNotMatch(commandSource, /message: target\.reason \? `\$\{message\.body\}\\n\\n\$\{target\.reason\}`/);
  });
});
