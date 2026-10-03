const BASE = '/api'

export type Rank = '15k'|'12k'|'10k'|'8k'|'5k'|'3k'|'1k'|'1d'|'3d'
export type HistoryMove = {x:number,y:number,color:1|-1}

async function post(path:string, body:any){
  const r = await fetch(`${BASE}${path}`, {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(body)})
  if(!r.ok) throw new Error(await r.text())
  return r.json()
}

export const katago = {
  genmove: (board:number[][], toMove:'B'|'W', rank:Rank, history:HistoryMove[]=[], maxVisits=400) =>
    post('/genmove', {board, toMove, rank, history, maxVisits}) as Promise<{move:{x:number,y:number,pass:boolean}, winrate:number, scoreLead:number}>,
  ranks: async()=> (await (await fetch(`${BASE}/ranks`)).json()).ranks as Rank[],
  health: async()=> await (await fetch(`${BASE}/health`)).json(),
}
