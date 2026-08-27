// Minimal MCTS stub (optional, deferred — mock uses heuristic only)
// Would use: select (PUCT with P_h) -> expand (legal) -> evaluate (W_s) -> backprop
// Real KataGo analysis engine does this via maxVisits MCTS already.

export type MCTSConfig = {
  maxVisits: number
  cPUCT: number
  temperatureEarly: number
  temperature: number
}

export function stubMCTS(){
  // No-op placeholder; real KataGo handles via -maxVisits
  return { visits: 0, bestMove: null }
}
