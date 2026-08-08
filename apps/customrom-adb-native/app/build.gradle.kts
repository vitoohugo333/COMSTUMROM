plugins {
    id("com.android.application")
}

android {
    namespace = "com.customrom.adb"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.customrom.adb"
        // O fluxo de exportação usa MediaStore.Downloads (API 29+).
        // O alvo operacional atual é o S23; manter minSdk 29 evita um caminho legado
        // sem utilidade para este projeto e deixa a exportação determinística.
        minSdk = 29
        // Mantemos target 35 deliberadamente. compileSdk 37 é exigido pela camada ADB,
        // mas elevar target muda comportamento de runtime e será uma decisão separada.
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
}

dependencies {
    implementation("com.flyfishxu:kadb:2.1.3")
}
