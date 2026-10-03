"""Assemble a calibration from a run dir. Usage:

  python3 tournament/fit_run.py tournament/runs/wave1 --out tournament/calibrations/2026-10-03

Writes table.json (20k=800 pinned), report.txt, and copies the run
manifest. Policy: free MLE -> triangle check -> isotonic monotonicity ->
wave-two targets. No hand-tuning, ever.
"""

import argparse
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

from tournament import calibrate, fit  # noqa: E402
from tournament.schedule import RUNGS, rung_label  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run_dir")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    rows = []
    resigns = 0
    with open(os.path.join(args.run_dir, "games.jsonl")) as f:
        for line in f:
            r = json.loads(line)
            rows.append({"black": r["black"], "white": r["white"],
                         "score": r["score"]})
            resigns += r.get("resigns", 0)
    print("games: %d (resigns-as-passes: %d)" % (len(rows), resigns))

    xs, ses = fit.fit_rungs(rows, RUNGS)
    weights = [1.0 / max(s, 1e-9) ** 2 if i else 1.0
               for i, s in enumerate(ses)]
    mono = calibrate.isotonic(xs, weights)
    pooled = sum(1 for a, b in zip(mono, mono[1:]) if b - a < 1e-9)
    table = calibrate.to_table(mono)

    bad = fit.triangle_violations(
        [{"black": r["black"], "white": r["white"], "score": r["score"]}
         for r in rows], RUNGS)
    disputes = []
    for i in range(RUNGS - 1):
        p = fit.p_inverted(xs, ses, i, i + 1)
        if p >= calibrate.DISPUTE_THRESHOLD:
            disputes.append((rung_label(i), rung_label(i + 1), round(p, 3)))

    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "table.json"), "w") as f:
        json.dump([{"rung": i, "label": rung_label(i),
                    "whr": round(v, 3), "se": round(s, 1)}
                   for i, (v, s) in enumerate(zip(table, ses))],
                  f, indent=1)
    man = os.path.join(args.run_dir, "manifest.json")
    if os.path.exists(man):
        shutil.copy(man, os.path.join(args.out, "manifest.json"))

    widest = sorted(range(1, RUNGS), key=lambda i: -ses[i])[:5]
    lines = [
        "games=%d resigns=%d" % (len(rows), resigns),
        "pooled blocks (isotonic ties): %d" % pooled,
        "triangle violations: %d" % len(bad),
    ]
    for a, c, emp, gap, se in bad:
        lines.append("  %s-%s emp=%+.0f fit=%+.0f se=%.0f"
                     % (rung_label(a), rung_label(c), emp, gap, se))
    lines.append("disputed adjacent pairs (wave-two candidates): %d"
                 % len(disputes))
    for a, b, p in disputes:
        lines.append("  %s-%s P(genuine)=%.3f" % (a, b, p))
    lines.append("loosest rungs: %s"
                 % ", ".join("%s(±%.0f)" % (rung_label(i), ses[i])
                             for i in widest))
    report = "\n".join(lines) + "\n"
    with open(os.path.join(args.out, "report.txt"), "w") as f:
        f.write(report)
    print(report)


if __name__ == "__main__":
    main()
