package com.customrom.adb

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("customrom_adb", Context.MODE_PRIVATE) }

    private var kadb: Kadb? = null
    private var currentHost: String = ""
    private var currentPort: Int = 0
    private var reconnecting = false
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private lateinit var statusView: TextView
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var pairingPortInput: EditText
    private lateinit var pairingCodeInput: EditText
    private lateinit var commandInput: EditText
    private lateinit var outputView: TextView
    private lateinit var recipeSpinner: Spinner
    private lateinit var sessionView: TextView

    private val recipes = mutableListOf<Recipe>()
    private var session: Session? = null

    data class Recipe(val id: String, val name: String, val risk: String, val command: String, val output: String)

    data class Execution(
        val at: Long,
        val title: String,
        val command: String,
        val output: String,
        val error: String,
        val exitCode: Int,
        val risk: String
    )

    data class Session(
        val id: String,
        val startedAt: Long,
        val directory: File,
        val executions: MutableList<Execution> = mutableListOf(),
        var reconnectCount: Int = 0,
        var transportErrors: Int = 0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadRecipes()
        startNewSession()
        setContentView(buildUi())
        restoreTarget()
        startMdnsDiscovery()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean("autoReconnect", true)) autoReconnect()
    }

    override fun onDestroy() {
        stopMdnsDiscovery()
        runCatching { kadb?.close() }
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "CUSTOMROM ADB"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "Cockpit Android / TayTech"
            textSize = 14f
        })
        statusView = TextView(this).apply {
            text = "● Desconectado"
            textSize = 18f
            setPadding(0, dp(14), 0, dp(10))
        }
        root.addView(statusView)

        root.addView(sectionTitle("Dispositivo e conexão"))
        hostInput = input("IP da TayTech", false)
        portInput = input("Porta de conexão", true)
        pairingPortInput = input("Porta de pareamento", true)
        pairingCodeInput = input("Código de pareamento", true)
        root.addView(hostInput)
        root.addView(portInput)

        root.addView(row(
            button("Conectar") { connectManual() },
            button("Reconectar") { autoReconnect(force = true) },
            button("Descobrir") { restartMdns() }
        ))

        root.addView(pairingPortInput)
        root.addView(pairingCodeInput)
        root.addView(row(
            button("Parear") { pairDevice() },
            button("Usar :5555") {
                val host = hostInput.text.toString().trim()
                if (host.isNotEmpty()) {
                    portInput.setText("5555")
                    connect(host, 5555, "tcp-5555")
                }
            }
        ))

        root.addView(sectionTitle("Terminal"))
        commandInput = EditText(this).apply {
            hint = "Cole um comando ou bloco inteiro"
            minLines = 5
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText("getprop ro.product.model")
        }
        root.addView(commandInput)
        root.addView(row(
            button("Executar") { executeFreeCommand() },
            button("Limpar saída") { outputView.text = "" },
            button("Copiar") { copyOutput() }
        ))

        outputView = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, dp(8), 0, dp(10))
        }
        root.addView(outputView)

        root.addView(sectionTitle("Diagnóstico CUSTOMROM"))
        recipeSpinner = Spinner(this)
        recipeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, recipes.map { "${it.name} · ${it.risk}" })
        root.addView(recipeSpinner)
        root.addView(row(
            button("Executar receita") { executeSelectedRecipe() },
            button("Snapshot completo") { executeRecipe(recipes.first { it.id == "snapshot-completo" }) }
        ))

        root.addView(sectionTitle("Sessão e evidência"))
        sessionView = TextView(this).apply {
            text = sessionText()
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(sessionView)
        root.addView(row(
            button("Nova sessão") { startNewSession(); refreshSessionText() },
            button("Exportar ZIP") { exportSession(share = false) },
            button("Compartilhar") { exportSession(share = true) }
        ))

        root.addView(sectionTitle("Ações avançadas"))
        root.addView(row(
            button("Diagnóstico rede") { recipes.find { it.id == "rede-adb" }?.let(::executeRecipe) },
            button("Logcat curto") { recipes.find { it.id == "logcat-curto" }?.let(::executeRecipe) },
            button("Pacotes/serviços") { recipes.find { it.id == "pacotes-servicos" }?.let(::executeRecipe) }
        ))

        root.addView(TextView(this).apply {
            text = "Risco: VERDE = leitura · AMARELO = alteração reversível · VERMELHO = estrutural. Comandos livres são classificados antes de executar."
            textSize = 12f
            setPadding(0, dp(14), 0, 0)
        })

        return scroll
    }

    private fun loadRecipes() {
        val json = assets.open("recipes.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            recipes += Recipe(
                id = o.getString("id"),
                name = o.getString("name"),
                risk = o.getString("risk"),
                command = o.getString("command"),
                output = o.getString("output")
            )
        }
    }

    private fun restoreTarget() {
        hostInput.setText(prefs.getString("host", "") ?: "")
        portInput.setText(prefs.getInt("port", 5555).toString())
        pairingPortInput.setText("")
        pairingCodeInput.setText("")
    }

    private fun connectManual() {
        val host = hostInput.text.toString().trim()
        val port = portInput.text.toString().toIntOrNull()
        if (host.isEmpty() || port == null) {
            toast("Informe IP e porta")
            return
        }
        connect(host, port, "manual")
    }

    private fun connect(host: String, port: Int, strategy: String, silent: Boolean = false) {
        if (reconnecting) return
        reconnecting = true
        setStatus("◌ Conectando $host:$port…")
        executor.execute {
            try {
                val candidate = Kadb.tryConnection(host, port)
                    ?: throw IllegalStateException("ADB não respondeu como dispositivo autenticado")
                runCatching { kadb?.close() }
                kadb = candidate
                currentHost = host
                currentPort = port
                prefs.edit().putString("host", host).putInt("port", port).putString("strategy", strategy).apply()
                mainHandler.post {
                    hostInput.setText(host)
                    portInput.setText(port.toString())
                    setStatus("● Conectada · $host:$port · $strategy")
                    reconnecting = false
                }
            } catch (t: Throwable) {
                session?.transportErrors = (session?.transportErrors ?: 0) + 1
                mainHandler.post {
                    setStatus("○ Falha em $host:$port · ${shortError(t)}")
                    reconnecting = false
                    if (!silent) toast(shortError(t))
                }
            }
        }
    }

    private fun autoReconnect(force: Boolean = false) {
        if (reconnecting) return
        if (!force && kadb?.connectionCheck() == true) {
            setStatus("● Conectada · $currentHost:$currentPort")
            return
        }
        val host = (prefs.getString("host", "") ?: "").trim()
        val savedPort = prefs.getInt("port", 5555)
        if (host.isEmpty()) {
            setStatus("○ Sem alvo salvo · use Descobrir ou informe o IP")
            return
        }

        val candidates = linkedSetOf(5555, savedPort)
        reconnecting = true
        setStatus("◌ Reconectando automaticamente…")
        executor.execute {
            var success: Triple<Kadb, Int, String>? = null
            for (port in candidates) {
                val candidate = runCatching { Kadb.tryConnection(host, port) }.getOrNull()
                if (candidate != null) {
                    success = Triple(candidate, port, if (port == 5555) "tcp-5555" else "last-endpoint")
                    break
                }
            }
            mainHandler.post {
                reconnecting = false
                if (success != null) {
                    runCatching { kadb?.close() }
                    kadb = success!!.first
                    currentHost = host
                    currentPort = success!!.second
                    prefs.edit().putInt("port", currentPort).putString("strategy", success!!.third).apply()
                    session?.reconnectCount = (session?.reconnectCount ?: 0) + 1
                    hostInput.setText(host)
                    portInput.setText(currentPort.toString())
                    setStatus("● Reconectada · $host:$currentPort · ${success!!.third}")
                } else {
                    setStatus("◌ Endpoint salvo indisponível · procurando mDNS…")
                    restartMdns()
                }
            }
        }
    }

    private fun pairDevice() {
        val host = hostInput.text.toString().trim()
        val port = pairingPortInput.text.toString().toIntOrNull()
        val code = pairingCodeInput.text.toString().trim()
        if (host.isEmpty() || port == null || code.length < 6) {
            toast("Informe IP, porta de pareamento e código")
            return
        }
        setStatus("◌ Pareando…")
        executor.execute {
            try {
                runBlocking { Kadb.pair(host, port, code, "CUSTOMROM ADB") }
                prefs.edit().putString("host", host).putBoolean("paired", true).apply()
                mainHandler.post {
                    setStatus("● Pareamento concluído · procurando endpoint…")
                    restartMdns()
                }
            } catch (t: Throwable) {
                mainHandler.post { setStatus("○ Falha no pareamento · ${shortError(t)}") }
            }
        }
    }

    private fun executeFreeCommand() {
        val command = commandInput.text.toString().trim()
        if (command.isEmpty()) return
        val risk = classifyRisk(command)
        if (risk == "VERDE") {
            executeCommand("Terminal livre", command, risk, null)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Comando $risk")
                .setMessage(riskMessage(risk, command))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Executar") { _, _ -> executeCommand("Terminal livre", command, risk, null) }
                .show()
        }
    }

    private fun executeSelectedRecipe() {
        if (recipes.isEmpty()) return
        executeRecipe(recipes[recipeSpinner.selectedItemPosition.coerceIn(recipes.indices)])
    }

    private fun executeRecipe(recipe: Recipe) {
        executeCommand(recipe.name, recipe.command, recipe.risk, recipe.output)
    }

    private fun executeCommand(title: String, command: String, risk: String, outputFile: String?) {
        val connection = kadb
        if (connection == null) {
            toast("Conecte a TayTech primeiro")
            autoReconnect(force = true)
            return
        }
        outputView.text = "Executando: $title…"
        executor.execute {
            val started = System.currentTimeMillis()
            try {
                val response = connection.shell(command)
                val all = buildString {
                    if (response.output.isNotEmpty()) append(response.output)
                    if (response.errorOutput.isNotEmpty()) {
                        if (isNotEmpty() && !endsWith("\n")) append('\n')
                        append(response.errorOutput)
                    }
                }
                val exec = Execution(started, title, command, response.output, response.errorOutput, response.exitCode, risk)
                session?.executions?.add(exec)
                appendSessionTerminal(exec)
                if (outputFile != null) File(session!!.directory, outputFile).writeText(all, Charsets.UTF_8)
                mainHandler.post {
                    outputView.text = "[$risk] $title · exit=${response.exitCode}\n\n$all"
                    refreshSessionText()
                }
            } catch (t: Throwable) {
                session?.transportErrors = (session?.transportErrors ?: 0) + 1
                runCatching { connection.resetConnection() }
                val message = "ERRO DE TRANSPORTE/EXECUÇÃO: ${t.stackTraceToString()}"
                val exec = Execution(started, title, command, "", message, -1, risk)
                session?.executions?.add(exec)
                appendSessionTerminal(exec)
                mainHandler.post {
                    outputView.text = message
                    setStatus("◌ Sessão caiu · reconectando…")
                    refreshSessionText()
                    autoReconnect(force = true)
                }
            }
        }
    }

    private fun startNewSession() {
        val id = utcId()
        val dir = File(filesDir, "sessions/$id").apply { mkdirs() }
        session = Session(id, System.currentTimeMillis(), dir)
        writeSessionMeta()
    }

    private fun appendSessionTerminal(execution: Execution) {
        val s = session ?: return
        val file = File(s.directory, "terminal.txt")
        file.appendText(
            "\n=== ${iso(execution.at)} | ${execution.title} | ${execution.risk} ===\n" +
                "$ ${execution.command}\n" +
                execution.output +
                (if (execution.error.isNotBlank()) "\n[stderr/error]\n${execution.error}" else "") +
                "\n[exit=${execution.exitCode}]\n",
            Charsets.UTF_8
        )
        writeSessionMeta()
    }

    private fun writeSessionMeta() {
        val s = session ?: return
        val json = JSONObject().apply {
            put("schema", 1)
            put("sessionId", s.id)
            put("startedAt", iso(s.startedAt))
            put("target", "TayTech")
            put("host", currentHost.ifBlank { prefs.getString("host", "") ?: "" })
            put("port", if (currentPort > 0) currentPort else prefs.getInt("port", 5555))
            put("strategy", prefs.getString("strategy", "unknown"))
            put("reconnectCount", s.reconnectCount)
            put("transportErrors", s.transportErrors)
            put("executionCount", s.executions.size)
            put("executions", JSONArray().apply {
                s.executions.forEach { e ->
                    put(JSONObject().apply {
                        put("at", iso(e.at))
                        put("title", e.title)
                        put("risk", e.risk)
                        put("exitCode", e.exitCode)
                        put("command", e.command)
                    })
                }
            })
        }
        File(s.directory, "manifest.json").writeText(json.toString(2), Charsets.UTF_8)
        File(s.directory, "resumo.md").writeText(buildSummary(s), Charsets.UTF_8)
    }

    private fun buildSummary(s: Session): String = buildString {
        append("# Sessão CUSTOMROM ${s.id}\n\n")
        append("- Alvo: **TayTech**\n")
        append("- Endpoint: `${currentHost.ifBlank { prefs.getString("host", "") ?: "não informado" }}:${if (currentPort > 0) currentPort else prefs.getInt("port", 5555)}`\n")
        append("- Estratégia: `${prefs.getString("strategy", "unknown")}`\n")
        append("- Reconexões: ${s.reconnectCount}\n")
        append("- Erros de transporte: ${s.transportErrors}\n")
        append("- Execuções: ${s.executions.size}\n\n")
        append("## Execuções\n\n")
        s.executions.forEachIndexed { index, e ->
            append("${index + 1}. **${e.title}** · ${e.risk} · exit=${e.exitCode}\n")
        }
        append("\nOs arquivos brutos permanecem anexados no pacote da sessão.\n")
    }

    private fun exportSession(share: Boolean) {
        val s = session ?: return
        writeSessionMeta()
        val zipTemp = File(cacheDir, "CUSTOMROM_SESSION_${s.id}.zip")
        createZip(s.directory, zipTemp)
        executor.execute {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, zipTemp.name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CUSTOMROM")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Não foi possível criar destino no Downloads")
                contentResolver.openOutputStream(uri)?.use { out -> zipTemp.inputStream().use { it.copyTo(out) } }
                    ?: throw IllegalStateException("Não foi possível abrir destino")
                mainHandler.post {
                    if (share) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, "Compartilhar sessão CUSTOMROM"))
                    } else {
                        toast("Salvo em Downloads/CUSTOMROM/${zipTemp.name}")
                    }
                }
            } catch (t: Throwable) {
                mainHandler.post { toast("Falha ao exportar: ${shortError(t)}") }
            }
        }
    }

    private fun createZip(source: File, target: File) {
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            source.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(source).invariantSeparatorsPath
                val entry = ZipEntry(relative)
                zip.putNextEntry(entry)
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val checksums = source.walkTopDown().filter { it.isFile }.joinToString("\n") { file ->
                "${sha256(file)}  ${file.relativeTo(source).invariantSeparatorsPath}"
            } + "\n"
            zip.putNextEntry(ZipEntry("checksums.sha256"))
            zip.write(checksums.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun startMdnsDiscovery() {
        if (discoveryListener != null) return
        val manager = getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager = manager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                setStatus("◌ Procurando ADB na rede…")
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains("_adb-tls-connect")) return
                @Suppress("DEPRECATION")
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        val port = resolved.port
                        mainHandler.post {
                            hostInput.setText(host)
                            portInput.setText(port.toString())
                            prefs.edit().putString("host", host).putInt("port", port).apply()
                            if (kadb?.connectionCheck() != true && !reconnecting) connect(host, port, "mdns", silent = true)
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (kadb?.connectionCheck() != true) setStatus("○ ADB mDNS saiu da rede")
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
                discoveryListener = null
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
                discoveryListener = null
            }
        }
        discoveryListener = listener
        runCatching { manager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { discoveryListener = null }
    }

    private fun stopMdnsDiscovery() {
        val manager = nsdManager ?: return
        val listener = discoveryListener ?: return
        runCatching { manager.stopServiceDiscovery(listener) }
        discoveryListener = null
    }

    private fun restartMdns() {
        stopMdnsDiscovery()
        startMdnsDiscovery()
    }

    private fun classifyRisk(command: String): String {
        val c = command.lowercase(Locale.ROOT)
        val red = listOf("fastboot", " flash ", "erase ", "pm uninstall", "adb root", "remount", "dd if=", "mkfs", "reboot bootloader")
        if (red.any { c.contains(it) }) return "VERMELHO"
        val yellow = listOf("pm disable", "pm enable", "am force-stop", "settings put", "pm clear", "svc ", "setprop ", "reboot")
        if (yellow.any { c.contains(it) }) return "AMARELO"
        return "VERDE"
    }

    private fun riskMessage(risk: String, command: String): String = when (risk) {
        "AMARELO" -> "Este comando pode alterar estado/configuração de forma reversível. Verifique rollback e alvo antes de executar.\n\n$command"
        else -> "Este comando pode alterar estrutura, remover dados ou exigir recuperação. Execute somente com plano de recuperação e autorização consciente.\n\n$command"
    }

    private fun copyOutput() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CUSTOMROM output", outputView.text))
        toast("Saída copiada")
    }

    private fun sessionText(): String {
        val s = session ?: return "Sem sessão"
        return "Sessão ${s.id} · ${s.executions.size} execuções · ${s.reconnectCount} reconexões · ${s.transportErrors} erros de transporte"
    }

    private fun refreshSessionText() {
        sessionView.text = sessionText()
    }

    private fun setStatus(text: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) statusView.text = text else mainHandler.post { statusView.text = text }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, dp(16), 0, dp(6))
    }

    private fun input(hintText: String, numeric: Boolean): EditText = EditText(this).apply {
        hint = hintText
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { action() }
    }

    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEach { v -> addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun shortError(t: Throwable): String = t.message?.take(160) ?: t::class.java.simpleName

    private fun utcId(): String {
        val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun iso(ms: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(ms))
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
