# Engine plan (brainstorm — closed out, kept as history)

The user set this goal: fully on-device play on the Xiaomi 15 Ultra at
BadukAI-grade speed (moves under a second). A 9x9 board is small. Go is a
board game where Black and White place stones to control territory.

Outcome: on-device GTP shipped. No self-build ran. No remote server
remains. The sections below are the dated working notes behind that
outcome. Read [ON_DEVICE.md](ON_DEVICE.md) for the session evidence and
[the curated engine doc](../../docs/KATAGO_INTEGRATION.md) for the current
picture.

## What worked, in order

1. **Vendored binary, patched.** BadukAI v1.20.13's `libkatago.so`
   (KataGo v1.16.0, has `humanSLProfile`). v1.19 and v1.20 lack the human
   mode. v1.23 and later carry a path gate. The package gate yielded to
   the patch recipe in ON_DEVICE.md — the NDK self-build (approach B
   below) never ran.
2. **Split nets.** b10 searches, b18-human steers. Matches BadukAI's
   observable recipe. Bot replies measured 483ms. The b28 strong net left
   the device (nothing used it after the GTP cutover).
3. **Candidate breadth.** HumanSL off plus wide root noise for candidates:
   19 real moves at 150 visits. Symmetry filler filtered. Winrate
   carry-forward in free play.
4. **Threshold-free, then count-based selection.** Per-rank point edges
   left `CandidateSelector`. Rank enters only through the b18 human net
   (shortest policy-sorted prefix covering 85% of the human mass, minimum
   5). Quality is ordinal inside that pool (lowest loss wins). Strategies
   became count families (2+3, 4+1, tesuji, top-by-policy, top-by-score).
   Feedback pill colors come from group membership, never point cutoffs.
   Settings IDs stayed unchanged, so saved styles survived the rename.

Still open: `preaz_` opening style (UI-only follow-up), 1-ply lookahead for
tempting-bad accuracy, NPU path (newer binary plus optimized net plus SNPE
config). Pondering stays off (off in BadukAI too).

## History: the silent-binary problem (2026-09-20)

The binary started but exited 0 with zero output for EVERY subcommand
(`version`, `gtp`, `analysis`, `runtests`, bogus names, missing models).
No-args and `--help` printed full usage (2205 B), so the binary loads and
its argument parser runs. Reading the binary's machine code showed: `main`
returns 0 when a command lookup yields null. Dispatch switches on 2-byte
prefixes plus byte-compare chains. A `readlink` plus length-plus-compare
sequence sits in the dispatch path. Readlink reads a system path into text.
The data section holds `/proc/self/cwd`, `/data/app/`, and
`/net.kir.baduk_ai-` adjacently.

Ranked theories at the time (resolved since — the package gate was real):

1. **Package-anchored gate (was likely, now proven).** Some path must
   satisfy a predicate over `net.kir.baduk_ai`. Our patch missed because the
   predicate also wants a `/data/app/` prefix, compares at fixed offsets,
   or tests an unvaried path.
2. **Dispatch-table quirk.** The compare chain may genuinely not route
   `gtp` in this build. Counter: it is their shipped engine.
3. **Linker argv forwarding.** `linker64 <lib.so> args` may shift arguments,
   so lookup sees garbage. Testable only by behavior probes (no root for
   tracing).
4. **OS incompatibility (was unlikely).** A loader mismatch is normally
   LOUD. Page size still needed a test (`getconf PAGESIZE`).

## History: approaches A–D (2026-09-20)

- **A. Finish the RE, then minimal patch — CLOSED.** Web research proved
  the gate: the comparison spans 27 bytes, our package needs 32, so patching
  needs section surgery on a foreign closed binary. The only public patch
  is of unknown provenance. Verdict stood: do not ship that. Build our
  own (was B).
- **B. Eigen CPU self-build — NEVER RAN.** Upstream KataGo is MIT licensed
  with no gate by construction. Needed: NDK download (~1GB), CMake, source,
  `-j2` on 3.8GB RAM plus swap, time-boxed in background. The patch recipe
  won before this started.
- **C. BadukAI-parity forensics — ran dry.** Replicate their config and
  models byte-for-byte minus paths. Install their APK to separate "our
  invocation" from "OS vs old binary".
- **D. Remote engine — DONE, then REMOVED.** PC CUDA KataGo plus `adb
  reverse` plus explicit selector unblocked that night and served as the
  cross-check oracle. Removed with the selector once on-device won.

## History: GTP cutover notes (2026-09-20, proven on the phone since)

Target was BadukAI-consistent play (decompiled v1.23.0 wrapper as recipe):
persistent-board `katago gtp` with tree reuse. HumanSL bundle through
kata-set-params (maxVisits 10, temp 0.7/0.85, explore 0.0,
humanSLChosenMoveProp 1.0, cpuct 0.5/0.2) plus `humanSLProfile rank_<9d..20k>`
(30k–21k clamp to 20k). Bot move via `time_settings 0 10 1` plus
`kata-genmove_analyze <color> 25`. Candidates via `kata-analyze` at 25
visits with HumanSL ACTIVE (we need the human policy. BadukAI deactivates
for its unbiased panel, we have none). Threads 2 on Eigen, 8 with a .dlc
present. No 450ms UI delay. No opening book (19x19-only, irrelevant).
winrates BLACK-perspective.

Two corrections versus these notes: scoring runs Chinese rules at komi 7.5
in code (the note says Japanese/komi 7 — code is truth), and candidates run
150 visits, not 25.

## History: test ladder (applies to any approach)

1. PC: unit tests and lint green plus Paparazzi unchanged (no UI in the
   engine slice).
2. Phone probes with no rebuild: `version`, then bogus command, then
   `gtp` plus `quit` with file-redirected stdin (never pipes for exit
   codes), then minimal `analysis` JSON. Each must print bytes. Silence
   means fail.
3. Gameplay: fresh start, candidates under 2s, full 9x9 game vs bot, then
   the scoring call.
4. Perf: time the cold first query against warm repeats. Compare BadukAI
   feel.

## See also

- [ON_DEVICE.md](ON_DEVICE.md) — session evidence and current numbers
- [the curated engine doc](../../docs/KATAGO_INTEGRATION.md) — the picture
  that replaced this plan
