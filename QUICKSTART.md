# WishWell Quickstart

Follow these minimal steps to get the development environment running.

## Prerequisites

- **Node.js**: >= 24.18.0
- **npm**: >= 11.6.0
- **Java**: JDK 21
- **React Native CLI**: 0.86.0 environment setup

## 1. Install Dependencies

```zsh
# Using nvm
nvm use
npm install -g npm@11.6.0
npm ci
```

## 2. Start the Metro Bundler

```zsh
npm start
```

## 3. Run Android

In a separate terminal:

```zsh
export JAVA_HOME=/path/to/openjdk-21
export ANDROID_HOME="$HOME/Library/Android/sdk"
npm run android
```

Android is the only supported platform; the former iOS Companion Edition has been removed.

For more detailed setup, troubleshooting, and workflow information, see the [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md).
