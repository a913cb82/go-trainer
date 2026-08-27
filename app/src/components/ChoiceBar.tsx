import type { Candidate } from '../lib/katagoClient'
export function ChoiceBar({candidates, onPick, disabled}:{candidates:Candidate[]|null, onPick:(c:Candidate)=>void, disabled?:boolean}){
  if(!candidates) return <div style={{minHeight:32, color:'#666'}}>Waiting for candidates… (playing vs rank or random)</div>
  return <div style={{display:'flex', gap:8, flexWrap:'wrap'}}>
    {candidates.map(c=> (
      <button key={c.label} onClick={()=>onPick(c)} disabled={disabled} style={{padding:'8px 12px', border:'1px solid #999', borderRadius:8, background:'#f5e6c8', cursor:'pointer'}}>
        <b>{c.label}</b> {String.fromCharCode(65+c.x)}{9-c.y}
      </button>
    ))}
  </div>
}
