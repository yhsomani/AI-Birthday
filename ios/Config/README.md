# iOS provider configuration

Provider files are intentionally absent from source control. Supply exactly one file for the
selected build tier:

- `dev/GoogleService-Info.plist` for `com.yashsomani.birthdayautopilot.dev`
- `staging/GoogleService-Info.plist` for `com.yashsomani.birthdayautopilot.staging`
- `prod/GoogleService-Info.plist` for `com.yashsomani.birthdayautopilot`

Set `BIRTHDAY_FIREBASE_PROJECT_ID` and `BIRTHDAY_GOOGLE_REVERSED_CLIENT_ID` as protected CI or
command-line build-setting overrides. The copy phase validates the plist's tier, bundle ID, project ID, iOS OAuth client,
and reversed URL scheme before copying it into the product. Release requires a valid prod config;
Debug remains buildable without provider access and fails closed at runtime.

A Release device/archive build also requires `BIRTHDAY_SOURCE_REVISION` to equal the clean
checkout's full lowercase Git revision. It is embedded as `BirthdaySourceRevision`, and the final
signed archive/IPA verifier binds it to the authority-approved artifact evidence.

Release also defaults `BIRTHDAY_PRIVACY_REVIEW_APPROVED` to `NO`. CI may set it to `YES` only
after the privacy inventory, SDK manifests, App Store privacy answers, Google-specific-service
login rationale, deletion URL, and reviewer materials have named review evidence. The app privacy
manifest declares required-reason API use and no tracking; it deliberately does not invent a final
`NSPrivacyCollectedDataTypes` answer before that review.

Each used tier must have a separate Firebase/Google Cloud project, iOS Firebase app, OAuth client,
API-key bundle restriction, App Attest registration, and Google Identity/Firebase/Functions App
Check enforcement. Never add a server client ID, OAuth client secret, auth code, refresh token, or
debug App Check provider to this target.
