import type { Board } from '../../lib/goban'
import type { Candidate } from '../../lib/katagoClient'
import { toSignMap } from '../../lib/goban'

const SIZE = 9
const CELL = 36
const PAD = 28
const BOARD_PX = (SIZE - 1) * CELL + PAD * 2

function starPoints(): [number,number][] {
  // 9x9 stars at 2,2 2,6 6,2 6,6 4,4 (0-indexed)
  return [[2,2],[2,6],[6,2],[6,6],[4,4]]
}

export function GobanView({board, candidates, evaluations, lastMove, onVertexClick}:{board:Board, candidates:Candidate[]|null, evaluations:(Candidate & {gap:number})[]|null, lastMove?:[number,number], onVertexClick:(x:number,y:number)=>void}){
  const signMap = toSignMap(board)
  const evalMap = new Map<string, number>()
  if(evaluations) for(const e of evaluations) evalMap.set(`${e.x},${e.y}`, e.gap)
  const candMap = new Map<string, Candidate>()
  if(candidates) for(const c of candidates) candMap.set(`${c.x},${c.y}`, c)

  const stars = starPoints()

  return (
    <div style={{maxWidth:560, margin:'0 auto'}}>
      <svg width={BOARD_PX} height={BOARD_PX} viewBox={`0 0 ${BOARD_PX} ${BOARD_PX}`} style={{display:'block', width:'100%', height:'auto', background:'#e8c07a', borderRadius:8, touchAction:'none'}} >
        {/* board background */}
        <rect x={0} y={0} width={BOARD_PX} height={BOARD_PX} fill="#e8c07a" rx={8}/>
        {/* grid lines */}
        {Array.from({length:SIZE},(_,i)=>{
          const p = PAD + i*CELL
          return <g key={i} stroke="#3e2b15" strokeWidth={1.2}>
            <line x1={PAD} y1={p} x2={PAD + (SIZE-1)*CELL} y2={p}/>
            <line x1={p} y1={PAD} x2={p} y2={PAD + (SIZE-1)*CELL}/>
          </g>
        })}
        {/* star points */}
        {stars.map(([x,y])=> <circle key={`${x}-${y}`} cx={PAD+x*CELL} cy={PAD+y*CELL} r={4} fill="#3e2b15"/>)}
        {/* stones */}
        {signMap.flatMap((row,y)=> row.map((v,x)=>{
          if(v===0) return null
          const cx = PAD + x*CELL, cy= PAD + y*CELL
          const isLast = lastMove && lastMove[0]===x && lastMove[1]===y
          return <g key={`${x}-${y}`}>
            <circle cx={cx} cy={cy} r={16} fill={v===1?'#111':'#fdf8ec'} stroke={v===1?'#000':'#8a7040'} strokeWidth={0.8}/>
            {isLast && <circle cx={cx} cy={cy} r={6} fill="none" stroke={v===1?'#fff':'#111'} strokeWidth={2} opacity={0.9}/>}
          </g>
        }))}
        {/* candidate ghost markers A–E — faint until feedback */}
        {candidates && candidates.map(c=>{
          const cx = PAD + c.x*CELL, cy= PAD + c.y*CELL
          const gap = evalMap.get(`${c.x},${c.y}`)
          let bg='#fff7cc', border='#7a5a1a'
          let fillOp = 0.32, strokeOp = 0.45, textOp = 0.75
          if(gap!==undefined){
            // feedback: slightly stronger but still muted
            if(gap>0.04){ bg='#ffcccc'; border='#8b0000'; fillOp=0.55; strokeOp=0.75 }
            else if(gap>0.02){ bg='#fff0a0'; border='#7a5a1a'; fillOp=0.45; strokeOp=0.65 }
            else { bg='#d6f0d6'; border='#2a5a1a'; fillOp=0.45; strokeOp=0.65 }
            textOp=0.9
          }
          return <g key={`cand-${c.label}`} style={{cursor:'pointer'}} onClick={()=>onVertexClick(c.x,c.y)}>
            <circle cx={cx} cy={cy} r={15} fill={bg} fillOpacity={fillOp} stroke={border} strokeOpacity={strokeOp} strokeWidth={1.4} strokeDasharray={gap===undefined? '4 3' : undefined}/>
            <text x={cx} y={cy+4} textAnchor="middle" fontSize={12} fontWeight={700} fill={border} opacity={textOp}>{c.label}</text>
          </g>
        })}
        {/* click targets */}
        {Array.from({length:SIZE},(_,y)=> Array.from({length:SIZE},(_,x)=>{
          const cx = PAD + x*CELL, cy= PAD + y*CELL
          // don't overlay stone clicks when there's a candidate marker (already clickable)
          if(candMap.has(`${x},${y}`)) return null
          return <circle key={`hit-${x}-${y}`} cx={cx} cy={cy} r={14} fill="transparent" style={{cursor:'pointer'}} onClick={()=>onVertexClick(x,y)}/>
        }))}
        {/* coordinates */}
        {Array.from({length:SIZE},(_,i)=>{
          const lab = String.fromCharCode(65+i + (i>=8?1:0)) // skip I
          return <g key={`coord-${i}`} fontSize={11} fill="#5a3e1a" fontFamily="system-ui">
            <text x={PAD+i*CELL} y={PAD-10} textAnchor="middle">{lab}</text>
            <text x={PAD-14} y={PAD+i*CELL+4} textAnchor="middle">{SIZE - i}</text>
          </g>
        })}
      </svg>
    </div>
  )
}
