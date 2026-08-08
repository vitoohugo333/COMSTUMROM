plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.customrom.adb"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.customrom.adb"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.flyfishxu:kadb:2.1.3")
}
