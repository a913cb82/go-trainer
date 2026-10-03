"""Fit contract: logistic Elo MLE over pairwise outcomes, scale 400.

Bots don't drift, so no time dimension: each game is one row
(black_rung, white_rung, black_score). Rung 0 pins at 0.0 (relative);
the 20k=800 absolute pin applies when writing the app table.
"""

import math
import random
import unittest

from tournament import fit


def synth(true_gaps, games_per_pair=2000, seed=3):
    """Simulated rows from known rung gaps (logistic, scale 400)."""
    rng = random.Random(seed)
    rungs = len(true_gaps) + 1
    rows = []
    for a in range(rungs):
        for b in range(a + 1, rungs):
            xa = sum(true_gaps[:a])
            xb = sum(true_gaps[:b])
            p = 1.0 / (1.0 + 10.0 ** (-(xa - xb) / 400.0))
            for k in range(games_per_pair):
                black, white = (a, b) if k % 2 else (b, a)
                # Winner drawn from Black's perspective.
                pb = p if black == a else 1.0 - p
                rows.append({
                    "black": black, "white": white,
                    "score": 1.0 if rng.random() < pb else 0.0,
                })
    return rows


class FitTest(unittest.TestCase):
    def test_recovers_known_gaps(self):
        xs, ses = fit.fit_rungs(synth([40.0, 40.0]))
        self.assertAlmostEqual(xs[0], 0.0, places=9)  # pin holds
        self.assertEqual(ses[0], 0.0)  # pin has no variance by construction
        self.assertAlmostEqual(xs[1] - xs[0], 40.0, delta=8.0)
        self.assertAlmostEqual(xs[2] - xs[1], 40.0, delta=8.0)
        self.assertTrue(all(s > 0 for s in ses[1:]))

    def test_standard_errors_shrink_with_data(self):
        _, ses_few = fit.fit_rungs(synth([40.0], games_per_pair=50))
        _, ses_many = fit.fit_rungs(synth([40.0], games_per_pair=400))
        self.assertGreater(ses_few[1], ses_many[1])

    def test_inversion_posterior_is_coin_flip_for_equals(self):
        # Perfectly balanced evidence (no RNG): fitted gap is exactly zero.
        rows = (
            [{"black": 0, "white": 1, "score": 1.0}] * 100
            + [{"black": 0, "white": 1, "score": 0.0}] * 100
            + [{"black": 1, "white": 0, "score": 1.0}] * 100
            + [{"black": 1, "white": 0, "score": 0.0}] * 100
        )
        xs, ses = fit.fit_rungs(rows, n_rungs=2)
        self.assertAlmostEqual(xs[1] - xs[0], 0.0, places=6)
        self.assertAlmostEqual(fit.p_inverted(xs, ses, 0, 1), 0.5, places=9)

    def test_strong_evidence_kills_inversion_probability(self):
        xs, ses = fit.fit_rungs(synth([120.0], games_per_pair=400))
        self.assertLess(fit.p_inverted(xs, ses, 0, 1), 0.01)

    def test_triangle_check_passes_consistent_data(self):
        rows = synth([40.0, 40.0, 40.0], games_per_pair=300)
        bad = fit.triangle_violations(rows)
        self.assertEqual(bad, [])

    def test_row_validation_rejects_garbage(self):
        with self.assertRaises(ValueError):
            fit.check_row({"black": 0, "white": 99, "score": 1.0})
        with self.assertRaises(ValueError):
            fit.check_row({"black": 1, "white": 1, "score": 1.0})
        with self.assertRaises(ValueError):
            fit.check_row({"black": 0, "white": 1, "score": 0.7})


if __name__ == "__main__":
    unittest.main()
