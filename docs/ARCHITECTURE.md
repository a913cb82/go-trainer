# Architecture

Native Android app (Kotlin/Compose). Kotlin is the programming language.
Compose is Android's UI toolkit. One screen owns the game. A setup gate owns
the engine files. The `app/` web client and `server/` PC engine are
development-only and sit outside this picture.

## Screens

`MainActivity` shows `SetupScreen` until `SetupViewModel` reports Ready.
Ready means setup has staged the engine binary and downloaded the models.
Then it shows `GameScreen`. New games open from a sheet (`NewGameScreen`). Stats open
as an overlay (`StatsScreen`).

## Game core (`game/` package)

- `GameViewModel`: the only state owner. It persists every state change
  through DataStore. DataStore is Android's settings storage. It holds
  settings, rated history, and the full game snapshot. Quitting resumes
  exactly where the game stopped, including review position.
- `GameFlow`: pure game-flow decisions (opening move, resync indicator,
  automatch, rated-game bookkeeping). It has zero Android imports and full
  unit tests.
- `GoBoard`: rules (capture, ko, suicide). Ko is a repeating position the
  rules forbid. `Scoring`, `Sgf`: result text and export. SGF is the standard
  text format for Go game records.
- Thinking indicator: a single `isThinking` flag covers engine load and
  search alike. The player cannot tell them apart, by design.

## Engine (`engine/` package)

On-device KataGo over GTP (`KataGoGtpEngine`). GTP is the text protocol that
drives KataGo. The first use spawns the process and loads the models. Then a
persistent board serves all queries for the game. Split nets do the work: a
small net searches moves, a human net steers selection toward your rank
(see [KATAGO_INTEGRATION.md](KATAGO_INTEGRATION.md)). A visit is one
simulated continuation. Replies use a small visit budget (live in
`KataGoGtpEngine`). The app requires KataGo: no mocks, no fallbacks.

## Rating

Every game writes a pending loss record on the player's first ply. A
ply is one move by one player. Abandoned games stay losses. Scoring settles
the record. The rating is a WHR refit over stored history, recomputed on
read — never stored, so no migration. WHR (Whole-History Rating) tracks how
skill changes over time. Graphs and labels show rank, never raw numbers
(see [RATINGS.md](RATINGS.md)).

## Review

The winrate graph doubles as the review slider: drag it to revisit past
positions.

## See also

- [TECH_STACK.md](TECH_STACK.md) — why these libraries and not others
- [KATAGO_INTEGRATION.md](KATAGO_INTEGRATION.md) — engine wiring and models
- [RATINGS.md](RATINGS.md) — WHR design and rank mapping
- [LEARNING_DESIGN.md](LEARNING_DESIGN.md) — retired multiple-choice mode
