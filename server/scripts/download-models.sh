#!/usr/bin/env bash
set -e
mkdir -p server/models server/config
echo "Downloading KataGo models (placeholder — update URLs to latest releases)..."
# Example: b28c512nbt human
# curl -L https://katagotraining.org/.../b28c512nbt-humanv0.bin.gz -o server/models/human.bin.gz
# curl -L https://katagotraining.org/.../b28c512nbt.bin.gz -o server/models/strong.bin.gz
echo "If KataGo not installed, server runs in MOCK mode — no download required for dev."
echo "Set KATAGO_BINARY=/path/to/katago KATAGO_MODE=real to use real engine."
# Minimal analysis config
cat > server/config/analysis.cfg <<'CFG'
numSearchThreads = 4
maxVisits = 200
maxPlayouts = 200
logSearchInfo = false
CFG
echo "Wrote server/config/analysis.cfg"
