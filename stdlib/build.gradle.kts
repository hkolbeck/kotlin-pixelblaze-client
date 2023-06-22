apply {
    plugin("org.jetbrains.kotlin.jvm")
}

plugins {
    java
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}

val implementation by configurations
dependencies {
    implementation(project(":core"))

    implementation(kotlin("stdlib-jdk8"))

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.9.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}