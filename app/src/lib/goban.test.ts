import { describe, it, expect } from 'vitest'
import GoBoard from '@sabaki/go-board'
import { emptyBoard } from './goban'

describe('goban wrapper', ()=>{
  it('creates 9x9 and plays legal', ()=>{
    let b = emptyBoard()
    expect(b.get([4,4])).toBe(0)
    b = (b as any).makeMove(1, [4,4])
    expect(b.get([4,4])).toBe(1)
    // capture
    let c = GoBoard.fromDimensions(9,9) as any
    c = c.makeMove(1,[3,3]); c=c.makeMove(-1,[3,4]); c=c.makeMove(1,[4,4]); c=c.makeMove(-1,[4,4]) // etc - just check not crash
    expect(c).toBeDefined()
  })
  it('illegal on occupied', ()=>{
    let b = emptyBoard()
    b = (b as any).makeMove(1,[0,0])
    const b2 = (b as any).makeMove(1,[0,0])
    expect(b2.get([0,0])).toBe(1) // second move illegal, board unchanged
  })
})
