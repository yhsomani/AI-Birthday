# Information Architecture

Primary shell: Dashboard → People → Calendar → History → Settings. Contextual routes: Person detail/form, Message Studio, onboarding, authentication bottom sheet, backup/restore, entitlement and failure recovery surfaces. (`IR`.)

## Navigation rules

- Entity/deep-link routes validate entity/occurrence ownership and fall back without revealing PII.
- Unsaved Studio edits block/back up; failure does not silently discard.
- “Today/upcoming/action needed” derive from the same canonical occurrence.
- Delivery settings, privacy explanation and recovery actions sit adjacent to the operation they affect.
- Conditional/unsupported capability surfaces are hidden or explicitly unavailable; no dead settings.

## Responsive/content model

Use 320–360dp minimum, wrapping actions, dynamic IME safe padding, 200% text, system font scaling, reduced motion and dark/light parity where approved. Public and mobile nomenclature must not mix “AI-Birthday” and “Birthday Autopilot.”
