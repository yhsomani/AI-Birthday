plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
}

fun releaseSigningIssues(): List<String> = buildList {
    val keystorePath = System.getenv("KEYSTORE_PATH")
    if (keystorePath.isNullOrBlank()) {
        add("KEYSTORE_PATH")
    } else if (!file(keystorePath).isFile) {
        add("KEYSTORE_PATH must point to an existing keystore file")
    }

    if (System.getenv("STORE_PASSWORD").isNullOrBlank()) add("STORE_PASSWORD")
    if (System.getenv("KEY_ALIAS").isNullOrBlank()) add("KEY_ALIAS")
    if (System.getenv("KEY_PASSWORD").isNullOrBlank()) add("KEY_PASSWORD")
}

fun releaseSigningFailureMessage(issues: List<String>): String =
    "Release signing is not configured. Missing or invalid: ${issues.joinToString()}. " +
        "Set KEYSTORE_PATH, STORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD for production release builds."

fun validateReleaseSigning() {
    val issues = releaseSigningIssues()
    if (issues.isNotEmpty()) {
        throw GradleException(releaseSigningFailureMessage(issues))
    }
}

val releaseSigningConfigured = releaseSigningIssues().isEmpty()
val releaseArtifactTaskNames = setOf(
    "assemble",
    "build",
    "assembleRelease",
    "bundleRelease",
    "packageRelease",
    "signReleaseBundle",
    "validateSigningRelease",
)
fun isAppReleaseArtifactRequest(taskName: String): Boolean {
    val normalizedTaskName = taskName.substringAfterLast(":")
    val targetsRootOrApp = !taskName.contains(":") ||
        taskName.startsWith(":app:") ||
        taskName.startsWith("app:")
    return targetsRootOrApp && normalizedTaskName in releaseArtifactTaskNames
}

if (gradle.startParameter.taskNames.any(::isAppReleaseArtifactRequest)) {
    validateReleaseSigning()
}

android {
    namespace = "com.example"
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aistudio.relateai.qxtjrk"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (releaseSigningConfigured && keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isCrunchPngs = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Install validation builds beside production-signed installs without deleting app data.
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "META-INF/gradle/incremental.annotation.processors"
            )
        }
    }

    baselineProfile {
        mergeIntoMain = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.useJUnit {
                    if (project.hasProperty("screenshot")) {
                        includeCategories("com.example.ui.screenshots.ScreenshotTests")
                    } else {
                        excludeCategories("com.example.ui.screenshots.ScreenshotTests")
                    }
                }
            }
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.vertexai)
    implementation(libs.firebase.analytics)
    
    // RelateAI app/data dependencies used by the active app UI and application shell.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.sun.mail.android)
    implementation(libs.sun.mail.activation)
    implementation(libs.google.api.client)
    implementation(libs.google.api.people)
    implementation(libs.play.services.auth)
    implementation(libs.converter.moshi)
    implementation(libs.sqlcipher)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.retrofit)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Android core dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Room
    implementation(libs.androidx.room.runtime)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Image loading
    implementation(libs.coil.compose)

    // Paging
    implementation(libs.androidx.paging.runtime)
    testImplementation(libs.androidx.paging.runtime)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.core)
}

tasks.matching {
    it.name in setOf(
        "packageRelease",
        "signReleaseBundle",
        "validateSigningRelease",
    )
}.configureEach {
    doFirst {
        validateReleaseSigning()
    }
}
