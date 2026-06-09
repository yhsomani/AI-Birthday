

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.hilt.android) apply false
  alias(libs.plugins.google.services) apply false
}

val buildJvmVersion = 21

subprojects {
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
            jvmToolchain(buildJvmVersion)
        }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    tasks.withType<Test>().configureEach {
        val toolchainService = project.extensions.findByType<org.gradle.jvm.toolchain.JavaToolchainService>()
            ?: project.rootProject.extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>()
        javaLauncher.set(toolchainService.launcherFor {
            languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(buildJvmVersion))
        })

        val localTrustStore = rootProject.layout.projectDirectory
            .file(".gradle/trust/cacerts-zscaler")
            .asFile
        if (localTrustStore.exists()) {
            systemProperty("javax.net.ssl.trustStore", localTrustStore.absolutePath)
            systemProperty("javax.net.ssl.trustStorePassword", "changeit")
        }
    }
}
