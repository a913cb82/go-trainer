import { create } from 'zustand'
import type { Sign } from '@sabaki/go-board'
import { emptyBoard, toSignMap } from '../lib/goban'
import type { Board } from '../lib/goban'
import type { Rank } from '../lib/katagoClient'

export type MoveRec = {x:number,y:number,color:1|-1}

type State = {
  board: Board
  history: MoveRec[]
  toMove: 1|-1
  rank: Rank
  winrateHistory: number[]
  status: 'playing'|'scoring'|'finished'
  passing: number
  setRank: (r:Rank)=>void
  newGame: ()=>void
  applyMove: (x:number,y:number)=>void
  pass: ()=>void
  pushWinrate: (w:number)=>void
  undo: ()=>void
}

export const useGame = create<State>((set, get)=>({
  board: emptyBoard(),
  history: [],
  toMove: 1,
  rank: '10k',
  winrateHistory: [],
  status: 'playing',
  passing: 0,
  setRank: rank=> set({rank}),
  newGame: ()=> set({board: emptyBoard(), history:[], toMove:1, winrateHistory:[], status:'playing', passing:0}),
  pushWinrate: w=> set(s=>({winrateHistory:[...s.winrateHistory, w]})),
  applyMove: (x,y)=>{
    const s=get()
    if(s.status!=='playing') return
    const col=s.toMove as unknown as Sign
    const next = (s.board as any).makeMove(col, [x,y]) as Board
    if((next as any).get([x,y])!==col) return
    const rec:MoveRec={x,y,color:s.toMove}
    set({board:next, history:[...s.history, rec], toMove: s.toMove===-1?1:-1, passing:0})
  },
  pass: ()=>{
    const s=get()
    const p=s.passing+1
    if(p>=2) set({status:'finished', passing:p})
    else set({passing:p, toMove: s.toMove===-1?1:-1, history:[...s.history, {x:-1,y:-1,color:s.toMove}]})
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
    set({board:b, history:h, toMove: 1, status:'playing', passing:0, winrateHistory: s.winrateHistory.slice(0, -undone)})
  },
}))

export function boardSignMap(board:Board){ return toSignMap(board) as number[][] }
