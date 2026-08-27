# KataGo Integration

## Models

- **HumanSL:** `b28c512nbt-humanv0` (or `b18c384nbt-humanv0`). Single net conditioned on rank via `humanSLProfile` / rank param. Verify with `katago analysis -help`.
- **Strong:** `b28c512nbt` for `W_s` ground truth.

Models gitignored in `server/models/`; fetched via `server/scripts/download-models.sh`.

## Engine

Spawn `katago analysis -model <bin.gz> -config analysis.cfg -config human.cfg`.
Protocol: JSON lines over stdin/stdout. No npm lib — write ~15-line JSON handler (GTP libs `@sabaki/gtp` etc are text-protocol only, skip).

Query:
```json
{"id":"q1","moves":[],"rules":"japanese","komi":7,"boardXSize":9,"boardYSize":9,"board":[],"analyzeTurns":["B"]}
```

Parse `moveInfos[].move, winrate, policy, scoreLead, ownership` for `P_h` and `W_s`.

## Queries

- **Candidates:** query both models on same position → compute `G`, pick per LEARNING_DESIGN thresholds → shuffle.
- **Genmove:** query HumanSL@rank, sample or argmax by config (temperature controls rank fidelity).
- Cache by board hash + rank.

## Deployment

Dockerfile installs `katago` binary + mounts `models/`. CPU fine for 9×9 ≤200 visits. Tune `numSearchThreads`, `maxVisits`.

## TODO

Verify exact HumanSL rank conditioning flag and `komi` 7 vs 7.5 effect on 9×9 human policy.
