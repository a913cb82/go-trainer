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

Parse `moveInfos[].move, winrate, humanPrior, policy, scoreLead, ownership` for `P_h` and `W_s`. `humanPrior` is the raw HumanSL policy used for rank imitation.

## HumanSL profiles and queries

KataGo supports two rank-profile families:

- `rank_<RANK>` — modern opening style (recommended for this app).
- `preaz_<RANK>` — pre-AlphaZero/2016 opening style, useful only when intentionally imitating that era.

For rank imitation, KataGo recommends setting `humanSLProfile`, preserving history with `ignorePreRootHistory=false`, requesting `includePolicy=true`, and sampling moves proportional to `humanPolicy` (even 1 visit is sufficient for raw imitation). The server now follows this for White and ranks candidate choices by `humanPolicy`; it also sends the real move history rather than reconstructing a fake row-major sequence.

- **Candidates:** query both models on same position → compute `G`, pick per LEARNING_DESIGN thresholds → shuffle.
- **Genmove:** query HumanSL@rank and sample from `humanPolicy`; pass only when KataGo's top result is pass.
- Cache by board hash + rank.

## Deployment

Dockerfile installs `katago` binary + mounts `models/`. CPU fine for 9×9 ≤200 visits. Tune `numSearchThreads`, `maxVisits`.

## MCTS / Search-ahead (planned / M5)

Real KataGo uses `maxVisits` MCTS. To improve "tempting bad" accuracy, could add 1-ply lookahead:

- After candidate: simulate move `m`, find opponent's best reply `o` (via `strongWinrate` or quick MCTS with `maxVisits=50`), compute `G_after = best(W_s after o) - W_s(m)`. Use `G_after` instead of `G` for "bad" selection — teaches avoiding moves that give opponent key point.
- Implementation: extend `katagoClient` with `POST /lookahead {board, move, maxVisits}`; server does quick `analysis` of 1-ply continuation. Fallback: client-side `toSignMap` + `isLegal` to simulate basic capture only.

Real engine (`mode=real`) gets this for free via KataGo's MCTS (`maxVisits=200+`).

## TODO

- [x] Binary + libzip/libssl fix done; `mode=real` confirmed.
- [x] Real engine query protocol (JSON lines) working; `humanSLProfile` injected.
- [ ] MCTS stub / 1-ply lookahead (optional, improves tempting-bad)
- [ ] Verify `komi` 7.5 vs 7 effect; test 13×13 toggle; WASM deferred.
