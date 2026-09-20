# Engine: getting KataGo ONTO the phone (brainstorm — living doc)

Goal (user-confirmed): fully on-device play on the Xiaomi 15 Ultra, BadukAI-grade
(<1s moves). Remote-via-PC works today and stays as the fallback/reference.

## Where we stand (2026-09-20, verified on-device)

- Shipped binary: BadukAI v1.20.13's `libkatago.so` (KataGo v1.16.0, has
  `humanSLProfile`, NO `/data/data` string — see ON_DEVICE.md for the version archaeology).
- Deps staged: libSNPE + libtensorflowlite beside it; `LD_LIBRARY_PATH` wired.
- Symptom: process starts, exits **0 with zero stdout/stderr**, for EVERY
  subcommand (`version`, `gtp`, `analysis`, `runtests`, bogus, missing model).
  No-args and `--help` print full usage (2205 B) — so the binary loads and its
  arg parser runs.
- Disassembly facts (capstone, AArch64 PLT0 = 32B so stubs are base+32+16*i):
  `main` returns 0 when a command lookup yields null; dispatch switches on
  2-byte prefix + memcmp-6/5/3 chains; a `readlink`+`strlen`+`memcmp len 0x17`
  sequence sits in the dispatch path; rodata holds `/proc/self/cwd`,
  `/data/app/`, `/net.kir.baduk_ai-` (18 B) adjacently.
- Ruled out: missing deps (fixed, was the first outage), bad config (no-config
  run also silent), model issues (missing-model AND model-free `runtests` also
  silent), cwd/layout matrix (files/, hexagon subdir, exact BadukAI layout,
  in-APK execution), argv[0] symlink, string patch `/net.kir.baduk_ai-` →
  `/com.gotrainer.nin` (byte-exact 19 B, both layouts).

## Candidate explanations (ranked)

1. **Package-anchored gate (likely).** Some path (cwd? .so path? argv[0]? exe?)
   must satisfy a predicate involving `net.kir.baduk_ai`. Our patch didn't hit
   because (a) the predicate also requires a `/data/app/` prefix our files
   layout lacks, (b) it memcmps at fixed offsets the `~~HASH` segments break,
   or (c) it tests a path we haven't varied. BadukAI passes trivially — its own
   install contains its own package.
2. **Dispatch-table quirk.** The 6/5/3-byte memcmp chain may genuinely not route
   `gtp`/`analysis` in this build (BadukAI fork drift?). Counter: it's their
   shipped engine; presumably GTP works for them.
3. **Linker argv forwarding.** Bionic `linker64 <lib.so> args` may deliver
   argc/argv shifted vs a normal exec, so lookup sees garbage → null → silent 0.
   Testable: strace-equivalent unavailable (no root); infer by invoking with
   junk argv variations and watching for ANY change in behavior.
4. **OS incompatibility (unlikely).** API-36 loader vs old binary would normally
   be LOUD (CANNOT LINK). Page size: check `getconf PAGESIZE` (16K kernels need
   aligned segments — usually loud too, but rule it out).

## Approaches

### A. (CLOSED 2026-09-20) Finish the RE, then minimal patch
Web research (philippmerz/badukai) confirmed the gate and killed this path:
the check is `/data/data/net.kir.baduk_ai` (27 B); our package needs 32 B
(`/data/user/0/com.gotrainer.nine`), requiring new-section surgery, and the
only public patch is of unknown provenance (author may never have run it).
Verdict: don't ship a patched foreign binary. Build our own (B).
- Finish mapping the readlink block: identify its INPUT path (which /proc entry?
  trace x0 backwards from 0x37e9b0) and the 18-byte memcmp operands (resolve
  x1+0xd54 / x20 at 0x37ec5c via backward slice).
- Then EITHER satisfy the predicate with layout (free) OR byte-exact same-length
  patch (proven tooling now: size asserts caught a 1-byte truncation once).
- Test loop (each ~2 min, no rebuild): push patched .so to /data/local/tmp,
  `run-as cp` into files/katago, probe `version`/bogus/`--help`, compare bytes.
- If predicate needs `/data/app/` prefix + package: run from a path we control
  that satisfies both — impossible without root (~~HASH segments unmatchable).
  FAIL-CLOSED to B.

### B. Eigen CPU self-build (clean, slow, certain-ish)
- Upstream KataGo, MIT, no gate by construction. Backend: Eigen (CPU).
- Needs: Android NDK download (~1GB), CMake, KataGo source, `-j2` on 3.8GB RAM
  (+8GB swap). Time-box a background attempt; failure mode is OOM (reduce to
  -j1, trim features).
- Perf question: Eigen b18/b28 on 8 Elite at 15-visit budgets — almost certainly
  <1s (15 evals is nothing), but unmeasured; BadukAI's 55–720 n/s table is for
  THEIR accelerated build. Our budgets are tiny enough that even 50 n/s works.
- If Eigen is too slow: revisit with OpenCL backend (clvk? needs GPU stack).

### C. BadukAI-parity forensics (cheap, runs dry?)
- Re-download v1.20.13 APK, extract ITS config/models invocation details
  (their exact cfg, model filenames, any wrapper scripts). Replicate byte-for-byte
  minus paths. If it still fails where theirs works, the ONLY delta is the
  package → confirms theory 1 and forces A/B.
- Install BadukAI v1.20.13 APK itself and check ITS engine runs on this phone —
  distinguishes "our invocation" from "OS vs old binary".

### D. Remote engine (DONE, tonight's unblocker)
- PC CUDA KataGo (GTX 1080 Ti) + `adb reverse` + explicit engine selector.
  Loud errors, no mocks. Also the cross-check oracle for on-device results later
  (same position → same scoreLead?).

## Test plan (applies to any approach)

1. PC: unit/verify/lint green + Paparazzi unchanged (no UI in this slice).
2. Phone probe ladder (no rebuild needed): `version` → bogus-cmd → `gtp`+`quit`
   with file-redirected stdin (NEVER pipes for exit codes) → `analysis` minimal
   JSON. Each must print bytes; silence = fail.
3. Gameplay: fresh launch → candidates <2s → full 9x9 game vs bot → scoring call.
4. Perf: time first-query (cold) vs subsequent (warm); compare BadukAI feel.

## Decision log
- 2026-09-20: v1.20.13 binary selected over v1.19 (no humanSL) and v1.23 (path check).
- 2026-09-20: GitHub KataGo release chosen for human net (katagotraining
  /modelsextra/ 403s bots); server script fixed to match.
- 2026-09-20: remote-engine interim ADDED (explicit selector, default REMOTE
  until on-device proven). No mocks, ever.

## GTP cutover (2026-09-20) — BadukAI-consistent play
Target: replace analysis-JSON transport with persistent GTP, matching BadukAI's
HumanSL recipe (decompiled v1.23.0, see /tmp/aki65/lzwrapper.py):
- `katago gtp`, persistent board, tree reuse across moves
- HumanSL bundle via kata-set-params (maxVisits=10, temp 0.7/0.85, explore 0.0,
  humanSLChosenMoveProp=1.0, cpuct 0.5/0.2) + kata-set-param humanSLProfile
  rank_<9d..20k> (30k-21k clamp to 20k); style stays rank_ (preaz_ = follow-up)
- Bot move: time_settings 0 10 1 + kata-genmove_analyze <color> 25 (move + winrate)
- Candidates: kata-set-param maxVisits 25 + kata-analyze (HumanSL stays ACTIVE —
  we need humanPrior; BadukAI deactivates for its unbiased panel, we don't have one)
- Score: final_score (Japanese rules, komi 7 — matches Scoring.KOMI)
- Threads: 2 on Eigen (measured), 8 when a .dlc is present (NPU, like BadukAI)
- No 450ms UI delay (BadukAI has none); no opening book (19x19-only, irrelevant)
- reportAnalysisWinratesAs=BLACK in gtp.cfg (our UI contract is BLACK-perspective)

## GTP cutover status: built, PC-green, awaiting phone
- KataGoGtpEngine (GTP persistent board, HumanSL bundle, 10/25 visits,
  time_settings 10s, final_score, threads 2/8-dlc) + 9 new unit tests.
- analysis engine + analysis.cfg DELETED; gtp.cfg asset added.
- PC gate: assemble + 54 unit tests + lint + Paparazzi (3 stale goldens for the
  pre-existing Engine-selector UI re-recorded) all green.
- MUST verify on phone: analyze text on OUR v1.16 binary (source read was
  master), kata-raw-human-nn presence, grid orientation (log-only check coded),
  BLACK perspective of text winrates, full game + final_score vs manual count.

## Split-net play — DONE (2026-09-20)
b10 (bundled asset) searches, b18-human steers. Matches BadukAI's observable
recipe; bot replies 483ms vs their ~1s class. Rev v6. b28 strong net deleted
from the device (obsolete since the GTP cutover; nothing used it).
Remaining BadukAI deltas: pondering (off there too), .dlc/NPU (no evidence
they use it for HumanSL), preaz_ opening style (UI-only follow-up).

## Candidate breadth + remote removal — DONE (2026-09-20)
HumanSL off + analysisWideRootNoise 0.20 for candidates (19 real moves @150v);
symmetry filler filtered; winrate carry-forward in free play; remote engine and
its permission/config deleted from the app. Gate: 65 unit tests + lint + Paparazzi.

## Threshold-free selection — DONE (2026-09-20)
Per-rank point thresholds are gone from CandidateSelector. Rank enters only
through the b18 human net (cumulative-mass pool: shortest policy-sorted prefix
covering 85% of the human mass, min 5). Quality is ordinal within that pool
(lowest loss = best). Strategy shapes: good-vs-tempting 2+3, blunder-check
4+1, tesuji 1 best (objective) + rest, human-like = top 5 by policy, strongest
= top 5 by score. Ties break toward the more human move (stable sort); groups
never overlap (dedup + fill). RankTest/thresholds survive only as feedback
pill coloring.
