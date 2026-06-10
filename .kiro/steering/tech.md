# RelateAI Tech Stack & Build Guide

## Build System

- **Gradle Kotlin DSL** (`.gradle.kts`)
- **Android Gradle Plugin**: 9.2.1
- **Kotlin**: 2.2.10
- **Java/Kotlin target**: JDK 21 toolchain with JVM 17 bytecode

## Project Structure

```
├── app/                 # Main application module
├── core/
│   ├── domain/         # Business logic, use cases, repositories (interfaces)
│   ├── data/           # Data layer (repositories, Room, API clients)
│   └── ui/             # Shared UI components, theme, design system
└── feature/            # Not active; keep feature UI under app/src/main/java/com/example/ui
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

# Build with KSP (Kotlin Symbol Processing)
./gradlew kspDebugKotlin

# Full feature-compliance validation
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew testDebugUnitTest lintDebug assembleDebug jacocoDebugUnitTestReport --no-configuration-cache

# View dependency tree
./gradlew app:dependencies
```

## Key Configuration

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Compile SDK**: 37
- **Java Compatibility**: 17 bytecode on a JDK 21 toolchain
- **Namespace**: `com.example`

## Development Setup

1. Set environment variables for signing (release builds):
   - `KEYSTORE_PATH`
   - `STORE_PASSWORD`
   - `KEY_PASSWORD`

2. Ensure `google-services.json` is present in `app/`

3. For local validation, use JDK 21. The root Gradle build configures unit tests to use the JDK 21 toolchain and applies the project-local Zscaler truststore when `.gradle/trust/cacerts-zscaler` exists.

## Code Generation

- **Room Schema Export**: Enabled in `build.gradle.kts` with `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`
- **Hilt**: Uses KSP for code generation (hilt-android-compiler)
- **Moshi**: Uses KSP for code generation (moshi-kotlin-codegen)
