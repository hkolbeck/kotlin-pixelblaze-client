apply {
    plugin("org.jetbrains.kotlin.jvm")
}

plugins {
    java
}

allprojects {
    tasks.test {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

val ktorVersion: String by project
dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation(kotlin("stdlib-jdk8"))

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.9.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}