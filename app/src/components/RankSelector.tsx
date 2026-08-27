import type { Rank, Strategy } from '../lib/katagoClient'
const RANKS: Rank[] = ['15k','12k','10k','8k','5k','3k','1k','1d','3d']
export function RankSelector({rank,n,strategy, onRank,onN,onStrategy, disabled}:{rank:Rank,n:number,strategy:Strategy,onRank:(r:Rank)=>void,onN:(n:number)=>void,onStrategy:(s:Strategy)=>void, disabled?:boolean}){
  return <div style={{display:'flex', gap:12, flexWrap:'wrap', alignItems:'center'}}>
    <label>Rank <select value={rank} onChange={e=>onRank(e.target.value as Rank)} disabled={disabled}>{RANKS.map(r=> <option key={r} value={r}>{r}</option>)}</select></label>
    <label>n <select value={n} onChange={e=>onN(Number(e.target.value))} disabled={disabled}><option value={3}>3</option><option value={5}>5</option></select></label>
    <label>Mode <select value={strategy} onChange={e=>onStrategy(e.target.value as Strategy)} disabled={disabled}>
      <option value="good-vs-tempting">Good vs tempting</option>
      <option value="human-only">Human only</option>
      <option value="tesuji">Tesuji</option>
      <option value="blunder-check">Blunder check</option>
      <option value="strong-only">Strong only</option>
    </select></label>
  </div>
}
