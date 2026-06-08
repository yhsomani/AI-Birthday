import re

with open('settings.gradle.kts', 'r') as f:
    content = f.read()

if 'include(":core:ui")' not in content:
    content = content.replace('include(":core:data")', 'include(":core:data")\ninclude(":core:ui")')

with open('settings.gradle.kts', 'w') as f:
    f.write(content)
