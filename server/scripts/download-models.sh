#!/usr/bin/env bash
# Fetch the KataGo HumanSL + strong nets (and a tuned analysis config) needed in
# 'real' mode. Models/binaries are gitignored — run this once after cloning.
#
#   ./server/scripts/download-models.sh
#
# Requires curl. To use the real engine, also:
#   export KATAGO_MODE=real KATAGO_BIN=./katago
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODELS="$ROOT/server/models"
CONFIG="$ROOT/server/config"
LIBS="$ROOT/server/libs"
mkdir -p "$MODELS" "$CONFIG" "$LIBS"

HUMAN_MODEL="${KATAGO_HUMAN_MODEL_URL:-https://github.com/lightvector/KataGo/releases/download/v1.15.0/b18c384nbt-humanv0.bin.gz}"
# A genuine strong KataGo net for the "good vs tempting-bad" comparison. This is
# the official b28c512nbt main-run net; substitute any strong kata1-* .bin.gz.
STRONG_MODEL="${KATAGO_STRONG_MODEL_URL:-https://media.katagotraining.org/uploaded/networks/models/kata1/kata1-b28c512nbt-s13255194368-d5935380940.bin.gz}"

dl() {
  # curl -L curl's the model, some hosts need a browser UA
  curl -L --fail --progress-bar -A "Mozilla/5.0" "$1" -o "$2"
}

if [ -f "$MODELS/b18c384nbt-humanv0.bin.gz" ]; then
  echo ">> HumanSL model present, skipping"
else
  echo ">> Downloading HumanSL model  (b18c384nbt-humanv0)"
  dl "$HUMAN_MODEL" "$MODELS/b18c384nbt-humanv0.bin.gz"
fi

if [ -f "$MODELS/strong.bin.gz" ]; then
  echo ">> Strong model present, skipping"
else
  echo ">> Downloading strong model    (kata1-b28c512nbt)"
  dl "$STRONG_MODEL" "$MODELS/strong.bin.gz"
fi

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

echo ">> Fetching the KataGo binary (CUDA 12.1 build) into server/katago"
if [ ! -x "$ROOT/server/katago" ]; then
  curl -L --fail --progress-bar \
    -o /tmp/katago.zip \
    "https://github.com/lightvector/KataGo/releases/download/v1.15.3/katago-v1.15.3-cuda12.1-cudnn8.9.7-linux-x64.zip"
  unzip -o /tmp/katago.zip -d /tmp/katago >/dev/null
  cp /tmp/katago/katago "$ROOT/server/katago"
  chmod +x "$ROOT/server/katago"
else
  echo "   (server/katago already present, skipping)"
fi

# The CUDA binary was built on older Ubuntu (20.04) and needs libssl.so.1.1 and
# libzip.so.5, which Ubuntu 22.04+ does NOT ship by default (22.04 has libssl.so.3,
# and libzip.so.5 is not in the default repos). Bundle them into server/libs/ so the
# server resolves them locally via LD_LIBRARY_PATH. The archive uses libzip.so.4; the
# binary wants the .5 soname, so copy it as .5 (ABI-compatible for our usage).
if [ ! -f "$LIBS/libssl.so.1.1" ]; then
  echo ">> Bundling runtime libs (libssl1.1 + libzip) into server/libs/"
  curl -L --fail -o /tmp/libssl11.deb \
    "http://archive.ubuntu.com/ubuntu/pool/main/o/openssl/libssl1.1_1.1.1f-1ubuntu2_amd64.deb"
  dpkg-deb -x /tmp/libssl11.deb /tmp/libssl11-x
  cp /tmp/libssl11-x/usr/lib/x86_64-linux-gnu/libssl.so.1.1 "$LIBS/"
  cp /tmp/libssl11-x/usr/lib/x86_64-linux-gnu/libcrypto.so.1.1 "$LIBS/"
  curl -L --fail -o /tmp/libzip4.deb \
    "http://archive.ubuntu.com/ubuntu/pool/universe/libz/libzip/libzip4_1.7.3-1ubuntu2_amd64.deb"
  dpkg-deb -x /tmp/libzip4.deb /tmp/libzip4-x
  cp /tmp/libzip4-x/usr/lib/x86_64-linux-gnu/libzip.so.4.0 "$LIBS/"
  ln -sf libzip.so.4.0 "$LIBS/libzip.so.5"
fi

echo ">> Done. Start the server in real mode:"
echo "   cd $ROOT/server"
echo "   export KATAGO_MODE=real KATAGO_BIN=./katago"
echo "   export LD_LIBRARY_PATH=$LIBS:\$LD_LIBRARY_PATH"
echo "   npm run dev"
