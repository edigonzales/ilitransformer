#!/usr/bin/env bash
# Validiert ein Demo-Beispiel end-to-end:
#   1. ili2c kompiliert das Modell
#   2. ilivalidator prueft die Eingabedaten
#   3. ilitransformer fuehrt die Transformation aus
#   4. ilivalidator prueft die Ausgabedaten
#
# Aufruf (aus dem Repo-Root):
#   demo/validate.sh demo/01-hello-copy
#
# Voraussetzungen (Pfade ggf. anpassen):
#   - ili2c und ilivalidator als JARs (siehe Variablen unten)
#   - ilitransformer-CLI unter bin/ilitransformer (in der Distribution)
set -euo pipefail

ILI2C="${ILI2C:-/pfad/zu/ili2c.jar}"
ILIVALIDATOR="${ILIVALIDATOR:-/pfad/zu/ilivalidator.jar}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLI="${CLI:-$REPO_ROOT/bin/ilitransformer}"

DIR="${1:?Usage: demo/validate.sh <demo-dir>}"
cd "$DIR"

echo "### [$DIR] ili2c (Modell) ###"
for ili in models/*.ili; do
  java -jar "$ILI2C" "$ili" 2>&1 | tail -2
done

echo "### [$DIR] ilivalidator (Eingabe) ###"
for data in data/*.xtf data/*.itf; do
  [ -e "$data" ] || continue
  java -jar "$ILIVALIDATOR" --modeldir "models" "$data" 2>&1 \
    | grep -E "Error|Warning|validation done|failed" | tail -3
done

echo "### [$DIR] ilitransformer transform ###"
"$CLI" transform -m profile.ilimap 2>&1 | grep -E "summary|Output committed|ERROR" | tail -5

echo "### [$DIR] ilivalidator (Ausgabe) ###"
for out in output*.xtf output*.itf; do
  [ -e "$out" ] || continue
  java -jar "$ILIVALIDATOR" --modeldir "models" "$out" 2>&1 \
    | grep -E "Error|Warning|validation done|failed" | tail -3
done
echo "### [$DIR] OK ###"
