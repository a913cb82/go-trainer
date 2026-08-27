import type { Rank } from './katagoClient'

const order: Rank[] = ['15k','12k','10k','8k','5k','3k','1k','1d','3d']
function idx(r:Rank){ return order.indexOf(r) }

export function thresholds(rank:Rank){
  const i = idx(rank)
  if(i<=1) return {good:0.02, bad:0.06}
  if(i<=3) return {good:0.02, bad:0.045}
  if(i<=5) return {good:0.015, bad:0.035}
  return {good:0.01, bad:0.025}
}

export function gapColor(gap:number, rank:Rank){
  const {good,bad}= thresholds(rank)
  if(gap <= good) return {name:'green' as const, hex:'#27864a', bg:'#c8f0c8'}
  if(gap <= bad) return {name:'yellow' as const, hex:'#b7791f', bg:'#fff6b0'}
  return {name:'red' as const, hex:'#c0392b', bg:'#ffcccc'}
}
