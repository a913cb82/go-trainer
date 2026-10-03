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
app.get('/health', async()=>({ok:true, mode: engine.mode, ranks: RANKS}))
app.get('/ranks', async()=>({ranks: RANKS}))

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

const scoreBody = z.object({
  board: boardSchema,
  history: historySchema,
  maxVisits: z.number().min(1).max(2000).default(500)
})
app.post('/score', async(req, reply)=>{
  const p = scoreBody.safeParse(req.body)
  if(!p.success) return reply.code(400).send({error:p.error.flatten()})
  const {board, maxVisits, history} = p.data as any
  try{
    const sign = board as number[][]
    const pos = positionArgs(sign, history, 'B')
    const query = {
      id: `s-${Date.now()}`,
      ...pos,
      rules: 'japanese',
      komi: 7,
      boardXSize: 9,
      boardYSize: 9,
      analyzeTurns: [pos.moves.length],
      maxVisits: maxVisits || 500,
      includeOwnership: true,
      includePolicy: false,
      // Pure strong net (no HumanSL profile): measurement, not human-like play.
      // BLACK perspective forced so positive = Black leads, komi included.
      overrideSettings: { reportAnalysisWinratesAs: 'BLACK', ignorePreRootHistory: false }
    }
    const res = await engine.query(query, 30000) as any
    const own = res?.ownership || res?.result?.ownership || []
    const ownership = Array.isArray(own) && own.length===81
      ? Array.from({length:9},(_,y)=> own.slice(y*9,(y+1)*9))
      : Array.from({length:9},()=>Array(9).fill(0))
    const root = res?.rootInfo || res?.result?.rootInfo || {}
    const scoreLead = Number(root.scoreLead ?? root.scoreMean ?? NaN)
    if(!Number.isFinite(scoreLead)) throw new Error('KataGo returned no scoreLead')
    return { scoreLead, ownership }
  } catch (e: any) {
    console.error('Real score error:', e.message)
    return reply.code(500).send({error: e.message})
  }
})

const port = Number(process.env.PORT||3001)
app.listen({port, host:'0.0.0.0'}).then(()=> console.log(`server ${port} mode=${engine.mode}`))
