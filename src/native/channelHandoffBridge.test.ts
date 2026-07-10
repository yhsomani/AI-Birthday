import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';

const source = readFileSync(join(process.cwd(), 'src/native/channelHandoffBridge.ts'), 'utf8');
const appSource = readFileSync(join(process.cwd(), 'src/App.tsx'), 'utf8');

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

  it('keeps App sent recording behind the explicit completion confirmation', () => {
    assert.match(appSource, /openManualHandoffTarget\(/);
    assert.match(appSource, /result\.needsSentConfirmation/);
    assert.match(appSource, /target\.markSentLabel, onPress: \(\) => dispatch\(\{ type: 'manualHandoff'/);
    assert.doesNotMatch(appSource, /message: target\.reason \? `\$\{message\.body\}\\n\\n\$\{target\.reason\}`/);
  });
});
