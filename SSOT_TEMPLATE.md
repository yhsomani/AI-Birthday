# SSOT.md: The Ultimate Project Single Source of Truth (Ultra-Detailed)

> **Template Version**: 2.0  
> **Status**: Master Template  
> **Target Audience**: Human Developers, Product Managers, AI Agents, Stakeholders.  
> **Goal**: 100% context parity. A reader should be able to contribute meaningfully after reading this document without asking a single clarification question.

---

## 0. Implementation Guide (How to use this)
- **Living Document**: This is not a "write once" file. It must be updated at every milestone.
- **AI-First**: When using an AI assistant, point them to this file first. It contains the "Source of Truth" for all their decisions.
- **Link, Don't Duplicate**: If a detailed spec exists elsewhere (e.g., Jira, Figma), provide a high-level summary here and a direct link.
- **The "Context Test"**: If you have to explain something to a new dev that isn't in here, add it immediately.

---

## 1. Executive Summary
- **Why it exists**: The high-level "elevator pitch" and current health of the project.
- **What to include**: 
    - 1-sentence mission statement.
    - Current phase (Discovery, MVP, Scaling, Maintenance).
    - Core tech stack (Language, Primary Framework, Database).
    - Primary KPIs (What does success look like *right now*?).
    - Key Stakeholders (Product Owner, Lead Architect, Lead Designer).
- **Example**: 
    - "RelateAI is an on-device 'Relationship Operating System' that uses local LLMs to automate personal outreach. Currently in MVP hardening. Built with Kotlin/Compose/Room/Gemini. Success is measured by Day-30 retention (Target: 20%)."
- **Common Mistakes**: Over-explaining technical details; ignoring the business purpose.

## 2. Problem Statement
- **Why it exists**: Grounds the team in the "Why". Prevents feature creep that doesn't solve the core pain.
- **What to include**: 
    - The "Gap": Describe the current manual or broken process.
    - The "Cost": What is lost (time, money, relationships) if this isn't solved?
    - The "Evidence": Data points or user quotes validating the pain.
- **Example**: 
    - "Manual relationship management is cognitively expensive. Users report 'reminder fatigue' where they ignore calendar pings because they lack the time to draft a thoughtful message. Cost: 40% of adult friendships reported as 'stale' due to lack of low-stakes contact."
- **Common Mistakes**: Describing the solution instead of the problem.

## 3. Product Vision
- **Why it exists**: The North Star for the next 12–24 months.
- **What to include**: 
    - The qualitative "Future State."
    - What we will NEVER do (Anti-Goals).
    - Emotional impact on the user.
- **Example**: 
    - "To be the invisible layer that makes every person feel like the most important person in your life. We will NEVER sell user contact data or build a social network. Impact: Users feel socially connected without the anxiety of manual tracking."
- **Common Mistakes**: Being too generic (e.g., "To be the best app").

## 4. User Personas
- **Why it exists**: Ensures the UX is tailored to specific behaviors, not a generic "average user."
- **What to include**: 
    - Name/Archetype.
    - Goals (What do they want to achieve?).
    - Frustrations (What stops them?).
    - Tech Savviness (Low/Med/High).
    - Context of Use (On the go, at a desk, etc.).
- **Example**: 
    - "**The Networking Powerhouse (Sarah):** 45, Real Estate. Goal: Maintain 500+ 'warm' leads. Frustration: Generic CRM messages feel 'fake'. Tech: High. Context: Mobile-first, between meetings."
- **Common Mistakes**: Creating too many (keep it to 2-3 primary).

## 5. User Pain Points
- **Why it exists**: The specific "micro-frustrations" that the product must solve.
- **What to include**: 
    - Action-based pains (e.g., "Hard to copy/paste from X to Y").
    - Emotional pains (e.g., "Feeling guilty for forgetting").
    - Cognitive pains (e.g., "Can't remember what we last talked about").
- **Example**: 
    - "1. Drafting fatigue: Takes 5 mins to write one 'Happy Birthday'. 2. Channel fragmentation: Is this person on SMS, WhatsApp, or Email? 3. Fear of bot-sounding messages."
- **Common Mistakes**: Listing bugs instead of UX/Life pains.

## 6. Core Value Proposition
- **Why it exists**: Defines the "Unfair Advantage" of your product.
- **What to include**: 
    - The primary benefit.
    - The secondary benefit.
    - The differentiator (Why you, not a competitor?).
- **Example**: 
    - "1. Effortless Outreach: 1-tap birthday wishes. 2. Deep Personalization: AI that references shared history. 3. Privacy First: All AI and data stay on-device (The 'Unfair Advantage')."
- **Common Mistakes**: Listing basic features as value props.

## 7. Business Requirements (BRDs)
- **Why it exists**: Defines the commercial constraints and success gates.
- **What to include**: 
    - Monetization Model (Freemium, Sub, One-time).
    - Regulatory Compliance (GDPR, HIPAA).
    - Delivery Timeline (Hard vs Soft dates).
    - Hardware/OS Constraints.
- **Example**: 
    - "BR-01: Must comply with GDPR 'Right to be Forgotten'. BR-02: Support Android 10+ (85% market coverage). BR-03: Zero recurring server costs for AI inference."
- **Common Mistakes**: Assuming developers "just know" the business constraints.

## 8. Functional Requirements (FRDs)
- **Why it exists**: The technical "What" in granular detail.
- **What to include**: 
    - Requirement ID.
    - Description (User shall...).
    - Priority (Must/Should/Could).
    - Acceptance Criteria (AC).
- **Example**: 
    - "FR-10: Automated Discovery. The system shall scan device contacts for birthday events daily. AC: Must handle leap years; Must ignore contacts with no phone/email."
- **Common Mistakes**: Being ambiguous (e.g., "Make it look good").

## 9. Non-Functional Requirements (NFRs)
- **Why it exists**: Defines the quality of the system.
- **What to include**: 
    - Performance (Latency, Throughput).
    - Scalability (Concurrent users).
    - Reliability (Uptime %).
    - Security (Encryption standards).
    - Observability (Logging, Analytics).
- **Example**: 
    - "NFR-01: AI response time < 3 seconds. NFR-02: Database must use AES-256 encryption. NFR-03: APK size must remain under 40MB."
- **Common Mistakes**: Not making them measurable (quantifiable).

## 10. Complete Feature Inventory
- **Why it exists**: A single master list to track build progress.
- **What to include**: 
    - Feature Name.
    - Scope (MVP/v1.1/v2.0).
    - Current Status (Concept/Dev/QA/Done).
    - Lead Developer.
- **Example**: 
    - "| Feature | Scope | Status | Owner | |---|---|---|---| | Google Contact Sync | MVP | Done | Alice | | WhatsApp Automation | MVP | In Dev | Bob |"
- **Common Mistakes**: Forgetting to update status; losing track of sub-features.

## 11. Feature Specifications
- **Why it exists**: The source of truth for *how* a feature works.
- **What to include**: 
    - Input/Output.
    - State Machine (Start -> Process -> Success/Error).
    - Edge Cases (No internet, low battery, storage full).
    - Permissions required.
- **Example**: 
    - "Feature: Style Coach. Input: User's sent folder or manual text. Logic: Extract tone/length/emoji-density. Output: StyleProfile JSON. Edge Case: If < 5 samples, use 'Generic Friendly' fallback."
- **Common Mistakes**: Missing edge cases (the 20% of work that takes 80% of time).

## 12. User Flows
- **Why it exists**: Maps the navigation logic and prevents "trapped" screens.
- **What to include**: 
    - Happy Path (Primary journey).
    - Error Paths (Invalid input, Auth failure).
    - Permission Gateways.
- **Example**: 
    - "Login Flow: [Splash] -> [Google Button] -> (Success?) -> [Style Coach] -> [Dashboard]. (Fail?) -> [Retry Screen] -> [Help Link]."
- **Common Mistakes**: Only documenting the "Happy Path."

## 13. System Architecture
- **Why it exists**: The high-level map of the codebase.
- **What to include**: 
    - Architectural Pattern (Clean, Hexagonal, Layered).
    - Module Definitions (What lives where?).
    - Data Flow (Unidirectional vs. Bidirectional).
    - Key Patterns (Repository, Factory, Observer).
- **Example**: 
    - "Pattern: Clean Architecture with MVI. Layers: `:app` (UI), `:domain` (Logic), `:data` (Repo/DB). Data Flow: Intent -> State -> View. Repo uses Room (local) and OkHttp (remote)."
- **Common Mistakes**: Documenting what you *wish* it was instead of the actual code structure.

## 14. Database Schema
- **Why it exists**: Essential for anyone touching data storage.
- **What to include**: 
    - Entity relationship diagram (ERD) description.
    - Table Definitions (Column, Type, Constraints).
    - Indices (Performance).
    - Migration Strategy (Auto vs Manual).
- **Example**: 
    - "Table `events`: `id` (PK, Long), `contact_id` (FK), `type` (ENUM: BIRTHDAY, ANNIVERSARY), `date` (Long). Index: `contact_id`. Migration: Room AutoMigrations where possible."
- **Common Mistakes**: Forgetting about `ON DELETE CASCADE` or indices.

## 15. API Documentation
- **Why it exists**: For integrating with any external or internal service.
- **What to include**: 
    - Endpoint URL.
    - Request Params/Body (Typed).
    - Response Body (Typed).
    - Error Codes (4xx, 5xx meanings).
    - Rate Limits / Quotas.
- **Example**: 
    - "Gemini Proxy: POST `/v1/generate`. Request: `GenerateRequest(prompt: String)`. Response: `200 OK: { text: String }`. Error: `429: Rate limit hit (60 RPM)`."
- **Common Mistakes**: Not providing example JSON payloads.

## 16. Third-Party Integrations
- **Why it exists**: Inventory of all dependencies and "black boxes."
- **What to include**: 
    - Provider Name.
    - SDK/Version.
    - Purpose (Why this one?).
    - Criticality (Does app die if this goes down?).
- **Example**: 
    - "1. Hilt (v2.5): DI. Critical. 2. SQLCipher (v4.5): DB Encryption. Critical. 3. Firebase (v32): Analytics. Non-critical."
- **Common Mistakes**: Using "mystery" libraries that nobody understands.

## 17. Authentication & Authorization
- **Why it exists**: The security gate of the app.
- **What to include**: 
    - Identity Provider (Google, Custom, SSO).
    - Token Handling (JWT, Refresh tokens, storage).
    - Scopes (What data do we have access to?).
    - Session Lifecycle (Timeout, Logout).
- **Example**: 
    - "Auth: Google OAuth 2.0. Scopes: `contacts.readonly`, `profile`. Tokens: Stored in `EncryptedSharedPreferences`. Refresh logic: Proactive refresh at 50 mins (60 min expiry)."
- **Common Mistakes**: Storing tokens in cleartext.

## 18. State Management
- **Why it exists**: How data is synced between logic and UI.
- **What to include**: 
    - Framework/Library (StateFlow, Redux, Bloc).
    - Event System (Single-fire vs Stream).
    - Persistence (How state survives process death).
- **Example**: 
    - "ViewModels expose `StateFlow<UiState>`. Single-fire events (Toast, Nav) via `Channel`. Process death: `SavedStateHandle` stores `contact_id`."
- **Common Mistakes**: "Drifting state" where UI shows something different than the DB.

## 19. Frontend Architecture
- **Why it exists**: Standards for building UI components.
- **What to include**: 
    - Design Pattern (Atomic, Component-based).
    - Styling methodology (Vanilla CSS, Theme-driven).
    - Component lifecycle.
    - Responsive Strategy (Phone vs Tablet vs Desktop).
- **Example**: 
    - "Jetpack Compose. Atoms: `RelateButton`, `RelateTextField`. Layouts: `Scaffold` driven. Tablet support: `NavigationRail` and 2-pane layout for detail screens."
- **Common Mistakes**: Hardcoding UI logic into Fragments/Activities.

## 20. Backend Architecture
- **Why it exists**: Standards for the server-side logic (if any).
- **What to include**: 
    - Language/Runtime (Node, Go, Python).
    - Database (SQL vs NoSQL).
    - Infrastructure (Serverless vs K8s).
    - API Protocol (REST, GraphQL, gRPC).
- **Example**: 
    - "N/A (Local-first). Future: Go-based microservice on AWS Lambda for cross-device sync. PostgreSQL for relational data."
- **Common Mistakes**: Over-engineering a backend for a client-heavy app.

## 21. Infrastructure & Deployment
- **Why it exists**: The "DevOps" view of the project.
- **What to include**: 
    - Source Control (Branching strategy: GitFlow vs Trunk).
    - CI/CD Tools (GitHub Actions, Bitrise).
    - Deployment Targets (Staging, Beta, Prod).
    - Build Variations (Debug, Release, Internal).
- **Example**: 
    - "GitHub Actions: Lint -> Test -> Build. Release Branch: Merges to `main` trigger Play Console upload. Flavour: `staging` uses mock API, `prod` uses real Gemini."
- **Common Mistakes**: Not having an automated "Build -> Test" gate.

## 22. Environment Variables
- **Why it exists**: Configuration that must never be hardcoded.
- **What to include**: 
    - Variable Name.
    - Description.
    - Default/Fallback.
    - Where to find/request access.
- **Example**: 
    - "`GEMINI_API_KEY`: Required for message gen. Default: Empty (App fails). Access: Request via Team Password Manager."
- **Common Mistakes**: Committing `.env` files to Git.

## 23. Coding Standards
- **Why it exists**: Ensures the codebase looks like it was written by one person.
- **What to include**: 
    - Style Guide (Kotlin Official, Airbnb, etc.).
    - Linting Rules (Custom Detekt/Checkstyle).
    - File Naming conventions.
    - Commenting philosophy (Why vs What).
- **Example**: 
    - "Rule 1: No `!!` (null-assertions). Rule 2: Max function length 40 lines. Rule 3: Repository methods must be `suspend`. Linter: `ktlint` + `detekt`."
- **Common Mistakes**: Having rules that aren't enforced by CI.

## 24. Design System
- **Why it exists**: The bridge between Figma and Code.
- **What to include**: 
    - Typography Scale (H1, Body, Caption).
    - Color Palette (Primary, Secondary, Semantic).
    - Spacing/Grid system (8dp grid).
    - Iconography library.
- **Example**: 
    - "Theme: Material 3. Colors: Purple-40 (Primary), Red-Error. Font: Inter (Regular/Bold). Grid: 4/8/16/32 units. Icon Set: Phosphor Icons (Light weight)."
- **Common Mistakes**: Developers "eyeballing" designs instead of using tokens.

## 25. Testing Strategy
- **Why it exists**: The safety net for refactoring.
- **What to include**: 
    - Unit Testing (Tools, Coverage goals).
    - Integration Testing (DB, Network).
    - UI/End-to-End Testing.
    - Mocking strategy (Fakes vs Mocks).
- **Example**: 
    - "Unit: JUnit5/MockK. Integration: Room `inMemoryDatabaseBuilder`. UI: Compose Test Rule + Maestro for E2E. Goal: 100% coverage on Domain layer."
- **Common Mistakes**: Testing implementation details instead of behavior.

## 26. Analytics & Monitoring
- **Why it exists**: How we know if the product is actually working.
- **What to include**: 
    - Tracking Plan (List of events).
    - Crash Reporting (Sentry/Crashlytics).
    - Performance Monitoring (Trace points).
    - Business Dashboards (Metabase/Mixpanel).
- **Example**: 
    - "Event: `message_sent` (variant_type, channel). Event: `onboarding_complete`. Monitoring: Sentry. Analytics: Firebase."
- **Common Mistakes**: Tracking "everything," which results in tracking "nothing useful."

## 27. Security Requirements
- **Why it exists**: Defines the "Zero Trust" boundary.
- **What to include**: 
    - At-rest encryption (DB, Prefs).
    - In-transit encryption (TLS 1.3).
    - Input validation strategy.
    - Secret management.
- **Example**: 
    - "SEC-01: No hardcoded API keys. SEC-02: DB password derived via PBKDF2. SEC-03: R8/ProGuard obfuscation enabled in release."
- **Common Mistakes**: Assuming local storage is "safe" by default.

## 28. Performance Requirements
- **Why it exists**: Defines the user's perception of speed.
- **What to include**: 
    - Boot time.
    - Frame rate (FPS).
    - Network timeout.
    - Memory footprint.
- **Example**: 
    - "PERF-01: < 1.5s cold start on Pixel 6. PERF-02: Scroll Jitter < 2% (JankStats). PERF-03: Background worker max 2 mins runtime."
- **Common Mistakes**: Only testing on high-end developer devices.

## 29. Known Issues & Technical Debt
- **Why it exists**: Acknowledges current flaws to prevent "re-discovery."
- **What to include**: 
    - Bug ID / Link.
    - Description.
    - Workaround (if any).
    - Debt items (Refactors needed).
- **Example**: 
    - "TD-05: `MainViewModel` has 1200 lines; needs split into `ContactsViewModel`. KI-02: WhatsApp sender doesn't work on dual-SIM devices."
- **Common Mistakes**: Hiding debt; it always comes back to haunt you.

## 30. Product Roadmap
- **Why it exists**: Contextualizes current work within the "Big Picture."
- **What to include**: 
    - Current Sprint focus.
    - Next Quarter (H1/H2).
    - Long-term (Vision items).
- **Example**: 
    - "Current: MVP Hardening (Security/Tests). Q3: Google Calendar Integration. Q4: Smart Gift Recommendations. 2027: Multi-platform (Desktop/iOS)."
- **Common Mistakes**: Treating a roadmap like a fixed-date contract.

## 31. Decision Log (ADRs)
- **Why it exists**: Records the "Why" to stop "Why didn't we just...?" questions.
- **What to include**: 
    - ADR ID / Date.
    - Problem.
    - Decision.
    - Consequence (Pros/Cons).
- **Example**: 
    - "ADR-01: Use Room over SQLDelight. Reason: Team expertise and mature auto-migrations. Consequence: Less Kotlin-multiplatform flexibility later."
- **Common Mistakes**: Changing architectural directions without recording the reason.

## 32. Glossary of Terms
- **Why it exists**: Standardizes language across Product and Engineering.
- **What to include**: 
    - Domain terms (Project specific).
    - Technical acronyms.
    - Business concepts.
- **Example**: 
    - "**Stale Contact**: A person not contacted in >90 days. **Revival**: An AI message designed to restart a conversation. **Dispatch**: The final step of sending a message."
- **Common Mistakes**: Assuming "everyone knows" what X means.

## 33. AI Context Section (The AI "Brain")
- **Why it exists**: **CRITICAL.** This is the configuration for your AI coding partner.
- **What to include**: 
    - **Language Style**: "Use idiomatic Kotlin 2.0 (context receivers, etc.)."
    - **Library Preferences**: "Use Moshi for JSON. Never suggest Gson."
    - **Forbidden Patterns**: "Do not use `MutableStateFlow` in UI; use a private backing field."
    - **Refactoring Strategy**: "When changing a DAO, always check the corresponding Repository."
    - **Documentation**: "Comment all public functions using KDoc."
    - **Prompting Hints**: "If I ask for a UI change, always look at `Theme.kt` first."
- **Example**: 
    - "AI-01: Architecture is strictly MVI. AI-02: Use Hilt for all DI. AI-03: When adding a feature, update the Feature Inventory (Section 10) in this file."
- **Common Mistakes**: Being vague. The more specific the rules, the better the AI performs.
