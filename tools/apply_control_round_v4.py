#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
sys.path.insert(0, str(TOOLS))
import apply_control_round_v3 as v3

ACTIVITY = ROOT / "apps/customrom-adb-native/app/src/main/java/com/customrom/adb/PremiumOpsActivity.kt"


def between(text: str, start: str, end: str, replacement: str, label: str) -> str:
    i = text.find(start)
    if i < 0:
        raise SystemExit(f"{label}: start marker not found")
    j = text.find(end, i)
    if j < 0:
        raise SystemExit(f"{label}: end marker not found")
    return text[:i] + replacement + text[j:]


def patch_activity() -> None:
    s = ACTIVITY.read_text(encoding="utf-8")
    s = v3.once(s, "import android.widget.FrameLayout\n", "import android.widget.FrameLayout\nimport android.widget.HorizontalScrollView\n", "horizontal import")
    s = v3.once(s, 'val scroll = ScrollView(this).apply { isHorizontalScrollBarEnabled = false }', 'val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }', "horizontal filters")
    s = v3.once(s, 'ActionDestination.PACKAGE -> openPackageFromAction(action.target)', 'ActionDestination.PACKAGE -> openPackageContext(action.target)', "contextual package destination")

    old_row = '''            report.actions.take(8).forEach { action ->
                val row = functionalActionRow(action) {
                    dialog.dismiss()
                    performFunctionalAction(action)
                }
                panel.addView(row, margins(top = 8))
            }'''
    new_row = '''            report.actions.take(64).forEach { action ->
                val row = functionalActionRow(action) {
                    if (action.destination == ActionDestination.PACKAGE) {
                        performFunctionalAction(action)
                    } else {
                        dialog.dismiss()
                        performFunctionalAction(action)
                    }
                }
                panel.addView(row, margins(top = 8))
            }'''
    s = v3.once(s, old_row, new_row, "preserve actionable parent modal")

    marker = '''    private fun openAppsFilter(filter: String) {'''
    contextual = '''    private fun openPackageContext(packageNameRaw: String) {
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

'''
    s = v3.once(s, marker, contextual + marker, "open package context")

    detail = '''    private fun showAppDetail(snapshotInput: PackageSnapshot) {
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

'''
    s = between(s, "    private fun showAppDetail(snapshotInput: PackageSnapshot) {", "    private fun inspectPackage(packageNameRaw: String) {", detail, "showAppDetail")

    inspect = '''    private fun inspectPackage(packageNameRaw: String) {
        val pkg = sanitizePackage(packageNameRaw) ?: return
        val command = "echo '=== PACKAGE ==='; dumpsys package $pkg 2>/dev/null | head -n 500; echo; echo '=== PID ==='; pidof $pkg 2>/dev/null; echo; echo '=== MEMINFO ==='; dumpsys meminfo $pkg 2>/dev/null | head -n 180; echo; echo '=== SERVICES MATCH ==='; dumpsys activity services 2>/dev/null | grep -i -B2 -A5 '$pkg' | head -n 160"
        executeOperation("Analisar ${PackageIntelligence.friendlyName(pkg)}", command, "VERDE", showDialog = false) { outcome, result ->
            if (result.success) {
                val index = appPackages.indexOfFirst { it.packageName == pkg }
                val base = if (index >= 0) appPackages[index] else PackageSnapshot(pkg)
                val updated = base.copy(metadata = outcome.stdout, running = outcome.stdout.contains("PID", true) && Regex("\\b[0-9]{2,}\\b").containsMatchIn(outcome.stdout))
                if (index >= 0) appPackages[index] = updated else appPackages.add(updated)
                refreshAppList()
                showAppDetail(updated)
            } else showTechnicalResult(result, combineRaw(outcome))
        }
    }

'''
    s = between(s, "    private fun inspectPackage(packageNameRaw: String) {", "    private fun forceStopPackage(snapshot: PackageSnapshot) {", inspect, "inspectPackage")

    force = '''    private fun forceStopPackage(snapshot: PackageSnapshot) {
        val pkg = sanitizePackage(snapshot.packageName) ?: return
        executeOperation("Parar ${PackageIntelligence.friendlyName(pkg)}", "am force-stop --user 0 $pkg; echo 'Aplicativo interrompido temporariamente'", "AMARELO", showDialog = false) { outcome, result ->
            if (result.success) {
                ledger.append(ChangeRecord(pkg, "force-stop", if (snapshot.running) "running" else "unknown", "stopped", System.currentTimeMillis(), session?.id ?: "", outcome.exitCode, ""))
                updatePackage(pkg) { it.copy(running = false) }
                appPackages.firstOrNull { it.packageName == pkg }?.let(::showAppDetail)
            } else showTechnicalResult(result, combineRaw(outcome))
        }
    }

'''
    s = between(s, "    private fun forceStopPackage(snapshot: PackageSnapshot) {", "    private fun disablePackage(snapshot: PackageSnapshot) {", force, "forceStop")

    disable = '''    private fun disablePackage(snapshot: PackageSnapshot) {
        val pkg = sanitizePackage(snapshot.packageName) ?: return
        val command = "pm disable-user --user 0 $pkg >/dev/null 2>&1; RC=$?; if pm list packages -d 2>/dev/null | grep -Fxq 'package:$pkg'; then echo 'Package desativado para usuário 0'; exit 0; else echo 'Falha: package não aparece como desativado'; exit ${'$'}RC; fi"
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
        val command = "pm enable --user 0 $pkg >/dev/null 2>&1; RC=$?; if pm list packages -d 2>/dev/null | grep -Fxq 'package:$pkg'; then echo 'Falha: package continua desativado'; exit 2; else echo 'Package ativo para usuário 0'; exit ${'$'}RC; fi"
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
        val command = "PID=$(pidof $pkg 2>/dev/null | awk '{print $1}'); if [ -n \"$PID\" ]; then logcat -d -v threadtime --pid=$PID -t 500 2>/dev/null || logcat -d -v threadtime -t 1200 2>/dev/null | grep -F '$pkg' | tail -n 500; else echo 'Package não está rodando; buscando referências recentes'; logcat -d -v threadtime -t 1600 2>/dev/null | grep -F '$pkg' | tail -n 500; fi"
        executeOperation("Logs de ${PackageIntelligence.friendlyName(pkg)}", command, "VERDE", showDialog = true) { _, _ -> }
    }

    private fun launchPackage(packageNameRaw: String) {
        val pkg = sanitizePackage(packageNameRaw) ?: return
        executeOperation("Abrir ${PackageIntelligence.friendlyName(pkg)} na TayTech", "monkey -p $pkg -c android.intent.category.LAUNCHER 1 2>/dev/null", "AMARELO", showDialog = true) { _, _ -> }
    }

'''
    s = between(s, "    private fun disablePackage(snapshot: PackageSnapshot) {", "    private fun restorePackage(snapshot: PackageSnapshot) {", disable, "disable/enable package")

    old_diag = '''        root.addView(featureAction("⇄", "O que trabalha em segundo plano?", "Jobs agendados viram owners investigáveis em vez de dump bruto.") { runRecipeById("jobs-agendados") }, margins(top = 8))'''
    new_diag = old_diag + '''
        root.addView(featureAction("●", "Quais serviços ficam sempre ativos?", "Foreground/persistent services viram packages acionáveis.") { runRecipeById("foreground-services") }, margins(top = 8))
        root.addView(featureAction("⌁", "Quais apps realmente foram usados?", "UsageStats ajuda a separar uso real de software apenas residente.") { runRecipeById("uso-apps") }, margins(top = 8))
        root.addView(featureAction("⚡", "Quem consumiu energia?", "Batterystats cruza atividade por UID/package desde a referência disponível.") { runRecipeById("batterystats-apps") }, margins(top = 8))
        root.addView(featureAction("⌂", "Quais launchers existem?", "Lista candidatos HOME e permite investigar cada launcher sem sair do fluxo.") { runRecipeById("launchers-disponiveis") }, margins(top = 8))'''
    s = v3.once(s, old_diag, new_diag, "diagnostic journeys")

    old_desc = '''        "diagnostico-lentidao" -> "Workflow composto: memória + CPU + top + disco + thermal."
        else -> "Rotina versionada do CUSTOMROM."'''
    new_desc = '''        "diagnostico-lentidao" -> "Workflow composto: memória + CPU + top + disco + thermal."
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
        else -> "Rotina versionada do CUSTOMROM."'''
    s = v3.once(s, old_desc, new_desc, "descriptions")

    old_dialog = '''    private fun premiumDialog(panel: View): AlertDialog = AlertDialog.Builder(this).setView(panel).create()'''
    new_dialog = '''    private fun premiumDialog(panel: View): AlertDialog {
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
    }'''
    s = v3.once(s, old_dialog, new_dialog, "scrollable dialogs")
    ACTIVITY.write_text(s, encoding="utf-8")


def main() -> None:
    v3.patch_models()
    patch_activity()
    v3.patch_recipes()
    v3.patch_validator()
    print("APPLY_CONTROL_ROUND_V4=PASS")


if __name__ == "__main__":
    main()
