#!/bin/bash
grep -rn "Text(\"" /workspace --include="*.kt" | grep -v "stringResource" | grep -v "/build/" | grep -v "strings.xml"
