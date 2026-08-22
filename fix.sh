git restore backend/functions/package-lock.json backend/functions/package.json backend/hosting/package-lock.json package-lock.json tools/toolchain-contract.test.mjs .github/workflows/ci.yml .github/workflows/android-release-evidence.yml
npm --prefix backend/functions ci
npm --prefix backend/functions audit fix --force
npm --prefix backend/hosting ci
npm --prefix backend/hosting audit fix --force
npm ci
npm audit fix --force

sed -i 's/\/usr\/local\/lib\/android\/sdk\/cmdline-tools\/latest\/bin\/sdkmanager/\/opt\/android-sdk\/cmdline-tools\/latest\/bin\/sdkmanager/g' .github/workflows/ci.yml
sed -i 's/\/usr\/local\/lib\/android\/sdk\/cmdline-tools\/latest\/bin\/sdkmanager/\/opt\/android-sdk\/cmdline-tools\/latest\/bin\/sdkmanager/g' .github/workflows/android-release-evidence.yml

sed -i 's/mkdirSync, //g' tools/toolchain-contract.test.mjs
sed -i 's/chmodSync, //g' tools/toolchain-contract.test.mjs
sed -i 's/spawnSync //g' tools/toolchain-contract.test.mjs
sed -i 's/import {  } from '"'node:child_process'"';//g' tools/toolchain-contract.test.mjs
