#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
RECIPES = ROOT / "apps" / "customrom-adb" / "recipes" / "recipes.json"

DANGEROUS_PATTERNS = [
    re.compile(r"\bfastboot\b", re.I),
    re.compile(r"\bflash\b", re.I),
    re.compile(r"\berase\b", re.I),
    re.compile(r"\bremount\b", re.I),
    re.compile(r"\badb\s+root\b", re.I),
    re.compile(r"\bpm\s+uninstall\b", re.I),
    re.compile(r"\bpm\s+disable", re.I),
    re.compile(r"\bam\s+force-stop\b", re.I),
]


def main() -> int:
    data = json.loads(RECIPES.read_text(encoding="utf-8"))
    assert data.get("schema") == 1
    recipes = data.get("recipes")
    assert isinstance(recipes, list) and recipes

    ids: set[str] = set()
    outputs: set[str] = set()
    by_id = {}
    for recipe in recipes:
        rid = recipe["id"]
        assert re.fullmatch(r"[a-z0-9][a-z0-9-]*", rid), rid
        assert rid not in ids, f"recipe id duplicado: {rid}"
        ids.add(rid)
        by_id[rid] = recipe

        output = recipe["output"]
        assert output not in outputs, f"output duplicado: {output}"
        outputs.add(output)
        assert recipe["risk"] in {"VERDE", "AMARELO", "VERMELHO"}
        assert bool(recipe.get("commands")) ^ bool(recipe.get("includes")), rid

        commands = recipe.get("commands", [])
        if recipe["risk"] == "VERDE":
            for command in commands:
                for pattern in DANGEROUS_PATTERNS:
                    assert not pattern.search(command), f"receita VERDE contém alteração: {rid}: {command}"

        if recipe["risk"] in {"AMARELO", "VERMELHO"}:
            assert recipe.get("rollback") or recipe["risk"] == "VERMELHO", f"AMARELO sem rollback: {rid}"

    for recipe in recipes:
        for included in recipe.get("includes", []):
            assert included in by_id, f"include inexistente: {recipe['id']} -> {included}"
            assert included != recipe["id"], f"auto-include: {recipe['id']}"

    print(f"RECIPES_VALID=PASS count={len(recipes)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
