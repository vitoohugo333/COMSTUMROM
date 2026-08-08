#!/usr/bin/env python3
"""Apply the read-only Spotify performance diagnostic to CUSTOMROM.

This follows the repository's existing apply-script pattern: the workflow mutates the
working tree, validates the generated sources, compiles the Android app, and commits
only verified generated source back to the active branch.
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RECIPES = ROOT / "apps/customrom-adb-native/app/src/main/assets/recipes.json"
ENGINE = ROOT / "apps/customrom-adb-native/app/src/main/java/com/customrom/adb/FunctionalActionEngine.kt"

RECIPE_ID = "spotify-diagnostico"

SPOTIFY_COMMAND = (
    "echo '=== SPOTIFY PACKAGE ==='; "
    "dumpsys package com.spotify.music 2>/dev/null | grep -E 'versionName=|versionCode=|minSdk=|targetSdk=|primaryCpuAbi=|secondaryCpuAbi=|codePath=|flags=' | head -n 100; "
    "echo; echo '=== SYSTEM COMPAT ==='; "
    "echo system_release=$(getprop ro.build.version.release); "
    "echo system_sdk=$(getprop ro.build.version.sdk); "
    "echo system_security_patch=$(getprop ro.build.version.security_patch); "
    "echo system_abi=$(getprop ro.product.cpu.abi); "
    "echo system_abilist=$(getprop ro.product.cpu.abilist); "
    "echo system_abilist64=$(getprop ro.product.cpu.abilist64); "
    "echo; echo '=== SPOTIFY PROCESS ==='; "
    "echo spotify_pid=$(pidof com.spotify.music 2>/dev/null); "
    "dumpsys meminfo com.spotify.music 2>/dev/null | head -n 180; "
    "echo; echo '=== SYSTEM MEMORY ==='; "
    "grep -E 'MemTotal|MemAvailable|SwapTotal|SwapFree' /proc/meminfo; "
    "echo; cat /proc/swaps 2>/dev/null; "
    "echo; echo '=== CPU RELEVANT ==='; "
    "dumpsys cpuinfo 2>/dev/null | grep -E 'com.spotify.music|com.google.android.gms|system_server|surfaceflinger|audioserver' | head -n 100; "
    "echo; echo '=== SPOTIFY GFX ==='; "
    "dumpsys gfxinfo com.spotify.music 2>/dev/null | head -n 240; "
    "echo; echo '=== AUDIO ==='; "
    "dumpsys audio 2>/dev/null | grep -i -E 'spotify|music|AudioTrack|focus|A2DP|active' | head -n 240; "
    "echo; echo '=== MEDIA SESSION ==='; "
    "dumpsys media_session 2>/dev/null | grep -i -E -A8 -B4 'spotify|com.spotify.music' | head -n 180; "
    "echo; echo '=== EXIT INFO ==='; "
    "dumpsys activity exit-info com.spotify.music 2>/dev/null | head -n 180; "
    "echo; echo '=== THERMAL ==='; "
    "dumpsys thermalservice 2>/dev/null | head -n 100; "
    "echo; echo '=== RECENT LOGS ==='; "
    "PID=$(pidof com.spotify.music 2>/dev/null); PID=${PID%% *}; "
    "if [ -n \"$PID\" ]; then "
    "logcat -d -v threadtime -t 1600 2>/dev/null | grep -E \" $PID |com\\.spotify\\.music|m\\.spotify\\.musi|AudioFlinger|AudioTrack|MediaCodec|CCodec|lmkd|lowmemorykiller|Choreographer|BluetoothA2dp\" | tail -n 500; "
    "else "
    "logcat -d -v threadtime -t 1200 2>/dev/null | grep -E 'com\\.spotify\\.music|m\\.spotify\\.musi|Spotify' | tail -n 300; "
    "fi"
)

ROUTE_ANCHOR = '        "tempo-sistema" -> simpleReport('
ROUTE_BLOCK = '''        "spotify-diagnostico" -> spotifyReport(raw)\n\n'''

METHOD_ANCHOR = '    private fun performanceReport(recipeId: String, raw: String): ActionableReport {'
METHOD_BLOCK = r'''    private fun spotifyReport(raw: String): ActionableReport {
        val findings = mutableListOf<String>()
        val actions = mutableListOf<FunctionalAction>()

        val version = inlineValue(raw, "versionName")
        val minSdk = inlineValue(raw, "minSdk")
        val targetSdk = inlineValue(raw, "targetSdk")
        val spotifyAbi = inlineValue(raw, "primaryCpuAbi")
        val release = lineValue(raw, "system_release")
        val sdk = lineValue(raw, "system_sdk")
        val patch = lineValue(raw, "system_security_patch")
        val systemAbi = lineValue(raw, "system_abi")
        val systemAbis = lineValue(raw, "system_abilist")
        val systemAbis64 = lineValue(raw, "system_abilist64")
        val spotifyPid = lineValue(raw, "spotify_pid")

        if (version != null) {
            findings += buildString {
                append("Spotify $version")
                if (minSdk != null || targetSdk != null) append(" · minSdk ${minSdk ?: "?"} · targetSdk ${targetSdk ?: "?"}")
                append('.')
            }
        }

        if (spotifyAbi != null && systemAbis != null) {
            val compatible = systemAbis.split(',').map { it.trim() }.contains(spotifyAbi)
            if (compatible) {
                findings += "Arquitetura compatível: o Spotify está em $spotifyAbi e a central oferece $systemAbis. ABI não explica a lentidão desta instalação."
            } else {
                findings += "Possível incompatibilidade de ABI: Spotify=$spotifyAbi, central=$systemAbis. Esta divergência precisa ser tratada antes de otimização."
            }
        } else if (systemAbi != null) {
            findings += "ABI principal da central: $systemAbi${if (systemAbis64.isNullOrBlank()) " · sem ABI 64-bit anunciada" else " · ABI64 $systemAbis64"}."
        }

        if (release != null || sdk != null) {
            findings += "Framework reportado: Android ${release ?: "?"} · SDK ${sdk ?: "?"}${patch?.let { " · patch $it" } ?: ""}."
            if (release == "13" && sdk == "30") {
                findings += "Combinação não padrão confirmada: a central anuncia release 13, mas expõe SDK 30. Isso aumenta o risco de comportamento irregular em aplicativos modernos mesmo quando a instalação é formalmente compatível."
            }
        }

        if (spotifyPid.isNullOrBlank()) {
            findings += "O Spotify não estava em execução nesta coleta; CPU, renderização e áudio do app ficam inconclusivos até repetir com ele aberto."
        } else {
            findings += "Spotify ativo nesta coleta · PID $spotifyPid."
        }

        val totalKb = metric(raw, "MemTotal")
        val availableKb = metric(raw, "MemAvailable")
        val swapTotalKb = metric(raw, "SwapTotal")
        val swapFreeKb = metric(raw, "SwapFree")
        if (totalKb != null && availableKb != null && totalKb > 0) {
            val pct = (availableKb * 100.0 / totalKb).toInt()
            findings += "Memória disponível do sistema: ${availableKb / 1024} MB de ${totalKb / 1024} MB ($pct%)."
            if (pct < 25) findings += "A margem de RAM está apertada; isso pode aumentar compactação, reclaim e disputa com processos de segundo plano."
        }
        if (swapTotalKb != null && swapTotalKb > 0 && swapFreeKb != null) {
            val usedKb = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
            findings += "Swap/ZRAM em uso: ${usedKb / 1024} MB de ${swapTotalKb / 1024} MB."
        }

        spotifyPssKb(raw)?.let { findings += "Spotify consumia aproximadamente ${it / 1024} MB de PSS no momento da coleta." }

        val cpuOwners = cpuPackageRegex.findAll(raw)
            .mapNotNull { match ->
                val cpu = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                cpu to match.groupValues[2].trimEnd(':')
            }
            .filter { (_, owner) ->
                owner == "com.spotify.music" || owner.startsWith("com.google.android.gms") || owner == "system_server" || owner == "surfaceflinger" || owner == "audioserver"
            }
            .distinctBy { it.second }
            .sortedByDescending { it.first }
            .toList()
        cpuOwners.take(8).forEach { (cpu, owner) -> findings += "CPU na fotografia: ${formatCpu(cpu)}% · $owner" }

        val spotifyCpu = cpuOwners.firstOrNull { it.second == "com.spotify.music" }?.first
        val gmsCpu = cpuOwners.firstOrNull { it.second.startsWith("com.google.android.gms") }?.first
        if (gmsCpu != null && gmsCpu >= 25.0) {
            findings += "Google Play Services está concorrendo fortemente por CPU nesta fotografia (${formatCpu(gmsCpu)}%). Isso pode degradar um app pesado mesmo sem defeito no Spotify."
        }
        if (spotifyCpu != null && spotifyCpu >= 75.0) {
            findings += "O próprio Spotify está usando perto de um núcleo inteiro ou mais nesta fotografia (${formatCpu(spotifyCpu)}%)."
        }

        val jank = Regex("Janky frames:\\s*(\\d+)\\s*\\(([0-9.]+)%\\)", RegexOption.IGNORE_CASE).find(raw)
        if (jank != null) {
            findings += "Renderização do Spotify: ${jank.groupValues[1]} frames janky (${jank.groupValues[2]}%). Isso é evidência direta de perda de fluidez na janela medida."
        }

        val spotifyGcMs = Regex("(?mi)^.*(?:m\\.spotify\\.musi|com\\.spotify\\.music).*GC.*total\\s+([0-9.]+)ms")
            .findAll(raw)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .maxOrNull()
        if (spotifyGcMs != null) {
            findings += "Maior ciclo de GC do Spotify visto nos logs recentes: ${formatMs(spotifyGcMs)} ms totais. Tempo total de GC não equivale automaticamente a uma pausa de interface."
        }

        val explicitAnr = Regex("(?mi)(ANR in com\\.spotify\\.music|am_anr.*com\\.spotify\\.music)").containsMatchIn(raw)
        val fatal = Regex("(?mi)(FATAL EXCEPTION.*(?:com\\.spotify\\.music|m\\.spotify\\.musi)|Process: com\\.spotify\\.music.*FATAL)").containsMatchIn(raw)
        when {
            explicitAnr -> findings += "Há evidência explícita de ANR do Spotify nesta coleta."
            fatal -> findings += "Há evidência explícita de crash fatal do Spotify nesta coleta."
            else -> findings += "Nenhum ANR/crash fatal do Spotify foi identificado pelos padrões estritos desta coleta. SIGQUIT/dump de stack isolado não é classificado como ANR."
        }

        if (Regex("(?mi)(OTHER KILLS BY SYSTEM.*empty|reason=13.*empty|empty for \\d+s)").containsMatchIn(raw)) {
            findings += "O histórico mostra descarte de processo vazio pelo sistema; isso é reclaim de background, não prova crash do Spotify."
        }
        if (Regex("(?mi)(A2DP.*disconnect|BluetoothA2dp.*disconnect|STATE_CONNECTED.*STATE_DISCONNECTED)").containsMatchIn(raw)) {
            findings += "Há menção recente a desconexão A2DP. Se o sintoma for corte/troca de áudio, a rota Bluetooth deve ser investigada separadamente do desempenho da UI."
        }

        actions += FunctionalAction("Abrir detalhe do Spotify", "Confere estado, logs e controles contextuais sem sair desta jornada.", ActionDestination.PACKAGE, "com.spotify.music")
        actions += FunctionalAction("Investigar Google Play Services", "Cruza a concorrência de CPU/memória com o principal serviço Google.", ActionDestination.PACKAGE, "com.google.android.gms")
        actions += FunctionalAction("Cruzar com CPU do sistema", "Compara Spotify com todos os consumidores pesados.", ActionDestination.RECIPE, "processos")
        actions += FunctionalAction("Cruzar com áudio", "Verifica foco, rota ativa e sessão de mídia.", ActionDestination.RECIPE, "audio-radio")
        actions += FunctionalAction("Cruzar com renderização", "Amplia a leitura de SurfaceFlinger e gfxinfo.", ActionDestination.RECIPE, "fluidez-gfx")
        actions += FunctionalAction("Ver crashes e ANRs", "Confere evidência histórica sem tratar dump de stack como ANR.", ActionDestination.RECIPE, "falhas-crashes")
        actions += FunctionalAction("Repetir com Spotify aberto", "Execute novamente durante a lentidão para obter uma fotografia causal melhor.", ActionDestination.RECIPE, "spotify-diagnostico")

        val abiCompatible = spotifyAbi != null && systemAbis?.split(',')?.map { it.trim() }?.contains(spotifyAbi) == true
        val summary = when {
            explicitAnr || fatal -> "A coleta encontrou instabilidade explícita do Spotify. A prioridade é correlacionar o evento com CPU, memória e áudio antes de alterar o sistema."
            spotifyPid.isNullOrBlank() -> "A compatibilidade estática foi analisada, mas falta a parte decisiva da prova: repetir enquanto o Spotify estiver aberto e lento."
            abiCompatible && (gmsCpu ?: 0.0) >= 25.0 -> "A ABI está correta. Nesta fotografia, a principal pista é concorrência de recursos do ambiente — especialmente Google Play Services — e não arquitetura incompatível."
            abiCompatible -> "A ABI está correta. O diagnóstico agora separa pressão de memória, CPU, renderização, áudio e peculiaridades do framework para localizar o gargalo real."
            else -> "A coleta foi estruturada para distinguir incompatibilidade de arquitetura de gargalos do sistema. Revise os achados antes de qualquer mudança."
        }

        return ActionableReport(
            "Por que o Spotify está lento?",
            summary,
            findings.ifEmpty { listOf("A build não expôs métricas suficientes para interpretar o Spotify nesta fotografia.") },
            actions.distinctBy { "${it.destination}:${it.target}:${it.label}" },
            "Ver coleta técnica do Spotify"
        )
    }

    private fun lineValue(raw: String, key: String): String? =
        Regex("(?m)^${Regex.escape(key)}=(.*)$").find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun inlineValue(raw: String, key: String): String? =
        Regex("\\b${Regex.escape(key)}=([^\\s]+)").find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() && it != "null" }

    private fun spotifyPssKb(raw: String): Long? =
        Regex("(?m)^\\s*TOTAL PSS:\\s*(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: Regex("(?m)^\\s*TOTAL\\s+(\\d+)\\s+").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()

    private fun formatMs(ms: Double): String =
        if (ms % 1.0 == 0.0) ms.toInt().toString() else String.format(Locale.US, "%.1f", ms)

'''


def add_recipe() -> bool:
    recipes = json.loads(RECIPES.read_text(encoding="utf-8"))
    if any(item.get("id") == RECIPE_ID for item in recipes):
        return False
    recipes.append(
        {
            "id": RECIPE_ID,
            "name": "Por que o Spotify está lento?",
            "risk": "VERDE",
            "command": SPOTIFY_COMMAND,
            "output": "63_spotify_diagnostico.txt",
        }
    )
    RECIPES.write_text(json.dumps(recipes, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return True


def patch_engine() -> bool:
    source = ENGINE.read_text(encoding="utf-8")
    changed = False
    if '"spotify-diagnostico" -> spotifyReport(raw)' not in source:
        if ROUTE_ANCHOR not in source:
            raise SystemExit("Spotify diagnostic: route anchor not found")
        source = source.replace(ROUTE_ANCHOR, ROUTE_BLOCK + ROUTE_ANCHOR, 1)
        changed = True
    if "private fun spotifyReport(raw: String)" not in source:
        if METHOD_ANCHOR not in source:
            raise SystemExit("Spotify diagnostic: method anchor not found")
        source = source.replace(METHOD_ANCHOR, METHOD_BLOCK + METHOD_ANCHOR, 1)
        changed = True
    if changed:
        ENGINE.write_text(source, encoding="utf-8")
    return changed


def verify_contract() -> None:
    recipes = json.loads(RECIPES.read_text(encoding="utf-8"))
    ids = [item["id"] for item in recipes]
    if len(ids) != len(set(ids)):
        raise SystemExit("Spotify diagnostic: duplicate recipe ids")
    recipe = next((item for item in recipes if item["id"] == RECIPE_ID), None)
    if not recipe:
        raise SystemExit("Spotify diagnostic: recipe missing after apply")
    if recipe["risk"] != "VERDE":
        raise SystemExit("Spotify diagnostic: recipe must remain read-only/VERDE")
    forbidden = ("pm disable", "pm enable", "pm clear", "am force-stop", "settings put", "rm -rf", "reboot", "fastboot", " flash ")
    normalized = " " + recipe["command"].lower() + " "
    hit = next((token for token in forbidden if token in normalized), None)
    if hit:
        raise SystemExit(f"Spotify diagnostic: forbidden mutating token in read-only recipe: {hit}")

    source = ENGINE.read_text(encoding="utf-8")
    required = (
        '"spotify-diagnostico" -> spotifyReport(raw)',
        "private fun spotifyReport(raw: String)",
        "Arquitetura compatível",
        "SIGQUIT/dump de stack isolado não é classificado como ANR",
        "Repetir com Spotify aberto",
    )
    missing = [token for token in required if token not in source]
    if missing:
        raise SystemExit("Spotify diagnostic: engine contract missing: " + ", ".join(missing))


if __name__ == "__main__":
    recipe_changed = add_recipe()
    engine_changed = patch_engine()
    verify_contract()
    print(f"SPOTIFY_DIAGNOSTIC_APPLY=PASS recipe_changed={int(recipe_changed)} engine_changed={int(engine_changed)}")
