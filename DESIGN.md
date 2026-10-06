# Design System: AI-Birthday

## 1. Visual Theme & Atmosphere

AI-Birthday is a calm, human-centered daily utility, not an AI showcase. The interface should feel warm, deliberate, trustworthy, and fast.

- Density: 5/10.
- Variance: 6/10.
- Motion: 4/10.
- Primary promise: remember the people, make the next action obvious, keep the user in control.

Avoid visual noise. Emotional value comes from personal context and useful timing, not decorative effects.

## 2. Color Palette & Roles

The interface has one primary accent.

- Linen Canvas (#FAF7F2) — application background.
- Paper Surface (#FFFFFF) — grouped content surfaces.
- Warm Surface (#F2ECE4) — secondary surfaces and form fills.
- Charcoal Ink (#1C1917) — primary text.
- Stone Text (#78716C) — supporting text.
- Structural Border (#E5DDD3) — dividers and outlines.
- Terracotta Accent (#A64B2A) — primary actions, active states and focus.
- Dark Canvas (#161413) — dark-mode background.
- Dark Surface (#201D1B) — dark-mode grouped surface.
- Dark Text (#F5F5F4) — dark-mode primary text.
- Dark Muted (#A8A29E) — dark-mode secondary text.

Success, error and WhatsApp green are semantic or external-brand colors only. They are not competing interface accents.

Never use purple or blue neon, decorative glow, pure black, or oversaturated gradients.

## 3. Typography Architecture

Use Outfit throughout the product UI.

- Display: 32px, weight 700, tight tracking.
- Headline: 24px, weight 700.
- Title: 18px and 16px, weight 600–700.
- Body: 16px and 14px with relaxed leading.
- Label: 12px, weight 600.
- Caption: 11–12px.
- Technical metadata: system monospace only when needed.

Dashboard and utility screens use sans-serif hierarchy. Do not use Inter or generic serif typography for software UI.

## 4. Layout System

- Mobile-first with 360dp as the minimum practical width.
- Base spacing: 4dp, with 8 / 12 / 16 / 20 / 24 / 32dp as common steps.
- Minimum touch target: 48dp.
- Compact-phone side padding: 16dp. Wider layouts may use 20–24dp.
- Never rely on fragile absolute-positioned content for normal UI.
- Use cards only when grouping or hierarchy is meaningful.
- One dominant primary action per surface.
- Do not use three equal feature cards or dense card grids.
- Keep the message visually dominant in Message Studio.
- Respect safe areas and keyboard insets.

## 5. Navigation & Information Architecture

Primary shell:

Dashboard → People → Calendar → History → Settings

Important flows:

Onboarding → core app
People → Add/Edit Person
Birthday → Message Studio
Notification → Message Studio for the relevant person
Message Studio → WhatsApp handoff → explicit user confirmation → History

Top-level navigation remains stable while contextual flows open above it.

## 6. Screen Intent

### Dashboard

Answer: Who needs attention today?

Order:
1. Birthdays today.
2. Action needed.
3. Upcoming birthdays.
4. Useful quick actions.

No filler metrics.

### Onboarding

Three concise steps:
Remember → Prepare → Send.

Explain local-first behavior, optional cloud backup, AI as optional, and the first useful action.

### People

People are the source of truth. Make creation fast. Required fields should remain minimal; advanced personalization preferences should use progressive disclosure.

### Message Studio

The message is the hero. Recipient context is visible but secondary. Generation, editing and delivery should feel like one continuous task.

### History

Show actual lifecycle state. Distinguish Draft, Opened in WhatsApp, and Confirmed sent.

### Settings

Group Account, Backup, Reminders, AI, Subscription and Help. Explain technical concepts only when a decision requires them.

## 7. Component Rules

- Primary button: terracotta fill, 48dp minimum height, 14dp radius, tactile press response, no glow.
- Secondary button: outline or tonal surface with the same height.
- Text input: label above, helper or error below, 14dp radius, visible focus.
- Card: 16dp radius, 1dp structural border, no decorative elevation.
- Bottom sheet: safe-area aware, drag handle, clear title, explicit action.
- Loading: compact progress for actions; skeletons for content-heavy list loading.
- Error: explain the problem and provide the next action while preserving input.
- Empty: explain what is missing and give one obvious next step.
- Snackbar: confirm only the state the app actually knows.

## 8. AI Experience

AI is an optional assistant. The UI must expose capability truthfully:

- AI available.
- AI unavailable and why.
- User credential configured or not.
- On-device AI available or not.
- Generation failed or succeeded.
- Draft requires user review.

Never imply an AI result exists before the provider returns one. Never invent recipient facts.

## 9. Delivery Semantics

- Draft — text is saved locally.
- Ready to send — message is complete.
- Opened in WhatsApp — the external app was opened with the message prepared.
- Confirmed sent — the user explicitly confirmed the send.

Never display Sent merely because a URL launch succeeded.

## 10. Motion & Accessibility

Motion is restrained: short spring/tactile feedback around 150–250ms, transform/opacity preferred, reduced-motion respected.

Every control should have:
- 48dp or larger touch target.
- Useful semantic label.
- Logical focus order.
- Visible focus state.
- Sufficient contrast.
- Usable large-text layout.
- State communication independent of color alone.

## 11. Anti-Patterns — NEVER

- Purple or neon AI gradients.
- Decorative gradients or glow.
- Pure black.
- Inter or generic serif software typography.
- Emoji-based product status copy.
- Fake metrics or fake history.
- Hardcoded birthday records.
- Overlapping or clipped components.
- Three-column equal-card layouts.
- Default pill-shaped controls.
- Vague AI clichés such as Elevate, Seamless, Next-Gen or Unleash.
- Fake success states.
- WhatsApp delivery claims based only on opening WhatsApp.
- Silent data loss after a failed save, network call or handoff.
