import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const read = relative => readFileSync(path.join(projectRoot, relative));

function pngInfo(relative) {
  const bytes = read(relative);
  assert.equal(bytes.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
  assert.equal(bytes.subarray(12, 16).toString('ascii'), 'IHDR');
  return {
    bytes,
    colorType: bytes[25],
    hasTransparencyChunk: bytes.includes(Buffer.from('tRNS', 'ascii')),
    height: bytes.readUInt32BE(20),
    width: bytes.readUInt32BE(16),
  };
}

function assertOpaqueSquare(relative, size) {
  const info = pngInfo(relative);
  assert.equal(info.width, size, relative);
  assert.equal(info.height, size, relative);
  assert.ok(
    [0, 2].includes(info.colorType),
    `${relative} has an alpha color type`,
  );
  assert.equal(info.hasTransparencyChunk, false, relative);
  return info.bytes;
}

test('Android launcher artwork is opaque and complete at every density', () => {
  for (const [density, size] of Object.entries({
    mdpi: 48,
    hdpi: 72,
    xhdpi: 96,
    xxhdpi: 144,
    xxxhdpi: 192,
  })) {
    assertOpaqueSquare(
      `android/app/src/main/res/mipmap-${density}/ic_launcher.png`,
      size,
    );
  }
  assertOpaqueSquare(
    'android/app/src/main/res/drawable-nodpi/app_icon.png',
    1024,
  );
  const adaptive = read(
    'android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
  ).toString('utf8');
  const round = read(
    'android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml',
  ).toString('utf8');
  const manifest = read('android/app/src/main/AndroidManifest.xml').toString(
    'utf8',
  );
  assert.match(adaptive, /@drawable\/app_icon/u);
  assert.match(adaptive, /@drawable\/ic_launcher_monochrome/u);
  assert.equal(round, adaptive);
  assert.match(manifest, /android:icon="@mipmap\/ic_launcher"/u);
  assert.match(manifest, /android:roundIcon="@mipmap\/ic_launcher_round"/u);
});

test('the 1024 artwork is identical across the reviewed source and android assets', () => {
  const identities = [
    'assets/branding/app-icon-1024.png',
    'android/app/src/main/res/drawable-nodpi/app_icon.png',
  ].map(relative => createHash('sha256').update(read(relative)).digest('hex'));
  assert.equal(new Set(identities).size, 1);
});
