import type { Board } from '../../lib/goban'
import { toSignMap } from '../../lib/goban'

const SIZE = 9
const CELL = 36
const PAD = 28
const BOARD_PX = (SIZE - 1) * CELL + PAD * 2

function starPoints(): [number,number][] {
  // 9x9 stars at 2,2 2,6 6,2 6,6 4,4 (0-indexed)
  return [[2,2],[2,6],[6,2],[6,6],[4,4]]
}

export function GobanView({board, lastMove, onVertexClick}:{board:Board, lastMove?:[number,number], onVertexClick:(x:number,y:number)=>void}){
  const signMap = toSignMap(board)

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
          const cx = PAD + lx*CELL, cy= PAD + ly*CELL
          const v = signMap[ly][lx]
          return <circle cx={cx} cy={cy} r={7} fill="none" stroke={v===1?'#fff':'#111'} strokeWidth={2} opacity={0.9} style={{pointerEvents:'none'}}/>
        })()}
        {/* click targets */}
        {Array.from({length:SIZE},(_,y)=> Array.from({length:SIZE},(_,x)=>{
          const cx = PAD + x*CELL, cy= PAD + y*CELL
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
