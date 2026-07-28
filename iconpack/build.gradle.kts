plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * A standalone ADW/Nova icon pack.
 *
 * It is deliberately dependency-free: the whole APK is a pile of PNGs, an
 * appfilter, and one screen of instructions built in code. That keeps it small
 * and means it installs on anything back to Android 5.
 */
android {
    namespace = "com.pixel.launcher.iconpack"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pixel.launcher.iconpack"
        minSdk = 21
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("BUILD_NUMBER") ?: "0"}"
    }

    signingConfigs {
        create("shared") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "../keystore/pixel-launcher.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "pixellauncher"
            keyAlias = System.getenv("KEY_ALIAS") ?: "pixel"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "pixellauncher"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            // Resource shrinking would strip the drawables: nothing references
            // them from code, they are looked up by name at runtime.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress += listOf("png")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}
