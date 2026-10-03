"""Shape-zoom plot: free, sigmoid, skip-18k variants, cube root.

Usage: python3 tournament/plot_shapes.py [out.png].
Skip-18k variants fit on 28 rungs (rung 2 excluded) and evaluate at all
29, showing what each shape says about 18k without seeing it.
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from tournament import fit, model_select as M, shapes as S, bezier
from tournament.schedule import rung_label

rows = []
for f in ["tournament/runs/wave1/games.jsonl",
          "tournament/runs/wave2/games.jsonl"]:
    for line in open(f):
        r = json.loads(line)
        rows.append({"black": r["black"], "white": r["white"],
                     "score": r["score"]})

xs_rel, ses = fit.fit_rungs(rows)
xs = [float(i + 1) for i in range(29)]
PIN = xs_rel[15]

free = [v - PIN + 1500 for v in xs_rel]

sig_full = S.sigmoid_fits(xs, xs_rel)
sig = [sig_full(x) - sig_full(16.0) + 1500 for x in xs]

keep = [i for i in range(29) if i != 2]
sig_skip = S.sigmoid_fits([xs[i] for i in keep], [xs_rel[i] for i in keep])
sig_s = [sig_skip(x) - sig_skip(16.0) + 1500 for x in xs]

# FP2, powers selected on 28 rungs (no 18k), evaluated at all 29.
tx, ty = [xs[i] for i in keep], [xs_rel[i] for i in keep]
best, be = None, None
for i, p1 in enumerate(M.POWERS):
    for p2 in M.POWERS[i:]:
        fn = M._ols_predictor(lambda x, ps=(p1, p2): M.fp_terms(x, ps))
        pred = fn(tx, ty)
        e = sum((pred(x) - y) ** 2 for x, y in zip(tx, ty))
        if be is None or e < be:
            best, be = (p1, p2), e
fp_fn = M._ols_predictor(lambda x, ps=best: M.fp_terms(x, ps))
fp_skip = fp_fn(tx, ty)
fp_s = [fp_skip(x) - fp_skip(16.0) + 1500 for x in xs]
print("skip-18k FP2 powers:", best)

# Bezier: fixed-knot control heights by OLS (cubic == cubic regression).
b3 = bezier.bezier_fits(3)(xs, xs_rel)
bez3 = [b3(x) - b3(16.0) + 1500 for x in xs]
b6 = bezier.bezier_fits(6)(xs, xs_rel)
bez6 = [b6(x) - b6(16.0) + 1500 for x in xs]

labels = [rung_label(i) for i in range(29)]
xi = list(range(29))
plt.figure(figsize=(13, 7))
plt.errorbar(xi, free, yerr=ses, fmt="o", ms=3, capsize=2, alpha=0.5,
             label="free fit (±SE)")
plt.plot(xi, sig, "-", label="sigmoid")
plt.plot(xi, sig_s, "--", label="sigmoid, 18k skipped")
plt.plot(xi, fp_s, "-.", label="FP2 %s, 18k skipped" % (best,))
plt.plot(xi, bez3, ":", lw=2, label="bezier-3 (== cubic)")
plt.plot(xi, bez6, linestyle=(0, (3, 1, 1, 1)), label="bezier-6")
plt.xticks(xi, labels, rotation=45)
plt.ylabel("WHR (5k = 1500 pin)")
plt.xlabel("bot rung")
plt.title("Shape zoom: what the curves say about 18k")
plt.legend(loc="upper left")
plt.grid(True, alpha=0.3)
plt.tight_layout()
out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/shapes.png"
plt.savefig(out, dpi=100)
print("wrote", out)
