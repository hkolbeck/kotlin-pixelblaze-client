pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

rootProject.name = "kotlin-pixelblaze-client"
include(":core", ":android", ":stdlib", ":sensor")
