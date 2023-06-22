plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("de.mannodermaus.android-junit5")
}

android {
    compileSdk = 32

    defaultConfig {
        minSdk = 24
        targetSdk = 32

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    dependencies {
        implementation(project(":core"))
        // implementation("com.squareup:gifencoder:0.10.1")

        androidTestImplementation(kotlin("test"))
        androidTestImplementation(platform("org.junit:junit-bom:5.9.3"))
        androidTestImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
        androidTestImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
        androidTestRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
        androidTestImplementation("androidx.test:runner:1.5.2")
    }
}
