import type { Board } from '../../lib/goban'
import type { Candidate, Rank } from '../../lib/katagoClient'
import { toSignMap } from '../../lib/goban'
import { gapColor } from '../../lib/rank'

const SIZE = 9
const CELL = 36
const PAD = 28
const BOARD_PX = (SIZE - 1) * CELL + PAD * 2

function starPoints(): [number,number][] {
  // 9x9 stars at 2,2 2,6 6,2 6,6 4,4 (0-indexed)
  return [[2,2],[2,6],[6,2],[6,6],[4,4]]
}

export function GobanView({board, candidates, evaluations, lastMove, feedbackMove, rank, onVertexClick}:{board:Board, candidates:Candidate[]|null, evaluations:(Candidate & {gap:number})[]|null, lastMove?:[number,number], feedbackMove?:[number,number], rank: Rank, onVertexClick:(x:number,y:number)=>void}){
  const signMap = toSignMap(board)
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
          return <g key={`${x}-${y}`}>
            <circle cx={cx} cy={cy} r={16} fill={v===1?'#111':'#fdf8ec'} stroke={v===1?'#000':'#8a7040'} strokeWidth={0.8}/>
          </g>
        }))}
        {/* last-move marker (enemy or any) — small ring */}
        {lastMove && (()=>{
          const [lx,ly] = lastMove
          if(signMap[ly]?.[lx]===0) return null
          // don't double-draw if feedback halo already covers same stone
          if(feedbackMove && lx===feedbackMove[0] && ly===feedbackMove[1]) return null
          const cx = PAD + lx*CELL, cy= PAD + ly*CELL
          const v = signMap[ly][lx]
          return <circle cx={cx} cy={cy} r={7} fill="none" stroke={v===1?'#fff':'#111'} strokeWidth={2} opacity={0.9} style={{pointerEvents:'none'}}/>
        })()}
        {/* feedback halo for your last move — rank-graduated, stays after enemy moves */}
        {feedbackMove && (()=>{
          const [lx,ly] = feedbackMove
          if(signMap[ly]?.[lx]===0) return null
          const cx = PAD + lx*CELL, cy= PAD + ly*CELL
          const gap = evaluations?.find(e=> e.x===lx && e.y===ly)?.gap
          if(gap===undefined) return null
          const col = gapColor(gap, rank).hex
          return <circle cx={cx} cy={cy} r={19} fill="none" stroke={col} strokeWidth={3.5} opacity={0.95} style={{pointerEvents:'none'}}/>
        })()}
        {/* current candidates — always faint, never tinted by old feedback */}
        {candidates && candidates.map(c=>{
          const cx = PAD + c.x*CELL, cy= PAD + c.y*CELL
          return <g key={`cand-${c.label}`} style={{cursor:'pointer'}} onClick={()=>onVertexClick(c.x,c.y)}>
            <circle cx={cx} cy={cy} r={15} fill="#fff7cc" fillOpacity={0.32} stroke="#7a5a1a" strokeOpacity={0.45} strokeWidth={1.4} strokeDasharray="4 3"/>
            <text x={cx} y={cy+4} textAnchor="middle" fontSize={12} fontWeight={700} fill="#7a5a1a" opacity={0.75}>{c.label}</text>
          </g>
        })}
        {/* click targets — below feedback so feedback stays visible */}
        {Array.from({length:SIZE},(_,y)=> Array.from({length:SIZE},(_,x)=>{
          const cx = PAD + x*CELL, cy= PAD + y*CELL
          if(candMap.has(`${x},${y}`)) return null
          return <circle key={`hit-${x}-${y}`} cx={cx} cy={cy} r={14} fill="transparent" style={{cursor:'pointer'}} onClick={()=>onVertexClick(x,y)}/>
        }))}
        {/* feedback alternatives — rank-graduated, on top */}
        {evaluations && evaluations.map(e=>{
          if(signMap[e.y]?.[e.x]!==0 && !(feedbackMove && e.x===feedbackMove[0] && e.y===feedbackMove[1])) return null
          const isPicked = feedbackMove && e.x===feedbackMove[0] && e.y===feedbackMove[1]
          if(isPicked) return null
          const cx = PAD + e.x*CELL, cy= PAD + e.y*CELL
          const {hex, bg} = gapColor(e.gap, rank)
          const isOverlap = candMap.has(`${e.x},${e.y}`)
          const ox = isOverlap ? 11 : 0
          const oy = isOverlap ? -11 : 0
          return <g key={`eval-${e.label}`} style={{pointerEvents:'none'}}>
            <circle cx={cx+ox} cy={cy+oy} r={isOverlap? 8 : 10} fill={bg} fillOpacity={0.96} stroke={hex} strokeWidth={1.8} strokeOpacity={1}/>
            <text x={cx+ox} y={cy+oy+3.5} textAnchor="middle" fontSize={isOverlap?7:9} fontWeight={800} fill={hex}>{e.label}</text>
          </g>
        })}
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
