#!/usr/bin/env python3
"""Build a portable CUSTOMROM Evidence Pack from a session directory.

This is the reference implementation for the future Android exporter.
It is intentionally platform-neutral so the format can be validated in CI first.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
from datetime import datetime, timezone
import zipfile


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def role_for(path: Path) -> str:
    name = path.name.lower()
    if "logcat" in name or path.suffix.lower() == ".log":
        return "logcat"
    if "device" in name or "estado_geral" in name:
        return "device-info"
    if "terminal" in name or "shell" in name:
        return "terminal"
    if path.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp"}:
        return "screenshot"
    if path.suffix.lower() in {".txt", ".md", ".json", ".xml", ".csv"}:
        return "report"
    return "attachment"


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument("session_dir", type=Path)
    p.add_argument("--title", default="Sessão CUSTOMROM")
    p.add_argument("--target", default="TayTech")
    p.add_argument("--model", default="")
    p.add_argument("--build", default="")
    p.add_argument("--endpoint", default="")
    p.add_argument("--strategy", default="manual", choices=["alive", "last-endpoint", "tcp-5555", "mdns", "manual", "pairing"])
    p.add_argument("--investigation", default="")
    p.add_argument("--out", type=Path, default=None)
    return p.parse_args()


def build_summary(meta: dict, files: list[dict]) -> str:
    lines = [
        f"# {meta['session']['title']}",
        "",
        f"- Alvo: **{meta['target']['name']}**",
        f"- Modelo: `{meta['target'].get('model') or 'não informado'}`",
        f"- Build: `{meta['target'].get('build') or 'não informada'}`",
        f"- Estratégia de conexão: `{meta['connection']['strategy']}`",
        f"- Endpoint: `{meta['connection'].get('endpoint') or 'não informado'}`",
        f"- Investigação: {meta['session'].get('investigation') or 'não informada'}",
        "",
        "## Arquivos",
        "",
    ]
    for item in files:
        lines.append(f"- `{item['path']}` — {item['role']} — {item['size']} bytes")
    lines += [
        "",
        "## Uso",
        "",
        "Este pacote preserva evidência bruta e metadados separados de qualquer interpretação. Envie o ZIP ou os arquivos relevantes para análise.",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    args = parse_args()
    session_dir = args.session_dir.resolve()
    if not session_dir.is_dir():
        raise SystemExit(f"Diretório de sessão inexistente: {session_dir}")

    now = datetime.now(timezone.utc)
    session_id = now.strftime("%Y%m%dT%H%M%SZ")
    out = args.out or session_dir.parent / f"CUSTOMROM_SESSION_{session_id}.zip"
    out = out.resolve()

    source_files = sorted(p for p in session_dir.rglob("*") if p.is_file())
    with tempfile.TemporaryDirectory() as tmp:
        stage = Path(tmp) / f"CUSTOMROM_SESSION_{session_id}"
        stage.mkdir(parents=True)

        files_meta: list[dict] = []
        for src in source_files:
            rel = src.relative_to(session_dir)
            dst = stage / "attachments" / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)
            files_meta.append(
                {
                    "path": str(Path("attachments") / rel).replace("\\", "/"),
                    "role": role_for(src),
                    "sha256": sha256(dst),
                    "size": dst.stat().st_size,
                }
            )

        meta = {
            "schema": 1,
            "session": {
                "id": session_id,
                "title": args.title,
                "startedAt": now.isoformat(),
                "endedAt": now.isoformat(),
                "notes": "",
                "investigation": args.investigation,
            },
            "target": {
                "name": args.target,
                "model": args.model,
                "manufacturer": "",
                "build": args.build,
                "board": "",
                "abi": "",
            },
            "connection": {
                "strategy": args.strategy,
                "endpoint": args.endpoint,
                "paired": True,
                "reconnectCount": 0,
                "transportErrors": 0,
            },
            "executions": [],
            "files": files_meta,
        }

        (stage / "manifest.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (stage / "resumo.md").write_text(build_summary(meta, files_meta), encoding="utf-8")
        checksum_lines = [f"{item['sha256']}  {item['path']}" for item in files_meta]
        (stage / "checksums.sha256").write_text("\n".join(checksum_lines) + ("\n" if checksum_lines else ""), encoding="utf-8")

        out.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            for file in sorted(p for p in stage.rglob("*") if p.is_file()):
                zf.write(file, file.relative_to(stage.parent))

    print(out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
