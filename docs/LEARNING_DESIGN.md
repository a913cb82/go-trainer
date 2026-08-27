# Learning Design

Every player turn shows `n` moves (A–E) to choose from. The teaching signal is *which* n.

## Inputs

- `P_h(m)`: HumanSL policy at selected rank (how often a human at that rank plays m).
- `W_s(m)`: Strong KataGo win-rate. `G(m) = max(W_s) - W_s(m)` = gap to best.

## Strategies

**S1 — Good-vs-Tempting-Bad (default): `n=5` → 3 good + 2 tempting bad, shuffled, unlabeled.**
- Good: `P_h` top-8, `G ≤ 1.5–2%`, strong top-10.
- Tempting bad: `P_h` top-8, `G ≥ 4–6%` (tighter at higher rank). Classic human bias the player must learn to reject.

Rank thresholds:

| Rank | Good `G` | Bad `G` | Spread |
|------|----------|---------|--------|
| 15k–10k | ≤2% | ≥6% | ~8% |
| 9k–5k  | ≤2% | ≥4.5% | ~6% |
| 4k–1k  | ≤1.5% | ≥3.5% | ~4% |
| Dan    | ≤1% | ≥2.5% | ~2–3% |

**S0 — Human top-N:** top-N by `P_h` only. Realistic but low contrast.

**S2 — Find the tesuji:** 1 strong-best low-`P_h` + 4 high-`P_h` bad. Use sparingly (puzzle mode).

**S3 — Blunder check:** 4 good + 1 huge gap `G ≥ 8–10%`. Good for early kyu.

**S4 — Strong top-N:** top-N by `W_s`. For review mode.

Default: S1. User can pick `n=3/5` and strategy per game.

## Feedback (after pick only)

- Reveal ordered by `W_s` with colors (green/yellow/red by `G`), `P_h%`, `Δ`, and rule-based tag (`atari`, `cut`, `empty triangle`).
- Ownership heatmap toggle + win-rate sparkline.

No pre-pick win-rates.

## Edge Cases

- Filter illegal/suicide, deduplicate adjacent traps.
- If not enough moves meet thresholds (endgame), reduce `n` or relax thresholds.

## Tuning

Log `G(picked)` per turn; mean should trend to 0. Opt-in telemetry for bad-pick rate feeds threshold tuning.
