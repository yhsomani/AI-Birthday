1. **Fix missing HapticFeedback on `PeopleScreen` list item tap (`lib/features/people/presentation/people_screen.dart`):**
   - The user currently selects a contact from the list view on `PeopleScreen` and a modal bottom sheet opens (`_showPersonDetailsModal(context, ref, person)`).
   - This interaction is high-frequency, primary UI, and changes state (opens a modal). As noted in `.jules/palette.md`, missing haptics on primary interactive elements breaks the responsive feel of the UI.
   - We will replace `onTap: () => _showPersonDetailsModal(context, ref, person),` with a block that includes `HapticFeedback.lightImpact();`.

2. **Fix missing HapticFeedback on `CalendarScreen` list item tap (`lib/features/calendar/presentation/calendar_screen.dart`):**
   - When a user taps on a calendar day item, or on a person inside the bottom sheet, haptic feedback should fire for consistency.
   - I'll add `HapticFeedback.lightImpact();` to the `onTap` block.

3. **Verify Changes:**
   - I will run `dart format .` and `flutter analyze` and `flutter test` to ensure there are no compilation or syntax errors.

4. **Add pre-commit steps:**
   - Complete pre-commit steps to make sure proper testing, verifications, reviews and reflections are done.

5. **Submit Change:**
   - Create a pull request using the format: "🎨 Palette: Add missing haptic feedback to list item interactions".
