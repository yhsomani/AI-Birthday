pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "My Application"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":feature:splash")
include(":feature:login")
include(":feature:dashboard")
include(":feature:contacts")
include(":feature:events")
include(":feature:analytics")
include(":feature:onboarding")
include(":feature:settings")
include(":feature:messages")

