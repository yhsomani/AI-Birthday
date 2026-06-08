import re

with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

content = content.replace('compileSdk { version = release(36) { minorApiLevel = 1 } }', 'compileSdk = 37')

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
