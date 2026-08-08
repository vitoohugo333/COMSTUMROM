#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
sys.path.insert(0, str(TOOLS))
import apply_control_round_v4 as v4

ACTIVITY = ROOT / "apps/customrom-adb-native/app/src/main/java/com/customrom/adb/PremiumOpsActivity.kt"


def replace_line_containing(text: str, needle: str, replacement: str) -> str:
    lines = text.splitlines()
    matches = [i for i, line in enumerate(lines) if needle in line]
    if len(matches) != 1:
        raise SystemExit(f"line containing {needle!r}: expected 1, found {len(matches)}")
    lines[matches[0]] = replacement
    return "\n".join(lines) + "\n"


def fix_generated_kotlin() -> None:
    s = ACTIVITY.read_text(encoding="utf-8")

    # v4 intentionally generates the complete UX change; fix Python/Kotlin escaping
    # before the permanent validator and Gradle see the source.
    s = s.replace('assessment.reasons.joinToString("\n")', 'assessment.reasons.joinToString("\\n")')
    s = s.replace('RC=$?', "RC=${'$'}?")

    log_line = r'''        val command = "PID=${'$'}(pidof $pkg 2>/dev/null | awk '{print ${'$'}1}'); if [ -n \"${'$'}PID\" ]; then logcat -d -v threadtime --pid=${'$'}PID -t 500 2>/dev/null || logcat -d -v threadtime -t 1200 2>/dev/null | grep -F '$pkg' | tail -n 500; else echo 'Package não está rodando; buscando referências recentes'; logcat -d -v threadtime -t 1600 2>/dev/null | grep -F '$pkg' | tail -n 500; fi"'''
    s = replace_line_containing(s, 'val command = "PID=$(', log_line)

    ACTIVITY.write_text(s, encoding="utf-8")


def main() -> None:
    v4.v3.patch_models()
    v4.patch_activity()
    fix_generated_kotlin()
    v4.v3.patch_recipes()
    v4.v3.patch_validator()
    print("APPLY_CONTROL_ROUND_V5=PASS")


if __name__ == "__main__":
    main()
