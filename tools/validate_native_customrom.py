#!/usr/bin/env python3
"""Verificações estáticas determinísticas do app nativo CUSTOMROM ADB.

Não substitui compilação nem teste Android. O objetivo é impedir regressões óbvias
em capacidades obrigatórias antes do Gradle rodar.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps" / "customrom-adb-native"
MAIN = APP / "app/src/main/java/com/customrom/adb/MainActivity.kt"
PREMIUM = APP / "app/src/main/java/com/customrom/adb/PremiumMainActivity.kt"
OPS = APP / "app/src/main/java/com/customrom/adb/PremiumOpsActivity.kt"
OPS_MODELS = APP / "app/src/main/java/com/customrom/adb/PremiumOpsModels.kt"
ADB_CONTROLLER = APP / "app/src/main/java/com/customrom/adb/AdbRemoteController.kt"
PREMIUM_MODELS = APP / "app/src/main/java/com/customrom/adb/PremiumModels.kt"
APPLICATION = APP / "app/src/main/java/com/customrom/adb/CustomromApp.kt"
MANIFEST = APP / "app/src/main/AndroidManifest.xml"
RECIPES = APP / "app/src/main/assets/recipes.json"
BUILD = APP / "app/build.gradle.kts"

MUTATING = [
    r"\bpm\s+disable",
    r"\bpm\s+enable",
    r"\bpm\s+uninstall",
    r"\bam\s+force-stop",
    r"\bsettings\s+put",
    r"\bpm\s+clear",
    r"\bsvc\s+",
    r"\bsetprop\s+",
    r"\buiautomator\s+dump",
    r"\brm\s+-",
    r"\bfastboot\b",
    r"\bflash\b",
    r"\berase\b",
    r"\bremount\b",
    r"\badb\s+root\b",
    r"\breboot\b",
]

REQUIRED_LEGACY_SIGNALS = [
    "Kadb.tryConnection",
    "Kadb.pair",
    "_adb-tls-connect._tcp.",
    "connectionCheck()",
    "resetConnection()",
    "MediaStore.Downloads",
    "ACTION_SEND",
    "classifyRisk",
    "CUSTOMROM_SESSION_",
    "checksums.sha256",
]

REQUIRED_OPS_SIGNALS = [
    "OperationPresenter",
    "PackageIntelligence",
    "ChangeLedger",
    "AdbRemoteController",
    "pm disable-user --user 0",
    "pm enable --user 0",
    "am force-stop --user 0",
    "Por que a central está lenta?",
    "Alterações feitas pelo CUSTOMROM",
    "Evidence Pack",
]

REQUIRED_MODEL_SIGNALS = [
    "SUCCESS_EMPTY",
    "COMMAND_ERROR",
    "TRANSPORT_ERROR",
    "PackageCriticality",
    "AssessmentConfidence",
    "candidateForReversibleTest",
    "wasDisabledByCustomrom",
    "Nenhum texto foi retornado pelo comando",
]

REQUIRED_CONTROLLER_SIGNALS = [
    "Kadb.tryConnection",
    "Kadb.pair",
    "_adb-tls-connect._tcp.",
    "connectionCheck()",
    "resetConnection()",
    "linkedSetOf(5555, savedPort)",
]


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def require_signals(source: str, signals: list[str], label: str) -> None:
    for signal in signals:
        if signal not in source:
            fail(f"capacidade obrigatória ausente em {label}: {signal}")


def main() -> int:
    for path in (
        MAIN,
        PREMIUM,
        OPS,
        OPS_MODELS,
        ADB_CONTROLLER,
        PREMIUM_MODELS,
        APPLICATION,
        MANIFEST,
        RECIPES,
        BUILD,
    ):
        if not path.is_file():
            fail(f"arquivo obrigatório ausente: {path.relative_to(ROOT)}")

    main_src = MAIN.read_text(encoding="utf-8")
    ops_src = OPS.read_text(encoding="utf-8")
    ops_models_src = OPS_MODELS.read_text(encoding="utf-8")
    controller_src = ADB_CONTROLLER.read_text(encoding="utf-8")
    premium_models_src = PREMIUM_MODELS.read_text(encoding="utf-8")
    app_src = APPLICATION.read_text(encoding="utf-8")
    manifest = MANIFEST.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")

    require_signals(main_src, REQUIRED_LEGACY_SIGNALS, "MainActivity")
    require_signals(ops_src, REQUIRED_OPS_SIGNALS, "PremiumOpsActivity")
    require_signals(ops_models_src, REQUIRED_MODEL_SIGNALS, "PremiumOpsModels")
    require_signals(controller_src, REQUIRED_CONTROLLER_SIGNALS, "AdbRemoteController")

    if 'android:name=".PremiumOpsActivity"' not in manifest:
        fail("PremiumOpsActivity não está registrada no AndroidManifest")
    if not re.search(
        r'<activity\s+[^>]*android:name="\.PremiumOpsActivity"[^>]*android:exported="true"[^>]*>[\s\S]*?<category android:name="android\.intent\.category\.LAUNCHER"',
        manifest,
    ):
        fail("PremiumOpsActivity precisa ser o launcher exportado da branch premium")
    if re.search(
        r'<activity\s+[^>]*android:name="\.PremiumMainActivity"[^>]*android:exported="true"',
        manifest,
    ):
        fail("PremiumMainActivity anterior não pode continuar exportada como launcher concorrente")

    if "KadbCert.configure" not in app_src or "OkioFilePrivateKeyStore" not in app_src:
        fail("identidade ADB persistente não está configurada")
    if 'android:name=".CustomromApp"' not in manifest:
        fail("CustomromApp não está registrado no AndroidManifest")

    if "rm -rf" not in premium_models_src or "setenforce 0" not in premium_models_src:
        fail("classificador premium não contém proteções ampliadas para shell estrutural")
    if "uiautomator dump" not in premium_models_src:
        fail("classificador premium precisa reconhecer interação/dump temporário como ação ativa")

    if not re.search(r"\bminSdk\s*=\s*29\b", build):
        fail("minSdk precisa permanecer 29 enquanto a exportação usar MediaStore.Downloads")
    if not re.search(r"\bcompileSdk\s*=\s*36\b", build):
        fail("compileSdk precisa permanecer 36 enquanto o backend validado for Kadb 2.1.1")
    if 'implementation("com.flyfishxu:kadb:2.1.1")' not in build:
        fail("backend ADB validado precisa permanecer com.flyfishxu:kadb:2.1.1")
    if 'implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")' not in build:
        fail("Coroutines 1.10.2 precisa permanecer disponível para Kadb.pair")
    if not re.search(r"\btargetSdk\s*=\s*35\b", build):
        fail("targetSdk 35 é decisão de compatibilidade de runtime e não deve subir por efeito colateral")

    recipes = json.loads(RECIPES.read_text(encoding="utf-8"))
    if not isinstance(recipes, list) or len(recipes) < 24:
        fail("catálogo premium de receitas precisa manter pelo menos 24 rotinas úteis")

    ids: set[str] = set()
    for recipe in recipes:
        rid = recipe.get("id")
        if not isinstance(rid, str) or not rid:
            fail("receita sem id")
        if rid in ids:
            fail(f"id de receita duplicado: {rid}")
        ids.add(rid)
        for field in ("name", "risk", "command", "output"):
            if not isinstance(recipe.get(field), str) or not recipe[field].strip():
                fail(f"receita {rid}: campo obrigatório inválido: {field}")
        if recipe["risk"] not in {"VERDE", "AMARELO", "VERMELHO"}:
            fail(f"receita {rid}: risco inválido")
        if recipe["risk"] == "VERDE":
            lower = recipe["command"].lower()
            for pattern in MUTATING:
                if re.search(pattern, lower):
                    fail(f"receita VERDE {rid} contém comando mutável: {pattern}")

    required_recipes = {
        "estado-geral",
        "memoria-zram",
        "processos",
        "rede-adb",
        "logcat-curto",
        "snapshot-completo",
        "diagnostico-lentidao",
        "thermal",
        "fluidez-gfx",
        "ui-hierarchy",
        "animacoes-off",
        "animacoes-on",
        "rotacao-auto-on",
        "rotacao-auto-off",
    }
    missing = required_recipes - ids
    if missing:
        fail(f"receitas premium obrigatórias ausentes: {sorted(missing)}")

    recipe_map = {recipe["id"]: recipe for recipe in recipes}
    if recipe_map["ui-hierarchy"]["risk"] != "AMARELO":
        fail("ui-hierarchy grava arquivo temporário e deve permanecer AMARELO")
    for rid in ("animacoes-off", "animacoes-on", "rotacao-auto-on", "rotacao-auto-off"):
        if recipe_map[rid]["risk"] != "AMARELO":
            fail(f"personalização reversível {rid} precisa permanecer AMARELA")
    for rid in ("diagnostico-lentidao", "thermal", "fluidez-gfx"):
        if recipe_map[rid]["risk"] != "VERDE":
            fail(f"diagnóstico somente leitura {rid} precisa permanecer VERDE")

    print("VALIDATE_NATIVE_CUSTOMROM=PASS")
    print(f"recipes={len(recipes)}")
    print("launcher=PremiumOpsActivity")
    print("human_operation_states=present")
    print("package_intelligence=present")
    print("change_ledger=present")
    print("persistent_adb_identity=present")
    print("mdns_reconnect=present")
    print("evidence_export=present")
    print("adb_backend=com.flyfishxu:kadb:2.1.1")
    print("coroutines=1.10.2")
    print("compileSdk=36")
    print("targetSdk=35")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
