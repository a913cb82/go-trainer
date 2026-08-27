import Fastify from 'fastify'
import cors from '@fastify/cors'
import { z } from 'zod'
import { RANKS } from './types.js'
import { KatagoEngine } from './katago.js'

const app = Fastify({logger:true})
await app.register(cors, {origin:true})

const engine = new KatagoEngine()

const boardSchema = z.array(z.array(z.number().min(-1).max(1))).length(9)
const historySchema = z.array(z.object({
  x: z.number().int().min(-1).max(8),
  y: z.number().int().min(-1).max(8),
  color: z.union([z.literal(1), z.literal(-1)])
})).default([])

// KataGo skips the letter 'I' for the 9th column — use 'J' for x=8.
const colChar = (x:number)=> String.fromCharCode(65 + (x>=8 ? x+1 : x))
const toX = (c:string)=>{ const v=c.charCodeAt(0)-65; return v>=8 ? v-1 : v }

function positionArgs(board:number[][], history:{x:number,y:number,color:1|-1}[], toMove:'B'|'W'){
  if(history.length){
    const moves: [string,string][] = history.map(h=>[
      h.color===1 ? 'B' : 'W',
      h.x<0 ? 'pass' : colChar(h.x)+(9-h.y)
    ])
    return {moves, initialStones: [] as [string,string][], initialPlayer: undefined}
  }
  const initialStones: [string,string][] = []
  for(let y=0; y<9; y++) for(let x=0; x<9; x++) if(board[y][x]!==0)
    initialStones.push([board[y][x]===1?'B':'W', colChar(x)+(9-y)])
  return {moves: [] as [string,string][], initialStones, initialPlayer: toMove}
}

function moveCoord(info:any){
  const coord = String(info?.move || '')
  if(coord==='pass') return {x:4,y:4,pass:true}
  if(!/^[A-HJ][1-9]$/.test(coord)) return null
  return {x:toX(coord), y:9-parseInt(coord.slice(1),10), pass:false}
}
function humanPrior(info:any){ return Number(info?.humanPrior ?? info?.prior ?? info?.policy ?? 0) }
function validInfos(infos:any[]){ return infos.filter(info=>moveCoord(info)!==null) }
function pointsThresholds(rank: string){
  // Mirrors app/src/lib/rank.ts thresholds (points, rank-graduated)
  const order = ['15k','12k','10k','8k','5k','3k','1k','1d','3d'] as const
  const i = (order as readonly string[]).indexOf(rank)
  if(i<=1) return {good:1.5, bad:4}
  if(i<=3) return {good:1.5, bad:3.2}
  if(i<=5) return {good:1, bad:2.5}
  return {good:0.8, bad:1.8}
}

app.get('/health', async()=>({ok:true, mode: engine.mode, ranks: RANKS}))
app.get('/ranks', async()=>({ranks: RANKS}))

const candidatesBody = z.object({
  board: boardSchema,
  toMove: z.enum(['B','W']).default('B'),
  rank: z.enum(RANKS as any).default('10k'),
  n: z.number().min(1).max(5).default(5),
  strategy: z.enum(['good-vs-tempting','human-only','tesuji','blunder-check','strong-only'] as any).default('good-vs-tempting'),
  history: historySchema,
  komi: z.number().default(7),
  maxVisits: z.number().min(1).max(2000).default(15)
})
app.post('/candidates', async(req, reply)=>{
  const parsed = candidatesBody.safeParse(req.body)
  if(!parsed.success) return reply.code(400).send({error: parsed.error.flatten()})
  const {board, rank, n, strategy, maxVisits, history} = parsed.data
  const profile = 'rank_' + String(rank)
  try{
    const sign = board as number[][]
    const pos = positionArgs(sign, history, 'B')
    const query = {
      id: `c-${Date.now()}`,
      ...pos,
      rules: 'japanese',
      komi: 7,
      boardXSize: 9,
      boardYSize: 9,
      analyzeTurns: [pos.moves.length],
      maxVisits,
      includePolicy: true,
      includeOwnership: false,
      overrideSettings: { humanSLProfile: profile, ignorePreRootHistory: false }
    }
    const res = await engine.query(query, 15000) as any
    const infos = validInfos(res?.moveInfos || res?.result?.moveInfos || [])
    const humanInfos = [...infos].sort((a,b)=> humanPrior(b)-humanPrior(a))
    const scoreInfos = [...infos].sort((a,b)=> Number(b.scoreLead ?? b.scoreMean ?? 0) - Number(a.scoreLead ?? a.scoreMean ?? 0))
    const bestScore = scoreInfos[0] ? Number(scoreInfos[0].scoreLead ?? scoreInfos[0].scoreMean ?? 0) : 0
    const th = pointsThresholds(String(rank))
    let pool: any[] = []
    if(strategy==='human-only') pool = humanInfos.slice(0, n)
    else if(strategy==='strong-only') pool = scoreInfos.slice(0, n)
    else if(strategy==='tesuji'){
      const best = scoreInfos[0]
      const bad = humanInfos.filter(s=> s!==best && (bestScore - Number(s.scoreLead ?? s.scoreMean ?? 0)) >= 2).slice(0, 4)
      pool = best ? [best, ...bad].slice(0, n) : humanInfos.slice(0, n)
    } else if(strategy==='blunder-check'){
      const good = humanInfos.filter(s=> (bestScore - Number(s.scoreLead ?? s.scoreMean ?? 0)) <= th.good).slice(0, 4)
      const bad = humanInfos.filter(s=> (bestScore - Number(s.scoreLead ?? s.scoreMean ?? 0)) >= 5).slice(0, 1)
      pool = [...good, ...bad].slice(0, n)
      if(pool.length < n) pool = humanInfos.slice(0, n)
    } else { // good-vs-tempting
      const good = humanInfos.filter(s=> (bestScore - Number(s.scoreLead ?? s.scoreMean ?? 0)) <= th.good).slice(0, 3)
      const bad = humanInfos.filter(s=> (bestScore - Number(s.scoreLead ?? s.scoreMean ?? 0)) >= th.bad).slice(0, 2)
      pool = [...good, ...bad]
      if(pool.length < n){
        const remaining = humanInfos.filter(s=> !pool.includes(s)).slice(0, n - pool.length)
        pool = [...pool, ...remaining]
      }
      pool = pool.slice(0, n)
    }
    const seen = new Set<string>()
    const mapped: any[] = []
    for(const info of pool){
      const point = moveCoord(info)
      if(!point || point.pass) continue
      const {x,y} = point
      const key = `${x},${y}`
      if(seen.has(key)) continue
      seen.add(key)
      const score = Number(info.scoreLead ?? info.scoreMean ?? 0)
      const gap = bestScore - score
      mapped.push({
        x, y,
        label: 'ABCDE'[mapped.length % 5] || 'A',
        humanPolicy: humanPrior(info),
        strongWinrate: info.winrate ?? 0.5,
        strongScore: score,
        scoreGap: Math.max(0, Math.round(gap*10)/10),
        tag: gap <= th.good ? 'good' : gap >= th.bad ? 'overconcentrated' : 'ok'
      })
    }
    return { moves: mapped, meta: { humanModel: 'b18c384nbt-humanv0', strongModel: 'strong', visits: maxVisits || 150, mode: 'real', profile } }
  } catch (e: any) {
    console.error('Real candidates error:', e.message)
    return reply.code(500).send({error: e.message})
  }
})

const genmoveBody = z.object({
  board: boardSchema,
  toMove: z.enum(['B','W']).default('B'),
  rank: z.enum(RANKS as any).default('10k'),
  history: historySchema,
  komi: z.number().default(7),
  maxVisits: z.number().min(1).max(2000).default(15)
})
app.post('/genmove', async(req, reply)=>{
  const p = genmoveBody.safeParse(req.body)
  if(!p.success) return reply.code(400).send({error:p.error.flatten()})
  const {board, toMove, rank, maxVisits, history} = p.data
  const profile = 'rank_' + String(rank)
  try{
    const sign = board as number[][]
    const pos = positionArgs(sign, history, toMove)
    const res = await engine.query({ id: `g-${Date.now()}`, ...pos, rules: 'japanese', komi: 7, boardXSize: 9, boardYSize: 9, analyzeTurns: [pos.moves.length], maxVisits: maxVisits || 150, includePolicy: true, includeOwnership: false, overrideSettings: { humanSLProfile: profile, ignorePreRootHistory: false } }, 15000) as any
    const infos = validInfos(res?.moveInfos || res?.result?.moveInfos || [])
    const top = (res?.moveInfos || res?.result?.moveInfos || []).find((info:any)=> info.order===0) || res?.moveInfos?.[0] || res?.result?.moveInfos?.[0]
    const pool = infos.length ? infos : (top ? [top] : [])
    const total = pool.reduce((sum:number, info:any)=> sum + humanPrior(info), 0)
    let pick = pool[0]
    if(total>0){
      let r = Math.random()*total
      for(const info of pool){
        r -= humanPrior(info)
        if(r<=0){ pick=info; break }
      }
    }
    if(top && String(top.move)==='pass') return { move: { x: 4, y: 4, pass: true }, winrate: top.winrate || 0.5, scoreLead: top.scoreLead || 0 }
    if(!pick) throw new Error('KataGo returned no legal move')
    const point = moveCoord(pick)
    if(!point || point.pass) throw new Error('KataGo returned no playable human move')
    return { move: point, winrate: pick.winrate || 0.5, scoreLead: pick.scoreLead || 0 }
  } catch (e: any) {
    console.error('Real genmove error:', e.message)
    return reply.code(500).send({error: e.message})
  }
})

const evaluateBody = z.object({
  board: boardSchema,
  move: z.object({x:z.number().min(0).max(8), y:z.number().min(0).max(8)}),
  toMove: z.enum(['B','W']).default('B'),
  rank: z.enum(RANKS as any).default('10k'),
  history: historySchema,
  maxVisits: z.number().min(1).max(2000).default(15)
})
app.post('/evaluate', async(req, reply)=>{
  const p = evaluateBody.safeParse(req.body)
  if(!p.success) return reply.code(400).send({error:p.error.flatten()})
  const {board, move, toMove, maxVisits, rank, history} = p.data as any
  const profile = 'rank_' + String(rank || '10k')
  try{
    const sign = board as number[][]
    const pos = positionArgs(sign, history, toMove)
    const evalCoord = colChar(move.x)+(9-move.y)
    const query = {
      id: `e-${Date.now()}`,
      moves: [...pos.moves, [toMove, evalCoord] as [string,string]],
      initialStones: pos.initialStones,
      rules: 'japanese',
      komi: 7,
      boardXSize: 9,
      boardYSize: 9,
      analyzeTurns: [pos.moves.length],
      maxVisits: maxVisits || 50,
      includeOwnership: true,
      includePolicy: true,
      overrideSettings: { humanSLProfile: profile, ignorePreRootHistory: false }
    }
    const res = await engine.query(query, 15000) as any
    const own = res?.ownership || res?.result?.ownership || []
    const ownership = Array.isArray(own) && own.length===81
      ? Array.from({length:9},(_,y)=> own.slice(y*9,(y+1)*9))
      : Array.from({length:9},()=>Array(9).fill(0))
    const infos = res?.moveInfos || res?.result?.moveInfos || []
    const root = res?.rootInfo || res?.result?.rootInfo || {}
    const byMove = (res?.roots?.[0]?.childInfos || []).find((c:any)=> c.move===evalCoord) || infos.find((c:any)=> c.move===evalCoord)
    const winrate = byMove?.winrate ?? root.winrate ?? 0.5
    const scoreLead = byMove?.scoreLead ?? root.scoreLead ?? 0
    return { winrate: Number(winrate) || 0.5, scoreLead: Number(scoreLead) || 0, ownership }
  } catch (e: any) {
    console.error('Real evaluate error:', e.message)
    return reply.code(500).send({error: e.message})
  }
})

const port = Number(process.env.PORT||3001)
app.listen({port, host:'0.0.0.0'}).then(()=> console.log(`server ${port} mode=${engine.mode}`))
