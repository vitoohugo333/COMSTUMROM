plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.customrom.adb"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.customrom.adb"
        // O fluxo de exportação usa MediaStore.Downloads (API 29+).
        // O alvo operacional atual é o S23; manter minSdk 29 evita um caminho legado
        // sem utilidade para este projeto e deixa a exportação determinística.
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
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
