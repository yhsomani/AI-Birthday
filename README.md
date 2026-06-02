<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/c24a367f-5df5-4d64-885b-65caf74bfe9b

> **For product/architecture documentation, see [`SSOT.md`](./SSOT.md)** (Single Source of Truth, v3.1).
> **For audit reports (dead UI, implementation gaps, production readiness), see [`reports/`](./reports).**

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device

## Production Release Builds

For Play Store releases, see [`SSOT.md` §23 Infrastructure & Deployment](./SSOT.md#23-infrastructure--deployment) and `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS` env vars. Also review [`reports/08-production-readiness.md`](./reports/08-production-readiness.md).
