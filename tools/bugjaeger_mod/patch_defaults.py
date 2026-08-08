#!/usr/bin/env python3
"""Patch cirúrgico para uma árvore já decodificada com Apktool.

Escopo deliberado:
- não toca em premium gates, anúncios ou monetização;
- não altera protocolo ADB;
- ativa por padrão recursos já existentes de reconexão/autofetch/background;
- aplica identidade visual/nome CUSTOMROM apenas quando o recurso pode ser localizado com segurança.

Uso:
    python3 tools/bugjaeger_mod/patch_defaults.py /caminho/decoded
"""
from __future__ import annotations

import sys
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
A = f"{{{ANDROID_NS}}}"
ET.register_namespace("android", ANDROID_NS)

PREF_KEYS = {
    "key.reconnect.last.wifi.targets",
    "key.autofetch.connection.params",
    "key.autofetch.pairing.info",
    "key.start.adb.server.foreground",
}


def patch_preferences(root: Path) -> list[str]:
    changed: list[str] = []
    prefs = root / "res" / "xml" / "prefs.xml"
    if not prefs.exists():
        return changed

    tree = ET.parse(prefs)
    xml_root = tree.getroot()
    for element in xml_root.iter():
        key = element.attrib.get(A + "key")
        if key in PREF_KEYS:
            old = element.attrib.get(A + "defaultValue")
            if old != "true":
                element.set(A + "defaultValue", "true")
                changed.append(f"prefs:{key}:default=true")

    if changed:
        tree.write(prefs, encoding="utf-8", xml_declaration=True)
    return changed


def patch_strings(root: Path) -> list[str]:
    changed: list[str] = []
    values = root / "res" / "values" / "strings.xml"
    if not values.exists():
        return changed

    tree = ET.parse(values)
    xml_root = tree.getroot()
    for item in xml_root.findall("string"):
        name = item.attrib.get("name", "")
        text = item.text or ""
        if name == "app_name" and text.strip() != "CUSTOMROM ADB":
            item.text = "CUSTOMROM ADB"
            changed.append("strings:app_name=CUSTOMROM ADB")

    if changed:
        tree.write(values, encoding="utf-8", xml_declaration=True)
    return changed


def main() -> int:
    if len(sys.argv) != 2:
        print("Uso: patch_defaults.py <apktool-decoded-dir>", file=sys.stderr)
        return 2

    root = Path(sys.argv[1]).resolve()
    if not root.exists():
        print(f"Diretório inexistente: {root}", file=sys.stderr)
        return 2

    changes = []
    changes += patch_preferences(root)
    changes += patch_strings(root)

    print("CUSTOMROM patch report")
    if not changes:
        print("- nenhum patch aplicável encontrado; árvore preservada")
    else:
        for change in changes:
            print(f"- {change}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
