import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.yashsomani.ai_birthday"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.yashsomani.ai_birthday"
        // Release signing is configured only when android/key.properties is present.
        // See https://flutter.dev/to/review-gradle-config.
        // ML Kit GenAI Prompt API requires Android API 26+.
        minSdk = maxOf(flutter.minSdkVersion, 26)
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        create("release") {
            val keyAliasProp = keystoreProperties["keyAlias"] as String?
            val keyPasswordProp = keystoreProperties["keyPassword"] as String?
            val storeFileProp = keystoreProperties["storeFile"] as String?
            val storePasswordProp = keystoreProperties["storePassword"] as String?

            if (
                keyAliasProp != null &&
                keyPasswordProp != null &&
                storeFileProp != null &&
                storePasswordProp != null
            ) {
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
                storeFile = file(storeFileProp)
                storePassword = storePasswordProp
            }
        }
    }

    buildTypes {
        release {
            // Never silently sign a release artifact with the debug key.
            val hasReleaseSigning = keystorePropertiesFile.exists() &&
                keystoreProperties["keyAlias"] != null &&
                keystoreProperties["keyPassword"] != null &&
                keystoreProperties["storeFile"] != null &&
                keystoreProperties["storePassword"] != null

            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Real on-device Gemini Nano integration through ML Kit GenAI Prompt API.
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    // ML Kit GenAI beta4 may fail at runtime when coroutines resolve to 1.10.x or older.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}

flutter {
    source = "../.."
}
