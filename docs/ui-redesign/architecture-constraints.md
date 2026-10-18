# Architecture & Technical Constraints

## 1. Non-Negotiable Boundaries
- **Local-First SQLite Persistence**: All birthdays and contacts reside locally in SQLite using Drift. UI components must read from Riverpod streams (`watch(birthdaysStreamProvider)`) and never bypass or replace them with mocks in production code.
- **Strict User Consent for External Messaging**: External messaging channels (WhatsApp & SMS) must always adhere to explicit user confirmation. WhatsApp handoff uses URI deep-links (`whatsapp://send`), while automated carrier SMS requires explicit consent opt-in.
- **Flavor Separation**: The repository supports two distinct build flavors:
  - `personal`: Allows full direct hardware SMS options and developer Gemini configurations.
  - `play`: Compliant with Google Play Store SMS/Call Log policies; strictly separates direct SMS behind Play policy constraints.
- **Secure Key Storage**: Gemini API keys and sensitive auth tokens are stored strictly within `flutter_secure_storage` with hardware-backed encryption.
- **Theme Extension Contract**: All styling derives from `AppPalette` and `Theme.of(context).extension<AppPalette>()`. Hardcoded color literals across screens are prohibited; WCAG AA contrast is validated by `test/ui/contrast_test.dart`.
