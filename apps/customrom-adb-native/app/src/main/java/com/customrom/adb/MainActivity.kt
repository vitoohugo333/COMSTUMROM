package com.customrom.adb

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
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
    private lateinit var homeStatusView: TextView
    private lateinit var endpointView: TextView
    private lateinit var commandInput: EditText
    private lateinit var commandRiskView: TextView
    private lateinit var outputView: TextView
    private lateinit var recipeSpinner: Spinner
    private lateinit var sessionView: TextView
    private lateinit var homeSessionView: TextView
    private lateinit var timelineHost: LinearLayout
    private lateinit var contentHost: FrameLayout

    private val navButtons = linkedMapOf<String, TextView>()
    private val screens = linkedMapOf<String, View>()
    private var currentScreen = "home"

    private val recipes = mutableListOf<Recipe>()
    private var session: Session? = null

    private val bg = Color.rgb(8, 12, 18)
    private val surface = Color.rgb(16, 23, 33)
    private val surface2 = Color.rgb(22, 31, 44)
    private val surface3 = Color.rgb(28, 39, 55)
    private val stroke = Color.rgb(41, 55, 73)
    private val textPrimary = Color.rgb(245, 248, 252)
    private val textSecondary = Color.rgb(150, 166, 187)
    private val accent = Color.rgb(116, 92, 255)
    private val accentSoft = Color.rgb(39, 32, 82)
    private val success = Color.rgb(56, 211, 159)
    private val warning = Color.rgb(247, 185, 85)
    private val danger = Color.rgb(255, 107, 107)

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
        window.statusBarColor = bg
        window.navigationBarColor = bg
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setOnApplyWindowInsetsListener { view, insets ->
                val left: Int
                val top: Int
                val right: Int
                val bottom: Int
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    left = bars.left
                    top = bars.top
                    right = bars.right
                    bottom = bars.bottom
                } else {
                    @Suppress("DEPRECATION")
                    left = insets.systemWindowInsetLeft
                    @Suppress("DEPRECATION")
                    top = insets.systemWindowInsetTop
                    @Suppress("DEPRECATION")
                    right = insets.systemWindowInsetRight
                    @Suppress("DEPRECATION")
                    bottom = insets.systemWindowInsetBottom
                }
                view.setPadding(left, top, right, bottom)
                insets
            }
        }
        root.addView(buildTopBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74)))

        contentHost = FrameLayout(this).apply { setBackgroundColor(bg) }
        screens["home"] = buildHomeScreen()
        screens["terminal"] = buildTerminalScreen()
        screens["diagnostics"] = buildDiagnosticsScreen()
        screens["sessions"] = buildSessionsScreen()
        screens["more"] = buildMoreScreen()

        val isWide = resources.configuration.screenWidthDp >= 700
        if (isWide) {
            val body = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(bg)
            }
            body.addView(buildNavigation(vertical = true), LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.MATCH_PARENT))
            body.addView(contentHost, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            root.addView(contentHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(buildNavigation(vertical = false), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74)))
        }

        showSection("home")
        return root
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(16), dp(8))
            setBackgroundColor(bg)
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("CUSTOMROM", 18f, textPrimary, true))
        brand.addView(text("ADB ENGINEERING CONSOLE", 9f, textSecondary, true).apply { letterSpacing = 0.14f })
        bar.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        statusView = text("● Procurando ADB", 12f, warning, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(surface2, 16, stroke)
            setOnClickListener { showConnectionDialog() }
        }
        bar.addView(statusView, LinearLayout.LayoutParams(dp(190), dp(40)))
        return bar
    }

    private fun buildNavigation(vertical: Boolean): View {
        val items = listOf(
            Triple("home", "⌂", "Central"),
            Triple("terminal", "⌘", "Terminal"),
            Triple("diagnostics", "◇", "Diagnóstico"),
            Triple("sessions", "▤", "Sessões"),
            Triple("more", "•••", "Mais")
        )
        val nav = LinearLayout(this).apply {
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (vertical) Gravity.TOP else Gravity.CENTER_VERTICAL
            setPadding(if (vertical) dp(12) else dp(8), if (vertical) dp(16) else dp(8), if (vertical) dp(12) else dp(8), dp(8))
            setBackgroundColor(surface)
        }
        items.forEach { (key, icon, label) ->
            val button = text(if (vertical) "$icon   $label" else "$icon\n$label", if (vertical) 14f else 11f, textSecondary, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(9), dp(8), dp(9))
                setOnClickListener { showSection(key) }
            }
            navButtons[key] = button
            if (vertical) {
                nav.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { bottomMargin = dp(8) })
            } else {
                nav.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }
        }
        return nav
    }

    private fun showSection(key: String) {
        currentScreen = key
        contentHost.removeAllViews()
        contentHost.addView(screens.getValue(key), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        navButtons.forEach { (name, button) ->
            val active = name == key
            button.setTextColor(if (active) textPrimary else textSecondary)
            button.background = if (active) rounded(accentSoft, 16, accent) else rounded(Color.TRANSPARENT, 16)
        }
    }

    private fun buildHomeScreen(): View {
        val root = verticalScroll()
        root.addView(eyebrow("DISPOSITIVO PRINCIPAL"))

        val hero = card().apply { setPadding(dp(20), dp(20), dp(20), dp(20)) }
        val firstLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val identity = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        identity.addView(text("TayTech", 26f, textPrimary, true))
        identity.addView(text("Multimídia · alvo confiável", 13f, textSecondary, false))
        firstLine.addView(identity, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        firstLine.addView(pill("ALVO ATIVO", success, Color.rgb(18, 57, 47)))
        hero.addView(firstLine)

        homeStatusView = text("Procurando ADB na rede…", 15f, warning, true).apply { setPadding(0, dp(18), 0, dp(6)) }
        endpointView = text("Endpoint ainda não confirmado", 13f, textSecondary, false)
        hero.addView(homeStatusView)
        hero.addView(endpointView)

        val heroActions = horizontalRow(dp(12))
        heroActions.addView(actionButton("Gerenciar conexão", false) { showConnectionDialog() }, weight())
        heroActions.addView(actionButton("Reconectar agora", true) { autoReconnect(force = true) }, weight())
        hero.addView(heroActions, margins(top = 18))
        root.addView(hero, margins(top = 8))

        root.addView(sectionHeader("Ações rápidas", "Fluxos que usamos de verdade"), margins(top = 24))
        val quick = horizontalRow(dp(12))
        quick.addView(featureCard("⌘", "Abrir terminal", "Shell completo com blocos, histórico e risco") { showSection("terminal") }, weight())
        quick.addView(featureCard("◇", "Diagnóstico completo", "Coleta uma fotografia técnica da central") { recipes.find { it.id == "snapshot-completo" }?.let(::executeRecipe) }, weight())
        root.addView(quick, margins(top = 10))

        root.addView(sectionHeader("Sessão atual", "Tudo que acontecer fica rastreável"), margins(top = 24))
        val sessionCard = card()
        homeSessionView = text(sessionText(), 14f, textPrimary, true)
        sessionCard.addView(homeSessionView)
        sessionCard.addView(text("Comandos, saídas, reconexões e arquivos são agregados automaticamente ao Evidence Pack.", 12f, textSecondary, false).apply { setPadding(0, dp(8), 0, 0) })
        val sessionActions = horizontalRow(dp(10))
        sessionActions.addView(actionButton("Ver sessão", false) { showSection("sessions") }, weight())
        sessionActions.addView(actionButton("Compartilhar", true) { exportSession(share = true) }, weight())
        sessionCard.addView(sessionActions, margins(top = 16))
        root.addView(sessionCard, margins(top = 10))

        root.addView(sectionHeader("Cockpit", "O que esta ferramenta concentra"), margins(top = 24))
        root.addView(infoStrip("Reconexão inteligente", "5555 → último endpoint → mDNS", "AUTO"), margins(top = 10))
        root.addView(infoStrip("Segurança operacional", "Classificação antes de alterar o alvo", "3 NÍVEIS"), margins(top = 8))
        root.addView(infoStrip("Evidência portátil", "ZIP estruturado pronto para compartilhar", "SHA-256"), margins(top = 8))
        root.addView(space(28))
        return root.parent as ScrollView
    }

    private fun buildTerminalScreen(): View {
        val root = verticalScroll()
        root.addView(eyebrow("TERMINAL WORKSPACE"))
        root.addView(text("Shell remoto sem atrito", 27f, textPrimary, true), margins(top = 4))
        root.addView(text("Cole um comando, várias linhas ou uma rotina inteira. A sessão preserva a saída e classifica risco antes de executar.", 13f, textSecondary, false), margins(top = 6))

        val editorCard = card().apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }
        val editorHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        editorHeader.addView(text("COMANDO", 10f, textSecondary, true).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        commandRiskView = pill("VERDE", success, Color.rgb(18, 57, 47))
        editorHeader.addView(commandRiskView)
        editorCard.addView(editorHeader)

        commandInput = EditText(this).apply {
            setText("getprop ro.product.model")
            hint = "Digite ou cole um bloco de shell…"
            setHintTextColor(Color.rgb(97, 112, 132))
            setTextColor(textPrimary)
            textSize = 14f
            minLines = 7
            gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(7, 11, 17), 16, stroke)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshCommandRisk()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        editorCard.addView(commandInput, margins(top = 12, height = 190))

        val actions = horizontalRow(dp(10))
        actions.addView(actionButton("Executar", true) { executeFreeCommand() }, weight())
        actions.addView(actionButton("Copiar saída", false) { copyOutput() }, weight())
        actions.addView(iconButton("×") { outputView.text = "" }, LinearLayout.LayoutParams(dp(48), dp(48)))
        editorCard.addView(actions, margins(top = 12))
        root.addView(editorCard, margins(top = 18))

        root.addView(sectionHeader("Saída", "Resposta completa do dispositivo"), margins(top = 24))
        val outputCard = card(Color.rgb(7, 11, 17)).apply { setPadding(dp(16), dp(14), dp(16), dp(16)) }
        val consoleTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        consoleTop.addView(text("CONSOLE", 10f, textSecondary, true).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        consoleTop.addView(pill("LIVE", accent, Color.rgb(36, 29, 78)))
        outputCard.addView(consoleTop)
        outputView = text("A saída aparecerá aqui.\n\nA conexão, o comando e o resultado também ficam vinculados à sessão atual.", 12f, Color.rgb(196, 208, 222), false).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(14), 0, 0)
        }
        outputCard.addView(outputView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)))
        root.addView(outputCard, margins(top = 10))
        root.addView(space(28))
        return root.parent as ScrollView
    }

    private fun buildDiagnosticsScreen(): View {
        val root = verticalScroll()
        root.addView(eyebrow("DIAGNÓSTICO CUSTOMROM"))
        root.addView(text("Diagnosticar sem decorar comandos", 27f, textPrimary, true), margins(top = 4))
        root.addView(text("Receitas versionadas executam coletas reproduzíveis e colocam a evidência dentro da sessão atual.", 13f, textSecondary, false), margins(top = 6))

        val snapshot = card(accentSoft).apply { setPadding(dp(18), dp(18), dp(18), dp(18)); background = rounded(accentSoft, 22, accent) }
        snapshot.addView(text("Fotografia completa da TayTech", 19f, textPrimary, true))
        snapshot.addView(text("Hardware, memória, processos, armazenamento, rede e sinais essenciais em uma única coleta.", 12f, Color.rgb(199, 193, 255), false).apply { setPadding(0, dp(7), 0, 0) })
        snapshot.addView(actionButton("Executar snapshot completo", true) { recipes.find { it.id == "snapshot-completo" }?.let(::executeRecipe) }, margins(top = 16))
        root.addView(snapshot, margins(top = 18))

        val legend = horizontalRow(dp(8))
        legend.addView(pill("VERDE · leitura", success, Color.rgb(18, 57, 47)))
        legend.addView(pill("AMARELO · reversível", warning, Color.rgb(65, 48, 20)))
        legend.addView(pill("VERMELHO · estrutural", danger, Color.rgb(67, 28, 32)))
        root.addView(legend, margins(top = 18))

        recipeSpinner = Spinner(this).apply {
            visibility = View.GONE
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, recipes.map { "${it.name} · ${it.risk}" })
        }
        root.addView(recipeSpinner, LinearLayout.LayoutParams(1, 1))

        root.addView(sectionHeader("Biblioteca de receitas", "Toque em executar para coletar"), margins(top = 22))
        recipes.filter { it.id != "snapshot-completo" }.forEach { recipe -> root.addView(recipeCard(recipe), margins(top = 10)) }
        root.addView(space(28))
        return root.parent as ScrollView
    }

    private fun buildSessionsScreen(): View {
        val root = verticalScroll()
        root.addView(eyebrow("EVIDENCE WORKSPACE"))
        root.addView(text("Sessão e evidências", 27f, textPrimary, true), margins(top = 4))
        root.addView(text("Uma investigação deixa de ser um monte de prints: vira uma linha do tempo exportável e verificável.", 13f, textSecondary, false), margins(top = 6))

        val summary = card()
        sessionView = text(sessionText(), 15f, textPrimary, true)
        summary.addView(sessionView)
        summary.addView(text("O pacote exportado inclui resumo, manifesto, terminal, arquivos de diagnóstico e checksums.", 12f, textSecondary, false).apply { setPadding(0, dp(8), 0, 0) })
        val actions = horizontalRow(dp(10))
        actions.addView(actionButton("Nova sessão", false) { startNewSession(); refreshSessionText(); refreshSessionTimeline() }, weight())
        actions.addView(actionButton("Exportar ZIP", false) { exportSession(share = false) }, weight())
        actions.addView(actionButton("Compartilhar", true) { exportSession(share = true) }, weight())
        summary.addView(actions, margins(top = 16))
        root.addView(summary, margins(top = 18))

        root.addView(sectionHeader("Linha do tempo", "Execuções recentes desta sessão"), margins(top = 24))
        timelineHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(timelineHost, margins(top = 8))
        refreshSessionTimeline()
        root.addView(space(28))
        return root.parent as ScrollView
    }

    private fun buildMoreScreen(): View {
        val root = verticalScroll()
        root.addView(eyebrow("FERRAMENTAS E CONTEXTO"))
        root.addView(text("Mais poder, sem poluir o fluxo principal", 27f, textPrimary, true), margins(top = 4))
        root.addView(text("As ferramentas técnicas continuam disponíveis, mas ficam organizadas por intenção em vez de empilhadas na tela inicial.", 13f, textSecondary, false), margins(top = 6))

        root.addView(sectionHeader("Conexão", "Controle manual quando a automação não for suficiente"), margins(top = 24))
        root.addView(menuRow("◎", "Gerenciar conexão ADB", "IP, porta, 5555, descoberta e pareamento") { showConnectionDialog() }, margins(top = 10))
        root.addView(menuRow("↻", "Forçar reconexão", "Tenta 5555, endpoint salvo e mDNS") { autoReconnect(force = true) }, margins(top = 8))

        root.addView(sectionHeader("Ferramentas rápidas", "Atalhos técnicos preservados"), margins(top = 24))
        root.addView(menuRow("≋", "Diagnóstico de rede", "Interfaces, endereço e contexto ADB") { recipes.find { it.id == "rede-adb" }?.let(::executeRecipe) }, margins(top = 10))
        root.addView(menuRow("▤", "Logcat curto", "Captura controlada para comportamento anômalo") { recipes.find { it.id == "logcat-curto" }?.let(::executeRecipe) }, margins(top = 8))
        root.addView(menuRow("◫", "Pacotes e serviços", "Inventário para investigação de bloat e dependências") { recipes.find { it.id == "pacotes-servicos" }?.let(::executeRecipe) }, margins(top = 8))

        val safety = card(Color.rgb(17, 25, 34)).apply { background = rounded(Color.rgb(17, 25, 34), 22, stroke) }
        safety.addView(text("Camada de segurança ativa", 16f, textPrimary, true))
        safety.addView(text("Comandos livres são classificados antes da execução. VERDE observa; AMARELO altera de forma reversível; VERMELHO exige recuperação e autorização consciente.", 12f, textSecondary, false).apply { setPadding(0, dp(8), 0, 0) })
        root.addView(safety, margins(top = 24))
        root.addView(space(28))
        return root.parent as ScrollView
    }

    private fun showConnectionDialog() {
        val host = premiumInput("IP da TayTech", prefs.getString("host", "") ?: "", false)
        val port = premiumInput("Porta de conexão", prefs.getInt("port", 5555).toString(), true)
        val pairingPort = premiumInput("Porta de pareamento", "", true)
        val pairingCode = premiumInput("Código de pareamento", "", true)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(surface, 24, stroke)
        }
        panel.addView(eyebrow("CONNECTION ORCHESTRATOR"))
        panel.addView(text("Conexão TayTech", 24f, textPrimary, true), margins(top = 4))
        panel.addView(text("A automação tenta reconectar sozinha. Use este painel quando quiser assumir o controle manual.", 12f, textSecondary, false), margins(top = 6))
        panel.addView(host, margins(top = 18))
        panel.addView(port, margins(top = 10))

        lateinit var dialog: AlertDialog
        val connectionActions = horizontalRow(dp(10))
        connectionActions.addView(actionButton("Conectar", true) {
            val h = host.text.toString().trim(); val p = port.text.toString().toIntOrNull()
            if (h.isBlank() || p == null) toast("Informe IP e porta") else { connect(h, p, "manual"); dialog.dismiss() }
        }, weight())
        connectionActions.addView(actionButton("Usar :5555", false) {
            val h = host.text.toString().trim()
            if (h.isBlank()) toast("Informe o IP") else { connect(h, 5555, "tcp-5555"); dialog.dismiss() }
        }, weight())
        panel.addView(connectionActions, margins(top = 12))
        panel.addView(actionButton("Descobrir automaticamente na rede", false) { restartMdns(); dialog.dismiss() }, margins(top = 10))

        panel.addView(divider(), margins(top = 18, bottom = 18, height = 1))
        panel.addView(text("Pareamento por código", 16f, textPrimary, true))
        panel.addView(text("Normalmente você faz isso uma vez. Depois preservamos a identidade ADB e tentamos reencontrar o alvo automaticamente.", 12f, textSecondary, false), margins(top = 5))
        panel.addView(pairingPort, margins(top = 12))
        panel.addView(pairingCode, margins(top = 10))
        panel.addView(actionButton("Parear dispositivo", true) {
            val h = host.text.toString().trim(); val p = pairingPort.text.toString().toIntOrNull(); val code = pairingCode.text.toString().trim()
            if (h.isBlank() || p == null || code.length < 6) toast("Informe IP, porta e código") else { pairDevice(h, p, code); dialog.dismiss() }
        }, margins(top = 12))
        panel.addView(actionButton("Fechar", false) { dialog.dismiss() }, margins(top = 10))

        dialog = AlertDialog.Builder(this).setView(panel).create()
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
        val host = prefs.getString("host", "") ?: ""
        val port = prefs.getInt("port", 5555)
        updateEndpoint(host, port)
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
                    updateEndpoint(host, port)
                    setStatus("● Conectada · $strategy")
                    reconnecting = false
                }
            } catch (t: Throwable) {
                session?.transportErrors = (session?.transportErrors ?: 0) + 1
                mainHandler.post {
                    setStatus("○ Falha em $host:$port · ${shortError(t)}")
                    reconnecting = false
                    refreshSessionText()
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
            setStatus("○ Sem alvo salvo · procurando mDNS…")
            restartMdns()
            return
        }

        val candidates = linkedSetOf(5555, savedPort)
        reconnecting = true
        setStatus("◌ Reconectando automaticamente…")
        executor.execute {
            var successResult: Triple<Kadb, Int, String>? = null
            for (candidatePort in candidates) {
                val candidate = runCatching { Kadb.tryConnection(host, candidatePort) }.getOrNull()
                if (candidate != null) {
                    successResult = Triple(candidate, candidatePort, if (candidatePort == 5555) "tcp-5555" else "last-endpoint")
                    break
                }
            }
            mainHandler.post {
                reconnecting = false
                if (successResult != null) {
                    runCatching { kadb?.close() }
                    kadb = successResult!!.first
                    currentHost = host
                    currentPort = successResult!!.second
                    prefs.edit().putInt("port", currentPort).putString("strategy", successResult!!.third).apply()
                    session?.reconnectCount = (session?.reconnectCount ?: 0) + 1
                    updateEndpoint(host, currentPort)
                    setStatus("● Reconectada · ${successResult!!.third}")
                    refreshSessionText()
                } else {
                    setStatus("◌ Endpoint salvo indisponível · procurando mDNS…")
                    restartMdns()
                }
            }
        }
    }

    private fun pairDevice(host: String, port: Int, code: String) {
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
            val dialog = AlertDialog.Builder(this)
                .setTitle("Comando $risk")
                .setMessage(riskMessage(risk, command))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Executar") { _, _ -> executeCommand("Terminal livre", command, risk, null) }
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(if (risk == "VERMELHO") danger else warning)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accent)
            }
            dialog.show()
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
        if (::outputView.isInitialized) outputView.text = "Executando: $title…"
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
                    if (::outputView.isInitialized) outputView.text = "[$risk] $title · exit=${response.exitCode}\n\n$all"
                    refreshSessionText()
                    refreshSessionTimeline()
                    if (currentScreen != "terminal") toast("$title concluído · exit=${response.exitCode}")
                }
            } catch (t: Throwable) {
                session?.transportErrors = (session?.transportErrors ?: 0) + 1
                runCatching { connection.resetConnection() }
                val message = "ERRO DE TRANSPORTE/EXECUÇÃO: ${t.stackTraceToString()}"
                val exec = Execution(started, title, command, "", message, -1, risk)
                session?.executions?.add(exec)
                appendSessionTerminal(exec)
                mainHandler.post {
                    if (::outputView.isInitialized) outputView.text = message
                    setStatus("◌ Sessão caiu · reconectando…")
                    refreshSessionText()
                    refreshSessionTimeline()
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
        s.executions.forEachIndexed { index, e -> append("${index + 1}. **${e.title}** · ${e.risk} · exit=${e.exitCode}\n") }
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
                mainHandler.post { toast("Falha ao exportar: ${shortError(t)}")
                }
            }
        }
    }

    private fun createZip(source: File, target: File) {
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            source.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(source).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(relative))
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
            override fun onDiscoveryStarted(serviceType: String) { setStatus("◌ Procurando ADB na rede…") }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains("_adb-tls-connect")) return
                @Suppress("DEPRECATION")
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        val port = resolved.port
                        mainHandler.post {
                            prefs.edit().putString("host", host).putInt("port", port).apply()
                            updateEndpoint(host, port)
                            if (kadb?.connectionCheck() != true && !reconnecting) connect(host, port, "mdns", silent = true)
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) { if (kadb?.connectionCheck() != true) setStatus("○ ADB mDNS saiu da rede") }
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
        runCatching { manager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { discoveryListener = null }
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

    private fun refreshCommandRisk() {
        if (!::commandInput.isInitialized || !::commandRiskView.isInitialized) return
        val risk = classifyRisk(commandInput.text.toString())
        val pair = when (risk) {
            "AMARELO" -> warning to Color.rgb(65, 48, 20)
            "VERMELHO" -> danger to Color.rgb(67, 28, 32)
            else -> success to Color.rgb(18, 57, 47)
        }
        commandRiskView.text = risk
        commandRiskView.setTextColor(pair.first)
        commandRiskView.background = rounded(pair.second, 999, pair.first)
    }

    private fun copyOutput() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CUSTOMROM output", if (::outputView.isInitialized) outputView.text else ""))
        toast("Saída copiada")
    }

    private fun sessionText(): String {
        val s = session ?: return "Sem sessão"
        return "Sessão ${s.id} · ${s.executions.size} execuções · ${s.reconnectCount} reconexões · ${s.transportErrors} erros de transporte"
    }

    private fun refreshSessionText() {
        val value = sessionText()
        if (::sessionView.isInitialized) sessionView.text = value
        if (::homeSessionView.isInitialized) homeSessionView.text = value
    }

    private fun refreshSessionTimeline() {
        if (!::timelineHost.isInitialized) return
        timelineHost.removeAllViews()
        val executions = session?.executions.orEmpty()
        if (executions.isEmpty()) {
            timelineHost.addView(card().apply {
                addView(text("Nenhuma execução ainda", 15f, textPrimary, true))
                addView(text("Abra o Terminal ou rode uma receita. O histórico aparecerá aqui automaticamente.", 12f, textSecondary, false).apply { setPadding(0, dp(6), 0, 0) })
            })
            return
        }
        executions.takeLast(12).reversed().forEach { e ->
            val item = card(Color.rgb(13, 19, 28)).apply { setPadding(dp(14), dp(13), dp(14), dp(13)) }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(text(e.title, 14f, textPrimary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(riskPill(e.risk))
            item.addView(top)
            item.addView(text("${iso(e.at).substring(11, 19)} · exit=${e.exitCode}", 11f, textSecondary, false).apply { setPadding(0, dp(5), 0, 0) })
            timelineHost.addView(item, margins(bottom = 8))
        }
    }

    private fun setStatus(value: String) {
        val update = {
            val color = when {
                value.contains("Conectada", true) || value.contains("Reconectada", true) || value.contains("concluído", true) -> success
                value.contains("Falha", true) || value.contains("caiu", true) || value.contains("saiu", true) -> danger
                else -> warning
            }
            if (::statusView.isInitialized) {
                statusView.text = value
                statusView.setTextColor(color)
                statusView.background = rounded(surface2, 16, color)
            }
            if (::homeStatusView.isInitialized) {
                homeStatusView.text = value.removePrefix("● ").removePrefix("◌ ").removePrefix("○ ")
                homeStatusView.setTextColor(color)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) update() else mainHandler.post(update)
    }

    private fun updateEndpoint(host: String, port: Int) {
        val label = if (host.isBlank()) "Endpoint ainda não confirmado" else "$host:$port · ${prefs.getString("strategy", "aguardando conexão")}" 
        if (::endpointView.isInitialized) endpointView.text = label
    }

    private fun recipeCard(recipe: Recipe): View {
        val result = card()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(recipe.name, 16f, textPrimary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(riskPill(recipe.risk))
        result.addView(top)
        result.addView(text(recipeDescription(recipe.id), 12f, textSecondary, false).apply { setPadding(0, dp(7), 0, 0) })
        result.addView(text("Saída: ${recipe.output}", 10f, Color.rgb(117, 135, 158), false).apply { setPadding(0, dp(8), 0, 0) })
        result.addView(actionButton("Executar receita", false) { executeRecipe(recipe) }, margins(top = 12))
        return result
    }

    private fun recipeDescription(id: String): String = when (id) {
        "estado-geral" -> "Identificação do sistema e fotografia segura do estado atual."
        "memoria-zram" -> "RAM, swap e ZRAM para investigar pressão de memória e lentidão."
        "processos" -> "Processos mais pesados para localizar consumo anormal."
        "rede-adb" -> "Contexto de rede e transporte ADB para problemas de conexão."
        "logcat-curto" -> "Captura curta de eventos Android para reproduções controladas."
        "pacotes-servicos" -> "Inventário de pacotes e serviços sem desativar nada."
        else -> "Rotina versionada do projeto CUSTOMROM."
    }

    private fun riskPill(risk: String): TextView = when (risk) {
        "AMARELO" -> pill(risk, warning, Color.rgb(65, 48, 20))
        "VERMELHO" -> pill(risk, danger, Color.rgb(67, 28, 32))
        else -> pill(risk, success, Color.rgb(18, 57, 47))
    }

    private fun verticalScroll(): LinearLayout {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            clipToPadding = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), 0)
        }
        scroll.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun card(color: Int = surface): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = rounded(color, 22, stroke)
    }

    private fun featureCard(icon: String, title: String, description: String, action: () -> Unit): View = card(surface2).apply {
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        addView(text(icon, 24f, accent, true))
        addView(text(title, 15f, textPrimary, true).apply { setPadding(0, dp(11), 0, 0) })
        addView(text(description, 11f, textSecondary, false).apply { setPadding(0, dp(6), 0, 0) })
        addView(text("Abrir  →", 11f, Color.rgb(183, 172, 255), true).apply { setPadding(0, dp(14), 0, 0) })
    }

    private fun infoStrip(title: String, detail: String, badge: String): View = card(Color.rgb(13, 19, 28)).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val body = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text(title, 14f, textPrimary, true))
        body.addView(text(detail, 11f, textSecondary, false).apply { setPadding(0, dp(4), 0, 0) })
        addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(pill(badge, accent, Color.rgb(36, 29, 78)))
    }

    private fun menuRow(icon: String, title: String, detail: String, action: () -> Unit): View = card(Color.rgb(13, 19, 28)).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        setOnClickListener { action() }
        addView(text(icon, 22f, accent, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val body = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text(title, 14f, textPrimary, true))
        body.addView(text(detail, 11f, textSecondary, false).apply { setPadding(0, dp(4), 0, 0) })
        addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(10) })
        addView(text("›", 28f, textSecondary, false))
    }

    private fun sectionHeader(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(title, 18f, textPrimary, true))
        addView(text(subtitle, 11f, textSecondary, false).apply { setPadding(0, dp(3), 0, 0) })
    }

    private fun eyebrow(value: String): TextView = text(value, 10f, Color.rgb(154, 139, 255), true).apply { letterSpacing = 0.14f }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = false
    }

    private fun pill(value: String, foreground: Int, backgroundColor: Int): TextView = text(value, 10f, foreground, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = rounded(backgroundColor, 999, foreground)
    }

    private fun actionButton(label: String, primary: Boolean, action: () -> Unit): TextView = text(label, 13f, if (primary) Color.WHITE else textPrimary, true).apply {
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = if (primary) rounded(accent, 16) else rounded(surface3, 16, stroke)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun iconButton(label: String, action: () -> Unit): TextView = text(label, 22f, textSecondary, false).apply {
        gravity = Gravity.CENTER
        background = rounded(surface3, 16, stroke)
        isClickable = true
        setOnClickListener { action() }
    }

    private fun premiumInput(hintText: String, initial: String, numeric: Boolean): EditText = EditText(this).apply {
        hint = hintText
        setText(initial)
        setTextColor(textPrimary)
        setHintTextColor(Color.rgb(103, 119, 139))
        textSize = 14f
        setSingleLine(true)
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        background = rounded(Color.rgb(8, 13, 20), 15, stroke)
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun horizontalRow(gap: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = GradientDrawable().apply { setSize(gap, 1); setColor(Color.TRANSPARENT) }
    }

    private fun divider(): View = View(this).apply { setBackgroundColor(stroke) }
    private fun space(height: Int): View = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun margins(top: Int = 0, bottom: Int = 0, height: Int? = null): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height?.let(::dp) ?: ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun rounded(color: Int, radius: Int, strokeColor: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
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
