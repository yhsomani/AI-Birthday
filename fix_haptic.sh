#!/bin/bash
# Adding HapticFeedback.lightImpact() to settings_screen.dart (onTap items)
# Adding HapticFeedback.lightImpact() to calendar_screen.dart (onTap items)
# Adding HapticFeedback.lightImpact() to people_screen.dart (onTap items)
# Adding HapticFeedback.lightImpact() to message_studio_screen.dart (onTap items)

grep -A 2 "onTap:" lib/features/calendar/presentation/calendar_screen.dart
echo "==============="
grep -A 2 "onTap:" lib/features/settings/presentation/settings_screen.dart
echo "==============="
grep -A 2 "onTap:" lib/features/people/presentation/people_screen.dart
echo "==============="
grep -A 2 "onTap:" lib/features/message_studio/presentation/message_studio_screen.dart
echo "==============="
grep -A 2 "onTap:" lib/features/onboarding/presentation/onboarding_screen.dart
