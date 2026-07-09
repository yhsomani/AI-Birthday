
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.io.File


plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.hilt.android) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.roborazzi) apply false
  jacoco
}

val buildJvmVersion = 21
val androidJdkImageTaskExactNames = setOf(
    "build",
    "check",
    "jacocoDebugUnitTestReport",
    "testDebugUnitTest",
)
val androidJdkImageTaskPrefixes = listOf(
    "assemble",
    "bundle",
    "compile",
    "connected",
    "install",
    "lint",
    "recordRoborazzi",
    "verifyRoborazzi",
)
val androidProjectTaskPathPrefixes = listOf(
    ":app:",
    ":core:data:",
    ":core:ui:",
    "app:",
    "core:data:",
    "core:ui:",
)
fun requestedTasksNeedAndroidJdkImage(): Boolean =
    gradle.startParameter.taskNames.any { taskName ->
        val normalizedTaskName = taskName.substringAfterLast(":")
        val isRootOrAndroidProjectTask =
            !taskName.contains(":") ||
                androidProjectTaskPathPrefixes.any { prefix -> taskName.startsWith(prefix) }

        isRootOrAndroidProjectTask &&
            (
                normalizedTaskName in androidJdkImageTaskExactNames ||
                    androidJdkImageTaskPrefixes.any { prefix -> normalizedTaskName.startsWith(prefix) }
            )
    }

fun jlinkCandidatePaths(javaHome: File, executableName: String): List<File> =
    listOfNotNull(
        javaHome.resolve("bin").resolve(executableName),
        javaHome.parentFile?.resolve("bin")?.resolve(executableName),
    ).distinctBy { it.absolutePath }

val jlinkExecutableName = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
    "jlink.exe"
} else {
    "jlink"
}
val currentJavaHome = File(System.getProperty("java.home"))
val currentJlinkCandidates = jlinkCandidatePaths(currentJavaHome, jlinkExecutableName)

if (requestedTasksNeedAndroidJdkImage() && currentJlinkCandidates.none { candidate -> candidate.isFile }) {
    throw GradleException(
        "Android Gradle tasks require a full JDK 21 with $jlinkExecutableName. " +
            "Gradle is currently running with java.home=${currentJavaHome.absolutePath}, which does not provide " +
            "bin/$jlinkExecutableName. Checked: ${currentJlinkCandidates.joinToString { it.absolutePath }}. " +
            "Configure the IDE Gradle JDK or JAVA_HOME to Android Studio JBR or Temurin JDK 21, then rerun. " +
            "On Windows, avoid the Antigravity/Red Hat extension JRE path because AGP's JdkImageTransform needs jlink."
    )
}

val coverageReportRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.substringAfterLast(":") == "jacocoDebugUnitTestReport"
}
val debugCoverageProjectPaths = listOf(":app", ":core:data", ":core:domain", ":core:ui")
val coverageTestTasks = mapOf(
    ":app" to "testDebugUnitTest",
    ":core:data" to "testDebugUnitTest",
    ":core:domain" to "test",
    ":core:ui" to "testDebugUnitTest",
)
val debugCoverageClassDirectories = mapOf(
    ":app" to "intermediates/classes/debug/transformDebugClassesWithAsm/dirs",
    ":core:data" to "intermediates/classes/debug/transformDebugClassesWithAsm/dirs",
    ":core:domain" to "classes/kotlin/main",
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
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
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

        dependsOn(
            debugCoverageProjects.map { project ->
                val testTask = requireNotNull(coverageTestTasks[project.path]) {
                    "Missing coverage test task mapping for ${project.path}"
                }
                project.tasks.named(testTask)
            },
        )

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
                            "jacoco/test.exec",
                            "jacoco/testDebugUnitTest.exec",
                            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                        )
                    }
                },
            ),
        )
    }
}
