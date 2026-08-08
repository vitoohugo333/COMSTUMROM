#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps" / "customrom-adb-native"
ACTIVITY = APP / "app/src/main/java/com/customrom/adb/PremiumOpsActivity.kt"
MODELS = APP / "app/src/main/java/com/customrom/adb/PremiumOpsModels.kt"
ENGINE = APP / "app/src/main/java/com/customrom/adb/FunctionalActionEngine.kt"
RECIPES = APP / "app/src/main/assets/recipes.json"
SETTINGS = APP / "settings.gradle.kts"
VALIDATOR = ROOT / "tools" / "validate_native_customrom.py"
AGENT = APP / "agent"


def replace_once(path: Path, old: str, new: str, marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    if marker in text:
        return
    if old not in text:
        raise SystemExit(f"anchor not found in {path}: {old[:80]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


# ---------------------------------------------------------------------------
# Package intelligence: owner-required apps are protected; OEM launcher is
# actionable HIGH, not hard-blocked.
# ---------------------------------------------------------------------------
replace_once(
    MODELS,
    '        val reasons = mutableListOf<String>()\n\n        if (knownProtectedPrefixes.any { packageLower == it || packageLower.startsWith("$it.") }) {',
    '''        val reasons = mutableListOf<String>()

        if (packageLower == "com.omegas.v7.test") {
            reasons += "carga obrigatória do proprietário; deve permanecer ativa e fora do debloat"
            reasons += "o consumo do ÔMEGAS não entra no ranking de otimização da TayTech"
            return PackageAssessment(PackageCriticality.PROTECTED, AssessmentConfidence.HIGH, reasons, false)
        }

        if (packageLower == "ginlemon.flowerfree") {
            reasons += "Smart Launcher escolhido pelo proprietário como HOME principal"
            reasons += "não deve ser tratado como bloat nem receber sugestão de stop/disable"
            return PackageAssessment(PackageCriticality.PROTECTED, AssessmentConfidence.HIGH, reasons, false)
        }

        if (packageLower == "com.jancar.launcher") {
            reasons += "launcher OEM da TayTech; existe launcher alternativo escolhido pelo proprietário"
            reasons += "controle avançado reversível permitido após confirmar o HOME atual"
            return PackageAssessment(PackageCriticality.HIGH, AssessmentConfidence.HIGH, reasons, true)
        }

        if (knownProtectedPrefixes.any { packageLower == it || packageLower.startsWith("$it.") }) {''',
    'carga obrigatória do proprietário; deve permanecer ativa e fora do debloat',
)

replace_once(
    MODELS,
    '            "com.spotify.music" to "Spotify",\n',
    '            "com.spotify.music" to "Spotify",\n            "com.omegas.v7.test" to "ÔMEGAS V7",\n            "ginlemon.flowerfree" to "Smart Launcher",\n            "com.google.android.googlequicksearchbox" to "Google App",\n            "com.google.android.apps.docs" to "Google Drive",\n',
    '"ginlemon.flowerfree" to "Smart Launcher"',
)

# ---------------------------------------------------------------------------
# Action engine: ignore OMEGAS globally in optimization-oriented parsing and
# add pragmatic performance, ADB persistence and log-storm reports.
# ---------------------------------------------------------------------------
replace_once(
    ENGINE,
    '    private val cpuPackageRegex = Regex("(?m)^\\\\s*([0-9]+(?:\\\\.[0-9]+)?)%\\\\s+\\\\d+/([A-Za-z0-9._:-]+)")\n',
    '    private val cpuPackageRegex = Regex("(?m)^\\\\s*([0-9]+(?:\\\\.[0-9]+)?)%\\\\s+\\\\d+/([A-Za-z0-9._:-]+)")\n    private val ignoredOptimizationOwners = setOf("com.omegas.v7.test")\n',
    'ignoredOptimizationOwners = setOf("com.omegas.v7.test")',
)

replace_once(
    ENGINE,
    '        "diagnostico-lentidao", "processos", "memoria-zram" -> performanceReport(recipeId, raw)\n',
    '''        "diagnostico-lentidao", "processos", "memoria-zram" -> performanceReport(recipeId, raw)
        "otimizacao-pragmatica" -> pragmaticOptimizationReport(raw)
        "adb-persistencia-diagnostico", "adb-persistencia-aplicar", "adb-persistencia-restaurar" -> adbPersistenceReport(recipeId, raw)
        "log-storm" -> logStormReport(raw)
        "customrom-agent-status", "customrom-agent-preparar" -> agentStatusReport(raw)
''',
    '"otimizacao-pragmatica" -> pragmaticOptimizationReport(raw)',
)

replace_once(
    ENGINE,
    '                if (owner == "top" || owner.startsWith("android.hardware.")) null else cpu to owner\n',
    '                if (owner == "top" || owner.startsWith("android.hardware.") || ignoredOptimizationOwners.contains(owner.substringBefore(\':\'))) null else cpu to owner\n',
    "ignoredOptimizationOwners.contains(owner.substringBefore(':'))",
)

replace_once(
    ENGINE,
    '                !candidate.contains("intent.action", true) &&\n                !candidate.startsWith("java.") &&\n',
    '                !candidate.contains("intent.action", true) &&\n                !ignoredOptimizationOwners.contains(candidate.substringBefore(\':\')) &&\n                !candidate.startsWith("java.") &&\n',
    "!ignoredOptimizationOwners.contains(candidate.substringBefore(':'))",
)

replace_once(
    ENGINE,
    '            p.startsWith("com.jancar") || p.contains("canbus") || p.contains("mcu") || p.contains("hiworld") -> 0\n            p.startsWith("com.omegas") -> 1\n            p.startsWith("com.google") -> 2\n',
    '            p.startsWith("com.jancar") || p.contains("canbus") || p.contains("mcu") || p.contains("hiworld") -> 0\n            p.startsWith("com.google") -> 1\n',
    'p.startsWith("com.google") -> 1',
)

REPORTS = r'''
    private fun pragmaticOptimizationReport(raw: String): ActionableReport {
        val findings = mutableListOf<String>()
        val actions = mutableListOf<FunctionalAction>()
        val home = lineValue(raw, "HOME_CURRENT")
        val homePkg = home?.substringBefore('/')?.trim()
        if (!homePkg.isNullOrBlank()) {
            findings += when (homePkg) {
                "ginlemon.flowerfree" -> "HOME atual confirmado: Smart Launcher. Ele é preferência do proprietário e fica protegido."
                "com.jancar.launcher" -> "HOME atual ainda é o launcher OEM Jancar. Não o desative antes de definir o Smart Launcher como HOME."
                else -> "HOME atual: $homePkg."
            }
        }

        val totalKb = metric(raw, "MemTotal")
        val availableKb = metric(raw, "MemAvailable")
        val swapTotalKb = metric(raw, "SwapTotal")
        val swapFreeKb = metric(raw, "SwapFree")
        if (totalKb != null && availableKb != null && totalKb > 0) {
            val pct = (availableKb * 100.0 / totalKb).toInt()
            findings += "Memória disponível: ${availableKb / 1024} MB de ${totalKb / 1024} MB ($pct%)."
        }
        if (swapTotalKb != null && swapFreeKb != null && swapTotalKb > 0) {
            findings += "Swap/ZRAM em uso: ${(swapTotalKb - swapFreeKb).coerceAtLeast(0) / 1024} MB de ${swapTotalKb / 1024} MB."
        }

        val cpuOwners = cpuPackageRegex.findAll(raw)
            .mapNotNull { match ->
                val cpu = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val owner = match.groupValues[2].trimEnd(':')
                if (owner == "top" || owner.startsWith("android.hardware.") || ignoredOptimizationOwners.contains(owner.substringBefore(':'))) null else cpu to owner
            }
            .distinctBy { it.second }
            .sortedByDescending { it.first }
            .take(12)
            .toList()
        cpuOwners.take(6).forEach { (cpu, owner) -> findings += "${formatCpu(cpu)}% de CPU: $owner" }

        val webviewBusy = cpuOwners.any { (_, owner) -> owner.startsWith("com.google.android.webview") } || raw.contains("com.google.android.webview:sandboxed_process0")
        val googleAppSeen = raw.contains("com.google.android.googlequicksearchbox")
        if (webviewBusy && googleAppSeen) {
            findings += "WebView pesado correlacionado ao Google App nesta fotografia. O próximo teste pode ser feito no próprio detalhe do Google App."
            actions += FunctionalAction("Abrir Google App", "Analisar, parar temporariamente ou desativar reversivelmente com confirmação.", ActionDestination.PACKAGE, "com.google.android.googlequicksearchbox")
        }
        cpuOwners.firstOrNull { it.second.substringBefore(':') == "com.google.android.apps.docs" }?.let { (cpu, _) ->
            findings += "Google Drive aparece consumindo ${formatCpu(cpu)}% de CPU nesta fotografia."
            actions += FunctionalAction("Abrir Google Drive", "Teste temporário e controle reversível ficam no detalhe do package.", ActionDestination.PACKAGE, "com.google.android.apps.docs")
        }

        if (raw.contains("com.jancar.launcher")) {
            actions += FunctionalAction("Abrir launcher nativo TayTech", "Launcher OEM: pode ser testado/desativado no usuário 0 se o Smart Launcher estiver como HOME.", ActionDestination.PACKAGE, "com.jancar.launcher", "AMARELO")
        }

        val wobble = lineValue(raw, "LOGTAG_WOBLE")?.toIntOrNull() ?: 0
        if (wobble >= 100) {
            findings += "Tempestade de log detectada: $wobble mensagens WOBLE na janela coletada. Isso merece investigação própria antes de mexer em Bluetooth/driver."
            actions += FunctionalAction("Investigar tempestade WOBLE", "Descobre volume, PIDs e contexto sem alterar serviços automotivos.", ActionDestination.RECIPE, "log-storm")
        }

        actions += FunctionalAction("Ver apps rodando", "Explora somente os processos ativos agora.", ActionDestination.APPS_FILTER, "Rodando")
        actions += FunctionalAction("Comparar depois", "Repete a fotografia depois de uma única mudança reversível.", ActionDestination.RECIPE, "otimizacao-pragmatica")

        val summary = when {
            cpuOwners.isNotEmpty() -> "A central foi analisada em um único fluxo. O ÔMEGAS foi excluído automaticamente e os próximos alvos são apresentados como ações, não como comandos para copiar."
            else -> "A coleta pragmática terminou. O ÔMEGAS foi excluído da otimização e a evidência técnica ficou preservada."
        }
        return ActionableReport("Analisar e enxugar a central", summary, findings, actions.distinctBy { "${it.destination}:${it.target}:${it.label}" }.take(64))
    }

    private fun adbPersistenceReport(recipeId: String, raw: String): ActionableReport {
        val findings = mutableListOf<String>()
        val actions = mutableListOf<FunctionalAction>()
        val enabled = lineValue(raw, "adb_enabled")
        val wifi = lineValue(raw, "adb_wifi_enabled")
        val persistPort = lineValue(raw, "persist_port")
        val servicePort = lineValue(raw, "service_port")
        val agentInstalled = lineValue(raw, "agent_installed") == "1"
        val rollback = lineValue(raw, "rollback_available") == "1"

        findings += "ADB do Android: ${enabled ?: "?"} · Wireless debugging: ${wifi ?: "?"}."
        findings += "Porta temporária: ${servicePort?.ifBlank { "não definida" } ?: "?"} · porta persistente: ${persistPort?.ifBlank { "não definida" } ?: "?"}."
        findings += if (agentInstalled) "CUSTOMROM Agent detectado na TayTech." else "CUSTOMROM Agent ainda não foi detectado na TayTech."

        val ideal = enabled == "1" && wifi == "1" && persistPort == "5555"
        if (ideal) {
            findings += "A ROM aceitou o estado ideal desta estratégia: ADB ligado, Wireless debugging ligado e persist.adb.tcp.port=5555. A prova definitiva ainda é reiniciar a TayTech."
        } else if (recipeId == "adb-persistencia-aplicar") {
            findings += "A tentativa foi aplicada e verificada com os valores acima. O que a ROM recusou permanece explícito; nenhuma falha é mascarada."
        }

        if (!ideal) actions += FunctionalAction("Tentar ADB persistente", "Salva rollback, mantém ADB/Wireless ligados e tenta persistir 5555 sem reiniciar o daemon.", ActionDestination.RECIPE, "adb-persistencia-aplicar", "AMARELO")
        actions += if (agentInstalled) {
            FunctionalAction("Preparar CUSTOMROM Agent", "Concede WRITE_SECURE_SETTINGS e manda o companion aplicar a recuperação de Wireless ADB.", ActionDestination.RECIPE, "customrom-agent-preparar", "AMARELO")
        } else {
            FunctionalAction("Verificar CUSTOMROM Agent", "Confirma se o companion de boot está instalado e com permissão.", ActionDestination.RECIPE, "customrom-agent-status")
        }
        actions += FunctionalAction("Abrir opções de desenvolvedor", "Fallback visual direto na TayTech.", ActionDestination.RECIPE, "abrir-depuracao-sem-fio", "AMARELO")
        if (rollback) actions += FunctionalAction("Restaurar configuração anterior", "Executa o rollback salvo antes da tentativa de persistência.", ActionDestination.RECIPE, "adb-persistencia-restaurar", "AMARELO")
        actions += FunctionalAction("Conferir novamente", "Lê tudo em uma única coleta.", ActionDestination.RECIPE, "adb-persistencia-diagnostico")

        return ActionableReport(
            "ADB após reinício",
            if (ideal) "Configuração persistente aceita nesta sessão; falta apenas o teste físico de reboot." else "O CUSTOMROM identificou o melhor caminho disponível sem exigir que você monte comandos manualmente.",
            findings,
            actions.distinctBy { "${it.destination}:${it.target}:${it.label}" }.take(64)
        )
    }

    private fun logStormReport(raw: String): ActionableReport {
        val count = lineValue(raw, "LOGTAG_WOBLE")?.toIntOrNull() ?: 0
        val findings = mutableListOf<String>()
        findings += if (count > 0) "$count mensagens WOBLE foram contadas na janela recente." else "Nenhuma mensagem WOBLE foi contada na janela recente."
        if (count >= 100) findings += "Volume alto confirmado. Como WOBLE pode vir de camada wireless/driver, o CUSTOMROM não desativa Bluetooth ou serviço automotivo automaticamente."
        val actions = listOf(
            FunctionalAction("Cruzar com processos", "Vê CPU/processos no mesmo contexto.", ActionDestination.RECIPE, "otimizacao-pragmatica"),
            FunctionalAction("Ver Bluetooth", "Lê o estado Bluetooth sem alterar nada.", ActionDestination.RECIPE, "bluetooth-status"),
            FunctionalAction("Repetir tempestade de logs", "Mede novamente para confirmar recorrência.", ActionDestination.RECIPE, "log-storm")
        )
        return ActionableReport("Tempestade de logs", "O volume foi medido e separado da decisão de desativar qualquer componente.", findings, actions)
    }

    private fun agentStatusReport(raw: String): ActionableReport {
        val installed = lineValue(raw, "agent_installed") == "1"
        val granted = lineValue(raw, "write_secure_settings") == "granted"
        val findings = mutableListOf<String>()
        findings += if (installed) "CUSTOMROM Agent instalado." else "CUSTOMROM Agent não instalado. O artifact V6 inclui o APK companion para a TayTech."
        if (installed) findings += if (granted) "Permissão WRITE_SECURE_SETTINGS concedida." else "Agent instalado, mas ainda sem WRITE_SECURE_SETTINGS."
        val actions = mutableListOf<FunctionalAction>()
        if (installed && !granted) actions += FunctionalAction("Preparar Agent agora", "Concede a permissão de desenvolvimento e executa o recovery uma vez.", ActionDestination.RECIPE, "customrom-agent-preparar", "AMARELO")
        actions += FunctionalAction("Conferir ADB após reinício", "Volta ao diagnóstico completo de persistência.", ActionDestination.RECIPE, "adb-persistencia-diagnostico")
        return ActionableReport("CUSTOMROM Agent", if (installed && granted) "Companion pronto para tentar reativar Wireless ADB a cada boot." else "O app mostrou exatamente o que falta para automatizar o boot.", findings, actions)
    }
'''

replace_once(
    ENGINE,
    '    private fun spotifyReport(raw: String): ActionableReport {\n',
    REPORTS + '\n    private fun spotifyReport(raw: String): ActionableReport {\n',
    'private fun pragmaticOptimizationReport(raw: String)',
)

# ---------------------------------------------------------------------------
# Premium UI: surface the two new high-value flows and human descriptions.
# ---------------------------------------------------------------------------
replace_once(
    ACTIVITY,
    '''        root.addView(featureAction("◇", "Por que a central está lenta?", "Memória, CPU, processos, armazenamento e thermal em uma coleta.") {
            recipes.firstOrNull { it.id == "diagnostico-lentidao" }?.let(::runRecipe)
        }, margins(top = 8))
''',
    '''        root.addView(featureAction("◇", "Analisar e enxugar a central", "Uma coleta: CPU, memória, HOME, WebView, Google, logs e próximos alvos — ÔMEGAS ignorado automaticamente.") {
            runRecipeById("otimizacao-pragmatica")
        }, margins(top = 8))
        root.addView(featureAction("↻", "ADB após reinício", "Diagnostica persistência, oferece correção e fallback sem montar comandos.") {
            runRecipeById("adb-persistencia-diagnostico")
        }, margins(top = 8))
''',
    '"Analisar e enxugar a central"',
)

replace_once(
    ACTIVITY,
    '        "diagnostico-lentidao" -> "Workflow composto: memória + CPU + top + disco + thermal."\n',
    '''        "diagnostico-lentidao" -> "Workflow composto: memória + CPU + top + disco + thermal."
        "otimizacao-pragmatica" -> "Diagnóstico de um toque que ignora ÔMEGAS, identifica HOME, WebView, Google/Drive, CPU, RAM/ZRAM e tempestade WOBLE."
        "adb-persistencia-diagnostico" -> "Lê em uma coleta se ADB, Wireless debugging, porta 5555 e CUSTOMROM Agent podem sobreviver ao reboot."
        "adb-persistencia-aplicar" -> "Salva rollback e tenta manter ADB/Wireless + porta 5555 sem reiniciar o daemon durante a operação."
        "adb-persistencia-restaurar" -> "Restaura os valores salvos antes da tentativa de persistência."
        "log-storm" -> "Mede a tempestade WOBLE e contexto recente sem desligar Bluetooth ou drivers."
        "customrom-agent-status" -> "Verifica se o companion de boot está instalado e autorizado na TayTech."
        "customrom-agent-preparar" -> "Concede WRITE_SECURE_SETTINGS ao companion e solicita uma aplicação imediata."
        "abrir-depuracao-sem-fio" -> "Abre diretamente as opções de desenvolvedor na TayTech como fallback visual."
''',
    '"otimizacao-pragmatica" -> "Diagnóstico de um toque',
)

# ---------------------------------------------------------------------------
# Recipes: every discovered issue becomes a one-tap workflow in the app.
# ---------------------------------------------------------------------------
recipes = json.loads(RECIPES.read_text(encoding="utf-8"))
existing = {r["id"] for r in recipes}
new_recipes = [
    {
        "id": "otimizacao-pragmatica",
        "name": "Analisar e enxugar a central",
        "risk": "VERDE",
        "command": "echo '=== CUSTOMROM OTIMIZACAO PRAGMATICA ==='; HOME=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tail -n 1); echo HOME_CURRENT=$HOME; echo; echo '=== MEMORIA ==='; grep -E 'MemTotal|MemAvailable|SwapTotal|SwapFree' /proc/meminfo; echo; echo '=== CPU ==='; dumpsys cpuinfo 2>/dev/null | head -n 90; echo; echo '=== TOP ==='; top -b -n 1 -m 35 2>/dev/null || top -n 1; echo; echo '=== WEBVIEW ==='; ps -A | grep -E 'webview|chromium' | head -n 40; for P in $(ps -A | grep 'com.google.android.webview:sandboxed_process0' | awk '{print $2}' | head -n 3); do echo WEBVIEW_PID=$P; dumpsys activity processes 2>/dev/null | grep -B18 -A24 \"$P\" | head -n 80; done; echo; echo '=== ALVOS CONHECIDOS ==='; for PKG in com.google.android.googlequicksearchbox com.google.android.apps.docs ginlemon.flowerfree com.jancar.launcher; do echo ---$PKG---; pm path $PKG 2>/dev/null; pidof $PKG 2>/dev/null; done; echo; echo '=== LOG STORM ==='; W=$(logcat -d -v tag -t 1500 2>/dev/null | grep -c '^WOBLE' || true); echo LOGTAG_WOBLE=$W; logcat -d -v tag -t 1500 2>/dev/null | awk -F: '{gsub(/^ +| +$/,\"\",$1); if($1!=\"\") print $1}' | sort | uniq -c | sort -nr | head -n 20",
        "output": "64_otimizacao_pragmatica.txt",
    },
    {
        "id": "log-storm",
        "name": "Investigar tempestade WOBLE",
        "risk": "VERDE",
        "command": "echo '=== WOBLE COUNT ==='; W=$(logcat -d -v threadtime -t 2000 2>/dev/null | grep -c ' WOBLE ' || true); echo LOGTAG_WOBLE=$W; echo; echo '=== AMOSTRA ==='; logcat -d -v threadtime -t 2000 2>/dev/null | grep ' WOBLE ' | tail -n 160; echo; echo '=== PIDS RECENTES ==='; logcat -d -v threadtime -t 2000 2>/dev/null | grep ' WOBLE ' | awk '{print $3}' | sort | uniq -c | sort -nr | head -n 20; echo; echo '=== PROCESSOS ==='; ps -A",
        "output": "65_tempestade_woble.txt",
    },
    {
        "id": "adb-persistencia-diagnostico",
        "name": "ADB após reinício — diagnosticar",
        "risk": "VERDE",
        "command": "echo '=== ADB PERSISTENCIA ==='; echo build_type=$(getprop ro.build.type); echo debuggable=$(getprop ro.debuggable); echo adb_secure=$(getprop ro.adb.secure); echo adb_enabled=$(settings get global adb_enabled); echo adb_wifi_enabled=$(settings get global adb_wifi_enabled); echo service_port=$(getprop service.adb.tcp.port); echo persist_port=$(getprop persist.adb.tcp.port); echo adbd_state=$(getprop init.svc.adbd); if pm path com.customrom.agent >/dev/null 2>&1; then echo agent_installed=1; else echo agent_installed=0; fi; if [ -f /storage/emulated/0/CUSTOMROM/experimentos/adb_persistencia_rollback.sh ]; then echo rollback_available=1; else echo rollback_available=0; fi; echo; ss -lnt 2>/dev/null | grep -E ':5555|:37[0-9]{3}|:4[0-9]{4}' || true",
        "output": "66_adb_persistencia_diagnostico.txt",
    },
    {
        "id": "adb-persistencia-aplicar",
        "name": "Tentar manter ADB após reinício",
        "risk": "AMARELO",
        "command": "mkdir -p /storage/emulated/0/CUSTOMROM/experimentos; R=/storage/emulated/0/CUSTOMROM/experimentos/adb_persistencia_rollback.sh; A=$(settings get global adb_enabled); W=$(settings get global adb_wifi_enabled); P=$(getprop persist.adb.tcp.port); { echo '#!/system/bin/sh'; if [ \"$A\" = null ] || [ -z \"$A\" ]; then echo 'settings delete global adb_enabled'; else echo \"settings put global adb_enabled '$A'\"; fi; if [ \"$W\" = null ] || [ -z \"$W\" ]; then echo 'settings delete global adb_wifi_enabled'; else echo \"settings put global adb_wifi_enabled '$W'\"; fi; if [ -n \"$P\" ]; then echo \"setprop persist.adb.tcp.port '$P'\"; else echo \"setprop persist.adb.tcp.port ''\"; fi; } > $R; chmod 700 $R 2>/dev/null || true; settings put global adb_enabled 1 2>&1; settings put global adb_wifi_enabled 1 2>&1; if [ \"$(getprop ro.adb.secure)\" = 1 ]; then setprop persist.adb.tcp.port 5555 2>&1; else echo 'persist_port_nao_alterada: adb_secure != 1'; fi; echo adb_enabled=$(settings get global adb_enabled); echo adb_wifi_enabled=$(settings get global adb_wifi_enabled); echo service_port=$(getprop service.adb.tcp.port); echo persist_port=$(getprop persist.adb.tcp.port); if pm path com.customrom.agent >/dev/null 2>&1; then echo agent_installed=1; else echo agent_installed=0; fi; echo rollback_available=1",
        "output": "67_adb_persistencia_aplicada.txt",
    },
    {
        "id": "adb-persistencia-restaurar",
        "name": "Restaurar configuração ADB anterior",
        "risk": "AMARELO",
        "command": "R=/storage/emulated/0/CUSTOMROM/experimentos/adb_persistencia_rollback.sh; if [ ! -f $R ]; then echo 'rollback não encontrado'; exit 2; fi; sh $R; RC=$?; echo adb_enabled=$(settings get global adb_enabled); echo adb_wifi_enabled=$(settings get global adb_wifi_enabled); echo service_port=$(getprop service.adb.tcp.port); echo persist_port=$(getprop persist.adb.tcp.port); if pm path com.customrom.agent >/dev/null 2>&1; then echo agent_installed=1; else echo agent_installed=0; fi; echo rollback_available=1; exit $RC",
        "output": "68_adb_persistencia_restaurada.txt",
    },
    {
        "id": "customrom-agent-status",
        "name": "CUSTOMROM Agent — estado",
        "risk": "VERDE",
        "command": "if pm path com.customrom.agent >/dev/null 2>&1; then echo agent_installed=1; else echo agent_installed=0; exit 0; fi; if dumpsys package com.customrom.agent 2>/dev/null | grep -A8 'grantedPermissions' | grep -q 'android.permission.WRITE_SECURE_SETTINGS'; then echo write_secure_settings=granted; else echo write_secure_settings=missing; fi; echo adb_enabled=$(settings get global adb_enabled); echo adb_wifi_enabled=$(settings get global adb_wifi_enabled)",
        "output": "69_customrom_agent_status.txt",
    },
    {
        "id": "customrom-agent-preparar",
        "name": "Preparar CUSTOMROM Agent",
        "risk": "AMARELO",
        "command": "if ! pm path com.customrom.agent >/dev/null 2>&1; then echo 'CUSTOMROM Agent não instalado'; echo agent_installed=0; exit 2; fi; pm grant com.customrom.agent android.permission.WRITE_SECURE_SETTINGS 2>&1; am broadcast -a com.customrom.agent.APPLY -n com.customrom.agent/.BootReceiver 2>&1; echo agent_installed=1; if dumpsys package com.customrom.agent 2>/dev/null | grep -A8 'grantedPermissions' | grep -q 'android.permission.WRITE_SECURE_SETTINGS'; then echo write_secure_settings=granted; else echo write_secure_settings=missing; fi; echo adb_enabled=$(settings get global adb_enabled); echo adb_wifi_enabled=$(settings get global adb_wifi_enabled)",
        "output": "70_customrom_agent_preparado.txt",
    },
    {
        "id": "abrir-depuracao-sem-fio",
        "name": "Abrir opções de desenvolvedor",
        "risk": "AMARELO",
        "command": "am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS 2>/dev/null || am start -a android.settings.SETTINGS",
        "output": "71_abrir_opcoes_desenvolvedor.txt",
    },
]
for item in new_recipes:
    if item["id"] not in existing:
        recipes.append(item)
RECIPES.write_text(json.dumps(recipes, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# ---------------------------------------------------------------------------
# Companion agent: local fallback on TayTech + boot receiver.
# ---------------------------------------------------------------------------
settings_text = SETTINGS.read_text(encoding="utf-8")
if 'include(":agent")' not in settings_text:
    SETTINGS.write_text(settings_text.rstrip() + '\ninclude(":agent")\n', encoding="utf-8")

write(AGENT / "build.gradle.kts", '''plugins {
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
''')

write(AGENT / "src/main/AndroidManifest.xml", '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />

    <application
        android:allowBackup="false"
        android:label="CUSTOMROM Agent"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".AgentActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <receiver
            android:name=".BootReceiver"
            android:enabled="true"
            android:exported="true"
            android:directBootAware="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
                <action android:name="com.customrom.agent.APPLY" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
''')

write(AGENT / "src/main/java/com/customrom/agent/AdbRecovery.java", '''package com.customrom.agent;

import android.content.Context;
import android.provider.Settings;

final class AdbRecovery {
    private AdbRecovery() {}

    static String apply(Context context) {
        try {
            Settings.Global.putInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 1);
            Settings.Global.putInt(context.getContentResolver(), "adb_wifi_enabled", 1);
            int adb = Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            int wifi = Settings.Global.getInt(context.getContentResolver(), "adb_wifi_enabled", 0);
            return "ADB=" + adb + " · Wireless=" + wifi;
        } catch (SecurityException security) {
            return "Permissão necessária: prepare o Agent pelo CUSTOMROM no S23.";
        } catch (Throwable error) {
            return "Falha: " + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        }
    }

    static String status(Context context) {
        int adb = Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
        int wifi = Settings.Global.getInt(context.getContentResolver(), "adb_wifi_enabled", 0);
        return "ADB=" + adb + " · Wireless=" + wifi;
    }
}
''')

write(AGENT / "src/main/java/com/customrom/agent/BootReceiver.java", '''package com.customrom.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String result = AdbRecovery.apply(context);
        Log.i("CUSTOMROM-Agent", "action=" + intent.getAction() + " result=" + result);
    }
}
''')

write(AGENT / "src/main/java/com/customrom/agent/AgentActivity.java", '''package com.customrom.agent;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AgentActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(40, 40, 40, 40);
        root.setBackgroundColor(Color.rgb(5, 10, 19));

        TextView title = new TextView(this);
        title.setText("CUSTOMROM Agent");
        title.setTextSize(24f);
        title.setTextColor(Color.WHITE);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView detail = new TextView(this);
        detail.setText("Fallback local para reativar ADB/Wireless debugging na TayTech após o boot.");
        detail.setTextSize(14f);
        detail.setTextColor(Color.LTGRAY);
        detail.setPadding(0, 18, 0, 24);
        detail.setGravity(Gravity.CENTER);
        root.addView(detail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setText(AdbRecovery.status(this));
        status.setTextSize(16f);
        status.setTextColor(Color.rgb(58, 214, 151));
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button apply = new Button(this);
        apply.setText("Ativar ADB agora");
        apply.setOnClickListener(v -> status.setText(AdbRecovery.apply(this)));
        LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120);
        button.topMargin = 28;
        root.addView(apply, button);

        setContentView(root);
    }
}
''')

# ---------------------------------------------------------------------------
# Validator gains direct contract checks for the V6 behavior.
# ---------------------------------------------------------------------------
replace_once(
    VALIDATOR,
    'BUILD = APP / "app/build.gradle.kts"\n',
    'BUILD = APP / "app/build.gradle.kts"\nSETTINGS = APP / "settings.gradle.kts"\nAGENT_MANIFEST = APP / "agent/src/main/AndroidManifest.xml"\nAGENT_ACTIVITY = APP / "agent/src/main/java/com/customrom/agent/AgentActivity.java"\nAGENT_RECEIVER = APP / "agent/src/main/java/com/customrom/agent/BootReceiver.java"\n',
    'AGENT_MANIFEST = APP / "agent/src/main/AndroidManifest.xml"',
)

replace_once(
    VALIDATOR,
    '        BUILD,\n    ):\n',
    '        BUILD,\n        SETTINGS,\n        AGENT_MANIFEST,\n        AGENT_ACTIVITY,\n        AGENT_RECEIVER,\n    ):\n',
    '        AGENT_RECEIVER,\n    ):',
)

replace_once(
    VALIDATOR,
    '        "tempo-sistema",\n    }\n',
    '        "tempo-sistema",\n        "otimizacao-pragmatica",\n        "log-storm",\n        "adb-persistencia-diagnostico",\n        "adb-persistencia-aplicar",\n        "adb-persistencia-restaurar",\n        "customrom-agent-status",\n        "customrom-agent-preparar",\n        "abrir-depuracao-sem-fio",\n    }\n',
    '"customrom-agent-preparar",\n        "abrir-depuracao-sem-fio"',
)

replace_once(
    VALIDATOR,
    '    print("VALIDATE_NATIVE_CUSTOMROM=PASS")\n',
    '''    if 'com.omegas.v7.test' not in action_engine_src or 'ignoredOptimizationOwners' not in action_engine_src:
        fail("ÔMEGAS precisa ser excluído automaticamente do ranking de otimização")
    if 'ginlemon.flowerfree' not in ops_models_src or 'com.jancar.launcher' not in ops_models_src:
        fail("papéis dos launchers Smart/Jancar precisam estar explícitos na inteligência de packages")
    if 'include(\":agent\")' not in SETTINGS.read_text(encoding="utf-8"):
        fail("CUSTOMROM Agent precisa estar incluído no build")
    agent_manifest = AGENT_MANIFEST.read_text(encoding="utf-8")
    if 'android.permission.WRITE_SECURE_SETTINGS' not in agent_manifest or 'android.intent.action.BOOT_COMPLETED' not in agent_manifest:
        fail("CUSTOMROM Agent precisa declarar permissão e receiver de boot")

    print("VALIDATE_NATIVE_CUSTOMROM=PASS")
''',
    'CUSTOMROM Agent precisa declarar permissão e receiver de boot',
)

replace_once(
    VALIDATOR,
    '    print("mdns_reconnect=present")\n',
    '    print("mdns_reconnect=present")\n    print("pragmatic_optimization=present")\n    print("omegas_ignored_in_optimization=present")\n    print("launcher_roles=present")\n    print("adb_persistence_flow=present")\n    print("customrom_agent=present")\n    print("log_storm_diagnostic=present")\n',
    'print("pragmatic_optimization=present")',
)

print("APPLY_PRACTICAL_OPS_V6=OK")
print(f"recipes={len(recipes)}")
