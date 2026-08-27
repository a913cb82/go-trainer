# Plan — 9×9 KataGo Teaching Game

> Executable checklist — run top to bottom. `README.md` is the pitch; this is the build order.

## Prerequisites

- Node 20+, npm/pnpm, Docker (for KataGo), Python not required. KataGo binary + 2 `.bin.gz` in `server/models/` (gitignored, `scripts/download-models.sh`).
- No secrets — CORS origin is PWA host, models are public releases.

## Bootstrap (copy-paste)

```bash
npm create vite@latest app -- --template react-ts
cd app && npm i @sabaki/shudan @sabaki/go-board @sabaki/sgf zustand @tanstack/react-query vite-plugin-pwa
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

- [x] **Frontend framework:** React + Vite (default) vs SvelteKit — either works
- [ ] **Backend language:** Node (TS) vs Python — pick one
- [x] **KataGo deployment:** Hosted CPU server first; WASM deferred to M5 (no npm wrapper, speak JSON directly)
- [x] **Board lib:** `@sabaki/shudan` 1.8.0 + `@sabaki/go-board` 1.4.3 — reuse, don't rewrite (`jgoboard`/`goban` are alts)
- [x] **SGF:** `@sabaki/sgf` 3.5.0
- [ ] **Capacitor vs TWA:** Capacitor for store listing; PWA alone for sideload

---

## M0 — Scaffold (1–2 days) — *no KataGo yet*

- [ ] Create `app/` (Vite+TS+React), wire `vite-plugin-pwa` manifest + Workbox, ESLint/Prettier, Vitest
- [ ] `lib/goban.ts`: wrap `@sabaki/go-board` → `new Board(9)`, `makeMove`, `isLegal`, `getLiberties`; `lib/sgf.ts` → `sgf.parse/stringify`
- [ ] `components/Board/`: `Shudan` with `signMap`/`paintMap`/`ghostStoneMap`, A–E markers, last-move glow, ko illegal dim, `touch-action:none`
- [ ] Game loop: `gameStore` (Zustand) holds `board/history/turn`, pass×2 ends, undo, new game, random opponent
- [ ] Rank & `n` selector (slider + 3/5 toggle), random candidates stub, SGF export downloads `.sgf`
- [ ] Tests: `goban.test.ts` (capture/ko/suicide), `sgf.test.ts` round-trip, Playwright e2e plays one game

**Done when:** `npm run dev` plays full 9×9 vs random, captures work, PWA installs, `npm test` passes.

## M1 — KataGo Server (2–4 days)

- [ ] `server/src/katago.ts`: spawn `katago analysis`, JSON-lines stdin/stdout, queue by id, parse `moveInfos[]` → `P_h`/`W_s`
- [ ] Load `b28c512nbt-humanv0` + strong `b28c512nbt`, verify `humanSLProfile`/`rank` flag (`--help`), freeze `komi 7` (or 7.5) + `boardXSize:9`
- [ ] `GET /ranks` returns mapping; `POST /genmove|/candidates|/evaluate` per `ARCHITECTURE.md` (validate with Zod)
- [ ] Candidate picker (LEARNING_DESIGN S1): query both nets @ ~150 visits, compute `G`, filter `P_h` top-8, pick 3 good (`G≤2%`) + 2 bad (`G≥4–6%`), shuffle
- [ ] Cache by `hash(board)+rank+strategy`, debounce, `scripts/download-models.sh` + `Dockerfile` + `docker-compose.yml` healthcheck
- [ ] Frontend: `katagoClient.ts` + TanStack Query, replace random stub, loading spinners, error toast on engine down

**Done when:** `curl POST /candidates` at 10k returns 5 shuffled with policy/winrate; UI shows A–E and opponent replies at rank.

## M2 — Choice UI & Feedback (2–3 days)

- [ ] Markers A–E shuffled, tap→commit (confirm/cancel), keys 1–5; strategy selector hits `strategy` param
- [ ] `FeedbackPanel`: ordered by `W_s`, colors by `G` (≤2% green, 2–4% yellow, >4% red), `P_h%` + `Δ` + tag (`atari`/`cut`/`empty triangle` from lib checks)
- [ ] Ownership heatmap toggle (from `evaluate`), win-rate sparkline + score bar (Recharts or canvas)
- [ ] Settings in `localStorage`, full SGF import (file drop) + export, game history list

**Done when:** every player turn is 5-way, pick reveals ordered feedback, graph updates, SGF round-trips.

## M3 — Learning Polish (1–2 weeks, iterative)

- [ ] Tune picker: rank thresholds per LEARNING_DESIGN, dedup adjacent/symmetric, shrink `n` if not enough moves
- [ ] Rank-graduated spread + after 3 blunders inject S3 confidence puzzle; hint toggle (choice vs free-play)
- [ ] Review mode: step SGF with same overlay, collect worst `G` for spaced-repetition
- [ ] Polish: sound/haptics, atari alert, pass/resign/scoring confirm, komi fixed

**Done when:** 10k vs 3d feel different in logs (`mean G(picked)` trending ↓).

## M4 — Android Portability (1–2 days)

- [ ] PWA audit: icons 512×512, `display:standalone`, Workbox shell, Lighthouse ≥95
- [ ] Capacitor: `cap init/add/sync`, test emulator/device; touch: no scroll/zoom on board, haptics, safe-area
- [ ] Store: screenshots, Play Console, version `android/app/build.gradle`; TWA alt documented

**Done when:** `npm run build && npx cap open android` → installable AAB, offline shell works.

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
| KataGo cost/latency | 150 visits on 9×9 CPU fine; cache; WASM fallback M5 |
| HumanSL rank flag unknown | Spike M1 `katago --help`; fallback single model + interpolation |
| Small-screen board | Shudan scales; test 360×640; `touch-action:none` only on board |
| Candidates feel arbitrary | Log `P_h` vs `W_s`; tune thresholds with dan review |

## Testing

- Unit: `goban` (capture/ko), `sgf` round-trip, candidate picker gaps (Vitest)
- E2E: Playwright plays full game via `/candidates` mock + real server, asserts feedback ordering
- Manual: play 20 games at 10k and 3d, log `G(picked)`

## Definition of Ready for v0

- New game → pick rank → 9×9 vs HumanSL → n-choice each turn → feedback + graph → scoring → SGF dl → PWA installable → AAB builds

## Open Questions

- Which HumanSL net (`b18c384nbt` vs `b28c512nbt-humanv0`)? Pin by policy diversity test
- Exact gap thresholds per rank — tune empirically on 100 9×9 positions
- Pre-pick hint? Leans no
