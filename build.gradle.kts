
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport


plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.hilt.android) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.roborazzi) apply false
  jacoco
}

val buildJvmVersion = 21
val coverageReportRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.substringAfterLast(":") == "jacocoDebugUnitTestReport"
}
val debugCoverageProjectPaths = listOf(":app", ":core:data", ":core:domain", ":core:ui")
val debugCoverageClassDirectories = mapOf(
    ":app" to "intermediates/classes/debug/transformDebugClassesWithAsm/dirs",
    ":core:data" to "intermediates/classes/debug/transformDebugClassesWithAsm/dirs",
    ":core:domain" to "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
    ":core:ui" to "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
)
val coverageClassExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "dagger/hilt/**",
    "hilt_aggregated_deps/**",
    "**/*JsonAdapter.*",
    "**/*_Factory.*",
    "**/*Factory.*",
    "**/*_AssistedFactory*.*",
    "**/*_GeneratedInjector.*",
    "**/*_MembersInjector.*",
    "**/Dagger*.*",
    "**/Hilt_*.*",
    "**/*Hilt*.*",
    "**/*HiltModules*.*",
    "**/*Module*.*",
    "**/*Dao_Impl.*",
    "**/*Database_Impl.*",
    "**/*ComposableSingletons*.*",
)

jacoco {
    toolVersion = "0.8.12"
}

subprojects {
    if (coverageReportRequested) {
        apply(plugin = "jacoco")

        extensions.configure<JacocoPluginExtension> {
            toolVersion = "0.8.12"
        }
    }

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

        if (coverageReportRequested) {
            extensions.configure<JacocoTaskExtension> {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
            outputs.upToDateWhen { false }
        }

        val localTrustStore = rootProject.layout.projectDirectory
            .file(".gradle/trust/cacerts-zscaler")
            .asFile
        if (localTrustStore.exists()) {
            systemProperty("javax.net.ssl.trustStore", localTrustStore.absolutePath)
            systemProperty("javax.net.ssl.trustStorePassword", "changeit")
        }
    }
}

gradle.projectsEvaluated {
    val debugCoverageProjects = debugCoverageProjectPaths.map(::project)
    tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Generates aggregate JaCoCo coverage for debug unit tests."

        dependsOn(debugCoverageProjects.map { it.tasks.named("testDebugUnitTest") })

        reports {
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
        }

        classDirectories.setFrom(
            files(
                debugCoverageProjects.map { project ->
                    val classDirectory = requireNotNull(debugCoverageClassDirectories[project.path]) {
                        "Missing coverage class directory mapping for ${project.path}"
                    }
                    project.fileTree(project.layout.buildDirectory.dir(classDirectory)) {
                        exclude(coverageClassExcludes)
                    }
                },
            ),
        )
        sourceDirectories.setFrom(
            files(
                debugCoverageProjects.flatMap { project ->
                    listOf(
                        project.layout.projectDirectory.dir("src/main/java"),
                        project.layout.projectDirectory.dir("src/main/kotlin"),
                    )
                },
            ),
        )
        executionData.setFrom(
            files(
                debugCoverageProjects.map { project ->
                    project.fileTree(project.layout.buildDirectory) {
                        include(
                            "jacoco/testDebugUnitTest.exec",
                            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                        )
                    }
                },
            ),
        )
    }
}
