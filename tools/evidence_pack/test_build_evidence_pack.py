#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile


def main() -> int:
    repo = Path(__file__).resolve().parents[2]
    builder = repo / "tools" / "evidence_pack" / "build_evidence_pack.py"

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        session = root / "session"
        session.mkdir()
        (session / "terminal.txt").write_text("getprop ro.product.model\nTayTech\n", encoding="utf-8")
        (session / "logcat.txt").write_text("I/Test: sample\n", encoding="utf-8")
        (session / "01_estado_geral_da_central.txt").write_text("MemTotal: 4096000 kB\n", encoding="utf-8")
        out = root / "pack.zip"

        proc = subprocess.run(
            [
                sys.executable,
                str(builder),
                str(session),
                "--title", "Teste Evidence Pack",
                "--target", "TayTech",
                "--model", "TAYTECH-TEST",
                "--build", "TEST-BUILD",
                "--endpoint", "192.168.1.10:5555",
                "--strategy", "tcp-5555",
                "--investigation", "teste determinístico",
                "--out", str(out),
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        print(proc.stdout.strip())
        assert out.exists() and out.stat().st_size > 0

        with zipfile.ZipFile(out) as zf:
            names = zf.namelist()
            roots = {name.split("/", 1)[0] for name in names}
            assert len(roots) == 1, roots
            prefix = next(iter(roots)) + "/"
            required = {
                prefix + "manifest.json",
                prefix + "resumo.md",
                prefix + "checksums.sha256",
                prefix + "attachments/terminal.txt",
                prefix + "attachments/logcat.txt",
                prefix + "attachments/01_estado_geral_da_central.txt",
            }
            missing = required - set(names)
            assert not missing, missing

            manifest = json.loads(zf.read(prefix + "manifest.json"))
            assert manifest["schema"] == 1
            assert manifest["target"]["name"] == "TayTech"
            assert manifest["connection"]["strategy"] == "tcp-5555"
            assert manifest["connection"]["endpoint"] == "192.168.1.10:5555"
            assert len(manifest["files"]) == 3
            assert all(item["sha256"] for item in manifest["files"])

            summary = zf.read(prefix + "resumo.md").decode("utf-8")
            assert "Teste Evidence Pack" in summary
            assert "TayTech" in summary

            checksums = zf.read(prefix + "checksums.sha256").decode("utf-8")
            assert "attachments/terminal.txt" in checksums
            assert "attachments/logcat.txt" in checksums

    print("EVIDENCE_PACK_TEST=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
