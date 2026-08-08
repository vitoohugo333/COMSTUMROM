plugins {
    id("com.android.application")
}

android {
    namespace = "com.customrom.agent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.customrom.agent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug { }
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
