# Developer Guide

Welcome to the WishWell repository. This guide covers environment setup, workflows, and common troubleshooting.

## 1. Prerequisites Checklist

- [ ] Node.js `>=24.18.0` (managed via nvm recommended)
- [ ] npm `>=11.6.0`
- [ ] Java JDK 21 (for Android build)
- [ ] Android Studio and Android SDK (API 36)
- [ ] Xcode 26.5 (for iOS build)
- [ ] Ruby `3.4.10` and Bundler `4.0.15` (for iOS CocoaPods)
- [ ] (Optional) Docker for reproducible builds

## 2. Step-by-Step Environment Setup

### Node & NPM

```zsh
nvm use
npm install -g npm@11.6.0
npm ci
```

### Firebase Packages

The Firebase functions and hosting are isolated packages. Ensure you install their dependencies separately:

```zsh
# Functions
cd backend/functions && npm ci && cd ../..

# Hosting
cd backend/hosting && npm ci && cd ../..
```

### Android Setup

Ensure your environment variables are configured:

```zsh
export JAVA_HOME=/path/to/openjdk-21
export ANDROID_HOME="$HOME/Library/Android/sdk"
npm run doctor:android
```

### iOS Setup (macOS only)

Ensure you are using the correct Xcode and Ruby versions:

```zsh
sudo xcode-select --switch /Applications/Xcode_26.5.app/Contents/Developer
bundle install
npm run ios:pods
npm run doctor:ios
```

## 3. Development Workflow

- **Branching**: Use feature branches (`feature/name` or `bugfix/name`).
- **Linting & Formatting**: `npm run lint` and `npm run format:check` are strictly enforced.
- **Testing**: Run unit tests via `npm run test`.
- **Commits**: Ensure your code passes `npm run check` before committing. Pre-commit hooks will run security scans.

## 4. Common Error Resolutions

### Mismatched Node/NPM Version

**Error**: `npm error engine Unsupported engine`
**Fix**: Ensure your node version is at least `24.18.0`. Use `nvm install 24.18.0 && nvm use`.

### iOS Pod Install Fails

**Error**: CocoaPods errors during `npm run ios:pods`
**Fix**: Ensure you are using exactly Ruby `3.4.10`. Verify with `ruby -v`. Use `rbenv` or `rvm` to manage Ruby versions.

### Android SDK Missing

**Error**: SDK path not found
**Fix**: Verify `ANDROID_HOME` is set correctly and points to a valid Android SDK installation.
