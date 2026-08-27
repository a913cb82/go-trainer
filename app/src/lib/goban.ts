import GoBoard from '@sabaki/go-board'
import type { Sign } from '@sabaki/go-board'

export type Board = InstanceType<typeof GoBoard>
export type Color = 1 | -1
export const BOARD_SIZE = 9

export function emptyBoard(): Board { return GoBoard.fromDimensions(9, 9) as Board }

export function toSignMap(board: Board): number[][] {
  return (board as any).signMap as number[][]
}

export function isLegal(board: Board, x: number, y: number, color: Color): boolean {
  if ((board as any).get([x, y]) !== 0) return false
  const next = (board as any).makeMove(color as Sign, [x, y])
  return next.get([x, y]) === color
}

export function scoreArea(signMap: number[][]): {b:number,w:number, empty:number} {
  let b=0,w=0,empty=0
  for(let y=0;y<9;y++) for(let x=0;x<9;x++){
    const v=signMap[y][x]
    if(v===1) b++; else if(v===-1) w++; else empty++
  }
  return {b,w,empty}
}
