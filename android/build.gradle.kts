// AGP 9 built-in Kotlin: KGP version is driven by this classpath (no kotlin-android plugin).
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        // Via Maven Central (Gradle Portal is unreachable here).
        classpath("app.cash.paparazzi:paparazzi-gradle-plugin:${libs.versions.paparazzi.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
