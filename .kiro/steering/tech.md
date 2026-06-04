# RelateAI Tech Stack & Build Guide

## Build System

- **Gradle Kotlin DSL** (`.gradle.kts`)
- **Android Gradle Plugin**: Latest stable
- **Kotlin**: 1.9+ with JVM 11 target

## Project Structure

```
├── app/                 # Main application module
├── core/
│   ├── domain/         # Business logic, use cases, repositories (interfaces)
│   ├── data/           # Data layer (repositories, Room, API clients)
│   └── ui/             # Shared UI components, theme, design system
└── feature/            # Feature modules (splash, login, dashboard, etc.)
```

## Key Dependencies

| Library | Purpose |
|---------|---------|
| `androidx.compose.*` | Jetpack Compose UI |
| `androidx.room.*` | Room Database with SQLCipher |
| `com.google.dagger:hilt.*` | Hilt dependency injection |
| `com.google.firebase:firebase-*` | Firebase Auth & Vertex AI |
| `com.squareup.retrofit2` | HTTP client |
| `io.coil-kt:coil-compose` | Image loading |
| `androidx.work:*` | Background scheduling |
| `androidx.security.crypto` | Encrypted SharedPreferences |
| `org.jetbrains.kotlinx:kotlinx-coroutines-*` | Coroutines |
| `com.google.code.gson:gson` | JSON serialization |
| `com.squareup.moshi:moshi-kotlin` | Alternative JSON parser |

## Build Commands

```bash
# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew testDebugUnitTest

# Run instrumented tests
./gradlew connectedDebugAndroidTest

# Run Roborazzi tests
./gradlew recordRoborazziDebug

# Build with KSP (Kotlin Symbol Processing)
./gradlew kspDebugKotlin

# View dependency tree
./gradlew app:dependencies
```

## Key Configuration

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Compile SDK**: 36 (API 1 minor)
- **Java Compatibility**: 11
- **Namespace**: `com.example`

## Development Setup

1. Set environment variables for signing (release builds):
   - `KEYSTORE_PATH`
   - `STORE_PASSWORD`
   - `KEY_PASSWORD`

2. Ensure `google-services.json` is present in `app/`

3. For testing with Robolectric, run tests with the Android environment

## Code Generation

- **Room Schema Export**: Enabled in `build.gradle.kts` with `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`
- **Hilt**: Uses KSP for code generation (hilt-android-compiler)
- **Moshi**: Uses KSP for code generation (moshi-kotlin-codegen)
