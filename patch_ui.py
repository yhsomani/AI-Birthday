import re

with open('core/ui/build.gradle.kts', 'r') as f:
    content = f.read()

content = content.replace('alias(libs.plugins.kotlin.android)', '')

with open('core/ui/build.gradle.kts', 'w') as f:
    f.write(content)
