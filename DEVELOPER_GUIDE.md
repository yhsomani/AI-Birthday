# Developer Guide

Welcome to the WishWell repository. This guide covers environment setup, workflows, and common troubleshooting.

## 1. Prerequisites Checklist

- [ ] Node.js `>=24.18.0` (managed via nvm recommended)
- [ ] npm `>=11.6.0`
- [ ] Java JDK 21 (for Android build)
- [ ] Android Studio and Android SDK (API 36)
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

The former iOS Setup steps were removed together with the iOS platform; Android is the only supported target.

## 3. Development Workflow

- **Branching**: Use feature branches (`feature/name` or `bugfix/name`).
- **Linting & Formatting**: `npm run lint` and `npm run format:check` are strictly enforced.
- **Testing**: Run unit tests via `npm run test`.
- **Commits**: Ensure your code passes `npm run check` before committing. Pre-commit hooks will run security scans.

## 4. Common Error Resolutions

### Mismatched Node/NPM Version

**Error**: `npm error engine Unsupported engine`
**Fix**: Ensure your node version is at least `24.18.0`. Use `nvm install 24.18.0 && nvm use`.

### Android SDK Missing

**Error**: SDK path not found
**Fix**: Verify `ANDROID_HOME` is set correctly and points to a valid Android SDK installation.
