#!/usr/bin/env python3
"""Inspeção estática reproduzível do APK de referência sem decompilar integralmente.

Gera um relatório humano + inventário JSON com:
- hash/tamanho;
- DEX e bibliotecas nativas;
- layouts XML presentes;
- strings-chave relacionadas a ADB/reconexão/pareamento/shell;
- indicadores de SDKs de ads/analytics observáveis em strings/classes.

Não modifica o APK.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

KEY_TERMS = [
    b"MdnsSdResolver",
    b"AdbShellRepository",
    b"TargetConnectionsManager",
    b"AdbDeviceHolder",
    b"AdbCommandProcessor",
    b"reconnectLastWifiConnections",
    b"saveConnectDataForReconnect",
    b"handleReconnectLastWifiConnections",
    b"_adb-tls-pairing",
    b"_adb-tls-connect",
    b"key.reconnect.last.wifi.targets",
    b"key.autofetch.connection.params",
    b"key.autofetch.pairing.info",
    b"key.start.adb.server.foreground",
    b"Free version only allows maximum",
    b"com.google.android.gms.ads",
    b"firebase",
    b"crashlytics",
]

LAYOUT_NAMES = {
    "activity_main.xml",
    "activity_shell.xml",
    "commands_fragment.xml",
    "devices.xml",
    "dialog_connect.xml",
    "dialog_pair.xml",
    "custom_command_dialog.xml",
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("apk", type=Path)
    p.add_argument("--out", type=Path, default=Path("out/apk-inspection"))
    args = p.parse_args()

    apk = args.apk.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    if not apk.is_file():
        raise SystemExit(f"APK inexistente: {apk}")

    with zipfile.ZipFile(apk) as zf:
        names = sorted(zf.namelist())
        dex = [n for n in names if re.fullmatch(r"classes\d*\.dex", Path(n).name)]
        libs = [n for n in names if n.startswith("lib/") and n.endswith(".so")]
        layouts = [n for n in names if Path(n).name in LAYOUT_NAMES]

        hits: dict[str, list[str]] = {}
        scan_names = [n for n in names if n.endswith(".dex") or n.endswith(".xml") or n.endswith(".arsc")]
        for name in scan_names:
            try:
                data = zf.read(name)
            except Exception:
                continue
            for term in KEY_TERMS:
                if term.lower() in data.lower():
                    hits.setdefault(term.decode("utf-8", "replace"), []).append(name)

    result = {
        "file": apk.name,
        "size": apk.stat().st_size,
        "sha256": sha256(apk),
        "dex": dex,
        "nativeLibraries": libs,
        "selectedLayouts": layouts,
        "signals": hits,
    }
    (out / "inventory.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    lines = [
        "# Auditoria estática reproduzível do APK de referência",
        "",
        f"- Arquivo: `{apk.name}`",
        f"- SHA-256: `{result['sha256']}`",
        f"- Tamanho: {result['size']} bytes",
        f"- DEX: {len(dex)}",
        f"- Bibliotecas nativas: {len(libs)}",
        "",
        "## Layouts selecionados encontrados",
        "",
    ]
    lines.extend(f"- `{x}`" for x in layouts)
    lines += ["", "## Sinais técnicos", ""]
    for term, files in sorted(hits.items()):
        lines.append(f"- `{term}` → {', '.join(f'`{f}`' for f in files[:8])}")
    lines += ["", "## Observação", "", "Este relatório prova apenas presença estática de recursos/sinais. Comportamento em runtime precisa de build e teste em Android real.", ""]
    (out / "report.md").write_text("\n".join(lines), encoding="utf-8")
    print(out / "report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
