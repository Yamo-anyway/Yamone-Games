plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.yamone.games"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yamone.games"
        minSdk = 26
        targetSdk = 36
        // Final dev55 validation: text-only bottom tabs + Home-style ranking list + themed system navigation.
        versionCode = 61
        versionName = "1.1.0-dev55"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":games:arcadecore"))
    implementation(project(":games:sudoku"))
    implementation(project(":games:icejump"))
    implementation(project(":games:fishmunch"))
    implementation(project(":games:snowrush"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.4.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
}
