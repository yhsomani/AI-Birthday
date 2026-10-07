#!/bin/bash
echo "Looking for ListTiles with onTap that don't have HapticFeedback inside people_screen.dart..."
grep -A 2 "onTap:" lib/features/people/presentation/people_screen.dart
