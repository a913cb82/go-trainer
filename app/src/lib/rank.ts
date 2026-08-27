import type { Rank } from './katagoClient'

const order: Rank[] = ['15k','12k','10k','8k','5k','3k','1k','1d','3d']
function idx(r:Rank){ return order.indexOf(r) }

export function thresholds(rank:Rank){
  const i = idx(rank)
  // Points (predicted score-lead deltas), rank-graduated. Stronger ranks tolerate
  // smaller mistakes. 1.5 pts ≈ a meaningful 9x9 mistake.
  if(i<=1) return {good:1.5, bad:4}    // 15k-12k
  if(i<=3) return {good:1.5, bad:3.2}  // 10k-8k
  if(i<=5) return {good:1, bad:2.5}    // 5k-3k
  return {good:0.8, bad:1.8}           // 1k-3d
}

export function gapColor(gap:number, rank:Rank){
  const {good,bad}= thresholds(rank)
  if(gap <= good) return {name:'green' as const, hex:'#27864a', bg:'#c8f0c8'}
  if(gap <= bad) return {name:'yellow' as const, hex:'#b7791f', bg:'#fff6b0'}
  return {name:'red' as const, hex:'#c0392b', bg:'#ffcccc'}
}
