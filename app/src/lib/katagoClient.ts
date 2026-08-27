const BASE = '/api'

export type Rank = '15k'|'12k'|'10k'|'8k'|'5k'|'3k'|'1k'|'1d'|'3d'
export type Strategy = 'good-vs-tempting'|'human-only'|'tesuji'|'blunder-check'|'strong-only'
export type Candidate = {x:number,y:number,label:string,humanPolicy:number,strongWinrate:number,strongScore:number,tag?:string}

async function post(path:string, body:any){
  const r = await fetch(`${BASE}${path}`, {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(body)})
  if(!r.ok) throw new Error(await r.text())
  return r.json()
}

export const katago = {
  candidates: (board:number[][], toMove:'B'|'W', rank:Rank, n:number, strategy:Strategy, maxVisits=150) =>
    post('/candidates', {board, toMove, rank, n, strategy, maxVisits}) as Promise<{moves:Candidate[], meta:any}>,
  genmove: (board:number[][], toMove:'B'|'W', rank:Rank, maxVisits=150) =>
    post('/genmove', {board, toMove, rank, maxVisits}) as Promise<{move:{x:number,y:number,pass:boolean}, winrate:number, scoreLead:number}>,
  evaluate: (board:number[][], move:{x:number,y:number}, toMove:'B'|'W', maxVisits=150) =>
    post('/evaluate', {board, move, toMove, maxVisits}) as Promise<{winrate:number, scoreLead:number, ownership:number[][]}>,
  ranks: async()=> (await (await fetch(`${BASE}/ranks`)).json()).ranks as Rank[],
  health: async()=> await (await fetch(`${BASE}/health`)).json(),
}
