# 13×13 Mode — Plan

Status: planned (Phase 0 decided: split ratings, Unranked-first).
Scope: free-play 13×13 alongside 9×9. Same 20k–9d ladder shape, same
komi 7.5 Chinese rules, no handicap.

Non-goals: 19×19, handicap stones, shared cross-size rating, mid-game
size switching, migrating old saves beyond "v2 reads as 9×9".

## Why this shape

Three workstreams in dependency order. The only real decision was
ratings: the anchor table is a fit to 2,120 9×9 bot games and rung gaps
move with board size, so 13×13 gets its own history keys, anchor table,
and calibration. Everything else is mechanical — `GoBoard`, `Sgf`,
komi, the conv nets, and the tournament scheduler are already
size-free.

HumanSL needs no per-size tuning: KataGo auto-scales
`chosenMoveTemperatureHalflife` by board area
(`searchhelpers.cpp:interpolateEarly`, `searchparams.h:76` — "scales
for board sizes other than 19"). Effective halflife is ~38 moves on
9×9, ~55 on 13×13, 80 on 19×19: proportionally the same game phase.
Under pure imitation (`piklLambda 1e8`) visits don't affect move choice
either, so `PLAY_VISITS = 10` stands on 13×13. Only one humanSL net
exists (b18 humanv0, 19×19-trained, plays any size); do not substitute
the 9×9 strength net (self-play, no rank conditioning).

## Phase 0 — decisions (done)

- Split ratings by size; ship 13×13 Unranked first, enable Rated after
  calibration; free-play only.

## Phase 1 — engine + logic (TDD, unit tests first)

- `KataGoGtpEngine.newGame(rank, boardSize)`: send `boardsize N`
  before `clear_board`/profile. `GoEngine.newGame` gains the param
  (default 9).
- Parameterize coordinate parsing: `parsePoint`, `PLAY_LINE`,
  analyze-move filter → size-aware letter/digit ranges (letters table
  already long enough — bounds only); size through `syncTo`/`playMove`
  `gtpCoord`; pass-move fallback `(6,6)` on 13.
- `GameState.boardSize` (default 9); serde `v2 → v3` (v2-without-size
  reads as 9); `chunked(size)`, `size*size` validation. Board rebuilds
  from history — no blob change.
- `GameViewModel`: thread `state.boardSize` into `syncBoard`,
  `captureFxFor`, `currentPosition`, `botReply` legality replay, engine
  `newGame`, review path. `applySetup` gains size; `newGame()`
  preserves it.
- Tests: serde round-trip with size; 13×13 `parsePoint` (`M13`,
  two-digit rows); `replay`/`fromHistory` at 13. Existing 9×9 tests
  pass unmodified.

## Phase 2 — UI

- `BoardView(boardSize)`: cell divisor `size-1`, loops `until size`,
  labels `(size - i)`, hoshi branch — 13×13 (0-indexed):
  `(3,3),(3,9),(9,3),(9,9),(3,6),(6,3),(6,9),(9,6),(6,6)`.
- `NewGameScreen`: "Board Size" section (9×9 / 13×13 segmented, same
  pattern as Game Type) + `draftBoardSize` plumbing through
  `GameScreen` draft/reset/`applySetup`. 13×13 forces Unranked with a
  "calibration pending" note until Phase 5.
- Titles size-aware (`"Go 9×9"`, `"Go 9×9 Trainer"`).
- Paparazzi: new 13×13 setup + in-game goldens; existing goldens pass
  unmodified.
- On-device: full 13×13 game vs bot, pass-pass scoring,
  rotation/resume (v3 serde), SGF export shows `SZ[13]`.

## Phase 3 — feel check (one build, one eyeball game)

- Confirm pass/resign still triggers sanely on the bigger board at 10
  visits. No visit bump, no halflife retune (see above).
- Opener eyeball-test; adjust only if openings look off vs 9×9.
- Larger-board NN cache only if second-order slowness shows.

## Phase 4 — 13×13 calibration (tournament branch, never merges)

- Constants-only change: `BOARD 9→13`, `MAX_PLIES 350→~1000`,
  `SZ[9]→SZ[13]`; `schedule.py` reused verbatim.
- Wave-one (~1,800 games, ~2–2.5h) → fit → disputes → conditional
  wave-two, same nested-LOOCV + game-BIC protocol as 9×9. Eyeball a
  sample of bot games first (19×19-trained human net on 13×13 is
  undocumented territory).
- Land on main as one commit: second anchor table + `CURVE_VERSION`
  bump + evidence tag, exactly like `calibration-2026-10-03`.

## Phase 5 — rated 13×13 + polish

- Split stores: `rated_history_13` / `rating_curve_13`;
  `GameFlow`/`GameViewModel` read/write per current size; automatch
  inverts the 13×13 table.
- Stats screen: size filter (All/9×9/13×13 or per-current-size; All
  must stay sane).
- Enable Rated toggle for 13×13.
- `server/` + `app/` dev stacks: board-size passthrough (`tsc` +
  vitest green).
- Docs: `RATINGS.md`, `KATAGO_INTEGRATION.md`, `ON_DEVICE.md`.

## Risks

- Bot quality on 13×13 at 10 visits (mitigated: Phase 3 eyeball
  before promising Rated; 9d caveat — raw net may undershoot top
  dans, documented `gtp_human9d_search_example.cfg` recipe held in
  reserve).
- Calibration compute: half a day unattended; wave-two only on
  disputes (same policy as 9×9).
- Stats All-view with two pools: size segmented control, curve code
  already handles arbitrary histories.

## References

- Official 5k recipe: `cpp/configs/gtp_human5k_example.cfg` (upstream
  KataGo) — our 0.85/0.70/80 matches it, not anything BadukAI-specific
  (BadukAI uses stock config, no HumanSL).
- Halflife scaling: `cpp/search/searchhelpers.cpp:558-562`,
  `cpp/search/searchparams.h:76`.
- OGS thread (visit calibration per level):
  `forums.online-go.com/t/katago-v1-15-x-new-human-like-play-and-analysis/52489`.
