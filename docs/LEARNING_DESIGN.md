# Learning Design

Every player turn shows candidate moves to choose from. A candidate is one
move you may play. The teaching signal is *which* moves the app offers.

## Inputs

- `P_h(m)`: HumanSL policy at the selected rank (how often a human at that
  rank plays move m). HumanSL is KataGo's mode that copies human play. One
  `kata-analyze` pool serves each turn. A visit is one simulated
  continuation.
- Loss(m): predicted points dropped against the strong best
  (`bestScore - strongScore`).

## Strategies

The setup sheet offers two sliders — best moves and worst moves — or free
choice with no suggestions. The live ranges sit in `NewGameScreen`.
Strategies form named shapes over the same best/worst split:

- **Good-vs-tempting** (default): lowest-loss human moves plus the
  highest-loss moves a human at this rank would still play.
- **Human-only**: most human moves, no score filter.
- **Strong-only**: objectively best moves, for review.
- **Tesuji**: the objective best (possibly rank-atypical) hidden among human
  bad moves. Tesuji means a skillful tactical move.
- **Blunder-check**: mostly good moves with one planted blunder. A blunder
  is a very bad move.
- **Split 3-2**: three good moves, two bad moves.

Selection re-ranks the single analyze pool — no extra query runs (see
`CandidateSelector`). Tags are `good` / `ok` / `overconcentrated`.
Overconcentrated means too many of your stones crowd one area. Both colors
rank from their own side to move. Thin pools top up in character.

## Feedback (after pick only)

The reveal orders moves by strong winrate with the loss gap against best.
Strong winrate is KataGo's estimate from full-strength search. The scope
covers all moves or picked-only. No values show before the pick. The winrate
graph doubles as the review slider. Winrate is the estimated chance to win.
(Ownership heatmap: the engine stores ownership data at scoring, but no
overlay exists yet.)

## Rated play

Only free-choice games rate (see [RATINGS.md](RATINGS.md)). Suggestions
games always leave no trace, even opted in.

## See also

- [ARCHITECTURE.md](ARCHITECTURE.md) — where candidates and feedback live
- [RATINGS.md](RATINGS.md) — rated play and automatch
- [KATAGO_INTEGRATION.md](KATAGO_INTEGRATION.md) — the analyze pool behind it
