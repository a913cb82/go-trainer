import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Paparazzi screenshot tests (applied by id; plugin jar comes from buildscript classpath).
apply(plugin = "app.cash.paparazzi")

android {
    namespace = "com.gotrainer.nine"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gotrainer.nine"
        minSdk = 34
        targetSdk = 36
        // Personal sideload: unique versionCode per build keeps HyperOS reinstalls
        // smooth (same-version reinstalls stall in MIUI verification).
        versionCode = (System.currentTimeMillis() / 1000).toInt()
        versionName = "1.0.0"
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
    // AGP 9 applies KGP itself (built-in Kotlin), lazily — so configure after evaluation.
    afterEvaluate {
        configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // Extract .so files to nativeLibraryDir at install: the engine runs
        // libkatago.so from app storage via linker64, so it must be a real file.
        jniLibs {
            useLegacyPackaging = true
        }
    }
    testOptions {
        // JVM unit tests: android.util.Log etc. become no-ops instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation("app.cash.paparazzi:paparazzi:2.0.0-alpha05")
}
