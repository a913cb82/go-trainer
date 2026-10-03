"""Stripped shape plot: everything ignores 18k. Usage: plot_shapes.py [out].

Free values refit on games not involving rung 2 (28 identified rungs;
18k left blank). FP2/bezier-3/5/6 fit on those 28 and evaluated at all
29, so each curve's 18k prediction shows in the gap. 5k pin throughout.
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from tournament import fit, model_select as M, bezier
from tournament.schedule import rung_label

rows = []
for f in ["tournament/runs/wave1/games.jsonl",
          "tournament/runs/wave2/games.jsonl"]:
    for line in open(f):
        r = json.loads(line)
        rows.append({"black": r["black"], "white": r["white"],
                     "score": r["score"]})

norows = [r for r in rows if r["black"] != 2 and r["white"] != 2]
print("games without 18k: %d / %d" % (len(norows), len(rows)))
free28, se28 = fit.fit_rungs(norows, n_rungs=29)

xs = [float(i + 1) for i in range(29)]
keep = [i for i in range(29) if i != 2]
tx, ty = [xs[i] for i in keep], [free28[i] for i in keep]

best, be = None, None
for i, p1 in enumerate(M.POWERS):
    for p2 in M.POWERS[i:]:
        fn = M._ols_predictor(lambda x, ps=(p1, p2): M.fp_terms(x, ps))
        pred = fn(tx, ty)
        e = sum((pred(x) - y) ** 2 for x, y in zip(tx, ty))
        if be is None or e < be:
            best, be = (p1, p2), e
print("skip-18k FP2 powers:", best)
fp = M._ols_predictor(lambda x, ps=best: M.fp_terms(x, ps))(tx, ty)

curves = {"FP2 %s" % (best,): [fp(x) for x in xs]}
for deg in (3, 5, 6):
    b = bezier.bezier_fits(deg)(tx, ty)
    curves["bezier-%d" % deg] = [b(x) for x in xs]

PIN = free28[15]
free = [free28[i] - PIN + 1500 for i in keep]
se = [se28[i] for i in keep]
for name in curves:
    v = curves[name]
    c = v[15]
    curves[name] = [a - c + 1500 for a in v]

labels = [rung_label(i) for i in range(29)]
plt.figure(figsize=(13, 7))
plt.errorbar(keep, free, yerr=se, fmt="o", ms=4, capsize=2, alpha=0.6,
             label="free fit, no 18k games (±SE)")
styles = ["-", "--", "-.", ":", (0, (3, 1, 1, 1))]
for (name, v), st in zip(sorted(curves.items()), styles):
    plt.plot(range(29), v, linestyle=st, label=name)
plt.xticks(range(29), labels, rotation=45)
plt.ylabel("WHR (5k = 1500 pin)")
plt.xlabel("bot rung")
plt.title("All blind to 18k: what each shape predicts in the gap")
plt.legend(loc="upper left")
plt.grid(True, alpha=0.3)
plt.tight_layout()
out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/shapes.png"
plt.savefig(out, dpi=100)
print("wrote", out)
