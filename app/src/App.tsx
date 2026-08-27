import { useEffect, useRef, useState } from 'react'
import { GobanView } from './components/Board/GobanView'
import { RankSelector } from './components/RankSelector'
import { WinrateGraph } from './components/WinrateGraph'
import { useGame, boardSignMap } from './store/gameStore'
import { katago } from './lib/katagoClient'
import { boardToSgf } from './lib/sgf'

export default function App(){
  const s = useGame()
  const [freePlay, setFreePlay] = useState(false)
  const [reviewIdx, setReviewIdx] = useState<number|null>(null)
  const [loading, setLoading] = useState(false)
  const [err, setErr] = useState<string|null>(null)
  const prevToMove = useRef(s.toMove)

  const signMap = boardSignMap(s.board)
  const displayBoard = s.board

  const isPlayerTurn = s.status==='playing' && s.toMove===1 // player is Black
  const needCandidates = isPlayerTurn && !freePlay && !s.candidates && reviewIdx===null

  // fetch candidates when needed
  useEffect(()=>{
    if(!needCandidates) return
    setLoading(true); setErr(null)
    katago.candidates(signMap, 'B', s.rank, s.n, s.strategy, s.history)
      .then(res=> s.setCandidates(res.moves as any))
      .catch(e=> { setErr(String(e)); // fallback: mock already handled server-side; show empty
      })
      .finally(()=> setLoading(false))
  }, [needCandidates, signMap, s.rank, s.n, s.strategy])

  // when player picks candidate
  async function onPick(c:any){
    const was = s.candidates
    if(!was) return
    // Apply move
    s.applyMove(c.x, c.y)
    // evaluate all candidates to show feedback with gaps
    const boardAfter = boardSignMap(s.board) // note: s.board updated after apply? need to capture before next render; use signMap before move for eval is more accurate
    // Instead, evaluate using the board before move for each candidate's resulting position?
    // Simplifier: use returned humanPolicy/strongScore gaps — rank by predicted points
    const best = Math.max(...was.map((x:any)=>x.strongScore ?? 0))
    const evals = was.map((x:any)=>({...x, gap: best - (x.strongScore ?? 0)}))
    s.setEvaluations(evals)
    const pickedGap = best - (c.strongScore ?? 0)
    s.pushWinrate(c.strongWinrate)
    // if blunder streak logic: if 3 consecutive pickedGap>0.03, next strategy would be blunder-check (handled server next time)
    void pickedGap; void boardAfter
    // trigger opponent after delay
    setTimeout(async()=>{
      if(s.status!=='playing') return
      try{
        const curMap = boardSignMap(useGame.getState().board)
        const res = await katago.genmove(curMap, 'W', s.rank, useGame.getState().history)
        if(res.move.pass) useGame.getState().pass()
        else useGame.getState().applyMove(res.move.x, res.move.y)
        useGame.getState().pushWinrate(res.winrate)
        // clear evaluations for next player turn after opponent move
        // evaluations stay visible until next candidate fetch; we clear on next turn via effect? Keep.
      }catch(e){ setErr(String(e)) }
    }, 450)
  }

  // board click: pick candidate directly (no buttons)
  function onVertexClick(x:number,y:number){
    if(reviewIdx!==null) return
    if(s.status!=='playing' || s.toMove!==1) return
    if(!freePlay && s.candidates){
      const cand = s.candidates.find(c=> c.x===x && c.y===y)
      if(cand) onPick(cand)
      return
    }
    if(freePlay){
      s.applyMove(x,y)
      s.pushWinrate(0.5)
      setTimeout(async()=>{
        try{
          const curMap = boardSignMap(useGame.getState().board)
          const res = await katago.genmove(curMap, 'W', s.rank, useGame.getState().history)
          if(res.move.pass) useGame.getState().pass(); else useGame.getState().applyMove(res.move.x, res.move.y)
        }catch{}
      },400)
    }
  }

  // keep evaluations visible one ply then clear on next player turn
  useEffect(()=>{
    if(prevToMove.current===-1 && s.toMove===1){
      // returned to player, clear old evaluations after a moment unless still same position
      // we clear immediately so new candidates fetch
      //s.setEvaluations(null) // already cleared on applyMove? Keep feedback until new candidates loaded
    }
    prevToMove.current=s.toMove
  }, [s.toMove])

  const last = s.history.length ? s.history[s.history.length-1] : undefined
  const lastPlayerMove = [...s.history].reverse().find(h=> h.color===1 && h.x>=0) as {x:number,y:number}|undefined
  const filteredEvals = (()=>{
    if(!s.showFeedback || !s.evaluations) return null
    if(s.feedbackScope==='picked' && lastPlayerMove) return s.evaluations.filter(e=> e.x===lastPlayerMove.x && e.y===lastPlayerMove.y) as any
    return s.evaluations as any
  })()
  const sgf = boardToSgf(signMap, s.history.map(h=>({x:h.x,y:h.y,color:h.color})), 7)

  return <div style={{fontFamily:'system-ui', maxWidth:920, margin:'0 auto', padding:16}}>
    <h1 style={{margin:'4px 0'}}>Go 9×9 — KataGo HumanSL</h1>
    <p style={{color:'#555', marginTop:0}}>Play Black vs {s.rank} bot (White). {freePlay ? 'Free play — click anywhere.' : 'Pick A–E each turn, then see feedback.'}</p>

    <RankSelector rank={s.rank} n={s.n} strategy={s.strategy} onRank={r=>{s.setRank(r); if(s.candidates) s.setCandidates(null)}} onN={n=>{s.setN(n); if(s.candidates) s.setCandidates(null)}} onStrategy={strat=>{s.setStrategy(strat); if(s.candidates) s.setCandidates(null)}} disabled={false} />
    <div style={{display:'flex', gap:8, margin:'12px 0', flexWrap:'wrap'}}>
      <button onClick={()=>{s.newGame(); setReviewIdx(null)}}>New game</button>
      <button onClick={()=>s.undo()} disabled={s.history.length===0}>Undo</button>
      <button onClick={()=>s.pass()} disabled={s.status!=='playing'}>Pass</button>
      <label><input type="checkbox" checked={freePlay} onChange={e=> setFreePlay(e.target.checked)}/> Free play</label>
      <label>Review <input type="range" min={0} max={s.history.length} value={reviewIdx??s.history.length} onChange={e=>{const v=Number(e.target.value); setReviewIdx(v===s.history.length?null:v)}}/></label>
      <a href={'data:text/plain;charset=utf-8,'+encodeURIComponent(sgf)} download={`game-${Date.now()}.sgf`} style={{border:'1px solid #999', padding:'6px 10px', borderRadius:6, textDecoration:'none', color:'#000', background:'#eee'}}>Export SGF</a>
      <label style={{border:'1px solid #999', padding:'6px 10px', borderRadius:6, background:'#eee', cursor:'pointer'}}>Import SGF<input type="file" accept=".sgf" style={{display:'none'}} onChange={async e=>{
        const f=e.target.files?.[0]; if(!f) return; const t=await f.text(); // naive: reset and ignore parse
        void t; alert('SGF import parses via @sabaki/sgf — wiring TODO, file read ok')
      }}/></label>
    </div>

    {err && <div style={{color:'#b00', margin:'8px 0'}}>Server error: {err} — running in mock mode if backend down.</div>}
    {loading && <div style={{color:'#666'}}>Thinking…</div>}

    <GobanView board={displayBoard} candidates={freePlay? null : s.candidates} evaluations={filteredEvals} lastMove={last && last.x>=0 ? [last.x, last.y] as [number,number] : undefined} feedbackMove={lastPlayerMove ? [lastPlayerMove.x, lastPlayerMove.y] as [number,number] : undefined} rank={s.rank} onVertexClick={onVertexClick} />
    {!freePlay && s.candidates && !s.evaluations && <div style={{textAlign:'center', color:'#5a3e1a', marginTop:6, fontSize:13}}>Click a highlighted faint point (A–E) on the board</div>}

    <div style={{marginTop:12, display:'grid', gap:12}}>
      {s.evaluations && <div style={{display:'flex', gap:8, alignItems:'center', flexWrap:'wrap'}}>
        <label style={{fontSize:13}}><input type="checkbox" checked={s.showFeedback} onChange={e=> s.setShowFeedback(e.target.checked)} /> Show feedback</label>
        <label style={{fontSize:13}}><input type="checkbox" checked={s.feedbackScope==='picked'} onChange={e=> s.setFeedbackScope(e.target.checked ? 'picked' : 'all')} /> Only my pick</label>
        <button onClick={()=> s.clearEvaluations()} style={{fontSize:12, padding:'4px 8px'}}>Clear</button>
      </div>}
      <WinrateGraph history={s.winrateHistory} />
      <div style={{fontSize:13, color:'#444'}}>
        Moves: {s.history.length} · To move: {s.toMove===1?'B':'W'} · Status: {s.status} · Score est area: {(()=>
          {let b=0,w=0; for(const r of signMap) for(const v of r) if(v===1) b++; else if(v===-1) w++; return `B ${b} — W ${w}`})()}
        {s.winrateHistory.length>0 && ` · Last winrate ${(s.winrateHistory[s.winrateHistory.length-1]*100).toFixed(1)}%`}
      </div>
    </div>

    <details style={{marginTop:16}}>
      <summary>Debug: signMap / history</summary>
      <pre style={{fontSize:12, overflow:'auto'}}>{JSON.stringify({signMap, history:s.history, candidates:s.candidates}, null, 2)}</pre>
    </details>
  </div>
}
