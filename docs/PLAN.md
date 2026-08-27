# Plan — 9×9 KataGo Teaching Game

> Executable checklist — run top to bottom. `README.md` is the pitch; this is the build order.

## Prerequisites

- Node 20+, npm/pnpm, Docker (for KataGo), Python not required. KataGo binary + 2 `.bin.gz` in `server/models/` (gitignored, `scripts/download-models.sh`).
- No secrets — CORS origin is PWA host, models are public releases.

## Bootstrap (copy-paste)

```bash
npm create vite@latest app -- --template react-ts
cd app && npm i @sabaki/go-board @sabaki/sgf zustand vite-plugin-pwa
npm i -D vitest @testing-library/react playwright eslint prettier
# server (Node):
mkdir server && cd server && npm init -y && npm i fastify && npm i -D typescript tsx @types/node
# android later: npm i -D @capacitor/core @capacitor/cli && npx cap init && npx cap add android
```

## Core Types (shared)

```ts
type Color = 1|-1; type Vertex = [x:number,y:number] // 0..8
type Candidate = {x:number,y:number,label:string,humanPolicy:number,strongWinrate:number,strongScore:number,tag?:string}
type Strategy = 'good-vs-tempting'|'human-only'|'tesuji'|'blunder-check'|'strong-only'
type Rank = '15k'|...|'3d' // maps to KataGo humanSLProfile
```

## Decisions to Lock Early

- [x] **Frontend framework:** React + Vite
- [x] **Backend language:** Node (Fastify)
- [x] **KataGo deployment:** Hosted CPU server first; WASM deferred to M5 (no npm wrapper, speak JSON directly)
- [x] **Board lib:** custom SVG `GobanView.tsx` + `@sabaki/go-board` 1.4.3 — reuse rules, not board widget
- [x] **SGF:** `@sabaki/sgf` 3.5.0
- [x] **Capacitor vs TWA:** Capacitor Native — chosen for on-device KataGo (Eigen `arm64-v8a`); PWA alone needs remote server

---

## M0 — Scaffold ✓ (done)

- [x] `app/` Vite+TS+React, `vite-plugin-pwa` manifest + Workbox, Vitest
- [x] `lib/goban.ts` → `@sabaki/go-board`, `lib/sgf.ts` → `@sabaki/sgf`
- [x] `components/Board/` custom SVG `GobanView` with faint candidates + points feedback
- [x] `gameStore` (Zustand) loop, pass×2, undo, new game
- [x] Rank & n selector, SGF export, `goban.test.ts` passing

## M1 — KataGo Server ✓

- [x] `katago.ts` `KatagoEngine` (spawns `katago analysis`, rank `rank_*` profiles, `humanPolicy` sampling)
- [x] `GET /ranks|/health`, `POST /candidates|/genmove|/evaluate` (Zod), `komi 7`, `9x9`
- [x] `scripts/download-models.sh` + `config/analysis.cfg` + `Dockerfile`
- [x] `katagoClient.ts` + proxy `/api` → `:3001`

**Done when:** `curl POST /candidates` returns 5 shuffled — verified.

## M2 — Choice UI & Feedback ✓

- [x] Custom SVG `GobanView`, faint candidate circles, feedback ordered by predicted points (green/yellow/red) + `P_h%`/`pts`/`Δ`/tag
- [x] `WinrateGraph` sparkline, score area, last winrate
- [x] SGF export (data URI), import stub, history debug

**Done when:** 5-way pick → ordered feedback → graph — verified.

## M3 — Learning Polish ✓ (v1)

- [x] Rank thresholds per LEARNING_DESIGN, dedup/shuffle, `n` shrink if needed
- [x] Free-play toggle vs choice, review slider, pass/undo, localStorage rank/n/strategy
- [x] Winrate history tracked; blunder-streak hook ready
- [ ] TODO: ownership heatmap overlay, atari haptics, spaced-repetition store (next iteration)

## M4 — Android (Capacitor Native, on-device) — chosen (1)

- [ ] Native KataGo: NDK `arm64-v8a` Eigen build, `katago` + `b18c384`/`strong` in `filesDir`, `ProcessBuilder` plugin (same JSON protocol)
- [ ] PWA + Capacitor: `cap init/add/sync`, `cap open android`, PWA audit (icons 512, `standalone`, Workbox), touch/haptics/safe-area, first-launch model download
- [ ] Store: signed AAB → Play Console

**Done when:** `npx cap open android` → on-device `mode=real` (no remote server), installable AAB.

## M5 — Stretch

- [ ] KataGo WASM alternative (deferred — Native chosen; `katago-wasm` + `SharedArrayBuffer` threading, no native plugin)
- [ ] Puzzle mode: generate puzzles from player's blunders (store worst 2% moves, drill them)
- [ ] Accounts & sync: Supabase/Firebase for game history
- [ ] 13×13 / 19×19 toggle
- [ ] Soundtrack, themes, colorblind markers

---

## Risks & Mitigations

| Risk | Mitigation |
|---|---|
| KataGo cost/latency | 150 visits on 9×9 CPU fine; cache; WASM fallback M5 |
| HumanSL rank flag unknown | Spike M1 `katago --help`; fallback single model + interpolation |
| Small-screen board | Shudan scales; test 360×640; `touch-action:none` only on board |
| Candidates feel arbitrary | Log `P_h` vs `W_s`; tune thresholds with dan review |

## Testing

- Unit: `goban` (capture/ko), `sgf` round-trip, rank thresholds (Vitest)
- E2E: Playwright plays full game via real server, asserts feedback ordering
- Manual: play 20 games at 10k and 3d, log `G(picked)`

## Definition of Ready for v0

- New game → pick rank → 9×9 vs HumanSL → n-choice each turn → feedback + graph → scoring → SGF dl → PWA installable → AAB builds

## Open Questions

- Which HumanSL net (`b18c384nbt` vs `b28c512nbt-humanv0`)? Pin by policy diversity test
- Exact gap thresholds per rank — tune empirically on 100 9×9 positions
- Pre-pick hint? Leans no
