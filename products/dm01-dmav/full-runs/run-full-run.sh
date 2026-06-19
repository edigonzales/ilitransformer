#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"

DATASET_SLUG="${1:-so-2549}"
SOURCE_PATH="${2:-}"
REPORT_DIR="${3:-}"
OUTPUT_PATH="${4:-}"

MANIFEST_PATH="${REPO_ROOT}/products/dm01-dmav/full-runs/${DATASET_SLUG}/manifest.yaml"

if [[ ! -f "${MANIFEST_PATH}" ]]; then
  echo "Manifest nicht gefunden: ${MANIFEST_PATH}" >&2
  echo "Usage: ./products/dm01-dmav/full-runs/run-full-run.sh [dataset-slug] [source-path] [report-dir] [output-path]" >&2
  exit 1
fi

GRADLE_ARGS=(
  "runDm01DmavFullRun"
  "-Pdm01DmavFullRunManifest=${MANIFEST_PATH}"
)

if [[ -n "${SOURCE_PATH}" ]]; then
  GRADLE_ARGS+=("-Pdm01DmavFullRunSource=${SOURCE_PATH}")
fi

if [[ -n "${REPORT_DIR}" ]]; then
  GRADLE_ARGS+=("-Pdm01DmavFullRunReportDir=${REPORT_DIR}")
fi

if [[ -n "${OUTPUT_PATH}" ]]; then
  GRADLE_ARGS+=("-Pdm01DmavFullRunOutput=${OUTPUT_PATH}")
fi

exec "${REPO_ROOT}/gradlew" "${GRADLE_ARGS[@]}"
