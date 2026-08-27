import { create } from 'zustand'
import type { Sign } from '@sabaki/go-board'
import { emptyBoard, toSignMap } from '../lib/goban'
import type { Board } from '../lib/goban'
import type { Candidate, Rank, Strategy } from '../lib/katagoClient'

export type MoveRec = {x:number,y:number,color:1|-1, candidate?:Candidate, gap?:number}

type State = {
  board: Board
  history: MoveRec[]
  toMove: 1|-1
  rank: Rank
  n: number
  strategy: Strategy
  candidates: Candidate[]|null
  evaluations: (Candidate & {gap:number})[]|null
  winrateHistory: number[]
  status: 'playing'|'scoring'|'finished'
  passing: number
  setRank: (r:Rank)=>void
  setN: (n:number)=>void
  setStrategy: (s:Strategy)=>void
  newGame: ()=>void
  applyMove: (x:number,y:number)=>void
  pass: ()=>void
  setCandidates: (c:Candidate[]|null)=>void
  setEvaluations: (e:(Candidate & {gap:number})[]|null)=>void
  pushWinrate: (w:number)=>void
  undo: ()=>void
}

export const useGame = create<State>((set, get)=>({
  board: emptyBoard(),
  history: [],
  toMove: 1,
  rank: '10k',
  n: 5,
  strategy: 'good-vs-tempting',
  candidates: null,
  evaluations: null,
  winrateHistory: [],
  status: 'playing',
  passing: 0,
  setRank: rank=> set({rank}),
  setN: n=> set({n}),
  setStrategy: strategy=> set({strategy}),
  newGame: ()=> set({board: emptyBoard(), history:[], toMove:1, candidates:null, evaluations:null, winrateHistory:[], status:'playing', passing:0}),
  setCandidates: candidates=> set({candidates}),
  setEvaluations: evaluations=> set({evaluations}),
  pushWinrate: w=> set(s=>({winrateHistory:[...s.winrateHistory, w]})),
  applyMove: (x,y)=>{
    const s=get()
    if(s.status!=='playing') return
    const col=s.toMove as unknown as Sign
    const next = (s.board as any).makeMove(col, [x,y]) as Board
    if((next as any).get([x,y])!==col) return
    const rec:MoveRec={x,y,color:s.toMove}
    set({board:next, history:[...s.history, rec], toMove: s.toMove===-1?1:-1, passing:0, candidates:null, evaluations:null})
  },
  pass: ()=>{
    const s=get()
    const p=s.passing+1
    if(p>=2) set({status:'finished', passing:p})
    else set({passing:p, toMove: s.toMove===-1?1:-1, candidates:null, evaluations:null, history:[...s.history, {x:-1,y:-1,color:s.toMove}]})
  },
  undo: ()=>{
    const s=get()
    if(s.history.length===0) return
    let b=emptyBoard()
    const h=s.history.slice(0,-1)
    for(const m of h) if(m.x>=0) b=(b as any).makeMove(m.color as unknown as Sign, [m.x,m.y]) as Board
    set({board:b, history:h, toMove: s.toMove===-1?1:-1, candidates:null, evaluations:null, status:'playing', passing:0, winrateHistory: s.winrateHistory.slice(0,-1)})
  },
}))

export function boardSignMap(board:Board){ return toSignMap(board) as number[][] }
