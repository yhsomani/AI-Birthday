const { spawn, spawnSync } = require('node:child_process');
const { access, cp, mkdir, mkdtemp, readFile, rm, symlink } = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');

const projectRoot = path.resolve(__dirname, '..');
const keepFixture = process.env.RELATEAI_KEEP_NATIVE_FIXTURE === '1';

const assertJava17 = () => {
  const javaHome = process.env.JAVA_HOME;
  if (!javaHome) {
    throw new Error('test:native-prebuild requires JAVA_HOME to point to JDK 17.');
  }

  const javaExecutable = path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
  const result = spawnSync(javaExecutable, ['-version'], { encoding: 'utf8' });
  const versionOutput = `${result.stdout ?? ''}\n${result.stderr ?? ''}`;
  if (result.status !== 0 || !/version "17(?:\.|\+)/.test(versionOutput)) {
    throw new Error(`test:native-prebuild requires JDK 17; JAVA_HOME resolved to: ${versionOutput.trim() || javaHome}`);
  }
};

const runAsync = (executable, args, cwd) =>
  new Promise((resolve, reject) => {
    const child = spawn(executable, args, {
      cwd,
      env: {
        ...process.env,
        CI: '1',
        EXPO_NO_TELEMETRY: '1'
      },
      stdio: 'inherit'
    });

    child.once('error', reject);
    child.once('exit', (code, signal) => {
      if (code === 0) {
        resolve();
        return;
      }
      reject(
        new Error(
          `${path.basename(executable)} ${args.join(' ')} failed${
            signal ? ` with signal ${signal}` : ` with exit code ${code ?? 'unknown'}`
          }.`
        )
      );
    });
  });

const assertAndroidReleasePolicyAsync = async androidRoot => {
  const manifestPath = path.join(androidRoot, 'app', 'src', 'main', 'AndroidManifest.xml');
  const manifest = await readFile(manifestPath, 'utf8');
  const activePermissions = [...manifest.matchAll(/<uses-permission\b[^>]*>/g)]
    .map(match => match[0])
    .filter(tag => !/tools:node=["']remove["']/.test(tag))
    .map(tag => tag.match(/android:name=["']([^"']+)["']/)?.[1])
    .filter(Boolean);
  const forbidden = new Set([
    'android.permission.WRITE_CONTACTS',
    'android.permission.READ_EXTERNAL_STORAGE',
    'android.permission.WRITE_EXTERNAL_STORAGE',
    'android.permission.SYSTEM_ALERT_WINDOW',
    'android.permission.SEND_SMS',
    'android.permission.READ_SMS',
    'android.permission.RECEIVE_SMS',
    'android.permission.READ_CALL_LOG',
    'android.permission.READ_PHONE_NUMBERS',
    'android.permission.USE_EXACT_ALARM',
    'android.permission.SCHEDULE_EXACT_ALARM',
    'android.permission.BIND_ACCESSIBILITY_SERVICE'
  ]);
  const drift = activePermissions.filter(permission => forbidden.has(permission));
  if (drift.length > 0) {
    throw new Error(`Generated Android manifest contains forbidden active permissions: ${drift.join(', ')}`);
  }
  if (!/<application\b[^>]*android:allowBackup=["']false["']/.test(manifest)) {
    throw new Error('Generated Android manifest must disable platform auto backup.');
  }
};

const copyProjectInputsAsync = async fixtureRoot => {
  const projectFiles = ['app.json', 'index.js', 'package.json', 'package-lock.json'];
  for (const relativePath of projectFiles) {
    await cp(path.join(projectRoot, relativePath), path.join(fixtureRoot, relativePath));
  }

  await mkdir(path.join(fixtureRoot, 'plugins'), { recursive: true });
  for (const pluginFile of ['android-generation.js', 'with-relateai-home-widget.js', 'with-relateai-shortcuts.js']) {
    await cp(path.join(projectRoot, 'plugins', pluginFile), path.join(fixtureRoot, 'plugins', pluginFile));
  }

  await mkdir(path.join(fixtureRoot, 'src', 'config'), { recursive: true });
  await cp(
    path.join(projectRoot, 'src', 'config', 'launcherShortcuts.json'),
    path.join(fixtureRoot, 'src', 'config', 'launcherShortcuts.json')
  );

  const sourceNodeModules = path.join(projectRoot, 'node_modules');
  await access(sourceNodeModules);
  await symlink(
    sourceNodeModules,
    path.join(fixtureRoot, 'node_modules'),
    process.platform === 'win32' ? 'junction' : 'dir'
  );
};

const main = async () => {
  assertJava17();
  const fixtureRoot = await mkdtemp(path.join(os.tmpdir(), 'relateai-native-prebuild-'));

  try {
    await copyProjectInputsAsync(fixtureRoot);
    const executableSuffix = process.platform === 'win32' ? '.cmd' : '';
    const expoExecutable = path.join(fixtureRoot, 'node_modules', '.bin', `expo${executableSuffix}`);
    await runAsync(expoExecutable, ['prebuild', '--clean', '--no-install', '--platform', 'android'], fixtureRoot);

    const androidRoot = path.join(fixtureRoot, 'android');
    await assertAndroidReleasePolicyAsync(androidRoot);
    const gradleExecutable = path.join(androidRoot, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
    await runAsync(gradleExecutable, [':app:assembleDebug', '--no-daemon', '--stacktrace'], androidRoot);
  } finally {
    if (keepFixture) {
      process.stdout.write(`Native prebuild fixture retained at ${fixtureRoot}\n`);
    } else {
      await rm(fixtureRoot, { recursive: true, force: true });
    }
  }
};

main().catch(error => {
  process.stderr.write(`${error instanceof Error ? error.stack : String(error)}\n`);
  process.exitCode = 1;
});
