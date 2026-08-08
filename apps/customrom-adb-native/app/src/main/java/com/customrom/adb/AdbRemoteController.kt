package com.customrom.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

sealed class RemoteConnectionState {
    data object Searching : RemoteConnectionState()
    data class Connecting(val endpoint: String) : RemoteConnectionState()
    data class Connected(val host: String, val port: Int, val strategy: String) : RemoteConnectionState()
    data class WaitingNetwork(val reason: String) : RemoteConnectionState()
    data class NeedsPairing(val reason: String) : RemoteConnectionState()
    data class Error(val reason: String) : RemoteConnectionState()
}

data class RemoteShellOutcome(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val transportError: Throwable? = null
)

class AdbRemoteController(
    private val context: Context,
    private val onState: (RemoteConnectionState) -> Unit
) {
    private val prefs = context.getSharedPreferences("customrom_adb", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val timeoutScheduler = Executors.newSingleThreadScheduledExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var kadb: Kadb? = null
    @Volatile private var currentHost = ""
    @Volatile private var currentPort = 0
    @Volatile private var reconnecting = false

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun start() {
        currentHost = prefs.getString("host", "") ?: ""
        currentPort = prefs.getInt("port", 5555)
        startMdnsDiscovery()
        autoReconnect(force = false)
    }

    fun close() {
        stopMdnsDiscovery()
        runCatching { kadb?.close() }
        executor.shutdownNow()
        timeoutScheduler.shutdownNow()
    }

    fun isConnected(): Boolean = runCatching { kadb?.connectionCheck() == true }.getOrDefault(false)

    fun endpointLabel(): String = if (currentHost.isBlank()) "endpoint não confirmado" else "$currentHost:$currentPort"

    fun savedHost(): String = prefs.getString("host", "") ?: ""
    fun savedPort(): Int = prefs.getInt("port", 5555)

    fun connect(host: String, port: Int, strategy: String = "manual", silent: Boolean = false) {
        if (host.isBlank() || port <= 0 || reconnecting) return
        reconnecting = true
        emit(RemoteConnectionState.Connecting("$host:$port"))
        executor.execute {
            try {
                val candidate = Kadb.tryConnection(host, port)
                    ?: throw IllegalStateException("ADB não respondeu como dispositivo autenticado")
                runCatching { kadb?.close() }
                kadb = candidate
                currentHost = host
                currentPort = port
                prefs.edit().putString("host", host).putInt("port", port).putString("strategy", strategy).apply()
                reconnecting = false
                emit(RemoteConnectionState.Connected(host, port, strategy))
            } catch (t: Throwable) {
                reconnecting = false
                if (!silent) emit(RemoteConnectionState.Error(shortError(t)))
            }
        }
    }

    fun autoReconnect(force: Boolean = false) {
        if (reconnecting) return
        if (!force && isConnected()) {
            emit(RemoteConnectionState.Connected(currentHost, currentPort, prefs.getString("strategy", "ativa") ?: "ativa"))
            return
        }
        val host = savedHost().trim()
        val savedPort = savedPort()
        if (host.isEmpty()) {
            emit(RemoteConnectionState.Searching)
            restartMdns()
            return
        }
        reconnecting = true
        emit(RemoteConnectionState.Connecting("reconexão automática"))
        executor.execute {
            var success: Triple<Kadb, Int, String>? = null
            linkedSetOf(5555, savedPort).forEach { port ->
                if (success == null) {
                    val candidate = runCatching { Kadb.tryConnection(host, port) }.getOrNull()
                    if (candidate != null) success = Triple(candidate, port, if (port == 5555) "tcp-5555" else "último endpoint")
                }
            }
            reconnecting = false
            if (success != null) {
                runCatching { kadb?.close() }
                kadb = success!!.first
                currentHost = host
                currentPort = success!!.second
                prefs.edit().putInt("port", currentPort).putString("strategy", success!!.third).apply()
                emit(RemoteConnectionState.Connected(host, currentPort, success!!.third))
            } else {
                emit(RemoteConnectionState.WaitingNetwork("endpoint salvo indisponível; procurando mDNS"))
                restartMdns()
            }
        }
    }

    fun pair(host: String, port: Int, code: String, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                runBlocking { Kadb.pair(host, port, code, "CUSTOMROM ADB") }
                prefs.edit().putString("host", host).putBoolean("paired", true).apply()
                mainHandler.post {
                    callback(true, "Pareamento concluído")
                    emit(RemoteConnectionState.Searching)
                    restartMdns()
                }
            } catch (t: Throwable) {
                mainHandler.post {
                    callback(false, shortError(t))
                    emit(RemoteConnectionState.NeedsPairing(shortError(t)))
                }
            }
        }
    }

    fun execute(
        command: String,
        timeoutMs: Long = 45_000L,
        callback: (RemoteShellOutcome) -> Unit
    ): Future<*> {
        val started = System.currentTimeMillis()
        val completed = AtomicBoolean(false)
        var task: Future<*>? = null

        task = executor.submit {
            val connection = kadb
            if (connection == null) {
                if (completed.compareAndSet(false, true)) {
                    val result = RemoteShellOutcome(
                        "",
                        "",
                        -1,
                        System.currentTimeMillis() - started,
                        IllegalStateException("TayTech não conectada")
                    )
                    mainHandler.post { callback(result) }
                    autoReconnect(force = true)
                }
                return@submit
            }
            try {
                val response = connection.shell(command)
                if (completed.compareAndSet(false, true)) {
                    val result = RemoteShellOutcome(
                        stdout = response.output,
                        stderr = response.errorOutput,
                        exitCode = response.exitCode,
                        durationMs = System.currentTimeMillis() - started
                    )
                    mainHandler.post { callback(result) }
                }
            } catch (t: Throwable) {
                if (completed.compareAndSet(false, true)) {
                    runCatching { connection.resetConnection() }
                    val result = RemoteShellOutcome("", "", -1, System.currentTimeMillis() - started, t)
                    mainHandler.post {
                        callback(result)
                        emit(RemoteConnectionState.WaitingNetwork("sessão caiu; reconectando"))
                        autoReconnect(force = true)
                    }
                }
            }
        }

        timeoutScheduler.schedule({
            if (completed.compareAndSet(false, true)) {
                task?.cancel(true)
                runCatching { kadb?.resetConnection() }
                val duration = System.currentTimeMillis() - started
                val error = TimeoutException("TIMEOUT: comando excedeu ${timeoutMs / 1000}s")
                val result = RemoteShellOutcome("", "", -1, duration, error)
                mainHandler.post {
                    callback(result)
                    emit(RemoteConnectionState.WaitingNetwork("comando excedeu o tempo limite; recuperando conexão"))
                    autoReconnect(force = true)
                }
            }
        }, timeoutMs.coerceAtLeast(1_000L), TimeUnit.MILLISECONDS)

        return task
    }

    fun cancel(task: Future<*>?) {
        task?.cancel(true)
        runCatching { kadb?.resetConnection() }
        emit(RemoteConnectionState.WaitingNetwork("operação interrompida; recuperando conexão"))
        autoReconnect(force = true)
    }

    fun restartMdns() {
        stopMdnsDiscovery()
        startMdnsDiscovery()
    }

    private fun startMdnsDiscovery() {
        if (discoveryListener != null) return
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager = manager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = emit(RemoteConnectionState.Searching)

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains("_adb-tls-connect")) return
                @Suppress("DEPRECATION")
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        val port = resolved.port
                        prefs.edit().putString("host", host).putInt("port", port).apply()
                        currentHost = host
                        currentPort = port
                        if (!isConnected() && !reconnecting) connect(host, port, "mDNS", silent = true)
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (!isConnected()) emit(RemoteConnectionState.WaitingNetwork("ADB mDNS saiu da rede"))
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
                discoveryListener = null
                emit(RemoteConnectionState.Error("falha ao iniciar descoberta mDNS ($errorCode)"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
                discoveryListener = null
            }
        }
        discoveryListener = listener
        runCatching {
            manager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            discoveryListener = null
            emit(RemoteConnectionState.Error(shortError(it)))
        }
    }

    private fun stopMdnsDiscovery() {
        val manager = nsdManager ?: return
        val listener = discoveryListener ?: return
        runCatching { manager.stopServiceDiscovery(listener) }
        discoveryListener = null
    }

    private fun emit(state: RemoteConnectionState) {
        if (Looper.myLooper() == Looper.getMainLooper()) onState(state) else mainHandler.post { onState(state) }
    }

    private fun shortError(t: Throwable): String = t.message?.take(180) ?: t::class.java.simpleName
}
