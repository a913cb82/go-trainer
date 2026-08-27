import type { Candidate, Rank } from '../lib/katagoClient'
import { gapColor } from '../lib/rank'
export function FeedbackPanel({evals, rank}:{evals:(Candidate & {gap:number})[]|null, rank: Rank}){
  if(!evals) return null
  const sorted=[...evals].sort((a,b)=> a.gap - b.gap)
  return <div style={{border:'1px solid #ccc', borderRadius:8, padding:12, background:'#fff8e7'}}>
    <b>Feedback (ordered by predicted points)</b>
    <ol style={{margin:'8px 0'}}>
      {sorted.map(e=>{
        const col = gapColor(e.gap, rank).hex
        const pts = e.strongScore ?? 0
        return <li key={e.label} style={{color:col}}><b>{e.label}</b> {String.fromCharCode(65+e.x)}{9-e.y} — human {Math.round(e.humanPolicy*100)}% · pts {pts>=0?'+':''}{(pts).toFixed(1)} · Δ {(e.gap).toFixed(1)} pts {e.tag?`· ${e.tag}`:''}</li>
      })}
    </ol>
  </div>
}
