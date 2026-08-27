#!/usr/bin/env bash
# Fetch the KataGo HumanSL + strong nets (and a tuned analysis config) needed in
# 'real' mode. Models/binaries are gitignored — run this once after cloning.
#
#   ./server/scripts/download-models.sh
#
# Requires curl. Set KATAGO_DIR to a parent dir where the extracted kataGo binary
# and any shared libs already live (optional). To use the real engine, also:
#   export KATAGO_MODE=real KATAGO_BIN=/path/to/katago
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODELS="$ROOT/server/models"
CONFIG="$ROOT/server/config"
mkdir -p "$MODELS" "$CONFIG"

HUMAN_MODEL="${KATAGO_HUMAN_MODEL_URL:-https://media.katagotraining.org/uploaded/networks/modelsextra/b18c384nbt-humanv0.bin.gz}"
# A genuine strong KataGo net for the "good vs tempting-bad" comparison. This is
# the official b28c512nbt main-run net; substitute any strong kata1-* .bin.gz.
STRONG_MODEL="${KATAGO_STRONG_MODEL_URL:-https://media.katagotraining.org/uploaded/networks/models/kata1/kata1-b28c512nbt-s13255194368-d5935380940.bin.gz}"

echo ">> Downloading HumanSL model  (b18c384nbt-humanv0)"
curl -L --fail --progress-bar "$HUMAN_MODEL" -o "$MODELS/b18c384nbt-humanv0.bin.gz"

echo ">> Downloading strong model    (kata1-b28c512nbt)"
curl -L --fail --progress-bar "$STRONG_MODEL" -o "$MODELS/strong.bin.gz"

echo ">> Writing 9x9-tuned analysis config to $CONFIG/analysis.cfg"
cat > "$CONFIG/analysis.cfg" <<'CFG'
# Minimal KataGo Analysis-engine config tuned for 9x9 on a single GPU.
logDir = analysis_logs
reportAnalysisWinratesAs = BLACK

# 9x9 needs far fewer visits than 19x19; a few hundred is plenty for teaching.
maxVisits = 500

# Interactive/single-position usage: keep per-query latency low.
numAnalysisThreads = 2
numSearchThreadsPerAnalysisThread = 16

nnMaxBatchSize = 64
nnCacheSizePowerOfTwo = 23
nnMutexPoolSizePowerOfTwo = 17

# Human SL is applied per-query via overrideSettings.humanSLProfile; nothing
# profile-specific is required here.
CFG

echo ">> Done. Start the server in real mode:"
echo "   cd $ROOT/server"
echo "   export KATAGO_MODE=real KATAGO_BIN=/path/to/katago"
echo "   npm run dev"
