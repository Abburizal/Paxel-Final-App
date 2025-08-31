buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        classpath("com.google.ar.sceneform:plugin:1.17.1")
    }
}

plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    id("androidx.room") version "2.6.1" apply false
    // Plugin ktlint untuk code style Kotlin
    // Hanya tersedia untuk subproject, tidak diterapkan di root
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1" apply false
}