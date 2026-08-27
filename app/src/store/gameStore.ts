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
  evalsHistory: ((Candidate & {gap:number})[]|null)[]
  showFeedback: boolean
  feedbackScope: 'all'|'picked' // all = green/yellow/red for all 5, picked = only your stone
  winrateHistory: number[]
  status: 'playing'|'scoring'|'finished'
  passing: number
  setRank: (r:Rank)=>void
  setN: (n:number)=>void
  setStrategy: (s:Strategy)=>void
  setShowFeedback: (v:boolean)=>void
  setFeedbackScope: (v:'all'|'picked')=>void
  newGame: ()=>void
  applyMove: (x:number,y:number)=>void
  pass: ()=>void
  setCandidates: (c:Candidate[]|null)=>void
  setEvaluations: (e:(Candidate & {gap:number})[]|null)=>void
  clearEvaluations: ()=>void
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
  evalsHistory: [],
  showFeedback: true,
  feedbackScope: 'all',
  winrateHistory: [],
  status: 'playing',
  passing: 0,
  setRank: rank=> set({rank}),
  setN: n=> set({n}),
  setStrategy: strategy=> set({strategy}),
  setShowFeedback: showFeedback=> set({showFeedback}),
  setFeedbackScope: (feedbackScope:'all'|'picked')=> set({feedbackScope}),
  newGame: ()=> set({board: emptyBoard(), history:[], toMove:1, candidates:null, evaluations:null, evalsHistory:[], winrateHistory:[], status:'playing', passing:0}),
  setCandidates: candidates=> set({candidates}),
  setEvaluations: evaluations=> set(s=> ({evaluations, evalsHistory: [...s.evalsHistory, evaluations]})),
  clearEvaluations: ()=> set(s=> ({evaluations:null, evalsHistory: s.evalsHistory.slice(0,-1)})),
  pushWinrate: w=> set(s=>({winrateHistory:[...s.winrateHistory, w]})),
  applyMove: (x,y)=>{
    const s=get()
    if(s.status!=='playing') return
    const col=s.toMove as unknown as Sign
    const next = (s.board as any).makeMove(col, [x,y]) as Board
    if((next as any).get([x,y])!==col) return
    const rec:MoveRec={x,y,color:s.toMove}
    // keep evaluations (so feedback stays after enemy move); only clear candidates
    set({board:next, history:[...s.history, rec], toMove: s.toMove===-1?1:-1, passing:0, candidates:null})
  },
  pass: ()=>{
    const s=get()
    const p=s.passing+1
    if(p>=2) set({status:'finished', passing:p})
    else set({passing:p, toMove: s.toMove===-1?1:-1, candidates:null, history:[...s.history, {x:-1,y:-1,color:s.toMove}]})
  },
  undo: ()=>{
    const s=get()
    if(s.history.length===0) return
    const lastBlackIdx = [...s.history].map((h,i)=> ({h,i})).reverse().find(({h})=> h.color===1 && h.x>=0)?.i
    if(lastBlackIdx===undefined) return
    const undone = s.history.length - lastBlackIdx
    const h = s.history.slice(0, lastBlackIdx)
    let b=emptyBoard()
    for(const m of h) if(m.x>=0) b=(b as any).makeMove(m.color as unknown as Sign, [m.x,m.y]) as Board
    // restore previous feedback (step back through evalsHistory)
    const newEvalsHistory = s.evalsHistory.slice(0, -1)
    const prevEvals = newEvalsHistory.length ? newEvalsHistory[newEvalsHistory.length-1] : null
    set({board:b, history:h, toMove: 1, candidates:null, evaluations: prevEvals, evalsHistory: newEvalsHistory, status:'playing', passing:0, winrateHistory: s.winrateHistory.slice(0, -undone)})
  },
}))

export function boardSignMap(board:Board){ return toSignMap(board) as number[][] }
