#!/bin/bash
# Search for ListTiles in people_screen.dart and settings_screen.dart that don't have haptic feedback
echo "=== Missing Haptic Feedback in people_screen.dart ==="
grep -A 2 "onTap:" lib/features/people/presentation/people_screen.dart

echo "=== Missing Haptic Feedback in settings_screen.dart ==="
grep -A 2 "onTap:" lib/features/settings/presentation/settings_screen.dart | grep -v HapticFeedback
