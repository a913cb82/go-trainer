import GoBoard from '@sabaki/go-board'

export type Board = InstanceType<typeof GoBoard>
export type Color = 1 | -1
export const BOARD_SIZE = 9

export function emptyBoard(): Board { return GoBoard.fromDimensions(9, 9) as Board }

export function toSignMap(board: Board): number[][] {
  return (board as any).signMap as number[][]
}


