# Design System Requirements

The existing Material-3 design system, bundled fonts, spacing/color tokens, celebration cards, chips and empty-state components should be retained. (`ER` supported by implementation evidence.)

Required evolution:
- Document components as contracts: purpose, states, semantics, motion, tokens, content bounds and responsive variants.
- Normalize terminology for “draft”, “handed off”, “user-confirmed sent”, “backup”, “restore”, “delete”, “unavailable”, and “locked”.
- Keep celebratory character while avoiding status decoration that obscures meaning.
- Motion respects reduced-motion; haptics are additive.
- Error and empty content leads to a clear action; no blame, secret, internal ID or PII leakage.
- Do not surface dead controls for disabled auto-SMS/auto-prepare/Autopilot.
