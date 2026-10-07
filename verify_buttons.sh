#!/bin/bash
find lib/ -name "*.dart" -exec grep -l "onPressed:" {} \; | while read file; do
  echo "--- $file ---"
  grep -A 3 "onPressed: () {" "$file" | grep -v "HapticFeedback"
done
