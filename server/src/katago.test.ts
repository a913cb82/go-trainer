import { describe, it, expect } from 'vitest'
import { mockCandidates, mockGenmove } from './katago.js'

describe('mock picker', ()=>{
  const empty = Array.from({length:9},()=>Array(9).fill(0))
  for(const strat of ['good-vs-tempting','human-only','tesuji','blunder-check','strong-only'] as const){
    it(`strategy ${strat} returns n shuffled`, ()=>{
      const m = mockCandidates(empty as any,'10k',5,strat)
      expect(m.length).toBe(5)
      expect(new Set(m.map(x=>`${x.x},${x.y}`)).size).toBe(5)
    })
  }
  it('genmove returns legal', ()=>{
    const g = mockGenmove(empty as any,'10k')
    expect(g.x).toBeGreaterThanOrEqual(0); expect(g.x).toBeLessThan(9)
  })
})
