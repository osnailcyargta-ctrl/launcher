plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pixel.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pixel.launcher"
        minSdk = 24
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("BUILD_NUMBER") ?: "0"}"

        resourceConfigurations += listOf("en", "in")
    }

    signingConfigs {
        create("shared") {
            // Checked-in key so every CI build produces an APK that installs *over*
            // the previous one. Override with the KEYSTORE_* env vars for a private key.
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "../keystore/pixel-launcher.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "pixellauncher"
            keyAlias = System.getenv("KEY_ALIAS") ?: "pixel"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "pixellauncher"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "**/*.kotlin_metadata",
            )
        }
    }

    androidResources {
        // Keep the APK small: the launcher only ships vector/pixel assets.
        noCompress += listOf("ttf")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
}
