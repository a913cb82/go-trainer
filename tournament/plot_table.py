"""Plot all five table columns. Usage: python3 tournament/plot_table.py [out.png]."""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from tournament import fit, calibrate, model_select as M
from tournament.schedule import rung_label

rows = []
for f in ["tournament/runs/wave1/games.jsonl",
          "tournament/runs/wave2/games.jsonl"]:
    for line in open(f):
        r = json.loads(line)
        rows.append({"black": r["black"], "white": r["white"],
                     "score": r["score"]})

xs_rel, ses = fit.fit_rungs(rows)
w = [1.0 / max(s, 1e-9) ** 2 if i else 1.0 for i, s in enumerate(ses)]
mono = calibrate.isotonic(xs_rel, w)
shift = 1500.0 - xs_rel[15]
free = [v + shift for v in xs_rel]
iso = [v + shift for v in mono]
lin = [1500 + (i - 15) * 38 for i in range(29)]
ship = [v + (115 if i == 2 else 0) for i, v in enumerate(lin)]

xx = [float(i + 1) for i in range(29)]
fn = M._ols_predictor(lambda x, ps=(3, 3): M.fp_terms(x, ps))
pred = fn(xx, xs_rel)
fp2 = [p + (1500.0 - pred(16.0)) for p in (pred(x) for x in xx)]

labels = [rung_label(i) for i in range(29)]
xi = list(range(29))

plt.figure(figsize=(13, 7))
plt.errorbar(xi, free, yerr=ses, fmt="o", ms=3, capsize=2, alpha=0.6,
             label="free fit (±SE)")
plt.plot(xi, iso, "s-", ms=3, label="isotonic")
plt.plot(xi, lin, "--", label="linear (38/rung)")
plt.plot(xi, fp2, "-.", label="FP2 (3,3)")
plt.plot(xi, ship, "k-", lw=2, label="ship (linear + 18k bump)")
plt.xticks(xi, labels, rotation=45)
plt.ylabel("WHR (5k = 1500 pin)")
plt.xlabel("bot rung")
plt.title("Tournament ladder: 2,280 games, all five estimates")
plt.legend(loc="upper left")
plt.grid(True, alpha=0.3)
plt.tight_layout()
out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/ladder.png"
plt.savefig(out, dpi=100)
print("wrote", out)
