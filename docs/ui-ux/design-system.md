# AI-Birthday — Design System Specification

**Version:** 2.0.0 (October 2026)  
**Status:** Authoritative  
**Stitch Asset Reference:** `assets/3123978714690340693` (AI-Birthday Editorial Design System)  
**Foundation:** Material 3 Adaptive + Editorial Typography + Anti-Generic Principles  

---

## 1. Design Philosophy & Principles

The AI-Birthday design system delivers a warm, trustworthy, and human-centric experience for celebrating life's relationships.

### Core Tenets
1. **Calm, Invisible Utility**: A silent, reliable companion that organizes dates and drafts greetings without noisy visual distraction.
2. **Editorial Warmth Over Tech SaaS Cliché**: Strictly avoid neon AI gradients, bubbly candy pills, decorative floating shapes, and fake reviews. Celebrate relationships with warm terracotta, vintage amber, forest green accents, and natural linen surfaces.
3. **Information Density with Generous Whitespace**: Above the fold clearly answers **What, Who, Why, and Next**. Structural hierarchy replaces card soup.
4. **Touch-First Accessibility**: Strictly enforce $\ge 48\text{dp}$ touch targets for all interactive elements, WCAG 2.1 AA contrast ratios ($\ge 4.5:1$), and dynamic text scale tolerance up to $1.5\times$ without clipping or overflow.

---

## 2. Color System Tokens

```
                   LIGHT THEME                               DARK THEME
Primary:           #A64B2A (Warm Terracotta)                 #E28464 (Terracotta Light)
Secondary Accent:  #D9822B (Vintage Amber)                   #F0A65B (Warm Amber)
Tertiary Positive: #2D5A46 (Forest Green)                    #4E8B6D (Sage Forest)
Background:        #FAF7F2 (Linen Natural)                   #161413 (Espresso Charcoal)
Surface:           #FFFFFF (Crisp White)                     #201D1B (Warm Slate)
Surface Variant:   #F2ECE4 (Linen Muted)                     #2B2724 (Deep Charcoal)
Outline / Border:  #E5DDD3 (Stone Border)                    #38332F (Muted Divider)
Text Primary:      #1C1917 (Stone 900)                       #F5F5F4 (Stone 100)
Text Secondary:    #78716C (Stone 500)                       #A8A29E (Stone 400)
Error:             #BA1A1A (Crimson Red)                     #FFB4AB (Soft Red)
```

### Color Semantics Table
| Token Name | Light Value | Dark Value | Usage |
| :--- | :--- | :--- | :--- |
| `AppColors.primary` | `#A64B2A` | `#E28464` | Primary brand moments, filled action buttons, selected navigation icons |
| `AppColors.secondary` | `#D9822B` | `#F0A65B` | Action needed banners, celebration badges, upcoming countdown chips |
| `AppColors.positive` | `#2D5A46` | `#4E8B6D` | Confirmed sent badges, WhatsApp handoff successes, verified facts |
| `AppColors.background` | `#FAF7F2` | `#161413` | Scaffold background, un-elevated surfaces |
| `AppColors.surface` | `#FFFFFF` | `#201D1B` | Cards, modal sheets, popups |
| `AppColors.surfaceVariant`| `#F2ECE4` | `#2B2724` | Input field fills, disabled chips, subtle container backdrops |
| `AppColors.border` | `#E5DDD3` | `#38332F` | Structural card borders (1px solid), dividers |
| `AppColors.textPrimary` | `#1C1917` | `#F5F5F4` | Headlines, titles, primary body text |
| `AppColors.textMuted` | `#78716C` | `#A8A29E` | Secondary captions, timestamps, placeholder text |

---

## 3. Typography Scale

The type system blends an **Editorial Serif** for display and headline moments with a highly legible, humanistic sans-serif (**Inter** / **Nunito**) for body copy and dense metadata.

| Role | Font Family | Size | Weight | Line Height | Tracking |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Headline XL** | Playfair Display / Serif | 32sp | Bold (700) | 40sp | -0.5px |
| **Headline LG** | Playfair Display / Serif | 24sp | Bold (700) | 32sp | -0.3px |
| **Headline MD** | Playfair Display / Serif | 20sp | SemiBold (600)| 28sp | -0.2px |
| **Title LG** | Inter / Nunito | 18sp | Bold (700) | 24sp | 0.0px |
| **Title MD** | Inter / Nunito | 16sp | SemiBold (600)| 22sp | 0.1px |
| **Body LG** | Inter / Nunito | 16sp | Regular (400) | 24sp | 0.0px |
| **Body MD** | Inter / Nunito | 14sp | Regular (400) | 20sp | 0.0px |
| **Label MD** | Inter / Nunito | 12sp | SemiBold (600)| 16sp | 0.5px (Uppercase) |
| **Label SM** | Inter / Nunito | 11sp | Medium (500) | 14sp | 0.4px |

---

## 4. Spacing, Rhythm & Radii Tokens

All layouts adhere to an 8dp linear grid (with 4dp micro-adjustments).

```dart
class AppSpacing {
  static const double xxs = 4.0;
  static const double xs = 8.0;
  static const double sm = 12.0;
  static const double md = 16.0;
  static const double lg = 20.0;
  static const double xl = 24.0;
  static const double xxl = 32.0;

  static const double touchTargetMin = 48.0;
  static const double buttonHeight = 48.0;
  static const double inputHeight = 52.0;
  static const double bottomClearance = 88.0; // Ensures content clears FAB/NavigationBar
}
```

### Corner Radii Hierarchy
- **Small (6px – 8px)**: Badges, countdown chips, inline tags, small input icons.
- **Medium (10px – 12px)**: Input fields, standard buttons, dialogs, cards.
- **Large (16px – 20px)**: Modal bottom sheets, major hero containers.
- **Full (9999px)**: Avatars and circular status indicators. (Avoid pill buttons for primary actions).

---

## 5. Reusable Component Specifications

### 5.1 Responsive Action Bar (`ResponsiveActionBar`)
- **Problem Fixed**: Squeezed action buttons in `MessageStudioScreen` and `DashboardScreen`.
- **Behavior**: On viewports with width $\ge 380\text{dp}$, renders side-by-side with `Expanded`. On viewports $< 380\text{dp}$ or when text scale $> 1.15\times$, automatically stacks buttons vertically with full width, guaranteeing zero overflow.

### 5.2 Countdown Badge (`CountdownChip`)
- Displays contextual time remaining:
  - `Today`: Primary terracotta tint with bold text.
  - `Tomorrow`: Amber tint with semi-bold text.
  - `In N days`: Neutral surface container tint.
- Guarantees minimum height 32dp and readable text contrast.

### 5.3 Contact Celebration Card (`CelebrationCard`)
- 1px structural stone border, 0 elevation.
- Clear initials avatar, recipient name, turns-age calculation, and accessible action trigger.
- Trailing actions stacked cleanly to avoid colliding with long names.

### 5.4 Scroll-Safe Modal Bottom Sheet (`AppBottomSheet`)
- Enforces `Flexible` + `SingleChildScrollView` + `SafeArea(bottom: true)`.
- Eliminates the crash where unconstrained facts lists exceed screen bounds.
- Includes drag handle indicator (32x4dp).

### 5.5 Section Header (`AppSectionHeader`)
- Uniform uppercase tracking (`0.8px`), 12sp, bold, primary terracotta color.
- Optional count pill or action link aligned right.
