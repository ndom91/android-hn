plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.hn2"
    // Compose 1.13 (material3 1.5) needs API 37.1 and AGP 9.1+.
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "app.hn2"
        minSdk = 26
        targetSdk = 35
        // CI passes its run number so every build is a newer version.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        // The latest released version. CI bumps the patch for each release; raise it here for a new minor.
        versionName = "0.1.31"
    }

    signingConfigs {
        getByName("debug") {
            // CI signs every build with the same key so new APKs install over old ones.
            System.getenv("HN2_DEBUG_KEYSTORE")?.let { storeFile = file(it) }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// AGP 9 compiles Kotlin itself; the compose and serialization plugins pick the Kotlin version.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.glance.appwidget)
    implementation(libs.work.runtime.ktx)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
