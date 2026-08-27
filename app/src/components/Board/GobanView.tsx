import { Goban } from '@sabaki/shudan'
import '@sabaki/shudan/css/goban.css'
import type { Board } from '../../lib/goban'
import type { Candidate } from '../../lib/katagoClient'
import { toSignMap } from '../../lib/goban'

// Shudan is Preact; cast to any for React compat
const GobanAny = Goban as unknown as React.ComponentType<any>

export function GobanView({board, candidates, evaluations, lastMove, onVertexClick}:{board:Board, candidates:Candidate[]|null, evaluations:(Candidate & {gap:number})[]|null, lastMove?:[number,number], onVertexClick:(x:number,y:number)=>void}){
  const signMap = toSignMap(board) as any
  const ghostMap: any = {}
  if(candidates){
    for(const c of candidates) ghostMap[`${c.x}-${c.y}`] = {sign: 1, type:'true', label: c.label}
  }
  const paintMap: any = {}
  if(evaluations){
    for(const e of evaluations){
      const gap = e.gap
      const color = gap>0.04 ? 'red' : gap>0.02 ? 'yellow' : 'green'
      paintMap[`${e.x}-${e.y}`] = color
    }
  }
  return (
    <div style={{maxWidth:560, margin:'0 auto', touchAction:'none'}}>
      <GobanAny
        vertexSize={9}
        signMap={signMap}
        paintMap={evaluations? paintMap : undefined}
        ghostStoneMap={candidates? ghostMap : undefined}
        markerMap={lastMove ? ({[`${lastMove[0]}-${lastMove[1]}`]: {type:'circle'}} as any) : undefined}
        showCoordinates
        animateStonePlacement
        onVertexClick={(_e:any, [x,y]:[number,number])=> onVertexClick(x,y)}
      />
    </div>
  )
}
