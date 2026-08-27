# Plan — 9×9 KataGo Teaching Game

> Source of truth is `README.md`; this file is the executable checklist.

## Decisions to Lock Early

- [x] **Frontend framework:** React + Vite (default) vs SvelteKit — either works
- [ ] **Backend language:** Node (TS) vs Python — pick one
- [x] **KataGo deployment:** Hosted CPU server first; WASM deferred to M5 (no npm wrapper, speak JSON directly)
- [x] **Board lib:** `@sabaki/shudan` 1.8.0 + `@sabaki/go-board` 1.4.3 — reuse, don't rewrite (`jgoboard`/`goban` are alts)
- [x] **SGF:** `@sabaki/sgf` 3.5.0
- [ ] **Capacitor vs TWA:** Capacitor for store listing; PWA alone for sideload

---

## M0 — Scaffold (1–2 days) — *no KataGo yet*

- [ ] `app/` Vite+TS+React, `vite-plugin-pwa`, ESLint/Prettier, Vitest; `npm i @sabaki/shudan @sabaki/go-board @sabaki/sgf Zustand`
- [ ] Board via `@sabaki/shudan` (signMap/paintMap) + A–E markers, last-move/ko highlight, responsive + touch
- [ ] Client `goban.ts` wraps `@sabaki/go-board` (capture/ko/suicide/legality); scoring via board lib + server `scoreLead`
- [ ] Basic play loop vs random/pseudo opponent, pass (2× ends), undo, new game, SGF via `@sabaki/sgf`
- [ ] Rank & `n` selector UI (no backend wiring yet — uses local random candidates)

**Done when:** can play a full 9×9 game in browser, tap stone, see captures, on refresh still PWA shell.

## M1 — KataGo Server (2–4 days)

- [ ] `server/` service: spawn `katago analysis` engine, JSON lines protocol, `boardSize:9`, `komi:7` (or 7.5)
- [ ] Load models: `b28c512nbt-humanv0` (or `b18c384nbt`) + strong `b28c512nbt` — document in `KATAGO_INTEGRATION.md`
- [ ] HumanSL difficulty mapping: verify rank → model/profile param (single human model with rank conditioning vs multiple bins). Expose `GET /ranks`.
- [ ] Endpoints:
  - `POST /genmove` — `{ board, player, rank } → { move, winrate }` (opponent)
  - `POST /candidates` — `{ board, rank, n, strategy } → { moves: [{x,y, humanPolicy, strongWinrate, tag}] }` (player's n choices)
  - `POST /evaluate` — `{ board, move } → { winrate, scoreLead, ownership }` for feedback
  - WS `subscribe` for streaming analysis (optional)
- [ ] Caching & batching: debounce board position, reuse search
- [ ] Docker + `docker-compose.yml` (katago binary + model mount), healthcheck, model download script (gitignored)
- [ ] Frontend wiring: replace random with real candidates/opponent moves, loading states

**Done when:** pick “10k”, get 5 HumanSL-conditioned move suggestions + opponent replies at that level.

## M2 — Choice UI & Feedback (2–3 days)

- [ ] Choice markers A–E (shuffled), tap to commit, confirm/cancel, keyboard 1–5
- [ ] Strategy selector: `good-tempering` (default), `human-only`, `tesuji`, `blunder-check` — backed by server `strategy` param
- [ ] Feedback panel *after* pick: ordered list, human% vs strong win-rate, Δ win-rate, ownership heatmap toggle
- [ ] Win-rate graph over moves (Recharts or custom canvas), score estimate bar
- [ ] Teachable moment copy: short tag per move (atari, cut, shape) — rule-based from ownership/liberties, no LLM yet
- [ ] Settings persistence (localStorage), game history, SGF import/export (full)

**Done when:** every player move is a 5-way choice with post-hoc ranking that feels instructive.

## M3 — Learning Polish (1–2 weeks, iterative)

- [ ] Tune “tempting bad” picker: gap thresholds per rank, deduplicate nearby points, avoid symmetric dupes
- [ ] Rank-graduated spread: wider at kyu, tighter at dan; A/B test thresholds
- [ ] Hint toggle: free-play (no hints) vs choice mode per game or per move
- [ ] Review mode: step through SGF with same candidate overlay, mark mistakes spaced-repetition
- [ ] Optional LLM explainer (server-side, off by default): “Why B is overconcentrated here”
- [ ] Sound, animations, atari alert, pass/resign/scoring UX, komi selector

**Done when:** 10k and 3d both feel distinct; testers say “I’m learning to avoid X”.

## M4 — Android Portability (1–2 days)

- [ ] PWA audit: manifest, icons (512×512), `display:standalone`, offline shell, Lighthouse ≥95
- [ ] Capacitor: `npm i @capacitor/core @capacitor/cli`, `npx cap init`, `npx cap add android`, `npx cap sync`, test on emulator/device
- [ ] Touch polish: prevent scroll/zoom on board, haptics, larger tap targets, safe-area insets
- [ ] Store assets: screenshots, feature graphic, Play Console listing, versioning `android/app/build.gradle`
- [ ] Trusted Web Activity alternative documented if PWA-only publish preferred

**Done when:** `npm run build && npx cap open android` yields installable APK/AAB that plays offline shell (server still remote).

## M5 — Stretch

- [ ] KataGo WASM offline (e.g. `katago-wasm` build, feature-flagged, lazy-loaded network)
- [ ] Puzzle mode: generate puzzles from player's blunders (store worst 2% moves, drill them)
- [ ] Accounts & sync: Supabase/Firebase for game history
- [ ] 13×13 / 19×19 toggle
- [ ] Soundtrack, themes, colorblind markers

---

## Risks & Mitigations

| Risk | Mitigation |
|---|---|
| KataGo server cost/latency on mobile | 9×9 low visits (50–200), CPU fine; cache; edge region close to users; WASM fallback later |
| HumanSL rank conditioning undocumented | Spike M1 early, read `katago` `--help`, inspect open-source human model configs; fallback: single human model with `humanSLProfile` interpolation |
| Board UX on small screens | SVG scales; test on 360×640; markers large; pinch disabled only on board |
| Move candidates feel arbitrary | Log humanPolicy vs strong gaps; add telemetry toggle; iterate thresholds with 9×9 dan review |

## Definition of Ready for “v0 Playable”

- New game → pick rank → play 9×9 vs HumanSL → each player turn shows n choices → feedback → finish → SGF download → installable on Android.

## Open Questions

- Which HumanSL net exactly (`b18c384nbt` vs `b28c512nbt-humanv0` newest)? Pin by testing policy diversity vs size.
- Exact win-rate gap thresholds for “tempting bad” per rank — tune empirically.
- Whether to show any pre-pick hint (e.g. “2 of these lose ≥5%”) — leans no, to preserve choice.
