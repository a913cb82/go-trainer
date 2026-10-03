# Ratings

Player skill is a Whole-History Rating (Coulom 2008). WHR tracks how skill
changes over time by refitting the full game history. Graphs and labels show
rank, never raw numbers. Ranks measure Go skill: kyu ranks are student
grades (20k weakest — the KataGo human-profile floor), dan ranks are master
grades (9d strongest).

## Conventions

- **Scale:** Elo — win probability `1 / (1 + 10^(-gap / 400))`. A 400-point
  gap means 10-to-1 odds.
- **Drift (`w²`):** drift models how fast skill changes. This one knob
  governs how fast the rating follows improvement. It starts at Coulom's
  Go-data fit and refits from our history later. The live value is `W2`
  in `Whr`.
- **Time:** whole days from real game timestamps. Same-evening bursts share
  a time point and gain almost no drift uncertainty. Uncertainty measures
  doubt in the rating.
- **Prior:** one virtual win plus one virtual loss against the 20k pin on
  the first day. New players start at 20k, uncertain.
- **Opponents:** 29 fixed bot rungs (20k–9d), FP2(3,3) fit to 2,120
  bot-vs-bot games (tournament branch, tag `calibration-2026-10-03`;
  rung-2 games excluded, 18k takes the smoothed value), 5k = 1500 pin.
  Anchors carry zero variance
  and zero color advantage. Recalibration edits the table only. Old 30k–21k
  records resolve to the 20k anchor — the only config below the floor — so
  no history migration was needed.
- **Solver:** one Newton chain over the player's game-days. The bots never
  move, so players never pull on each other. Tridiagonal Thomas solves run
  each pass. A backtracking line search keeps long streaks convergent.
  Full-history refits run in milliseconds.

## What the player sees

`whrToRank` maps WHR to fractional rungs (0 = 20k … 28 = 9d), clamped.
High uncertainty shows the provisional `?`, always beside the rank — never
bare. The threshold lives in `BotRatings`. The stats curve is the causal
refit: point g is the rating over games `1..g` only, so every win/loss moves
its own dot. Points are cached versioned per game (`RatingCurve`); game flow
writes only the latest point and Stats backfills stale entries lazily.

## Rated play and automatch

Every game records. The app writes a pending loss on the first ply, so
abandoned games stay losses. A
ply is one move by one player. Clean finishes upgrade the record to win or
draw. History stores rank IDs, not ratings, and the rating recomputes on
read — old games follow anchor changes with no migration. Automatch inverts
the WHR expected score to pick the rung nearest the target winrate.

## Scheduled refits

Anchor refit from bot-vs-bot tournaments, `w²` refit from our own history,
and a color-advantage check — in that order
(see [NEXT_STEPS.md](NEXT_STEPS.md)).

## See also

- [ARCHITECTURE.md](ARCHITECTURE.md) — where rating lives in the app
- [LEARNING_DESIGN.md](LEARNING_DESIGN.md) — retired multiple-choice mode
- [NEXT_STEPS.md](NEXT_STEPS.md) — the refit schedule
