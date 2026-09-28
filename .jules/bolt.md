## 2024-05-24 - [Optimize Redaction Regex]
**Learning:** String `replaceAll` and `toLowerCase` operations inside loops during parameter redaction represent a significant logging overhead in Dart.
**Action:** Pre-compile `RegExp` with `caseSensitive: false` to optimize frequent string filtering logic.
