# Architecture

```
Browser PWA (app/) --HTTPS--> Server (server/)
  Vite/React/SVG  POST /candidates   Node -> katago analysis
  Zustand         POST /genmove       ├─ HumanSL model
  Workbox cache   POST /evaluate     └─ Strong model
        |
        v (Capacitor WebView)
  Android wrapper  (same dist/)
```

Offline: local `@sabaki/go-board` handles legality; candidate/genmove queue until reconnect.

## Frontend `app/`

```
app/src/
  components/Board/  # GobanView.tsx custom SVG (faint candidates, points feedback)
  components/        # RankSelector, WinrateGraph
  lib/               # goban.ts (→ @sabaki/go-board), sgf.ts (→ @sabaki/sgf), katagoClient.ts
  store/gameStore.ts # Zustand
```

State: `board, history, turn, rank, n, strategy, candidates (shuffled), evaluations, winrateHistory, status`.

## Backend `server/`

```
server/src/
  index.ts, katago.ts, types.ts
models/.gitignore, Dockerfile
```

One long-lived `katago analysis` per model, multiplexed over stdin/stdout JSON lines, cached by board hash. No npm wrapper — direct JSON (skip `@sabaki/gtp`).

## API

**POST /candidates**
```json
// req: { board, toMove, rank, n, strategy, history, komi }
// resp: { moves: [{x,y,label,humanPolicy,strongWinrate,strongScore,scoreGap,tag}], meta: {humanModel, strongModel, visits} }
```
Moves ranked by `humanPolicy`; feedback gap `G = bestPoints - points` (rank-graduated).

**POST /genmove**
```json
// req: { board, toMove, rank, history, komi }
// resp: { move: {x,y,pass}, winrate, scoreLead }
```

**POST /evaluate**
```json
// req: { board, move, toMove, rank, history }
// resp: { winrate, scoreLead, ownership: number[9][9] }
```

## Turn Flow

1. `POST /candidates` → server picks candidates (see LEARNING_DESIGN, by `humanPolicy` + points thresholds).
2. Render faint circles, no values.
3. Player picks → local `go-board` legality + reveal points feedback (halo + badges).
4. `POST /genmove` → opponent reply (sampled by `humanPolicy`).

Scoring: two passes → `@sabaki/go-board` area score + strong `scoreLead` advisory.

## Config

- `komi: 7` (or 7.5 — freeze early, affects HumanSL).
- CORS: PWA origin only. Rate-limit by board hash. Models not in git.
