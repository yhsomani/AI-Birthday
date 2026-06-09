# RelateAI Project Structure & Conventions

## Directory Layout

```
app/
├── src/main/
│   ├── java/com/example/
│   │   ├── MainActivity.kt           # Main entry point, navigation setup
│   │   ├── RelateAIApp.kt            # Application class, HiltAndroidApp
│   │   ├── SecurityChecks.kt         # Certificate pin expiry check
│   │   ├── ui/                       # Active Compose screens, navigation, and ViewModels
│   │   └── automation/
│   │       └── notifications/
│   │           └── MessageEditActivity.kt
│   ├── kotlin/com/example/           # Kotlin source (not used, prefer java path)
│   └── res/                          # Resources
│       ├── layout/                   # XML layouts (minimal, mostly Compose)
│       ├── drawable/                 # Drawables
│       └── mipmap-*/                 # App icons
├── build/
├── schemas/                          # Room schema exports (JSON)
└── google-services.json              # Firebase config
```

```
core/
├── domain/                           # Business logic (no Android dependencies)
│   ├── src/main/kotlin/com/example/domain/
│   │   ├── repository/               # Repository interfaces
│   │   ├── usecase/                  # Use case classes (e.g., RefreshHealthScoresUseCase)
│   │   └── model/                    # Domain models
│   └── build.gradle.kts
│
├── data/                             # Data layer implementation (Room, API clients)
│   ├── src/main/kotlin/com/example/
│   │   ├── core/
│   │   │   ├── db/                   # Room database, entities, DAOs
│   │   │   ├── auth/                 # AuthManager
│   │   │   ├── prefs/                # SecurePrefs, encrypted storage
│   │   │   ├── gemini/               # Gemini AI client
│   │   │   └── automation/           # Workers, scheduling, senders, notifications
│   │   ├── data/                     # Repository implementations
│   │   └── di/                       # Hilt modules (AppModule, AppModuleBinds)
│   └── build.gradle.kts
│
└── ui/                               # Shared UI components and theme
    ├── src/main/kotlin/com/example/core/ui/
    │   ├── components/               # Reusable Compose primitives
    │   └── theme/                    # Color, Typography, Theme composition
    └── build.gradle.kts
```

```
feature/                              # Not active
└── Do not add new code here. Feature UI currently lives under app/src/main/java/com/example/ui/.
```

## Architecture Patterns

### Clean Architecture Layers

```
UI Layer (app/src/main/java/com/example/ui)
    ↓ uses
Domain Layer (core/domain)
    ↓ uses
Data Layer (core/data)
    ↓ uses
External Dependencies (Room, Firebase, Network)
```

### Dependency Injection with Hilt

- Use `@HiltAndroidApp` in Application class
- Use `@EntryPoint` for access from non-injectable classes (e.g., MainActivity)
- Use `@AndroidEntryPoint` for Activities/Fragments
- Use `@HiltViewModel` for ViewModels
- Module bindings in `core/data/src/main/kotlin/com/example/di/AppModule.kt`

### Repository Pattern

- Define interfaces in `core/domain/repository/`
- Implement in `core/data/src/main/kotlin/com/example/data/repository/`
- Inject repositories into domain layer use cases

### Database (Room)

- Main database: `AppDatabase` with SQLCipher encryption
- Entities: `ContactEntity`, `EventEntity`, `PendingMessageEntity`, etc.
- DAOs: `ContactDao`, `EventDao`, `PendingMessageDao`, etc.
- Migrations: Defined in `AppDatabase.MIGRATION_*` constants
- Schema exports: Stored in `app/schemas/`

### Data Storage

- **Encrypted SharedPreferences**: `SecurePrefs` for sensitive data (tokens, credentials)
- **Room Database**: Local persistence for contacts, events, messages
- **Firebase**: Remote authentication and Gemini AI integration

## Code Style Conventions

### Naming

- **Classes**: PascalCase (e.g., `MainActivity`, `LoginViewModel`)
- **Functions/Variables**: camelCase (e.g., `signInWithGoogle`, `healthScore`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `DEFAULT_SIGN_IN`)
- **Repository Methods**: Verb phrases (e.g., `getAll()`, `updateHealthScore()`)
- **Use Cases**:Verb phrases in present tense (e.g., `refreshHealthScores()`)

### Compose UI

- Use Material 3 components (`MaterialTheme`, `Surface`, etc.)
- Theme: `MyApplicationTheme(darkTheme = isDark)`
- Colors: `RelateAIColors` object from `core/ui`
- Dark mode support with `DarkColorScheme` and `LightColorScheme`

### Coroutines & Flow

- Use `kotlinx.coroutines` for async operations
- Repository methods return `Flow<T>` for reactive data streams
- Use `suspend` for one-shot operations
- Always specify dispatchers: `Dispatchers.IO` for database/network, `Dispatchers.Main` for UI

### Error Handling

- Use try-catch with logging via `android.util.Log`
- Handle exceptions gracefully in UI layer
- Use sealed classes for success/failure states where appropriate

### Security

- Never hardcode secrets - use environment variables or secure storage
- Use `EncryptedSharedPreferences` for credentials
- Certificate pinning configured in `network_security_config.xml`
- Biometric lock supported via `androidx.biometric`
