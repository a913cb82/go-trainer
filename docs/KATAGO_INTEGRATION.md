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

## MCTS / Search-ahead (planned / M5)

**No MCTS in mock** — currently `P_h`/`W_s` from single analysis call; real KataGo uses `maxVisits` MCTS with `humanSLRootExploreProbWeightless`. To improve "tempting bad" accuracy, add 1-ply lookahead to mock:

- After candidate: simulate move `m`, find opponent's best reply `o` (via `strongWinrate` or quick MCTS with `maxVisits=50`), compute `G_after = best(W_s after o) - W_s(m)`. Use `G_after` instead of `G` for "bad" selection — teaches avoiding moves that give opponent key point.
- Implementation: extend `katagoClient` with `POST /lookahead {board, move, maxVisits}`; server does quick `analysis` of 1-ply continuation. Fallback: client-side `toSignMap` + `isLegal` to simulate basic capture only.

Real engine (`mode=real`) gets this for free via KataGo's MCTS (`maxVisits=200+`).

## TODO

- [x] Binary + libzip/libssl fix done; `mode=real` confirmed.
- [x] Real engine query protocol (JSON lines) working; `humanSLProfile` injected.
- [ ] MCTS stub / 1-ply lookahead (optional, improves tempting-bad)
- [ ] Verify `komi` 7.5 vs 7 effect; test 13×13 toggle; WASM deferred.
