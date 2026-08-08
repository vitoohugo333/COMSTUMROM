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
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
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
import java.util.concurrent.Future
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PremiumOpsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("customrom_adb", Context.MODE_PRIVATE) }
    private val ledger by lazy { ChangeLedger(this) }
    private lateinit var adb: AdbRemoteController

    private val recipes = mutableListOf<PremiumRecipe>()
    private var session: PremiumSession? = null
    private var activeTask: Future<*>? = null
    private var currentScreen = "commands"
    private var lastRawOutput = ""
    private var latestHumanResult: HumanOperationResult? = null

    private lateinit var contentHost: FrameLayout
    private lateinit var statusView: TextView
    private lateinit var statusDetailView: TextView
    private lateinit var operationBanner: LinearLayout
    private lateinit var operationTitle: TextView
    private lateinit var operationDetail: TextView
    private val navButtons = linkedMapOf<String, TextView>()
    private val screens = linkedMapOf<String, View>()

    private lateinit var commandSearch: EditText
    private lateinit var commandListHost: LinearLayout
    private lateinit var terminalInput: EditText
    private lateinit var terminalRisk: TextView
    private lateinit var terminalOutput: TextView
    private lateinit var terminalRunButton: TextView
    private lateinit var appSearch: EditText
    private lateinit var appListHost: LinearLayout
    private lateinit var appStatusView: TextView
    private lateinit var diagnosticSummaryView: TextView
    private lateinit var diagnosticRawView: TextView
    private lateinit var diagnosticActionsHost: LinearLayout
    private lateinit var sessionSummaryView: TextView
    private lateinit var timelineHost: LinearLayout
    private lateinit var ledgerHost: LinearLayout

    private val appPackages = mutableListOf<PackageSnapshot>()
    private var appFilter = "Todos"

    private val bg = Color.rgb(5, 10, 19)
    private val surface = Color.rgb(10, 18, 32)
    private val surface2 = Color.rgb(14, 25, 43)
    private val surface3 = Color.rgb(18, 32, 53)
    private val line = Color.rgb(37, 54, 77)
    private val textPrimary = Color.rgb(244, 248, 255)
    private val textSecondary = Color.rgb(142, 159, 183)
    private val textMuted = Color.rgb(91, 111, 139)
    private val cyan = Color.rgb(0, 174, 239)
    private val cyanSoft = Color.rgb(5, 48, 72)
    private val success = Color.rgb(58, 214, 151)
    private val warning = Color.rgb(248, 184, 73)
    private val danger = Color.rgb(255, 92, 111)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        loadRecipes()
        startNewSession()
        adb = AdbRemoteController(this, ::renderConnectionState)
        setContentView(buildUi())
        adb.start()
    }

    override fun onDestroy() {
        activeTask?.cancel(true)
        adb.close()
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
                    @Suppress("DEPRECATION") left = insets.systemWindowInsetLeft
                    @Suppress("DEPRECATION") top = insets.systemWindowInsetTop
                    @Suppress("DEPRECATION") right = insets.systemWindowInsetRight
                    @Suppress("DEPRECATION") bottom = insets.systemWindowInsetBottom
                }
                view.setPadding(left, top, right, bottom)
                insets
            }
        }
        root.addView(buildTopBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(82)))
        root.addView(buildOperationBanner())
        contentHost = FrameLayout(this).apply { setBackgroundColor(bg) }
        root.addView(contentHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildBottomNavigation(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)))

        screens["commands"] = buildCommandsScreen()
        screens["terminal"] = buildTerminalScreen()
        screens["apps"] = buildAppsScreen()
        screens["diagnostics"] = buildDiagnosticsScreen()
        screens["sessions"] = buildSessionsScreen()
        showSection("commands")
        return root
    }

    private fun buildTopBar(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(14), dp(8))
            setBackgroundColor(bg)
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("CUSTOMROM", 19f, textPrimary, true).apply { letterSpacing = 0.02f })
        brand.addView(text("S23  →  TAYTECH", 9f, textSecondary, true).apply {
            letterSpacing = 0.16f
            setPadding(0, dp(2), 0, 0)
        })
        root.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val statusColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        statusView = text("◌ PROCURANDO", 11f, warning, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(surface2, 999, warning)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { showConnectionDialog() }
            pressFeedback(this)
        }
        statusDetailView = text("ADB remoto", 9f, textMuted, false).apply {
            gravity = Gravity.END
            setPadding(0, dp(4), dp(2), 0)
            maxLines = 1
        }
        statusColumn.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)))
        statusColumn.addView(statusDetailView)
        root.addView(statusColumn)
        return root
    }

    private fun buildOperationBanner(): View {
        operationBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(surface2, 16, line)
            visibility = View.GONE
            setOnClickListener { latestHumanResult?.let { showTechnicalResult(it, lastRawOutput) } }
        }
        operationTitle = text("Pronto", 13f, textPrimary, true)
        operationDetail = text("", 11f, textSecondary, false).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        operationBanner.addView(operationTitle)
        operationBanner.addView(operationDetail, margins(top = 3))
        return operationBanner
    }

    private fun buildBottomNavigation(): View {
        val items = listOf(
            Triple("commands", "▶", "Comandos"),
            Triple("terminal", "⌘", "Terminal"),
            Triple("apps", "▦", "Apps"),
            Triple("diagnostics", "◇", "Diagnóstico"),
            Triple("sessions", "▤", "Sessões")
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(8))
            setBackgroundColor(Color.rgb(7, 14, 25))
            items.forEach { (key, icon, label) ->
                val item = TextView(this@PremiumOpsActivity).apply {
                    text = "$icon\n$label"
                    textSize = 10f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setTextColor(textMuted)
                    setPadding(dp(4), dp(5), dp(4), dp(5))
                    setOnClickListener { showSection(key) }
                    pressFeedback(this)
                }
                navButtons[key] = item
                addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }
        }
    }

    private fun showSection(key: String) {
        currentScreen = key
        contentHost.removeAllViews()
        contentHost.addView(screens.getValue(key), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        navButtons.forEach { (name, view) ->
            val active = name == key
            view.setTextColor(if (active) cyan else textMuted)
            view.background = if (active) rounded(Color.rgb(7, 35, 52), 18) else rounded(Color.TRANSPARENT, 18)
        }
        when (key) {
            "commands" -> refreshCommandList()
            "apps" -> refreshAppList()
            "sessions" -> { refreshSessionSummary(); refreshTimeline(); refreshLedger() }
        }
    }

    private fun buildCommandsScreen(): View {
        val root = verticalScroll()
        root.addView(pageTitle("Comandos", "Personalização e investigação sem decorar shell"))
        root.addView(infoStrip("TayTech", "Conexão automática e recuperável", "TOCAR PARA GERENCIAR") { showConnectionDialog() }, margins(top = 16))

        root.addView(sectionTitle("Ações de alto valor", "Fluxos compostos para o dia a dia"), margins(top = 22))
        root.addView(featureAction("◇", "Por que a central está lenta?", "Memória, CPU, processos, armazenamento e thermal em uma coleta.") {
            recipes.firstOrNull { it.id == "diagnostico-lentidao" }?.let(::runRecipe)
        }, margins(top = 8))
        root.addView(featureAction("▦", "Gerenciar aplicativos", "Lista real da TayTech com criticidade, confiança, motivos e rollback.") {
            showSection("apps")
            if (appPackages.isEmpty()) loadAppInventory()
        }, margins(top = 8))

        root.addView(sectionTitle("Personalização rápida", "Mudanças reversíveis continuam exigindo confirmação"), margins(top = 22))
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(compactAction("Animações 0x") { runRecipeById("animacoes-off") }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        row1.addView(compactAction("Animações 1x") { runRecipeById("animacoes-on") }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(row1, margins(top = 8))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(compactAction("Rotação auto") { runRecipeById("rotacao-auto-on") }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        row2.addView(compactAction("Rotação fixa") { runRecipeById("rotacao-auto-off") }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(row2, margins(top = 8))

        commandSearch = input("Buscar comando, função ou shell…", "", false).apply { addTextChangedListener(simpleWatcher { refreshCommandList() }) }
        root.addView(commandSearch, margins(top = 20))
        root.addView(sectionTitle("Biblioteca", "Favoritos aparecem primeiro · ${recipes.size} receitas"), margins(top = 20))
        commandListHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(commandListHost, margins(top = 8))
        root.addView(space(24))
        refreshCommandList()
        return root.parent as ScrollView
    }

    private fun refreshCommandList() {
        if (!::commandListHost.isInitialized) return
        commandListHost.removeAllViews()
        val query = if (::commandSearch.isInitialized) commandSearch.text.toString().trim().lowercase(Locale.ROOT) else ""
        val favorites = prefs.getStringSet("favorite_recipes", emptySet()) ?: emptySet()
        val filtered = recipes.filter {
            query.isBlank() || it.name.lowercase(Locale.ROOT).contains(query) || it.command.lowercase(Locale.ROOT).contains(query) || recipeDescription(it.id).lowercase(Locale.ROOT).contains(query)
        }.sortedWith(compareByDescending<PremiumRecipe> { favorites.contains(it.id) }.thenBy { it.name })
        if (filtered.isEmpty()) {
            commandListHost.addView(emptyState("Nenhum comando encontrado", "Tente outro termo."))
            return
        }
        filtered.forEach { recipe -> commandListHost.addView(commandRow(recipe, favorites.contains(recipe.id)), margins(bottom = 8)) }
    }

    private fun commandRow(recipe: PremiumRecipe, favorite: Boolean): View {
        val row = card().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(11), dp(8), dp(11)) }
        val play = text("▶", 20f, bg, true).apply {
            gravity = Gravity.CENTER
            background = rounded(if (recipe.risk == "VERDE") cyan else warning, 999)
            setOnClickListener { runRecipe(recipe) }
            pressFeedback(this)
        }
        row.addView(play, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text(recipe.name, 15f, textPrimary, true))
        body.addView(text(recipeDescription(recipe.id), 11f, textSecondary, false).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(4), 0, 0) })
        val meta = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        meta.addView(riskPill(recipe.risk))
        meta.addView(text("  ${recipeCategory(recipe.id)}", 9f, textMuted, true))
        body.addView(meta, margins(top = 7))
        row.addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val star = text(if (favorite) "★" else "☆", 22f, if (favorite) cyan else textMuted, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { toggleFavorite(recipe.id); refreshCommandList() }
            pressFeedback(this)
        }
        row.addView(star, LinearLayout.LayoutParams(dp(44), dp(48)))
        row.setOnClickListener { showRecipeDetails(recipe) }
        pressFeedback(row)
        return row
    }

    private fun buildTerminalScreen(): View {
        val root = verticalScroll()
        root.addView(pageTitle("Terminal", "Shell remoto com estados humanos e detalhes técnicos sob demanda"))
        val editor = card().apply { setPadding(dp(14), dp(14), dp(14), dp(14)) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text("COMANDO / BLOCO", 10f, textMuted, true).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        terminalRisk = riskPill("VERDE")
        top.addView(terminalRisk)
        editor.addView(top)
        terminalInput = input("Cole um comando ou várias linhas…", "getprop ro.product.model", false).apply {
            minLines = 6
            gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            addTextChangedListener(simpleWatcher { refreshTerminalRisk() })
        }
        editor.addView(terminalInput, margins(top = 12, height = 180))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        terminalRunButton = primaryButton("Executar") { runTerminal() }
        actions.addView(terminalRunButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        actions.addView(softButton("Interromper") { cancelActiveOperation() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        editor.addView(actions, margins(top = 10))
        root.addView(editor, margins(top = 16))

        root.addView(sectionTitle("Resultado", "A mensagem principal é humana; códigos ficam nos detalhes"), margins(top = 22))
        val console = card(Color.rgb(3, 8, 15)).apply { setPadding(dp(14), dp(14), dp(14), dp(14)) }
        terminalOutput = text("Pronto para executar.\n\nQuando um comando retornar exit=0 sem texto, você verá “Concluído · nenhum texto foi retornado”, e não uma falsa “saída zero”.", 12f, Color.rgb(190, 205, 224), false).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.15f)
        }
        console.addView(terminalOutput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)))
        val consoleActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        consoleActions.addView(softButton("Copiar") { copyText(terminalOutput.text.toString()) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
        consoleActions.addView(softButton("Limpar") { terminalOutput.text = "" }, LinearLayout.LayoutParams(0, dp(46), 1f))
        console.addView(consoleActions, margins(top = 12))
        root.addView(console, margins(top = 8))
        root.addView(space(24))
        refreshTerminalRisk()
        return root.parent as ScrollView
    }

    private fun buildAppsScreen(): View {
        val root = verticalScroll()
        root.addView(pageTitle("Aplicativos", "Inventário real, criticidade explicável e rollback visível"))
        val header = card(cyanSoft).apply { background = rounded(cyanSoft, 20, cyan) }
        header.addView(text("Inteligência de packages", 17f, textPrimary, true))
        header.addView(text("O CUSTOMROM separa criticidade do aplicativo do risco da ação. Desconhecido nunca vira seguro por ausência de informação.", 11f, Color.rgb(185, 221, 238), false).apply { setPadding(0, dp(6), 0, 0) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(primaryButton("Carregar / atualizar") { loadAppInventory() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        actions.addView(softButton("Alterações") { appFilter = "Alterados"; refreshAppList() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(actions, margins(top = 12))
        root.addView(header, margins(top = 16))

        appStatusView = text("Toque em carregar para consultar a TayTech.", 11f, textSecondary, false)
        root.addView(appStatusView, margins(top = 12))
        appSearch = input("Buscar nome ou package…", "", false).apply { addTextChangedListener(simpleWatcher { refreshAppList() }) }
        root.addView(appSearch, margins(top = 12))
        root.addView(buildAppFilters(), margins(top = 10))
        appListHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(appListHost, margins(top = 12))
        root.addView(space(24))
        refreshAppList()
        return root.parent as ScrollView
    }

    private fun buildAppFilters(): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Todos", "Rodando", "Usuário", "Sistema", "Desativados", "Protegidos", "Candidatos", "Alterados").forEach { label ->
            row.addView(filterChip(label) { appFilter = label; refreshAppList() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { rightMargin = dp(7) })
        }
        scroll.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll
    }

    private fun refreshAppList() {
        if (!::appListHost.isInitialized) return
        appListHost.removeAllViews()
        val query = if (::appSearch.isInitialized) appSearch.text.toString().trim().lowercase(Locale.ROOT) else ""
        val changed = ledger.list().map { it.packageName }.toSet()
        val filtered = appPackages.filter { snapshot ->
            val assessment = PackageIntelligence.assess(snapshot)
            val matchesQuery = query.isBlank() || snapshot.packageName.lowercase(Locale.ROOT).contains(query) || PackageIntelligence.friendlyName(snapshot.packageName).lowercase(Locale.ROOT).contains(query)
            val matchesFilter = when (appFilter) {
                "Rodando" -> snapshot.running
                "Usuário" -> snapshot.kind == "Usuário"
                "Sistema" -> snapshot.kind == "Sistema"
                "Desativados" -> snapshot.disabled
                "Protegidos" -> assessment.criticality == PackageCriticality.PROTECTED || assessment.criticality == PackageCriticality.HIGH
                "Candidatos" -> assessment.candidateForReversibleTest
                "Alterados" -> changed.contains(snapshot.packageName)
                else -> true
            }
            matchesQuery && matchesFilter
        }.sortedWith(compareBy<PackageSnapshot>({ PackageIntelligence.assess(it).criticality.ordinal }, { PackageIntelligence.friendlyName(it.packageName) }))

        if (filtered.isEmpty()) {
            appListHost.addView(emptyState(if (appPackages.isEmpty()) "Inventário ainda não carregado" else "Nenhum aplicativo neste filtro", if (appPackages.isEmpty()) "Carregue a lista diretamente da TayTech." else "Troque o filtro ou a busca."))
            return
        }
        filtered.forEach { appListHost.addView(appRow(it), margins(bottom = 8)) }
    }

    private fun appRow(snapshot: PackageSnapshot): View {
        val assessment = PackageIntelligence.assess(snapshot)
        val row = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val identity = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        identity.addView(text(PackageIntelligence.friendlyName(snapshot.packageName), 15f, textPrimary, true))
        identity.addView(text(snapshot.packageName, 10f, textMuted, false).apply { typeface = Typeface.MONOSPACE; maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE })
        top.addView(identity, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(criticalityPill(assessment))
        row.addView(top)
        val state = buildString {
            append(snapshot.kind)
            if (snapshot.running) append(" · rodando")
            if (snapshot.disabled) append(" · desativado")
            if (ledger.wasDisabledByCustomrom(snapshot.packageName)) append(" · alterado pelo CUSTOMROM")
        }
        row.addView(text(state, 10f, textSecondary, false).apply { setPadding(0, dp(7), 0, 0) })
        row.addView(text(assessment.reasons.firstOrNull() ?: "Analisar para obter mais evidência", 10f, textMuted, false).apply { setPadding(0, dp(5), 0, 0); maxLines = 2 })
        row.setOnClickListener { showAppDetail(snapshot) }
        pressFeedback(row)
        return row
    }

    private fun loadAppInventory() {
        appStatusView.text = "Consultando packages, estado, origem e processos…"
        val command = "echo __ALL__; pm list packages -f -u; echo __SYSTEM__; pm list packages -s -f -u; echo __THIRD__; pm list packages -3 -f -u; echo __DISABLED__; pm list packages -d -f -u; echo __RUNNING__; ps -A"
        executeOperation("Inventário de aplicativos", command, "VERDE", showDialog = false) { outcome, result ->
            if (result.success) {
                val parsed = parsePackageInventory(outcome.stdout)
                appPackages.clear()
                appPackages.addAll(parsed)
                appStatusView.text = "${parsed.size} packages carregados · classificação conservadora local"
                refreshAppList()
            } else {
                appStatusView.text = result.title + " · " + result.detail.take(120)
            }
        }
    }

    private fun parsePackageInventory(raw: String): List<PackageSnapshot> {
        val sections = mutableMapOf<String, MutableList<String>>()
        var current = ""
        raw.lineSequence().forEach { lineRaw ->
            val line = lineRaw.trim()
            if (line.startsWith("__") && line.endsWith("__")) {
                current = line
                sections.getOrPut(current) { mutableListOf() }
            } else if (current.isNotBlank()) {
                sections.getOrPut(current) { mutableListOf() }.add(line)
            }
        }
        fun parsePackageLines(lines: List<String>): Map<String, String> = buildMap {
            lines.filter { it.startsWith("package:") }.forEach { line ->
                val value = line.removePrefix("package:")
                val idx = value.lastIndexOf('=')
                if (idx > 0) put(value.substring(idx + 1).trim(), value.substring(0, idx).trim())
                else put(value.trim(), "")
            }
        }
        val all = parsePackageLines(sections["__ALL__"].orEmpty())
        val system = parsePackageLines(sections["__SYSTEM__"].orEmpty()).keys
        val third = parsePackageLines(sections["__THIRD__"].orEmpty()).keys
        val disabled = parsePackageLines(sections["__DISABLED__"].orEmpty()).keys
        val runningText = sections["__RUNNING__"].orEmpty().joinToString("\n")
        return all.map { (pkg, path) ->
            PackageSnapshot(
                packageName = pkg,
                apkPath = path,
                kind = when { third.contains(pkg) -> "Usuário"; system.contains(pkg) -> "Sistema"; path.contains("/data/app/") -> "Usuário"; else -> "Sistema" },
                disabled = disabled.contains(pkg),
                running = runningText.contains(pkg)
            )
        }
    }

    private fun showAppDetail(snapshotInput: PackageSnapshot) {
        val snapshot = appPackages.firstOrNull { it.packageName == snapshotInput.packageName } ?: snapshotInput
        val assessment = PackageIntelligence.assess(snapshot)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(12)); background = rounded(surface, 24, line) }
        panel.addView(text(PackageIntelligence.friendlyName(snapshot.packageName), 21f, textPrimary, true))
        panel.addView(text(snapshot.packageName, 11f, textMuted, false).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }, margins(top = 4))
        panel.addView(criticalityPill(assessment), margins(top = 10))
        panel.addView(text("Confiança ${assessment.confidence.label}", 11f, textSecondary, true), margins(top = 8))
        panel.addView(text(assessment.reasons.joinToString("\n") { "• $it" }, 12f, textSecondary, false), margins(top = 10))
        if (snapshot.apkPath.isNotBlank()) panel.addView(text(snapshot.apkPath, 10f, textMuted, false).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }, margins(top = 10))
        val stateText = buildString {
            append(if (snapshot.disabled) "Desativado" else "Ativo")
            append(" · ").append(if (snapshot.running) "rodando" else "sem processo detectado")
            append(" · ").append(snapshot.kind)
        }
        panel.addView(text(stateText, 11f, if (snapshot.disabled) warning else success, true), margins(top = 10))

        lateinit var dialog: AlertDialog
        panel.addView(primaryButton("Analisar com mais evidência") { dialog.dismiss(); inspectPackage(snapshot.packageName) }, margins(top = 16))
        val mutableAllowed = assessment.criticality != PackageCriticality.PROTECTED
        if (assessment.criticality == PackageCriticality.HIGH || assessment.criticality == PackageCriticality.UNKNOWN) {
            panel.addView(callout("Controle avançado", "Criticidade ${assessment.criticality.label}: esta função pode ser importante, mas a decisão é sua. O comando atua somente no usuário 0, é mostrado antes da execução e o estado é verificado depois."), margins(top = 12))
        }
        if (mutableAllowed) {
            panel.addView(softButton("Parar temporariamente") { dialog.dismiss(); forceStopPackage(snapshot) }, margins(top = 8))
            if (snapshot.disabled) {
                val enableLabel = if (ledger.wasDisabledByCustomrom(snapshot.packageName)) "Restaurar alteração do CUSTOMROM" else "Ativar para usuário 0"
                panel.addView(softButton(enableLabel) { dialog.dismiss(); enablePackage(snapshot) }, margins(top = 8))
            } else {
                val disableLabel = if (snapshot.kind == "Sistema") "Desativar para usuário 0 (avançado)" else "Desativar reversivelmente"
                panel.addView(dangerButton(disableLabel) { dialog.dismiss(); disablePackage(snapshot) }, margins(top = 8))
            }
            panel.addView(softButton("Logs recentes deste app") { dialog.dismiss(); showPackageLog(snapshot.packageName) }, margins(top = 8))
            panel.addView(softButton("Abrir app na TayTech") { dialog.dismiss(); launchPackage(snapshot.packageName) }, margins(top = 8))
        } else {
            panel.addView(callout("Núcleo protegido", "Este package pertence ao núcleo Android/ADB/hardware essencial conhecido. Aqui o CUSTOMROM evita desativação porque perder o próprio caminho de recuperação é diferente de interromper uma função automotiva reversível."), margins(top = 12))
            panel.addView(softButton("Logs recentes deste app") { dialog.dismiss(); showPackageLog(snapshot.packageName) }, margins(top = 8))
        }
        panel.addView(softButton("Fechar") { dialog.dismiss() }, margins(top = 10))
        dialog = premiumDialog(panel)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun inspectPackage(packageNameRaw: String) {
        val pkg = sanitizePackage(packageNameRaw) ?: return
        val command = "echo '=== PACKAGE ==='; dumpsys package $pkg 2>/dev/null | head -n 500; echo; echo '=== PID ==='; pidof $pkg 2>/dev/null; echo; echo '=== MEMINFO ==='; dumpsys meminfo $pkg 2>/dev/null | head -n 180; echo; echo '=== SERVICES MATCH ==='; dumpsys activity services 2>/dev/null | grep -i -B2 -A5 '$pkg' | head -n 160"
        executeOperation("Analisar ${PackageIntelligence.friendlyName(pkg)}", command, "VERDE", showDialog = false) { outcome, result ->
            if (result.success) {
                val index = appPackages.indexOfFirst { it.packageName == pkg }
                val base = if (index >= 0) appPackages[index] else PackageSnapshot(pkg)
                val updated = base.copy(metadata = outcome.stdout, running = outcome.stdout.contains("PID", true) && Regex("\b[0-9]{2,}\b").containsMatchIn(outcome.stdout))
                if (index >= 0) appPackages[index] = updated else appPackages.add(updated)
                refreshAppList()
                showAppDetail(updated)
            } else showTechnicalResult(result, combineRaw(outcome))
        }
    }

    private fun forceStopPackage(snapshot: PackageSnapshot) {
        val pkg = sanitizePackage(snapshot.packageName) ?: return
        executeOperation("Parar ${PackageIntelligence.friendlyName(pkg)}", "am force-stop --user 0 $pkg; echo 'Aplicativo interrompido temporariamente'", "AMARELO", showDialog = false) { outcome, result ->
            if (result.success) {
                ledger.append(ChangeRecord(pkg, "force-stop", if (snapshot.running) "running" else "unknown", "stopped", System.currentTimeMillis(), session?.id ?: "", outcome.exitCode, ""))
                updatePackage(pkg) { it.copy(running = false) }
                appPackages.firstOrNull { it.packageName == pkg }?.let(::showAppDetail)
            } else showTechnicalResult(result, combineRaw(outcome))
        }
    }

    private fun disablePackage(snapshot: PackageSnapshot) {
        val pkg = sanitizePackage(snapshot.packageName) ?: return
        val command = "pm disable-user --user 0 $pkg >/dev/null 2>&1; RC=${'$'}?; if pm list packages -d 2>/dev/null | grep -Fxq 'package:$pkg'; then echo 'Package desativado para usuário 0'; exit 0; else echo 'Falha: package não aparece como desativado'; exit ${'$'}RC; fi"
        executeOperation("Desativar ${PackageIntelligence.friendlyName(pkg)}", command, "AMARELO", showDialog = false) { outcome, result ->
            if (result.success) {
                ledger.append(ChangeRecord(pkg, "disable", if (snapshot.disabled) "disabled" else "enabled", "disabled", System.currentTimeMillis(), session?.id ?: "", outcome.exitCode, "pm enable --user 0 $pkg"))
                updatePackage(pkg) { it.copy(disabled = true, running = false) }
                appPackages.firstOrNull { it.packageName == pkg }?.let(::showAppDetail)
            } else showTechnicalResult(result, combineRaw(outcome))
        }
    }

    private fun enablePackage(snapshot: PackageSnapshot) {
        val pkg = sanitizePackage(snapshot.packageName) ?: return
        val command = "pm enable --user 0 $pkg >/dev/null 2>&1; RC=${'$'}?; if pm list packages -d 2>/dev/null | grep -Fxq 'package:$pkg'; then echo 'Falha: package continua desativado'; exit 2; else echo 'Package ativo para usuário 0'; exit ${'$'}RC; fi"
        executeOperation("Ativar ${PackageIntelligence.friendlyName(pkg)}", command, "AMARELO", showDialog = false) { outcome, result ->
            if (result.success) {
                ledger.append(ChangeRecord(pkg, "enable", "disabled", "enabled", System.currentTimeMillis(), session?.id ?: "", outcome.exitCode, "pm disable-user --user 0 $pkg"))
                updatePackage(pkg) { it.copy(disabled = false) }
                appPackages.firstOrNull { it.packageName == pkg }?.let(::showAppDetail)
            } else showTechnicalResult(result, combineRaw(outcome))
        }
    }

    private fun showPackageLog(packageNameRaw: String) {
        val pkg = sanitizePackage(packageNameRaw) ?: return
        val command = "PID=${'$'}(pidof $pkg 2>/dev/null | awk '{print ${'$'}1}'); if [ -n \"${'$'}PID\" ]; then logcat -d -v threadtime --pid=${'$'}PID -t 500 2>/dev/null || logcat -d -v threadtime -t 1200 2>/dev/null | grep -F '$pkg' | tail -n 500; else echo 'Package não está rodando; buscando referências recentes'; logcat -d -v threadtime -t 1600 2>/dev/null | grep -F '$pkg' | tail -n 500; fi"
        executeOperation("Logs de ${PackageIntelligence.friendlyName(pkg)}", command, "VERDE", showDialog = true) { _, _ -> }
    }

    private fun launchPackage(packageNameRaw: String) {
        val pkg = sanitizePackage(packageNameRaw) ?: return
        executeOperation("Abrir ${PackageIntelligence.friendlyName(pkg)} na TayTech", "monkey -p $pkg -c android.intent.category.LAUNCHER 1 2>/dev/null", "AMARELO", showDialog = true) { _, _ -> }
    }

    private fun restorePackage(snapshot: PackageSnapshot) {
        val pkg = sanitizePackage(snapshot.packageName) ?: return
        if (!ledger.wasDisabledByCustomrom(pkg)) {
            toast("O CUSTOMROM não possui evidência de ter desativado este package")
            return
        }
        executeOperation("Restaurar ${PackageIntelligence.friendlyName(pkg)}", "pm enable --user 0 $pkg", "AMARELO", showDialog = true) { outcome, result ->
            if (result.success) {
                ledger.append(ChangeRecord(pkg, "restore", "disabled", "enabled", System.currentTimeMillis(), session?.id ?: "", outcome.exitCode, ""))
                updatePackage(pkg) { it.copy(disabled = false) }
            }
        }
    }

    private fun updatePackage(packageName: String, transform: (PackageSnapshot) -> PackageSnapshot) {
        val index = appPackages.indexOfFirst { it.packageName == packageName }
        if (index >= 0) appPackages[index] = transform(appPackages[index])
        refreshAppList()
        refreshLedger()
    }

    private fun sanitizePackage(value: String): String? {
        val clean = value.trim()
        if (!Regex("^[A-Za-z0-9._]+$").matches(clean)) {
            toast("Package inválido")
            return null
        }
        return clean
    }

    private fun buildDiagnosticsScreen(): View {
        val root = verticalScroll()
        root.addView(pageTitle("Diagnóstico", "Perguntas humanas primeiro; comandos e logs continuam disponíveis"))
        root.addView(featureAction("◇", "Por que a central está lenta?", "Coleta segura de memória, CPU, processos, disco e thermal.") { runRecipeById("diagnostico-lentidao") }, margins(top = 16))
        root.addView(featureAction("▱", "A interface está engasgando?", "SurfaceFlinger, gfxinfo e estado de janela para investigar fluidez.") { runRecipeById("fluidez-gfx") }, margins(top = 8))
        root.addView(featureAction("♨", "Existe pressão térmica?", "ThermalService e zonas térmicas disponíveis no sistema.") { runRecipeById("thermal") }, margins(top = 8))
        root.addView(featureAction("◎", "ADB está instável?", "Rede, Wi‑Fi, propriedades ADB e portas do sistema.") { runRecipeById("rede-adb") }, margins(top = 8))
        root.addView(featureAction("↻", "O que inicia junto com a central?", "Transforma receivers e serviços de boot em aplicativos navegáveis.") { runRecipeById("boot-servicos") }, margins(top = 8))
        root.addView(featureAction("!", "Quais apps estão falhando?", "Crashes e ANRs viram atalhos para os packages relacionados.") { runRecipeById("falhas-crashes") }, margins(top = 8))
        root.addView(featureAction("◌", "Quem acorda a central?", "Wakelocks e alarmes são ligados aos possíveis aplicativos responsáveis.") { runRecipeById("wakelocks-alarmes") }, margins(top = 8))
        root.addView(featureAction("⇄", "O que trabalha em segundo plano?", "Jobs agendados viram owners investigáveis em vez de dump bruto.") { runRecipeById("jobs-agendados") }, margins(top = 8))
        root.addView(featureAction("●", "Quais serviços ficam sempre ativos?", "Foreground/persistent services viram packages acionáveis.") { runRecipeById("foreground-services") }, margins(top = 8))
        root.addView(featureAction("⌁", "Quais apps realmente foram usados?", "UsageStats ajuda a separar uso real de software apenas residente.") { runRecipeById("uso-apps") }, margins(top = 8))
        root.addView(featureAction("⚡", "Quem consumiu energia?", "Batterystats cruza atividade por UID/package desde a referência disponível.") { runRecipeById("batterystats-apps") }, margins(top = 8))
        root.addView(featureAction("⌂", "Quais launchers existem?", "Lista candidatos HOME e permite investigar cada launcher sem sair do fluxo.") { runRecipeById("launchers-disponiveis") }, margins(top = 8))

        root.addView(sectionTitle("Último resultado", "Resumo humano primeiro · evidência técnica sob demanda"), margins(top = 22))
        val summary = card().apply { setPadding(dp(14), dp(14), dp(14), dp(14)) }
        diagnosticSummaryView = text("Nenhum diagnóstico executado nesta abertura.", 13f, textPrimary, true)
        summary.addView(diagnosticSummaryView)
        diagnosticRawView = text("", 10f, textMuted, false).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            maxLines = 24
            visibility = View.GONE
        }
        summary.addView(diagnosticRawView, margins(top = 10))
        lateinit var evidenceToggle: TextView
        evidenceToggle = softButton("Ver evidência técnica") {
            val show = diagnosticRawView.visibility != View.VISIBLE
            diagnosticRawView.visibility = if (show) View.VISIBLE else View.GONE
            evidenceToggle.text = if (show) "Ocultar evidência técnica" else "Ver evidência técnica"
        }
        summary.addView(evidenceToggle, margins(top = 10))
        root.addView(summary, margins(top = 8))

        root.addView(sectionTitle("O que você pode fazer agora", "Cada resultado útil continua em uma próxima ação, sem executar mudanças sozinho"), margins(top = 22))
        diagnosticActionsHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        diagnosticActionsHost.addView(emptyState("Execute um diagnóstico", "Os próximos passos aparecerão aqui conforme o que for encontrado."))
        root.addView(diagnosticActionsHost, margins(top = 8))

        root.addView(sectionTitle("Ferramentas especializadas", "Receitas derivadas de práticas ADB e do aprendizado do projeto"), margins(top = 22))
        listOf("atividade-atual", "ui-hierarchy", "audio-radio", "bluetooth-status", "energia-power", "armazenamento", "boot-servicos", "logcat-curto").forEach { id ->
            recipes.firstOrNull { it.id == id }?.let { recipe -> root.addView(diagnosticRecipeRow(recipe), margins(top = 8)) }
        }
        root.addView(space(24))
        return root.parent as ScrollView
    }

    private fun diagnosticRecipeRow(recipe: PremiumRecipe): View = card().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val body = LinearLayout(this@PremiumOpsActivity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text(recipe.name, 14f, textPrimary, true))
        body.addView(text(recipeDescription(recipe.id), 10f, textSecondary, false).apply { setPadding(0, dp(4), 0, 0) })
        addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(text("Executar", 11f, cyan, true).apply { gravity = Gravity.CENTER; setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { runRecipe(recipe) }; pressFeedback(this) })
    }

    private fun buildSessionsScreen(): View {
        val root = verticalScroll()
        root.addView(pageTitle("Sessões", "Evidência, histórico e todas as alterações feitas pelo CUSTOMROM"))
        val summary = card()
        sessionSummaryView = text(sessionText(), 14f, textPrimary, true)
        summary.addView(sessionSummaryView)
        summary.addView(text("Cada shell executado fica vinculado à sessão. Alterações reversíveis também entram no ledger persistente.", 11f, textSecondary, false).apply { setPadding(0, dp(6), 0, 0) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(softButton("Nova sessão") { startNewSession(); refreshSessionSummary(); refreshTimeline() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
        actions.addView(primaryButton("Compartilhar Evidence Pack") { exportSession(true) }, LinearLayout.LayoutParams(0, dp(46), 1f))
        summary.addView(actions, margins(top = 12))
        root.addView(summary, margins(top = 16))

        root.addView(sectionTitle("Alterações feitas pelo CUSTOMROM", "O app só promete Restaurar quando possui histórico da própria mudança"), margins(top = 22))
        ledgerHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ledgerHost, margins(top = 8))

        root.addView(sectionTitle("Linha do tempo", "Execuções mais recentes"), margins(top = 22))
        timelineHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(timelineHost, margins(top = 8))
        root.addView(space(24))
        refreshSessionSummary(); refreshTimeline(); refreshLedger()
        return root.parent as ScrollView
    }

    private fun refreshLedger() {
        if (!::ledgerHost.isInitialized) return
        ledgerHost.removeAllViews()
        val records = ledger.list().takeLast(20).reversed()
        if (records.isEmpty()) {
            ledgerHost.addView(emptyState("Nenhuma alteração registrada", "Leituras e diagnósticos não entram neste ledger."))
            return
        }
        records.forEach { record ->
            val item = card(Color.rgb(9, 17, 29))
            val title = when (record.action) { "disable" -> "Desativado"; "restore" -> "Restaurado"; "force-stop" -> "Parado temporariamente"; else -> record.action }
            item.addView(text("$title · ${PackageIntelligence.friendlyName(record.packageName)}", 13f, textPrimary, true))
            item.addView(text(record.packageName, 9f, textMuted, false).apply { typeface = Typeface.MONOSPACE; setPadding(0, dp(4), 0, 0) })
            item.addView(text("${iso(record.at).replace('T', ' ').take(19)} · ${record.previousState} → ${record.newState}", 10f, textSecondary, false).apply { setPadding(0, dp(5), 0, 0) })
            if (record.action == "disable" && ledger.wasDisabledByCustomrom(record.packageName)) {
                item.addView(softButton("Restaurar") {
                    val snap = appPackages.firstOrNull { it.packageName == record.packageName } ?: PackageSnapshot(record.packageName, disabled = true)
                    restorePackage(snap)
                }, margins(top = 9))
            }
            ledgerHost.addView(item, margins(bottom = 8))
        }
    }

    private fun runTerminal() {
        val command = terminalInput.text.toString().trim()
        if (command.isEmpty()) return
        val risk = PremiumSafetyPolicy.classify(command)
        executeOperation("Terminal livre", command, risk, showDialog = false) { outcome, result ->
            terminalOutput.text = buildString {
                append(result.title).append('\n')
                append(result.detail).append("\n\n")
                val raw = (outcome.stdout + if (outcome.stderr.isNotBlank()) "\n${outcome.stderr}" else "").trim()
                if (raw.isNotBlank()) append(raw).append("\n\n")
                append("Detalhes técnicos: ").append(result.technical)
            }
        }
    }

    private fun cancelActiveOperation() {
        val task = activeTask ?: return
        adb.cancel(task)
        activeTask = null
        val result = OperationPresenter.cancelled("Operação interrompida pelo usuário")
        renderOperation(result)
        if (::terminalOutput.isInitialized && currentScreen == "terminal") terminalOutput.text = "${result.title}\n${result.detail}"
    }

    private fun runRecipeById(id: String) {
        recipes.firstOrNull { it.id == id }?.let(::runRecipe) ?: toast("Receita não encontrada: $id")
    }

    private fun runRecipe(recipe: PremiumRecipe) {
        executeOperation(recipe.name, recipe.command, recipe.risk, showDialog = false) { outcome, result ->
            val raw = combineRaw(outcome)
            if (result.success) {
                session?.let { File(it.directory, recipe.output).writeText(raw, Charsets.UTF_8) }
                val report = FunctionalActionEngine.analyze(recipe.id, raw)
                if (::diagnosticSummaryView.isInitialized) {
                    diagnosticSummaryView.text = report.summary
                    diagnosticRawView.text = raw.take(18000)
                    diagnosticRawView.visibility = View.GONE
                    renderDiagnosticActions(report)
                }
                showActionableResult(recipe, report, result, raw)
            } else {
                if (::diagnosticSummaryView.isInitialized) {
                    diagnosticSummaryView.text = "${result.title}: ${result.detail.take(260)}"
                    diagnosticRawView.text = raw.take(18000)
                    diagnosticRawView.visibility = View.GONE
                    diagnosticActionsHost.removeAllViews()
                    diagnosticActionsHost.addView(emptyState("A coleta não terminou", "Resolva a falha indicada antes de seguir para uma ação."))
                }
                showTechnicalResult(result, raw)
            }
        }
    }

    private fun renderDiagnosticActions(report: ActionableReport) {
        if (!::diagnosticActionsHost.isInitialized) return
        diagnosticActionsHost.removeAllViews()
        if (report.actions.isEmpty()) {
            diagnosticActionsHost.addView(emptyState("Nenhuma ação automática sugerida", "A evidência continua disponível para investigação manual."))
            return
        }
        report.actions.take(10).forEach { action ->
            diagnosticActionsHost.addView(functionalActionRow(action), margins(bottom = 8))
        }
    }

    private fun showActionableResult(recipe: PremiumRecipe, report: ActionableReport, result: HumanOperationResult, raw: String) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
            background = rounded(surface, 24, line)
        }
        panel.addView(text(report.title, 21f, success, true))
        panel.addView(text(report.summary, 12f, textSecondary, false), margins(top = 8))

        if (report.findings.isNotEmpty()) {
            panel.addView(text("O que foi encontrado", 11f, textPrimary, true), margins(top = 16))
            report.findings.take(8).forEach { finding ->
                panel.addView(text("• $finding", 11f, textSecondary, false), margins(top = 6))
            }
        }

        lateinit var dialog: AlertDialog
        if (report.actions.isNotEmpty()) {
            panel.addView(text("O que você pode fazer agora", 11f, textPrimary, true), margins(top = 18))
            report.actions.take(64).forEach { action ->
                val row = functionalActionRow(action) {
                    if (action.destination == ActionDestination.PACKAGE) {
                        performFunctionalAction(action)
                    } else {
                        dialog.dismiss()
                        performFunctionalAction(action)
                    }
                }
                panel.addView(row, margins(top = 8))
            }
        }

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(softButton(report.evidenceLabel) {
            dialog.dismiss()
            showTechnicalResult(result, raw)
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
        footer.addView(primaryButton("Concluir") { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        panel.addView(footer, margins(top = 16))
        dialog = premiumDialog(panel)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun functionalActionRow(action: FunctionalAction, overrideAction: (() -> Unit)? = null): View = card(Color.rgb(9, 17, 29)).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val body = LinearLayout(this@PremiumOpsActivity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text(action.label, 13f, textPrimary, true))
        body.addView(text(action.detail, 10f, textSecondary, false).apply { setPadding(0, dp(4), 0, 0); maxLines = 3 })
        addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(text("›", 22f, if (action.risk == "AMARELO") warning else cyan, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(34), dp(48)))
        setOnClickListener { overrideAction?.invoke() ?: performFunctionalAction(action) }
        pressFeedback(this)
    }

    private fun performFunctionalAction(action: FunctionalAction) {
        when (action.destination) {
            ActionDestination.PACKAGE -> openPackageContext(action.target)
            ActionDestination.APPS_FILTER -> openAppsFilter(action.target)
            ActionDestination.RECIPE -> runRecipeById(action.target)
            ActionDestination.SCREEN -> {
                if (screens.containsKey(action.target)) {
                    showSection(action.target)
                    if (action.target == "apps" && appPackages.isEmpty()) loadAppInventory()
                } else toast("Destino indisponível: ${action.target}")
            }
            ActionDestination.TERMINAL -> {
                showSection("terminal")
                terminalInput.setText(action.target)
            }
        }
    }

    private fun openPackageContext(packageNameRaw: String) {
        val pkg = sanitizePackage(packageNameRaw) ?: return
        appPackages.firstOrNull { it.packageName == pkg }?.let {
            showAppDetail(it)
            return
        }
        val command = "echo '__PATH__'; pm path $pkg 2>/dev/null; echo '__DISABLED__'; pm list packages -d 2>/dev/null | grep -Fx 'package:$pkg' || true; echo '__PID__'; pidof $pkg 2>/dev/null || true; echo '__DETAIL__'; dumpsys package $pkg 2>/dev/null | head -n 420"
        executeOperation("Preparar ${PackageIntelligence.friendlyName(pkg)}", command, "VERDE", showDialog = false) { outcome, result ->
            if (!result.success) {
                showTechnicalResult(result, combineRaw(outcome))
                return@executeOperation
            }
            val lines = outcome.stdout.lineSequence().map { it.trim() }.toList()
            val path = lines.firstOrNull { it.startsWith("package:") }?.removePrefix("package:").orEmpty()
            val disabled = lines.any { it == "package:$pkg" }
            val pidIndex = lines.indexOf("__PID__")
            val running = pidIndex >= 0 && lines.drop(pidIndex + 1).takeWhile { !it.startsWith("__") }.any { line -> line.any(Char::isDigit) }
            val kind = if (path.contains("/data/app/")) "Usuário" else "Sistema"
            val snapshot = PackageSnapshot(pkg, path, kind, disabled, running, metadata = outcome.stdout)
            appPackages.removeAll { it.packageName == pkg }
            appPackages.add(snapshot)
            showAppDetail(snapshot)
        }
    }

    private fun openAppsFilter(filter: String) {
        appFilter = filter
        showSection("apps")
        if (appPackages.isEmpty()) loadAppInventory() else refreshAppList()
    }

    private fun openPackageFromAction(packageNameRaw: String) {
        val pkg = sanitizePackage(packageNameRaw) ?: return
        appFilter = "Todos"
        showSection("apps")
        if (::appSearch.isInitialized) appSearch.setText(pkg)
        if (appPackages.isEmpty()) {
            loadAppInventory()
        } else {
            refreshAppList()
            appPackages.firstOrNull { it.packageName == pkg }?.let(::showAppDetail)
        }
    }

    private fun executeOperation(
        title: String,
        command: String,
        risk: String,
        showDialog: Boolean,
        callback: (RemoteShellOutcome, HumanOperationResult) -> Unit
    ) {
        if (risk == "VERMELHO") {
            val blocked = HumanOperationResult(OperationPhase.COMMAND_ERROR, "Ação bloqueada", "Operações estruturais/destrutivas não pertencem ao fluxo comum deste APK.", "risk=VERMELHO\n$command", false)
            renderOperation(blocked)
            showTechnicalResult(blocked, command)
            return
        }
        if (risk == "AMARELO") {
            AlertDialog.Builder(this)
                .setTitle("Confirmar ação reversível")
                .setMessage("$title\n\n${PremiumSafetyPolicy.explanation(risk)}\n\n$command")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Executar") { _, _ -> executeNow(title, command, risk, showDialog, callback) }
                .show()
        } else {
            executeNow(title, command, risk, showDialog, callback)
        }
    }

    private fun executeNow(
        title: String,
        command: String,
        risk: String,
        showDialog: Boolean,
        callback: (RemoteShellOutcome, HumanOperationResult) -> Unit
    ) {
        if (activeTask?.isDone == false) {
            toast("Já existe uma operação em andamento")
            return
        }
        val running = OperationPresenter.running(title)
        renderOperation(running)
        if (::terminalRunButton.isInitialized) terminalRunButton.isEnabled = false
        activeTask = adb.execute(command) { outcome ->
            val result = if (outcome.transportError != null) {
                OperationPresenter.transportError(title, outcome.transportError.message ?: outcome.transportError::class.java.simpleName, outcome.durationMs)
            } else {
                OperationPresenter.fromShell(title, outcome.stdout, outcome.stderr, outcome.exitCode, outcome.durationMs)
            }
            activeTask = null
            lastRawOutput = combineRaw(outcome)
            latestHumanResult = result
            renderOperation(result)
            appendExecution(title, command, risk, outcome)
            if (::terminalRunButton.isInitialized) terminalRunButton.isEnabled = true
            callback(outcome, result)
            if (showDialog) showTechnicalResult(result, lastRawOutput)
        }
    }

    private fun appendExecution(title: String, command: String, risk: String, outcome: RemoteShellOutcome) {
        val s = session ?: return
        val execution = PremiumExecution(System.currentTimeMillis(), title, command, outcome.stdout, outcome.stderr.ifBlank { outcome.transportError?.stackTraceToString().orEmpty() }, outcome.exitCode, risk, outcome.durationMs)
        s.executions += execution
        val file = File(s.directory, "terminal.txt")
        file.appendText("\n=== ${iso(execution.at)} | $title | $risk ===\n$ $command\n${outcome.stdout}${if (outcome.stderr.isNotBlank()) "\n[stderr]\n${outcome.stderr}" else ""}\n[exit=${outcome.exitCode} duration=${outcome.durationMs}ms]\n", Charsets.UTF_8)
        writeSessionMeta()
        refreshSessionSummary(); refreshTimeline()
    }

    private fun renderOperation(result: HumanOperationResult) {
        latestHumanResult = result
        if (!::operationBanner.isInitialized) return
        operationBanner.visibility = View.VISIBLE
        operationTitle.text = result.title
        operationDetail.text = result.detail.take(260)
        val color = when (result.phase) {
            OperationPhase.SUCCESS_WITH_OUTPUT, OperationPhase.SUCCESS_EMPTY -> success
            OperationPhase.COMMAND_ERROR, OperationPhase.TRANSPORT_ERROR -> danger
            OperationPhase.RUNNING, OperationPhase.QUEUED -> warning
            OperationPhase.CANCELLED -> warning
            else -> textSecondary
        }
        operationTitle.setTextColor(color)
        operationBanner.background = rounded(surface2, 16, color)
    }

    private fun showTechnicalResult(result: HumanOperationResult, raw: String) {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(12)); background = rounded(surface, 24, line) }
        panel.addView(text(result.title, 21f, if (result.success) success else if (result.phase == OperationPhase.RUNNING) warning else danger, true))
        panel.addView(text(result.detail.take(1200), 12f, textSecondary, false), margins(top = 8))
        if (raw.isNotBlank()) {
            panel.addView(text("Saída técnica", 11f, textPrimary, true), margins(top = 16))
            panel.addView(text(raw.take(18000), 10f, Color.rgb(185, 203, 224), false).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true); maxLines = 28; setPadding(dp(10), dp(10), dp(10), dp(10)); background = rounded(Color.rgb(3, 8, 15), 12, line) }, margins(top = 7))
        }
        panel.addView(text(result.technical, 10f, textMuted, false).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }, margins(top = 12))
        lateinit var dialog: AlertDialog
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(softButton("Copiar detalhes") { copyText("${result.title}\n${result.detail}\n\n$raw\n\n${result.technical}") }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
        actions.addView(primaryButton("Fechar") { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        panel.addView(actions, margins(top = 14))
        dialog = premiumDialog(panel)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun renderConnectionState(state: RemoteConnectionState) {
        if (!::statusView.isInitialized) return
        when (state) {
            RemoteConnectionState.Searching -> setConnectionUi("◌ PROCURANDO", "Descoberta ADB/mDNS", warning)
            is RemoteConnectionState.Connecting -> setConnectionUi("↻ CONECTANDO", state.endpoint, warning)
            is RemoteConnectionState.Connected -> setConnectionUi("● CONECTADA", "${state.host}:${state.port} · ${state.strategy}", success)
            is RemoteConnectionState.WaitingNetwork -> setConnectionUi("○ AGUARDANDO", state.reason, warning)
            is RemoteConnectionState.NeedsPairing -> setConnectionUi("! PAREAR", state.reason, warning)
            is RemoteConnectionState.Error -> setConnectionUi("× FALHA", state.reason, danger)
        }
    }

    private fun setConnectionUi(label: String, detail: String, color: Int) {
        statusView.text = label
        statusView.setTextColor(color)
        statusView.background = rounded(surface2, 999, color)
        statusDetailView.text = detail
    }

    private fun showConnectionDialog() {
        val host = input("IP da TayTech", adb.savedHost(), false)
        val port = input("Porta", adb.savedPort().toString(), true)
        val pairPort = input("Porta de pareamento", "", true)
        val pairCode = input("Código de pareamento", "", true)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(12)); background = rounded(surface, 24, line) }
        panel.addView(text("Conexão TayTech", 22f, textPrimary, true))
        panel.addView(text("O fluxo normal é automático. Use este painel somente quando quiser assumir controle manual.", 11f, textSecondary, false), margins(top = 6))
        panel.addView(host, margins(top = 16)); panel.addView(port, margins(top = 8))
        lateinit var dialog: AlertDialog
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(primaryButton("Conectar") {
            val h = host.text.toString().trim(); val p = port.text.toString().toIntOrNull()
            if (h.isBlank() || p == null) toast("Informe IP e porta") else { adb.connect(h, p, "manual"); dialog.dismiss() }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
        row.addView(softButton("Reconectar") { adb.autoReconnect(force = true); dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        panel.addView(row, margins(top = 10))
        panel.addView(softButton("Descobrir automaticamente") { adb.restartMdns(); dialog.dismiss() }, margins(top = 8))
        panel.addView(divider(), margins(top = 16, bottom = 16, height = 1))
        panel.addView(text("Parear por código", 15f, textPrimary, true))
        panel.addView(pairPort, margins(top = 8)); panel.addView(pairCode, margins(top = 8))
        panel.addView(primaryButton("Parear") {
            val h = host.text.toString().trim(); val p = pairPort.text.toString().toIntOrNull(); val code = pairCode.text.toString().trim()
            if (h.isBlank() || p == null || code.length < 6) toast("Informe IP, porta e código") else {
                adb.pair(h, p, code) { ok, message -> toast(message); if (ok) dialog.dismiss() }
            }
        }, margins(top = 10))
        panel.addView(softButton("Fechar") { dialog.dismiss() }, margins(top = 8))
        dialog = premiumDialog(panel)
        dialog.show(); dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun loadRecipes() {
        val array = JSONArray(assets.open("recipes.json").bufferedReader().use { it.readText() })
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            recipes += PremiumRecipe(o.getString("id"), o.getString("name"), o.getString("risk"), o.getString("command"), o.getString("output"))
        }
    }

    private fun showRecipeDetails(recipe: PremiumRecipe) {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(12)); background = rounded(surface, 24, line) }
        panel.addView(text(recipe.name, 20f, textPrimary, true))
        panel.addView(riskPill(recipe.risk), margins(top = 9))
        panel.addView(text(recipeDescription(recipe.id), 12f, textSecondary, false), margins(top = 10))
        panel.addView(text(recipe.command, 10f, Color.rgb(184, 202, 225), false).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(dp(10), dp(10), dp(10), dp(10)); background = rounded(Color.rgb(3, 8, 15), 12, line); maxLines = 16 }, margins(top = 12))
        lateinit var dialog: AlertDialog
        panel.addView(primaryButton("Executar") { dialog.dismiss(); runRecipe(recipe) }, margins(top = 14))
        panel.addView(softButton("Abrir no Terminal") { dialog.dismiss(); terminalInput.setText(recipe.command); showSection("terminal") }, margins(top = 8))
        panel.addView(softButton("Fechar") { dialog.dismiss() }, margins(top = 8))
        dialog = premiumDialog(panel); dialog.show(); dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun summarizeRecipe(id: String, outcome: RemoteShellOutcome, result: HumanOperationResult): String {
        if (!result.success) return "${result.title}: ${result.detail.take(180)}"
        val out = outcome.stdout
        return when (id) {
            "memoria-zram", "diagnostico-lentidao" -> {
                val total = Regex("MemTotal:\\s+(\\d+)").find(out)?.groupValues?.getOrNull(1)?.toLongOrNull()
                val available = Regex("MemAvailable:\\s+(\\d+)").find(out)?.groupValues?.getOrNull(1)?.toLongOrNull()
                if (total != null && available != null) "Memória disponível: ${available / 1024} MB de ${total / 1024} MB. Coleta concluída; detalhes técnicos preservados abaixo." else "Coleta de desempenho concluída. Veja a evidência técnica para memória, CPU e processos."
            }
            "thermal" -> "Leitura térmica concluída. Procure zonas com temperaturas anormais ou ThermalService sinalizando throttling."
            "rede-adb" -> "Diagnóstico de rede/ADB concluído. Endpoint atual: ${adb.endpointLabel()}."
            "fluidez-gfx" -> "Coleta de renderização concluída. SurfaceFlinger/gfxinfo foram preservados para análise de jank e composição."
            "ui-hierarchy" -> "Hierarquia da tela atual capturada temporariamente e o arquivo auxiliar foi removido da TayTech."
            else -> "${result.title}. ${out.lineSequence().firstOrNull { it.isNotBlank() }?.take(180) ?: "Nenhum texto adicional."}"
        }
    }

    private fun recipeDescription(id: String): String = when (id) {
        "estado-geral" -> "Identidade, build, memória e armazenamento em uma fotografia segura."
        "memoria-zram" -> "RAM, swap e ZRAM para entender pressão de memória."
        "processos" -> "CPU e processos que mais consomem recursos."
        "rede-adb" -> "Rede, Wi‑Fi, propriedades ADB e portas para instabilidade de conexão."
        "pacotes-servicos" -> "Inventário técnico de packages e serviços ativos."
        "logcat-curto" -> "Eventos recentes do Android para investigar comportamento anômalo."
        "snapshot-completo" -> "Evidence Pack técnico amplo para análise posterior."
        "thermal" -> "ThermalService e sensores térmicos expostos pelo kernel."
        "energia-power" -> "Bateria, PowerManager e device idle/wake."
        "armazenamento" -> "Uso de disco, mounts e serviços de armazenamento."
        "fluidez-gfx" -> "SurfaceFlinger, gfxinfo e janela atual para investigar engasgos."
        "atividade-atual" -> "Descobre qual app/janela está em primeiro plano e contexto de display."
        "apps-terceiros" -> "Lista packages instalados fora da imagem principal."
        "apps-desativados" -> "Lista packages atualmente desativados."
        "tela-config" -> "Resolução, densidade, brilho, rotação e timeout atuais."
        "animacoes-status" -> "Lê as três escalas de animação sem alterar nada."
        "audio-radio" -> "Rotas, foco e sessões de áudio/mídia."
        "bluetooth-status" -> "Estado Bluetooth e contexto de dispositivos/serviços."
        "boot-servicos" -> "Receivers de boot e serviços correntes para mapear persistência."
        "developer-state" -> "Configurações técnicas relevantes para personalização."
        "animacoes-off" -> "Define escalas de animação em 0x; reversível por Animações 1x."
        "animacoes-on" -> "Restaura escalas de animação padrão em 1x."
        "rotacao-auto-on" -> "Permite que o acelerômetro controle a rotação."
        "rotacao-auto-off" -> "Desativa rotação automática; reversível."
        "stayon-on" -> "Mantém a tela acordada durante alimentação; reversível."
        "stayon-off" -> "Restaura a política normal de suspensão durante alimentação."
        "ui-hierarchy" -> "Prática destilada de tooling ADB: captura a árvore UI atual para entender elementos e estados."
        "diagnostico-lentidao" -> "Workflow composto: memória + CPU + top + disco + thermal."
        "foreground-services" -> "Serviços persistentes/foreground para descobrir quem permanece ativo."
        "appops-auditoria" -> "AppOps e permissões especiais observadas pelo Android."
        "batterystats-apps" -> "Histórico de consumo e atividade por UID/package desde a última carga."
        "uso-apps" -> "UsageStats e atividade recente para entender o que realmente está sendo usado."
        "deviceidle-whitelist" -> "Apps liberados das restrições de Doze/device idle."
        "launchers-disponiveis" -> "Launchers HOME disponíveis e resolução do launcher atual."
        "webview-provider" -> "Provider WebView atual, versões válidas e estado de atualização."
        "localizacao-gnss" -> "Providers de localização/GNSS e estado observacional."
        "sensores-status" -> "Sensores registrados, clientes e eventos expostos pelo SensorService."
        "camera-status" -> "Câmeras, clientes e estado do serviço media.camera."
        "processos-oom" -> "Processos, importância/adj e estado do ActivityManager."
        "rede-netstats" -> "Estatísticas de rede por UID e interfaces para investigar tráfego."
        "device-policy" -> "Administradores, políticas e restrições gerenciadas do Android."
        "notificacoes-status" -> "Serviço de notificações, listeners e packages relacionados."
        "pacotes-instaladores" -> "Packages com caminho e origem/installer quando o Android expõe."
        "background-limits" -> "Limites globais de processos/cache e freezer de apps."
        "ethernet-status" -> "Estado da pilha Ethernet e interfaces cabeadas."
        "tempo-sistema" -> "Data, timezone e políticas automáticas de horário."
        else -> "Rotina versionada do CUSTOMROM."
    }

    private fun recipeCategory(id: String): String = when {
        id.startsWith("animacoes") || id.startsWith("rotacao") || id.startsWith("stayon") || id == "tela-config" -> "PERSONALIZAÇÃO"
        id.contains("app") || id.contains("pacote") || id.contains("boot") -> "APPS / SISTEMA"
        id.contains("rede") || id.contains("bluetooth") -> "CONECTIVIDADE"
        id.contains("thermal") || id.contains("memoria") || id.contains("process") || id.contains("fluidez") || id.contains("lentidao") -> "PERFORMANCE"
        else -> "DIAGNÓSTICO"
    }

    private fun toggleFavorite(id: String) {
        val current = (prefs.getStringSet("favorite_recipes", emptySet()) ?: emptySet()).toMutableSet()
        if (!current.add(id)) current.remove(id)
        prefs.edit().putStringSet("favorite_recipes", current).apply()
    }

    private fun startNewSession() {
        val id = utcId()
        val dir = File(filesDir, "sessions/$id").apply { mkdirs() }
        session = PremiumSession(id, System.currentTimeMillis(), dir)
        writeSessionMeta()
    }

    private fun writeSessionMeta() {
        val s = session ?: return
        val json = JSONObject().apply {
            put("schema", 2)
            put("sessionId", s.id)
            put("startedAt", iso(s.startedAt))
            put("target", "TayTech")
            put("endpoint", if (::adb.isInitialized) adb.endpointLabel() else "unknown")
            put("executionCount", s.executions.size)
            put("changeLedgerCount", ledger.list().size)
            put("executions", JSONArray().apply {
                s.executions.forEach { e -> put(JSONObject().apply { put("at", iso(e.at)); put("title", e.title); put("risk", e.risk); put("exitCode", e.exitCode); put("durationMs", e.durationMs); put("command", e.command) }) }
            })
        }
        File(s.directory, "manifest.json").writeText(json.toString(2), Charsets.UTF_8)
        File(s.directory, "changes.json").writeText(JSONArray(ledger.list().map { record -> JSONObject().apply { put("packageName", record.packageName); put("action", record.action); put("previousState", record.previousState); put("newState", record.newState); put("at", record.at); put("sessionId", record.sessionId); put("exitCode", record.exitCode); put("rollbackCommand", record.rollbackCommand) } }).toString(2), Charsets.UTF_8)
    }

    private fun refreshSessionSummary() {
        if (::sessionSummaryView.isInitialized) sessionSummaryView.text = sessionText()
    }

    private fun sessionText(): String {
        val s = session ?: return "Sem sessão"
        return "Sessão ${s.id} · ${s.executions.size} execuções · ${ledger.list().size} alterações registradas"
    }

    private fun refreshTimeline() {
        if (!::timelineHost.isInitialized) return
        timelineHost.removeAllViews()
        val executions = session?.executions.orEmpty().takeLast(15).reversed()
        if (executions.isEmpty()) {
            timelineHost.addView(emptyState("Nenhuma execução ainda", "Comandos, Terminal e Diagnóstico aparecerão aqui."))
            return
        }
        executions.forEach { e ->
            val item = card(Color.rgb(9, 17, 29)).apply { setPadding(dp(13), dp(11), dp(13), dp(11)) }
            val title = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            title.addView(text(e.title, 13f, textPrimary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            title.addView(riskPill(e.risk))
            item.addView(title)
            val human = OperationPresenter.fromShell(e.title, e.output, e.error, e.exitCode, e.durationMs)
            item.addView(text("${human.title} · ${e.durationMs} ms", 10f, if (human.success) success else danger, true).apply { setPadding(0, dp(5), 0, 0) })
            timelineHost.addView(item, margins(bottom = 8))
        }
    }

    private fun exportSession(share: Boolean) {
        val s = session ?: return
        writeSessionMeta()
        val zip = File(cacheDir, "CUSTOMROM_SESSION_${s.id}.zip")
        createZip(s.directory, zip)
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, zip.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CUSTOMROM")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("Não foi possível criar arquivo em Downloads")
            contentResolver.openOutputStream(uri)?.use { out -> zip.inputStream().use { it.copyTo(out) } } ?: throw IllegalStateException("Não foi possível abrir destino")
            if (share) {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Compartilhar Evidence Pack CUSTOMROM"))
            } else toast("Salvo em Downloads/CUSTOMROM")
        } catch (t: Throwable) {
            toast("Falha ao exportar: ${t.message ?: t::class.java.simpleName}")
        }
    }

    private fun createZip(source: File, target: File) {
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            source.walkTopDown().filter { it.isFile }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(source).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val checksums = source.walkTopDown().filter { it.isFile }.joinToString("\n") { "${sha256(it)}  ${it.relativeTo(source).invariantSeparatorsPath}" } + "\n"
            zip.putNextEntry(ZipEntry("checksums.sha256")); zip.write(checksums.toByteArray(StandardCharsets.UTF_8)); zip.closeEntry()
        }
    }

    private fun combineRaw(outcome: RemoteShellOutcome): String = buildString {
        if (outcome.stdout.isNotBlank()) append(outcome.stdout)
        if (outcome.stderr.isNotBlank()) { if (isNotEmpty() && !endsWith("\n")) append('\n'); append(outcome.stderr) }
        if (outcome.transportError != null) { if (isNotEmpty()) append('\n'); append(outcome.transportError.stackTraceToString()) }
    }

    private fun refreshTerminalRisk() {
        if (!::terminalInput.isInitialized || !::terminalRisk.isInitialized) return
        val risk = PremiumSafetyPolicy.classify(terminalInput.text.toString())
        terminalRisk.text = risk
        val color = when (risk) { "VERMELHO" -> danger; "AMARELO" -> warning; else -> success }
        terminalRisk.setTextColor(color); terminalRisk.background = rounded(surface3, 999, color)
    }

    private fun buildAppFilterText(): String = appFilter

    private fun filterChip(label: String, action: () -> Unit): TextView = text(label, 10f, if (buildAppFilterText() == label) cyan else textSecondary, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = rounded(if (buildAppFilterText() == label) cyanSoft else surface, 999, if (buildAppFilterText() == label) cyan else line)
        setOnClickListener { action(); }
        pressFeedback(this)
    }

    private fun criticalityPill(assessment: PackageAssessment): TextView {
        val color = when (assessment.criticality) {
            PackageCriticality.PROTECTED, PackageCriticality.HIGH -> danger
            PackageCriticality.MEDIUM, PackageCriticality.UNKNOWN -> warning
            PackageCriticality.LOW -> success
        }
        return pill("${assessment.criticality.label} · ${assessment.confidence.label}", color)
    }

    private fun riskPill(risk: String): TextView = pill(risk, when (risk) { "VERMELHO" -> danger; "AMARELO" -> warning; else -> success })

    private fun pill(value: String, color: Int): TextView = text(value, 9f, color, true).apply { gravity = Gravity.CENTER; setPadding(dp(9), dp(5), dp(9), dp(5)); background = rounded(surface3, 999, color) }

    private fun pageTitle(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(title, 27f, textPrimary, true))
        addView(text(subtitle, 12f, textSecondary, false).apply { setPadding(0, dp(4), 0, 0) })
    }

    private fun sectionTitle(title: String, detail: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(title, 16f, textPrimary, true))
        addView(text(detail, 10f, textMuted, false).apply { setPadding(0, dp(3), 0, 0) })
    }

    private fun featureAction(icon: String, title: String, detail: String, action: () -> Unit): View = card().apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(text(icon, 23f, cyan, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(10) })
        val body = LinearLayout(this@PremiumOpsActivity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text(title, 14f, textPrimary, true))
        body.addView(text(detail, 10f, textSecondary, false).apply { setPadding(0, dp(4), 0, 0) })
        addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(text("›", 24f, cyan, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(32), dp(48)))
        setOnClickListener { action() }; pressFeedback(this)
    }

    private fun infoStrip(title: String, detail: String, badge: String, action: () -> Unit): View = card().apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        val body = LinearLayout(this@PremiumOpsActivity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text(title, 13f, textPrimary, true)); body.addView(text(detail, 10f, textSecondary, false).apply { setPadding(0, dp(3), 0, 0) })
        addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(text(badge, 9f, cyan, true))
        setOnClickListener { action() }; pressFeedback(this)
    }

    private fun callout(title: String, detail: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(11), dp(12), dp(11)); background = rounded(Color.rgb(48, 28, 30), 14, danger)
        addView(text(title, 12f, danger, true)); addView(text(detail, 10f, Color.rgb(235, 190, 196), false).apply { setPadding(0, dp(4), 0, 0) })
    }

    private fun emptyState(title: String, detail: String): View = card(Color.rgb(8, 15, 26)).apply { addView(text(title, 14f, textPrimary, true)); addView(text(detail, 10f, textMuted, false).apply { setPadding(0, dp(5), 0, 0) }) }

    private fun compactAction(label: String, action: () -> Unit): TextView = text(label, 11f, textPrimary, true).apply { gravity = Gravity.CENTER; background = rounded(surface2, 16, line); setOnClickListener { action() }; pressFeedback(this) }

    private fun primaryButton(label: String, action: () -> Unit): TextView = text(label, 12f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = rounded(cyan, 15); setOnClickListener { action() }; pressFeedback(this) }
    private fun softButton(label: String, action: () -> Unit): TextView = text(label, 12f, textPrimary, true).apply { gravity = Gravity.CENTER; background = rounded(surface2, 15, line); setOnClickListener { action() }; pressFeedback(this) }
    private fun dangerButton(label: String, action: () -> Unit): TextView = text(label, 12f, danger, true).apply { gravity = Gravity.CENTER; background = rounded(Color.rgb(48, 24, 31), 15, danger); setOnClickListener { action() }; pressFeedback(this) }

    private fun input(hintText: String, initial: String, numeric: Boolean): EditText = EditText(this).apply {
        hint = hintText; setText(initial); setTextColor(textPrimary); setHintTextColor(textMuted); textSize = 13f; setSingleLine(!hintText.contains("Cole"));
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT); background = rounded(Color.rgb(3, 9, 17), 14, line); setPadding(dp(13), dp(11), dp(13), dp(11))
    }

    private fun card(color: Int = surface): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13)); background = rounded(color, 18, line) }

    private fun verticalScroll(): LinearLayout {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true; clipToPadding = false }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), 0) }
        scroll.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun premiumDialog(panel: View): AlertDialog {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        scroll.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return AlertDialog.Builder(this).setView(scroll).create().apply {
            setOnShowListener {
                val metrics = resources.displayMetrics
                window?.setLayout((metrics.widthPixels * 0.94f).toInt(), (metrics.heightPixels * 0.90f).toInt())
            }
        }
    }
    private fun divider(): View = View(this).apply { setBackgroundColor(line) }
    private fun space(height: Int): View = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun margins(top: Int = 0, bottom: Int = 0, height: Int? = null): LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height?.let(::dp) ?: ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun rounded(color: Int, radius: Int, strokeColor: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(color); cornerRadius = dp(radius).toFloat(); if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL); includeFontPadding = false
    }

    private fun pressFeedback(view: View) {
        view.isClickable = true; view.isFocusable = true
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.alpha = 0.72f; v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 1f
            }
            false
        }
    }

    private fun simpleWatcher(action: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = action()
        override fun afterTextChanged(s: Editable?) = Unit
    }

    private fun copyText(value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CUSTOMROM", value)); toast("Copiado")
    }

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun utcId(): String = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
    private fun iso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val n = input.read(buffer); if (n <= 0) break; md.update(buffer, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
