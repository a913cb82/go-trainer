"""Schedule contract tests (stdlib unittest: python3 -m unittest)."""

import unittest

from tournament import schedule as S


class ScheduleTest(unittest.TestCase):
    def test_ladder_shape(self):
        self.assertEqual(S.RUNGS, 29)
        self.assertEqual(len(S.ladder_pairs()), 28 + 27 + 26)  # 81
        for a, b in S.ladder_pairs():
            self.assertTrue(1 <= abs(a - b) <= 3)
            self.assertTrue(0 <= a < 29 and 0 <= b < 29)

    def test_long_range_shape(self):
        self.assertEqual(len(S.LONG_RANGE), 10)
        self.assertIn((0, 28), S.LONG_RANGE)  # full span present
        for a, b in S.LONG_RANGE:
            self.assertTrue(0 <= a < b < 29)

    def test_labels_mirror_app(self):
        self.assertEqual(S.rung_label(0), "20k")
        self.assertEqual(S.rung_label(19), "1k")
        self.assertEqual(S.rung_label(20), "1d")
        self.assertEqual(S.rung_label(28), "9d")
        with self.assertRaises(ValueError):
            S.rung_label(29)

    def test_schedule_size(self):
        for n in (1, 10):
            self.assertEqual(len(S.build_schedule(n, seed=7)), 91 * 2 * n)

    def test_color_balance_per_pairing(self):
        games = S.build_schedule(10, seed=7)
        for (a, b) in S.all_pairings():
            black = sum(1 for g in games if g == (a, b))
            white = sum(1 for g in games if g == (b, a))
            self.assertEqual(black, 10, (a, b))
            self.assertEqual(white, 10, (a, b))

    def test_seed_reproducible_and_matters(self):
        s1 = S.build_schedule(3, seed=1)
        self.assertEqual(s1, S.build_schedule(3, seed=1))
        self.assertNotEqual(s1, S.build_schedule(3, seed=2))
        # Interleaved, not grouped: first 10 games span several pairings.
        self.assertGreater(len(set(s1[:10])), 3)


if __name__ == "__main__":
    unittest.main()
