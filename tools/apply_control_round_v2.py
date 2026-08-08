#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "apps/customrom-adb-native/app/src/main/java/com/customrom/adb"
ACTIVITY = JAVA / "PremiumOpsActivity.kt"
MODELS = JAVA / "PremiumOpsModels.kt"
ENGINE = JAVA / "FunctionalActionEngine.kt"
RECIPES = ROOT / "apps/customrom-adb-native/app/src/main/assets/recipes.json"
VALIDATOR = ROOT / "tools/validate_native_customrom.py"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 occurrence, found {count}")
    return text.replace(old, new, 1)


def patch_activity() -> None:
    text = ACTIVITY.read_text(encoding="utf-8")
    if "import android.widget.HorizontalScrollView" not in text:
        text = replace_once(text, "import android.widget.FrameLayout\n", "import android.widget.FrameLayout\nimport android.widget.HorizontalScrollView\n", "horizontal import")

    text = replace_once(
        text,
        'val scroll = ScrollView(this).apply { isHorizontalScrollBarEnabled = false }',
        'val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }',
        "app filters horizontal scroll",
    )

    text = replace_once(
        text,
        'report.actions.take(8).forEach { action ->',
        'report.actions.take(64).forEach { action ->',
        "actionable modal action cap",
    )

    text = replace_once(
        text,
        'ActionDestination.PACKAGE -> openPackageFromAction(action.target)',
        'ActionDestination.PACKAGE -> openPackageContext(action.target)',
        "contextual package action",
    )

    insert_marker = '''    private fun openAppsFilter(filter: String) {'''
    contextual_method = '''    private fun openPackageContext(packageNameRaw: String) {
        val pkg = sanitizePackage(packageNameRaw) ?: return
        appPackages.firstOrNull { it.packageName == pkg }?.let {
            showAppDetail(it)
            return
        }
        val command = "echo '__PATH__'; pm path $pkg 2>/dev/null; echo '__DISABLED__'; pm list packages -d 2>/dev/null | grep -Fx 'package:$pkg' || true; echo '__PID__'; pidof $pkg 2>/dev/null || true; echo '__DETAIL__'; dumpsys package $pkg 2>/dev/null | head -n 320"
        executeOperation("Preparar ${PackageIntelligence.friendlyName(pkg)}", command, "VERDE", showDialog = false) { outcome, result ->
            if (!result.success) {
                showTechnicalResult(result, combineRaw(outcome))
                return@executeOperation
            }
            val lines = outcome.stdout.lineSequence().map { it.trim() }.toList()
            val path = lines.firstOrNull { it.startsWith("package:") }?.removePrefix("package:").orEmpty()
            val disabled = lines.any { it == "package:$pkg" }
            val pidIndex = lines.indexOf("__PID__")
            val running = pidIndex >= 0 && lines.drop(pidIndex + 1).takeWhile { !it.startsWith("__") }.any { it.matches(Regex("[0-9 ]+")) && it.any(Char::isDigit) }
            val kind = if (path.contains("/data/app/")) "Usuário" else "Sistema"
            val snapshot = PackageSnapshot(pkg, path, kind, disabled, running, metadata = outcome.stdout)
            appPackages.removeAll { it.packageName == pkg }
            appPackages.add(snapshot)
            showAppDetail(snapshot)
        }
    }

'''
    if "private fun openPackageContext(" not in text:
        text = replace_once(text, insert_marker, contextual_method + insert_marker, "contextual package method")

    old_mutable = '''        val mutableAllowed = assessment.criticality == PackageCriticality.LOW || assessment.criticality == PackageCriticality.MEDIUM
        if (mutableAllowed) {'''
    new_mutable = '''        val mutableAllowed = assessment.criticality != PackageCriticality.PROTECTED
        if (assessment.criticality == PackageCriticality.HIGH || assessment.criticality == PackageCriticality.UNKNOWN) {
            panel.addView(callout("Controle avançado", "Este package pode ter dependências relevantes. O CUSTOMROM não o classifica como automotivo protegido, mas exige decisão consciente. A ação usa somente user 0, mostra o comando antes de executar e mantém rollback no ledger."), margins(top = 12))
        }
        if (mutableAllowed) {'''
    text = replace_once(text, old_mutable, new_mutable, "advanced mutable gate")

    old_disable_block = '''            if (snapshot.disabled && ledger.wasDisabledByCustomrom(snapshot.packageName)) {
                panel.addView(softButton("Restaurar aplicativo") { dialog.dismiss(); restorePackage(snapshot) }, margins(top = 8))
            } else if (!snapshot.disabled) {
                panel.addView(dangerButton("Desativar reversivelmente") { dialog.dismiss(); disablePackage(snapshot) }, margins(top = 8))
            } else {
                panel.addView(text("Este package já está desativado, mas o CUSTOMROM não possui evidência de ter feito essa alteração. Por segurança, não afirma rollback automático.", 11f, warning, false), margins(top = 10))
            }'''
    new_disable_block = '''            if (snapshot.disabled) {
                val enableLabel = if (ledger.wasDisabledByCustomrom(snapshot.packageName)) "Restaurar alteração do CUSTOMROM" else "Ativar para usuário 0"
                panel.addView(softButton(enableLabel) { dialog.dismiss(); enablePackage(snapshot) }, margins(top = 8))
            } else {
                val disableLabel = if (snapshot.kind == "Sistema") "Desativar para usuário 0 (avançado)" else "Desativar reversivelmente"
                panel.addView(dangerButton(disableLabel) { dialog.dismiss(); disablePackage(snapshot) }, margins(top = 8))
            }
            panel.addView(softButton("Logs recentes deste app") { dialog.dismiss(); showPackageLog(snapshot.packageName) }, margins(top = 8))
            panel.addView(softButton("Abrir app na TayTech") { dialog.dismiss(); launchPackage(snapshot.packageName) }, margins(top = 8))'''
    text = replace_once(text, old_disable_block, new_disable_block, "enable disable contextual controls")

    old_protection = '''        } else {
            panel.addView(callout("Proteção ativa", "O fluxo comum não oferece Parar/Desativar para criticidade ${assessment.criticality.label}. Analise primeiro e preserve funções do sistema/veículo."), margins(top = 12))
        }
        panel.addView(softButton("Fechar") { dialog.dismiss() }, margins(top = 10))'''
    new_protection = '''        } else {
            panel.addView(callout("Proteção automotiva ativa", "Este package possui evidência forte de função CAN/MCU/HVAC/rádio/câmera/ACC ou núcleo Android essencial. O CUSTOMROM mantém Parar/Desativar fora deste fluxo para evitar perda de função veicular."), margins(top = 12))
            panel.addView(softButton("Logs recentes deste app") { dialog.dismiss(); showPackageLog(snapshot.packageName) }, margins(top = 8))
        }
        panel.addView(softButton("Abrir na aba Apps") { dialog.dismiss(); openPackageFromAction(snapshot.packageName) }, margins(top = 10))
        panel.addView(softButton("Fechar") { dialog.dismiss() }, margins(top = 8))'''
    text = replace_once(text, old_protection, new_protection, "protected contextual controls")

    disable_marker = '''    private fun restorePackage(snapshot: PackageSnapshot) {'''
    extra_methods = '''    private fun enablePackage(snapshot: PackageSnapshot) {
        val pkg = sanitizePackage(snapshot.packageName) ?: return
        val command = "pm enable --user 0 $pkg >/dev/null 2>&1; if pm list packages -d 2>/dev/null | grep -Fxq 'package:$pkg'; then echo 'Falha: package continua desativado'; exit 2; else echo 'Package ativo para usuário 0'; fi"
        executeOperation("Ativar ${PackageIntelligence.friendlyName(pkg)}", command, "AMARELO", showDialog = true) { outcome, result ->
            if (result.success) {
                ledger.append(ChangeRecord(pkg, "enable", "disabled", "enabled", System.currentTimeMillis(), session?.id ?: "", outcome.exitCode, "pm disable-user --user 0 $pkg"))
                updatePackage(pkg) { it.copy(disabled = false) }
            }
        }
    }

    private fun showPackageLog(packageNameRaw: String) {
        val pkg = sanitizePackage(packageNameRaw) ?: return
        val command = "PID=$(pidof $pkg 2>/dev/null | awk '{print $1}'); if [ -n \"$PID\" ]; then logcat -d -v threadtime --pid=$PID -t 500 2>/dev/null || logcat -d -v threadtime -t 1200 2>/dev/null | grep -F '$pkg' | tail -n 500; else echo 'Package não está rodando; buscando referências recentes'; logcat -d -v threadtime -t 1600 2>/dev/null | grep -F '$pkg' | tail -n 500; fi"
        executeOperation("Logs de ${PackageIntelligence.friendlyName(pkg)}", command, "VERDE", showDialog = true) { _, _ -> }
    }

    private fun launchPackage(packageNameRaw: String) {
        val pkg = sanitizePackage(packageNameRaw) ?: return
        val command = "monkey -p $pkg -c android.intent.category.LAUNCHER 1 2>/dev/null || cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $pkg 2>/dev/null"
        executeOperation("Abrir ${PackageIntelligence.friendlyName(pkg)} na TayTech", command, "AMARELO", showDialog = true) { _, _ -> }
    }

'''
    if "private fun enablePackage(" not in text:
        text = replace_once(text, disable_marker, extra_methods + disable_marker, "advanced package methods")

    old_disable_command = '''        executeOperation("Desativar ${PackageIntelligence.friendlyName(pkg)}", "pm disable-user --user 0 $pkg", "AMARELO", showDialog = true) { outcome, result ->'''
    new_disable_command = '''        val command = "pm disable-user --user 0 $pkg >/dev/null 2>&1; RC=$?; echo '__VERIFY__'; pm list packages -d 2>/dev/null | grep -Fx 'package:$pkg'; [ $RC -eq 0 ]"
        executeOperation("Desativar ${PackageIntelligence.friendlyName(pkg)}", command, "AMARELO", showDialog = true) { outcome, result ->'''
    text = replace_once(text, old_disable_command, new_disable_command, "verified disable command")

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
    text = replace_once(text, old_dialog, new_dialog, "scrollable premium dialog")

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
    text = replace_once(text, old_desc, new_desc, "new recipe descriptions")

    old_category = '''        id.contains("thermal") || id.contains("memoria") || id.contains("process") || id.contains("fluidez") || id.contains("lentidao") -> "PERFORMANCE"
        else -> "DIAGNÓSTICO"'''
    new_category = '''        id.contains("thermal") || id.contains("memoria") || id.contains("process") || id.contains("fluidez") || id.contains("lentidao") || id.contains("batterystats") -> "PERFORMANCE"
        id.contains("netstats") || id.contains("ethernet") -> "CONECTIVIDADE"
        id.contains("sensor") || id.contains("camera") || id.contains("gnss") || id.contains("localizacao") -> "HARDWARE"
        id.contains("appops") || id.contains("foreground") || id.contains("usage") || id.contains("uso-apps") || id.contains("launcher") || id.contains("pacotes") -> "APPS / SISTEMA"
        else -> "DIAGNÓSTICO"'''
    text = replace_once(text, old_category, new_category, "new recipe categories")

    ACTIVITY.write_text(text, encoding="utf-8")


def patch_models() -> None:
    text = MODELS.read_text(encoding="utf-8")
    text = replace_once(
        text,
        '''        "canbus", "canbox", "jancar", "hiworld", "mcu", "vehicle", "carservice", "car.service",
        "hvac", "climate", "reverse", "rearview", "camera", "parking", "sensor", "steering",
        "radio", "dsp", "amplifier", "audio", "bluetooth", "btservice", "accservice", "sleep", "wake"''',
        '''        "canbus", "canbox", "hiworld", "mcu", "vehicle", "carservice", "car.service",
        "hvac", "climate", "reverse", "rearview", "camera", "parking", "sensor", "steering",
        "radio", "dsp", "amplifier", "accservice", "sleep", "wake"''',
        "automotive token refinement",
    )
    text = replace_once(
        text,
        '''        "telephony", "phone", "launcher", "inputmethod", "providers.settings", "externalstorage",
        "downloads.provider", "documentsui", "wifi", "ethernet", "location", "framework"''',
        '''        "telephony", "phone", "launcher", "inputmethod", "providers.settings", "externalstorage",
        "downloads.provider", "documentsui", "wifi", "ethernet", "location", "framework", "bluetooth", "btservice", "audio"''',
        "core token refinement",
    )
    text = replace_once(
        text,
        '''            return PackageAssessment(PackageCriticality.MEDIUM, AssessmentConfidence.MEDIUM, reasons, false)''',
        '''            reasons += "pode ser testado de forma reversível no usuário 0, com confirmação e rollback"
            return PackageAssessment(PackageCriticality.MEDIUM, AssessmentConfidence.MEDIUM, reasons, true)''',
        "system reversible candidate",
    )
    MODELS.write_text(text, encoding="utf-8")


def patch_engine() -> None:
    text = ENGINE.read_text(encoding="utf-8")
    text = replace_once(text, "val actions = preferred.take(10).map { pkg ->", "val actions = preferred.take(60).map { pkg ->", "package action breadth")
    # There are two caps of 12; raising both is intentional: performance remains small because its producer is bounded,
    # while package discovery can expose every object found in a normal diagnostic.
    text = text.replace('.distinctBy { "${it.destination}:${it.target}:${it.label}" }.take(12)', '.distinctBy { "${it.destination}:${it.target}:${it.label}" }.take(64)')
    ENGINE.write_text(text, encoding="utf-8")


def patch_recipes() -> None:
    recipes = json.loads(RECIPES.read_text(encoding="utf-8"))
    existing = {r["id"] for r in recipes}
    additions = [
        {"id":"foreground-services","name":"Serviços persistentes e foreground","risk":"VERDE","command":"echo '=== FOREGROUND / SERVICES ==='; dumpsys activity services 2>/dev/null | grep -E -i -B2 -A8 'foreground|startRequested=true|isForeground=true|packageName=' | head -n 700","output":"45_foreground_services.txt"},
        {"id":"appops-auditoria","name":"Permissões especiais e AppOps","risk":"VERDE","command":"echo '=== APPOPS ==='; dumpsys appops 2>/dev/null | head -n 900","output":"46_appops.txt"},
        {"id":"batterystats-apps","name":"Consumo por apps desde a carga","risk":"VERDE","command":"echo '=== BATTERYSTATS ==='; dumpsys batterystats --charged 2>/dev/null | head -n 1000","output":"47_batterystats_apps.txt"},
        {"id":"uso-apps","name":"Aplicativos usados recentemente","risk":"VERDE","command":"echo '=== USAGESTATS ==='; dumpsys usagestats 2>/dev/null | head -n 900","output":"48_usage_apps.txt"},
        {"id":"deviceidle-whitelist","name":"Exceções de economia de energia","risk":"VERDE","command":"echo '=== DEVICEIDLE WHITELIST ==='; cmd deviceidle whitelist 2>/dev/null; echo; dumpsys deviceidle 2>/dev/null | grep -E -i -A120 'Whitelist|mPowerSaveWhitelistApps|mExceptIdleWhitelistApps' | head -n 360","output":"49_deviceidle_whitelist.txt"},
        {"id":"launchers-disponiveis","name":"Launchers disponíveis na central","risk":"VERDE","command":"echo '=== HOME CANDIDATES ==='; cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null; echo; echo '=== HOME RESOLVIDO ==='; cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null","output":"50_launchers.txt"},
        {"id":"webview-provider","name":"WebView e provider atual","risk":"VERDE","command":"echo '=== WEBVIEW ==='; dumpsys webviewupdate 2>/dev/null | head -n 420","output":"51_webview.txt"},
        {"id":"localizacao-gnss","name":"Localização e GNSS","risk":"VERDE","command":"echo '=== LOCATION ==='; dumpsys location 2>/dev/null | head -n 800","output":"52_localizacao_gnss.txt"},
        {"id":"sensores-status","name":"Sensores e clientes ativos","risk":"VERDE","command":"echo '=== SENSOR SERVICE ==='; dumpsys sensorservice 2>/dev/null | head -n 800","output":"53_sensores.txt"},
        {"id":"camera-status","name":"Câmeras e clientes ativos","risk":"VERDE","command":"echo '=== CAMERA SERVICE ==='; dumpsys media.camera 2>/dev/null | head -n 650","output":"54_camera.txt"},
        {"id":"processos-oom","name":"Prioridade e pressão dos processos","risk":"VERDE","command":"echo '=== ACTIVITY PROCESSES ==='; dumpsys activity processes 2>/dev/null | head -n 1000","output":"55_processos_oom.txt"},
        {"id":"rede-netstats","name":"Tráfego e estatísticas de rede","risk":"VERDE","command":"echo '=== NETSTATS ==='; dumpsys netstats 2>/dev/null | head -n 900","output":"56_netstats.txt"},
        {"id":"device-policy","name":"Políticas e administradores do dispositivo","risk":"VERDE","command":"echo '=== DEVICE POLICY ==='; dumpsys device_policy 2>/dev/null | head -n 700","output":"57_device_policy.txt"},
        {"id":"notificacoes-status","name":"Notificações, listeners e canais","risk":"VERDE","command":"echo '=== NOTIFICATION ==='; dumpsys notification 2>/dev/null | head -n 900","output":"58_notificacoes.txt"},
        {"id":"pacotes-instaladores","name":"Origem de instalação dos aplicativos","risk":"VERDE","command":"echo '=== PACKAGES + INSTALLER ==='; pm list packages -f -i -u 2>/dev/null","output":"59_pacotes_instaladores.txt"},
        {"id":"background-limits","name":"Limites de processos em segundo plano","risk":"VERDE","command":"echo 'background_process_limit='$(settings get global background_process_limit); echo 'cached_apps_freezer='$(settings get global cached_apps_freezer); echo 'activity_manager_constants='$(settings get global activity_manager_constants)","output":"60_background_limits.txt"},
        {"id":"ethernet-status","name":"Ethernet e rede cabeada","risk":"VERDE","command":"echo '=== ETHERNET ==='; dumpsys ethernet 2>/dev/null | head -n 500; echo; ip link 2>/dev/null","output":"61_ethernet.txt"},
        {"id":"tempo-sistema","name":"Horário, timezone e sincronização","risk":"VERDE","command":"echo '=== DATE ==='; date; echo 'timezone='$(getprop persist.sys.timezone); echo 'auto_time='$(settings get global auto_time); echo 'auto_time_zone='$(settings get global auto_time_zone)","output":"62_tempo_sistema.txt"}
    ]
    for recipe in additions:
        if recipe["id"] not in existing:
            recipes.append(recipe)
    RECIPES.write_text(json.dumps(recipes, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def patch_validator() -> None:
    text = VALIDATOR.read_text(encoding="utf-8")
    text = replace_once(text, '    "openPackageFromAction",\n', '    "openPackageContext",\n    "HorizontalScrollView",\n    "isVerticalScrollBarEnabled = true",\n    "Desativar para usuário 0 (avançado)",\n    "Logs recentes deste app",\n', "actionable v2 validator signals")
    text = replace_once(text, 'if not isinstance(recipes, list) or len(recipes) < 40:', 'if not isinstance(recipes, list) or len(recipes) < 60:', "recipe minimum")
    text = replace_once(text, 'fail("catálogo premium expandido precisa manter pelo menos 40 rotinas úteis")', 'fail("catálogo premium expandido precisa manter pelo menos 60 rotinas úteis")', "recipe minimum message")
    old_required_tail = '''        "home-remoto",
    }'''
    new_required_tail = '''        "home-remoto",
        "foreground-services",
        "appops-auditoria",
        "batterystats-apps",
        "uso-apps",
        "deviceidle-whitelist",
        "launchers-disponiveis",
        "webview-provider",
        "localizacao-gnss",
        "sensores-status",
        "camera-status",
        "processos-oom",
        "rede-netstats",
        "device-policy",
        "notificacoes-status",
        "pacotes-instaladores",
        "background-limits",
        "ethernet-status",
        "tempo-sistema",
    }'''
    text = replace_once(text, old_required_tail, new_required_tail, "new required recipes")
    text = replace_once(text, 'print("actionable_boot_crash_wake_jobs=present")', 'print("actionable_boot_crash_wake_jobs=present")\n    print("scrollable_actionable_dialog=present")\n    print("contextual_package_control=present")\n    print("advanced_system_disable_user0=present")\n    print("recipe_catalog_minimum=60")', "new validation output")
    VALIDATOR.write_text(text, encoding="utf-8")


def main() -> None:
    patch_activity()
    patch_models()
    patch_engine()
    patch_recipes()
    patch_validator()
    print("APPLY_CONTROL_ROUND_V2=PASS")


if __name__ == "__main__":
    main()
