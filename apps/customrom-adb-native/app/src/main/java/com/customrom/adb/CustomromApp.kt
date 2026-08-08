package com.customrom.adb

import android.app.Application
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import okio.Path.Companion.toPath
import java.io.File

class CustomromApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // O pareamento ADB depende da identidade criptográfica do host.
        // Kadb usa memória por padrão; portanto configuramos armazenamento persistente
        // antes de qualquer conexão/pareamento para que reiniciar o app não gere outra chave.
        val keyFile = File(filesDir, "adb/adb_private_key.pem")
        KadbCert.configure(
            store = OkioFilePrivateKeyStore(keyFile.absolutePath.toPath())
        )
        KadbCert.ensureReady()
    }
}
