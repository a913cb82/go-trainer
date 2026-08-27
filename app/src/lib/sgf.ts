// eslint-disable-next-line @typescript-eslint/no-explicit-any
// @ts-ignore
import * as sgf from '@sabaki/sgf'

export function boardToSgf(_signMap: number[][], history: {x:number,y:number,color:number}[], komi=7){
  const cols='abcdefghi'
  const rows='abcdefghi'
  const moves = history.map(h=>{
    const pt = h.x<0 ? '' : `${cols[h.x]}${rows[h.y]}`
    return `;${h.color===1?'B':'W'}[${pt}]`
  }).join('')
  const sgfStr = `(;GM[1]FF[4]SZ[9]KM[${komi}]${moves})`
  try{
    const trees = sgf.parse(sgfStr)
    return sgf.stringify(trees[0])
  }catch{ return sgfStr }
}

export function parseSgf(sgfStr:string){
  try{ return (sgf as any).parse(sgfStr) } catch(e){ console.warn(e); return [] }
}
