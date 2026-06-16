#!/bin/bash
# validate.sh – Run ilivalidator on ITF/XTF transfer files
# Usage: ./validate.sh <transfer-file> [model-name]
#
# Prerequisites:
#   ilivalidator jar at /Users/stefan/apps/ilivalidator-1.15.0/ilivalidator-1.15.0.jar
#   Model files under src/test/data/av/models/
#   Or downloadable from https://models.interlis.ch
#
# Examples:
#   ./validate.sh build/out/dmav-bb.xtf
#   ./validate.sh src/test/resources/fixtures/dm01-dmav/lfp3/dm01-real-extract.itf

set -euo pipefail

ILIVALIDATOR="/Users/stefan/apps/ilivalidator-1.15.0/ilivalidator-1.15.0.jar"
MODELDIR="src/test/data/av/models/"
REMOTE_MODELDIR="https://models.interlis.ch"

TRANSFER_FILE="${1:-}"
if [ -z "$TRANSFER_FILE" ]; then
    echo "Usage: $0 <transfer-file>"
    echo ""
    echo "Validates an ITF or XTF transfer file against the official"
    echo "DM01/DMAV models using ilivalidator."
    echo ""
    echo "Model directory: $MODELDIR"
    echo "Remote models:   $REMOTE_MODELDIR"
    exit 1
fi

if [ ! -f "$TRANSFER_FILE" ]; then
    echo "ERROR: File not found: $TRANSFER_FILE"
    exit 1
fi

if [ ! -f "$ILIVALIDATOR" ]; then
    echo "ERROR: ilivalidator not found at $ILIVALIDATOR"
    echo "Download from: https://jars.interlis.guru"
    exit 1
fi

echo "=== ilivalidator ==="
echo "Transfer: $TRANSFER_FILE"
echo "Size:     $(du -h "$TRANSFER_FILE" | cut -f1)"
echo ""

java -jar "$ILIVALIDATOR" \
    --modeldir "$MODELDIR" \
    --modeldir "$REMOTE_MODELDIR" \
    "$TRANSFER_FILE"

echo ""
echo "Done."
