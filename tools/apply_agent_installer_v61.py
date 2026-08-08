#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps" / "customrom-adb-native"
BUILD = APP / "app/build.gradle.kts"
CONTROLLER = APP / "app/src/main/java/com/customrom/adb/AdbRemoteController.kt"
ACTIVITY = APP / "app/src/main/java/com/customrom/adb/PremiumOpsActivity.kt"
ENGINE = APP / "app/src/main/java/com/customrom/adb/FunctionalActionEngine.kt"
VALIDATOR = ROOT / "tools" / "validate_native_customrom.py"


def replace_once(path: Path, old: str, new: str, marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    if marker in text:
        return
    if old not in text:
        raise SystemExit(f"anchor not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Bundle the companion APK into the S23 app as a generated asset. The agent is
# built first, so a clean checkout can reproduce the one-tap installer.
replace_once(
    BUILD,
    '''dependencies {
    implementation("com.flyfishxu:kadb:2.1.1")
''',
    '''val embeddedAgentAssets = layout.buildDirectory.dir("generated/customromAgentAssets").get().asFile
val prepareEmbeddedAgent by tasks.registering(Copy::class) {
    dependsOn(":agent:assembleDebug")
    from(project(":agent").layout.buildDirectory.file("outputs/apk/debug/agent-debug.apk"))
    into(embeddedAgentAssets)
    rename { "CUSTOMROM-Agent-TayTech-debug.apk" }
}

android.sourceSets.named("main") {
    assets.srcDir(embeddedAgentAssets)
}

tasks.configureEach {
    if (name == "preDebugBuild") dependsOn(prepareEmbeddedAgent)
}

dependencies {
    implementation("com.flyfishxu:kadb:2.1.1")
''',
    'CUSTOMROM-Agent-TayTech-debug.apk',
)

# Kadb 2.1.1 exposes install(File, vararg options). Keep it on the controller's
# serialized executor and return the same observable outcome model as shell.
replace_once(
    CONTROLLER,
    'import java.util.concurrent.Executors\n',
    'import java.io.File\nimport java.util.concurrent.Executors\n',
    'import java.io.File',
)

INSTALL_METHOD = '''
    fun installApk(
        file: File,
        replaceExisting: Boolean = true,
        timeoutMs: Long = 90_000L,
        callback: (RemoteShellOutcome) -> Unit
    ): Future<*> {
        val started = System.currentTimeMillis()
        val completed = AtomicBoolean(false)
        var task: Future<*>? = null

        task = executor.submit {
            val connection = kadb
            if (connection == null) {
                if (completed.compareAndSet(false, true)) {
                    val result = RemoteShellOutcome("", "", -1, System.currentTimeMillis() - started, IllegalStateException("TayTech não conectada"))
                    mainHandler.post { callback(result); autoReconnect(force = true) }
                }
                return@submit
            }
            if (!file.isFile || file.length() <= 0L) {
                if (completed.compareAndSet(false, true)) {
                    mainHandler.post { callback(RemoteShellOutcome("", "APK local ausente ou vazio", 2, System.currentTimeMillis() - started)) }
                }
                return@submit
            }
            try {
                if (replaceExisting) connection.install(file, "-r") else connection.install(file)
                if (completed.compareAndSet(false, true)) {
                    mainHandler.post { callback(RemoteShellOutcome("Success", "", 0, System.currentTimeMillis() - started)) }
                }
            } catch (t: Throwable) {
                if (completed.compareAndSet(false, true)) {
                    val transport = if (runCatching { connection.connectionCheck() }.getOrDefault(false)) null else t
                    if (transport != null) runCatching { connection.resetConnection() }
                    val result = RemoteShellOutcome("", t.message ?: t::class.java.simpleName, 1, System.currentTimeMillis() - started, transport)
                    mainHandler.post {
                        callback(result)
                        if (transport != null) {
                            emit(RemoteConnectionState.WaitingNetwork("instalação perdeu o transporte; reconectando"))
                            autoReconnect(force = true)
                        }
                    }
                }
            }
        }

        timeoutScheduler.schedule({
            if (completed.compareAndSet(false, true)) {
                task?.cancel(true)
                runCatching { kadb?.resetConnection() }
                val duration = System.currentTimeMillis() - started
                val error = TimeoutException("TIMEOUT: instalação excedeu ${timeoutMs / 1000}s")
                mainHandler.post {
                    callback(RemoteShellOutcome("", "", -1, duration, error))
                    emit(RemoteConnectionState.WaitingNetwork("instalação excedeu o tempo limite; recuperando conexão"))
                    autoReconnect(force = true)
                }
            }
        }, timeoutMs.coerceAtLeast(5_000L), TimeUnit.MILLISECONDS)

        return task
    }

'''
replace_once(
    CONTROLLER,
    '    fun cancel(task: Future<*>?) {\n',
    INSTALL_METHOD + '    fun cancel(task: Future<*>?) {\n',
    'fun installApk(',
)

# Action graph now offers installation, not merely detection, when the agent is absent.
replace_once(
    ENGINE,
    '''        actions += if (agentInstalled) {
            FunctionalAction("Preparar CUSTOMROM Agent", "Concede WRITE_SECURE_SETTINGS e manda o companion aplicar a recuperação de Wireless ADB.", ActionDestination.RECIPE, "customrom-agent-preparar", "AMARELO")
        } else {
            FunctionalAction("Verificar CUSTOMROM Agent", "Confirma se o companion de boot está instalado e com permissão.", ActionDestination.RECIPE, "customrom-agent-status")
        }
''',
    '''        actions += if (agentInstalled) {
            FunctionalAction("Preparar CUSTOMROM Agent", "Concede WRITE_SECURE_SETTINGS e manda o companion aplicar a recuperação de Wireless ADB.", ActionDestination.RECIPE, "customrom-agent-preparar", "AMARELO")
        } else {
            FunctionalAction("Instalar CUSTOMROM Agent", "O APK companion já está dentro do CUSTOMROM no S23; instalar e preparar é um fluxo de um toque.", ActionDestination.RECIPE, "customrom-agent-instalar", "AMARELO")
        }
''',
    '"Instalar CUSTOMROM Agent", "O APK companion já está dentro do CUSTOMROM no S23',
)

replace_once(
    ENGINE,
    '''        val actions = mutableListOf<FunctionalAction>()
        if (installed && !granted) actions += FunctionalAction("Preparar Agent agora", "Concede a permissão de desenvolvimento e executa o recovery uma vez.", ActionDestination.RECIPE, "customrom-agent-preparar", "AMARELO")
        actions += FunctionalAction("Conferir ADB após reinício", "Volta ao diagnóstico completo de persistência.", ActionDestination.RECIPE, "adb-persistencia-diagnostico")
''',
    '''        val actions = mutableListOf<FunctionalAction>()
        when {
            !installed -> actions += FunctionalAction("Instalar Agent na TayTech", "Transfere e instala o companion embutido usando a conexão ADB atual.", ActionDestination.RECIPE, "customrom-agent-instalar", "AMARELO")
            !granted -> actions += FunctionalAction("Preparar Agent agora", "Concede a permissão de desenvolvimento e executa o recovery uma vez.", ActionDestination.RECIPE, "customrom-agent-preparar", "AMARELO")
        }
        actions += FunctionalAction("Conferir ADB após reinício", "Volta ao diagnóstico completo de persistência.", ActionDestination.RECIPE, "adb-persistencia-diagnostico")
''',
    '"Instalar Agent na TayTech", "Transfere e instala o companion embutido',
)

# Intercept the internal action and install the embedded agent over Kadb 2.1.1.
replace_once(
    ACTIVITY,
    '            ActionDestination.RECIPE -> runRecipeById(action.target)\n',
    '            ActionDestination.RECIPE -> if (action.target == "customrom-agent-instalar") installCustomromAgent() else runRecipeById(action.target)\n',
    'action.target == "customrom-agent-instalar"',
)

INSTALL_UI = '''
    private fun installCustomromAgent() {
        if (activeTask?.isDone == false) {
            toast("Já existe uma operação em andamento")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Instalar CUSTOMROM Agent na TayTech")
            .setMessage("O companion é pequeno e roda na própria multimídia para tentar reativar ADB/Wireless debugging após o boot. A instalação é reversível e não toca em MCU, CAN ou firmware.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Instalar") { _, _ -> installCustomromAgentNow() }
            .show()
    }

    private fun installCustomromAgentNow() {
        val assetName = "CUSTOMROM-Agent-TayTech-debug.apk"
        val apk = File(cacheDir, assetName)
        try {
            assets.open(assetName).use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
        } catch (t: Throwable) {
            val failed = HumanOperationResult(OperationPhase.COMMAND_ERROR, "Agent indisponível", "O APK companion não foi encontrado dentro desta build.", t.stackTraceToString(), false)
            renderOperation(failed)
            showTechnicalResult(failed, t.stackTraceToString())
            return
        }

        renderOperation(HumanOperationResult(OperationPhase.RUNNING, "Instalando CUSTOMROM Agent", "Enviando o companion diretamente do S23 para a TayTech…", "Kadb.install · ${apk.length()} bytes", false))
        activeTask = adb.installApk(apk, replaceExisting = true) { outcome ->
            activeTask = null
            val raw = combineRaw(outcome)
            val result = if (outcome.transportError != null) {
                OperationPresenter.transportError("Instalar CUSTOMROM Agent", outcome.transportError.message ?: outcome.transportError::class.java.simpleName, outcome.durationMs)
            } else {
                OperationPresenter.fromShell("Instalar CUSTOMROM Agent", outcome.stdout, outcome.stderr, outcome.exitCode, outcome.durationMs)
            }
            lastRawOutput = raw
            renderOperation(result)
            appendExecution("Instalar CUSTOMROM Agent", "Kadb.install($assetName, -r)", "AMARELO", outcome)
            apk.delete()
            if (!result.success) {
                showTechnicalResult(result, raw)
                return@installApk
            }

            val prepare = "pm grant com.customrom.agent android.permission.WRITE_SECURE_SETTINGS 2>&1; am broadcast -a com.customrom.agent.APPLY -n com.customrom.agent/.BootReceiver 2>&1; echo agent_installed=1; if dumpsys package com.customrom.agent 2>/dev/null | grep -A8 'grantedPermissions' | grep -q 'android.permission.WRITE_SECURE_SETTINGS'; then echo write_secure_settings=granted; else echo write_secure_settings=missing; fi; echo adb_enabled=$(settings get global adb_enabled); echo adb_wifi_enabled=$(settings get global adb_wifi_enabled)"
            executeNow("Preparar CUSTOMROM Agent", prepare, "AMARELO", showDialog = false) { _, prepared ->
                if (prepared.success) runRecipeById("customrom-agent-status") else showTechnicalResult(prepared, lastRawOutput)
            }
        }
    }

'''
replace_once(
    ACTIVITY,
    '    private fun executeOperation(\n',
    INSTALL_UI + '    private fun executeOperation(\n',
    'private fun installCustomromAgentNow()',
)

# Validator explicitly covers the one-tap install chain and embedded asset build.
replace_once(
    VALIDATOR,
    '    if \'include(":agent")\' not in SETTINGS.read_text(encoding="utf-8"):\n        fail("CUSTOMROM Agent precisa estar incluído no build")\n',
    '''    if 'include(":agent")' not in SETTINGS.read_text(encoding="utf-8"):
        fail("CUSTOMROM Agent precisa estar incluído no build")
    if 'CUSTOMROM-Agent-TayTech-debug.apk' not in BUILD.read_text(encoding="utf-8"):
        fail("app do S23 precisa embutir o Agent para instalação de um toque")
    if 'fun installApk(' not in controller_src or 'connection.install(file, "-r")' not in controller_src:
        fail("AdbRemoteController precisa instalar APK remoto pela API Kadb validada")
    if 'installCustomromAgentNow()' not in ops_src or 'customrom-agent-instalar' not in ops_src:
        fail("fluxo visual precisa instalar o Agent sem exigir transferência manual")
''',
    'app do S23 precisa embutir o Agent para instalação de um toque',
)

replace_once(
    VALIDATOR,
    '    print("customrom_agent=present")\n',
    '    print("customrom_agent=present")\n    print("customrom_agent_one_tap_install=present")\n',
    'print("customrom_agent_one_tap_install=present")',
)

print("APPLY_AGENT_INSTALLER_V61=OK")
# CI trigger: V6.1 one-tap Agent installer gate.
