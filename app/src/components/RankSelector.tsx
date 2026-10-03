import type { Rank } from '../lib/katagoClient'
const RANKS: Rank[] = ['15k','12k','10k','8k','5k','3k','1k','1d','3d']
export function RankSelector({rank, onRank, disabled}:{rank:Rank,onRank:(r:Rank)=>void, disabled?:boolean}){
  return <div style={{display:'flex', gap:12, flexWrap:'wrap', alignItems:'center'}}>
    <label>Rank <select value={rank} onChange={e=>onRank(e.target.value as Rank)} disabled={disabled}>{RANKS.map(r=> <option key={r} value={r}>{r}</option>)}</select></label>
  </div>
}
