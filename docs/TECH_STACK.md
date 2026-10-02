# Tech Stack

Two stacks: what ships, and what supports development. Do not mix them.

## Shipped: native Android

- **Kotlin + Compose (Material3)** — all UI, single activity. Kotlin is the
  programming language. Compose is Android's UI toolkit.
- **Coroutines + Flow** — engine work runs off the main thread. `StateFlow`
  holds the game state.
- **DataStore Preferences** — Android's settings storage. It holds settings,
  rated history, and the full game snapshot.
- **JUnit4** — unit tests for pure logic. The `game/` package has no Android
  imports.
- **Paparazzi** — screenshot tests with no emulator.

## Development only

- **Web client (`app/`)**: Vite + React + TypeScript, Zustand for state,
  `@sabaki/go-board` as the rules reference, `@sabaki/sgf` for game
  records, Vitest + Playwright for tests.
- **PC engine (`server/`)**: Node spawns `katago analysis` (JSON lines over
  stdin/stdout) as the reference implementation. Models stay out of git and
  arrive by script.

## Engine delivery

On-device KataGo (Eigen `arm64-v8a`) over GTP. Eigen is a math library that
runs the neural nets on the phone CPU. GTP is the text protocol that drives
KataGo. No CUDA runs on the device. No WASM ships. No remote server runs in
the shipped path.

## See also

- [ARCHITECTURE.md](ARCHITECTURE.md) — how these pieces fit together
- [KATAGO_INTEGRATION.md](KATAGO_INTEGRATION.md) — engine wiring and models
