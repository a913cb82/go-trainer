import Fastify from 'fastify'
import cors from '@fastify/cors'
import { z } from 'zod'
import { RANKS } from './types.js'
import { mockCandidates, mockGenmove, mockEvaluate, KatagoEngine } from './katago.js'

const app = Fastify({logger:true})
await app.register(cors, {origin:true})

const engine = new KatagoEngine()

const boardSchema = z.array(z.array(z.number().min(-1).max(1))).length(9) // 9x9 signMap
// Accept also flat string for convenience, but primary is 2D array

app.get('/health', async()=>({ok:true, mode: engine.mode, ranks: RANKS}))
app.get('/ranks', async()=>({ranks: RANKS}))

const candidatesBody = z.object({
  board: boardSchema,
  toMove: z.enum(['B','W']).default('B'),
  rank: z.enum(RANKS as any).default('10k'),
  n: z.number().min(1).max(5).default(5),
  strategy: z.enum(['good-vs-tempting','human-only','tesuji','blunder-check','strong-only'] as any).default('good-vs-tempting'),
  komi: z.number().default(7)
})
app.post('/candidates', async(req, reply)=>{
  const parsed = candidatesBody.safeParse(req.body)
  if(!parsed.success) return reply.code(400).send({error: parsed.error.flatten()})
  const {board, rank, n, strategy} = parsed.data
  // try real engine if available, else mock
  if(engine.mode==='real'){
    // TODO: wire real query; for now fallback to mock to keep simple
  }
  const moves = mockCandidates(board as any, rank as any, n, strategy as any)
  return {moves, meta:{humanModel:'mock', strongModel:'mock', visits:150, mode: engine.mode}}
})

const genmoveBody = z.object({
  board: boardSchema,
  toMove: z.enum(['B','W']).default('B'),
  rank: z.enum(RANKS as any).default('10k'),
  komi: z.number().default(7)
})
app.post('/genmove', async(req, reply)=>{
  const p = genmoveBody.safeParse(req.body)
  if(!p.success) return reply.code(400).send({error:p.error.flatten()})
  const {board, rank} = p.data
  const m = mockGenmove(board as any, rank as any)
  return {move:{x:m.x,y:m.y,pass:false}, winrate:m.winrate, scoreLead:m.scoreLead}
})

const evaluateBody = z.object({
  board: boardSchema,
  move: z.object({x:z.number().min(0).max(8), y:z.number().min(0).max(8)}),
  toMove: z.enum(['B','W']).default('B')
})
app.post('/evaluate', async(req, reply)=>{
  const p = evaluateBody.safeParse(req.body)
  if(!p.success) return reply.code(400).send({error:p.error.flatten()})
  const {board, move} = p.data
  const r = mockEvaluate(board as any, move)
  return r
})

const port = Number(process.env.PORT||3001)
app.listen({port, host:'0.0.0.0'}).then(()=> console.log(`server ${port} mode=${engine.mode}`))
