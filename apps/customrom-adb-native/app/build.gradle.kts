plugins {
    id("com.android.application")
}

android {
    namespace = "com.customrom.adb"
    // Kadb 2.1.1 é a última linha upstream confirmada por fonte com compileSdk estável 36.
    // Evitamos depender da plataforma Android 17/API 37 preview apenas para compilar o cliente ADB.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.customrom.adb"
        // O fluxo de exportação usa MediaStore.Downloads (API 29+).
        // O alvo operacional atual é o S23; manter minSdk 29 evita um caminho legado
        // sem utilidade para este projeto e deixa a exportação determinística.
        minSdk = 29
        // Mantemos target 35 deliberadamente. Elevar target muda comportamento de runtime
        // e continua sendo uma decisão separada da versão usada apenas para compilação.
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
    implementation("com.flyfishxu:kadb:2.1.1")
    // MainActivity chama Kadb.pair (suspend) através de runBlocking.
    // Kadb 2.1.1 usa Coroutines 1.10.2; declarar a mesma versão diretamente
    // torna a API kotlinx.coroutines disponível no classpath de compilação do app.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
