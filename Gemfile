source 'https://rubygems.org'

# Match .ruby-version and CI exactly. Do not fall back to Apple's system Ruby.
ruby '3.4.10'

# CocoaPods 1.16.2 requires xcodeproj >= 1.27. Keeping the older React Native
# template's xcodeproj < 1.26 workaround would silently force an obsolete pod
# toolchain, so the lockfile owns the complete compatible transitive graph.
gem 'cocoapods', '1.16.2'

# Ruby 3.4.0 has removed some libraries from the standard library.
gem 'bigdecimal'
gem 'logger'
gem 'benchmark'
gem 'mutex_m'
gem 'nkf'
