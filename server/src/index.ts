import Fastify from 'fastify'
import cors from '@fastify/cors'
import { z } from 'zod'
import { RANKS } from './types.js'
import { mockCandidates, mockGenmove, mockEvaluate, KatagoEngine } from './katago.js'

const app = Fastify({logger:true})
await app.register(cors, {origin:true})

const engine = new KatagoEngine()

const boardSchema = z.array(z.array(z.number().min(-1).max(1))).length(9)

app.get('/health', async()=>({ok:true, mode: engine.mode, ranks: RANKS}))
app.get('/ranks', async()=>({ranks: RANKS}))

const candidatesBody = z.object({
  board: boardSchema,
  toMove: z.enum(['B','W']).default('B'),
  rank: z.enum(RANKS as any).default('10k'),
  n: z.number().min(1).max(5).default(5),
  strategy: z.enum(['good-vs-tempting','human-only','tesuji','blunder-check','strong-only'] as any).default('good-vs-tempting'),
  komi: z.number().default(7),
  maxVisits: z.number().min(1).max(2000).default(150)
})
app.post('/candidates', async(req, reply)=>{
  const parsed = candidatesBody.safeParse(req.body)
  if(!parsed.success) return reply.code(400).send({error: parsed.error.flatten()})
  const {board, rank, n, strategy, maxVisits} = parsed.data
  const profile = 'preaz_' + (String(rank).includes('k') ? String(rank).replace('k','') : (String(rank).includes('d') ? String(rank).replace('d','') : String(rank)))
  if(engine.mode==='real'){
    try{
      const initStones: [string,string][] = []
      const sign = board as number[][]
      const movesArr: [string,string][] = []
      for(let y=0; y<9; y++) for(let x=0; x<9; x++) if(sign[y][x]!==0) movesArr.push([(sign[y][x]===1?'B':'W'), String.fromCharCode(65+x)+(9-y)])
      const query = {
        id: `c-${Date.now()}`,
        moves: movesArr,
        initialStones: initStones,
        rules: 'japanese',
        komi: 7,
        boardXSize: 9,
        boardYSize: 9,
        analyzeTurns: [movesArr.length || 0],
        maxVisits,
        includePolicy: true,
        includeOwnership: false,
        overrideSettings: { humanSLProfile: profile }
      }
      const res = await engine.query(query, 15000) as any
      const infos = res?.moveInfos || res?.result?.moveInfos || []
      const best = infos.reduce((b:any, c:any)=> (c.winrate > (b.winrate||0) ? c : b), infos[0] || {winrate:0.5, policy:0.1, move:{}, scoreLead:0})
      const mapped = infos.slice(0, Math.min(n, infos.length)).map((info:any, idx:number)=>({
        x: info.move ? (String(info.move[1]||'C').charCodeAt(0)-65) : 3,
        y: info.move ? (9 - parseInt(String(info.move[1]||'C4').slice(1) || '4')) : 4,
        label: 'ABCDE'[idx % 5] || 'A',
        humanPolicy: info.policy || info.prior || 0.1,
        strongWinrate: info.winrate || 0.5,
        strongScore: info.scoreLead || 0,
        tag: info.winrate > 0.53 ? 'good' : info.winrate < 0.45 ? 'overconcentrated' : 'ok'
      }))
      return { moves: mapped, meta: { humanModel: 'b18c384nbt-humanv0', strongModel: 'strong', visits: maxVisits || 150, mode: 'real', profile } }
    } catch (e: any) {
      console.error('Real candidates error:', e.message)
      const moves = mockCandidates(board as any, rank as any, n, strategy as any, maxVisits)
      return { moves, meta: { mode: 'mock-fallback', error: e.message, visits: maxVisits || 150 } }
    }
  }
  const moves = mockCandidates(board as any, rank as any, n, strategy as any, maxVisits)
  return { moves, meta: { humanModel: 'mock', strongModel: 'mock', visits: maxVisits || 150, mode: engine.mode } }
})

const genmoveBody = z.object({
  board: boardSchema,
  toMove: z.enum(['B','W']).default('B'),
  rank: z.enum(RANKS as any).default('10k'),
  komi: z.number().default(7),
  maxVisits: z.number().min(1).max(2000).default(150)
})
app.post('/genmove', async(req, reply)=>{
  const p = genmoveBody.safeParse(req.body)
  if(!p.success) return reply.code(400).send({error:p.error.flatten()})
  const {board, toMove, rank, maxVisits} = p.data
  const profile = 'preaz_' + (String(rank).includes('k') ? String(rank).replace('k','') : (String(rank).includes('d') ? String(rank).replace('d','') : String(rank)))
  if(engine.mode==='real'){
    try{
      const initStones: [string,string][] = []
      const sign = board as number[][]
      const movesArr: [string,string][] = []
      for(let y=0; y<9; y++) for(let x=0; x<9; x++) if(sign[y][x]!==0) movesArr.push([(sign[y][x]===1?'B':'W'), String.fromCharCode(65+x)+(9-y)])
      const res = await engine.query({ id: `g-${Date.now()}`, moves: movesArr, initialStones: initStones, rules: 'japanese', komi: 7, boardXSize: 9, boardYSize: 9, analyzeTurns: [movesArr.length || 0], maxVisits: maxVisits || 150, includePolicy: true, includeOwnership: false, overrideSettings: { humanSLProfile: profile } }, 15000) as any
      const best = res?.moveInfos?.[0] || res?.result?.moveInfos?.[0] || { winrate: 0.5, policy: 0.1, move: { x: 4, y: 4 }, scoreLead: 0 }
      const pickCoord = best?.move ? best.move[1] || 'C4' : 'C4'
      const cx = (pickCoord.charCodeAt(0) - 65) || 2
      const cyStr = (pickCoord.slice(1) || '4')
      const cy = 9 - parseInt(cyStr)
      return { move: { x: cx>=0 ? cx : 2, y: cy>=0 ? cy : 4, pass: false }, winrate: best?.winrate || 0.5, scoreLead: best?.scoreLead || 0 }
    } catch (e: any) {
      console.error('Real genmove error:', e.message)
      const m = mockGenmove(board as any, rank as any, maxVisits)
      return { move: { x: m.x, y: m.y, pass: false }, winrate: m.winrate, scoreLead: m.scoreLead }
    }
  }
  const m = mockGenmove(board as any, rank as any, maxVisits)
  return { move: { x: m.x, y: m.y, pass: false }, winrate: m.winrate, scoreLead: m.scoreLead }
})

const evaluateBody = z.object({
  board: boardSchema,
  move: z.object({x:z.number().min(0).max(8), y:z.number().min(0).max(8)}),
  toMove: z.enum(['B','W']).default('B'),
  maxVisits: z.number().min(1).max(2000).default(150)
})
app.post('/evaluate', async(req, reply)=>{
  const p = evaluateBody.safeParse(req.body)
  if(!p.success) return reply.code(400).send({error:p.error.flatten()})
  const {board, move, toMove, maxVisits} = p.data
  const profile = 'preaz_' + (String(p.data.rank || '10k').includes('k') ? String(p.data.rank || '10k').replace('k','') : (String(p.data.rank || '10k').includes('d') ? String(p.data.rank || '10k').replace('d','') : String(p.data.rank || '10k')))
  if(engine.mode==='real'){
    try{
      const query = {
        id: `e-${Date.now()}`,
        board: board as number[][],
        rules: 'japanese',
        komi: 7,
        boardXSize: 9,
        boardYSize: 9,
        maxVisits: maxVisits || 150,
        includeOwnership: true,
        includePolicy: true,
        overrideSettings: { humanSLProfile: profile }
      }
      const res = await engine.query(query, 15000) as any
      const ownership = res?.ownership || res?.result?.ownership || Array.from({length:9},()=>Array(9).fill(0.5))
      const best = (res?.moveInfos || res?.result?.moveInfos || []).reduce((b:any,c:any)=> (c.winrate > (b.winrate||0) ? c : b), {winrate:0.5})
      return { winrate: best.winrate || 0.5, scoreLead: (best.winrate - 0.5)*14, ownership }
    } catch (e: any) {
      console.error('Real evaluate error:', e.message)
      const r = mockEvaluate(board as any, move, maxVisits)
      return r
    }
  }
  const r = mockEvaluate(board as any, move, maxVisits)
  return r
})

const port = Number(process.env.PORT||3001)
app.listen({port, host:'0.0.0.0'}).then(()=> console.log(`server ${port} mode=${engine.mode}`))
