## 2024-10-02 - Add Haptic Feedback to Primary Interactive Elements
**Learning:** Adding subtle physical feedback (like haptic impact) to primary interactive elements (such as `FloatingActionButton` and other high-frequency buttons) provides a more satisfying, responsive feel, especially on mobile devices. This enhances the overall intuitive feel of the UI by acknowledging user actions immediately.
**Action:** Implement `HapticFeedback.lightImpact()` inside the `onPressed` handlers of primary interactive elements to consistently provide physical acknowledgement of user interaction.

## 2024-10-03 - Eradicate UI Slop and Generic AI Templates
**Learning:** Overusing simple gradients, "Inter-everywhere" typography, and missing cause-and-effect motion makes an application feel like a generic SaaS template rather than a thoughtful, intent-driven product.
**Action:** Replace arbitrary default Material parameters. Introduce distinctive typography sets (e.g. `google_fonts` using Playfair Display for headers and Nunito for text) combined with solid structural cards and consistent `HapticFeedback.lightImpact()` on all data-mutating buttons to elevate the perceived product fit, clarity, and hierarchy.
## 2024-05-15 - Add Haptic Feedback to FloatingActionButton
**Learning:** Subtle haptic feedback on primary interactive elements like FABs makes the interface feel more responsive and tactile.
**Action:** Always consider adding HapticFeedback.lightImpact() to primary call-to-actions in Flutter apps.
