buildscript {
    dependencies {
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.9.3.0")
    }
}

plugins {
    kotlin("jvm") version "1.8.21"
    id("com.android.library") version "7.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.8.21" apply false
    id("de.mannodermaus.android-junit5") version "1.9.3.0" apply false
}

allprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}