import { spawn, ChildProcess } from 'node:child_process'
import { existsSync } from 'node:fs'
import type { Rank, Strategy } from './types.js'
import { rankIndex } from './types.js'

export type KatagoMode = 'mock'|'real'
type Pending = { resolve:(v:any)=>void, reject:(e:any)=>void }

const KOMI = 7
const VISITS = 150

// Thresholds per rank for S1 (as in LEARNING_DESIGN)
function thresholds(rank:Rank){
  const i = rankIndex(rank)
  if(i<=1) return {good:0.02, bad:0.06} // 15k-12k
  if(i<=3) return {good:0.02, bad:0.045}
  if(i<=5) return {good:0.015, bad:0.035}
  return {good:0.01, bad:0.025}
}

// Mock generator: plausible 9x9 moves based on empty-ish heuristics
function allLegalMoves(signMap:number[][]): [number,number][]{
  const moves:[number,number][]=[]
  for(let y=0;y<9;y++) for(let x=0;x<9;x++) if(signMap[y][x]===0) moves.push([x,y])
  return moves
}
function mockPolicyScore(x:number,y:number, rank:Rank): number{
  // center slightly higher at dan, corners/edges at kyu - simple
  const cx = Math.abs(x-4), cy = Math.abs(y-4), dist = cx+cy
  const i = rankIndex(rank)
  // dan prefers center (lower dist), kyu more random
  const centerBonus = (8 - i) * 0.02 * (4 - dist/2)
  const cornerBonus = (i < 3 ? 0.03 : 0) * ((x<2||x>6)&&(y<2||y>6)?1:0)
  return 0.05 + Math.random()*0.05 + centerBonus + cornerBonus
}
function mockStrongWinrate(x:number,y:number, signMap:number[][], maxVisits=150): number{
  // heuristic + visit-scaled noise (no tree search in mock; real KataGo uses visits)
  const neighbors = [[1,0],[-1,0],[0,1],[0,-1]].filter(([dx,dy])=>{
    const nx=x+dx, ny=y+dy; return nx>=0&&nx<9&&ny>=0&&ny<9&&signMap[ny][nx]!==0
  }).length
  const noiseScale = Math.max(0.15, 150 / Math.max(30, maxVisits)) // more visits = less noise
  const base = 0.48 + neighbors*0.02 + (Math.random()-0.5)*0.04*noiseScale
  const star = (x===2||x===6)&&(y===2||y===6) ? 0.02 : 0
  // TODO: 1-ply search ahead would penalize moves that give opponent strong reply; mock omits this — real engine does MCTS
  return Math.max(0.2, Math.min(0.75, base+star))
}

export function mockCandidates(signMap:number[][], rank:Rank, n:number, strategy:Strategy, maxVisits=150){
  const legal = allLegalMoves(signMap)
  if(legal.length===0) return []
  const th = thresholds(rank)
  // generate scored list
  const scored = legal.map(([x,y])=>{
    const ph = mockPolicyScore(x,y,rank)
    const ws = mockStrongWinrate(x,y, signMap, maxVisits)
    return {x,y,ph,ws}
  }).sort((a,b)=> b.ph - a.ph)

  const bestWs = Math.max(...scored.map(s=>s.ws))

  let pool: typeof scored = []
  if(strategy==='human-only') pool = scored.slice(0, Math.min(n, scored.length))
  else if(strategy==='strong-only') pool = [...scored].sort((a,b)=> b.ws - a.ws).slice(0,n)
  else if(strategy==='blunder-check'){
    const good = scored.filter(s=> bestWs - s.ws <= th.good).slice(0,4)
    const bad = scored.filter(s=> bestWs - s.ws >= 0.08).slice(0,1)
    pool = [...good, ...bad]
    if(pool.length<n) pool = scored.slice(0,n)
  } else if(strategy==='tesuji'){
    const best = [...scored].sort((a,b)=> b.ws - a.ws)[0]
    const bad = scored.filter(s=> bestWs - s.ws >= 0.03 && s !== best).slice(0,4)
    pool = [best, ...bad]
  } else { // good-vs-tempting
    const good = scored.filter(s=> bestWs - s.ws <= th.good).slice(0,3)
    const bad = scored.filter(s=> s.ph > 0.06 && bestWs - s.ws >= th.bad).slice(0,2)
    pool = [...good, ...bad]
    // fill if not enough
    if(pool.length<n){
      const remaining = scored.filter(s=> !pool.includes(s)).slice(0, n-pool.length)
      pool = [...pool, ...remaining]
    }
    pool = pool.slice(0,n)
  }

  // attach gaps and shuffle
  const withScores = pool.map(p=>({
    x:p.x, y:p.y,
    humanPolicy: Math.round(p.ph*1000)/1000,
    strongWinrate: Math.round(p.ws*1000)/1000,
    strongScore: Math.round((p.ws-0.5)*14*10)/10, // fake scoreLead
    tag: bestWs - p.ws <0.02 ? 'good' : bestWs - p.ws >0.05 ? 'overconcentrated' : 'ok'
  }))
  // shuffle
  for(let i=withScores.length-1;i>0;i--){ const j=Math.floor(Math.random()*(i+1)); const t=withScores[i]; withScores[i]=withScores[j]; withScores[j]=t }
  const labels = 'ABCDE'
  return withScores.map((c,i)=>({...c, label: labels[i]}))
}

export function mockGenmove(signMap:number[][], rank:Rank, maxVisits=150){
  const cands = mockCandidates(signMap, rank, 8, 'human-only', maxVisits) as any[]
  // weighted sample by humanPolicy
  const total = cands.reduce((s,c)=>s+c.humanPolicy,0) || 1
  let r = Math.random()*total, pick=cands[0]
  for(const c of cands){ r-=c.humanPolicy; if(r<=0){ pick=c; break } }
  return { x:pick.x, y:pick.y, winrate: pick.strongWinrate, scoreLead: pick.strongScore }
}

export function mockEvaluate(signMap:number[][], move:{x:number,y:number}, maxVisits=150){
  const ws = mockStrongWinrate(move.x, move.y, signMap, maxVisits)
  const ownership = Array.from({length:9},(_,y)=> Array.from({length:9},(_,x)=> {
    // fake ownership: near stones -> biased
    const d = Math.hypot(x-move.x, y-move.y)
    return Math.max(0, Math.min(1, 0.5 + (0.5 - ws)*0.3 - d*0.05 + (Math.random()-0.5)*0.1))
  }))
  return { winrate: ws, scoreLead: (ws-0.5)*14, ownership }
}

// Real engine wrapper (minimal) — if binary exists, otherwise mock
export class KatagoEngine {
  mode: KatagoMode
  proc?: ChildProcess
  pending = new Map<string, Pending>()
  buf = ''

  constructor(){
    const bin = process.env.KATAGO_BINARY || 'katago'
    // check exists only if absolute path; otherwise try spawn and fallback
    this.mode = 'mock'
    if(process.env.KATAGO_MODE==='real' || existsSync(bin)){
      try { this.spawn(bin); this.mode='real' } catch { this.mode='mock' }
    }
  }
  spawn(bin:string){
    const model = process.env.KATAGO_MODEL || 'server/models/strong.bin.gz'
    const humanModel = process.env.KATAGO_HUMAN_MODEL || 'server/models/human.bin.gz'
    const args = ['analysis','-model',model,'-human-model',humanModel,'-config','server/config/analysis.cfg']
    this.proc = spawn(bin, args, {stdio:['pipe','pipe','pipe']})
    this.proc.stdout?.on('data',d=> this.onData(d.toString()))
    this.proc.stderr?.on('data',d=> console.error('[katago]', d.toString().slice(0,500)))
    this.proc.on('error',()=> { this.mode='mock'; console.warn('katago spawn failed, using mock') })
  }
  onData(chunk:string){
    this.buf+=chunk
    let idx
    while((idx=this.buf.indexOf('\n'))>=0){
      const line=this.buf.slice(0,idx).trim(); this.buf=this.buf.slice(idx+1)
      if(!line) continue
      try{ const obj=JSON.parse(line); const p=this.pending.get(obj.id); if(p){ this.pending.delete(obj.id); p.resolve(obj)} }catch{}
    }
  }
  async query(obj:any, timeoutMs=3000): Promise<any>{
    if(this.mode==='mock') throw new Error('mock mode')
    return new Promise((resolve,reject)=>{
      const id = obj.id || Math.random().toString(36).slice(2)
      obj.id=id
      this.pending.set(id,{resolve,reject})
      this.proc!.stdin!.write(JSON.stringify(obj)+'\n')
      setTimeout(()=>{ if(this.pending.has(id)){ this.pending.delete(id); reject(new Error('katago timeout')) } }, timeoutMs)
    })
  }
}
