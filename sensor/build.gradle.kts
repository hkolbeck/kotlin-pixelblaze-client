apply {
    plugin("org.jetbrains.kotlin.jvm")
}

plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.wendykierp:JTransforms:3.1")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.9.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}