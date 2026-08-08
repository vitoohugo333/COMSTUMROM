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
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
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
import java.util.concurrent.Future
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PremiumMainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("customrom_adb", Context.MODE_PRIVATE) }

    private var kadb: Kadb? = null
    private var currentHost = ""
    private var currentPort = 0
    private var reconnecting = false
    private var commandFuture: Future<*>? = null
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val recipes = mutableListOf<PremiumRecipe>()
    private var session: PremiumSession? = null
    private var currentScreen = "commands"
    private var lastOutput = ""

    private lateinit var contentHost: FrameLayout
    private lateinit var statusView: TextView
    private lateinit var statusDetailView: TextView
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
    private lateinit var sessionSummaryView: TextView
    private lateinit var timelineHost: LinearLayout

    private val appPackages = mutableListOf<AppPackage>()
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

    data class AppPackage(
        val packageName: String,
        val kind: String,
        val disabled: Boolean
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
        commandFuture?.cancel(true)
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

        val statusColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
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
                val item = TextView(this@PremiumMainActivity).apply {
                    text = "$icon\n$label"
                    textSize = 10f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setTextColor(textMuted)
                    setPadding(dp(4), dp(5), dp(4), dp(5))
                    isClickable = true
                    isFocusable = true
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
            "sessions" -> { refreshSessionSummary(); refreshTimeline() }
        }
    }

    private fun buildCommandsScreen(): View {
        val root = verticalScroll()
        root.addView(pageTitle("Comandos", "Ações prontas para controlar e investigar a TayTech"))

        val connectionHint = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(surface, 18, line)
        }
        connectionHint.addView(text("TayTech", 13f, textPrimary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        connectionHint.addView(text("conexão automática", 11f, cyan, true))
        connectionHint.setOnClickListener { showConnectionDialog() }
        pressFeedback(connectionHint)
        root.addView(connectionHint, margins(top = 16))

        commandSearch = input("Buscar comando, categoria ou shell…", "", false).apply {
            addTextChangedListener(simpleWatcher { refreshCommandList() })
        }
        root.addView(commandSearch, margins(top = 14))

        val quick = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        quick.addView(softButton("Snapshot completo") {
            recipes.firstOrNull { it.id == "snapshot-completo" }?.let { runRecipe(it) }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
        quick.addView(softButton("Terminal livre") { showSection("terminal") }, LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(quick, margins(top = 12))

        root.addView(sectionTitle("Biblioteca", "Favoritos aparecem primeiro"), margins(top = 24))
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
        val favoriteIds = prefs.getStringSet("favorite_recipes", emptySet()) ?: emptySet()
        val filtered = recipes.filter { recipe ->
            query.isBlank() || recipe.name.lowercase(Locale.ROOT).contains(query) ||
                recipe.command.lowercase(Locale.ROOT).contains(query) || recipeDescription(recipe.id).lowercase(Locale.ROOT).contains(query)
        }.sortedWith(compareByDescending<PremiumRecipe> { favoriteIds.contains(it.id) }.thenBy { it.name })

        if (filtered.isEmpty()) {
            commandListHost.addView(emptyState("Nenhum comando encontrado", "Tente outro termo de busca."))
            return
        }
        filtered.forEach { recipe -> commandListHost.addView(commandRow(recipe, favoriteIds.contains(recipe.id)), margins(bottom = 8)) }
    }

    private fun commandRow(recipe: PremiumRecipe, favorite: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(10), dp(12))
            background = rounded(surface, 18, line)
        }
        val play = TextView(this).apply {
            text = "▶"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(bg)
            background = rounded(if (recipe.risk == "VERDE") cyan else warning, 999)
            setOnClickListener { runRecipe(recipe) }
            pressFeedback(this)
        }
        row.addView(play, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) })

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text(recipe.name, 15f, textPrimary, true))
        body.addView(text(recipeDescription(recipe.id), 11f, textSecondary, false).apply {
            setPadding(0, dp(4), 0, 0)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        val meta = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        meta.addView(riskPill(recipe.risk))
        meta.addView(text("  ${recipe.output}", 9f, textMuted, false).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(meta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7) })
        row.addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val star = TextView(this).apply {
            text = if (favorite) "★" else "☆"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(if (favorite) cyan else textMuted)
            setOnClickListener {
                toggleFavorite(recipe.id)
                refreshCommandList()
            }
            pressFeedback(this)
        }
        row.addView(star, LinearLayout.LayoutParams(dp(44), dp(48)))
        row.setOnClickListener { showCommandDetails(recipe) }
        pressFeedback(row)
        return row
    }

    private fun showCommandDetails(recipe: PremiumRecipe) {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(8))
            background = rounded(surface, 22, line)
        }
        body.addView(text(recipe.name, 20f, textPrimary, true))
        body.addView(riskPill(recipe.risk), margins(top = 10))
        body.addView(text(recipeDescription(recipe.id), 13f, textSecondary, false), margins(top = 12))
        body.addView(text(recipe.command, 11f, Color.rgb(184, 202, 225), false).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.rgb(4, 9, 16), 14, line)
        }, margins(top = 14))
        lateinit var dialog: AlertDialog
        body.addView(primaryButton("Executar agora") { dialog.dismiss(); runRecipe(recipe) }, margins(top = 16))
        body.addView(softButton("Abrir no Terminal") {
            dialog.dismiss()
            terminalInput.setText(recipe.command)
            showSection("terminal")
        }, margins(top = 8))
        dialog = premiumDialog(body)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun toggleFavorite(id: String) {
        val current = (prefs.getStringSet("favorite_recipes", emptySet()) ?: emptySet()).toMutableSet()
        if (!current.add(id)) current.remove(id)
        prefs.edit().putStringSet("favorite_recipes", current).apply()
    }

    private fun buildTerminalScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(bg)
        }
        root.addView(pageTitle("Terminal", "Shell remoto completo · conteúdo preservado ao navegar"))

        val console = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(3, 8, 15), 18, line)
        }
        val consoleHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        consoleHead.addView(text("CONSOLE", 10f, textMuted, true).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        consoleHead.addView(softIconButton("Copiar") { copyText(terminalOutput.text.toString()) })
        consoleHead.addView(softIconButton("Limpar") { terminalOutput.text = "" }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { leftMargin = dp(6) })
        console.addView(consoleHead)

        terminalOutput = text("Aguardando comando.\nA conexão é recuperada automaticamente quando possível.", 12f, Color.rgb(197, 213, 232), false).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(12), 0, dp(12))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(terminalOutput, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        console.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(console, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(14) })

        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(surface, 18, line)
        }
        val editorHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        editorHead.addView(text("COMANDO / BLOCO", 9f, textMuted, true).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        terminalRisk = riskPill("VERDE")
        editorHead.addView(terminalRisk)
        editor.addView(editorHead)

        terminalInput = EditText(this).apply {
            setText("getprop ro.product.model")
            hint = "Digite ou cole um bloco ADB shell…"
            setHintTextColor(textMuted)
            setTextColor(textPrimary)
            textSize = 13f
            minLines = 3
            maxLines = 6
            gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            background = rounded(Color.rgb(4, 9, 16), 14, line)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addTextChangedListener(simpleWatcher { refreshTerminalRisk() })
        }
        editor.addView(terminalInput, margins(top = 8))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actions.addView(softButton("Interromper") { interruptCommand() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
        terminalRunButton = primaryButton("Executar") { executeTerminalCommand() }
        actions.addView(terminalRunButton, LinearLayout.LayoutParams(0, dp(46), 1.5f))
        editor.addView(actions, margins(top = 8))
        root.addView(editor, margins(top = 10))
        return root
    }

    private fun executeTerminalCommand() {
        val command = terminalInput.text.toString().trim()
        if (command.isEmpty()) return
        val risk = PremiumSafetyPolicy.classify(command)
        if (risk == "VERMELHO") {
            showBlockedRedAction(command)
            return
        }
        if (risk == "AMARELO") {
            confirmYellow("Executar comando reversível?", command) { runCommand("Terminal livre", command, risk, null, true) }
        } else {
            runCommand("Terminal livre", command, risk, null, true)
        }
    }

    private fun refreshTerminalRisk() {
        if (!::terminalInput.isInitialized || !::terminalRisk.isInitialized) return
        styleRiskPill(terminalRisk, PremiumSafetyPolicy.classify(terminalInput.text.toString()))
    }

    private fun interruptCommand() {
        val active = commandFuture
        if (active == null || active.isDone) {
            toast("Nenhum comando longo em execução")
            return
        }
        active.cancel(true)
        runCatching { kadb?.resetConnection() }
        terminalOutput.append("\n\n[interrompido pelo usuário — transporte será reconectado]\n")
        setConnectionState("RECONNECTING", "Interrompendo e recuperando sessão")
        autoReconnect(force = true)
    }

    private fun buildAppsScreen(): View {
        val root = verticalScroll()
        root.addView(pageTitle("Aplicativos", "Gerencie packages sem decorar comandos pm"))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(surface, 18, line)
        }
        appStatusView = text("Inventário ainda não carregado", 13f, textPrimary, true)
        hero.addView(appStatusView)
        hero.addView(text("Pacotes automotivos sensíveis são protegidos por presunção e recebem apenas análise no fluxo comum.", 11f, textSecondary, false), margins(top = 5))
        hero.addView(primaryButton("Carregar / atualizar inventário") { loadPackageInventory() }, margins(top = 12))
        root.addView(hero, margins(top = 16))

        appSearch = input("Buscar por package…", "", false).apply { addTextChangedListener(simpleWatcher { refreshAppList() }) }
        root.addView(appSearch, margins(top = 12))

        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        listOf("Todos", "Usuário", "Sistema", "Protegidos").forEachIndexed { index, filter ->
            val b = miniFilterButton(filter) { appFilter = filter; refreshAppList() }
            filters.addView(b, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (index > 0) leftMargin = dp(6) })
        }
        root.addView(filters, margins(top = 10))

        appListHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(appListHost, margins(top = 12))
        root.addView(space(24))
        refreshAppList()
        return root.parent as ScrollView
    }

    private fun loadPackageInventory() {
        val command = "echo __USER__; pm list packages -3; echo __SYSTEM__; pm list packages -s; echo __DISABLED__; pm list packages -d"
        appStatusView.text = "Lendo pacotes da TayTech…"
        runCommand("Inventário de aplicativos", command, "VERDE", null, false) { execution ->
            parsePackageInventory(execution.output)
            appStatusView.text = "${appPackages.size} pacotes · toque em um item para analisar ou agir"
            refreshAppList()
        }
    }

    private fun parsePackageInventory(raw: String) {
        val user = linkedSetOf<String>()
        val system = linkedSetOf<String>()
        val disabled = linkedSetOf<String>()
        var section = ""
        raw.lineSequence().forEach { lineRaw ->
            val line = lineRaw.trim()
            when (line) {
                "__USER__" -> section = "Usuário"
                "__SYSTEM__" -> section = "Sistema"
                "__DISABLED__" -> section = "Desabilitado"
                else -> if (line.startsWith("package:")) {
                    val pkg = line.removePrefix("package:").substringAfterLast('=').trim()
                    when (section) {
                        "Usuário" -> user += pkg
                        "Sistema" -> system += pkg
                        "Desabilitado" -> disabled += pkg
                    }
                }
            }
        }
        appPackages.clear()
        user.forEach { appPackages += AppPackage(it, "Usuário", disabled.contains(it)) }
        system.filterNot { user.contains(it) }.forEach { appPackages += AppPackage(it, "Sistema", disabled.contains(it)) }
        appPackages.sortBy { it.packageName }
    }

    private fun refreshAppList() {
        if (!::appListHost.isInitialized) return
        appListHost.removeAllViews()
        if (appPackages.isEmpty()) {
            appListHost.addView(emptyState("Nenhum inventário carregado", "Toque em “Carregar / atualizar inventário”. Nenhuma alteração é feita nesta leitura."))
            return
        }
        val query = if (::appSearch.isInitialized) appSearch.text.toString().trim().lowercase(Locale.ROOT) else ""
        val items = appPackages.filter { app ->
            val protected = PremiumSafetyPolicy.isProtectedPackage(app.packageName)
            val filterOk = when (appFilter) {
                "Usuário" -> app.kind == "Usuário"
                "Sistema" -> app.kind == "Sistema"
                "Protegidos" -> protected
                else -> true
            }
            filterOk && (query.isBlank() || app.packageName.lowercase(Locale.ROOT).contains(query))
        }
        if (items.isEmpty()) {
            appListHost.addView(emptyState("Nenhum aplicativo neste filtro", "Altere o filtro ou a busca."))
            return
        }
        items.forEach { app -> appListHost.addView(appRow(app), margins(bottom = 7)) }
    }

    private fun appRow(app: AppPackage): View {
        val protected = PremiumSafetyPolicy.isProtectedPackage(app.packageName)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = rounded(surface, 17, if (protected) warning else line)
        }
        val icon = TextView(this).apply {
            text = if (protected) "◆" else "▦"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(if (protected) warning else cyan)
            background = rounded(if (protected) Color.rgb(58, 43, 16) else cyanSoft, 13)
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(11) })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text(app.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }, 14f, textPrimary, true))
        body.addView(text(app.packageName, 10f, textSecondary, false).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE; setPadding(0, dp(3), 0, 0) })
        val state = when {
            protected -> "PROTEGIDO · ${app.kind}"
            app.disabled -> "DESATIVADO · ${app.kind}"
            else -> app.kind
        }
        body.addView(text(state, 9f, if (protected) warning else if (app.disabled) danger else textMuted, true).apply { setPadding(0, dp(5), 0, 0) })
        row.addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("›", 27f, textMuted, false), LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.setOnClickListener { showPackageActions(app) }
        pressFeedback(row)
        return row
    }

    private fun showPackageActions(app: AppPackage) {
        val protected = PremiumSafetyPolicy.isProtectedPackage(app.packageName)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(10))
            background = rounded(surface, 22, if (protected) warning else line)
        }
        panel.addView(text(app.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }, 20f, textPrimary, true))
        panel.addView(text(app.packageName, 11f, textSecondary, false).apply { setTextIsSelectable(true) }, margins(top = 5))
        if (protected) {
            panel.addView(text("PROTEGIDO POR PRESUNÇÃO", 10f, warning, true), margins(top = 12))
            panel.addView(text("O nome sugere integração automotiva sensível. No fluxo comum o CUSTOMROM permite investigar, mas não oferece parar/desativar este pacote.", 12f, textSecondary, false), margins(top = 6))
        } else {
            panel.addView(text("${app.kind} · ${if (app.disabled) "desativado" else "habilitado"}", 11f, textSecondary, false), margins(top = 10))
        }

        lateinit var dialog: AlertDialog
        panel.addView(primaryButton("Analisar pacote") {
            dialog.dismiss()
            analyzePackage(app.packageName)
        }, margins(top = 16))

        if (!protected) {
            panel.addView(softButton("Parar temporariamente") {
                dialog.dismiss()
                confirmYellow("Parar ${app.packageName.substringAfterLast('.')}?", "am force-stop ${app.packageName}") {
                    runCommand("Force-stop ${app.packageName}", "am force-stop ${app.packageName}", "AMARELO", null, false)
                }
            }, margins(top = 8))

            if (app.disabled) {
                panel.addView(softButton("Restaurar aplicativo") {
                    dialog.dismiss()
                    confirmYellow("Restaurar aplicativo?", "pm enable ${app.packageName}") {
                        runCommand("Restaurar ${app.packageName}", "pm enable ${app.packageName}", "AMARELO", null, false) { loadPackageInventory() }
                    }
                }, margins(top = 8))
            } else {
                panel.addView(dangerOutlineButton("Desativar para o usuário") {
                    dialog.dismiss()
                    confirmYellow("Desativar aplicativo?", "pm disable-user --user 0 ${app.packageName}") {
                        runCommand("Desativar ${app.packageName}", "pm disable-user --user 0 ${app.packageName}", "AMARELO", null, false) { loadPackageInventory() }
                    }
                }, margins(top = 8))
            }
        }
        panel.addView(softButton("Fechar") { dialog.dismiss() }, margins(top = 8))
        dialog = premiumDialog(panel)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun analyzePackage(packageName: String) {
        val command = "echo '=== PACKAGE ==='; dumpsys package $packageName; echo; echo '=== MEMORY ==='; dumpsys meminfo $packageName 2>/dev/null; echo; echo '=== PROCESS ==='; ps -A | grep '$packageName' 2>/dev/null"
        showSection("diagnostics")
        runCommand("Analisar $packageName", command, "VERDE", null, false) { execution -> showDiagnosticResult(execution) }
    }

    private fun buildDiagnosticsScreen(): View {
        val root = verticalScroll()
        root.addView(pageTitle("Diagnóstico", "Entenda o estado da TayTech sem decorar shell"))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Color.rgb(5, 40, 59), 20, cyan)
        }
        hero.addView(text("Snapshot para análise", 18f, textPrimary, true))
        hero.addView(text("Identidade, memória, CPU, processos, disco e packages em uma coleta reproduzível.", 12f, Color.rgb(188, 224, 240), false), margins(top = 5))
        hero.addView(primaryButton("Executar snapshot completo") {
            recipes.firstOrNull { it.id == "snapshot-completo" }?.let { recipe ->
                runCommand(recipe.name, recipe.command, recipe.risk, recipe.output, false) { showDiagnosticResult(it) }
            }
        }, margins(top = 13))
        root.addView(hero, margins(top = 16))

        root.addView(sectionTitle("Coletas rápidas", "Somente leitura por padrão"), margins(top = 22))
        recipes.filter { it.id != "snapshot-completo" }.forEach { recipe ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(11), dp(12), dp(11))
                background = rounded(surface, 17, line)
            }
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            body.addView(text(recipe.name, 14f, textPrimary, true))
            body.addView(text(recipeDescription(recipe.id), 10f, textSecondary, false).apply { setPadding(0, dp(3), 0, 0) })
            row.addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text("Executar", 11f, cyan, true).apply { gravity = Gravity.CENTER })
            row.setOnClickListener {
                runCommand(recipe.name, recipe.command, recipe.risk, recipe.output, false) { showDiagnosticResult(it) }
            }
            pressFeedback(row)
            root.addView(row, margins(top = 7))
        }

        root.addView(sectionTitle("Resultado", "Resumo primeiro, evidência bruta disponível"), margins(top = 24))
        diagnosticSummaryView = text("Nenhuma coleta executada nesta tela ainda.", 14f, textPrimary, true).apply {
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded(surface, 17, line)
        }
        root.addView(diagnosticSummaryView, margins(top = 8))
        diagnosticRawView = text("", 11f, Color.rgb(190, 207, 227), false).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(3, 8, 15), 16, line)
        }
        root.addView(diagnosticRawView, margins(top = 8))
        val resultActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        resultActions.addView(softButton("Copiar") { copyText(diagnosticRawView.text.toString()) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        resultActions.addView(primaryButton("Compartilhar") { shareText("Diagnóstico CUSTOMROM", diagnosticRawView.text.toString()) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(resultActions, margins(top = 8))
        root.addView(space(24))
        return root.parent as ScrollView
    }

    private fun showDiagnosticResult(execution: PremiumExecution) {
        if (::diagnosticRawView.isInitialized) {
            diagnosticRawView.text = execution.output.ifBlank { execution.error }
            diagnosticSummaryView.text = diagnosticSummary(execution)
        }
    }

    private fun diagnosticSummary(execution: PremiumExecution): String {
        if (execution.exitCode != 0) return "${execution.title}\nConcluído com exit=${execution.exitCode}. Consulte a saída técnica antes de concluir qualquer diagnóstico."
        val raw = execution.output
        val totalKb = Regex("MemTotal:\\s+(\\d+)\\s+kB").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()
        val availableKb = Regex("MemAvailable:\\s+(\\d+)\\s+kB").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (totalKb != null && availableKb != null) {
            val totalGb = totalKb / 1024.0 / 1024.0
            val availableGb = availableKb / 1024.0 / 1024.0
            return "${execution.title}\n${"%.1f".format(Locale.US, availableGb)} GB disponíveis de ${"%.1f".format(Locale.US, totalGb)} GB. Coleta concluída em ${execution.durationMs} ms. A saída técnica continua sendo a evidência autoritativa."
        }
        return "${execution.title}\nColeta concluída · exit=${execution.exitCode} · ${raw.length} caracteres · ${execution.durationMs} ms. Leia a evidência bruta antes de inferir causa."
    }

    private fun buildSessionsScreen(): View {
        val root = verticalScroll()
        root.addView(pageTitle("Sessões", "Uma investigação inteira vira um Evidence Pack compartilhável"))

        sessionSummaryView = text(sessionSummary(), 14f, textPrimary, true).apply {
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(surface, 18, line)
        }
        root.addView(sessionSummaryView, margins(top = 16))

        val actionsTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionsTop.addView(softButton("Nova sessão") { startNewSession(); refreshSessionSummary(); refreshTimeline() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
        actionsTop.addView(primaryButton("Compartilhar ZIP") { exportSession(true) }, LinearLayout.LayoutParams(0, dp(46), 1.2f))
        root.addView(actionsTop, margins(top = 10))
        root.addView(softButton("Salvar ZIP em Downloads/CUSTOMROM") { exportSession(false) }, margins(top = 8))

        root.addView(sectionTitle("Linha do tempo", "Últimas execuções da sessão atual"), margins(top = 24))
        timelineHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(timelineHost, margins(top = 8))
        root.addView(space(24))
        refreshTimeline()
        return root.parent as ScrollView
    }

    private fun runRecipe(recipe: PremiumRecipe) {
        if (recipe.risk == "VERMELHO") {
            showBlockedRedAction(recipe.command)
            return
        }
        val action = {
            runCommand(recipe.name, recipe.command, recipe.risk, recipe.output, false) { execution ->
                if (currentScreen == "diagnostics") showDiagnosticResult(execution)
                toast("${recipe.name} · exit=${execution.exitCode}")
            }
        }
        if (recipe.risk == "AMARELO") confirmYellow("Executar ação reversível?", recipe.command, action) else action()
    }

    private fun runCommand(
        title: String,
        command: String,
        risk: String,
        outputFile: String?,
        showInTerminal: Boolean,
        onResult: ((PremiumExecution) -> Unit)? = null
    ) {
        val connection = kadb
        if (connection == null || connection.connectionCheck() != true) {
            toast("Reconectando a TayTech…")
            autoReconnect(force = true)
            if (showInTerminal && ::terminalOutput.isInitialized) terminalOutput.text = "Aguardando reconexão. Toque em Executar novamente quando o status ficar conectado."
            return
        }
        if (showInTerminal && ::terminalOutput.isInitialized) terminalOutput.text = "Executando $title…"
        setBusy(true)
        commandFuture = executor.submit {
            val started = System.currentTimeMillis()
            try {
                val response = connection.shell(command)
                val duration = System.currentTimeMillis() - started
                val all = buildString {
                    if (response.output.isNotEmpty()) append(response.output)
                    if (response.errorOutput.isNotEmpty()) {
                        if (isNotEmpty() && !endsWith("\n")) append('\n')
                        append(response.errorOutput)
                    }
                }
                val execution = PremiumExecution(started, title, command, response.output, response.errorOutput, response.exitCode, risk, duration)
                session?.executions?.add(execution)
                appendSessionTerminal(execution)
                outputFile?.let { fileName -> session?.directory?.let { File(it, fileName).writeText(all, Charsets.UTF_8) } }
                lastOutput = all
                mainHandler.post {
                    setBusy(false)
                    if (showInTerminal && ::terminalOutput.isInitialized) terminalOutput.text = "[$risk] $title · exit=${response.exitCode} · ${duration}ms\n\n$all"
                    refreshSessionSummary()
                    refreshTimeline()
                    onResult?.invoke(execution)
                }
            } catch (t: Throwable) {
                val duration = System.currentTimeMillis() - started
                session?.transportErrors = (session?.transportErrors ?: 0) + 1
                val error = t.stackTraceToString()
                val execution = PremiumExecution(started, title, command, "", error, -1, risk, duration)
                session?.executions?.add(execution)
                appendSessionTerminal(execution)
                runCatching { connection.resetConnection() }
                mainHandler.post {
                    setBusy(false)
                    if (::terminalOutput.isInitialized && showInTerminal) terminalOutput.text = "ERRO DE TRANSPORTE\n\n${shortError(t)}"
                    setConnectionState("RECONNECTING", "Sessão caiu · recuperando")
                    refreshSessionSummary()
                    refreshTimeline()
                    onResult?.invoke(execution)
                    autoReconnect(force = true)
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        if (::terminalRunButton.isInitialized) {
            terminalRunButton.text = if (busy) "Executando…" else "Executar"
            terminalRunButton.isEnabled = !busy
            terminalRunButton.alpha = if (busy) 0.55f else 1f
        }
    }

    private fun showBlockedRedAction(command: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Ação VERMELHA bloqueada")
            .setMessage("${PremiumSafetyPolicy.explanation("VERMELHO")}\n\n$command")
            .setPositiveButton("Entendi", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(danger)
    }

    private fun confirmYellow(title: String, command: String, action: () -> Unit) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("${PremiumSafetyPolicy.explanation("AMARELO")}\n\n$command")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ -> action() }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(textSecondary)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(warning)
    }

    private fun showConnectionDialog() {
        val host = input("IP da TayTech", prefs.getString("host", "") ?: "", false)
        val port = input("Porta de conexão", prefs.getInt("port", 5555).toString(), true)
        val pairingPort = input("Porta de pareamento", "", true)
        val pairingCode = input("Código de pareamento", "", true)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(10))
            background = rounded(surface, 22, line)
        }
        panel.addView(text("Conexão TayTech", 21f, textPrimary, true))
        panel.addView(text("No uso normal o CUSTOMROM reconecta sozinho. Abra este painel apenas quando precisar assumir o controle manual.", 12f, textSecondary, false), margins(top = 5))
        panel.addView(text(connectionDetail(), 11f, cyan, true), margins(top = 12))
        panel.addView(host, margins(top = 14))
        panel.addView(port, margins(top = 8))

        lateinit var dialog: AlertDialog
        val connectRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        connectRow.addView(primaryButton("Conectar") {
            val h = host.text.toString().trim(); val p = port.text.toString().toIntOrNull()
            if (h.isBlank() || p == null) toast("Informe IP e porta") else { dialog.dismiss(); connect(h, p, "manual") }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
        connectRow.addView(softButton("Descobrir") { dialog.dismiss(); restartMdns() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        panel.addView(connectRow, margins(top = 10))
        panel.addView(softButton("Tentar IP salvo em :5555") {
            val h = host.text.toString().trim()
            if (h.isBlank()) toast("Informe o IP") else { dialog.dismiss(); connect(h, 5555, "tcp-5555") }
        }, margins(top = 8))

        panel.addView(divider(), margins(top = 16, bottom = 14, height = 1))
        panel.addView(text("Parear quando necessário", 15f, textPrimary, true))
        panel.addView(text("Depois do pairing, a identidade ADB é preservada e o app tenta reencontrar a TayTech automaticamente.", 11f, textSecondary, false), margins(top = 4))
        panel.addView(pairingPort, margins(top = 10))
        panel.addView(pairingCode, margins(top = 8))
        panel.addView(primaryButton("Parear dispositivo") {
            val h = host.text.toString().trim(); val p = pairingPort.text.toString().toIntOrNull(); val code = pairingCode.text.toString().trim()
            if (h.isBlank() || p == null || code.length < 6) toast("Informe IP, porta e código") else { dialog.dismiss(); pairDevice(h, p, code) }
        }, margins(top = 10))
        panel.addView(softButton("Fechar") { dialog.dismiss() }, margins(top = 8))
        dialog = premiumDialog(panel)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun restoreTarget() {
        val host = prefs.getString("host", "") ?: ""
        val port = prefs.getInt("port", 5555)
        currentHost = host
        currentPort = if (host.isBlank()) 0 else port
        if (host.isBlank()) setConnectionState("SEARCHING", "Nenhum alvo salvo") else setConnectionState("SEARCHING", "$host:$port · aguardando")
    }

    private fun connect(host: String, port: Int, strategy: String, silent: Boolean = false) {
        if (reconnecting) return
        reconnecting = true
        setConnectionState("CONNECTING", "$host:$port")
        executor.execute {
            try {
                val candidate = Kadb.tryConnection(host, port) ?: throw IllegalStateException("ADB não respondeu como dispositivo autenticado")
                runCatching { kadb?.close() }
                kadb = candidate
                currentHost = host
                currentPort = port
                prefs.edit().putString("host", host).putInt("port", port).putString("strategy", strategy).putBoolean("autoReconnect", true).apply()
                mainHandler.post {
                    reconnecting = false
                    setConnectionState("CONNECTED", "$host:$port · $strategy")
                }
            } catch (t: Throwable) {
                session?.transportErrors = (session?.transportErrors ?: 0) + 1
                mainHandler.post {
                    reconnecting = false
                    setConnectionState("ERROR", "$host:$port · ${shortError(t)}")
                    refreshSessionSummary()
                    if (!silent) toast(shortError(t))
                }
            }
        }
    }

    private fun autoReconnect(force: Boolean = false) {
        if (reconnecting) return
        if (!force && kadb?.connectionCheck() == true) {
            setConnectionState("CONNECTED", connectionDetail())
            return
        }
        val host = (prefs.getString("host", "") ?: "").trim()
        val savedPort = prefs.getInt("port", 5555)
        if (host.isEmpty()) {
            setConnectionState("SEARCHING", "Procurando ADB por mDNS")
            restartMdns()
            return
        }
        val candidates = linkedSetOf(5555, savedPort)
        reconnecting = true
        setConnectionState("RECONNECTING", "Tentando alvo conhecido")
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
                    setConnectionState("CONNECTED", "$host:$currentPort · ${successResult!!.third}")
                    refreshSessionSummary()
                } else {
                    setConnectionState("SEARCHING", "Endpoint salvo indisponível · procurando mDNS")
                    restartMdns()
                }
            }
        }
    }

    private fun pairDevice(host: String, port: Int, code: String) {
        setConnectionState("PAIRING", "$host:$port")
        executor.execute {
            try {
                runBlocking { Kadb.pair(host, port, code, "CUSTOMROM ADB") }
                prefs.edit().putString("host", host).putBoolean("paired", true).apply()
                mainHandler.post {
                    setConnectionState("SEARCHING", "Pareado · procurando endpoint de conexão")
                    restartMdns()
                }
            } catch (t: Throwable) {
                mainHandler.post { setConnectionState("ERROR", "Pairing · ${shortError(t)}") }
            }
        }
    }

    private fun startMdnsDiscovery() {
        if (discoveryListener != null) return
        val manager = getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager = manager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { setConnectionState("SEARCHING", "Procurando ADB na rede") }
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
                            currentHost = host
                            currentPort = port
                            if (kadb?.connectionCheck() != true && !reconnecting) connect(host, port, "mdns", silent = true)
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (kadb?.connectionCheck() != true) setConnectionState("SEARCHING", "ADB mDNS saiu da rede")
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
                discoveryListener = null
                setConnectionState("ERROR", "mDNS indisponível · código $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
                discoveryListener = null
            }
        }
        discoveryListener = listener
        runCatching { manager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { discoveryListener = null; setConnectionState("ERROR", "Falha ao iniciar descoberta") }
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

    private fun setConnectionState(state: String, detail: String) {
        val update = {
            val (label, color) = when (state) {
                "CONNECTED" -> "● CONECTADA" to success
                "CONNECTING" -> "◌ CONECTANDO" to warning
                "RECONNECTING" -> "↻ RECONECTANDO" to warning
                "PAIRING" -> "◌ PAREANDO" to warning
                "ERROR" -> "× FALHA" to danger
                else -> "◌ PROCURANDO" to warning
            }
            if (::statusView.isInitialized) {
                statusView.text = label
                statusView.setTextColor(color)
                statusView.background = rounded(surface2, 999, color)
            }
            if (::statusDetailView.isInitialized) statusDetailView.text = detail
        }
        if (Looper.myLooper() == Looper.getMainLooper()) update() else mainHandler.post(update)
    }

    private fun connectionDetail(): String {
        val h = currentHost.ifBlank { prefs.getString("host", "") ?: "" }
        val p = if (currentPort > 0) currentPort else prefs.getInt("port", 5555)
        return if (h.isBlank()) "Sem endpoint confirmado" else "$h:$p · ${prefs.getString("strategy", "auto")}" 
    }

    private fun loadRecipes() {
        val json = assets.open("recipes.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            recipes += PremiumRecipe(o.getString("id"), o.getString("name"), o.getString("risk"), o.getString("command"), o.getString("output"))
        }
    }

    private fun startNewSession() {
        val id = utcId()
        val dir = File(filesDir, "sessions/$id").apply { mkdirs() }
        session = PremiumSession(id, System.currentTimeMillis(), dir)
        writeSessionMeta()
    }

    private fun appendSessionTerminal(execution: PremiumExecution) {
        val s = session ?: return
        File(s.directory, "terminal.txt").appendText(
            "\n=== ${iso(execution.at)} | ${execution.title} | ${execution.risk} | ${execution.durationMs}ms ===\n" +
                "$ ${execution.command}\n" + execution.output +
                (if (execution.error.isNotBlank()) "\n[stderr/error]\n${execution.error}" else "") +
                "\n[exit=${execution.exitCode}]\n",
            Charsets.UTF_8
        )
        writeSessionMeta()
    }

    private fun writeSessionMeta() {
        val s = session ?: return
        val json = JSONObject().apply {
            put("schema", 2)
            put("surface", "Galaxy S23 controller")
            put("target", "TayTech")
            put("sessionId", s.id)
            put("startedAt", iso(s.startedAt))
            put("host", currentHost.ifBlank { prefs.getString("host", "") ?: "" })
            put("port", if (currentPort > 0) currentPort else prefs.getInt("port", 5555))
            put("strategy", prefs.getString("strategy", "unknown"))
            put("reconnectCount", s.reconnectCount)
            put("transportErrors", s.transportErrors)
            put("executionCount", s.executions.size)
            put("executions", JSONArray().apply {
                s.executions.forEach { e ->
                    put(JSONObject().apply {
                        put("at", iso(e.at)); put("title", e.title); put("risk", e.risk); put("exitCode", e.exitCode); put("durationMs", e.durationMs); put("command", e.command)
                    })
                }
            })
        }
        File(s.directory, "manifest.json").writeText(json.toString(2), Charsets.UTF_8)
        File(s.directory, "resumo.md").writeText(buildSummary(s), Charsets.UTF_8)
    }

    private fun buildSummary(s: PremiumSession): String = buildString {
        append("# Sessão CUSTOMROM ${s.id}\n\n")
        append("- Controlador: **Galaxy S23**\n")
        append("- Alvo remoto: **TayTech**\n")
        append("- Endpoint: `${connectionDetail()}`\n")
        append("- Reconexões: ${s.reconnectCount}\n")
        append("- Erros de transporte: ${s.transportErrors}\n")
        append("- Execuções: ${s.executions.size}\n\n")
        append("## Execuções\n\n")
        s.executions.forEachIndexed { index, e -> append("${index + 1}. **${e.title}** · ${e.risk} · exit=${e.exitCode} · ${e.durationMs}ms\n") }
        append("\nArquivos brutos e checksums seguem no pacote.\n")
    }

    private fun sessionSummary(): String {
        val s = session ?: return "Sem sessão ativa"
        return "Sessão ${s.id}\n${s.executions.size} execuções · ${s.reconnectCount} reconexões · ${s.transportErrors} erros de transporte\n${connectionDetail()}"
    }

    private fun refreshSessionSummary() {
        if (::sessionSummaryView.isInitialized) sessionSummaryView.text = sessionSummary()
    }

    private fun refreshTimeline() {
        if (!::timelineHost.isInitialized) return
        timelineHost.removeAllViews()
        val executions = session?.executions.orEmpty()
        if (executions.isEmpty()) {
            timelineHost.addView(emptyState("Nenhuma execução nesta sessão", "Comandos, diagnósticos e ações de aplicativos aparecerão aqui."))
            return
        }
        executions.takeLast(20).reversed().forEach { execution ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(11), dp(13), dp(11))
                background = rounded(surface, 16, line)
            }
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(text(execution.title, 13f, textPrimary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(riskPill(execution.risk))
            row.addView(head)
            row.addView(text("${iso(execution.at).substring(11, 19)} · exit=${execution.exitCode} · ${execution.durationMs}ms", 10f, textSecondary, false), margins(top = 4))
            timelineHost.addView(row, margins(bottom = 7))
        }
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
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("Não foi possível criar destino")
                contentResolver.openOutputStream(uri)?.use { out -> zipTemp.inputStream().use { it.copyTo(out) } } ?: throw IllegalStateException("Não foi possível abrir destino")
                mainHandler.post {
                    if (share) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, "Compartilhar Evidence Pack CUSTOMROM"))
                    } else toast("Salvo em Downloads/CUSTOMROM/${zipTemp.name}")
                }
            } catch (t: Throwable) {
                mainHandler.post { toast("Falha ao exportar: ${shortError(t)}") }
            }
        }
    }

    private fun createZip(source: File, target: File) {
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            source.walkTopDown().filter { it.isFile }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(source).invariantSeparatorsPath))
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

    private fun recipeDescription(id: String): String = when (id) {
        "estado-geral" -> "Identidade, build, memória e armazenamento em uma fotografia segura."
        "memoria-zram" -> "RAM, swap e ZRAM para investigar pressão de memória."
        "processos" -> "Processos mais pesados e visão geral de atividade."
        "rede-adb" -> "Interfaces, rotas, propriedades ADB e portas relevantes."
        "pacotes-servicos" -> "Inventário de packages e services sem alterar nada."
        "logcat-curto" -> "Eventos Android recentes para reproduções controladas."
        "snapshot-completo" -> "Coleta ampla pronta para análise no ChatGPT."
        else -> "Rotina versionada do CUSTOMROM."
    }

    private fun pageTitle(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(title, 26f, textPrimary, true))
        addView(text(subtitle, 12f, textSecondary, false).apply { setPadding(0, dp(4), 0, 0) })
    }

    private fun sectionTitle(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(title, 17f, textPrimary, true))
        addView(text(subtitle, 10f, textMuted, false).apply { setPadding(0, dp(3), 0, 0) })
    }

    private fun verticalScroll(): LinearLayout {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true; clipToPadding = false }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), 0) }
        scroll.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun emptyState(title: String, detail: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(24), dp(18), dp(24))
        background = rounded(surface, 18, line)
        addView(text(title, 14f, textPrimary, true).apply { gravity = Gravity.CENTER })
        addView(text(detail, 11f, textSecondary, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) })
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = false
    }

    private fun input(hintText: String, initial: String, numeric: Boolean): EditText = EditText(this).apply {
        hint = hintText
        setText(initial)
        setTextColor(textPrimary)
        setHintTextColor(textMuted)
        textSize = 13f
        setSingleLine(true)
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        background = rounded(Color.rgb(4, 9, 16), 14, line)
        setPadding(dp(13), dp(11), dp(13), dp(11))
    }

    private fun primaryButton(label: String, action: () -> Unit): TextView = text(label, 12f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        background = rounded(cyan, 14)
        isClickable = true
        isFocusable = true
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); action() }
        pressFeedback(this)
    }

    private fun softButton(label: String, action: () -> Unit): TextView = text(label, 12f, textPrimary, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(surface2, 14, line)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        pressFeedback(this)
    }

    private fun dangerOutlineButton(label: String, action: () -> Unit): TextView = text(label, 12f, danger, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(Color.rgb(39, 14, 21), 14, danger)
        setOnClickListener { action() }
        pressFeedback(this)
    }

    private fun softIconButton(label: String, action: () -> Unit): TextView = text(label, 10f, textSecondary, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = rounded(surface2, 12)
        setOnClickListener { action() }
        pressFeedback(this)
    }

    private fun miniFilterButton(label: String, action: () -> Unit): TextView = text(label, 10f, textSecondary, true).apply {
        gravity = Gravity.CENTER
        background = rounded(surface2, 13, line)
        setOnClickListener { action(); setTextColor(cyan) }
        pressFeedback(this)
    }

    private fun riskPill(risk: String): TextView = text(risk, 9f, textPrimary, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(8), dp(4))
        styleRiskPill(this, risk)
    }

    private fun styleRiskPill(view: TextView, risk: String) {
        val (fg, back) = when (risk) {
            "AMARELO" -> warning to Color.rgb(58, 42, 14)
            "VERMELHO" -> danger to Color.rgb(56, 16, 27)
            else -> success to Color.rgb(12, 49, 38)
        }
        view.text = risk
        view.setTextColor(fg)
        view.background = rounded(back, 999, fg)
    }

    private fun premiumDialog(content: View): AlertDialog = AlertDialog.Builder(this).setView(content).create().apply {
        setOnShowListener { window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }
    }

    private fun divider(): View = View(this).apply { setBackgroundColor(line) }
    private fun space(height: Int): View = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }

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

    private fun pressFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.alpha = 0.78f; v.scaleX = 0.985f; v.scaleY = 0.985f }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f }
            }
            false
        }
    }

    private fun simpleWatcher(onChanged: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChanged()
        override fun afterTextChanged(s: Editable?) = Unit
    }

    private fun copyText(value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CUSTOMROM", value))
        toast("Copiado")
    }

    private fun shareText(title: String, value: String) {
        if (value.isBlank()) { toast("Ainda não há conteúdo para compartilhar"); return }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, value)
        }
        startActivity(Intent.createChooser(intent, "Compartilhar via"))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    private fun shortError(t: Throwable): String = t.message?.take(180) ?: t::class.java.simpleName

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
                val count = input.read(buffer)
                if (count <= 0) break
                md.update(buffer, 0, count)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
