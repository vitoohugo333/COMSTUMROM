#!/usr/bin/env bash
set -euo pipefail

# Rebuild helper for the user's supplied APK.
# This script intentionally does NOT patch premium/ad/monetization gates.
# It only applies CUSTOMROM-owned patches from patch_defaults.py.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APKTOOL_JAR="${APKTOOL_JAR:-$ROOT/.tools/apktool.jar}"
SOURCE_HINT="${1:-${SOURCE_HINT:-}}"
WORK="${WORK_DIR:-$ROOT/.work/bugjaeger-mod}"
OUT="${OUT_DIR:-$ROOT/out}"

mkdir -p "$WORK" "$OUT"
rm -rf "$WORK/decoded" "$WORK/source"
mkdir -p "$WORK/source"

if [[ ! -f "$APKTOOL_JAR" ]]; then
  echo "ERRO: Apktool não encontrado em $APKTOOL_JAR" >&2
  echo "Defina APKTOOL_JAR ou coloque o jar nesse caminho." >&2
  exit 2
fi

pick_apk() {
  local candidate=""

  if [[ -n "$SOURCE_HINT" && -f "$ROOT/$SOURCE_HINT" ]]; then
    candidate="$ROOT/$SOURCE_HINT"
  elif [[ -n "$SOURCE_HINT" && -f "$SOURCE_HINT" ]]; then
    candidate="$SOURCE_HINT"
  else
    candidate="$(find "$ROOT" -type f \( -iname '*bugjaeger*.apk' -o -iname '*hackendebug*.apk' \) -print -quit || true)"
  fi

  if [[ -n "$candidate" && "$candidate" == *.apk ]]; then
    printf '%s\n' "$candidate"
    return 0
  fi

  local zip=""
  if [[ -n "$candidate" && "$candidate" == *.zip ]]; then
    zip="$candidate"
  else
    zip="$(find "$ROOT" -type f \( -iname '*bugjaeger*.zip' -o -iname '*hackendebug*.zip' \) -print -quit || true)"
  fi

  if [[ -z "$zip" ]]; then
    # Último fallback: procurar um ZIP que contenha APK, sem assumir nome.
    while IFS= read -r z; do
      if unzip -l "$z" 2>/dev/null | grep -qiE '\.apk$'; then
        zip="$z"
        break
      fi
    done < <(find "$ROOT" -type f -iname '*.zip' -print)
  fi

  if [[ -n "$zip" ]]; then
    unzip -j -o "$zip" '*.apk' -d "$WORK/source" >/dev/null
    candidate="$(find "$WORK/source" -type f -iname '*.apk' -print -quit || true)"
  fi

  if [[ -z "$candidate" || ! -f "$candidate" ]]; then
    echo "ERRO: não encontrei APK de referência no repositório/ZIP." >&2
    exit 3
  fi

  printf '%s\n' "$candidate"
}

APK="$(pick_apk)"
ORIGINAL_SHA="$(sha256sum "$APK" | awk '{print $1}')"
echo "APK de referência: $APK"
echo "SHA-256 original: $ORIGINAL_SHA"

java -jar "$APKTOOL_JAR" d -f "$APK" -o "$WORK/decoded"
python3 "$ROOT/tools/bugjaeger_mod/patch_defaults.py" "$WORK/decoded" | tee "$OUT/patch-report.txt"
java -jar "$APKTOOL_JAR" b "$WORK/decoded" -o "$OUT/CUSTOMROM-ADB-unsigned.apk"

cat > "$OUT/build-metadata.txt" <<EOF
CUSTOMROM ADB mod build
original_apk=$APK
original_sha256=$ORIGINAL_SHA
patcher=tools/bugjaeger_mod/patch_defaults.py
unsigned_apk=$OUT/CUSTOMROM-ADB-unsigned.apk
EOF

sha256sum "$OUT/CUSTOMROM-ADB-unsigned.apk" >> "$OUT/build-metadata.txt"
echo "Build concluída: $OUT/CUSTOMROM-ADB-unsigned.apk"
