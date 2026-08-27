# Architecture

```
Browser PWA (app/) --HTTPS--> Server (server/)
  Vite/React/SVG  POST /candidates   Node/Python -> katago analysis
  Zustand/Query   POST /genmove       ├─ HumanSL model
  Workbox cache   POST /evaluate     └─ Strong model
        |
        v (Capacitor WebView)
  Android wrapper  (same dist/)
```

Offline: local `goban.ts` handles legality; candidate/genmove queue until reconnect.

## Frontend `app/`

```
app/src/
  components/Board/  # BoardSvg, Stone, Marker, OwnershipOverlay
  components/        # ChoiceBar, FeedbackPanel, RankSelector, WinrateGraph
  lib/               # goban.ts, sgf.ts, katagoClient.ts
  store/gameStore.ts # Zustand
```

State: `board, history, turn, rank, n, strategy, candidates (shuffled), evaluations, winrateHistory, status`.

## Backend `server/`

```
server/src/
  index.ts, katago.ts, routes/{candidates,genmove,evaluate}.ts
  types.ts
models/.gitignore, Dockerfile, docker-compose.yml
```

One long-lived `katago analysis` process per model, multiplexed over stdin/stdout JSON lines, cached by board hash.

## API

**POST /candidates**
```json
// req: { board, toMove, rank, n, strategy, komi }
// resp: { moves: [{x,y,label,humanPolicy,strongWinrate,strongScore}], meta: {humanModel, strongModel, visits} }
```
Moves shuffled; client hides `strongWinrate` until after pick.

**POST /genmove**
```json
// req: { board, toMove, rank, komi }
// resp: { move: {x,y,pass}, winrate, scoreLead }
```

**POST /evaluate**
```json
// req: { board, move, toMove }
// resp: { winrate, scoreLead, ownership: number[9][9] }
```

## Turn Flow

1. `POST /candidates` → server picks candidates (see LEARNING_DESIGN).
2. Render A–E, no win-rates.
3. Player picks → local `goban.play()` + `POST /evaluate` for all 5 → reveal ordering.
4. `POST /genmove` → opponent reply.

Scoring: two passes → `goban.score()` + strong `scoreLead` advisory.

## Config

- `komi: 7` (or 7.5 — freeze early, affects HumanSL).
- CORS: PWA origin only. Rate-limit by board hash. Models not in git.
