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
    r"\bfastboot\b",
    r"\bflash\b",
    r"\berase\b",
    r"\bremount\b",
    r"\badb\s+root\b",
    r"\breboot\b",
]

REQUIRED_MAIN_SIGNALS = [
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
    "executeSelectedRecipe",
]


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> int:
    for path in (MAIN, APPLICATION, MANIFEST, RECIPES, BUILD):
        if not path.is_file():
            fail(f"arquivo obrigatório ausente: {path.relative_to(ROOT)}")

    main_src = MAIN.read_text(encoding="utf-8")
    app_src = APPLICATION.read_text(encoding="utf-8")
    manifest = MANIFEST.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")

    for signal in REQUIRED_MAIN_SIGNALS:
        if signal not in main_src:
            fail(f"capacidade obrigatória ausente em MainActivity: {signal}")

    if "KadbCert.configure" not in app_src or "OkioFilePrivateKeyStore" not in app_src:
        fail("identidade ADB persistente não está configurada")
    if 'android:name=".CustomromApp"' not in manifest:
        fail("CustomromApp não está registrado no AndroidManifest")
    if not re.search(r"\bminSdk\s*=\s*29\b", build):
        fail("minSdk precisa permanecer 29 enquanto a exportação usar MediaStore.Downloads")

    # Regressão fechada em 2026-08-07: Kadb 2.1.3 passou a exigir compileSdk 37,
    # mas a plataforma numérica android-37 não estava disponível no sdkmanager do runner.
    # Kadb 2.1.1 expõe as APIs usadas pelo app e declara compileSdk 36 upstream.
    if not re.search(r"\bcompileSdk\s*=\s*36\b", build):
        fail("compileSdk precisa permanecer 36 enquanto o backend validado for Kadb 2.1.1")
    if 'implementation("com.flyfishxu:kadb:2.1.1")' not in build:
        fail("backend ADB validado precisa permanecer com.flyfishxu:kadb:2.1.1")
    if 'implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")' not in build:
        fail("Coroutines 1.10.2 precisa ser dependência direta porque MainActivity usa runBlocking")
    if not re.search(r"\btargetSdk\s*=\s*35\b", build):
        fail("targetSdk 35 é decisão de compatibilidade de runtime e não deve subir por efeito colateral")

    recipes = json.loads(RECIPES.read_text(encoding="utf-8"))
    if not isinstance(recipes, list) or len(recipes) < 5:
        fail("catálogo nativo de receitas está vazio/incompleto")

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

    required_recipes = {"estado-geral", "memoria-zram", "processos", "rede-adb", "logcat-curto", "snapshot-completo"}
    missing = required_recipes - ids
    if missing:
        fail(f"receitas obrigatórias ausentes: {sorted(missing)}")

    print("VALIDATE_NATIVE_CUSTOMROM=PASS")
    print(f"recipes={len(recipes)}")
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
