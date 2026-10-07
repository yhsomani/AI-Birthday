#!/bin/bash
find lib/ -name "*.dart" -exec grep -l "onTap:" {} \; | while read file; do
  echo "--- $file ---"
  grep -A 3 "onTap: () {" "$file" | grep -v "HapticFeedback"
done
