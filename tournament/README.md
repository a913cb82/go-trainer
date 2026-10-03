# Bot tournament: calibrating the 20k–9d ladder

Bot-vs-bot round-robin to replace the OGS-derived `WhrAnchors` spacing
with measured gaps. **This directory lives on the `tournament` branch
only and never merges to main.** Main sees exactly one commit per
calibration: the table edit + `CURVE_VERSION` bump, with a message naming
the tag below.

## Workflow

1. **Pilot** (~30 min): determinism check (same matchup ×4 — identical
   games mean 10-visit MCTS has no usable noise; stop and diversify
   openings), throughput scaling at 1/2/3/4 parallel streams, 3-rung
   mini-fit. Gate: the opener below diffs clean against the app's engine
   log (`KataGoGtpEngine` is source of truth for every value in `gtp.py`).
2. **Wave one**: `build_schedule(n=10, seed=…)` → 1,820 games (~2–2.5h).
   Interleaved order; both colors; failures quarantined, never fitted.
3. **Fit**: `fit.py` → gaps + SEs + triangle check + inversion report.
   Isotonic monotonicity if the free fit flips neighbors.
4. **Wave two** (conditional): disputed pairs + loosest gaps only.
5. **Land**: write the table (20k pinned at 800), tag
   `calibration-<date>`, one commit on main naming the tag.

## Layout

- `schedule.py` — pairing enumeration + seeded schedule (tested).
- `gtp.py` — opener shaping, response parsing, engine subprocess (tested
  except the process loop, which needs the binary).
- `fit.py` — logistic Elo MLE (scale 400), SEs, inversion posterior,
  triangle violations (tested on synthetic data with known gaps).
- `test_*.py` — stdlib unittest: `python3 -m unittest discover -s tournament`.
- `calibrations/<date>/` — committed evidence per run: `manifest.json`
  (binary version, net hashes, params, pairing plan, N, seed),
  `games.jsonl` (the fit input), `table.json` (fitted output).
- `runs/` — gitignored scratch: raw SGFs, engine logs. Archive the
  tarball with the tag; it never enters git.

## Provenance rule

Git holds everything needed to reproduce and audit a calibration,
nothing needed merely to run one. Binaries and nets stay out (as in
`server/`); fetch them per the manifest URLs and verify hashes.
