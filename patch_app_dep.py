import re

with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

if 'implementation(project(":core:ui"))' not in content:
    content = content.replace('implementation(project(":core:data"))', 'implementation(project(":core:data"))\n    implementation(project(":core:ui"))')

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
