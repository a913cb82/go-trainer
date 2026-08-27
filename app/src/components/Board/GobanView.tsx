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
        {/* stones — halo for last move colored by feedback gap */}
        {signMap.flatMap((row,y)=> row.map((v,x)=>{
          if(v===0) return null
          const cx = PAD + x*CELL, cy= PAD + y*CELL
          const isLast = lastMove && lastMove[0]===x && lastMove[1]===y
          const lastGap = isLast && evaluations ? evaluations.find(e=> e.x===x && e.y===y)?.gap : undefined
          const haloCol = lastGap===undefined ? (v===1?'#fff':'#111') : lastGap>0.04 ? '#c0392b' : lastGap>0.02 ? '#b7791f' : '#27864a'
          const haloW = lastGap===undefined ? 2 : 3
          return <g key={`${x}-${y}`}>
            <circle cx={cx} cy={cy} r={16} fill={v===1?'#111':'#fdf8ec'} stroke={v===1?'#000':'#8a7040'} strokeWidth={0.8}/>
            {isLast && <circle cx={cx} cy={cy} r={7} fill="none" stroke={haloCol} strokeWidth={haloW} opacity={0.95}/>}
          </g>
        }))}
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
        {/* feedback for PREVIOUS turn — on top of everything */}
        {evaluations && evaluations.map(e=>{
          if(signMap[e.y]?.[e.x]!==0 && !(lastMove && e.x===lastMove[0] && e.y===lastMove[1])) return null
          const isPicked = lastMove && e.x===lastMove[0] && e.y===lastMove[1]
          if(isPicked) return null
          const cx = PAD + e.x*CELL, cy= PAD + e.y*CELL
          const col = e.gap>0.04 ? '#c0392b' : e.gap>0.02 ? '#b7791f' : '#27864a'
          const bg = e.gap>0.04 ? '#ffcccc' : e.gap>0.02 ? '#fff6b0' : '#c8f0c8'
          const isOverlap = candMap.has(`${e.x},${e.y}`)
          const ox = isOverlap ? 11 : 0
          const oy = isOverlap ? -11 : 0
          return <g key={`eval-${e.label}`} style={{pointerEvents:'none'}}>
            <circle cx={cx+ox} cy={cy+oy} r={isOverlap? 8 : 10} fill={bg} fillOpacity={0.96} stroke={col} strokeWidth={1.8} strokeOpacity={1}/>
            <text x={cx+ox} y={cy+oy+3.5} textAnchor="middle" fontSize={isOverlap?7:9} fontWeight={800} fill={col}>{e.label}</text>
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
