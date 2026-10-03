import { useState } from 'react'
import { GobanView } from './components/Board/GobanView'
import { RankSelector } from './components/RankSelector'
import { WinrateGraph } from './components/WinrateGraph'
import { useGame, boardSignMap } from './store/gameStore'
import { katago } from './lib/katagoClient'
import { boardToSgf } from './lib/sgf'

export default function App(){
  const s = useGame()
  const [reviewIdx, setReviewIdx] = useState<number|null>(null)
  const [err, setErr] = useState<string|null>(null)

  const signMap = boardSignMap(s.board)

  async function botReply(){
    try{
      const curMap = boardSignMap(useGame.getState().board)
      const res = await katago.genmove(curMap, 'W', s.rank, useGame.getState().history)
      if(res.move.pass) useGame.getState().pass()
      else useGame.getState().applyMove(res.move.x, res.move.y)
      useGame.getState().pushWinrate(res.winrate)
    }catch(e){ setErr(String(e)) }
  }

  // board click: play anywhere (free play only), then the bot replies
  function onVertexClick(x:number,y:number){
    if(reviewIdx!==null) return
    if(s.status!=='playing' || s.toMove!==1) return
    s.applyMove(x,y)
    s.pushWinrate(0.5)
    setTimeout(botReply, 400)
  }

  const last = s.history.length ? s.history[s.history.length-1] : undefined
  const sgf = boardToSgf(signMap, s.history.map(h=>({x:h.x,y:h.y,color:h.color})), 7)

  return <div style={{fontFamily:'system-ui', maxWidth:920, margin:'0 auto', padding:16}}>
    <h1 style={{margin:'4px 0'}}>Go 9×9 — KataGo HumanSL</h1>
    <p style={{color:'#555', marginTop:0}}>Play Black vs {s.rank} bot (White). Click anywhere.</p>

    <RankSelector rank={s.rank} onRank={r=>{s.setRank(r)}} disabled={false} />
    <div style={{display:'flex', gap:8, margin:'12px 0', flexWrap:'wrap'}}>
      <button onClick={()=>{s.newGame(); setReviewIdx(null)}}>New game</button>
      <button onClick={()=>s.undo()} disabled={s.history.length===0}>Undo</button>
      <button onClick={()=>s.pass()} disabled={s.status!=='playing'}>Pass</button>
      <label>Review <input type="range" min={0} max={s.history.length} value={reviewIdx??s.history.length} onChange={e=>{const v=Number(e.target.value); setReviewIdx(v===s.history.length?null:v)}}/></label>
      <a href={'data:text/plain;charset=utf-8,'+encodeURIComponent(sgf)} download={`game-${Date.now()}.sgf`} style={{border:'1px solid #999', padding:'6px 10px', borderRadius:6, textDecoration:'none', color:'#000', background:'#eee'}}>Export SGF</a>
      <label style={{border:'1px solid #999', padding:'6px 10px', borderRadius:6, background:'#eee', cursor:'pointer'}}>Import SGF<input type="file" accept=".sgf" style={{display:'none'}} onChange={async e=>{
        const f=e.target.files?.[0]; if(!f) return; const t=await f.text(); // naive: reset and ignore parse
        void t; alert('SGF import parses via @sabaki/sgf — wiring TODO, file read ok')
      }}/></label>
    </div>

    {err && <div style={{color:'#b00', margin:'8px 0'}}>Server error: {err}</div>}

    <GobanView board={s.board} lastMove={last && last.x>=0 ? [last.x, last.y] as [number,number] : undefined} onVertexClick={onVertexClick} />

    <div style={{marginTop:12, display:'grid', gap:12}}>
      <WinrateGraph history={s.winrateHistory} />
      <div style={{fontSize:13, color:'#444'}}>
        Moves: {s.history.length} · To move: {s.toMove===1?'B':'W'} · Status: {s.status} · Score est area: {(()=>
          {let b=0,w=0; for(const r of signMap) for(const v of r) if(v===1) b++; else if(v===-1) w++; return `B ${b} — W ${w}`})()}
        {s.winrateHistory.length>0 && ` · Last winrate ${(s.winrateHistory[s.winrateHistory.length-1]*100).toFixed(1)}%`}
      </div>
    </div>


  </div>
}
